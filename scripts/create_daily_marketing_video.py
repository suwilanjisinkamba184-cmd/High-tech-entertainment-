#!/usr/bin/env python3
"""Render an original, branded HTE motion-graphics promo without paid AI APIs."""
from datetime import datetime, timezone
from pathlib import Path
import math
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output"
OUT.mkdir(exist_ok=True)
now = datetime.now(timezone.utc)
date_key = now.strftime("%Y-%m-%d")
day = int(now.strftime("%j"))
W, H, FPS, SECONDS = 720, 1280, 24, 10
FRAMES = FPS * SECONDS
logo_svg = ROOT / "high-tech-logo.svg"
logo_png = OUT / "hte-logo.png"
video_path = OUT / f"hte-daily-promo-{date_key}.mp4"
caption_path = OUT / f"hte-daily-caption-{date_key}.txt"
SITE = "https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/"

cairosvg.svg2png(url=str(logo_svg), write_to=str(logo_png), output_width=420, output_height=420)
logo = Image.open(logo_png).convert("RGBA")
logo.thumbnail((310, 310))

font_bold_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
font_regular_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
def font(size, bold=True):
    return ImageFont.truetype(font_bold_path if bold else font_regular_path, size)

themes = [
    ("MUSIC", "BEATS", "PRODUCTION", "Your sound. Your story."),
    ("CREATE", "RECORD", "ELEVATE", "Built around the power of sound."),
    ("MUSIC PRODUCTION", "ARTIST DEVELOPMENT", "DIGITAL PROMOTION", "Music. Beats. Production. Beyond."),
    ("FIND YOUR SOUND", "SHARE YOUR VISION", "MAKE IT REAL", "Discover High-Tech Entertainment."),
    ("FROM THE STUDIO", "TO YOUR SPEAKERS", "TO YOUR WORLD", "The sound of new possibilities."),
    ("CREATIVE SOUND", "BOLD IDEAS", "REAL MUSIC", "Connect with High-Tech Entertainment."),
    ("YOUR NEXT SOUND", "STARTS HERE", "HIGH-TECH ENTERTAINMENT", "Music. Beats. Production. Beyond."),
]
a, b, c, tagline = themes[(day - 1) % len(themes)]
captions = [
    "Music. Beats. Production. Beyond. Discover High-Tech Entertainment and connect with the team.",
    "Your sound. Your story. Explore High-Tech Entertainment: music production, beats and creative services.",
    "From creative ideas to finished sound. Discover High-Tech Entertainment.",
    "Looking for your next sound? Explore High-Tech Entertainment and connect with our team.",
    "Music, production and creative energy. Visit High-Tech Entertainment online.",
    "Make room for new sound. Discover High-Tech Entertainment.",
    "High-Tech Entertainment — Music. Beats. Production. Beyond. Explore our official website.",
]
caption = captions[(day - 1) % len(captions)]
caption_path.write_text(caption + "\n" + SITE, encoding="utf-8")

cmd = [
    "ffmpeg", "-y", "-loglevel", "error",
    "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
    "-r", str(FPS), "-i", "-", "-an",
    "-c:v", "libx264", "-preset", "veryfast", "-crf", "21",
    "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(video_path)
]
proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)

def centered(draw, text, y, fnt, fill, spacing=0):
    box = draw.textbbox((0, 0), text, font=fnt, stroke_width=0)
    draw.text(((W - (box[2] - box[0])) / 2, y), text, font=fnt, fill=fill)

try:
    for frame in range(FRAMES):
        t = frame / FPS
        phase = frame / max(1, FRAMES - 1)
        base = Image.new("RGB", (W, H), (4, 5, 7))
        # Subtle vertical graphite gradient.
        gradient = Image.new("RGB", (1, H))
        gd0 = ImageDraw.Draw(gradient)
        for y in range(H):
            lift = int(9 * (1 - y / H))
            gd0.point((0, y), fill=(5 + lift // 3, 6 + lift // 3, 8 + lift // 2))
        base = gradient.resize((W, H))
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        sweep_x = int(-220 + (W + 440) * phase)
        gd.ellipse((sweep_x - 260, 250, sweep_x + 260, 770), fill=(173, 112, 26, 42))
        glow = glow.filter(ImageFilter.GaussianBlur(105))
        base = Image.alpha_composite(base.convert("RGBA"), glow)
        d = ImageDraw.Draw(base)
        # Fine gold orbital arcs and animated sweep.
        d.arc((55, 180, 665, 790), start=195, end=345, fill=(177, 130, 51, 105), width=2)
        d.arc((90, 215, 630, 755), start=15, end=165, fill=(190, 196, 202, 55), width=1)
        line_y = int(805 + 16 * math.sin(phase * math.pi * 2))
        d.line((70, line_y, 650, line_y), fill=(206, 162, 75, 150), width=2)
        # Floating particles, deterministic and smooth.
        for i in range(34):
            x = int((i * 83 + frame * (1 + i % 3)) % W)
            y = int((i * 137 + frame * (2 + i % 2)) % H)
            r = 1 + (i % 3)
            d.ellipse((x-r, y-r, x+r, y+r), fill=(210, 170, 93, 90))
        # Logo reveal and branded typography.
        bob = int(5 * math.sin(phase * math.pi * 2))
        base.alpha_composite(logo, ((W - logo.width)//2, 205 + bob))
        centered(d, "HIGH-TECH ENTERTAINMENT", 555, font(25), (231, 205, 151, 255))
        # Three rotating campaign lines.
        if t < 3.0:
            lines = [a, b, c]
            for idx, line in enumerate(lines):
                centered(d, line, 700 + idx * 67, font(43), (246, 225, 181, 255))
        elif t < 6.3:
            centered(d, tagline.upper(), 760, font(31), (239, 214, 159, 255))
            centered(d, "MUSIC • BEATS • PRODUCTION", 835, font(21), (201, 207, 213, 255))
        else:
            centered(d, "DISCOVER OUR WORLD", 720, font(34), (244, 222, 177, 255))
            centered(d, "VISIT OUR OFFICIAL WEBSITE", 790, font(20), (197, 202, 208, 255))
            centered(d, "HIGH-TECH ENTERTAINMENT", 850, font(24), (226, 190, 116, 255))
        centered(d, "MUSIC. BEATS. PRODUCTION. BEYOND.", 1110, font(17), (179, 184, 190, 255))
        centered(d, "ZAMBIA  •  GLOBAL VIBES", 1150, font(14, False), (150, 153, 158, 255))
        proc.stdin.write(base.convert("RGB").tobytes())
finally:
    proc.stdin.close()
    code = proc.wait()
    if code != 0:
        raise SystemExit(f"ffmpeg exited with status {code}")

if not video_path.exists() or video_path.stat().st_size < 100_000:
    raise SystemExit("Video output is missing or unexpectedly small.")
print(f"VIDEO={video_path}")
print(f"CAPTION={caption_path}")
print(f"SIZE={video_path.stat().st_size}")
