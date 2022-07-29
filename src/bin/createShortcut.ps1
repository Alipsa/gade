# Creata a shortcut to Gade on the desktop
$scriptDir = split-path -parent $MyInvocation.MyCommand.Definition
# if the above does not work, try to use $scriptDir = $PSScriptRoot instead
$WScriptObj = New-Object -ComObject ("WScript.Shell")
$shortcut = $WscriptObj.CreateShortcut("$env:USERPROFILE\Desktop\Gade.lnk")
$shortcut.TargetPath = "C:\Windows\System32"
$shortcut.Arguments = "/c $scriptDir\gade.cmd"
$shortcut.WorkingDirectory = "$scriptDir"
$shortcut.IconLocation = "$scriptDir\gade-icon.ico, 0"
$shortcut.Save()