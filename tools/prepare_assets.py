"""Reproduce the bundled artwork from the user-supplied reference APK.

Only explicitly selected images/fonts are copied, never code or APK configuration.
Requires Pillow for the portrait crop; does not change user data or Gradle files.
"""
from pathlib import Path
from zipfile import ZipFile
from prepare_card_portraits import prepare_card_portraits
from prepare_login_font import prepare_login_font

root = Path(__file__).resolve().parents[1]
main = root / 'app/src/main'
assets = main / 'assets'
resources = main / 'res'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)

with ZipFile(root / 'recursos/SIU_1.82_APKPure.apk') as apk:
    for font in ('FontAwesomeSolid.otf', 'OpenSans-Regular.ttf', 'OpenSans-Semibold.ttf',
                 'Kontana.ttf', 'RookieRegular.ttf'):
        write(assets / 'fonts' / font, apk.read('assets/' + font))
    artwork = ('bandera_bolivia', 'logo', 'logo_actual_small', 'logo_carnet',
               'logo_untext', 'logo_varios', 'materia_mensaje',
               'mensaje_abierto', 'mensaje_cerrado', 'mensaje_pendiente',
               'univalle_login_imagen', 'univalle_login_imagen_oscuro',
               'univalle_papel', 'usuario')
    for name in artwork:
        write(assets / 'artwork' / (name + '.png'), apk.read('res/drawable/' + name + '.png'))
    for density in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
        for name in ('icon_social', 'icon_materias', 'icon_academica', 'icon_recursos', 'icon_tips'):
            write(resources / ('drawable-' + density) / (name + '.png'),
                  apk.read('res/drawable-' + density + '-v4/' + name + '.png'))
        for name in ('appicon', 'appicon_background', 'appicon_foreground'):
            write(resources / ('mipmap-' + density) / (name + '.png'),
                  apk.read('res/mipmap-' + density + '-v4/' + name + '.png'))

prepare_card_portraits()
prepare_login_font()
print('Original artwork, launcher layers, fonts and reference portraits prepared.')
