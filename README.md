# ELM Dash — Android Auto OBD dashboard MVP

**Fejlesztőként itt kezdd:** [AGENTS.md](AGENTS.md) → [részletes projektátadás](docs/PROJECT_HANDOFF.md). Autó, konfiguráció, felhasználói kérések, architektúra és még nem igazolt működés egy helyen. [GitHub feltöltési útmutató](docs/GITHUB.md).

**Aktuális: 0.13.0.** Telefonos menü: Műszerfal · Napló · Rádió; kapcsolat a jobb felső fogaskeréken. Napi és útadatok a Naplóban. Út végén összesítő értesítés, OBD-kimaradásnál 3 perces türelem.

Elsődleges célpont az **Android Auto fejegység**; a telefon a Bluetooth-adatolvasást, a beállításokat és a demót biztosítja. Közvetlen **Bluetooth Classic ELM327 → Kotlin → Android Auto** adatút. Nem kell Torque vagy külön PID-szolgáltató. Magyar, sötét Compose telefonos kísérőfelület; széles és összecsukható kijelzőkhöz alkalmazkodó rács.

**Állapot (2026-09-16):** a hagyományos médiafelületet használó próba **megjelent a valódi, vezeték nélkül kapcsolódó Carpuride-on**; ezt a felhasználó a „2400 rpm / 89 °C” tesztfelirattal visszaigazolta. Erre épül az új **ELM Dash • AA Média** (`mediaDebug`) változat, a közös demó- és élő ELM-adatfolyammal. A **0.4.0** már valódi internetes rádiót, 14 beépített állomást, kedvenceket és naptári napi fogyasztási összesítést is tartalmaz. A jobb oldali kis médiakártya elrendezését továbbra is az AA host dönti el. A médiafelület dashboardként használata nem hivatalosan támogatott út. A korábbi `unsupportedDebug` / Car App Library változatot a telefon normál AA-indítója továbbra is elutasítja; az a DHU-s kísérleti felület marad. A bizonyítékok és a még nyitott hardvertesztek: [VALIDATION.md](VALIDATION.md).

A Car App Library főképernyőjén **nagy, színes fordulatszám–vízhőfok grafika** és négy adatsor mutatja az RPM-et, a vízhőfokot, a fogyasztást és a sebességet. A **2005-ös Kalos 1.2 8V szívó, kézi váltós profilhoz** terhelést figyelembe vevő fel/le váltási jelzés is tartozik. A jelmagyarázat és a becslés feltételei: [DRIVING_GUIDANCE.md](DRIVING_GUIDANCE.md).

**Új a 0.9-ben:** 45 literes Kalos-tank becsült töltöttsége, „Teletankoltam” gomb, automatikus útnapló részletes adatokkal és CSV-mentéssel. Használat és korlátok: [TANK_AND_JOURNAL.md](TANK_AND_JOURNAL.md).

**A 0.8-ban:** fix l/100 km kijelzés, „Most” előtag nélkül; az AA alsó sora 8 másodpercenként vált a műszeradatok és a rádió neve között. A borító műszerblokkja kb. 15%-kal magasabb.

**A 0.7-ben:** Kalos MAP/IAT-alapú fogyasztásbecslés, 10 másodperces gördülő Most érték és a Carpuride kis kártyájának takarását kerülő borító.

**A 0.6-ban:** rádió automatikus újracsatlakozása kimaradás után, az utoljára kiválasztott állomás indítása Android Auto csatlakozáskor, kézi szüneteltetés elsőbbségével. Megmarad az automatikus OBD-kapcsolat és a külön kapcsolatvesztési figyelmeztetés.

**Verzió:** 0.13.0 · **Minimum:** Android 8 / API 26 · **Target/compile:** Android 16 / API 36.

**Új a 0.13-ban:** tankoláskor megadott literből és fizetett összegből számított saját literár; ebből útköltség a Naplóban, az értesítésben és CSV-ben. Az internetes árlekérés megszűnt. [Forrás, offline működés és részletek](docs/TRIP_COST.md).

## Gyors kipróbálás autó nélkül

1. Telepítsd az `ELM Dash • AA Média` debug APK-t, vagy buildeld a `mediaDebug` változatot. A Car App Library / DHU próbához továbbra is az `unsupportedDebug` tartozik.
2. A 0.11.1-ben a felhasználó kérésére nincs telefonos demóindító gomb. Az alábbi demóleírás a belső szimulátor és a korábbi tesztek működését ismerteti; a szimulátort automatizált tesztek használják.
3. A Műszerfalon változó RPM, sebesség, fogyasztás és motoradat jelenik meg. A **Napló** lapon külön nő a mai és az aktuális úthoz tartozó összesítés.
4. A **Rádió** lapon indítsd el az Oxygen Music vagy egy másik állomás lejátszását. Ehhez internetkapcsolat kell; OBD-adapter nem szükséges.
5. A műszerfal **Leállítás** gombja megszünteti az adatfolyamot. Az utolsó számértékek a helyükön maradnak, szürkén. `—` csak akkor jelenik meg, ha még nem érkezett érték.

A demó egy `ElmTransport` implementáció: ASCII CAN-válaszokat ad a valódi ELM parsernek, a PID repositorynak és a trip computernek. Minden képernyő jelöli a szimulációt. Nem menti felül az utolsó valódi mérést. A demóhoz nincs szükség Bluetooth-engedélyre; önmagában nem indít foreground service-t.

## Mit tartalmaz?

| Modul | Feladat |
|---|---|
| `elm` | Transport interfész, ELM inicializálás, soros parancskezelés, ASCII/parser, ECU-azonosítók |
| `obd` | Mode 01 PID-ek, támogatási bitmaszkok, ütemezett lekérdezés, minőség és adatfrissesség |
| `trip` | ECU/MAF/MAP fogyasztás, 10 s kijelzési átlag, távolság/üzemanyag, úthatárok és naptári napi összesítés; Kalos fokozatbecslés és stabil váltási jelzés |
| `connection` | Android Bluetooth SPP, megszakítható socket, foreground service, újracsatlakozás, demó, helyi mentés |
| `dashboard` | Compose Material 3 telefonos dashboard, adapterválasztás, beállítások |
| `dashboard-graphics` | Közös Canvas műszergrafika: RPM, vízhőfok, színek, nyilak és megtartott szürke értékek |
| `auto-media` | Media3 ExoPlayer rádió, állomások/kedvencek/keresés, MediaBrowserService és kísérleti AA műszer-borítókép |
| `auto` | Kizárólag debugban használt Car App Library szolgáltatás, grafikus főlap és három részletes adatlap |
| `app` | Alkalmazás belépési pont és buildváltozatok |

```text
app ── dashboard ── connection ── trip ── obd ── elm
 │                      ▲
 ├─ mediaDebug ───── auto-media ── dashboard-graphics
 └─ unsupportedDebug ─ auto ───── dashboard-graphics
```

A JVM-modulok Android nélkül tesztelhetők. Az adott változat telefonos és autós UI-ja egy folyamaton belül ugyanazt a `StateFlow<DashboardState>` adatfolyamot olvassa; nem nyit két Bluetooth-kapcsolatot. A dependency injection egyszerű, explicit `DashboardGraph`, további keretrendszer nélkül.

## Build

Szükséges JDK 17, Android SDK 36, Build Tools 35.0.0 és internet az első buildhez. Android Studióban nyisd meg ezt a mappát, és várd meg a Gradle Sync-et. A projektben Gradle Wrapper van; külön Gradle-telepítés nem szükséges.

```sh
# macOS / Linux
chmod +x gradlew  # első klónozás után, ha a futtatási bit hiányzik
export ANDROID_HOME="$HOME/Library/Android/sdk"  # Linuxon a saját SDK mappád
./gradlew :app:assemblePhoneDebug :app:assembleUnsupportedDebug :app:assembleMediaDebug

# Windows PowerShell: a JAVA_HOME és ANDROID_HOME beállítása után
.\gradlew.bat :app:assemblePhoneDebug :app:assembleUnsupportedDebug :app:assembleMediaDebug
```

Az SDK helye `local.properties` fájlban is megadható: `sdk.dir=/sajat/Android/sdk`. Ez a géphez kötött fájl nincs a forráscsomagban.

| Változat | Csomagnév | APK |
|---|---|---|
| `phoneDebug` | `hu.elmdash.app` | `app/build/outputs/apk/phone/debug/app-phone-debug.apk` |
| `unsupportedDebug` | `hu.elmdash.app.unsupported` | `app/build/outputs/apk/unsupported/debug/app-unsupported-debug.apk` |
| `mediaDebug` | `hu.elmdash.app.media` | `app/build/outputs/apk/media/debug/app-media-debug.apk` |
| `phoneRelease` | `hu.elmdash.app` | Aláírandó telefonos kiadás |

**Nincs `unsupportedRelease` vagy `mediaRelease`:** a variant filter letiltja. A `phone` build nem függ az `auto` vagy `auto-media` modultól, és nem tartalmaz Android Auto szolgáltatást vagy kategóriabejegyzést. A debug APK fejlesztői kulccsal aláírt, nem Play-kiadás. A könyvtárverziókat a `gradle/libs.versions.toml` rögzíti: Kotlin 2.2.21, AGP 8.13.2, Gradle 8.13, Compose BOM 2025.12.01, Car App Library 1.7.0, Media3 ExoPlayer 1.9.3.

### Telepítés USB-n

1. Samsungon kapcsold be a Fejlesztői beállításokat és az USB-hibakeresést; csatlakozáskor fogadd el a számítógép kulcsát.
2. Ellenőrizd az eszközt, telepítsd az APK-t, nyisd meg:

```sh
adb devices
adb install -r app/build/outputs/apk/media/debug/app-media-debug.apk
adb shell am start -n hu.elmdash.app.media/hu.elmdash.app.MainActivity
```

Több eszköznél az `adb -s ESZKOZAZONOSITO …` formát használd. APK-fájlból is telepíthető a telefonon, az adott fájlkezelő telepítési engedélyével.

## Párosítás és első valódi mérés

1. Az ELM327 legyen az autó OBD-csatlakozójában, a gyújtás legyen bekapcsolva. Az első beállítást álló autóban végezd.
2. Samsung **Beállítások → Kapcsolatok → Bluetooth**: párosítsd az adaptert. A PIN az adapter dokumentációjában van; gyakori a `1234` vagy `0000`.
3. Állítsd le a Torque és más OBD-appok kapcsolatát: egy SPP adapter jellemzően egy klienssel működik.
4. ELM Dash → **Kapcsolat → Bluetooth-hozzáférés engedélyezése / Eszközlista frissítése**. Android 12+-on szükséges a **Közeli eszközök** engedély. Az értesítési engedély a leállítható állapotjelzőhöz ajánlott; elutasítása nem tiltja le a Bluetooth-hozzáférést.
5. Válaszd ki az adaptert: azonnal mentődik. Az AA Média automatika a következő autós csatlakozáskor használja; kézi próbához **OBD-kapcsolat indítása**. Párosított eszközöket listázunk, helyadatot és Bluetooth-keresési engedélyt nem kérünk.
6. Ha normál SPP-vel nem csatlakozik, leállítás után próbáld a **Kompatibilis RFCOMM módot**. Ez a nyilvános insecure RFCOMM API-t használja; nincs rejtett csatorna-1 reflection.
7. A tesztelt Kalos nem ad `015E` üzemanyagáramot vagy MAF-ot. Alapértelmezett profilja **Kalos 1.2** (MAP/RPM/IAT becslés); a már elmentett profil megmarad. Módosítás: mérés leállítása → **fogaskerék → Fogyasztás és autó beállításai**. Más autó üzemanyagát és profilját külön állítsd be; dízelhez a Kalos-modell nem használható.
8. Indítsd újra a mérést, majd ellenőrizd az RPM, sebesség és hőmérséklet életszerűségét.

Bluetooth **Classic SPP** szükséges. BLE-only, Wi-Fi és USB adapter nincs az MVP-ben. A telefonra kötött USB a build/telepítésre és az Auto fejlesztői tesztre szolgál, nem az OBD-adatokra.

## Automatikus indulás az Android Autóval

Egyszeri beállítás a **Kapcsolat** lapon:

1. Párosítsd és válaszd ki az **OBD-adaptert**. A választás azonnal mentődik; nem kell a Kapcsolódás gombot megnyomni a mentéshez.
2. Az **Automatikus OBD-kapcsolat** maradjon bekapcsolva. A `mediaDebug` frissítése ezt egyszer alapból bekapcsolja; a későbbi kikapcsolást tiszteletben tartja.
3. Az **Autós fejegység Bluetooth-eszköze** alatt válaszd a Carpuride-ot (a teszttelefonon `W113`). Ez az ébresztőeszköz, nem az OBD-adapter. Más Bluetooth-eszközök csatlakozása nem indít olvasást.
4. **Automatikus háttérkapcsolat engedélyezése** → a rendszer akkumulátor-korlátozás alóli kivételének engedélyezése. A Közeli eszközök és az értesítések engedélye is szükséges az olvasáshoz, illetve a figyelmeztetéshez. A beállítás nem kapcsolja be helyetted a Bluetooth-t.

Működés:

- A hivatalos `CarConnection` API jelzi az Android Auto projekcióját. A figyelő az alkalmazás folyamatában fut, a rádió kiválasztásától és hanglejátszásától függetlenül.
- A kiválasztott fejegység rendszerből érkező Bluetooth ACL-csatlakozási eseménye egy nem futó appfolyamatot is felébreszthet. Ez indítja a háttérbeli OBD-próbát, és áthidalja a vezeték nélküli AA indulását. Ha két percen belül nem lesz AA-kapcsolat, ez az előzetes munkamenet leáll.
- A mentett adaptert közvetlen RFCOMM-csatlakozással próbáljuk elérni. Nincs folyamatos rádiós keresés. Ha az adapter vagy az ECU még nem válaszol, növekvő várakozás után 30 másodpercenként újra próbálkozunk; valódi adatok érkezésekor az olvasás folytatódik.
- Az AA megszűnése öt másodperc türelmi idő után leállítja **az automatikusan indított** OBD-mérést. Rövid vezeték nélküli megszakadás nem bontja feleslegesen. A kézzel indított mérést nem állítja le az AA bontása.
- A **Leállítás** az aktuális AA-munkamenetre szünetelteti az automatikát. Új AA-csatlakozáskor, vagy az **Automatika folytatása** gombbal ismét engedélyezhető. Aktív demót/kézi mérést az automatika nem indít újra.
- **Nincs kapcsolat az autóval** jelenik meg a telefonon és az AA médiafőcímében, ha nincs élő kapcsolat. Sikertelen kapcsolódáskor külön értesítés is jön, egy megszakadás alatt egyszer. Sikeres adatolvasáskor a figyelmeztetés törlődik. Nem indul automatikus demó, a korábbi értékek szürkék maradnak.
- Visszaigazolt AA-csatlakozáskor a rádió is elindítja az utoljára kiválasztott állomást. Ez külön kikapcsolható a Rádió lapon. Az alkalmazás nem kényszeríti előtérbe a telefon vagy a fejegység képernyőjét.

**Platformkorlát:** a CarConnection állapotfigyelő önmagában nem képes egy nem futó folyamatot felébreszteni. Ezért szerepel külön a kiválasztott fejegység Bluetooth-ébresztése. Vezetékes AA-nál, Bluetooth-ébresztő nélkül a hideg indulás nem garantált, amíg a host nem köti a média-szolgáltatást vagy az appot egyszer meg nem nyitod. A rendszerbeállításokban végzett **Kényszerített leállítás** után az appot újra meg kell nyitni; ezt nem kerüljük meg. Samsung Mélyalvó alkalmazások listája és egy AA-frissítés befolyásolhatja a működést.

Az automatika és a hiányjelzés tesztelve szimulált AA-eseményekkel; a W113-on a teljes hideg indulás és a valódi OBD-kapcsolat próbája még hátravan, mert a fejegység/autó a fejlesztéskor nincs jelen. Nem tekintjük ezt már igazolt autós tesztnek.

## Háttérműködés és hibák

- A felhasználó a látható Activityből indítja a `connectedDevice` foreground service-t. Folyamatos értesítés és **Leállítás** gomb tartozik hozzá.
- A kézi mérés maximum 4 órás. Az automatikus mérés az AA-kapcsolat élettartamát követi: a korlátozott idejű ébresztési zár csak visszaigazolt AA-kapcsolat mellett újul meg. Nincs bootkor indulás vagy egész napos Bluetooth-keresés.
- Socket csatlakozási limit: 12 s; normál parancs: 4 s; kezdeti protokollkeresés: 15 s. Timeout/cancel lezárja a socketet, ezért késői bájtok nem keverednek a következő PID-be.
- Kézi mérésnél az újracsatlakozás legfeljebb 5 próbát tesz növekvő várakozással. AA által indított automatikus mérésnél ezután 30 másodpercenként tovább próbálkozik, amíg a munkamenet aktív. 30 s stabil mérés után a próbálkozásszámláló újraindul. Folyamatos ECU-adathiány újracsatlakozást vált ki; az `ATRV` válasz önmagában nem számít ECU-válasznak.
- A hiányzó adat nem nulla. Sikertelen olvasásnál vagy 5 másodperces elévülésnél az utolsó számérték szürkén, „Utolsó ismert adat” jelzéssel marad a helyén. A számítás csak friss adatot kap. A számjegyek tabuláris szélességűek; a minta és az időbélyeg együtt frissül, ezért az új minták nem villannak hiányzóra két frissítés között. Az Auto szürke színezésének végső megjelenítését a host kezeli.
- Samsung akkukezelésnél szükség esetén: **Alkalmazások → ELM Dash → Akkumulátor → Korlátlan**. Ezt az alkalmazás nem módosítja automatikusan.
- Beállítás és az utolsó valódi megfigyelés helyben mentődik 5 másodpercenként. Nincs felhő, analitika vagy helyadatgyűjtés. A 0.13-ban ismét csak a `mediaDebug` használ internetet a rádióhoz; árletöltés nincs. OBD-adatot nem küldünk ezeknek a szervereknek. Folyamatleállás után az előző mérés külön jelenik meg; az új kapcsolat új utat kezd.

## PID-ek és mértékegységek

`A`, `B`: a pozitív Mode 01 válasz adatbájtjai. Az első `0100`, szükség szerint `0120`, `0140` maszk adja a támogatást. Válaszoló ECU-k közül RPM-et támogató egységet választunk, és annak azonosítójához ragaszkodunk. Sikertelen maszklekérdezés esetén az érintett tartományt megpróbáljuk kiolvasni; nem nyilvánítjuk automatikusan nem támogatottnak.

| PID | Adat | Átalakítás | Kért időköz* |
|---|---|---|---|
| `0104` | Motor terhelése | `100 × A / 255` % | 1 s |
| `0105` | Hűtőfolyadék | `A − 40` °C | 3 s |
| `010B` | MAP abszolút nyomás | `A` kPa | 1,5 s |
| `010C` | RPM | `(256A+B)/4` | 0,5 s |
| `010D` | Sebesség | `A` km/h | 0,5 s |
| `010F` | Beszívott levegő hőmérséklet / IAT | `A-40` °C | 2 s |
| `0110` | MAF légtömegáram | `(256A+B)/100` g/s | 0,7 s |
| `0111` | TPS fojtószelep | `100 × A / 255` % | 1,5 s |
| `011F` | Motor futásideje | `256A+B` s | 2 s |
| `0142` | ECU tápfeszültség | `(256A+B)/1000` V | 3 s |
| `015E` | Üzemanyagáram | `(256A+B)/20` l/h | 0,7 s |
| `ATRV` | Adapter tápfeszültség | Szöveges voltérték | `0142` helyettesítése |

\* Soros lekérdezés: a tényleges frissítés a busz és az adapter sebességétől függ, nem garantált Hz. Három sikertelen minta után 15 s az adott PID újrapróbálási időköze. A `0142` ECU-feszültség és az `ATRV` adapterfeszültség eltérhet; a forrást kiírjuk. A MAP abszolút nyomás, nem turbónyomás. Az MVP Mode 01 egykeretes válaszokat támogat: fejléc nélküli, 11/29 bites CAN és háromfejléces legacy ISO/KWP/J1850. Nincs VIN, hibakódtörlés, gyári PID vagy többkeretes ISO-TP szolgáltatás.

## Fogyasztás és az „indítás óta” jelentése

Elsődleges forrás a `015E`. Ennek hiányában a kiválasztott benzines profilnál:

```text
l/h = MAF[g/s] × 3600 / (AFR × sűrűség[g/l]) × korrekció
l/100 km = l/h × 100 / sebesség[km/h]    (legalább 5 km/h-nál)
```

A benzin profil AFR-je 14,7 és sűrűsége 745 g/l; az E10 profil közelítése 14,1 és 750 g/l. Ezek állandó becslési paraméterek, nem a tank aktuális összetételének mérései. A **Kalos 1.2** profilban MAF nélkül a mért MAP, IAT és RPM alapján számolunk közelítést. A 0.7-ben a korábban ki nem választott profil alapértéke Kalos; a felhasználó által már elmentett profil megmarad. Az ECU/MAF mindig elsőbbséget kap. A Kalos névleges 1.2 motorjának számításhoz használt térfogata **1150 cm³**, a VE (töltési tényező) kiinduló értéke **0,80**. Ez nem gyári kalibráció, a fogaskerék → Fogyasztás és autó beállításai alatt módosítható. Hiányzó vagy elöregedett MAP/IAT/RPM esetén nem használunk kitalált levegőhőmérsékletet vagy helyettesítő fogyasztást; a felület megnevezi a hiányzó bemenetet. Dúsítás, dízel változó légfelesleg és motorféki befecskendezés-leállítás nem következtethető ki ebből megbízhatóan. A felirat **MAF-becslés**, illetve **MAP-becslés • kalibrálandó**, az AA érték előtt `≈` jel áll. A fix VE miatt a MAP-becslés hibája a fordulatszámtól és terheléstől függhet; az egyetlen tankolási korrekció sem helyettesít egy mért motortérképet.

```text
levegősűrűség[kg/m³] = MAP[kPa] × 1000 / (287,05 × (IAT[°C] + 273,15))
becsült MAF[g/s] = levegősűrűség × (1150 / 1 000 000) × RPM/120 × VE × 1000
```

A négyütemű motor két fordulatonként szív be egy lökettérfogatot. A további üzemanyagképlet a fenti MAF-képlettel azonos. Források: [MathWorks speed-density modell](https://in.mathworks.com/help/autoblks/ug/spark-ignition-engine-simple-speed-density-breathing-model-1.html), [IDAE 2005 járműadatok, Kalos 1.2 SOHC 1150 cm³](https://coches.idae.es/storage/pdf/Guiasemestre22005.pdf), [OBD PID 0F dekódolás](https://www.csselectronics.com/pages/obd2-pid-table-on-board-diagnostics-j1979).

### 10 másodperces gördülő átlag

A telefon, az AA szövege és borítója ugyanazt a simított értéket mutatja. Az utolsó **10 s** üzemanyag- és távolságmintáit azonos időablakban integráljuk; `l/100 km = ablak liter / ablak km × 100`. A kijelzés egysége mindig **l/100 km**; 5 km/h alatt „—” jelenik meg. A belső liter/óra adat megmarad az összesítéshez, de a fogyasztási kártya nem vált erre az egységre. Induláskor a rendelkezésre álló, rövidebb ablakból számolunk. Adathiánynál az utolsó szám szürke marad, a simítóablak kiürül; hiányzó időt nem hidal át. A **napi és útösszesítés a nyers érvényes mintákat használja**, így a kijelzés késleltetése nem módosítja a literszámlálót.

Az út a motor járásának első érvényes RPM-mintájánál kezdődik. A motor futásidejének visszaesése új utat jelez (a 65535 → 0 túlcsordulást kivéve). Ha futásidő nincs, legalább 8 másodpercig megfigyelt nulla RPM utáni indulás kezd új utat. Ez utóbbi egyszerű MVP-szabály start-stop autóban új utat eredményezhet; kézi nullázás is van.

**Nem számolunk visszamenőleg:** ha járó motorhoz később csatlakozol, csak a kapcsolódás után megfigyelt rész mérhető. Az UI csak észlelt indulás / legfeljebb 10 s ECU-futásidő esetén jelzi az indítástól történő megfigyelést. Gyújtásállapot önmagában nem szabványos kötelező PID; a mérés motorfutáshoz kötött.

A `TripComputer` 250 ms-os minták között trapézszabállyal összegzi a sebességet és a liter/órát. Az alapjárati fogyasztás is beleszámít. A fogyasztási átlag számlálója és nevezője **ugyanazokból a szakaszokból** képződik, ahol mindkét adat rendelkezésre áll. 100 m alatt nincs átlag. Adathiány és 2 s feletti mintaköz nem kerül becsléssel kitöltésre; az átlag ilyenkor részleges. Az adatlefedettség az aktív megfigyelési időre vonatkozik; bontáskor kihagyott teljes időszakokat a „részleges” jelzés mutatja.

## Mai fogyasztás: naptári nap, éjféli fordulás

A **mai átlag a telefon helyi dátuma szerint éjfélkor vált napot**. Motorleállítás, újraindítás, OBD-bontás, az aktuális út kézi nullázása és az alkalmazás újranyitása nem nullázza. A folyamatban lévő és a leállított képernyő is frissíti a dátumot; folyamatújraindításkor az aktuális nap mentése töltődik vissza.

- A napi átlag az adott nap érvényes, azonos lefedettségű liter- és távolságösszegéből készül, nem az utak átlagainak átlagából.
- A napi liter, kilométer, átlag és lefedettség 5 másodpercenként és szabályos leállításkor helyben mentődik. Az utolsó 31 megfigyelt dátum marad meg; a felület a mai napot mutatja.
- A demó saját napi számlálót használ és nem írja át a valódi napi adatokat.
- Éjfélkor az új nap még `—` átlagot mutat, amíg nincs legalább 100 m megfelelő mérés. A napváltáson átívelő mintaköz nem kerül egyik naphoz sem becsléssel hozzáadva.
- Az app indulása előtti vagy OBD-kimaradás alatti vezetést nem lehet utólag kiszámolni; a napi adat a ténylegesen megfigyelt szakaszok összesítése. Erőszakos folyamatleállításnál az utolsó mentés utáni néhány másodperc elveszhet.
- 5 km/h alatt a pillanatnyi l/100 km nem értelmezhető: a cím és a kép ilyenkor „—” jelet mutat, l/100 km egységgel. Az alapjárati üzemanyag a napi átlagba beleszámít.

## Internetes rádió

A `mediaDebug` változat **Rádió** lapján és az AA **Rádióállomások / Kedvenc rádiók** listájában használható.

- **14 beépített állomás:** Oxygen Music, Retro Rádió, Rádió 1, Best FM, Sláger FM, Jazzy, Kossuth, Petőfi, Bartók, Dankó, Hír FM, 103.9 a ROCK, InfoRádió, Oxygen Classic Rock. Ellenőrzött streamcímek és források: [RADIO_STATIONS.md](RADIO_STATIONS.md).
- **Lejátszás, szünet, leállítás, előző/következő állomás**, telefonos ékezetfüggetlen keresés, mentett kedvencek. Az AA médiavezérlő ugyanazt a lejátszót vezérli; a hangalapú keresési callback állomásnévre keres, a hangfelismerés a host feladata.
- Legfeljebb **20 saját HTTP/HTTPS stream** menthető névvel, törölhető és kedvencnek jelölhető. Közvetlen MP3/AAC/Ogg vagy HLS cím szükséges, nem egy lejátszós weboldal. DRM és bejelentkezés nem támogatott. A Roxy pontos, felhasználó által ígért linkje még nincs megadva; saját állomásként azonnal felvehető.
- A megadott Oxygen-cím pontosan `https://oxygenmusic.hu:8443/oxygenmusic` (AAC).
- Media3 ExoPlayer kezeli a dekódolást, pufferelést, hangfókuszt és a hangkimenet lecsatlakozását. Rövid hálózatkimaradást a puffer áthidalhat; hosszabb megszakadásnál a lejátszó várakozik, és automatikusan újracsatlakozik. A kihagyott műsort nem tárolja: újracsatlakozáskor az élő adást folytatja.
- Átmeneti hálózati hiba, HTTP 408/429/5xx vagy váratlan streamvég után az újrapróbálkozás várakozása **2, 5, 10, 20, majd legfeljebb 30 másodperc**. Egy 30 másodpercig beragadt pufferelés is újracsatlakozást vált ki. A használható internet visszatérésének eseménye előrehozza a következő próbát. HTTP 401/403/404, hibás formátum és dekódolási hiba végleges hibajelzést ad; ilyenkor másik állomás vagy javított URL szükséges.
- Az app az **alapértelmezett, Android által internetképesnek igazolt hálózatot** figyeli, nem pusztán bármely Wi-Fi kapcsolatot. Ez a vezeték nélküli AA helyi Wi-Fi hálózata mellett is fontos. A várakozó lejátszás életben tartja a médiaszolgáltatást; a felirat jelzi az internet hiányát vagy az újracsatlakozást.
- **Rádió indítása Android Auto csatlakozáskor:** alapból bekapcsolt, a telefon Rádió lapján állítható. A hivatalos CarConnection projekciós jelzése indítja az utoljára kiválasztott állomást. Önmagában az OBD vagy a fejegység Bluetooth-csatlakozása még nem indít rádiót. Nincs bootkori rádióindítás; AA nélkül a telefonos alkalmazás megnyitása sem kezdi el a lejátszást.
- **A kézi Szünet/Leállítás törli a függő újracsatlakozást**, és az adott AA-munkamenetben tiltja az automatikus újraindítást. A következő új AA-kapcsolat ismét indíthat rádiót. Az átmeneti hangfókuszvesztést a lejátszó kezeli; tartós hangfókuszvesztés vagy a hangkimenet lecsatlakozása szüneteltet, és a hálózat visszatérése nem éleszti fel a rádiót.
- Az AA bontása **5 másodperc után** leállítja az AA által indított rádiót; a rövid kapcsolatingadozás nem indít új adást. Az AA előtt kézzel elindított telefonos lejátszást az AA bontása nem állítja le. A rendszer Kényszerített leállítás művelete után az appot újra meg kell nyitni; ez nem folyamatos háttérindítási garancia.
- A lejátszás külön `mediaPlayback` foreground service állapotot és médiaértesítést használ. Az OBD-olvasás és a rádió egymástól független: a rádió szüneteltetése nem állítja le az OBD-t; az OBD leállítása nem némítja el a rádiót.
- `PLAYING` kizárólag az ExoPlayer tényleges `isPlaying` állapotából képződik; kapcsolódáskor és automatikus helyreállítás alatt `BUFFERING`, végleges hibánál `ERROR`. Nincs néma hanggal vagy hamis lejátszási állapottal aktivált kártya.
- A telefon internetkapcsolatát használja. Vezeték nélküli AA mellett is szükséges működő mobiladat vagy internetes Wi-Fi. Az app nem változtatja a hangerőt vagy a hálózati beállításokat.

## Android Auto: hivatalos korlát és külön lab út

**Az általános OBD-dashboard nem hivatalos Car App Library kategória.** A szabályos telefonos alkalmazás működése ettől független. A host a sablonokat rajzolja; a Compose UI nem vetíthető át tetszőlegesen. Nincs hivatalos dashboard-beillesztő API a Coolwalk médiakártyájához. Az alábbi médiaút a meglévő médianézet kísérleti használata.

Az `unsupportedDebug` a nyilvános Car App Library `PaneTemplate` API-ját használja, de **IOT kategóriát deklarál kizárólag a fejlesztői host-felderítéshez**. Ettől az app nem lesz kategóriának megfelelő IoT-alkalmazás, és nem állítjuk, hogy Playre jóváhagyható. Megnyitáskor közvetlenül a grafikus dashboard jelenik meg. Alul a Motor / szenzorok és az Út és fogyasztás gomb nyitja a részletes adatlapokat; a Motor lapon külön Szenzorok gomb van. A színes hátterek a műszergrafikába rajzolódnak; a teljes hostfelület hátterét nem módosítjuk. Car API 4-től közös nagy műszerkép, régebbi hoston soronként kisebb grafika jelenik meg. A lapok legfeljebb négy sort használnak. A sablon- és sorcímek állandók, a frissítési kérés legfeljebb 2 másodpercenként történik; a host tovább korlátozhatja.

Az engedékeny `HostValidator` csak debugban fut. A kísérleti csomag külön alkalmazásazonosítót kap. Ez helyi fejlesztői út, **nem garantált megoldás bármely Samsung / Android Auto / fejegység kombinációra**. A korábbi beszélgetésben említett „Unknown sources elég” állítást pontosítani kell: a Google jelenlegi dokumentációja szerint ez a kapcsoló **nem vonatkozik a Car App Library appokra**.

### Valódi Carpuride: kísérleti AA Média út

1. Telepítsd a **`mediaDebug`** változatot; a neve **ELM Dash • AA Média**, csomagja `hu.elmdash.app.media`.
2. A telefon Android Auto beállításaiban a verzióinformációt tízszer megérintve engedélyezd a fejlesztői menüt. Ebben **Alkalmazásmód → Fejlesztői**, **Ismeretlen források → be**. A sikeres Carpuride-próbán ez a két beállítás volt aktív; a vezeték nélküli AA bekapcsolva maradt.
3. A **Kapcsolat** lapon egyszer válaszd ki az OBD-adaptert és az autós Bluetooth-fejegységet, valamint engedélyezd az automatikus háttérkapcsolatot. Az AA Média változatban az automatika alapból be van kapcsolva. Autó nélkül a Demó továbbra is kézzel indítható. Másik ELM Dash változat vagy Torque ne tartson kapcsolatot ugyanazzal az adapterrel.
4. Csatlakozz a Carpuride-hoz, és az AA alkalmazáslistájából nyisd meg az **ELM Dash • AA Média** appot. Ha friss telepítés után még hiányzik, ellenőrizd a telefon **Egyéni alkalmazásindító** listáját, majd álló helyzetben bontsd és csatlakoztasd újra az AA-t. Nem szükséges a DHU head unit server.
5. AA-csatlakozáskor alapból az utoljára kiválasztott rádió indul. A telefon **Rádió** lapján vagy az AA **Rádióállomások** listájában választhatsz másikat. A normál lejátszás/szünet gomb a valódi rádiót vezérli; előző/következő az állomások között vált.
6. A **cím a pillanatnyi és mai átlagfogyasztást** mutatja l/100 km-ben. Az **512 × 512 pixeles borító felső, középső részének négy műszere: RPM, vízhőfok, 10 s fogyasztás, mai átlag**. Az alsó kb. 45% üres háttér az AA által rárajzolt feliratok és gombok számára; oldalt is van vágási tartalék. A 0.8-as műszerblokk magassága 202-ről 233 pixelre nőtt (+15,3%); álló helyzetben a 10 s mező „—” jelet mutat. A színek és váltási nyíl megmaradnak. A host a képet átméretezheti, vághatja vagy elrejtheti; a teljes képernyő színét az app nem állíthatja.
7. A **Műszerek / fogyasztás** böngészőmappa vagy a **Műszerlap váltása** egyedi művelet váltja az alsó adatsort: RPM/vízhőfok/terhelés, napi liter/km és útátlag, MAP/TPS/feszültség. Az alsó sor **8 másodpercig az aktuális műszerlapot, majd 8 másodpercig az állomás nevét** mutatja. Így például az „1742 rpm • 86 °C • 18 %” és az „Oxygen Music” váltja egymást; az állomás neve nem a hosszú szenzorsor végére kerül. Szünet, kapcsolódás és rádióhiba külön jelölést kap. A váltás álló OBD-adatok mellett is működik; csak a szöveg cserélődik, a borító nem villan újra.
8. A **Demó indítása** és **OBD / demó leállítása** külön művelet. Megnyitáskor nem indul automatikusan szimuláció vagy Bluetooth-kapcsolat. Élő kapcsolatot a mentett adapterrel az automatika indít, vagy szükség esetén kézzel a telefonon indítható.
9. Az AA többablakos kezdőnézetében ellenőrizd a kisebb médiakártyát valódi rádiólejátszás közben. A kártya rendelkezésre állását, oldalát és méretét az AA/fejegység dönti el; az app nem tudja kikényszeríteni a jobb oldali egyharmados elrendezést.

A `mediaDebug` a már ténylegesen elfogadott hagyományos Android `MediaBrowserService` / `MediaSession` API-t kapcsolja össze a Media3 ExoPlayerrel, a Car App Libraryből csak a hivatalos `CarConnection` állapotfigyelőt használva. A média változat nem deklarál Car App Library szolgáltatást vagy IOT kategóriát. A saját műszergrafika adatkimaradáskor szürkül, a szöveges szenzoradatok „utolsó” jelölést kapnak. A napi összesítés korábban érvényesen mért értéke megmarad. A demó felirata leállítás után is megmarad.

**A rádió valódi médiafunkció; az OBD-adatok médiametaadatként és borítóképként megjelenítése továbbra is nem támogatott debug kísérlet.** Ez önmagában nem jelent Play-kompatibilitást. A telefon–fejegység kombináció vagy egy AA-frissítés megváltoztathatja az elfogadását. Normál ADB-telepítést használunk; nincs Play-regisztráció, telepítőazonosító-hamisítás vagy külső APK-függőség. A médiaböngészőhöz az ellenőrzött Android Auto csomag/UID és a saját UID fér hozzá.

### Fejegység nélkül: Desktop Head Unit (DHU)

1. Android Studio → SDK Manager → SDK Tools → **Android Auto Desktop Head Unit Emulator** telepítése.
2. Telepítsd az `unsupportedDebug` APK-t; nyisd meg és indíts demót.
3. A telefon Android Auto beállításaiban a verzióinformációra 10 koppintással nyisd meg a fejlesztői módot, majd a menüből **Start head unit server**.
4. USB-n csatlakoztatott, feloldott telefon mellett:

```sh
adb forward tcp:5277 tcp:5277
cd "$ANDROID_HOME/extras/google/auto"
./desktop-head-unit
# Ha a DHU támogatja, explicit ADB-kapcsolat: ./desktop-head-unit --adb=5277
# DHU 2.x alternatíva: ./desktop-head-unit --usb
```

5. Fogadd el a telefonon megjelenő szükséges első kapcsolódási lépéseket, és keresd az **ELM Dash • Auto Lab** alkalmazást. Az Auto fejlécében közvetlenül is indítható a **Demó**, és a **Leállítás** gomb is elérhető; az első valódi adapterpárosítás és kapcsolatindítás a telefonon történik.
6. A telefonon futó demó adatait az Auto adatlapokon is látni kell. A DHU nem OBD-szimulátor; a demó adatokat maga az app állítja elő.

**Ellenőrzött működés (2026-09-15):** Samsung Galaxy Z Fold4, Android 16, Android Auto 17.6.663454 és macOS DHU 2.0 között az ADB-s kapcsolat működött. A helyben telepített app megjelent az Auto indítójában; mindhárom adatlap, az autós Demó/Leállítás gomb és az utolsó értékek szürke megőrzése működött. Ehhez a próbához nem kellett az Unknown sources kapcsoló. A közvetlen `--usb` mód ezen a gépen szinkronizációs hibával leállt; az ADB-alagút sikeres volt. Ez a teszt valódi telefonos Android Auto futás, szoftveres fejegységgel.

Teszt után zárd be a DHU-t, a telefon Android Auto fejlesztői menüjében állítsd le a head unit servert, majd `adb forward --remove tcp:5277`. A részletes ellenőrzési jegyzőkönyv a [VALIDATION.md](VALIDATION.md) fájlban van.

Ha a host nem sorolja fel / elutasítja a helyben telepített sablonos appot, az `Unknown sources` kapcsoló nem bizonyít megoldást. A Google dokumentált, megbízható telepítési forrása lehet saját Play Console **Internal App Sharing / Internal Test Track**, de ez sem ad kategóriaengedélyt vagy garantált terjeszthetőséget az OBD-dashboardnak. Ez a projekt nem publikál Playre, nem módosítja az Android Autót, és nem tartalmaz root/Xposed vagy installer-hamisító automatizmust. A telefonos demó és a hostfüggetlen sablontesztek ettől még futtathatók.

### A korábbi Car App Library út fizikai fejegységen

A jelenlegi Samsung/Android Auto pároson a Car App Library változathoz a sima ADB-telepítés nem elegendő: ez az app a telefon AA-indítólistájából is hiányzik. Az újratelepítés és a „Fejlesztői” alkalmazási mód próbája nem oldotta fel az elutasítást. Ne kezeld a DHU-s képernyőképet a fizikai fejegységes működés bizonyítékaként. A Google dokumentált következő lehetősége a tényleges Play Console Internal App Sharing telepítés; a telepítő nevének átírása nem egyenértékű vele. Ehhez még nincs feltöltés vagy sikeres teszteredmény.

Előbb a telefonon indíts élő OBD-kapcsolatot, majd csatlakoztasd az Android Autót. Ha a host elfogadja a lab appot, az indítójából nyitható meg. A kísérleti integrációt álló járműben ellenőrizd. A képernyő frissítési viselkedését, adatfrissességét és esetleges kategóriaelutasítását külön kell validálni. A telefonos UI nem függ ettől.

## Tesztelés

```sh
./gradlew :elm:test :obd:test :trip:test :connection:testDebugUnitTest :auto:testDebugUnitTest :auto-media:testDebugUnitTest
./gradlew :app:lintPhoneDebug :app:lintUnsupportedDebug :app:lintMediaDebug
./gradlew :app:assemblePhoneDebug :app:assembleUnsupportedDebug :app:assembleMediaDebug :app:assemblePhoneRelease
```

A tesztek lefedik a fejléc/echo/hibás válaszokat, ECU-választást, PID-képleteket, stale adatot, feszültség fallbacket, soros hozzáférést, idle fogyasztást, MAF opt-int, adathiányos átlagot, újraindulást és runtime wrapet. Külön tesztek ellenőrzik a Kalos váltási logikáját, a hideg motor/adathiány/fokozatváltás miatti nyílelvételt és a határértékek körüli stabilitást. Robolectric teszt ellenőrzi a grafikus Auto főlapot, a három részletes PaneTemplate lapot és a demó/offline jelzést.

A konkrét build és telefonos próba eredménye: [VALIDATION.md](VALIDATION.md). **A statikus médiapróba valódi Carpuride-on megjelent; a valódi ELM-adatkapcsolatot és a mérési pontosságot ez nem igazolja.**

### Következő bővítések

- Valódi adapterekből származó anonimizált válasz-fixture gyűjtemény; több K-line / klón kompatibilitási teszt.
- Felhasználó által állítható start-stop / úthatár-politika; korábbi utak listája és export.
- BLE/Wi-Fi transport, felhasználó által megadott gyári PID-ek, pontosabb üzemanyagmodell csak megfelelő szenzoradatokkal.
- Hivatalos Android Auto út csak megfelelő új kategória/API vagy ténylegesen szabályos termékfunkció esetén.

## Elsődleges források

Ellenőrizve: 2026-09-15–16. A források protokoll- és platformreferenciák; a megvalósítás saját Kotlin-kód.

- [ELM Electronics — ELM327 datasheet](https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf): AT-parancsok, válaszformátumok és tápfeszültség.
- [CSS Electronics — OBD2 intro](https://www.csselectronics.com/pages/obd2-explained-simple-intro), [OBD2 DBC](https://www.csselectronics.com/pages/obd2-dbc-file): PID-adatmodellek és dekódolás.
- [Android — Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).
- [Android — foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types).
- [Android for Cars — kategóriák és manifest](https://developer.android.com/training/cars/apps/library/set-up-project).
- [Android for Cars — tesztelés és Unknown sources korlát](https://developer.android.com/training/cars/testing).
- [Android for Cars — Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu).
- [Android for Cars — template restrictions](https://developer.android.com/training/cars/apps/library/template-restrictions).
- [Car App Library kiadások](https://developer.android.com/jetpack/androidx/releases/car-app).

- [Android for Cars — MediaSession és egyedi műveletek](https://developer.android.com/training/cars/media/enable-playback).

- [Media3 — lejátszóesemények és tényleges isPlaying állapot](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).

- [Android — CarConnection API](https://developer.android.com/training/cars/apps/library/connection-api).
- [Android — háttérből indított foreground service kivételek](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- [Android — Bluetooth ACL implicit broadcast kivételek](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions).

### A rádió helyreállításának megvalósítása

Az `auto-media` modulban a `RadioRecovery` kezeli a lejátszási szándékot és az újrapróbálkozást, a `RadioNetwork` az alapértelmezett hálózatot, a `RadioErrors` az átmeneti hibákat. Az `AaRadioSession` kezeli az AA-munkamenet és a kézi szünet elsőbbségét; az `AutoRadio` kapcsolja ezt a médiaszolgáltatáshoz. Az `ACCESS_NETWORK_STATE` engedély csak a média változatba kerül. Az OBD-adatokat a rádió nem küldi a streamkiszolgálónak.

Platformforrások: [Media3 lejátszóesemények](https://developer.android.com/media/media3/exoplayer/listening-to-player-events), [Android hálózati állapot és callbackek](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).

### Út végi összesítés

A 0.10-es verzió útlezáráskor telefonos értesítést küld a km / liter / l/100 km adatokkal. Motorleállítás: 8 s megfigyelt nulla RPM; kapcsolatvesztés: 3 perc friss motoradat nélkül. Rövid BT-kimaradás nem külön út. Részletek: [tank és útnapló](TANK_AND_JOURNAL.md#út-végi-értesítés-010).

### Telefonos navigáció (0.11)

Az alsó menü: **Műszerfal · Napló · Rádió** (a rádió nélküli változatban két pont). A jobb felső fogaskerék nyitja a Kapcsolatot, innen a fogyasztási profil és a kalibráció is elérhető. A Napló egyesíti a mai összesítést, a folyamatban lévő utat, a tankolást és az úttörténetet. A napi kártya részletei lenyithatók. A rendszer vissza gombja visszavezet a beállításokból az előző lapra.

A 0.12.1 a felhasználó kérésére az ár nélküli régi utakhoz is egyszer rögzíti a frissen letöltött aktuális benzinárat. A meglévő árak változatlanok; az utólagos rögzítés külön jelzett. Hiányzó literadat helyére nem ír kitalált fogyasztást.
