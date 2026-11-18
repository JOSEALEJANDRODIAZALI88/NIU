"""Reproduce both bundled portraits from the supplied physical-card photograph."""
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageCms, ImageOps


def prepare_card_portraits():
    root = Path(__file__).resolve().parents[1]
    source = root / "recursos/tarjeta fisica/photo_2026-10-29_10-34-15.jpg"
    assets = root / "app/src/main/assets"
    with Image.open(source) as original:
        photo = ImageOps.exif_transpose(original)
        profile = photo.info.get("icc_profile")
        if profile:
            photo = ImageCms.profileToProfile(
                photo, ImageCms.ImageCmsProfile(BytesIO(profile)),
                ImageCms.createProfile("sRGB"), outputMode="RGB")
        # Square framing inside the printed photo: no border, name or card artwork.
        portrait = photo.convert("RGB").crop((742, 130, 1150, 538))
        portrait.save(assets / "portrait.jpg", quality=95, subsampling=0)
        portrait.save(assets / "portrait_carnet.png")


if __name__ == "__main__":
    prepare_card_portraits()
    print("Physical-card portraits prepared.")
