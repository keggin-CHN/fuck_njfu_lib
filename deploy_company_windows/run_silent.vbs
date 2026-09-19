Set ws = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
currentDir = fso.GetParentFolderName(WScript.ScriptFullName)

serviceCmd = "cmd.exe /c " & Chr(34) & Chr(34) & currentDir & "\run_service.bat" & Chr(34) & Chr(34)
tunnelCmd = "cmd.exe /c " & Chr(34) & Chr(34) & currentDir & "\run_tunnel.bat" & Chr(34) & Chr(34)

ws.Run serviceCmd, 0, False
WScript.Sleep 1000
ws.Run tunnelCmd, 0, False
