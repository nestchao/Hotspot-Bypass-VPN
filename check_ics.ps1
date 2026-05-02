
$netShare = New-Object -ComObject HNetCfg.HNetShare
$count = 0
foreach ($conn in $netShare.EnumEveryConnection) {
    $count++
    $props = $netShare.NetConnectionProps($conn)
    Write-Host "Name: $($props.Name)"
    $config = $netShare.INetSharingConfigurationForINetConnection($conn)
    Write-Host "  SharingEnabled: $($config.SharingEnabled)"
    Write-Host "  SharingType: $($config.SharingType)"
}
Write-Host "Total connections found: $count"
