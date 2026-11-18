"""Add missing Spanish glyphs using Rookie's own letter outlines.

Requires fonttools: python -m pip install fonttools
Preserves the source font; generates RookieSpanish.ttf deterministically.
"""
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.transformPen import TransformPen


def prepare_login_font():
    assets = Path(__file__).resolve().parents[1] / "app/src/main/assets/fonts"
    font = TTFont(assets / "RookieRegular.ttf", recalcTimestamp=False)
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    for base, accented in zip("NnAaEeIiOoUuUu", "ÑñÁáÉéÍíÓóÚúÜü"):
        if ord(accented) in cmap:
            continue
        name = "uni%04X" % ord(accented)
        letter = cmap[ord(base)]
        original = font["glyf"][letter]
        original.recalcBounds(font["glyf"])
        center = (original.xMin + original.xMax) / 2
        top = original.yMax + 45
        pen = TTGlyphPen(glyphs)
        glyphs[letter].draw(pen)
        if accented in "Ññ":
            # A hand-drawn wave above the original N, matching its rounded strokes.
            pen.moveTo((center-165, top+20))
            pen.qCurveTo((center-105, top+115), (center-15, top+55))
            pen.qCurveTo((center+80, top-10), (center+145, top+85))
            pen.lineTo((center+175, top+55))
            pen.qCurveTo((center+95, top-65), (center-25, top+10))
            pen.qCurveTo((center-95, top+55), (center-130, top-5))
            pen.closePath()
        elif accented in "Üü":
            glyphs["uni0308"].draw(TransformPen(pen, (1, 0, 0, 1, center+192, top-646)))
        else:
            glyphs["tonos"].draw(TransformPen(pen, (1, 0, 0, 1, center-96, top-500)))
        font["glyf"][name] = pen.glyph()
        font["hmtx"][name] = font["hmtx"][letter]
        for table in font["cmap"].tables:
            if table.isUnicode():
                table.cmap[ord(accented)] = name
    font.setGlyphOrder(font["glyf"].glyphOrder)
    if "DSIG" in font:
        del font["DSIG"]
    for record in font["name"].names:
        if record.nameID in (1, 3, 4, 6):
            text = "RookieSpanish" if record.nameID == 6 else "Rookie Spanish"
            record.string = text.encode(record.getEncoding())
    font.save(assets / "RookieSpanish.ttf")
    print("Prepared RookieSpanish.ttf (Spanish letters use Rookie outlines).")


if __name__ == "__main__":
    prepare_login_font()
