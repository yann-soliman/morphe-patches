#!/usr/bin/env python3
import json
import sys
from pathlib import Path

PUBLIC_PATCHES = {
    "Naolib vélo : carte avec microG",
    "Naolib vélo : déverrouiller les vélos à distance",
    "Naolib vélo : ouvrir les parkings à distance",
    "Naolib vélo : conserver les parkings",
    "Leboncoin : supprimer les publicités",
    "Leboncoin : limiter le suivi",
    "Leboncoin : conserver les filtres de recherche",
    "Meteo Consult: remove ads and subscription prompts",
    "Meteo Consult: disable video autoplay",
    "Keep Cool : autoriser le + dans les adresses e-mail",
    "Keep Cool : afficher les disponibilités",
}
PERSONAL_PATCHES = PUBLIC_PATCHES | {
    "Keep Cool : calendrier de réservation à 30 jours",
}


def names(path: str) -> set[str]:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return {patch["name"] for patch in data["patches"]}


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: verify_bundle_visibility.py PUBLIC_LIST PERSONAL_LIST")

    public = names(sys.argv[1])
    personal = names(sys.argv[2])

    if public != PUBLIC_PATCHES:
        raise SystemExit(
            f"public bundle mismatch: expected {sorted(PUBLIC_PATCHES)}, got {sorted(public)}"
        )
    if personal != PERSONAL_PATCHES:
        raise SystemExit(
            f"personal bundle mismatch: expected {sorted(PERSONAL_PATCHES)}, got {sorted(personal)}"
        )

    print("Bundle visibility: PASS")
    print(f"Public patches: {len(public)}")
    print(f"Personal patches: {len(personal)}")


if __name__ == "__main__":
    main()
