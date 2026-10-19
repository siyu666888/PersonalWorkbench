#!/usr/bin/env bash
# 在模拟器上安装并遍历主要页面，采集 README 演示素材（截图 + GIF）
# 用法：bash tools/capture_demo.sh
set -u
ADB=/d/devtools/android-sdk/platform-tools/adb.exe
S=emulator-5554
APK=app/build/outputs/apk/debug/PersonalWorkbench-1.6.0-debug.apk
OUT=docs/screenshots
PKG=com.example.personalworkbench
W=1080; H=2340          # AVD Pixel_API34 分辨率
NAV_Y=2240              # 底部导航条中心 Y
NAV_X1=108; NAV_X2=324; NAV_X3=540; NAV_X4=756; NAV_X5=972

mkdir -p "$OUT"

echo "== 1/5 等待设备开机 =="
for i in $(seq 1 90); do
  st=$("$ADB" -s $S shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$st" = "1" ] && { echo "已开机（$((i*20))s）"; break; }
  sleep 20
done
[ "$("$ADB" -s $S shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "设备未就绪"; exit 1; }

echo "== 2/5 关闭动画 + 安装 =="
for a in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" -s $S shell settings put global $a 0
done
"$ADB" -s $S shell settings put global window_scaling_force_unres 1 >/dev/null 2>&1
"$ADB" -s $S install -r -t "$APK" 2>&1 | tail -2 || { echo "安装失败"; exit 1; }

echo "== 3/5 冷启动（清数据保证干净首屏）=="
"$ADB" -s $S shell pm clear "$PKG" >/dev/null 2>&1
"$ADB" -s $S shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 12

shot() { "$ADB" -s $S exec-out screencap -p > "$OUT/$1.png"; echo "  ✓ $1.png"; }
tapnav() { "$ADB" -s $S shell input tap "$1" "$NAV_Y"; sleep 3; }

echo "== 4/5 逐页截图 =="
shot 01-home                 # 首页
tapnav $NAV_X2; shot 02-todo          # 待办
tapnav $NAV_X3; shot 03-notes         # 笔记
tapnav $NAV_X4; shot 04-calendar      # 日程
tapnav $NAV_X5; shot 05-settings      # 我的

# 首页内二级页：快捷入口 -> 报表（滚动到底部再找）
tapnav $NAV_X1; sleep 2
"$ADB" -s $S shell input swipe 540 1800 540 700 300; sleep 2
shot 06-home-scrolled

echo "== 5/5 录制演示 GIF =="
# 从首页开始录，依次切Tab 制造动态
"$ADB" -s $S shell screenrecord --time-limit 20 --bit-rate 6000000 /sdcard/demo.mp4 &
REC=$!
sleep 2
for x in $NAV_X2 $NAV_X3 $NAV_X4 $NAV_X5 $NAV_X1; do
  tapnav $x
done
sleep 2
wait $REC 2>/dev/null
"$ADB" -s $S pull /sdcard/demo.mp4 "$OUT/demo.mp4" 2>&1 | tail -1
"$ADB" -s $S shell rm -f /sdcard/demo.mp4

# 转 GIF：10fps、宽 420、循环
ffmpeg -y -i "$OUT/demo.mp4" -vf "fps=10,scale=420:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3" -loop 0 "$OUT/demo.gif" 2>&1 | tail -3

rm -f "$OUT/demo.mp4"
echo "== 完成，产物 =="
ls -la "$OUT"