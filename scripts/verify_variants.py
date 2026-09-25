"""Run after assemblePhoneDebug / assembleUnsupportedDebug / assembleMediaDebug / assemblePhoneRelease."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
android = "{http://schemas.android.com/apk/res/android}"
for variant in ("phoneDebug", "phoneRelease", "unsupportedDebug", "mediaDebug"):
    manifest = root / f"app/build/intermediates/merged_manifests/{variant}/process{variant[0].upper() + variant[1:]}Manifest/AndroidManifest.xml"
    tree = ET.parse(manifest)
    auto = [s for s in tree.findall(".//service") if s.get(android + "name") == "hu.elmdash.auto.DashboardCarService"]
    media = [s for s in tree.findall(".//service") if s.get(android + "name") == "hu.elmdash.media.DashboardMediaService"]
    metadata = [m for m in tree.findall(".//meta-data") if m.get(android + "name") == "com.google.android.gms.car.application"]
    assert bool(auto) == variant.startswith("unsupported"), variant
    assert bool(media) == variant.startswith("media"), variant
    assert bool(metadata) == (variant in ("unsupportedDebug", "mediaDebug")), variant
    assert any(p.get(android + "name") == "android.permission.INTERNET" for p in tree.findall("uses-permission")) == variant.startswith("media")
    assert any(p.get(android + "name") == "android.permission.ACCESS_NETWORK_STATE" for p in tree.findall("uses-permission")) == variant.startswith("media")
    print(f"{variant}: isolation OK")
