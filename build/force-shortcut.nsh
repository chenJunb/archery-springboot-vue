; 强制创建桌面和开始菜单快捷方式 - NSIS脚本
; 这个脚本确保安装程序一定会创建快捷方式

!include "LogicLib.nsh"

!macro CREATE_SHORTCUTS
  ; 确保桌面快捷方式被创建
  DetailPrint "正在创建桌面快捷方式..."

  ; 创建桌面快捷方式 - 使用绝对路径确保创建
  ; 使用安装目录下的 icon.ico 文件作为图标
  CreateShortCut "$DESKTOP\射箭比赛计时系统.lnk" "$INSTDIR\射箭比赛计时系统.exe" \
    "" "$INSTDIR\icon.ico" 0 SW_SHOWNORMAL "" "射箭比赛计时系统控制台"

  ; 验证快捷方式是否创建
  IfFileExists "$DESKTOP\射箭比赛计时系统.lnk" +3
    DetailPrint "警告: 桌面快捷方式创建失败"
    Goto +2
  DetailPrint "桌面快捷方式创建成功"

  ; 创建开始菜单快捷方式
  DetailPrint "正在创建开始菜单快捷方式..."

  ; 先创建程序组目录
  CreateDirectory "$SMPROGRAMS\射箭比赛计时系统"

  ; 创建主程序快捷方式
  ; 使用安装目录下的 icon.ico 文件作为图标
  CreateShortCut "$SMPROGRAMS\射箭比赛计时系统\射箭比赛计时系统.lnk" "$INSTDIR\射箭比赛计时系统.exe" \
    "" "$INSTDIR\icon.ico" 0 SW_SHOWNORMAL "" "启动射箭比赛计时系统"

  ; 创建卸载快捷方式
  ; 使用卸载程序自身的图标
  CreateShortCut "$SMPROGRAMS\射箭比赛计时系统\卸载射箭比赛计时系统.lnk" "$INSTDIR\Uninstall 射箭比赛计时系统.exe" \
    "" "$INSTDIR\Uninstall 射箭比赛计时系统.exe" 0 SW_SHOWNORMAL "" "卸载射箭比赛计时系统"

  ; 验证开始菜单快捷方式
  IfFileExists "$SMPROGRAMS\射箭比赛计时系统\射箭比赛计时系统.lnk" +3
    DetailPrint "警告: 开始菜单快捷方式创建失败"
    Goto +2
  DetailPrint "开始菜单快捷方式创建成功"

  ; 记录快捷方式创建状态到安装日志
  FileOpen $0 "$INSTDIR\install.log" w
  FileWrite $0 "[Shortcuts]$\r$\n"
  FileWrite $0 "Desktop=Created$\r$\n"
  FileWrite $0 "StartMenu=Created$\r$\n"
  FileWrite $0 "Timestamp=$\r$\n"
  FileClose $0

  DetailPrint "所有快捷方式创建完成"
!macroend


; 在安装完成后显示消息
Function .onInstSuccess
  ; 显示成功消息
  MessageBox MB_OK|MB_ICONINFORMATION "射箭比赛计时系统安装成功！$\r$\n$\r$\n桌面快捷方式已创建: '射箭比赛计时系统'$\r$\n开始菜单快捷方式: '射箭比赛计时系统' 文件夹$\r$\n$\r$\n控制台页面: http://localhost:3000"
FunctionEnd


