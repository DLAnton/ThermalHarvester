"""Генератор текстур Thermal Harvester (оригинальная пиксель-арт графика в стиле машин Thermal Expansion).

Запуск: python3 tools/gen_textures.py  (нужен Pillow). Пишет PNG в src/main/resources/assets/thermalharvest/textures.
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'thermalharvest', 'textures')

BORDER = (0x38, 0x39, 0x39, 255)
DARK = (0x3b, 0x3b, 0x3b, 255)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def body_shade(x, y):
    """Светло-серый корпус с диагональным затенением (светлее сверху-слева)."""
    v = 0xc4 - (x + y) * 2
    if x == 1 or y == 1:
        v -= 10
    return (v, v, v, 255)


def base_face():
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                im.putpixel((x, y), BORDER)
            else:
                im.putpixel((x, y), body_shade(x, y))
    return im


# Окно с колосьями: x 4..11, y 2..9 (рамка), внутри 5..10 x 3..8
WINDOW = (4, 2, 11, 9)


def draw_window(im, active, frame):
    x0, y0, x1, y1 = WINDOW
    for x in range(x0, x1 + 1):
        im.putpixel((x, y0), DARK)
        im.putpixel((x, y1), DARK)
    for y in range(y0, y1 + 1):
        im.putpixel((x0, y), DARK)
        im.putpixel((x1, y), DARK)
    # фон окна
    for y in range(y0 + 1, y1):
        for x in range(x0 + 1, x1):
            if active:
                g = 0x2a + (y - y0) * 3
                im.putpixel((x, y), (g // 2, g, g // 2 + 6, 255))
            else:
                v = 0x48 + (y - y0) * 2
                im.putpixel((x, y), (v, v, v, 255))
    # три колоса
    stems = [5, 7, 9] if not active else [5, 7, 9]
    sway = [0, 1, 0, -1][frame % 4] if active else 0
    for i, sx in enumerate(stems):
        top = y0 + 1 + (i % 2)
        # стебель
        for y in range(top + 3, y1):
            col = hexc('4caf50') if active else (0x6a, 0x6a, 0x6a, 255)
            im.putpixel((sx, y), col)
        # колос (зерно) — 3 пикселя, со смещением при «ветре»
        for k in range(3):
            y = top + k
            x = sx + (sway if k == 0 else 0)
            x = max(x0 + 1, min(x1 - 1, x))
            if active:
                col = hexc('f2c94c') if k != 1 else hexc('e0a800')
            else:
                col = (0x86, 0x86, 0x86, 255) if k != 1 else (0x7a, 0x7a, 0x7a, 255)
            im.putpixel((x, y), col)
        # ость
        ox = max(x0 + 1, min(x1 - 1, sx + 1 + sway))
        im.putpixel((ox, top), hexc('ffe082') if active else (0x90, 0x90, 0x90, 255))


def draw_lights(im, active, frame):
    on = [hexc('2ecc71'), hexc('27ae60'), hexc('58d68d'), hexc('27ae60')][frame % 4]
    off = hexc('3a463f')
    for y in (4, 6, 8):
        im.putpixel((2, y), on if active else off)
        im.putpixel((13, y), on if active else off)


def draw_reel(im, active, frame):
    """Нижняя полоса — «мотовило» с зубьями; при работе зубья бегут."""
    for x in range(3, 13):
        im.putpixel((x, 11), DARK)
        im.putpixel((x, 13), DARK)
    im.putpixel((2, 12), DARK)
    im.putpixel((13, 12), DARK)
    for x in range(3, 13):
        if active:
            lit = (x + frame) % 3 == 0
            col = hexc('f2c94c') if lit else hexc('8d6e2a')
        else:
            col = (0x6a, 0x6a, 0x6a, 255) if x % 3 == 0 else (0x58, 0x58, 0x58, 255)
        im.putpixel((x, 12), col)


def make_face(active, frame=0):
    im = base_face()
    draw_window(im, active, frame)
    draw_lights(im, active, frame)
    draw_reel(im, active, frame)
    return im


def save(im, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    return path


RED_PULSE = ['c0392b', 'b5341f', 'a32d1c', 'b5341f']
SCAN_ON = hexc('58d68d')
SCAN_DIM = hexc('2f7a4c')
LAMP_PIXELS = [(x, y) for y in (4, 6, 8) for x in (2, 13)]
SLOT_PIXELS = [(x, 12) for x in range(3, 13)]


def draw_lights_sleep(im, frame):
    """Сон: все лампы красные, медленно «дышат»."""
    col = hexc(RED_PULSE[frame % len(RED_PULSE)])
    for p in LAMP_PIXELS:
        im.putpixel(p, col)


def draw_lights_scan(im, frame):
    """Анализ: зелёная «бегущая» лампа сверху вниз (с двух сторон синхронно), остальные притушены."""
    lit = (4, 6, 8)[frame % 3]
    for (x, y) in LAMP_PIXELS:
        im.putpixel((x, y), SCAN_ON if y == lit else SCAN_DIM)


def draw_slot_ready(im, frame):
    """Удобрение заправлено: прорезь ровно светится янтарём, без зубцов."""
    for (x, y) in SLOT_PIXELS:
        im.putpixel((x, y), hexc('c99a2e') if x not in (3, 12) else hexc('a37a22'))


def draw_slot_run(im, frame):
    """Удобрение расходуется: бегущие зубцы."""
    for (x, y) in SLOT_PIXELS:
        im.putpixel((x, y), hexc('f2c94c') if (x + frame) % 3 == 0 else hexc('8d6e2a'))


FULL_PULSE = ['e67e22', 'f39c12', 'e67e22', 'd35400']


def draw_window_full(im, frame):
    """Хранилище переполнено: рамка окна светится оранжевым и медленно пульсирует."""
    col = hexc(FULL_PULSE[frame % len(FULL_PULSE)])
    x0, y0, x1, y1 = WINDOW
    for x in range(x0, x1 + 1):
        im.putpixel((x, y0), col)
        im.putpixel((x, y1), col)
    for y in range(y0, y1 + 1):
        im.putpixel((x0, y), col)
        im.putpixel((x1, y), col)


def draw_window_on(im, frame):
    """Сбор: окно с колосьями (копия области окна активной панели)."""
    full = base_face()
    draw_window(full, True, frame)
    x0, y0, x1, y1 = WINDOW
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), full.getpixel((x, y)))


def overlay(painter, frames, rel, frametime=0):
    """Накладка на лицевую панель: прозрачный лист 16x16, закрашена только своя область (анимация — полосой кадров)."""
    strip = Image.new('RGBA', (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        painter(im, f)
        strip.paste(im, (0, 16 * f))
    save(strip, rel)
    meta = os.path.join(ROOT, rel + '.mcmeta')
    if frames > 1:
        with open(meta, 'w') as fh:
            fh.write('{\n\t"animation": {\n\t\t"frametime": %d\n\t}\n}\n' % frametime)
    elif os.path.exists(meta):
        os.remove(meta)


OBSOLETE = ['harvester_active', 'harvester_scan', 'harvester_sleep', 'harvester_fert', 'harvester_harvest']


def blocks():
    # корпус панели — всё погашено; состояния рисуются независимыми накладками поверх (слой CUTOUT)
    save(make_face(False), 'blocks/harvester_face.png')
    overlay(draw_lights_sleep, 4, 'blocks/overlay/lamps_sleep.png', 10)
    overlay(draw_lights_scan, 3, 'blocks/overlay/lamps_scan.png', 5)
    overlay(draw_window_on, 8, 'blocks/overlay/window.png', 4)
    overlay(draw_window_full, 4, 'blocks/overlay/window_full.png', 10)
    overlay(draw_slot_ready, 1, 'blocks/overlay/slot_ready.png')
    overlay(draw_slot_run, 3, 'blocks/overlay/slot_run.png', 4)
    for name in OBSOLETE:
        for ext in ('.png', '.png.mcmeta'):
            path = os.path.join(ROOT, 'blocks', name + ext)
            if os.path.exists(path):
                os.remove(path)


# ---------------------------------------------------------------- augments
# Расширения в манере Thermal Expansion (по разбору оригинальных иконок TE):
#  * подложка — холодное «стекло»: светлый верх-лево -> тёмный низ-право, рамка #282828 со срезанными углами;
#  * знак ~8-10 пикселей по центру, поля 2-3 пикселя;
#  * ореол узкий (1-2 пикселя) и только подкрашивает подложку в цвет знака, не поднимая яркость;
#  * контур — тёмный оттенок цвета знака (затемнён ещё на четверть тона), а не чёрный.

import math

FRAME = (0x28, 0x28, 0x28, 255)
PLATE_CORNERS = ((1, 1), (14, 1), (1, 14), (14, 14))


def glass_plate():
    im = Image.new('RGBA', (16, 16), FRAME)
    tl, br = (0xe2, 0xee, 0xf0), (0x78, 0x90, 0x98)
    for y in range(1, 15):
        for x in range(1, 15):
            if (x, y) in PLATE_CORNERS:
                continue
            t = min(1.0, max(0.0, ((y - 1) * 0.78 + (x - 1) * 0.22) / 13.0)) ** 0.9
            im.putpixel((x, y), tuple(int(a + (b - a) * t) for a, b in zip(tl, br)) + (255,))
    return im


def with_outline(fill):
    """fill: {(x, y): символ}. Добавляет контур 'k' вокруг знака (4-соседство)."""
    g = [['.'] * 16 for _ in range(16)]
    for (x, y), ch in fill.items():
        g[y][x] = ch
    for y in range(1, 15):
        for x in range(1, 15):
            if g[y][x] == '.' and any((x + dx, y + dy) in fill for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                g[y][x] = 'k'
    return [''.join(r) for r in g]


def darker(c, k=0.75):
    return tuple(int(v * k) for v in c[:3]) + (255,)


def compose_augment_te(rows, palette, glow, outline):
    im = glass_plate()
    glow = hexc(glow)
    pal = dict(palette)
    pal['k'] = pal['F'] = darker(hexc(outline))
    pix = {(x, y): pal[ch] for y, r in enumerate(rows) for x, ch in enumerate(r) if ch != '.'}
    glum = max(1.0, sum(glow[:3]) / 3.0)
    for y in range(1, 15):
        for x in range(1, 15):
            if (x, y) in pix or (x, y) in PLATE_CORNERS:
                continue
            d = min((math.hypot(x - px, y - py) for (px, py) in pix), default=99)
            k = 0.5 if d < 1.5 else (0.26 if d < 2.5 else (0.08 if d < 3.3 else 0.0))
            if k:
                r, g, b, _ = im.getpixel((x, y))
                lum = (r + g + b) / 3.0
                tr, tg, tb = (min(255, c * lum / glum) for c in glow[:3])
                im.putpixel((x, y), (int(r + (tr - r) * k), int(g + (tg - g) * k), int(b + (tb - b) * k), 255))
    for p, c in pix.items():
        im.putpixel(p, c)
    return im


def fill_rect(f, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            f[(x, y)] = ch


def green_tone(x, y):
    """Объёмная заливка: светлее сверху-слева, темнее снизу-справа."""
    s = x + y
    return 'L' if s <= 13 else ('G' if s <= 17 else 'D')


# Расширитель зоны: угловые скобки участка с золотым центром
def range_fill():
    f = {}
    for (cx, cy, dx, dy) in ((3, 3, 1, 1), (12, 3, -1, 1), (3, 12, 1, -1), (12, 12, -1, -1)):
        for i in range(3):
            f[(cx + dx * i, cy)] = green_tone(cx + dx * i, cy)
            f[(cx, cy + dy * i)] = green_tone(cx, cy + dy * i)
    fill_rect(f, 6, 6, 9, 9, 'y')
    for p in ((6, 6), (7, 6), (6, 7)):
        f[p] = 'Y'
    for p in ((9, 9), (8, 9), (9, 8)):
        f[p] = 'O'
    return f


RANGE_PAL = {'L': hexc('8fe39c'), 'G': hexc('39b552'), 'D': hexc('1e7333'),
             'Y': hexc('fff0a0'), 'y': hexc('f2c94c'), 'O': hexc('c9952a')}


# Рециркулятор удобрений: две круговые стрелки и гранула удобрения в центре
def recycler_fill():
    ring = {(x, y) for y in range(16) for x in range(16) if 2.0 <= math.hypot(x + 0.5 - 8, y + 0.5 - 8) <= 3.9}
    gap = {(8, 4), (9, 4), (8, 5), (9, 5)}
    head = {(10, 3), (9, 4), (10, 4), (11, 4)}
    mirror = lambda pts: {(15 - x, 15 - y) for x, y in pts}
    f = {p: green_tone(*p) for p in (ring - gap - mirror(gap)) | head | mirror(head)}
    fill_rect(f, 7, 7, 8, 8, 'y')
    f[(7, 7)] = 'Y'
    f[(8, 8)] = 'O'
    return f


RECYCLER_PAL = {'L': hexc('8fe39c'), 'G': hexc('39b552'), 'D': hexc('1e7333'),
                'Y': hexc('e6f57a'), 'y': hexc('b5d334'), 'O': hexc('7a9a1e')}


# Катализатор роста: колба с зелёным реагентом
def catalyst_fill():
    f = {}
    fill_rect(f, 7, 3, 8, 5, 'W')
    f[(8, 4)] = 'w'
    f[(8, 5)] = 'w'
    body = {6: (6, 9), 7: (5, 10), 8: (5, 10), 9: (4, 11), 10: (4, 11), 11: (4, 11), 12: (5, 10)}
    for y, (a, b) in body.items():
        for x in range(a, b + 1):
            f[(x, y)] = 'W' if y < 8 else ('L' if x + y <= 15 else ('G' if x + y <= 18 else 'D'))
    for p in ((5, 9), (6, 10), (9, 8)):
        f[p] = 'l'
    return f


CATALYST_PAL = {'W': hexc('dfe8ee'), 'w': hexc('b9c6cf'),
                'L': hexc('b9f56a'), 'l': hexc('f4ffd6'), 'G': hexc('6cc23a'), 'D': hexc('3d8a22')}

# Модуль орошения: картридж TE (рамка, стекло, верхняя перемычка с горлом) с окном-каплей воды.
# F — рамка картриджа (цвет контура); углы окна — стекло, чтобы икона не была тяжелее соседних.
IRRIG_ROWS = [
    "................",
    "................",
    "................",
    "....FFFFFFFF....",
    "....FhggggqF....",
    "....FFFLBFFF....",
    "....FgLBBMgF....",
    "....FLBBBBMF....",
    "....FLWBBBMF....",
    "....FLBBBBMF....",
    "....FgBBMMgF....",
    "....FqqqqqqF....",
    "....FFFFFFFF....",
    "................",
    "................",
    "................",
]
IRRIG_PAL = {'h': hexc('eef8fc'), 'g': hexc('c4dde8'), 'q': hexc('86a2b2'),
             'W': hexc('d6f1ff'), 'L': hexc('7fc6f7'), 'B': hexc('3a92e6'), 'M': hexc('1f62b0')}


def items():
    save(compose_augment_te(with_outline(range_fill()), RANGE_PAL, '5ee07a', '1c4a24'),
         'items/augment/harvester_range.png')
    save(compose_augment_te(IRRIG_ROWS, IRRIG_PAL, '7fc6ff', '294450'),
         'items/augment/harvester_irrigation.png')
    save(compose_augment_te(with_outline(recycler_fill()), RECYCLER_PAL, '7be38d', '1c4a24'),
         'items/augment/harvester_recycler.png')
    save(compose_augment_te(with_outline(catalyst_fill()), CATALYST_PAL, 'c6f56b', '3a5a14'),
         'items/augment/harvester_catalyst.png')


# ---------------------------------------------------------------- GUI

PANEL = (0xc6, 0xc6, 0xc6, 255)
WHITE = (255, 255, 255, 255)
SHADOW = (0x55, 0x55, 0x55, 255)
SLOT_BG = (0x8b, 0x8b, 0x8b, 255)
SLOT_DARK = (0x37, 0x37, 0x37, 255)


def panel(d, x0, y0, w, h):
    x1, y1 = x0 + w - 1, y0 + h - 1
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=PANEL)
    # чёрный контур со скруглением
    d.line([x0 + 2, y0, x1 - 3, y0], fill=(0, 0, 0, 255))
    d.line([x0 + 3, y1, x1 - 2, y1], fill=(0, 0, 0, 255))
    d.line([x0, y0 + 2, x0, y1 - 3], fill=(0, 0, 0, 255))
    d.line([x1, y0 + 3, x1, y1 - 2], fill=(0, 0, 0, 255))
    for p in [(x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x1 - 1, y0 + 2), (x0 + 1, y1 - 2), (x0 + 2, y1 - 1), (x1 - 1, y1 - 1)]:
        d.point(p, fill=(0, 0, 0, 255))
    # объём
    d.line([x0 + 2, y0 + 1, x1 - 3, y0 + 1], fill=WHITE)
    d.line([x0 + 1, y0 + 2, x0 + 1, y1 - 3], fill=WHITE)
    d.line([x0 + 2, y0 + 2, x0 + 3, y0 + 2], fill=WHITE)
    d.line([x0 + 3, y1 - 1, x1 - 2, y1 - 1], fill=SHADOW)
    d.line([x1 - 1, y0 + 3, x1 - 1, y1 - 2], fill=SHADOW)
    d.point((x1 - 2, y1 - 2), fill=SHADOW)


def slot(d, x, y):
    """Слот 18x18, (x,y) — позиция предмета (как у Slot)."""
    x0, y0 = x - 1, y - 1
    d.rectangle([x0, y0, x0 + 17, y0 + 17], fill=SLOT_BG)
    d.line([x0, y0, x0 + 16, y0], fill=SLOT_DARK)
    d.line([x0, y0, x0, y0 + 16], fill=SLOT_DARK)
    d.line([x0 + 1, y0 + 17, x0 + 17, y0 + 17], fill=WHITE)
    d.line([x0 + 17, y0 + 1, x0 + 17, y0 + 17], fill=WHITE)


def gui():
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    panel(d, 0, 0, 176, 166)
    # выходная сетка 5x3
    for row in range(3):
        for col in range(5):
            slot(d, 58 + col * 18, 17 + row * 18)
    # слот зарядки и слот удобрения
    slot(d, 8, 53)
    slot(d, 34, 17)
    # рамка шкалы запаса питательности (18x4) под слотом удобрения
    x0, y0 = 33, 36
    d.rectangle([x0, y0, x0 + 17, y0 + 3], fill=SLOT_BG)
    d.line([x0, y0, x0 + 16, y0], fill=SLOT_DARK)
    d.line([x0, y0, x0, y0 + 2], fill=SLOT_DARK)
    d.line([x0 + 1, y0 + 3, x0 + 17, y0 + 3], fill=WHITE)
    d.line([x0 + 17, y0 + 1, x0 + 17, y0 + 3], fill=WHITE)
    d.rectangle([x0 + 1, y0 + 1, x0 + 16, y0 + 2], fill=(0x3a, 0x3a, 0x3a, 255))
    # инвентарь игрока
    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(d, 8 + col * 18, 142)

    # ---- спрайты (правая часть атласа) ----
    # фон бака 18x62 в (176,0): углублённая рамка, как у баков TE
    tx, ty = 176, 0
    d.rectangle([tx, ty, tx + 17, ty + 61], fill=SLOT_BG)
    d.line([tx, ty, tx + 16, ty], fill=SLOT_DARK)
    d.line([tx, ty, tx, ty + 60], fill=SLOT_DARK)
    d.line([tx + 1, ty + 61, tx + 17, ty + 61], fill=WHITE)
    d.line([tx + 17, ty + 1, tx + 17, ty + 61], fill=WHITE)

    # кнопка подсветки зоны: 4 состояния по 16x16 в (176..240, 64)
    # ряд y=64: выкл, выкл+наведение, «пашня», «пашня»+наведение; ряд y=80: «вся зона», «вся зона»+наведение
    for i, (on, hover) in enumerate([(False, False), (False, True), (True, False), (True, True)]):
        draw_zone_button(im, 176 + i * 16, 64, on, hover)
    draw_zone_button(im, 176, 80, True, False, full=True)
    draw_zone_button(im, 192, 80, True, True, full=True)

    # заполнение шкалы питательности 16x2 в (176,100)
    for x in range(16):
        im.putpixel((176 + x, 100), hexc('9be15a'))
        im.putpixel((176 + x, 101), hexc('5aa02c'))
    save(im, 'gui/harvester.png')


def draw_zone_button(im, x0, y0, on, hover, full=False):
    """Кнопка в стиле CoFH: рамка с объёмом, внутри — знак «сигнальная разметка» (чёрно-жёлтые полосы).
    Выкл — серый малый знак; «пашня» — яркий малый знак; «вся зона» — яркий знак на всю кнопку."""
    d = ImageDraw.Draw(im)
    base = (0xb4, 0xb4, 0xb4, 255) if not hover else (0xc8, 0xc8, 0xd8, 255)
    if on:
        base = (0x8e, 0x8e, 0x8e, 255) if not hover else (0xa0, 0xa0, 0xb0, 255)
    d.rectangle([x0, y0, x0 + 15, y0 + 15], fill=base)
    light, dark = (WHITE, SHADOW) if not on else (SHADOW, WHITE)
    d.line([x0, y0, x0 + 14, y0], fill=light)
    d.line([x0, y0, x0, y0 + 14], fill=light)
    d.line([x0 + 1, y0 + 15, x0 + 15, y0 + 15], fill=dark)
    d.line([x0 + 15, y0 + 1, x0 + 15, y0 + 15], fill=dark)
    size = 10 if full else 6
    ix = iy = (16 - size) // 2
    ix += x0
    iy += y0
    yellow = hexc('f2c230') if on else hexc('c9b56a')
    black = hexc('222222') if on else hexc('5a5a5a')
    for yy in range(size):
        for xx in range(size):
            band = ((xx + yy) // 2) % 2
            im.putpixel((ix + xx, iy + yy), yellow if band == 0 else black)
    d.rectangle([ix - 1, iy - 1, ix + size, iy + size], outline=(0x20, 0x20, 0x20, 255))


def mask():
    """Текстура разметки зоны: приглушённые янтарные и графитовые полосы (наклон и масштаб задаются UV в рендере).
    Графитовые полосы полупрозрачнее янтарных, чтобы разметка не «чернила» землю."""
    im = Image.new('RGBA', (16, 16))
    amber = (0xd2, 0x9f, 0x3c, 255)
    graphite = (0x2a, 0x2a, 0x2a, 150)
    for x in range(16):
        col = amber if (x // 4) % 2 == 0 else graphite
        for y in range(16):
            im.putpixel((x, y), col)
    save(im, 'misc/zone_mask.png')


if __name__ == '__main__':
    blocks()
    items()
    gui()
    mask()
    print('textures written to', os.path.abspath(ROOT))
