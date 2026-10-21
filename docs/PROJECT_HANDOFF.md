# ELM Dash — projektátadás és termékkövetelmények

Állapot: **2026-10-21 · 0.17.2**. Ez a fájl a beszélgetés nélkül is használható fejlesztői összefoglaló. A régebbi tesztek bizonyítékai a [VALIDATION.md](../VALIDATION.md)-ben, a buildparancsok a [README.md](../README.md)-ben vannak.

## Aktuális felület (0.16.17)

- AA borító: RPM / terhelés; 10 s fogyasztás / napi fogyasztás; vízhőfok / aktuális út km / hatótáv. A korábbi elrendezések alább történeti bejegyzések.
- Világosított csempék, 4%-kal magasabb adatblokk. Az alsó szövegsor állandó rádiónév; nincs műszer/rádió/hatótáv/Útinform váltogatás.
- Telefonos útrészletekben az OSM automatikusan betöltődik; exportált HTML-ben kézzel indítható.
- Folyamatos 0 RPM: 120 s; friss RPM hiánya: 180 s útlezárási határ.

## 1. Cél és használat

A cél vezetés közben az Android Auto fejegységen szeretné látni a Chevrolet Kalos motoradatait és fogyasztását, lehetőleg a térkép mellett a kisebb médiakártyában is. A telefon közvetlenül az ELM327 adapterből olvas, internetes rádiót játszik le, menti a napokat/utakat és kezeli a beállításokat. A Torque eredetileg működött az adapterrel, de az új app önálló: nem Torque-plugin.

A telefonos UI nem helyettesíti az AA célt. Jelenleg a valódi fejegységen működő integráció a rádióhoz tartozó média-session metaadata és dinamikus borítóképe. A normál AA médiakártya méretét, vágását és feliratait a host határozza meg.

## 2. Autó, telefon, fejegység

| Tulajdonság | Megerősített adat / alkalmazott beállítás |
|---|---|
| Autó | Chevrolet Kalos, 2005 |
| Motor | 1.2 8V E-TEC, 72 LE / 54 kW profil |
| Feltöltés / váltó | Szívó benzines, kézi váltó; megerősített profil |
| Számítási hengerűrtartalom | 1150 cm³ |
| Tank | 45 l névleges kapacitás; lásd a forrásokat alább |
| Telefon | Samsung Galaxy Z Fold4 (SM-F936B), Android 16 / API 36 a tesztek idején |
| Autós kijelző | Carpuride W113, vezeték nélküli Android Auto |
| Adapter | Bluetooth Classic / SPP ELM327; pontos hardver/firmware gyártó nem igazolt |
| Használt app | ELM Dash • AA Média, `hu.elmdash.app.media`, `mediaDebug` |
| Utoljára telepített verzió | 0.13.0-aa-media, versionCode 15 |

A Bluetooth-címek és az USB-sorozatszám személyes eszközazonosítók: nem részei a repónak. A telefonon a kiválasztott OBD-adapter és fejegység címe helyben mentett. Új gépen `adb devices`, új telefonon a fogaskerék → Kapcsolat alatt válassz eszközt; ne találgass címeket.

Tankméret forrása: [Kalos kézikönyv, 41. oldal](https://www.manualslib.com/manual/813940/Daewoo-Kalos.html?page=41), [műszaki adatok, 262. oldal](https://www.manualslib.com/manual/813940/Daewoo-Kalos.html?page=262), [2005 Chevrolet Kalos 1.2 SE modelladat](https://www.km77.com/coches/chevrolet/kalos/2005/3-puertas/se/kalos-3p-12-se/datos). A 45 l a névleges tank, nem a minden tankoláskor betöltendő mennyiség.

## 3. Valódi OBD-tapasztalat és fogyasztási konfiguráció

A 0.7-es autós próbában az RPM, sebesség, MAP, TPS, hűtőfolyadék-hőfok és feszültség olvasható volt. A MAF (`0110`) és az ECU üzemanyagáram (`015E`) nem volt támogatott. A szívólevegő-hőmérséklet (`010F`, IAT) elérhető volt. Ez magyarázta a korábbi üres fogyasztási kijelzést: kizárólag ECU-adatból ezen az autón nem jött fogyasztás.

A `SessionStore` alapértelmezett profilja, ha a felhasználó még nem választott, `KALOS_12`. A már mentett választást megőrzi. A `FuelSettings` modell önmagában ECU_ONLY alapértékű; a termék alapbeállítását ne ebből az egy konstruktorból következtesd.

- Forrássorrend: **ECU üzemanyagáram → MAF-alapú becslés → Kalos MAP/RPM/IAT becslés**.
- Kalos: 1,15 l; AFR 14,7; benzinsűrűség 745 g/l; VE kezdetben 0,80; korrekció kezdetben 1,00.
- VE állítható 0,40–1,20; korrekció 0,50–2,00. Ezek modellparaméterek, nem hitelesített gyári kalibráció.
- MAP-becslés a gáz sűrűségéből és a négyütemű motor becsült beszívott térfogatából számol. Hiányzó IAT/MAP/RPM helyére nem szabad tippelt értéket tenni.
- Hidegindítási dúsítás és motorféki befecskendezés-leállítás nem állapítható meg megbízhatóan ebből. Az eredményen legyen `≈` / becslés jelzés.
- A tankoláskor megadott kúti liter jelenleg naplóadat, nem automatikus kalibráció.
- A mai nap régebbi, fogyasztás nélküli kilométereit nem töltjük ki utólag becsült üzemanyaggal. A teljes megtett távolság és a fogyasztási átlaghoz használható távolság eltérhet.

Részletes váltásjelzés: [DRIVING_GUIDANCE.md](../DRIVING_GUIDANCE.md). A fordulatszám, terhelés, melegedés és becsült fokozat alapján adott szín/nyíl tanács, nem gyári váltásjelző; hűtőfolyadékból olajhőmérsékletet nem állítunk.

## 4. Elfogadott kérések és végleges működés

### Kijelzés

- RPM és vízhőfok jól olvasható az AA-n. Színes háttér és fel/le váltási jelzések a szívó benzines Kaloshoz.
- A számok ne villogjanak adathiánynál: utolsó ismert érték szürkül, a kártya helye és mérete stabil. Soha nem látott adat „—”.
- A jelenlegi fogyasztás **10 másodperces gördülő átlag**, az ablakban integrált liter / kilométer aránya. Nem a l/100 km minták egyszerű számtani átlaga.
- Kijelzési egység mindig **l/100 km**; állva/5 km/h alatt „—”, nem l/h. A belső liter/óra a számítás része marad.
- Az AA főcímről a „Most” szó lekerült. Példa: `≈5,8 • Ma ≈7,0 l/100 km`.
- Az alsó metaadatsor 8 másodpercenként vált az aktuális műszeradatok és a rádió neve/állapota között. Álló OBD-adatok mellett is váltson; kézi szünetet ne nevezzen lejátszásnak.
- A felhasználó képei igazolták, hogy a kis médiakártya feliratai és gombjai eltakarják az eredeti borító alját. Az új 512×512 borítón az adatblokk y=44..277; y=288..511 üres. A blokk a korábbihoz képest +15,3% magas. A host további vágása eszközfüggő.

### Rádió és automatika

- Tényleges rádiólejátszó kell: állomáslista, következő/előző, keresés, kedvencek, saját URL, hangfókusz és megszakadáskezelés.
- 20 beépített állomás, többek között Retro; a katalógus és forrásai: [RADIO_STATIONS.md](../RADIO_STATIONS.md). A Roxy külön felmerült igényként; a tényleges elérhetőséget a katalógusból ellenőrizd, ne feltételezz működő streamet pusztán a beszélgetés alapján.
- A felhasználó pontos Oxygen URL-je: `https://oxygenmusic.hu:8443/oxygenmusic`.
- Hálózatvesztéskor 2/5/10/20/30 s újrapróbálkozás; hálózat-visszatérés figyelése, 30 s pufferelési watchdog. Kézi szünet ne indítsa újra magát.
- AA-csatlakozásra az utolsó állomás automatikusan indulhat (alapból engedélyezett); a kézi vezérlés elsőbbsége megmarad. A rádió és az OBD külön életciklus.
- Mentett OBD automatikusan csatlakozzon AA/ismert fejegység jelenlétére. Ne kelljen minden induláskor kapcsolódást nyomni. Nincs autó esetén állapot és figyelmeztetés.
- Az automatikus OBD újrapróbálkozik, később 30 s ütemben. Kézi mérés véges újrapróbálkozást használ. Az AA első BT-ébresztésének külön várakozási ablaka van: ez nem az útlezárási határ.

### Nap, út, tank, értesítés

| Fogalom | Szabály |
|---|---|
| Napi összesítés | Helyi naptári nap, éjféli forduló; nem motorindítás. 31 megfigyelt nap mentve. |
| Naplózott út indulása | Első friss RPM > 0. A csatlakozás előtti utat nem rekonstruáljuk. |
| Igazolt motorleállítás | 120 s folyamatosan megfigyelt 0 RPM lezárja. |
| Motoradat / kapcsolat elvesztése | 180 s a legutóbbi friss RPM óta. Rövidebb kimaradás ugyanazt az utat folytatja, részleges jelzéssel. |
| Új motorindítás | ECU-futásidő visszaesése külön utat indíthat; 16 bites túlcsordulást nem kezelünk restartként. |
| Kézi leállítás / nullázás | Azonnali lezárás; nullázás megőrzi a napi adatot és a naplót, nem küld út végi értesítést. |
| AA megszakadása | Automatikus OBD-szolgáltatás 182 s után áll le; AA visszatérése törli ezt az időzítőt. Rádió rövidebb, külön türelmi idővel. |
| Folyamat megszakadása | Újranyitáskor az aktív checkpoint egyszer lezárul, hiányjelzéssel. Nem hidaljuk át kitalált adattal a kiesést. |
| Útnapló | Legfeljebb 200 út, részletes statisztikák és CSV-export. |
| Tank | Első valódi „Teletankoltam” jelzésig ismeretlen. Onnantól 45 l mínusz nyers integrált fogyasztás; 0–100% közé szorítva. |
| Tankolásnapló | Legfeljebb 100 teletankolás; opcionális kúti liter. Részleges tankolási modell jelenleg nincs. |
| Út végi értesítés | Km, liter, l/100 km; becslés/adathiány jelzése; külön csatorna, útazonosítónként egyszer. |

Az értesítéshez POST_NOTIFICATIONS engedély és engedélyezett „Út végi összesítés” csatorna kell. Telefonos értesítés, AA felugró megjelenítés nincs garantálva. Kényszerleállított/kikapcsolt telefon nem tud időben értesíteni. Demó nem küld valódi útösszesítést.

Az útnaplóban: indulás/befejezés/lezárás oka, távolság, liter, átlag, időtartam, átlag/max sebesség, mozgás/alapjárat ideje, alapjárati üzemanyag, max RPM, min/max vízhőfok, átlagos terhelés, forrás és lefedettség. A 0.14 opcionálisan külön részletes GPS/OBD-fájlt is készít; az összesítő CSV továbbra sem tartalmaz helyadatot. CSV: UTF-8 BOM, pontosvessző, idézett mezők, UTC időbélyeg; a telefon helyi időt mutat.

### Telefonos navigáció (0.11)

Alul **Műszerfal · Napló · Rádió**. Jobb felső fogaskerék → Kapcsolat → Fogyasztás és autó beállításai. Az egykori „Út/nap” és „Napló” egyesült. Napló tetején napi összesítés, lenyitható részletek és aktív út, utána tank és előzmények. Android Back és fejléc nyíl visszalép. Rádió nélküli változatban csak két alsó pont van.

## 5. Forrástérkép és adatfolyam

| Modul / fontos osztály | Feladat |
|---|---|
| `elm`: ElmTransport, ElmSession, parser | Egyidejűség nélküli soros parancs/válasz, ELM inicializálás, ASCII keretek |
| `obd`: PidRepository, Telemetry | Támogatási bitmaszk, lekérdezési ütemezés, friss/hibás/utolsó adat |
| `trip`: FuelCalculator, FuelWindow | Nyers forrásválasztás és ettől külön 10 s kijelzési simítás |
| `trip`: TripComputer, DailyComputer | Integrálás, közösen mért szakaszok, napváltás |
| `trip`: JourneyLog, JourneyCsv | Úthatárok, tank, statisztika, napló és export; tiszta Kotlin |
| `trip`: DrivingAdvisor | Kalos vezetési/váltási jelzés |
| `connection`: DashboardController/Graph | Közös StateFlow, valódi és demó ág, 250 ms számítási tick, 1 s határidő-ellenőrzés |
| `connection`: ObdService, AutoObd | Foreground service, ébresztés, AA/BT életciklus, visszakapcsolódás |
| `connection`: JourneyStore, SessionStore | Helyi SharedPreferences/JSON mentések |
| `connection`: JourneyNotifications | Külön út végi csatorna, lokalizált összesítés, deduplikáció |
| `dashboard`: ElmDashboard, JournalScreen | Compose navigáció, napi/út/tank UI; rádiót az app injektálja |
| `dashboard-graphics` | Canvas műszerek |
| `auto-media`: DashboardMediaService, MediaDashboard | Framework MediaBrowser/MediaSession, ExoPlayer, metaadat/borító, négy adatlap (motor/fogyasztás/szenzor/tank) |
| `auto` | Car App Library debug út, főleg DHU vizsgálathoz |
| `app` | Belépési pont, engedélyek és flavorok |

Fő adatút: `ELM → parser → PID repository → DashboardController → nyers fuel/trip/day/journey + FuelWindow → StateFlow → telefon és AA`. A napi/út/tank integrátor külön állapot; a kijelzés simításából egyiket se tápláld.

## 6. Mentés és adatmegőrzés

- `elm-dashboard`: eszközválasztás, fogyasztási profil, automatikus kapcsolódás, utolsó mérés, `daily-v1` napi JSON.
- `elm-journal`, `v1` JSON: tank, tankolások, utak és aktív vázlat egy snapshotban. Normál checkpoint 5 s; lezáráskor külön mentés. `.apply()` aszinkron, váratlan leállásnál az utolsó szakasz elveszhet.
- `elm-radio`: állomás, kedvencek, saját streamek, automatikus indulás/szünet állapot.
- `elm-journey-notifications`: már értesített útazonosítók. Régi történetet alkalmazásindításkor nem értesít újra.
- Frissítés `adb install -r`; ne távolítsd el az appot adatmegőrzés helyett. A már telepített debug apphoz ugyanaz az aláírókulcs kell; új laptop vagy CI kulcsa eltérhet.
- A demó ugyanazon parser/repository láncon fut, de külön napi adatokkal és valódi mentések nélkül.
- Saját backend vagy analitika nincs; 0.14-től opcionális saját WebDAV-feltöltés van. Rádió internetkapcsolata nem továbbít OBD-telemetriát az alkalmazásból.

## 7. Build, CI, teszt és kiadás

JDK 17; Gradle 8.13; AGP 8.13.2; Kotlin 2.2.21; compile/target SDK 36, min 26; Build Tools 35.0.0. A függőségek a `gradle/libs.versions.toml`-ban vannak. Compose BOM 2025.12.01, Car App Library 1.7.0, Media3 1.9.3.

Elsődleges fejlesztői változat `mediaDebug`; csomag `hu.elmdash.app.media`. `unsupportedDebug` külön `hu.elmdash.app.unsupported`, kísérleti CAL/DHU. `phoneDebug` és `phoneRelease` AA nélkül. `mediaRelease` és `unsupportedRelease` szándékosan tiltott. A felhasználó telefonjáról a fölösleges második ELM-appot már eltávolítottuk.

Teljes lokális/CI-ellenőrzés a projekt gyökerében:

```sh
./gradlew :elm:test :obd:test :trip:test :connection:testDebugUnitTest \
  :auto:testDebugUnitTest :auto-media:testDebugUnitTest \
  :app:lintPhoneDebug :app:lintUnsupportedDebug :app:lintMediaDebug \
  :app:assemblePhoneDebug :app:assembleUnsupportedDebug :app:assembleMediaDebug \
  :app:assemblePhoneRelease
python3 scripts/verify_variants.py
```

A `.github/workflows` már tartalmaz buildet, tesztet, lintet, izolációellenőrzést és APK/report artifactot. A GitHub repository: https://github.com/szabbenjamin/ELM-dash. A korábbi tesztek helyben futottak. Ne nevezd a helyi buildet sikeres GitHub CI-futásnak.

## 8. Bizonyított állapot és következő ellenőrzések

- Korábbi valós Carpuride-próba: médiaapp megjelenik, változó adatok látszanak; rádió tényleges lejátszása igazolt. A kezdeti CAL-app normál AA launcherben nem jelent meg.
- 0.7: valódi Kalos fogyasztási adatút és 10 s simítás AA-sessionben igazolt. Nem tankolással kalibrált fogyasztás.
- 0.10: **128 automatizált teszt** sikeres (elm 7, obd 6, trip 45, connection 26, auto 6, auto-media 38), három debug lint 0 hiba, változatizoláció sikeres. Értesítés Robolectricben ellenőrzött, beleértve a véges próbálkozás utáni 180 s lezárást és deduplikációt.
- 0.11: UI-átrendezés, build/lint/izoláció és valódi telefonos navigáció ellenőrizve. A korábbi 128 tesztet ehhez a tisztán UI-változtatáshoz nem futtattuk újra.
- Nyitott autós próba: új borító olvashatósága 1/3 és 2/3 nézetben, 8 s rádiósor-váltás, tankbecslés tényleges teletankolástól, új útnapló és út végi értesítés valódi motorleállítás/BT-kimaradás után.
- Nyitott kalibráció: több valós tankolás és mért út alapján VE/korrekció pontossága. Ne tüntesd fel szenzorosan mért tankszintként.
- Folyamatkilövés/újraindítás adathiányt okoz; a meglévő mentést helyreállítjuk, de háttérben garantált „mindig működik” ígéret nincs.
- A 2026-10-09-i kérés kifejezetten GPS-rögzítést és saját WebDAV-ra automatikus útvégi feltöltést kér. Fizetős Play-regisztráció vagy felhőfiók vásárlása továbbra sem kérés. GitHubhoz most dokumentált, megosztható projektet szeretne.

## 9. Fejlődési idővonal röviden

0.1–0.2: moduláris OBD/telefon/CAL, DHU és Kalos grafika. 0.3: működő AA médiaút. 0.4: rádió és naptári napi fogyasztás. 0.5: automatikus OBD. 0.6: rádiókimaradás-kezelés/AA autoindítás. 0.7: valódi Kalos MAP/IAT fogyasztás és 10 s simítás. 0.8: fix l/100 km, váltakozó rádiósor, magasabb borító. 0.9: tank és útnapló. 0.10: út végi értesítés, 3 perces türelem. 0.11: hárompontos telefonos menü és összevont Napló.

Új kérés teljesítésekor ezt a dokumentumot a végleges megvalósításhoz igazítsd, és őrizd meg a nem igazolt feltételek jelölését.

## 0.11.1 felületi pontosítás

A felhasználó kérésére a „Demó indítása” gomb kikerült a telefonos Műszerfalról és a Kapcsolat oldalról. A belső szimulátor teszteléshez megmaradt. Ne tedd vissza a gombot új kérés nélkül.

## Új felhasználói kérés és működés: benzinköltség (0.12)

Induláskor töltsön le átlagos magyar benzinárat, és az út összesítőjében jelenjen meg a becsült Ft-költség. Megvalósítás: Holtankoljak.hu 95 E10 átlagárkártya, aszinkron kérés valódi útindításkor, útonként rögzített ár, maximum 7 napos offline cache jelzéssel. Napló, értesítés és CSV tartalmazza. A következő felhasználói pontosítás szerint a régi, ár nélküli utak a friss aktuális árat egyszer megkapják (0.12.1); meglévő ár nem változik. Utólag rögzítettként jelölt. A teljes specifikáció, mentési formátum és tesztek: [TRIP_COST.md](TRIP_COST.md). A felhasználó közvetlen mobilhálózatos autós ellenőrzése még hátravan.

## Legfrissebb döntés: saját tankolási ár (0.13)

A felhasználó az internetes átlagár helyett a tankoláskor megadott összegből és literből akar számolni. Ez felülírja a 0.12-es automatikus árlekérést és kitöltést. Az új utak a legutóbbi áras saját tankolást használják; megadott ár nélkül ismeretlen a költség. A korábbi utak rögzített ára megmarad. Az aktuális részletek a [TRIP_COST.md](TRIP_COST.md) tetején vannak.

## 0.14 – részletes menetnapló (2026-10-09)

Percenkénti GNSS-kérés járó motornál, másodpercenkénti OBD-pillanatkép, lezárt JSONL-fájlok és WorkManager WebDAV-feltöltés. Beállítások/engedélyek/export: új Útvonal és WebDAV oldal. Alapból kikapcsolt; valódi útvonal vagy hitelesítő adat nem kerülhet a repóba. Teljes szervizdiagnosztikát nem állítunk: a jelenlegi 11 standard PID és számított fogyasztás a lefedettség. Részletek: [ROUTE_RECORDING.md](ROUTE_RECORDING.md).

## 0.15 – úttérkép és HTML-export

Felhasználói kérés: teljes képernyős útrészletek interaktív OSM-térképpel, perces sebesség/fogyasztáslista nélkül; a telefon HTML-térképet is gyártson és töltsön fel WebDAV-ra. Megvalósítás: RouteReport közös HTML-generátor, beágyazott Leaflet, külön OSM-csempebetöltés, szakaszolt GPS-vonal, JourneyDialog WebView. JSONL+HTML közös sikerjelölő; régi JSONL-feltöltések HTML-pótlása. Nincs visszamenőleges GPS és automatikus távoli fájlletöltés. Részletes működés/korlátok: ROUTE_RECORDING.md.

## 0.15.1 – keresztény rádiók

Hat új alapállomás: Mária Rádió, Magyar Katolikus Rádió, UCB 1/2 (angol), ERF Plus/Jess (német). A telefon és az Android Auto közös katalógusát bővítik; a mentett kiválasztás, kedvencek és saját állomások megmaradnak. Hivatalos források és streampróba: [RADIO_STATIONS.md](../RADIO_STATIONS.md).

## 0.16.0 – Útinform első változat (2026-10-19)

Új `traffic` Kotlin modul, külön location FGS, titkosított NAP HTTP Pull URL és fogaskerék → Útinform oldal. AA-kapcsolatra indul, az OBD/útvonalnapló nélkül is működik. Koordinátás DATEX II 2.x teljes pillanatképek, hely- és irányszűrés, 20 másodperces deduplikált alsó médiasor. Nincs útillesztés vagy navigációs útvonal; TMC/OpenLR kimarad. Éles NAP-előfizetés és Carpuride-próba hátravan. Beállítás és részletes korlátok: [TRAFFIC.md](TRAFFIC.md). A korábbi kutatási dokumentum történeti javaslat, nem a végleges implementáció specifikációja.

## 0.16.1 – becsült hátralévő hatótáv

Képlet: tankban becsült liter × 100 / átlagos l/100 km. Legalább 1 km párosított fogyasztás/távolság után a mai átlag az alap; addig a legutóbbi 30 lezárt út párosított liter/távolság összegének aránya (legalább 1 km). Nem az utak átlagainak számtani átlaga, és nem a 10 s fogyasztás. Hiányzó tank/pozitív átlag esetén nincs szám. Adathiány bizonytalansági jelzéssel; mindig becslés. 10 km-re lefelé kerekített kijelzés, 0–10 km között <10. Telefon: Műszerfal és Napló tankkártya. AA: Tank adatlap, valamint a harmadik 8 s-os alsó sor (motor → rádió → hatótáv); Útinform prioritása megmarad. Demó nem használ valódi úttörténetet hatótávhoz. A számítás nem ír át tankot/naplót.

## 0.16.2 – sűrített AA-borító

A compactOverview 3×3 rácsban kilenc adatot mutat: RPM/váltási jel, vízhőfok, sebesség; 10 s és napi fogyasztás, terhelés; hatótáv, tank%, feszültség. A teljes blokk továbbra is x=64..448, y=44..277, alul nincs adat az AA vezérlői miatt. Hiányzó adat szürke, tank/hatótáv adathiány borostyánsárga és kérdőjel. Hosszabb számok szélességre illeszkednek. A host mérete és sötétítése továbbra sem vezérelhető.

## 0.16.3 – magasabb AA-adatblokk

Valódi fejegységes felhasználói visszajelzés alapján a kilencmezős blokk magassága 233-ról 269 pixelre nőtt (+15,5%), változatlan felső éllel és szélességgel. Új tartomány: x=64..448, y=44..313; y=324-től üres háttér. A betűk is kissé nagyobbak. Ez felülírja a korábbi 277 pixeles alsó határt.

## 0.16.4 – hatmezős AA-borító

Felhasználói pontosítás: a borítóról lekerült a sebesség, feszültség és tank%. Az új 2×3 rács: RPM/vízhőfok, 10 s/napi fogyasztás, motorterhelés/hatótáv. Magasság változatlan (44..313), szélesebb cellák és nagyobb számok. A váltakozó hatótávsorból is kikerült a tank%. Telefonos diagnosztikai adatok megmaradnak.

## 0.16.5 – RPM mellett terhelés

Felhasználói sorrend: első sor RPM / terhelés, második sor 10 s / napi fogyasztás, harmadik sor vízhőfok / hatótáv. Méretek változatlanok.

## 0.16.6 – hatótáv színküszöbök

Felhasználói kérésre az AA hatótávcsempe színe a nyers becsült hatótáv szerint változik: <100 km piros; 100..150 km (150 kizárva) narancs; >=150 km alap kékesszürke; hiányzó adat szürke. A bizonytalanság továbbra is kérdőjel, de önmagában már nem színez narancsra. Pontosan 100 km narancs, pontosan 150 km alap szín.

## 0.16.9 – zöld hatótáv

Felhasználói pontosítás: a nyers becsült hatótáv >250 km esetén zöld. Pontosan 250 km még kékesszürke. További határok változatlanok: 150..250 alap; 100..<150 narancs; <100 piros; ismeretlen szürke.

## 0.16.8 – Szívólevegő az AA borítón

Az alsó sor három mező: vízhőfok, szívólevegő (IAT), hatótáv. A felső két sor változatlan. IAT frissen semleges, elavulva utolsó érték szürkén; nem külső hőmérséklet. A média frissítési kulcsa az IAT változását is követi.

## 0.16.9 – Váltakozó napi / út csempe

A napi fogyasztás csempéje 8 másodpercenként vált az aktuális út távolságára és átlagfogyasztására. Élő módban kizárólag a journal.active összesítését használja, nem a kapcsolati munkamenetet; lezárt vagy hiányzó út esetén nincs kitalált érték. A váltás a médiaszolgáltatás monoton órájáról működik friss OBD-minta nélkül is.

## 0.16.10 – Állandó háromoszlopos fogyasztássor

Felhasználói pontosítás: nincs napi/út váltogatás. A második sor három állandó csempe: 10 s fogyasztás, napi átlag, aktuális útátlag (alatta megtett km). A többi sor változatlan.

A telefonos útrészletek megnyitása automatikusan betölti az OSM alaptérképet, ha van GPS-pont. Az exportált HTML továbbra is kézi betöltést használ. Hálózati hibánál a vonal és az összesítés elérhető marad.

## 0.16.11 – Világosabb AA csempék

A kompakt borító csempéinek RGB-csatornái 18%-kal a fehér felé tolódnak az AA sötétítő rétegének ellensúlyozására. A színkategóriák, küszöbök és az üres háttér változatlanok.

## 0.16.12 – Adatblokk +4% magasság

A kompakt AA-adatblokk 4%-os függőleges skálázása y=44 rögzített felső éllel; alsó él körülbelül y=324. A feliratok és számok a csempével együtt nőnek. y=335 alatt továbbra is üres háttér.

## 0.16.13 – Napi távolság az alsó középső csempén

A felhasználó levetette a szívólevegőt az AA borítóról; helyén MA KM, a helyi naptári nap összes távolsága. Az útátlag alól kikerült az aktuális út távolsága. A telefonos IAT és fogyasztásbecslés változatlan. A napi távolság változása frissíti a média borítót.

## 0.16.14 – Elrendezési félreértés javítása

A felhasználó a teljes útátlag + útkilométer csempét kérte alul középre, nem külön napi km-t. Végleges sorrend: RPM / terhelés; 10 s fogyasztás / napi fogyasztás; vízhőfok / útátlag alatta útkm / hatótáv. Napi km és IAT nincs a borítón.

## 0.16.15 – ÚT KM önálló nagy érték

Alul középen kizárólag az aktuális út megtett távolsága, nagy számmal, ÚT KM címkével. Az útátlag és a kisebb másodlagos szám kikerült. Élő módban journal.active.summary.distanceKm, hiányzó aktív útnál szürke vonal; nem napi vagy kapcsolati munkamenet-távolság.

## 0.16.16 – Motorleállási várakozás két perc

A megfigyelt 0 RPM lezárási küszöbe 8 helyett 120 másodperc. A futásidő nélküli RPM-alapú újraindításdetektálás ugyanezt követi. Kapcsolatvesztés továbbra is 180 másodperc; a külön ECU-futásidő-visszaesés és kézi leállítás szabálya változatlan.

## 0.16.17 – Állandó rádiónév az alsó sorban

A felhasználó kérésére a média lejátszó alsó szövegsora kizárólag a kiválasztott rádió neve. Nem vált motoradatra, hatótávra vagy Útinformra. A lejátszási állapot továbbra is a vezérlők/PlaybackState feladata. Az Útinform-adatgyűjtés beállítása változatlan, de ezen a soron nincs eseménymegjelenítés.

### NAP méretkorlát-javítás (0.16.18)
24 MiB válasz engedélyezett; részletes, URL-t nem közlő hibák. Éles 12,6 MB XML feldolgozása ellenőrizve, de valamennyi rekord helyadata/típusa jelenleg kihagyott; események tényleges megjelenítéséhez helyfeloldás kell. Lásd TRAFFIC.md.

## 0.17.0 — autófenntartási funkciók, részleges blokkos folyamat

- Parkolóhely: külön opt-in a Műszerfalon; friss, legfeljebb 10 s / 25 m GPS-fix az első megfigyelt 0 RPM-nél, előtte járó motor szükséges. Újraindulás törli, 120 s után parkolás. OBD foreground service alatt 2 s GPS-frissítés, pontos + háttérhelyengedéllyel, bekapcsolás után OBD újraindítás. Az időpont és pontosság mentett, kétes / hiányzó fixből nincs helyettesítő adat. A rendszer nem tud garantálni pontos GPS-t a motorleállás pillanatában.
- Telefonos Műszerfal láthatósága alatt friss helyet kér. 50 m-en túli eltávolodásnál (mindkét pont hibasugarán kívül) OSM-kártya és gyalogos OSM-útvonaltervező. Új motorindulás nélkül a mentett függő pont 2 perc után a UI-ban parkolásnak számít akkor is, ha az OBD-szolgáltatás időközben leállt. Ez utolsó ismert hely, nem folyamatos autókövetés.
- Szerviznapló: Napló és Műszerfal kártyából teljes képernyős szerkesztő. Elvégzés dátuma/km, opcionális következő km/hónap, szabad név/megjegyzés. Nincs kitalált gyári intervallum. Kilométeróra kézzel frissítendő; OBD-mérésből nem következtetünk teljes járműfutásra. Legutolsó azonos nevű szerviz határozza meg a következő esedékességet, 30 nap / 500 km előjelzéssel. WorkManager napi értesítés (Android késleltetheti).
- Külön opt-in hűvös idő emlékeztető: Open-Meteo, legutóbbi legfeljebb 7 napos parkolóhely 0,01 fokra kerekítve. 7 °C vagy az előző legfeljebb 7 napos értékhez képest 10 °C lehűlés, legfeljebb heti értesítés. Nem IAT és nem TPMS. Internet / parkolóhely nélkül nincs időjárásos jelzés.
- Teletankolás: kötelező tele-megerősítés. Csak üzemanyag nettó fizetendő összege, kedvezmény után. Két teletankolás közt eltérés literben és a becsült mennyiség százalékában; adathiánynál nem kalibrálható. Javasolt szorzó kijelzés, automatikus módosítás nincs.
- Ez az a nap! Rádió a hivatalos MP3 streammel bekerült.
- **Még nincs kész:** blokk fotózása/felismerése, tételek és kedvezmények automatikus elkülönítése. A felhasználónak feltett döntés: külön OpenAI API szerver (külön költség), vagy helyi OCR. Ebbe a Codex-chatbe nincs automatikus telefonos képbefogadó végpont. A válasz függőben; blokkfotót nem küldünk külső szolgáltatásnak. A felismert adatokhoz majd szerkeszthető megerősítő lépés és bizonytalan kedvezmények jelzése szükséges.

## 0.17.2 — helyi blokkfelismerés és egyszerű tankolási folyamat

A felhasználó helyi OCR-t választott, felhő nélkül. Bundled ML Kit Latin 16.0.1, nincs képfeltöltő/API-végpont. Fotózás FileProvider cache-fájlba vagy képválasztás; legfeljebb 2400 px hosszú oldalra dekódolás API 28+-on. A kép nem kerül naplóba/WebDAV-ra; saját cache-kép felismerés után törlődik, félbehagyott kamera esetén következő belépéskor 24 órán túli maradványok törlődnek. Kiválasztott eredeti képet nem törlünk.

A konzervatív parser csak az egyértelmű üzemanyagtételből vesz litert és összeget; összesent soha. Explicit üzemanyag-negatív Ft kedvezményt levon, egyéb kupon/százalékos/vegyes kedvezménynél összeg üres és kézi pontosítás szükséges. A felismert nyers szöveg és magyarázat lenyitható, memóriában él. Gyakori OCR I/l csere kezelve a liter mennyiségnél. Nincs garancia minden blokkelrendezés felismerésére.

A végső termékkérés: kevés kognitív terhelés. Teljes képernyős Tankolás → három mező (benzinfajta, liter, kedvezmény utáni üzemanyag-Ft) → Ellenőrzés → nagy összesítő + tele-megerősítés → Mentés. Nem mentünk OCR-ből automatikusan. Külön explicit „csak tele tank” út adatok nélkül megőrzi a korábbi működést. A benzinfajta opcionális új mező a FullRefill/JourneyStore-ban, régi rekordok továbbra is betölthetők. Naplóban nagy liter/Ft és benzinfajta.

Telefon: újracsatlakoztatás után 0.17.2 adatmegőrző telepítése sikeres. Szintetikus blokk képválasztásos OCR-próbája: benzin 95 E10, 20 liter, 11 700 Ft (300 Ft üzemanyagkedvezmény levonva, kávé kizárva), 585 Ft/l. A három mező és az ellenőrző összesítő telefonon ellenőrizve, mentés nélkül megszakítva. Saját tesztkép törölve. Valódi blokk/kamerás teljes folyamat még nincs ellenőrizve.

## 0.17.3 — automatikus parkolóhely és szervizkártya

Felhasználói kérésre nincs parkolóhely opt-in kapcsoló. A régi enabled preferenciát nem olvassuk. Az OBD szolgáltatás pontos + háttérbeli helyengedéllyel automatikusan figyeli a parkolóhelyet; futás közben megadott engedélynél is felveszi a location foreground típust. A motorleállás/GPS-frissesség/120 s/50 m szabály változatlan. Engedélyvesztéskor nincs új helyrögzítés, a korábbi parkolóhely megmarad. A műszerfal hiányzó helyengedélyhez gombot ad; a beállításokból visszatérve újraellenőrzi. Az útvonalrögzítés külön opt-in marad.

A szervizkártya a Műszerfalon a motoradatok után, a Naplóban a tankolási összesítés után található, nem az oldal tetején.

## 0.17.4 — külön Szerviz menüpont

A szervizadatok kizárólag az alsó sáv Szerviz pontján érhetők el. Közvetlen, görgethető oldal, nincs köztes kártya vagy megnyitandó modal. Műszerfal és Napló szervizkártyái eltávolítva; adatok, értesítések és mentési működés változatlanok. Telefonos sorrend: Műszerfal, Napló, Szerviz, Rádió; rádió nélküli variánsban az első három.

### Parkolóhely telefonos előnézet és térképjavítás

Debug MainActivity `parking_preview=true` extra: kizárólag memóriabeli, véletlen irányú 2,5 km-es pont az első friss telefonhelyből. Külön TESZT jelzés, bezárható, tíz perc kompozíciós idő után kikapcsol; nézet újranyitásakor újraindulhat. Nem módosít ParkingStore-t, naplót, útvonalat vagy WebDAV-adatot. Előnézethez hálózati hely is elfogadott, max. 500 m hibával; valódi parkolás határai változatlanok. Release figyelmen kívül hagyja az extrát.

Telefonon felfedezett fehér OSM: a DOM térképmagasság 0 px volt a százalékos méretezés ellenére. Javítás explicit 220 CSS px a 220 dp WebView-hoz, ResizeObserver/invalidateSize, parkolóhely változásakor új WebView.

A parkolókártya Navigálás az autóhoz gombja vízszintesen középre igazított (felhasználói pontosítás).

### Gyalogos navigáció Google Maps alkalmazással

Felhasználói kérésre a középre igazított gomb explicit com.google.android.apps.maps célcsomagnak küld google.navigation:q=lat,lon&mode=w intentet. A kiindulópontot a Maps az aktuális helyből kezeli. Az alkalmazáson belüli alaptérkép továbbra is OSM. Hiányzó/letiltott Google Maps esetén érthető üzenet, nincs csendes böngészős vagy autós navigációs fallback. Hivatalos specifikáció: https://developer.android.com/guide/components/google-maps-intents .
