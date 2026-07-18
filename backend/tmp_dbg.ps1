$base='http://127.0.0.1:8080'
$r=Invoke-RestMethod -Uri ($base+'/api/auth/login') -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"admin123456"}'
$token=$r.token
Write-Host "TOKEN_LEN=$($token.Length)"
$h=@{'Authorization'="Bearer $token"}
$body='{"type":"expense","amount":12.5,"recordDate":"2026-07-18","categoryL1":"x","categoryL2":"y","note":"z"}'
try { $c=Invoke-RestMethod -Uri ($base+'/api/records') -Method Post -ContentType 'application/json' -Headers $h -Body $body; Write-Host "CREATE_OK"; Write-Host ($c|ConvertTo-Json) }
catch { Write-Host "CREATE_ERR"; Write-Host $_.ErrorDetails.Message }
