# ELM Dash — projektátadás és termékkövetelmények

Állapot: **2026-09-18 · 0.13.0**. Ez a fájl a beszélgetés nélkül is használható fejlesztői összefoglaló. A régebbi tesztek bizonyítékai a [VALIDATION.md](../VALIDATION.md)-ben, a buildparancsok a [README.md](../README.md)-ben vannak.

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
- 14 beépített állomás, többek között Retro; a katalógus és forrásai: [RADIO_STATIONS.md](../RADIO_STATIONS.md). A Roxy külön felmerült igényként; a tényleges elérhetőséget a katalógusból ellenőrizd, ne feltételezz működő streamet pusztán a beszélgetés alapján.
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
| Igazolt motorleállítás | 8 s folyamatosan megfigyelt 0 RPM lezárja. |
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

Az útnaplóban: indulás/befejezés/lezárás oka, távolság, liter, átlag, időtartam, átlag/max sebesség, mozgás/alapjárat ideje, alapjárati üzemanyag, max RPM, min/max vízhőfok, átlagos terhelés, forrás és lefedettség. Nincs GPS, útvonal vagy cím. CSV: UTF-8 BOM, pontosvessző, idézett mezők, UTC időbélyeg; a telefon helyi időt mutat.

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
- Nincs backend, felhőszinkron vagy analitika. Rádió internetkapcsolata nem továbbít OBD-telemetriát az alkalmazásból.

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
- A felhasználó nem kért GPS-t, fizetős Play-regisztráció megvásárlását, felhőfiókot vagy automatikus adatfeltöltést. GitHubhoz most dokumentált, megosztható projektet szeretne.

## 9. Fejlődési idővonal röviden

0.1–0.2: moduláris OBD/telefon/CAL, DHU és Kalos grafika. 0.3: működő AA médiaút. 0.4: rádió és naptári napi fogyasztás. 0.5: automatikus OBD. 0.6: rádiókimaradás-kezelés/AA autoindítás. 0.7: valódi Kalos MAP/IAT fogyasztás és 10 s simítás. 0.8: fix l/100 km, váltakozó rádiósor, magasabb borító. 0.9: tank és útnapló. 0.10: út végi értesítés, 3 perces türelem. 0.11: hárompontos telefonos menü és összevont Napló.

Új kérés teljesítésekor ezt a dokumentumot a végleges megvalósításhoz igazítsd, és őrizd meg a nem igazolt feltételek jelölését.

## 0.11.1 felületi pontosítás

A felhasználó kérésére a „Demó indítása” gomb kikerült a telefonos Műszerfalról és a Kapcsolat oldalról. A belső szimulátor teszteléshez megmaradt. Ne tedd vissza a gombot új kérés nélkül.

## Új felhasználói kérés és működés: benzinköltség (0.12)

Induláskor töltsön le átlagos magyar benzinárat, és az út összesítőjében jelenjen meg a becsült Ft-költség. Megvalósítás: Holtankoljak.hu 95 E10 átlagárkártya, aszinkron kérés valódi útindításkor, útonként rögzített ár, maximum 7 napos offline cache jelzéssel. Napló, értesítés és CSV tartalmazza. A következő felhasználói pontosítás szerint a régi, ár nélküli utak a friss aktuális árat egyszer megkapják (0.12.1); meglévő ár nem változik. Utólag rögzítettként jelölt. A teljes specifikáció, mentési formátum és tesztek: [TRIP_COST.md](TRIP_COST.md). A felhasználó közvetlen mobilhálózatos autós ellenőrzése még hátravan.

## Legfrissebb döntés: saját tankolási ár (0.13)

A felhasználó az internetes átlagár helyett a tankoláskor megadott összegből és literből akar számolni. Ez felülírja a 0.12-es automatikus árlekérést és kitöltést. Az új utak a legutóbbi áras saját tankolást használják; megadott ár nélkül ismeretlen a költség. A korábbi utak rögzített ára megmarad. Az aktuális részletek a [TRIP_COST.md](TRIP_COST.md) tetején vannak.
