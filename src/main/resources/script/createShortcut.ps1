# Creata a shortcut to Gade on the desktop
$scriptDir = split-path -parent $MyInvocation.MyCommand.Definition
# if the above does not work, try to use $scriptDir = $PSScriptRoot instead
$WScriptObj = New-Object -ComObject ("WScript.Shell")
$desktopPath = [Environment]::GetFolderPath('Desktop')
$shortcut = $WscriptObj.CreateShortcut("$desktopPath\Gade.lnk")
$cmdPath = (get-command cmd.exe).Path
$shortcut.TargetPath = "$cmdPath"
$shortcut.Arguments = "/c $scriptDir\gade.cmd"
$shortcut.WorkingDirectory = "$scriptDir"
$shortcut.IconLocation = "$scriptDir\gade-icon.ico, 0"
$shortcut.Save()