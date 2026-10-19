# -*- coding: utf-8 -*-
"""
App 启动图标 adaptive 前景提取。

思路（避免把白色书页/人形剪影当成背景挖空）：
1. 定位圆角矩形图标区域（四角为浅灰紫背景，与图标底色差异明显）。
2. 用「掩膜归一化高斯模糊」迭代估计图标内部的平滑渐变背景（排除 artwork）。
3. 计算与背景模型的色差 diff，并计算边缘强度图作为「屏障」（artwork 轮廓）。
4. 从图像边界做洪水填充，只能通过 (diff 小 且 非屏障) 的像素；
   未被填充到的像素 = artwork（包括被轮廓包围的白色区域，如书页、人形剪影）。
5. 形态学清理 + 羽化，紧致裁切，等比缩放到 adaptive 安全区并居中到 432x432 透明画布。
"""
import sys
import numpy as np
from PIL import Image, ImageFilter
from collections import deque
from scipy import ndimage  # 若无则退化为纯 numpy 实现

SRC = r"C:\Users\sidey\.workbuddy\clipboard-images\clipboard-2026-10-14T07-51-50-149Z-9614f39a.jpg"
OUT_FG = r"D:\PersonalWorkbench\app\src\main\res\drawable-xxxhdpi\ic_launcher_foreground.png"
OUT_PREVIEW = r"C:\Users\sidey\AppData\Local\Temp\icon_preview2.png"
CANVAS = 432
SAFE_RATIO = 0.62


def find_icon_bbox(arr: np.ndarray) -> tuple[int, int, int, int]:
    """定位圆角矩形图标区域：对整图做重度模糊得到背景模型(含暗角渐变)，
    取与模型差异最大的连通域的 bbox，再内缩模糊半径+阴影余量。"""
    from scipy import ndimage
    img = Image.fromarray(arr.astype(np.uint8))
    blur_radius = 45
    model = np.asarray(img.filter(ImageFilter.GaussianBlur(blur_radius))).astype(np.float32)
    d = np.sqrt(((arr.astype(np.float32) - model) ** 2).sum(axis=2))
    mask = d > 25
    lbl, n = ndimage.label(mask)
    if n == 0:
        h, w, _ = arr.shape
        return 0, 0, w, h
    sizes = ndimage.sum(mask, lbl, range(1, n + 1))
    big = int(np.argmax(sizes)) + 1
    ys, xs = np.where(lbl == big)
    inset = blur_radius + 10 + int(0.02 * max(xs.max() - xs.min(), ys.max() - ys.min()))
    return int(xs.min()) + inset, int(ys.min()) + inset, int(xs.max()) - inset, int(ys.max()) - inset


def estimate_background(rgb: np.ndarray, radius: int = 45, iters: int = 6, tight: float = 12.0) -> np.ndarray:
    """掩膜归一化高斯模糊，迭代估计平滑渐变背景"""
    img = Image.fromarray(rgb.astype(np.uint8))
    blur = img.filter(ImageFilter.GaussianBlur(radius))
    model = np.asarray(blur).astype(np.float32)
    for _ in range(iters):
        diff = np.sqrt(((rgb.astype(np.float32) - model) ** 2).sum(axis=2))
        keep = (diff < tight).astype(np.float32)
        num = np.asarray(Image.fromarray((rgb.astype(np.float32) * keep[..., None]).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))).astype(np.float32)
        den = np.asarray(Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))).astype(np.float32) / 255.0
        model = num / np.maximum(den[..., None], 1e-3)
        model = np.clip(model, 0, 255)
    return model


def bfs_from_border(passable: np.ndarray) -> np.ndarray:
    """从四周边界出发，在 passable 像素上做 4 连通 BFS，返回可达区域。"""
    ch, cw = passable.shape
    reachable = np.zeros((ch, cw), dtype=bool)
    dq = deque()
    for x in range(cw):
        for y in (0, ch - 1):
            if passable[y, x] and not reachable[y, x]:
                reachable[y, x] = True
                dq.append((y, x))
    for y in range(ch):
        for x in (0, cw - 1):
            if passable[y, x] and not reachable[y, x]:
                reachable[y, x] = True
                dq.append((y, x))
    while dq:
        y, x = dq.popleft()
        if y > 0 and passable[y - 1, x] and not reachable[y - 1, x]:
            reachable[y - 1, x] = True; dq.append((y - 1, x))
        if y < ch - 1 and passable[y + 1, x] and not reachable[y + 1, x]:
            reachable[y + 1, x] = True; dq.append((y + 1, x))
        if x > 0 and passable[y, x - 1] and not reachable[y, x - 1]:
            reachable[y, x - 1] = True; dq.append((y, x - 1))
        if x < cw - 1 and passable[y, x + 1] and not reachable[y, x + 1]:
            reachable[y, x + 1] = True; dq.append((y, x + 1))
    return reachable


def main() -> None:
    arr = np.asarray(Image.open(SRC).convert("RGB"))
    x0, y0, x1, y1 = find_icon_bbox(arr)
    print("icon bbox:", x0, y0, x1, y1, "size:", x1 - x0, y1 - y0)
    # 略微内缩，避免把图标外部灰底/圆角边缘纳入
    pad = int(min(x1 - x0, y1 - y0) * 0.02)
    crop = arr[y0 + pad:y1 - pad, x0 + pad:x1 - pad].astype(np.float32)
    ch, cw, _ = crop.shape

    bg_model = estimate_background(crop)
    diff = np.sqrt(((crop - bg_model) ** 2).sum(axis=2))

    gray = crop.mean(axis=2)
    gy, gx = np.gradient(gray)
    edge = np.sqrt(gx ** 2 + gy ** 2)
    edge_barrier = edge > 14.0
    # 屏障膨胀 1px，防止洪水填充穿过轮廓缝隙
    edge_barrier = np.asarray(Image.fromarray((edge_barrier * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))) > 127

    # ---- pass1: 背景色差 + 边缘屏障，从边界洪水填充 ----
    # 与背景模型接近的像素可通过；图案内部被清晰轮廓(屏障)包围，填充无法到达 => 保留。
    # 严格阈值：避免填充穿过柔和过渡带（如工作台投影）在图案内部形成漏填通道。
    THRESH = 13.0
    passable = (diff < THRESH) & (~edge_barrier)
    # 裁剪边缘可能切到图标描边，强制视为背景，避免生成一圈“边框”
    m = 8
    passable[:m, :] = True; passable[-m:, :] = True
    passable[:, :m] = True; passable[:, -m:] = True

    reachable = bfs_from_border(passable)
    fg = (~reachable)

    # ---- pass2: 从边界清除「浅色低饱和」的柔和投影/晕影 ----
    # 书页等浅色图案内部被轮廓屏障包围，二次填充同样无法到达，因此得以保留。
    lum = crop.mean(axis=2)
    sat = crop.max(axis=2) - crop.min(axis=2)
    passable2 = (lum > 196.0) & (sat < 42.0) & (~edge_barrier)
    passable2[:m, :] = True; passable2[-m:, :] = True
    passable2[:, :m] = True; passable2[:, -m:] = True
    reach2 = bfs_from_border(passable2)
    fg = fg & (~reach2)

    # ---- pass3: 裁掉图案轮廓底部的投影残余 ----
    # 找最强 artwork 像素（深色或高饱和）所在的最低行，把其下方的残留投影一并清除。
    strong = (sat > 70.0) | (lum < 120.0)
    rows = np.where((fg & strong).sum(axis=1) > 8)[0]
    if rows.size > 0:
        y_bottom = int(rows.max())
        fg[y_bottom + 3:, :] = False
    # 形态学：闭合填平（把底部投影残余填成实心轮廓，避免锯齿/空洞）+ 去掉极小碎块
    fg = ndimage.binary_closing(fg, structure=np.ones((3, 3)), iterations=7)
    fg = ndimage.binary_opening(fg, structure=np.ones((3, 3)), iterations=1)
    lbl, n = ndimage.label(fg)
    if n > 0:
        sizes = ndimage.sum(fg, lbl, range(1, n + 1))
        keep_ids = [i + 1 for i, s in enumerate(sizes) if s >= 60]
        fg = np.isin(lbl, keep_ids).astype(np.uint8)
    print("fg px:", int(fg.sum()), "components kept:", len(keep_ids) if n else 0)

    # 轮廓平滑：模糊后重新二值化，得到圆润边缘
    soft = Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.5))
    fg = (np.asarray(soft) > 128).astype(np.uint8)

    # 羽化 alpha
    alpha = Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.0))
    alpha_np = np.asarray(alpha).astype(np.float32) / 255.0
    alpha_np = np.clip((alpha_np - 0.3) / 0.6, 0, 1)  # 收紧边缘，去掉半透明晕影

    rgba = np.dstack([crop.astype(np.uint8), (alpha_np * 255).astype(np.uint8)])
    fg_img = Image.fromarray(rgba, "RGBA")

    # 紧致裁切
    bbox = fg_img.getbbox()
    fg_img = fg_img.crop(bbox)
    print("tight bbox:", bbox, "->", fg_img.size)

    # 等比缩放至安全区并居中
    target = int(CANVAS * SAFE_RATIO)
    scale = target / max(fg_img.width, fg_img.height)
    new_size = (max(1, round(fg_img.width * scale)), max(1, round(fg_img.height * scale)))
    fg_img = fg_img.resize(new_size, Image.LANCZOS)
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.paste(fg_img, ((CANVAS - new_size[0]) // 2, (CANVAS - new_size[1]) // 2), fg_img)
    canvas.save(OUT_FG)
    print("saved", OUT_FG, canvas.size)

    # 预览：叠到中性灰 + 叠到白底，便于检查
    for name, bgcol in (("gray", (128, 128, 128, 255)), ("white", (255, 255, 255, 255))):
        prev = Image.new("RGBA", (CANVAS, CANVAS), bgcol)
        prev.alpha_composite(canvas)
        prev.resize((256, 256), Image.LANCZOS).save(OUT_PREVIEW.replace(".png", f"_{name}.png"))
    print("preview saved:", OUT_PREVIEW.replace(".png", "_gray.png"), OUT_PREVIEW.replace(".png", "_white.png"))


if __name__ == "__main__":
    main()
