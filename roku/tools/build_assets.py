"""Builds the Roku app's images and fonts from CleanFrame's own assets.

Run from the repo root:  python3 roku/tools/build_assets.py
Needs Pillow and fontTools; marks need Node + Playwright (node roku/tools/marks.mjs).
Everything is sized for Roku's 1920x1080 (FHD) UI and kept small: Roku apps are zipped
and sideloaded, so the whole package stays well under a few MB.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
import json

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'app/assets'
OUT = ROOT / 'roku'
W, H = 1920, 1080
DOCK = (96, 676, 1728, 232)  # x, y, w, h: the Google TV dock's broad geometry
RADIUS = 48

def cover(img, w=W, h=H):
    r = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * r), round(img.height * r)), Image.LANCZOS)
    x, y = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))

def rounded_mask(w, h, r):
    m = Image.new('L', (w * 4, h * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * 4 - 1, h * 4 - 1), r * 4, fill=255)
    return m.resize((w, h), Image.LANCZOS)

walls = json.loads((APP / 'wallpapers/SOURCES.json').read_text())
(OUT / 'images/walls').mkdir(parents=True, exist_ok=True)
(OUT / 'images/glass').mkdir(parents=True, exist_ok=True)
catalogue = []
x, y, w, h = DOCK
mask = rounded_mask(w, h, RADIUS)
for wall in walls:
    wid = wall['id']
    photo = cover(Image.open(APP / f'wallpapers/{wid}.jpg').convert('RGB'))
    if wid == 'misty':  # one photo ships in the package as the offline fallback; the rest load from GitHub
        photo.save(OUT / 'images/walls/misty.jpg', quality=72, optimize=True, progressive=True)
    # Dock glass: CleanFrame's pre-rendered Soft material, cut to the dock with rounded corners,
    # baked over the photo so the corners blend (Roku rectangles have no corner radius).
    soft = Image.open(APP / f'materials/{wid}-study-soft.jpg').convert('RGB').resize((W, H))
    band = photo.crop((x, y, x + w, y + h))
    glass = soft.crop((x, y, x + w, y + h))
    band.paste(glass, (0, 0), mask)
    band.save(OUT / f'images/glass/{wid}.jpg', quality=80, optimize=True)
    catalogue.append({'id': wid, 'name': wall.get('name') or wid.title()})

# Generic pieces: focus plate (9-patch), top scrim for the clock, icons, splash.
def nine_patch(w, h, r, rgba, path):
    body = Image.new('RGBA', (w * 4, h * 4), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, w * 4 - 1, h * 4 - 1), r * 4, fill=rgba)
    body = body.resize((w, h), Image.LANCZOS)
    img = Image.new('RGBA', (w + 2, h + 2), (0, 0, 0, 0))
    img.paste(body, (1, 1))
    d = ImageDraw.Draw(img)
    d.line((1 + r, 0, w - r, 0), fill=(0, 0, 0, 255))          # stretchable x
    d.line((0, 1 + r, 0, h - r), fill=(0, 0, 0, 255))          # stretchable y
    d.line((1 + r, h + 1, w - r, h + 1), fill=(0, 0, 0, 255))  # content x
    d.line((w + 1, 1 + r, w + 1, h - r), fill=(0, 0, 0, 255))  # content y
    img.save(path)
nine_patch(64, 64, 28, (255, 255, 255, 46), OUT / 'images/plate.9.png')
nine_patch(64, 64, 28, (246, 241, 231, 235), OUT / 'images/plate-solid.9.png')

scrim = Image.new('RGBA', (W, 420), (0, 0, 0, 0))
px = scrim.load()
for yy in range(420):
    a = int(70 * (1 - yy / 420) ** 1.6)
    for xx in range(W):
        px[xx, yy] = (8, 12, 14, a)
scrim.save(OUT / 'images/scrim-top.png', optimize=True)

hero = cover(Image.open(APP / 'wallpapers/misty.jpg').convert('RGB'))
hero.resize((W, H)).save(OUT / 'images/splash_fhd.jpg', quality=60, optimize=True)
for name, size in [('icon_focus_fhd.jpg', (540, 405)), ('icon_focus_hd.jpg', (290, 218))]:
    tile = cover(hero, *size).convert('RGBA')
    tile = Image.alpha_composite(tile, Image.new('RGBA', size, (0, 0, 0, 60)))
    font = ImageFont.truetype(str(APP / 'fonts/geist/Geist-Variable.ttf'), size[1] // 6)
    d = ImageDraw.Draw(tile)
    tw = d.textlength('CleanFrame', font=font)
    d.text(((size[0] - tw) / 2, size[1] * .42), 'CleanFrame', font=font, fill=(255, 255, 255, 245))
    tile.convert('RGB').save(OUT / 'images' / name, quality=85)

# Static weights from Geist (OFL) — Roku takes plain TTF files.
for weight, name in [(300, 'Geist-Light'), (500, 'Geist-Medium')]:
    f = TTFont(APP / 'fonts/geist/Geist-Variable.ttf')
    instantiateVariableFont(f, {'wght': weight}, inplace=True)
    f.save(OUT / f'fonts/{name}.ttf')
(OUT / 'fonts/OFL.txt').write_text((APP / 'fonts/geist/OFL.txt').read_text())

(OUT / 'source/walls.json').write_text(json.dumps(catalogue, indent=1) + '\n')
print('walls:', len(catalogue))
