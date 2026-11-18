"""End-to-end login checks against an emulator with fingerprint 1 already enrolled.

Uses the real Android biometric prompt and emulator fingerprint HAL, not mocked callbacks.
Run after installing the debug APK: python tools/verify_login_device.py
Complete Chrome's first-run setup before running. Set ANDROID_SERIAL if needed.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = shutil.which("adb") or str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
OUT = Path(__file__).resolve().parent / "visual-checks"
OUT.mkdir(exist_ok=True)
PACKAGE = "com.siu.local"
checks = 0


def adb(*args):
    return subprocess.check_output([ADB, *args], timeout=30)


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/siu-login-check.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/siu-login-check.xml"))


def find(label, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = tree()
        for node in root.iter("node"):
            if label.casefold() in (node.get("text", "").casefold(), node.get("content-desc", "").casefold()):
                return node
    raise AssertionError(f"Control not visible: {label}")


def tap(label):
    node = find(label)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))


def check(label, message):
    global checks
    find(label)
    checks += 1
    print("OK:", message, flush=True)


def screenshot(name):
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))


def launch():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")


launch()
check("Usar contraseña", "Biometric prompt appears automatically on cold launch")
adb("emu", "finger", "touch", "2")
check("Usar contraseña", "Unregistered fingerprint keeps authentication locked")
adb("emu", "finger", "touch", "1")
check("Abrir menú", "Enrolled fingerprint opens home")

launch()
tap("Usar contraseña")
check("Contraseña", "Cancel biometric authentication falls back to password")
assert not any(n.get("content-desc") == "Entrar con biometría" for n in tree().iter("node")), "No fingerprint icon should be shown on login"
checks += 1
print("OK: Login has no fingerprint icon", flush=True)
launch()
check("Usar contraseña", "Biometrics opens again automatically on a new launch")
adb("shell", "input", "keyevent", "4")
check("Contraseña", "System Back cancels biometrics without signing in")
tap("Contraseña")
adb("shell", "input", "text", "000000")
adb("shell", "input", "keyevent", "66")
check("Contraseña incorrecta", "Incorrect PIN is rejected through the keyboard")
tap("Aceptar")
tap("Borrar contraseña")
tap("Contraseña")
adb("shell", "input", "text", "154745")
screenshot("login-keyboard")
adb("shell", "input", "keyevent", "66")
check("Abrir menú", "Correct PIN signs in using IME Done")

tap("Abrir menú")
tap("Cerrar sesión")
tap("Cerrar sesión")
check("Contraseña", "Logout returns to locked login")
tap("INICIAR SESIÓN EN OTRA CUENTA")
check("Usuario", "Other-account form is usable")
tap("Usuario")
adb("shell", "input", "text", "DAJ2033135")
tap("Contraseña")
adb("shell", "input", "text", "154745")
adb("shell", "input", "keyevent", "66")
check("Abrir menú", "Configured local account can sign in with username and PIN")

launch()
tap("Usar contraseña")
tap("Política de privacidad")
root = tree()
assert any(n.get("package") == "com.android.chrome" for n in root.iter("node")), "External Chrome browser must open"
checks += 1
print("OK: Privacy link opens Chrome externally", flush=True)
screenshot("login-policy-browser")
adb("shell", "input", "keyevent", "4")
check("Contraseña", "Returning from browser keeps the session locked")
print(f"PASS: {checks} device login checks", flush=True)
