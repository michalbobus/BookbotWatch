#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Desktopova referencia pre druhu funkciu: pocet kusov skladom na detaile knihy.
Zodpoveda tomu, co robi StockClient.kt.

    python tools/stock.py https://bookbot.sk/g/180963/b/22784943 3
"""
import json
import re
import sys
import urllib.request

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124 Safari/537.36")

NEXT_DATA = re.compile(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', re.S)
SENTENCE = re.compile(
    r"Sklad(?:om|em)\s+m[áa]me\s+celk(?:om|em).{0,400}?>\s*(\d+)\s*ks\s*<", re.S | re.I)
SENTENCE_TITLE = re.compile(
    r"Sklad(?:om|em)\s+m[áa]me\s+celk(?:om|em).{0,400}?knih[yu]\s*<a[^>]*>(.*?)</a>\s*\((\d{4})\)",
    re.S | re.I)


def fetch(url):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Language": "sk,cs;q=0.9,en;q=0.8",
    })
    return urllib.request.urlopen(req, timeout=25).read().decode("utf-8", "replace")


def pick_selected(mothers, data):
    if not mothers:
        return None
    for m in mothers:
        if m.get("selected"):
            return m
    pid = (data.get("product") or {}).get("id")
    if pid:
        for m in mothers:
            if (m.get("product") or {}).get("id") == pid:
                return m
    return mothers[0]


def parse_next_data(html):
    m = NEXT_DATA.search(html)
    if not m:
        return None
    try:
        data = json.loads(m.group(1))["props"]["pageProps"]["componentProps"]["data"]
        mothers = data["variants"]["mother"]
        chosen = pick_selected(mothers, data)
        if chosen is None:
            return None
        count = chosen.get("inStockCount", -1)
        if count < 0:
            return None
        mother = chosen.get("mother") or {}
        price = chosen.get("minPriceX100")
        return {
            "title": mother.get("title") or "Kniha",
            "year": mother.get("year") or "",
            "count": count,
            "price": price if price and price > 0 else None,
            "source": "__NEXT_DATA__",
        }
    except Exception as e:
        print("json parse zlyhal:", e)
        return None


def parse_html(html):
    m = SENTENCE.search(html)
    if not m:
        return None
    t = SENTENCE_TITLE.search(html)
    title = re.sub(r"<[^>]+>", "", t.group(1)).strip() if t else "Kniha"
    year = t.group(2) if t else ""
    return {"title": title, "year": year, "count": int(m.group(1)),
            "price": None, "source": "HTML fallback"}


def main():
    url = sys.argv[1] if len(sys.argv) > 1 else "https://bookbot.sk/g/180963/b/22784943"
    threshold = int(sys.argv[2]) if len(sys.argv) > 2 else 3
    html = fetch(url)

    primary = parse_next_data(html)
    fallback = parse_html(html)

    for name, info in (("primárne (JSON)", primary), ("záložné (HTML)", fallback)):
        if info is None:
            print("%-18s -> nenašlo sa" % name)
        else:
            price = ("%.2f €" % (info["price"] / 100.0)).replace(".", ",") if info["price"] else "-"
            print("%-18s -> %s (%s): %d ks, cena %s" % (
                name, info["title"], info["year"], info["count"], price))

    info = primary or fallback
    if info is None:
        print("\nCHYBA: počet kusov sa nepodarilo zistiť.")
        return
    print("\nprah: pod %d ks" % threshold)
    if info["count"] < threshold:
        print(">>> UPOZORNENIE: skladom už len %d ks" % info["count"])
    else:
        print("ok, skladom %d ks - bez upozornenia" % info["count"])


if __name__ == "__main__":
    main()
