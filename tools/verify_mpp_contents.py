#!/usr/bin/env python3
import io
import sys
import zipfile
from pathlib import Path

CALENDAR_SOURCES = {
    "io/github/yannsoliman/patches/keepcool/KeepcoolCalendarPatch.kt",
    "io/github/yannsoliman/patches/keepcool/KeepcoolCalendarFingerprints.kt",
}

CALENDAR_CLASSES = {
    "io/github/yannsoliman/patches/keepcool/KeepcoolCalendarPatchKt.class",
    "io/github/yannsoliman/patches/keepcool/KeepcoolCalendarFingerprintsKt.class",
    "io/github/yannsoliman/patches/keepcool/BookingCalendarSetupFingerprint.class",
    "io/github/yannsoliman/patches/keepcool/DatePickerBuildFingerprint.class",
}


def archive_entries(path: Path) -> set[str]:
    if not zipfile.is_zipfile(path):
        raise SystemExit(f"{path} is not a ZIP/JAR-compatible MPP")

    entries: set[str] = set()
    with zipfile.ZipFile(path) as zf:
        for name in zf.namelist():
            entries.add(name)
            if name.endswith((".jar", ".zip")):
                data = zf.read(name)
                if zipfile.is_zipfile(io.BytesIO(data)):
                    with zipfile.ZipFile(io.BytesIO(data)) as nested:
                        entries.update(nested.namelist())
    return entries


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: verify_mpp_contents.py PUBLIC_MPP PERSONAL_MPP")

    public_entries = archive_entries(Path(sys.argv[1]))
    personal_entries = archive_entries(Path(sys.argv[2]))

    leaked = sorted(
        name for name in (CALENDAR_SOURCES | CALENDAR_CLASSES)
        if name in public_entries
    )
    missing = sorted(name for name in CALENDAR_CLASSES if name not in personal_entries)

    if leaked:
        raise SystemExit(f"calendar artifacts leaked into public MPP: {leaked}")
    if missing:
        interesting = sorted(
            name for name in personal_entries
            if "keepcool" in name.lower() or name.endswith((".dex", ".jar", ".class"))
        )
        print("Personal MPP relevant entries:", file=sys.stderr)
        for name in interesting[:200]:
            print(f"  {name}", file=sys.stderr)
        raise SystemExit(f"calendar classes missing from personal MPP: {missing}")

    print("MPP contents: PASS")
    print("Calendar artifacts absent from public MPP")
    print("Calendar classes present in personal MPP")


if __name__ == "__main__":
    main()
