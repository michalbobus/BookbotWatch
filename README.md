# BookBot hliadka

Android aplikácia s dvoma nezávislými strážcami na **bookbot.sk**. Obidva bežia
v jednej kontrole a hlásia sa notifikáciou aj e-mailom (predvolene na
`fajnes@gmail.com`).

## 1. Strážca cien

1. Načíta TXT zoznam (cez systémový výber súboru, číta sa pri každej kontrole,
   takže úpravy v súbore sa hneď prejavia).
2. Stiahne výpis z bookbot.sk. Produkty berie z JSON bloku `__NEXT_DATA__`
   (`props.pageProps.componentProps.items`), nie z HTML tried — tie sú hashované
   a menia sa pri každom deployi stránky. Ak by sa JSON zmenil, appka spadne späť
   na parsovanie HTML kariet.
3. Spáruje riadky zo zoznamu s ponukami: primárne podľa rímskeho čísla dielu
   (`(díl V)`), sekundárne podľa názvu, bez ohľadu na diakritiku.
4. Ak je aktuálna cena **nižšia** ako cena v TXT → upozornenie.

## 2. Strážca skladu

Sleduje ľubovoľný počet odkazov na konkrétne vydanie, napr.
`https://bookbot.sk/g/180963/b/22784943`. Z detailu vytiahne počet kusov skladom —
to je číslo z vety *„Skladom máme celkom **3 ks** knihy Dobrodružství Luthera
Arkwrighta (2005)."* Keď klesne **pod nastavený prah** (predvolene 3), pošle
upozornenie.

Číslo sa berie z `__NEXT_DATA__`:
`props.pageProps.componentProps.data.variants.mother[selected].inStockCount`,
odtiaľ aj názov, rok a najnižšia cena. Záloha je regex na tú vetu v HTML.
Prah sa dá v appke meniť šípkami pre každý odkaz zvlášť.

Prvý odkaz (Arkwright, prah 3 ks) je predvyplnený — dá sa zmazať.

## Proti spamovaniu

Appka si pamätá hodnotu, pri ktorej už upozornila — znovu pošle až keď cena
alebo počet kusov klesne **ešte nižšie**. Keď sa hodnota vráti nad prah, pamäť
sa vyčistí. Tlačidlo *Vynulovať históriu* to resetuje ručne.

## Formát TXT súboru

Stĺpce oddelené tabulátorom, hlavička je voliteľná:

```
Diel	Názov	Cena
V	V kruhu	8,49 €
VI	Holky s holkama	6,99 €
```

Tolerované je aj:
- oddelenie viacerými medzerami, `;` alebo `|`
- vynechaný stĺpec dielu (`Matka země	7,49 €`)
- vynechaná cena (`X	Odpovědi`) — referenciou sa stane prvá videná cena na webe
- riadky začínajúce `#` sa ignorujú

## Zostavenie APK cez GitHub Actions

Repozitár musí mať **tento priečinok (`BookbotWatch`) ako koreň** — je tu
`settings.gradle.kts` aj `.github/workflows/android.yml`.

```bash
cd BookbotWatch
git init
git add .
git commit -m "BookBot hliadka"
git branch -M main
git remote add origin https://github.com/<tvoj-ucet>/<repo>.git
git push -u origin main
```

Po pushnutí: **GitHub → Actions → Build APK → artefakt `BookbotWatch-apk`**.
V ňom sú dva súbory:

- `app-debug.apk` — inštaluj tento, funguje hneď
- `app-release.apk` — podpísaný debug kľúčom, tiež inštalovateľný

APK prenes do telefónu a nainštaluj (treba povoliť *Inštalácia z neznámych zdrojov*).

## Nastavenie e-mailu v aplikácii

Android nevie poslať e-mail sám od seba na pozadí — appka ho posiela cez SMTP.
Pre Gmail:

1. Na odosielacom účte zapni dvojfaktorové overenie.
2. Vygeneruj **heslo aplikácie** (Google účet → Zabezpečenie → Heslá aplikácií).
3. V appke vyplň:
   - SMTP server `smtp.gmail.com`, port `587`
   - Prihlásenie = celá gmailová adresa odosielateľa
   - Heslo aplikácie = tých 16 znakov (nie bežné heslo)
4. Klikni **Poslať testovací e-mail**.

Port `465` appka berie ako SSL, čokoľvek iné ako STARTTLS. Iný poskytovateľ
(Seznam, vlastný server) funguje rovnako, len zmeň server a port.

## Kontrola na pozadí

Prepínač *Automatická kontrola* + interval (1 / 3 / 6 / 12 / 24 h). Beží cez
WorkManager, prežije aj reštart telefónu. Android môže spustenie posunúť podľa
režimu úspory batérie — pre spoľahlivé intervaly vylúč appku z optimalizácie batérie.

## Overenie bez telefónu

V `tools/` sú referenčné implementácie tej istej logiky pre desktop:

```bash
# ceny: tabuľka ref. cena / aktuálna cena, označí zlacnené
python tools/check.py "../y posledni z muzu vaughn.txt"

# sklad: počet kusov cez JSON aj cez HTML zálohu, vyhodnotí prah
python tools/stock.py https://bookbot.sk/g/180963/b/22784943 3
```

## Štruktúra

```
app/src/main/java/com/bookbotwatch/
  data/Models.kt        dátové typy + formátovanie ceny
  data/TxtParser.kt     parser TXT zoznamu
  data/Matcher.kt       párovanie zoznam ↔ ponuky
  data/Prefs.kt         nastavenia + stav medzi kontrolami
  net/Http.kt           spoločné sťahovanie stránok
  net/BookbotClient.kt  parsovanie výpisu (ceny)
  net/StockClient.kt    parsovanie detailu knihy (kusy skladom)
  core/PriceChecker.kt  jadro: obidve kontroly a rozhodnutie o upozornení
  mail/Mailer.kt        SMTP odosielanie (JavaMail)
  notify/Notifier.kt    notifikačné kanály a notifikácie
  work/CheckWorker.kt   periodická kontrola (WorkManager)
  ui/                   Compose GUI (Material 3)
```
