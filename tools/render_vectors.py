#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
轻量 SVG 光栅化器（仅覆盖本项目图标用到的子集），用于把 Android VectorDrawable
渲染成 PNG 做人工视觉核对。

支持: M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z; solid fill + 线性/径向渐变(按角度近似);
      描边(stroke)按折线绘制。

用法: python tools/render_vectors.py
"""
import math
import os
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"

NAV_ICONS = [
    ("ic_nav_home_color", "home"),
    ("ic_nav_todo_color", "todo"),
    ("ic_nav_note_color", "note"),
    ("ic_nav_calendar_color", "calendar"),
    ("ic_nav_me_color", "me"),
]

QUICK_ICONS = [
    ("ic_nav_todo_color", "quick-todo"),
    ("ic_nav_note_color", "quick-note"),
    ("ic_nav_calendar_color", "quick-calendar"),
    ("ic_quick_goal_color", "quick-goal"),
    ("ic_quick_file_color", "quick-file"),
    ("ic_quick_project_color", "quick-project"),
    ("ic_quick_report_color", "quick-report"),
]

TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")


def parse_path(d):
    return TOKEN.findall(d)


def flatten(d, samples=14):
    """把 pathData 展开成若干「点列」（每个子路径一个 list）。"""
    ts = parse_path(d)
    i = 0
    subpaths = []
    cur = []
    x = y = 0.0
    sx = sy = 0.0
    cmd = None
    prev_ctrl = None

    def num():
        nonlocal i
        v = float(ts[i])
        i += 1
        return v

    def push(px, py):
        cur.append((px, py))

    while i < len(ts):
        t = ts[i]
        if re.match(r"[A-Za-z]", t):
            cmd = t
            i += 1
        # 隐式重复上一个命令
        rel = cmd.islower()
        c = cmd.upper()

        if c == "M":
            nx, ny = num(), num()
            px = x + nx if rel else nx
            py = y + ny if rel else ny
            if cur:
                subpaths.append(cur)
            cur = []
            push(px, py)
            x, y = px, py
            sx, sy = px, py
            cmd = "l" if rel else "L"
        elif c == "L":
            nx, ny = num(), num()
            px = x + nx if rel else nx
            py = y + ny if rel else ny
            push(px, py)
            x, y = px, py
        elif c == "H":
            nx = num()
            px = x + nx if rel else nx
            push(px, y)
            x = px
        elif c == "V":
            ny = num()
            py = y + ny if rel else ny
            push(x, py)
            y = py
        elif c in ("C", "S", "Q", "T"):
            if c == "C":
                if rel:
                    x1 = x + num(); y1 = y + num()
                    x2 = x + num(); y2 = y + num()
                    nx = x + num(); ny = y + num()
                else:
                    x1 = num(); y1 = num()
                    x2 = num(); y2 = num()
                    nx = num(); ny = num()
                p0 = (x, y); p1 = (x1, y1); p2 = (x2, y2); p3 = (nx, ny)
                prev_ctrl = p2
            elif c == "S":
                if rel:
                    x2 = x + num(); y2 = y + num(); nx = x + num(); ny = y + num()
                else:
                    x2 = num(); y2 = num(); nx = num(); ny = num()
                p1 = (2 * x - prev_ctrl[0], 2 * y - prev_ctrl[1]) if prev_ctrl else (x, y)
                p0 = (x, y); p2 = (x2, y2); p3 = (nx, ny)
                prev_ctrl = p2
            elif c == "Q":
                if rel:
                    x1 = x + num(); y1 = y + num(); nx = x + num(); ny = y + num()
                else:
                    x1 = num(); y1 = num(); nx = num(); ny = num()
                # 升阶为三次贝塞尔
                p0 = (x, y); p3 = (nx, ny)
                p1 = (p0[0] + 2.0 / 3 * (x1 - p0[0]), p0[1] + 2.0 / 3 * (y1 - p0[1]))
                p2 = (p3[0] + 2.0 / 3 * (x1 - p3[0]), p3[1] + 2.0 / 3 * (y1 - p3[1]))
                prev_ctrl = (x1, y1)
            else:  # T
                if rel:
                    nx = x + num(); ny = y + num()
                else:
                    nx = num(); ny = num()
                q = (2 * x - prev_ctrl[0], 2 * y - prev_ctrl[1]) if prev_ctrl else (x, y)
                p0 = (x, y); p3 = (nx, ny)
                p1 = (p0[0] + 2.0 / 3 * (q[0] - p0[0]), p0[1] + 2.0 / 3 * (q[1] - p0[1]))
                p2 = (p3[0] + 2.0 / 3 * (q[0] - p3[0]), p3[1] + 2.0 / 3 * (q[1] - p3[1]))
                prev_ctrl = q
            for k in range(1, samples + 1):
                t0 = k / samples
                mt = 1 - t0
                bx = (mt ** 3 * p0[0] + 3 * mt ** 2 * t0 * p1[0]
                      + 3 * mt * t0 ** 2 * p2[0] + t0 ** 3 * p3[0])
                by = (mt ** 3 * p0[1] + 3 * mt ** 2 * t0 * p1[1]
                      + 3 * mt * t0 ** 2 * p2[1] + t0 ** 3 * p3[1])
                push(bx, by)
            x, y = p3
        elif c == "A":
            rx, ry = num(), num()
            rot = num()
            large = int(num())
            sweep = int(num())
            nx = num(); ny = num()
            px = x + nx if rel else nx
            py = y + ny if rel else ny
            for pt in arc_points(x, y, rx, ry, rot, large, sweep, px, py):
                push(*pt)
            x, y = px, py
        elif c == "Z":
            push(sx, sy)
            subpaths.append(cur)
            cur = []
            x, y = sx, sy
        else:
            i += 1

    if cur:
        subpaths.append(cur)
    return subpaths


def arc_points(x1, y1, rx, ry, rot_deg, large, sweep, x2, y2, samples=20):
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    rx, ry = abs(rx), abs(ry)
    phi = math.radians(rot_deg)
    cosp, sinp = math.cos(phi), math.sin(phi)
    dx2, dy2 = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cosp * dx2 + sinp * dy2
    y1p = -sinp * dx2 + cosp * dy2
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        s = math.sqrt(lam)
        rx *= s
        ry *= s
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    co = math.sqrt(max(num / den, 0.0))
    if large == sweep:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cosp * cxp - sinp * cyp + (x1 + x2) / 2.0
    cy = sinp * cxp + cosp * cyp + (y1 + y2) / 2.0

    def ang(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        n = math.hypot(ux, uy) * math.hypot(vx, vy)
        a = math.acos(max(-1.0, min(1.0, dot / n))) if n else 0.0
        return -a if ux * vy - uy * vx < 0 else a

    theta1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dtheta = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if sweep == 0 and dtheta > 0:
        dtheta -= 2 * math.pi
    elif sweep == 1 and dtheta < 0:
        dtheta += 2 * math.pi

    pts = []
    for k in range(1, samples + 1):
        th = theta1 + dtheta * k / samples
        px = cx + rx * math.cos(th) * cosp - ry * math.sin(th) * sinp
        py = cy + rx * math.cos(th) * sinp + ry * math.sin(th) * cosp
        pts.append((px, py))
    return pts


def argb(color):
    c = color.lstrip("#")
    if len(c) == 8:
        return (int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16), int(c[0:2], 16))
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)


def sample_stops(stops, offs, t):
    """在色标序列上按 t 线性插值取色"""
    if not stops:
        return (0, 0, 0, 0)
    if t <= offs[0]:
        return stops[0]
    if t >= offs[-1]:
        return stops[-1]
    for i in range(len(offs) - 1):
        if offs[i] <= t <= offs[i + 1]:
            span = offs[i + 1] - offs[i]
            k = 0.0 if span <= 0 else (t - offs[i]) / span
            a, b = stops[i], stops[i + 1]
            return (
                int(a[0] + (b[0] - a[0]) * k),
                int(a[1] + (b[1] - a[1]) * k),
                int(a[2] + (b[2] - a[2]) * k),
                int(a[3] + (b[3] - a[3]) * k),
            )
    return stops[-1]


def render(path, size=128):
    tree = ET.parse(path)
    root = tree.getroot()
    vw = float(root.get(A + "viewportWidth", "24"))
    vh = float(root.get(A + "viewportHeight", "24"))
    scale = size / vw
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    for el in root:
        if el.tag != "path":
            continue
        d = el.get(A + "pathData", "")
        subs = flatten(d)

        stroke = el.get(A + "strokeColor")
        if stroke:
            layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            dr = ImageDraw.Draw(layer)
            w = max(1, int(round(float(el.get(A + "strokeWidth", "1")) * scale)))
            for pts in subs:
                dr.line([(px * scale, py * scale) for px, py in pts],
                        fill=argb(stroke), width=w, joint="curve")
            img = Image.alpha_composite(img, layer)
            continue

        fill = el.get(A + "fillColor")
        grad = None
        for child in el:
            if child.tag == AAPT + "attr" and child.get("name") == "android:fillColor":
                grad = child.find("gradient")

        if grad is None and not fill:
            continue

        # 先按当前 path 的几何生成蒙版（渐变填充与蒙版必须成对，不能跨 path 复用）
        mask = Image.new("L", (size, size), 0)
        md = ImageDraw.Draw(mask)
        for pts in subs:
            if len(pts) >= 3:
                md.polygon([(px * scale, py * scale) for px, py in pts], fill=255)
            elif len(pts) == 2:
                md.line([(px * scale, py * scale) for px, py in pts], fill=255, width=1)

        if grad is not None:
            items = grad.findall("item")
            gtype = grad.get(A + "type", "linear")
            stops = [argb(it.get(A + "color", "#FF000000")) for it in items]
            offs = [float(it.get(A + "offset", "0")) for it in items]
            cx = float(grad.get(A + "centerX", "0"))
            cy = float(grad.get(A + "centerY", "0"))
            rad = float(grad.get(A + "gradientRadius", "0"))
            if gtype == "radial":
                # 径向渐变：按到圆心的距离取色
                layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
                px_acc = layer.load()
                for yy in range(size):
                    for xx in range(size):
                        if not mask.getpixel((xx, yy)):
                            continue
                        dist = math.hypot(xx / scale - cx, yy / scale - cy)
                        t = min(max(dist / rad, 0.0), 1.0) if rad > 0 else 1.0
                        px_acc[xx, yy] = sample_stops(stops, offs, t)
                img = Image.alpha_composite(img, layer)
                continue
            # 线性渐变：沿 start->end 方向投影取色（比纯垂直渐变更贴近真机）
            sx = float(grad.get(A + "startX", "0"))
            sy = float(grad.get(A + "startY", "0"))
            ex = float(grad.get(A + "endX", str(vw)))
            ey = float(grad.get(A + "endY", str(vh)))
            vx, vy = ex - sx, ey - sy
            denom = vx * vx + vy * vy
            layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            px_acc = layer.load()
            for yy in range(size):
                for xx in range(size):
                    if not mask.getpixel((xx, yy)):
                        continue
                    px_, py_ = xx / scale, yy / scale
                    t = 0.0 if denom <= 0 else ((px_ - sx) * vx + (py_ - sy) * vy) / denom
                    px_acc[xx, yy] = sample_stops(stops, offs, min(max(t, 0.0), 1.0))
            img = Image.alpha_composite(img, layer)
            continue

        # 纯色填充
        color = argb(fill)
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        px_acc = layer.load()
        for yy in range(size):
            for xx in range(size):
                if mask.getpixel((xx, yy)):
                    px_acc[xx, yy] = color
        img = Image.alpha_composite(img, layer)

    return img


def main():
    pad = 24
    cell = 128
    for group, out_name in ((NAV_ICONS, "nav-icons-preview.png"),
                            (QUICK_ICONS, "quick-icons-preview.png")):
        rows = (len(group) + 4) // 5
        sheet = Image.new("RGBA", (5 * (cell + pad) + pad, rows * (cell + pad) + pad),
                          (255, 255, 255, 255))
        dr = ImageDraw.Draw(sheet)
        for idx, (name, _) in enumerate(group):
            r, c = divmod(idx, 5)
            icon = render(os.path.join(RES, "drawable", name + ".xml"), cell)
            x = pad + c * (cell + pad)
            y = pad + r * (cell + pad)
            sheet.alpha_composite(icon, (x, y))
            dr.rectangle([x - 6, y - 6, x + cell + 6, y + cell + 6], outline=(220, 216, 230, 255))
        out = os.path.join(ROOT, "tools", out_name)
        sheet.convert("RGB").save(out)
        print("wrote", out, sheet.size)


if __name__ == "__main__":
    main()
