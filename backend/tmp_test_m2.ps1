$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$log = 'd:\itcast\vibeCoding\vibeCoding_HeiMa\account_book\backend\m2_test.log'
Set-Content $log ''

$ready = $false
for ($i = 0; $i -lt 40; $i++) {
  try {
    $h = Invoke-RestMethod -Uri ($base + '/api/health') -Method Get -TimeoutSec 2
    if ($h.status -eq 'ok') { $ready = $true; break }
  } catch {}
  Start-Sleep -Seconds 1
}
if (-not $ready) { Add-Content $log 'BACKEND NOT READY'; exit 1 }
$suf = [System.Guid]::NewGuid().ToString('N').Substring(0, 8)

function Post($path, $body, $token) {
  $h = @{}
  if ($token) { $h['Authorization'] = "Bearer $token" }
  $json = $body | ConvertTo-Json -Compress
  try {
    $resp = Invoke-RestMethod -Uri ($base + $path) -Method Post -ContentType 'application/json' -Headers $h -Body $json
    if ($resp.code -ne 0) { return $resp }
    return $resp.data
  } catch {
    $msg = $_.ErrorDetails.Message
    if (-not $msg) { $msg = $_.Exception.Message }
    $st = 'na'
    try { $st = [int]$_.Exception.Response.StatusCode } catch {}
    try { $p = $msg | ConvertFrom-Json; return @{ code = $p.code; status = $st; message = $p.message } } catch { return @{ code = -1; status = $st; message = $msg } }
  }
}

function GetR($path, $token, $params) {
  $url = $base + $path
  if ($params) { $url += '?' + $params }
  try { $resp = Invoke-RestMethod -Uri $url -Method Get -Headers @{ Authorization = "Bearer $token" }; return $resp.data }
  catch {
    $msg = $_.ErrorDetails.Message
    $st = 'na'
    try { $st = [int]$_.Exception.Response.StatusCode } catch {}
    try { $p = $msg | ConvertFrom-Json; return @{ code = $p.code; status = $st; message = $p.message } } catch { return @{ code = -1; status = $st; message = $msg } }
  }
}

function DelR($path, $token) {
  try { Invoke-RestMethod -Uri ($base + $path) -Method Delete -Headers @{ Authorization = "Bearer $token" } | Out-Null; return @{ code = 0 } }
  catch { $msg = $_.ErrorDetails.Message; try { $p = $msg | ConvertFrom-Json; return @{ code = $p.code; message = $p.message } } catch { return @{ code = -1; message = $msg } } }
}

function PutR($path, $body, $token) {
  $h = @{}
  if ($token) { $h['Authorization'] = "Bearer $token" }
  $json = $body | ConvertTo-Json -Compress
  try {
    $resp = Invoke-RestMethod -Uri ($base + $path) -Method Put -ContentType 'application/json' -Headers $h -Body $json
    if ($resp.code -ne 0) { return $resp }
    return $resp.data
  } catch {
    $msg = $_.ErrorDetails.Message
    if (-not $msg) { $msg = $_.Exception.Message }
    $st = 'na'
    try { $st = [int]$_.Exception.Response.StatusCode } catch {}
    try { $p = $msg | ConvertFrom-Json; return @{ code = $p.code; status = $st; message = $p.message } } catch { return @{ code = -1; status = $st; message = $msg } }
  }
}

$pass = 0; $fail = 0
function Check($name, $cond, $extra) {
  if ($cond) { $line = "[PASS] $name"; $script:pass++ }
  else { $line = "[FAIL] $name -> $extra"; $script:fail++ }
  Write-Host $line
  Add-Content $log $line
}

$admin = Post '/api/auth/login' @{ username='admin'; password='admin123456' }
Check 'admin login' ($admin.token -ne $null) ($admin | ConvertTo-Json)
$tree = GetR '/api/categories' $admin.token
Check 'seed category tree not empty' ($tree.Count -gt 0) ($tree.Count)
$exp = $tree | Where-Object { $_.type -eq 'expense' }
Check 'expense L1 seeded' ($exp.Count -gt 0) ($exp.Count)

$ua = Post '/api/auth/register' @{ username="m2A$suf"; password='abc123456' }
Check 'register userA' ($ua.token -ne $null) ($ua | ConvertTo-Json)
$tokenA = $ua.token

$rec = Post '/api/records' @{ type='expense'; amount=23.5; recordDate='2026-07-18'; categoryL1='Food'; categoryL2='Lunch'; note='test' } $tokenA
Check 'A create record' ($rec.id -ne $null) ($rec | ConvertTo-Json)
$recId = $rec.id

$list = @(GetR '/api/records' $tokenA 'month=2026-07')
Check 'A record in list' ($list.Count -ge 1) ($list | ConvertTo-Json -Compress)

$stats = GetR '/api/records/stats/monthly' $tokenA 'month=2026-07'
Check 'monthly expense=23.5' ([math]::Abs([double]$stats.expense - 23.5) -lt 0.001) ($stats | ConvertTo-Json)

$l1food = $exp | Select-Object -First 1
$addL2 = Post '/api/categories' @{ name='MidnightSnack'; parentId=$l1food.id } $tokenA
Check 'A add custom L2' ($addL2.id -ne $null) ($addL2 | ConvertTo-Json)

$addL1 = Post '/api/categories' @{ name="MyL1$suf"; type='expense'; icon='X' } $tokenA
Check 'A add custom L1' ($addL1.id -ne $null) ($addL1 | ConvertTo-Json)

$del = DelR "/api/records/$recId" $tokenA
Check 'A delete record' ($del.code -eq 0) ($del | ConvertTo-Json)

$ub = Post '/api/auth/register' @{ username="m2B$suf"; password='abc123456' }
$tokenB = $ub.token
$listB = GetR '/api/records' $tokenB 'month=2026-07'
Check 'B list empty (isolation)' ($listB.Count -eq 0) ($listB.Count)
$treeB = GetR '/api/categories' $tokenB
$customInB = $treeB | Where-Object { $_.name -eq "MyL1$suf" }
Check 'B cannot see A custom L1 (isolation)' ($customInB -eq $null) ''

$evil = PutR "/api/categories/$($addL1.id)" @{ name='hacked' } $tokenB
Check 'B edit A category rejected (403)' ($evil.code -eq 403) ($evil | ConvertTo-Json)

$nologin = GetR '/api/records' $null ''
Check 'no login rejected (401/403)' ($nologin.status -eq 401 -or $nologin.status -eq 403) ($nologin | ConvertTo-Json)

$result = "RESULT: PASS=$pass FAIL=$fail"
Write-Host $result
Add-Content $log $result
if ($fail -gt 0) { exit 1 }
