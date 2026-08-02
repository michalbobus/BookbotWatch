#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Desktopova referencna implementacia toho, co robi appka.
Sluzi na overenie parsovania a parovania bez toho, aby sa musel stavat APK.

    python tools/check.py "..\\y posledni z muzu vaughn.txt"
"""
import json
import re
import sys
import unicodedata
import urllib.request

DEFAULT_URL = "https://bookbot.sk/p/q/y%20posledn%C3%AD%20z%20mu%C5%BE%C5%AF/language/1"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124 Safari/537.36")

NBSP = " "
ROMAN = re.compile(r"^[IVXLCDM]{1,7}$", re.I)
COLUMN_SPLIT = re.compile(r"\t+| {2,}|\s*[;|]\s*")
TRAILING_PRICE = re.compile(
    r"(\d{1,5}[.,]\d{1,2}\s*(?:€|EUR)?|\d{1,5}\s*(?:€|EUR))\s*$", re.I)
NEXT_DATA = re.compile(
    r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', re.S)


def price_to_cents(raw):
    s = raw.replace(NBSP, " ").replace(" ", "").replace("€", "")
    s = re.sub(r"EUR", "", s, flags=re.I).strip()
    m = re.match(r"^(\d{1,5})(?:[.,](\d{1,2}))?$", s)
    if not m:
        return None
    return int(m.group(1)) * 100 + int((m.group(2) or "").ljust(2, "0"))


def normalize(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def parse_txt(text):
    items = []
    for raw in text.splitlines():
        line = raw.replace(NBSP, " ").replace("﻿", "").strip()
        if not line or line.startswith("#") or line.startswith("//"):
            continue
        n = normalize(line)
        if "cena" in n and ("nazov" in n or "nazev" in n or "titul" in n):
            continue

        rest, cents = line, None
        m = TRAILING_PRICE.search(line)
        if m:
            parsed = price_to_cents(m.group(1))
            if parsed is not None:
                cents = parsed
                rest = line[:m.start()].strip().rstrip("\t;|-").strip()
        if not rest:
            continue

        cols = [c.strip() for c in COLUMN_SPLIT.split(rest) if c.strip()]
        volume, name = "", rest
        if len(cols) >= 2 and ROMAN.match(cols[0]):
            volume, name = cols[0].upper(), " ".join(cols[1:])
        elif len(cols) >= 2:
            name = " ".join(cols)
        items.append({"volume": volume, "name": name, "ref": cents})
    return items


def fetch_offers(url):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Language": "sk,cs;q=0.9,en;q=0.8",
    })
    html = urllib.request.urlopen(req, timeout=25).read().decode("utf-8", "replace")
    m = NEXT_DATA.search(html)
    if not m:
        return []
    items = (json.loads(m.group(1))["props"]["pageProps"]["componentProps"]["items"])
    offers = []
    for it in items:
        cents = it.get("highlight_price_eur") or 0
        gid = it.get("grandmothers_id")
        title = it.get("grandmothers_title") or ""
        if cents > 0 and gid and title:
            offers.append({"id": gid, "title": title, "cents": cents,
                           "stock": bool(it.get("is_in_stock", True))})
    return offers


def best_match(item, offers):
    nname = normalize(item["name"])
    vol_re = None
    if item["volume"]:
        vol_re = re.compile(r"\b(?:dil|diel|die)\s+%s\b" % normalize(item["volume"]))

    best, best_score = None, 0
    for o in offers:
        ntitle = normalize(o["title"])
        score = 0
        if vol_re and vol_re.search(ntitle):
            score += 2
        if nname and nname in ntitle:
            score += 1
        if score == 0:
            continue
        if best is None or score > best_score or (
                score == best_score and (o["stock"], -o["cents"]) > (best["stock"], -best["cents"])):
            best, best_score = o, score
    return best


def eur(c):
    return ("%.2f €" % (c / 100.0)).replace(".", ",")


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "y posledni z muzu vaughn.txt"
    url = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_URL
    with open(path, encoding="utf-8-sig") as f:
        items = parse_txt(f.read())
    offers = fetch_offers(url)
    print("zoznam: %d poloziek, web: %d ponuk\n" % (len(items), len(offers)))

    drops = []
    for it in items:
        o = best_match(it, offers)
        label = ("%s. %s" % (it["volume"], it["name"])) if it["volume"] else it["name"]
        if not o:
            print("%-28s  NENAJDENE" % label)
            continue
        ref = it["ref"]
        now = o["cents"]
        flag = ""
        if ref is not None and now < ref:
            flag = "  <<< ZLACNENE o %s" % eur(ref - now)
            drops.append((label, ref, now))
        print("%-28s  ref=%-9s teraz=%-9s %-46s%s" % (
            label, eur(ref) if ref else "-", eur(now), o["title"][:46], flag))

    print("\nzlacnene: %d" % len(drops))


if __name__ == "__main__":
    main()
