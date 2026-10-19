#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把 Android VectorDrawable 转换为 SVG，并生成一个 HTML 预览页，
用于人工核对「底部导航彩色拟物图标」与「App 启动图标」的最终效果。

用法: python tools/build_icon_preview.py
输出: tools/icon-preview.html
"""
import os
import re
import base64
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"
OUT = os.path.join(ROOT, "tools", "icon-preview.html")

NAV_ICONS = [
    ("ic_nav_home_color", "首页", "home"),
    ("ic_nav_todo_color", "待办", "todo"),
    ("ic_nav_note_color", "笔记", "note"),
    ("ic_nav_calendar_color", "日程", "calendar"),
    ("ic_nav_me_color", "我的", "me"),
]

QUICK_ICONS = [
    ("ic_nav_todo_color", "待办", "q-todo"),
    ("ic_nav_note_color", "笔记", "q-note"),
    ("ic_nav_calendar_color", "日程", "q-calendar"),
    ("ic_quick_goal_color", "目标", "q-goal"),
    ("ic_quick_file_color", "文件", "q-file"),
    ("ic_quick_project_color", "项目", "q-project"),
    ("ic_quick_report_color", "报表", "q-report"),
]


def argb_to_svg(color):
    """Android #AARRGGBB -> (svg #RRGGBB, opacity)"""
    c = color.lstrip("#")
    if len(c) == 8:
        a = int(c[0:2], 16) / 255.0
        return "#" + c[2:], a
    return "#" + c, 1.0


def vector_to_svg(path):
    tree = ET.parse(path)
    root = tree.getroot()
    vw = root.get(A + "viewportWidth", "24")
    vh = root.get(A + "viewportHeight", "24")

    defs = []
    body = []
    gid = [0]

    for el in root:
        if el.tag != "path":
            continue
        d = el.get(A + "pathData", "")
        stroke = el.get(A + "strokeColor")
        if stroke:
            col, op = argb_to_svg(stroke)
            sw = el.get(A + "strokeWidth", "1")
            body.append(
                '<path d="%s" fill="none" stroke="%s" stroke-opacity="%.3f" '
                'stroke-width="%s" stroke-linecap="round"/>' % (d, col, op, sw)
            )
            continue

        fill = el.get(A + "fillColor")
        grad = None
        for child in el:
            if child.tag == AAPT + "attr" and child.get("name") == "android:fillColor":
                grad = child.find("gradient")

        if grad is not None:
            gid[0] += 1
            gname = "g%d" % gid[0]
            sx, sy = grad.get(A + "startX", "0"), grad.get(A + "startY", "0")
            ex, ey = grad.get(A + "endX", str(vw)), grad.get(A + "endY", str(vh))
            stops = []
            for item in grad.findall("item"):
                off = item.get(A + "offset", "0")
                col, op = argb_to_svg(item.get(A + "color", "#FF000000"))
                stops.append(
                    '<stop offset="%s" stop-color="%s" stop-opacity="%.3f"/>' % (off, col, op)
                )
            defs.append(
                '<linearGradient id="%s" gradientUnits="userSpaceOnUse" '
                'x1="%s" y1="%s" x2="%s" y2="%s">%s</linearGradient>'
                % (gname, sx, sy, ex, ey, "".join(stops))
            )
            body.append('<path d="%s" fill="url(#%s)"/>' % (d, gname))
        elif fill:
            col, op = argb_to_svg(fill)
            body.append(
                '<path d="%s" fill="%s" fill-opacity="%.3f"/>' % (d, col, op)
            )
        else:
            body.append('<path d="%s"/>' % d)

    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s">%s%s</svg>'
        % (vw, vh, "<defs>%s</defs>" % "".join(defs) if defs else "", "".join(body))
    )


def png_data_uri(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")


def main():
    cells = []
    quick_cells = []
    svgs = {}
    for name, label, key in NAV_ICONS:
        svg = vector_to_svg(os.path.join(RES, "drawable", name + ".xml"))
        svgs[key] = svg
        cells.append(
            '<div class="cell"><div class="ico">%s</div><div class="cap">%s</div></div>'
            % (svg, label)
        )
    for name, label, key in QUICK_ICONS:
        svg = vector_to_svg(os.path.join(RES, "drawable", name + ".xml"))
        svgs[key] = svg
        quick_cells.append(
            '<div class="cell"><div class="ico">%s</div><div class="cap">%s</div></div>'
            % (svg, label)
        )

    launcher = png_data_uri(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher.png"))
    round_icon = png_data_uri(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_round.png"))
    fg = png_data_uri(os.path.join(RES, "drawable-xxxhdpi", "ic_launcher_foreground.png"))
    bg_svg = vector_to_svg(os.path.join(RES, "drawable", "ic_launcher_background.xml"))

    nav_icons = "".join(svgs[k] for _, _, k in NAV_ICONS)
    nav_labels = "".join(
        '<div class="nav-item"><span class="nav-ico">%s</span><span class="nav-txt">%s</span></div>'
        % (svgs[k], lbl)
        for _, lbl, k in NAV_ICONS
    )
    quick_labels = "".join(
        '<div class="q-entry"><span class="q-ico">%s</span><span class="q-txt">%s</span></div>'
        % (svgs[k], lbl)
        for _, lbl, k in QUICK_ICONS
    )

    html = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>个人工作台 · V1.3.0 图标预览</title>
<style>
  :root {{
    --bg:#f4f2f8; --card:#ffffff; --text:#1d1b20; --sub:#49454f; --line:#e2dceb; --accent:#6750a4;
  }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--text);
         font-family:-apple-system,"PingFang SC","Microsoft YaHei",system-ui,sans-serif; }}
  .wrap {{ max-width:960px; margin:0 auto; padding:32px 20px 56px; }}
  h1 {{ font-size:22px; margin:0 0 6px; }}
  .sub {{ color:var(--sub); font-size:13px; margin-bottom:28px; }}
  h2 {{ font-size:15px; margin:32px 0 14px; letter-spacing:.02em; }}
  .card {{ background:var(--card); border:1px solid var(--line); border-radius:16px;
           padding:22px; box-shadow:0 1px 2px rgba(0,0,0,.04); }}
  .grid {{ display:grid; grid-template-columns:repeat(5,1fr); gap:14px; }}
  .cell {{ text-align:center; padding:16px 6px; border-radius:12px; background:#faf9fd; }}
  .ico {{ width:48px; height:48px; margin:0 auto 10px; }}
  .ico svg {{ width:48px; height:48px; display:block; }}
  .cap {{ font-size:12px; color:var(--sub); }}
  .row {{ display:flex; gap:26px; flex-wrap:wrap; align-items:flex-start; }}
  .appicon {{ text-align:center; }}
  .appicon img {{ width:132px; height:132px; display:block; border-radius:28px;
                  box-shadow:0 6px 18px rgba(0,0,0,.14); }}
  .appicon.round img {{ border-radius:50%; }}
  .appicon .cap {{ margin-top:10px; font-size:12px; color:var(--sub); }}
  .layers {{ position:relative; width:132px; height:132px; border-radius:28px;
             overflow:hidden; box-shadow:0 6px 18px rgba(0,0,0,.14); }}
  .layers .bg, .layers .fg {{ position:absolute; inset:0; }}
  .layers .bg svg {{ width:100%; height:100%; display:block; }}
  .layers .fg {{ background-size:100% 100%; }}
  .phone {{ width:300px; border-radius:24px; padding:14px 10px 8px;
            background:var(--card); border:1px solid var(--line);
            box-shadow:0 1px 2px rgba(0,0,0,.04); }}
  .navbar {{ display:flex; justify-content:space-between; align-items:center;
             background:#ece6f0; border-radius:18px; padding:10px 6px; }}
  .nav-item {{ flex:1; display:flex; flex-direction:column; align-items:center; gap:4px; }}
  .nav-ico svg {{ width:28px; height:28px; display:block; }}
  .nav-txt {{ font-size:11px; line-height:13px; letter-spacing:.4px; color:#49454f; }}
  .quick {{ display:grid; grid-template-columns:repeat(4,1fr); gap:10px; }}
  .q-entry {{ display:flex; align-items:center; gap:10px; background:#faf9fd;
             border-radius:12px; padding:12px 10px; }}
  .q-ico svg {{ width:30px; height:30px; display:block; }}
  .q-txt {{ font-size:13px; color:#1d1b20; }}
  ul {{ margin:8px 0 0; padding-left:20px; color:var(--sub); font-size:13px; line-height:1.8; }}
</style>
</head>
<body>
<div class="wrap">
  <h1>V1.3.1 图标预览</h1>
  <div class="sub">彩色拟物风格底部导航图标（已放大） · 首页快捷入口图标 · App 启动图标（自适应 / 圆形）</div>

  <h2>① 底部导航图标（28dp，已放大）</h2>
  <div class="card">
    <div class="grid">{cells}</div>
  </div>

  <h2>② 底部导航栏实际效果</h2>
  <div class="card">
    <div class="phone">
      <div class="navbar">{nav_labels}</div>
    </div>
  </div>

  <h2>③ 首页快捷入口图标（30dp，拟物化统一风格）</h2>
  <div class="card">
    <div class="quick">{quick_labels}</div>
    <div class="grid" style="margin-top:14px">{quick_cells}</div>
  </div>

  <h2>③ App 启动图标</h2>
  <div class="card">
    <div class="row">
      <div class="appicon"><img src="{launcher}" alt="ic_launcher"/><div class="cap">方形（ic_launcher）</div></div>
      <div class="appicon round"><img src="{round_icon}" alt="ic_launcher_round"/><div class="cap">圆形（ic_launcher_round）</div></div>
      <div class="appicon"><img src="{fg}" alt="foreground"
        style="border-radius:16px;background:#f0f0f0;box-shadow:none;"/><div class="cap">自适应前景层（xxxhdpi PNG）</div></div>
      <div>
        <div class="layers"><div class="bg">{bg_svg}</div><div class="fg" style="background-image:url('{fg}')"></div></div>
        <div class="cap" style="text-align:center;margin-top:10px;font-size:12px;color:#49454f;">背景层 + 前景层合成</div>
      </div>
    </div>
    <ul>
      <li>自适应图标由 <code>mipmap-anydpi-v26/ic_launcher.xml</code> 组合背景渐变层与前景 PNG。</li>
      <li>API &lt; 26 的设备回落到 <code>mipmap-*dpi/ic_launcher.png</code> 五档位图。</li>
      <li>前景内容收敛在中心 62% 安全区内，任何厂商蒙版（圆/方/水滴）都不会裁到主体。</li>
    </ul>
  </div>
</div>
</body>
</html>
""".format(
        cells="".join(cells),
        quick_cells="".join(quick_cells),
        quick_labels=quick_labels,
        nav_labels=nav_labels,
        launcher=launcher,
        round_icon=round_icon,
        fg=fg,
        bg_svg=bg_svg,
    )

    with open(OUT, "w", encoding="utf-8") as f:
        f.write(html)
    print("wrote", OUT, len(html), "bytes")


if __name__ == "__main__":
    main()
