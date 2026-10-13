"""Compose the documentation images: system bars, device frames, hero banner, branding comparison.

Usage: python tools/screenshots/compose.py <repo> <web-dir>
Inputs: app/build/screenshots (Roborazzi) and <web-dir>/{demo,status}.png (render_web.mjs).
Fonts: Roboto from FONTS_DIR (default C:/Windows/Fonts/).
"""
import os
import pathlib
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

repo = pathlib.Path(sys.argv[1])
web = pathlib.Path(sys.argv[2])
raw = repo / "app" / "build" / "screenshots"
out = repo / "docs" / "images"
out.mkdir(parents=True, exist_ok=True)

FONTS = os.environ.get("FONTS_DIR", "C:/Windows/Fonts/").rstrip("/") + "/"
DP = 3  # rendered at xxhdpi
STATUS_H = 24 * DP
NAV_H = 20 * DP


def font(name, size):
    return ImageFont.truetype(FONTS + name, size)


def load(path):
    return Image.open(path).convert("RGB")


# --------------------------------------------------------------------------- screen composition

def toolbar_bottom(frame):
    """First row of the (empty, white) web area: white at both edges, unlike toolbar text."""
    px = frame.load()
    for y in range(1, frame.height):
        if px[4, y] == px[frame.width - 5, y] == px[frame.width // 2, y] == (255, 255, 255):
            return y
    raise ValueError("web area not found")


def with_web(frame, page, top):
    result = frame.copy()
    result.paste(page.crop((0, 0, frame.width, frame.height - top)), (0, top))
    return result


def with_web_under_scrim(frame, page, top):
    """Replace the dimmed empty web area (behind a bottom sheet) with the equally dimmed page."""
    gray = frame.getpixel((frame.width // 2, top + 40))
    factor = gray[0] / 255
    region = frame.crop((0, top, frame.width, frame.height))
    diff = ImageChops.difference(region, Image.new("RGB", region.size, gray)).convert("L")
    mask = diff.point(lambda v: 255 if v <= 3 else 0)
    dimmed = Image.blend(Image.new("RGB", region.size, (0, 0, 0)), page.crop((0, 0, region.width, region.height)), factor)
    region.paste(dimmed, (0, 0), mask)
    result = frame.copy()
    result.paste(region, (0, top))
    return result


def luminance(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def with_system_bars(screen):
    """Adds a status bar (continuing the top row's colors) and a gesture navigation bar."""
    w, h = screen.size
    top = screen.crop((0, 0, w, 1)).resize((w, STATUS_H))
    bottom = screen.crop((0, h - 1, w, h)).resize((w, NAV_H))
    canvas = Image.new("RGB", (w, h + STATUS_H + NAV_H))
    canvas.paste(top, (0, 0))
    canvas.paste(screen, (0, STATUS_H))
    canvas.paste(bottom, (0, STATUS_H + h))
    draw = ImageDraw.Draw(canvas)

    icon = (255, 255, 255) if luminance(top.resize((1, 1)).getpixel((0, 0))) < 150 else (32, 33, 36)
    cy = STATUS_H // 2
    draw.text((60, cy), "9:41", font=font("Roboto-Medium.ttf", 38), fill=icon, anchor="lm")
    # Wi-Fi fan
    wx = w - 150
    draw.pieslice([wx - 30, cy - 18, wx + 30, cy + 42], 225, 315, fill=icon)
    # Battery (vertical, 80 %)
    bx = w - 80
    draw.rounded_rectangle([bx - 13, cy - 19, bx + 13, cy + 21], radius=5, outline=icon, width=4)
    draw.rectangle([bx - 5, cy - 25, bx + 5, cy - 19], fill=icon)
    draw.rectangle([bx - 7, cy - 10, bx + 7, cy + 15], fill=icon)

    pill = (255, 255, 255) if luminance(bottom.resize((1, 1)).getpixel((0, 0))) < 150 else (60, 62, 66)
    py = h + STATUS_H + NAV_H // 2
    draw.rounded_rectangle([w // 2 - 66, py - 7, w // 2 + 66, py + 7], radius=7, fill=pill)
    return canvas


def device(screen, radius=78, bezel=30):
    w, h = screen.size
    W, H = w + 2 * bezel, h + 2 * bezel
    body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(body)
    draw.rounded_rectangle([0, 0, W - 1, H - 1], radius=radius + bezel, fill=(27, 28, 32, 255))
    draw.rounded_rectangle([4, 4, W - 5, H - 5], radius=radius + bezel - 4, outline=(74, 77, 84, 255), width=3)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    body.paste(screen, (bezel, bezel), mask)
    cx = W // 2
    draw.ellipse([cx - 15, bezel + STATUS_H // 2 - 15, cx + 15, bezel + STATUS_H // 2 + 15], fill=(16, 16, 18, 255))
    return body


def with_shadow(img, blur=36, offset=(0, 26), opacity=95, margin=90):
    W, H = img.size
    canvas = Image.new("RGBA", (W + 2 * margin, H + 2 * margin), (0, 0, 0, 0))
    silhouette = Image.new("RGBA", img.size, (0, 0, 0, 0))
    silhouette.putalpha(img.split()[3].point(lambda a: opacity if a > 0 else 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow.alpha_composite(silhouette, (margin + offset[0], margin + offset[1]))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(img, (margin, margin))
    return canvas


def framed(screen):
    return with_shadow(device(with_system_bars(screen)))


def save(img, name, width=None):
    if width:
        img = img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
    img.save(out / name, optimize=True)
    print(name, img.size)
    return img


# --------------------------------------------------------------------------- screens

demo_frame = load(raw / "browser-demo-frame.png")
top = toolbar_bottom(demo_frame)
screens = {
    "launcher": load(raw / "launcher.png"),
    "launcher-branded": load(raw / "launcher-branded.png"),
    "browser": with_web(demo_frame, load(web / "demo.png"), top),
    "menu": with_web_under_scrim(load(raw / "browser-menu-frame.png"), load(web / "demo.png"), top),
    "error": with_web(load(raw / "browser-wms-frame.png"), load(web / "status.png"), top),
    "settings": load(raw / "settings.png"),
    "profile-editor": load(raw / "profile-editor.png"),
    "color-picker": load(raw / "color-picker.png"),
}
devices = {}
for name, screen in screens.items():
    devices[name] = framed(screen)
    save(devices[name], f"screen-{name}.png", width=520)


# --------------------------------------------------------------------------- hero banner

def gradient(size, stops):
    """Diagonal gradient through the given RGB stops."""
    w, h = size
    small = Image.new("RGB", (256, 1))
    for x in range(256):
        t = x / 255 * (len(stops) - 1)
        i = min(int(t), len(stops) - 2)
        f = t - i
        small.putpixel((x, 0), tuple(round(a + (b - a) * f) for a, b in zip(stops[i], stops[i + 1])))
    band = small.resize((w + h, 1)).resize((w + h, h))
    img = Image.new("RGB", size)
    for y in range(h):
        img.paste(band.crop((y, y, y + w, y + 1)), (0, y))
    return img


def glow(canvas, center, radius, color, alpha):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(
        [center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius], fill=color + (alpha,)
    )
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius // 2)))


def phone(name, height, angle=0):
    img = devices[name]
    img = img.resize((round(img.width * height / img.height), height), Image.LANCZOS)
    return img.rotate(angle, resample=Image.BICUBIC, expand=True) if angle else img


def chip(canvas, xy, text, fnt):
    """Translucent pill; drawn on its own layer so alpha blends with the background."""
    x, y = xy
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    tw = draw.textlength(text, font=fnt)
    draw.rounded_rectangle([x, y, x + tw + 44, y + 52], radius=26, fill=(255, 255, 255, 38), outline=(255, 255, 255, 80), width=2)
    draw.text((x + 22, y + 26), text, font=fnt, fill=(255, 255, 255, 255), anchor="lm")
    canvas.alpha_composite(layer)
    return x + tw + 44 + 14


W, H = 1800, 960
hero = gradient((W, H), [(9, 36, 92), (13, 71, 161), (0, 121, 107)]).convert("RGBA")
glow(hero, (1350, 260), 420, (120, 180, 255), 70)
glow(hero, (300, 900), 380, (0, 200, 170), 55)
dots = Image.new("RGBA", hero.size, (0, 0, 0, 0))
dd = ImageDraw.Draw(dots)
for gx in range(30, W, 46):
    for gy in range(30, H, 46):
        dd.ellipse([gx - 2, gy - 2, gx + 2, gy + 2], fill=(255, 255, 255, 18))
hero.alpha_composite(dots)

back_left = phone("launcher", 700, angle=7)
back_right = phone("menu", 700, angle=-7)
front = phone("browser", 830)
hero.alpha_composite(back_left, (980, 150))
hero.alpha_composite(back_right, (1370, 150))
hero.alpha_composite(front, (1135, 80))

text_layer = Image.new("RGBA", hero.size, (0, 0, 0, 0))
draw = ImageDraw.Draw(text_layer)
draw.text((110, 300), "EnterpriseBrowser", font=font("Roboto-Black.ttf", 96), fill=(255, 255, 255, 255))
draw.text((114, 420), "The kiosk browser for Zebra & Honeywell", font=font("Roboto-Medium.ttf", 44), fill=(255, 255, 255, 230))
draw.text((114, 486), "Scanner · Kiosk · MDM provisioning · JavaScript API", font=font("Roboto-Regular.ttf", 32), fill=(255, 255, 255, 175))
hero.alpha_composite(text_layer)
x = 114
for label in ["DataWedge", "Managed config", "Lock task", "Keyboard wedge"]:
    x = chip(hero, (x, 580), label, font("Roboto-Medium.ttf", 27))
save(hero.convert("RGB"), "hero.png", width=1400)


# --------------------------------------------------------------------------- branding comparison

B = Image.new("RGBA", (1600, 1040), (244, 246, 251, 255))
glow(B, (800, 520), 520, (13, 71, 161), 22)
left = phone("launcher", 880)
right = phone("launcher-branded", 880)
B.alpha_composite(left, (200, 70))
B.alpha_composite(right, (1600 - 200 - right.width, 70))
bd = ImageDraw.Draw(B)
cx, cy = 800, 520
bd.ellipse([cx - 58, cy - 58, cx + 58, cy + 58], fill=(13, 71, 161, 255))
bd.polygon([(cx - 24, cy - 26), (cx + 30, cy), (cx - 24, cy + 26)], fill=(255, 255, 255, 255))
label_font = font("Roboto-Medium.ttf", 34)
bd.text((200 + left.width // 2, 1000), "Default", font=label_font, fill=(68, 71, 78, 255), anchor="mm")
bd.text((1600 - 200 - right.width // 2, 1000), "Custom branding via configuration", font=label_font, fill=(68, 71, 78, 255), anchor="mm")
save(B.convert("RGB"), "branding.png", width=1200)
