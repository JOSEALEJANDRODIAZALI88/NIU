"""Compare the supplied login references with screenshots from the instrumentation."""
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageCms, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PAIRS = [
    ("photo_2026-11-11_23-15-05.jpg", "login.png", "Acceso"),
    ("photo_2026-11-11_23-15-06 (3).jpg", "login-empty-pin.png", "PIN vacio"),
]
sheet = Image.new("RGB", (1180, 680), "#202020")
draw = ImageDraw.Draw(sheet)
for index, (reference, actual, label) in enumerate(PAIRS):
    for column, (path, heading) in enumerate([
        (ROOT / "referencias" / reference, label + " - referencia"),
        (ROOT / "tools/visual-checks" / actual, label + " - implementado"),
    ]):
        image = Image.open(path)
        if image.info.get("icc_profile"):
            image = ImageCms.profileToProfile(
                image, ImageCms.ImageCmsProfile(BytesIO(image.info["icc_profile"])),
                ImageCms.createProfile("sRGB"), outputMode="RGB")
        image = image.convert("RGB").resize((295, 640), Image.Resampling.LANCZOS)
        x = (index * 2 + column) * 295
        draw.text((x + 12, 13), heading, fill="white")
        sheet.paste(image, (x, 40))
destination = ROOT / "tools/comparisons/login.jpg"
destination.parent.mkdir(exist_ok=True)
sheet.save(destination, quality=95)
print(destination)
