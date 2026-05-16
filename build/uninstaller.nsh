; 自定义NSIS卸载脚本 - 删除用户数据目录
!macro customUnInstall
  ; 删除用户数据目录
  ${if} ${RunningX64}
    StrCpy $0 "$LOCALAPPDATA\Programs\archery-timer"
  ${else}
    StrCpy $0 "$APPDATA\archery-timer"
  ${endif}

  ; 检查目录是否存在
  IfFileExists "$0\*.*" 0 +3
    RMDir /r "$0"
    DetailPrint "Deleted user data directory: $0"

  ; 也可以删除注册表中的设置（如果需要）
  ; DeleteRegKey HKCU "Software\射箭比赛计时系统"

!macroend

; 或者使用更通用的方法
Function un.onUninstSuccess
  ; 卸载成功后执行的代码
  ; 可以在这里显示消息
  MessageBox MB_OK "应用程序已成功卸载。$\r$\n用户数据目录已被保留。"
FunctionEnd

Function un.onUninstFailed
  MessageBox MB_OK|MB_ICONEXCLAMATION "卸载失败。"
FunctionEnd

; 在卸载过程中删除用户数据
Section "Uninstall"
  ; 删除安装目录
  RMDir /r "$INSTDIR"

  ; 删除开始菜单快捷方式
  Delete "$SMPROGRAMS\射箭比赛计时系统.lnk"

  ; 删除桌面快捷方式
  Delete "$DESKTOP\射箭比赛计时系统.lnk"

  ; 删除用户数据目录（谨慎使用）
  ; RMDir /r "$APPDATA\archery-timer"

SectionEnd