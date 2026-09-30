<#
.SYNOPSIS
  Roblox Images Fix (RF) — Windows Edition
  Чинит загрузку иконок/обложек/аватарок в САМОМ КЛИЕНТЕ Roblox и в Studio.

.DESCRIPTION
  Клиент Roblox качает картинки с tr.rbxcdn.com напрямую (мимо браузера),
  поэтому юзерскрипты на него не действуют. Этот инструмент автоматически:

    1. Через DNS-over-HTTPS находит РАБОЧИЕ IP домена tr.rbxcdn.com
       (CloudFront- и Akamai-цепочки; они не блокируются, в отличие от имени rbxcdn.com).
    2. Проверяет каждый IP реальным TLS-соединением (curl --resolve, SNI).
    3. Прописывает лучший IP в C:\Windows\System32\drivers\etc\hosts.
    4. Ставит задачу планировщика, которая каждые 6 часов и при входе в систему
       обновляет IP (CloudFront их периодически меняет).

  Это автоматизация того самого «пропиши IP в hosts», только:
  - IP подбираются тестом соединения, а не «у соседа сработало»;
  - обновляются сами, а не умирают через неделю.

.PARAMETER Install
  Применить фикс + создать задачу планировщика (нужны права администратора).
.PARAMETER Apply
  Только обновить hosts прямо сейчас (без планировщика).
.PARAMETER Remove
  Полностью убрать фикс: почистить hosts и удалить задачу.
.PARAMETER Status
  Показать, что сейчас прописано и жив ли фикс.
.PARAMETER Test
  Диагностика: проверить доступность IP без изменения hosts.
.PARAMETER Menu
  Интерактивное меню (запускается по умолчанию без параметров).

.EXAMPLE
  PowerShell (от админа): .\RobloxCDNFix.ps1 -Install

.NOTES
  Работает на Windows 10/11. Использует встроенный curl.exe и Invoke-RestMethod.
#>
[CmdletBinding()]
param(
  [switch]$Install,
  [switch]$Apply,
  [switch]$Remove,
  [switch]$Status,
  [switch]$Test,
  [switch]$Menu,

  # Какие хосты чинить (по умолчанию главный — thumbnails/tr)
  [string[]]$Targets = @('tr.rbxcdn.com'),

  [string]$HostsPath = "$env:SystemRoot\System32\drivers\etc\hosts",
  [string]$TaskName  = 'RobloxCDNFix',
  [string]$BlockBegin = '# >>> roblox-images-fix (auto) >>>',
  [string]$BlockEnd   = '# <<< roblox-images-fix <<<'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ─────────────────────────── вспомогательные ───────────────────────────

function Test-Admin {
  $id = [Security.Principal.WindowsIdentity]::GetCurrent()
  return (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Invoke-DohJson {
  param([string]$Name)
  # Cloudflare -> Google (если один заблокирован, пробуем второй)
  $endpoints = @(
    "https://cloudflare-dns.com/dns-query?name=$Name&type=A",
    "https://dns.google/resolve?name=$Name&type=A"
  )
  foreach ($ep in $endpoints) {
    try {
      return Invoke-RestMethod -Uri $ep -Headers @{ Accept = 'application/dns-json' } -TimeoutSec 10
    } catch { continue }
  }
  return $null
}

# Раскручивает CNAME-цепочку и возвращает A-записи (IP)
function Get-ResolvedIps {
  param([string]$Name)
  $cur = $Name
  for ($i = 0; $i -lt 6; $i++) {
    $d = Invoke-DohJson -Name $cur
    if (-not $d -or -not $d.Answer) { return @() }
    $cname = $d.Answer | Where-Object { $_.type -eq 5 } | Select-Object -First 1
    if ($cname) {
      $cur = ([string]$cname.data).TrimEnd('.')
      continue
    }
    return @($d.Answer | Where-Object { $_.type -eq 1 } | ForEach-Object { [string]$_.data })
  }
  return @()
}

# Кандидаты-IP для хоста: прямой резолв + 'aws'-цепочка (CloudFront) + 'ak'-цепочка (Akamai)
function Get-CandidateIps {
  param([string]$HostName)
  $label = ($HostName -split '\.')[0]
  $list = @()
  $list += Get-ResolvedIps -Name $HostName                                   # прямой (может не работать в РФ)
  $list += Get-ResolvedIps -Name ($HostName -replace '^[^.]+\.', "$($label)aws.")  # ...aws.rbxcdn.com -> CloudFront
  $list += Get-ResolvedIps -Name ($HostName -replace '^[^.]+\.', "$($label)ak.")   # ...ak.rbxcdn.com  -> Akamai
  return @($list | Select-Object -Unique)
}

# Живой ли IP: реальный TLS-запрос с правильным SNI через curl --resolve
function Test-EdgeIp {
  param([string]$HostName, [string]$Ip)
  $out = & curl.exe -s --resolve "$($HostName):443:$($Ip)" -o NUL -w '%{http_code}' --connect-timeout 6 --max-time 10 "https://$HostName/" 2>$null
  return ($LASTEXITCODE -eq 0 -and $out -ne '000')
}

# Выбирает первый живой IP из кандидатов
function Find-WorkingIp {
  param([string]$HostName)
  $ips = Get-CandidateIps -HostName $HostName
  if (-not $ips) { throw "Не удалось получить ни одного IP для $HostName (DoH недоступен? проверь интернет)" }
  Write-Host "  Кандидаты для ${HostName}: $($ips -join ', ')"
  foreach ($ip in $ips) {
    if (Test-EdgeIp -HostName $HostName -Ip $ip) {
      Write-Host "  ✔ живой IP: $ip" -ForegroundColor Green
      return $ip
    } else {
      Write-Host "  ✗ $ip не отвечает" -ForegroundColor DarkGray
    }
  }
  throw "Все кандидаты для $HostName недоступны. Возможно, блокировка глубже DNS — см. README (zapret/VPN)."
}

# Читает hosts, вырезает наш блок
function Get-HostsContent {
  if (Test-Path $HostsPath) { return @(Get-Content $HostsPath -ErrorAction SilentlyContinue) } else { return @() }
}
function Remove-FixBlock {
  param([string[]]$Lines)
  $res = @(); $inside = $false
  foreach ($l in $Lines) {
    if ($l -match [regex]::Escape($BlockBegin)) { $inside = $true; continue }
    if ($l -match [regex]::Escape($BlockEnd))   { $inside = $false; continue }
    if (-not $inside) { $res += $l }
  }
  return ,$res
}

function Write-FixBlock {
  param([hashtable]$Pinned)   # @{ hostname = ip }
  $lines = Remove-FixBlock (Get-HostsContent)
  if ($lines.Count -gt 0 -and [string]::IsNullOrWhiteSpace($lines[-1])) {
    if ($lines.Count -eq 1) { $lines = @() } else { $lines = $lines[0..($lines.Count-2)] }
  }
  $block = @('', $BlockBegin, '# Roblox Images Fix (RF) — обновляется автоматически, не редактируйте вручную')
  foreach ($h in $Pinned.Keys) { $block += ('{0} {1}' -f $Pinned[$h], $h) }
  $block += ('# обновлено: {0:yyyy-MM-dd HH:mm}' -f (Get-Date))
  $block += $BlockEnd
  ($lines + $block) -join "`r`n" | Set-Content -Path $HostsPath -Encoding Ascii
}

function Clear-DnsCache {
  & ipconfig /flushdns | Out-Null
  Write-Host '  ✔ кэш DNS очищен'
}

# ─────────────────────────── основные действия ───────────────────────────

function Invoke-Apply {
  if (-not (Test-Admin)) { throw 'Нужны права администратора (ПКМ по .bat → «Запуск от имени администратора»).' }
  Write-Host "`n=== Обновляю IP для $($Targets -join ', ') ===" -ForegroundColor Cyan
  $pinned = @{}
  foreach ($t in $Targets) {
    try { $pinned[$t] = Find-WorkingIp -HostName $t }
    catch { Write-Host "  ⚠ $($_.Exception.Message)" -ForegroundColor Yellow }
  }
  if ($pinned.Count -eq 0) { throw 'Ни один хост не удалось починить.' }
  Write-Host '  Пишу hosts...'
  Write-FixBlock -Pinned $pinned
  Clear-DnsCache
  Write-Host '✔ Готово! Перезапусти Roblox (полностью закрой и открой).' -ForegroundColor Green
}

function Invoke-Install {
  Invoke-Apply
  Write-Host "`n=== Создаю задачу планировщика «$TaskName» (обновление каждые 6 ч + при входе) ===" -ForegroundColor Cyan
  $ps = (Get-Process -Id $PID).Path
  $action  = New-ScheduledTaskAction -Execute $ps -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Apply"
  $tRepeat = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(5) -RepetitionInterval (New-TimeSpan -Hours 6) -RepetitionDuration (New-TimeSpan -Days 3650)
  $tLogon  = New-ScheduledTaskTrigger -AtLogOn
  $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
  Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger @($tRepeat, $tLogon) -Settings $settings -Description 'Roblox Images Fix (RF): автообновление IP для rbxcdn.com' -Force | Out-Null
  Write-Host '✔ Задача создана. Фикс будет поддерживать себя сам.' -ForegroundColor Green
}

function Invoke-Remove {
  if (-not (Test-Admin)) { throw 'Нужны права администратора.' }
  Write-Host "`n=== Удаляю фикс ===" -ForegroundColor Cyan
  Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
  Write-Host '  ✔ задача планировщика удалена'
  $lines = Remove-FixBlock (Get-HostsContent)
  $lines -join "`r`n" | Set-Content -Path $HostsPath -Encoding Ascii
  Write-Host '  ✔ hosts почищен'
  Clear-DnsCache
  Write-Host '✔ Фикс полностью удалён.' -ForegroundColor Green
}

function Show-Status {
  Write-Host "`n=== Статус Roblox Images Fix (RF) ===" -ForegroundColor Cyan
  $lines  = Get-HostsContent
  $inside = $false; $found = @()
  foreach ($l in $lines) {
    if ($l -match [regex]::Escape($BlockBegin)) { $inside = $true; continue }
    if ($l -match [regex]::Escape($BlockEnd))   { $inside = $false; continue }
    if ($inside -and $l -match '^\s*(\d{1,3}(?:\.\d{1,3}){3})\s+(\S+)') { $found += [pscustomobject]@{ IP = $Matches[1]; Host = $Matches[2] } }
  }
  if ($found) {
    foreach ($f in $found) {
      $alive = Test-EdgeIp -HostName $f.Host -Ip $f.IP
      $mark  = if ($alive) { '✔ живой' } else { '✗ мёртвый — запусти обновление' }
      $color = if ($alive) { 'Green' } else { 'Red' }
      Write-Host ("  {0} -> {1}  [{2}]" -f $f.Host, $f.IP, $mark) -ForegroundColor $color
    }
  } else {
    Write-Host '  hosts: фикс НЕ установлен' -ForegroundColor Yellow
  }
  $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
  if ($task) { Write-Host ("  планировщик: задача «{0}», состояние: {1}" -f $TaskName, $task.State) }
  else       { Write-Host '  планировщик: задача не создана' -ForegroundColor Yellow }
}

function Invoke-TestOnly {
  Write-Host "`n=== Диагностика (hosts НЕ меняется) ===" -ForegroundColor Cyan
  foreach ($t in $Targets) {
    try {
      $ip = Find-WorkingIp -HostName $t
      Write-Host "  $t : можно починить, рабочий IP — $ip (примени -Apply)" -ForegroundColor Green
    } catch {
      Write-Host "  $t : $($_.Exception.Message)" -ForegroundColor Red
    }
  }
}

function Show-Menu {
  while ($true) {
    Write-Host ''
    Write-Host '╔══════════════════════════════════════════════╗' -ForegroundColor Cyan
    Write-Host '║   Roblox Images Fix (RF) — Windows Edition   ║' -ForegroundColor Cyan
    Write-Host '╠══════════════════════════════════════════════╣' -ForegroundColor Cyan
    Write-Host '║  1) Установить фикс + автoобновление         ║'
    Write-Host '║  2) Обновить IP прямо сейчас                 ║'
    Write-Host '║  3) Статус                                   ║'
    Write-Host '║  4) Диагностика (ничего не меняет)           ║'
    Write-Host '║  5) Удалить фикс                             ║'
    Write-Host '║  0) Выход                                    ║'
    Write-Host '╚══════════════════════════════════════════════╝' -ForegroundColor Cyan
    $c = Read-Host 'Выбери пункт'
    try {
      switch ($c) {
        '1' { Invoke-Install }
        '2' { Invoke-Apply }
        '3' { Show-Status }
        '4' { Invoke-TestOnly }
        '5' { Invoke-Remove }
        '0' { return }
      }
    } catch {
      Write-Host "Ошибка: $($_.Exception.Message)" -ForegroundColor Red
    }
  }
}

# ─────────────────────────── точка входа ───────────────────────────

if     ($Install) { try { Invoke-Install } catch { Write-Host "Ошибка: $($_.Exception.Message)" -ForegroundColor Red; exit 1 } }
elseif ($Apply)   { try { Invoke-Apply   } catch { Write-Host "Ошибка: $($_.Exception.Message)" -ForegroundColor Red; exit 1 } }
elseif ($Remove)  { try { Invoke-Remove  } catch { Write-Host "Ошибка: $($_.Exception.Message)" -ForegroundColor Red; exit 1 } }
elseif ($Status)  { Show-Status }
elseif ($Test)    { Invoke-TestOnly }
else              { Show-Menu }
