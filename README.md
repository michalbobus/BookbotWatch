# BookBot hliadka

Android aplikácia s tromi nezávislými strážcami. Bookbot.sk (ceny aj sklad) a
restorio.sk (kusy aj cena). Všetky bežia v jednej kontrole a hlásia sa
notifikáciou aj e-mailom (predvolene na `fajnes@gmail.com`).

## 1. Strážca cien (bookbot.sk)

1. Načíta TXT zoznam (cez systémový výber súboru, číta sa pri každej kontrole,
   takže úpravy v súbore sa hneď prejavia — a appka doňho vie aj sama zapisovať,
   pozri nižšie).
2. Stiahne výpis z bookbot.sk. Produkty berie z JSON bloku `__NEXT_DATA__`
   (`props.pageProps.componentProps.items`), nie z HTML tried — tie sú hashované
   a menia sa pri každom deployi stránky. Ak by sa JSON zmenil, appka spadne späť
   na parsovanie HTML kariet.
3. Spáruje riadky zo zoznamu s ponukami: primárne podľa rímskeho čísla dielu
   (`(díl V)`), sekundárne podľa názvu, bez ohľadu na diakritiku.
4. Ak je aktuálna cena **nižšia** ako cena v TXT → upozornenie.

### Rozpoznanie vypredaného kusu

Keď sa posledný kus vydania predá, bookbot ho **zvyčajne úplne odstráni z výpisu**
— nezobrazí sa tam ani s tagom "Vypredané". Appka si preto priebežne pamätá
posledné známe ID ponuky pre každú položku zo zoznamu. Keď sa pri kontrole
položka vo výpise nenájde, appka si overí jej stav priamo na detaile knihy
(`https://bookbot.sk/g/<id>`) — ak je tam počet kusov 0 (rovnaká logika ako
pri strážcovi skladu, veta *„Strážiť dostupnosť knihy“* namiesto tlačidla na
kúpu), zobrazí sa status **„vypredaný“** aj s poslednou známou cenou, namiesto
matúceho „na stránke sa nenašlo“.

Občas sa ale stane, že bookbot vypredanú položku vo výpise **ponechá** — len
s interným príznakom `is_in_stock: false`, bez viditeľného štítku. Appka preto
tento príznak pri každej ponuke kontroluje priamo a nikdy neberie cenu takejto
položky ako platnú referenciu ani ako zlacnenie; aj vtedy sa zobrazí status
„vypredaný“.

### Úprava zoznamu priamo v appke

V sekcii **Sledovaný zoznam** sa dá:
- **Vybrať TXT** — vybrať existujúci súbor v telefóne
- **Nový súbor** — appka rovno vytvorí prázdny TXT tam, kam ukážeš
- **Pridať položku** — dialóg na diel / názov / cenu
- pri každej položke ceruzka na **úpravu ceny** a kôš na **odstránenie**

Všetky tieto úpravy sa zapisujú priamo do vybraného TXT súboru (cez SAF, appka
má naň trvalé oprávnenie na čítanie aj zápis), takže sa dajú súbežne upravovať
aj ručne v inom editore — appka pri každej kontrole číta aktuálny obsah.

## 2. Strážca skladu (bookbot.sk)

Sleduje ľubovoľný počet odkazov na konkrétne vydanie, napr.
`https://bookbot.sk/g/180963/b/22784943`. Z detailu vytiahne počet kusov skladom —
to je číslo z vety *„Skladom máme celkom **3 ks** knihy Dobrodružství Luthera
Arkwrighta (2005)."* Keď klesne **pod nastavený prah** (predvolene 3), pošle
upozornenie.

Rovnako ako pri restorio.sk (nižšie) sa dá nastaviť aj **cieľová cena** — appka
upozorní aj vtedy, keď cena knihy klesne na túto hodnotu alebo pod ňu, nezávisle
od prahu kusov. Obe podmienky sa dajú kombinovať alebo použiť len jednu z nich
(cieľová cena je voliteľná, necháš ju prázdnu, ak ju nechceš sledovať).

Číslo aj cena sa berú z `__NEXT_DATA__`:
`props.pageProps.componentProps.data.variants.mother[selected].inStockCount`
a `minPriceX100`, odtiaľ aj názov a rok. Záloha je regex na tú vetu v HTML.
Prah kusov sa dá v appke meniť šípkami, cieľová cena kliknutím na jej riadok —
pre každý odkaz zvlášť.

Prvý odkaz (Arkwright, prah 3 ks) je predvyplnený — dá sa zmazať.

## 3. Strážca na restorio.sk (kusy aj cena)

Sekcia **Restorio – sledovanie**. Sleduje konkrétne vydanie na restorio.sk, napr.
`https://www.restorio.sk/9788076794306` (stačí zadať aj samotný kód produktu bez
URL). Na rozdiel od bookbot.sk, kde je viac nových kusov od rôznych predajcov,
restorio predáva použité knihy — zvyčajne jeden unikátny kus — takže appka rieši
dostupnosť ako **skladom / vypredané** (1 ks / 0 ks).

Dajú sa nastaviť dve nezávislé podmienky naraz, presne v štýle strážcu skladu:
- **prah kusov** (šípky) — upozorní, keď počet klesne pod nastavenú hodnotu
  (predvolene 1, teda keď sa kus vypredá)
- **cieľová cena** (klik na riadok cieľovej ceny) — upozorní, keď cena klesne
  na túto hodnotu alebo pod ňu; necháš prázdne, ak cenu nechceš sledovať

Cena sa berie z `<meta property="product:price:amount">` (Open Graph, generuje
sa server-side), záloha je JSON-LD blok `application/ld+json` a napokon regex na
prvú cenu v HTML. Dostupnosť sa pozná podľa vety *„Upozornite ma, keď bude tento
produkt na sklade"* — jej prítomnosť znamená vypredané.

Predvyplnený príklad: `https://www.restorio.sk/9788076794306`, cieľová cena
14,96 € — dá sa zmazať alebo upraviť rovnako ako ostatné odkazy.

## Proti spamovaniu

Appka si pamätá hodnotu, pri ktorej už upozornila — znovu pošle až keď cena
alebo počet kusov klesne **ešte nižšie**. Keď sa hodnota vráti nad prah, pamäť
sa vyčistí. Tlačidlo *Vynulovať históriu* to resetuje ručne (týka sa cien aj
skladu na bookbot.sk; zmena prahu/cieľovej ceny pri jednotlivom odkaze — na
bookbot aj na restorio — resetuje históriu len pre ten odkaz).

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

Presne tento formát appka používa aj pri zápise zmien z GUI (pridanie/úprava/
zmazanie položky), takže súbor zostáva čitateľný aj v inom editore.

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

V `tools/` sú referenčné implementácie tej istej logiky pre desktop (zatiaľ len
pre bookbot.sk, restorio.sk nie je pokryté):

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
  data/TxtParser.kt      parser aj serializátor TXT zoznamu
  data/Matcher.kt        párovanie zoznam ↔ ponuky
  data/Prefs.kt           nastavenia + stav medzi kontrolami
  net/Http.kt              spoločné sťahovanie stránok
  net/BookbotClient.kt    parsovanie výpisu na bookbot.sk (ceny)
  net/StockClient.kt      parsovanie detailu knihy na bookbot.sk (kusy skladom)
  net/RestorioClient.kt   parsovanie detailu knihy na restorio.sk (kusy + cena)
  core/PriceChecker.kt    jadro: všetky tri kontroly a rozhodnutie o upozornení
  mail/Mailer.kt            SMTP odosielanie (JavaMail)
  notify/Notifier.kt        notifikačné kanály a notifikácie
  work/CheckWorker.kt       periodická kontrola (WorkManager)
  ui/                        Compose GUI (Material 3)
```

