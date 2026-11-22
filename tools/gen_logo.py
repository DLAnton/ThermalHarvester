"""Логотип Thermal Harvester: изометрический блок Сборщика из текстур мода (tools/gen_textures.py).

Каждый тексель рисуется ровным многоугольником с 4-кратным сглаживанием, фон прозрачный.
Картинка крупная (меню модов Cleanroom только уменьшает её, без сглаживания) и чуть шире, чем высока:
квадратный logoFile Cleanroom подставил бы вместо значка в списке, а значок задан через iconItem.

Запуск: python3 tools/gen_logo.py  (нужен Pillow). Пишет
  src/main/resources/assets/thermalharvest/logo.png  (logoFile в mcmod.info)
  docs/images/logo.png                               (README)
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import gen_textures as tx  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_MOD = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'thermalharvest', 'logo.png')
OUT_DOCS = os.path.join(HERE, '..', 'docs', 'images', 'logo.png')

W, H = 520, 512      # итоговый размер
SS = 4               # сглаживание краёв
MARGIN = 6           # поля сверху и снизу

SHADE = {'top': 1.0, 'left': 0.92, 'right': 0.72}


def side_face():
    """Боковина: корпус, разделённый на две панели (как на прежнем логотипе)."""
    im = tx.base_face()
    for x in range(1, 15):
        im.putpixel((x, 7), tx.BORDER)
        v = tx.body_shade(x, 8)[0] + 12
        im.putpixel((x, 8), (v, v, v, 255))
    return im


def shade(c, k):
    return (round(c[0] * k), round(c[1] * k), round(c[2] * k), 255)


def draw_face(d, tex, origin, e1, e2, k):
    for ty in range(16):
        for tx_ in range(16):
            u0, u1, v0, v1 = tx_ / 16, (tx_ + 1) / 16, ty / 16, (ty + 1) / 16
            pts = [(origin[0] + e1[0] * u + e2[0] * v, origin[1] + e1[1] * u + e2[1] * v)
                   for u, v in ((u0, v0), (u1, v0), (u1, v1), (u0, v1))]
            col = shade(tex.getpixel((tx_, ty)), k)
            d.polygon(pts, fill=col, outline=col)


def render():
    w, h = W * SS, H * SS
    r = (h - 2 * MARGIN * SS) / 2          # «радиус» шестиугольника куба
    c = math.cos(math.radians(30))
    cx, cy = w / 2, h / 2
    top = (cx, cy - r)
    ur, lr = (cx + r * c, cy - r / 2), (cx + r * c, cy + r / 2)
    bot = (cx, cy + r)
    ll, ul = (cx - r * c, cy + r / 2), (cx - r * c, cy - r / 2)
    ctr = (cx, cy)

    def vec(a, b):
        return (b[0] - a[0], b[1] - a[1])

    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    draw_face(d, tx.base_face(), ul, vec(ul, top), vec(ul, ctr), SHADE['top'])
    draw_face(d, tx.make_face(True), ul, vec(ul, ctr), vec(ul, ll), SHADE['left'])
    draw_face(d, side_face(), ctr, vec(ctr, ur), vec(ctr, bot), SHADE['right'])
    return im.resize((W, H), Image.LANCZOS)


if __name__ == '__main__':
    logo = render()
    for path in (OUT_MOD, OUT_DOCS):
        logo.save(path, optimize=True)
        print('written', os.path.abspath(path), logo.size)
