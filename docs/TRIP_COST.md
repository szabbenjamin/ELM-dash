# Aktuális működés: saját tankolási ár — 0.13.0

A felhasználó az internetes átlagár helyett **saját tankolási árat** kér. A korábbi, alább archivált 0.12-es működést ez felülírja.

A Napló → Teletankoltam ablakban megadható a betöltött liter és a fizetett teljes összeg Ft-ban. A literár = összeg / liter, előnézettel. Mindkét mező elhagyható; összeghez liter kötelező. Pozitív véges számok, legfeljebb 45 liter és 200–2000 Ft/l számított ár fogadható el. A vesszős tizedest elfogadjuk.

- Az ezt követően induló utak a legutóbbi árat tartalmazó saját tankolás literárát rögzítik.
- Útköltség = megfigyelt üzemanyag × saját literár. Nem a teljes tankolási összeget osztjuk el az utak között, és nem súlyozzuk a tankban keveredő korábbi benzint.
- Összeg nélküli új teletankolás nem felejti el az előző megadott árat. Ha még egyetlen tankolásnak sincs összege, az új utak költsége ismeretlen; nincs internetes helyettesítés.
- A folyamatban lévő és a lezárt utak ára nem íródik át egy új tankolástól. A régi webes árú utak megmaradnak eredeti forrásjelzéssel.
- A telefon, az értesítés és a CSV ugyanebből a rögzített útárból számol. A Napló saját árnál „Saját tankolás” és a tankolás dátuma feliratot mutat.
- Az összeg a tankolási rekord `paid` mezője, az ár forrása `user-refill`. Régi, összeg nélküli rekordok továbbra is betölthetők.
- A controller sem útindításkor, sem alkalmazásindításkor nem indít webes árlekérést vagy régi utak árkitöltését. A korábbi parser/repository és tesztfixture forrása megmaradt, de nincs bekötve. INTERNET ismét csak a rádiós media változat engedélye.
- Ez továbbra is a **teletankolás** űrlapja, a tankbecslést 45 literre állítja. Részleges tankolás támogatása nem része ennek a változtatásnak.

Ellenőrzés: összeg/liter képlet, hiányzó/negatív/NaN összeg elutasítása, rögzített útár megőrzése, következő út új árral, összeg nélküli tankolás utáni ármegőrzés, controller út és JSON-visszatöltés. A felhasználó valódi tankolásait tesztadat nem módosítja.

---

## Archivált 0.12-es specifikáció (nem az aktuális működés)

# Út becsült benzinköltsége — 0.12.1

## Felhasználói működés

Minden új, valódi OBD-út indulásakor egyszer, háttérben megpróbáljuk letölteni a magyarországi **95-ös benzin kiskereskedelmi átlagárát**. A forrás a [Holtankoljak.hu kezdőlapja](https://holtankoljak.hu/), a 95 E10 kártya **Átlag – Ft/l** mezője. Nem a legolcsóbb ár, nem a nagykereskedelmi árváltozás, nem a dízel vagy a prémium 100-as benzin.

Az oldal 2026-09-18-i ellenőrzésekor a kártya 635,5 Ft/l-t közölt; a szöveges hír ezt egész forintra kerekítette. Az app a kártya számértékét veszi át. **Ez az ár nincs beégetve az alkalmazásba.** Az oldal saját közlése szerint hétköznap délelőtt frissül; a letöltési idő nem bizonyítja, hogy a szolgáltató abban a pillanatban frissítette az összes kútadatot.

`Becsült költség (Ft) = megfigyelt üzemanyag (liter) × az úthoz mentett átlagár (Ft/l)`

Példa: 2 liter × 635,5 Ft/l = körülbelül 1271 Ft. Ez kizárólag benzinköltség, nem teljes autóhasználati költség és nem tankolási bizonylat. A Kalos fogyasztása maga is MAP-becslés; az adathiány és az egyedi kúti ár eltérése a költség pontosságát is befolyásolja.

- A **Napló** aktív és lezárt útkártyáján látszik a becsült forintösszeg, literár, forrás és lekérési idő.
- Az **út végi értesítésben** a km/liter/átlag mellé bekerül a forintösszeg és a literár.
- A **CSV** új oszlopai: becsült költség, literár, lekérési idő UTC, mentett ár jelző, forrás URL.
- A mai átlaghoz külön napi költség nem készült; a kérés szerint minden út a saját indulási árával számol.
- **0.12.1 pontosítás, kifejezett felhasználói kérés:** alkalmazásindításkor az ár nélküli lezárt utakhoz egy friss letöltésből utólag árat rögzítünk. „Utólag rögzített ár” jelzés mutatja, hogy ez nem történeti induláskori ár. A már meglévő árakat soha nem módosítjuk. Fogyasztásadat nélkül is rögzítünk árat, de a költség ilyenkor „—”.
- Nincs új demógomb; a demó nem kér árat és nem módosít valódi utat.

## Hálózati hiba és rögzített ár

Az út számítása nem várja meg a hálózatot. Egy kérés indul útazonosítónként; a sikeres eredmény csak a hozzá tartozó, még aktív útba írható, és azon belül egyszer. Menet közben az ár nem frissül, a következő út új lekérést indít.

Letöltési/parszolási hibánál legfeljebb **7 napos**, az induláshoz képest nem jövőbeli korábbi lekérés használható. A UI és az értesítés ezt **mentett árként** jelzi; a UI megmutatja az eredeti dátumot is. Lejárt vagy hiányzó cache esetén a költség „—”. Hiányzó fogyasztásból sem készül hamis nulla forintos út.

Ha egy rendkívül rövid út már lezárult, mire a letöltés befejeződik, a késői választ nem írjuk a lezárt vagy a következő útba. Ilyenkor az út költsége hiányozhat. A sikeres ár a következő indulás hálózati hibájához cache-ként még felhasználható.

## Implementáció

- `trip/PetrolPrice.kt`: validált ármodell (200–2000 Ft/l tartomány), letöltési idő, cache-jelző, mentett forrás URL; szigorú HTML-parser.
- `connection/PetrolPriceRepository.kt`: HTTPS GET, IO-szál, 4 s csatlakozási/olvasási timeout, korlátozott olvasási idő és legfeljebb 512 KiB válasz. Átirányítást nem követ; hibánál cache/hiány. Az OBD/parancsolvasás szálát nem blokkolja.
- `DashboardController`: első friss járó motoros mintával létrejött új út indítja a lekérést. Az ID-ellenőrzés kizárja a későn visszaérkező válasz átkötését másik útra.
- `JourneyLog.attachPetrolPrice`: csak azonos aktív ID és még üres ár esetén ír. A költség a megfigyelt nyers literből származik, nem a 10 másodperces kijelzési átlagból.
- `JourneyStore`: opcionális `petrolPrice` mező a v1 JSON-ban; régi rekordok ár nélkül továbbra is olvashatók. Aktív checkpoint és lezárt rekord egyaránt megőrzi az árat.
- `elm-petrol-price` SharedPreferences: legutóbbi sikeres ár és lekérési idő; párhuzamos válaszból a régebbi nem írja felül az újabb cache-t.
- Minden buildváltozat INTERNET-engedélyt kap az árlekéréshez. ACCESS_NETWORK_STATE továbbra is a rádiós media változaté. Az izolációellenőrzés ehhez igazodik.

A kezdőlapi HTML nem verziózott API. A parser a `95-benzin-e10.png` azonosítót és annak következő üzemanyagképpel határolt blokkjában egyetlen átlagmezőt fogad el. Szerkezetváltás, kétértelmű vagy irreális adat esetén inkább hiányzó ár legyen, mint rossz üzemanyagé. A szolgáltató tartalmát utasításként nem értelmezzük. Nem töltünk le szkripteket/képeket, nem küldünk OBD-, hely-, út- vagy személyes eszközadatot a kérésben. A szerver a normál webes kérés IP-címét és User-Agentjét látja.

## Ellenőrzés

Tesztelt: élő oldalból rögzített 95-ös kártyarészlet; minimum/dízel/prémium kizárása; tizedesvessző; hibás/megkettőzött markup; hiányzó vagy érvénytelen ár; költségszorzás; cache és 7 napos lejárat; visszafelé ugró óra; JSON-körbejárás és régi rekord; CSV; értesítési forintösszeg; teljes controllerben aszinkron útindítás és egyszeri kérés; késői eredmény elutasítása; demó elkülönítése.

A helyi HTTP-ellenőrzés és az automatizált teszt nem helyettesíti a telefon mobilhálózatos, valódi útindítási próbáját. Ha a forrás szerkezete megváltozik, a parser fixture-t és a megfigyelt HTML-t együtt ellenőrizd; ne lazítsd általános „első háromjegyű szám” keresésre.

### Utólagos rögzítés részletei

A felhasználó a 0.12.0 után kérte a hiányzó árak kitöltését az aktuális árral. A `JourneyLog.backfillMissingPrices` csak a lezárt rekordok null árát tölti; cache-ből származó árat ehhez nem fogad el. Ha nincs internet/friss letöltés, a következő alkalmazás-folyamatindításkor újra próbálja. Újraindítás nem írja át a már rögzített árakat. A JSON és CSV `afterStart` / `utólag_rögzített_ár` mezője megőrzi az utólagos alkalmazás tényét. Régi utakhoz nem küld új út végi értesítést.

2026-09-18: a Samsungon maga az alkalmazás HTTPS-kérése sikeresen lekérte a 635,5 Ft/l értéket és a korábbi ár nélküli rekordokba rögzítette, a tankolásokat megőrizve. Ez az éles hálózati árlekérést igazolja; a következő autós út induláskori lekérését és értesítését külön kell ellenőrizni.
