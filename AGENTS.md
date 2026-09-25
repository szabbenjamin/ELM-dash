# Fejlesztő agentek belépési pontja

Ez az ELM Dash Android-projekt. A felhasználó elsődleges célpontja **Android Auto**, a telefon kísérőfelület. Az aktuális átadás dátuma 2026-09-18, appverzió 0.13.0.

## Kezdés

1. Olvasd el a [projektátadást](docs/PROJECT_HANDOFF.md): autó, hardver, elfogadott működés, konfiguráció és nyitott feladatok.
2. A [VALIDATION.md](VALIDATION.md) különíti el az automatizált tesztet, a telefonos próbát és a valódi autós visszaigazolást. Egy korábbi autós próba nem bizonyít egy későbbi verziót.
3. Build/telepítés: [README.md](README.md). GitHub: [docs/GITHUB.md](docs/GITHUB.md).
4. A kód az aktuális implementáció forrása; az átadás az elfogadott termékigényt rögzíti. Eltérésnél keresd meg az okot, ne tekintsd a régi README bekezdéseit új felhasználói kérésnek.

## Megőrzendő viselkedés

- Közvetlen Bluetooth Classic SPP ELM327; Torque nem függőség. Egy közös controller és egy valódi OBD-kapcsolat.
- A használt telefonos csomag `hu.elmdash.app.media` (`mediaDebug`). Ne telepíts mellé másik ELM-változatot rutinból; a felhasználó kérte a duplikált app eltávolítását.
- Hiányzó/elavult adat nem nulla. Utolsó szám maradjon szürkén, ne villogjon vagy ugráljon a layout.
- Fogyasztás kijelzése l/100 km, a pillanatnyi helyén 10 s gördülő liter/távolság arány. 5 km/h alatt „—”; ne válts l/h kijelzésre. Nyers fogyasztást integrálj naphoz, úthoz és tankhoz, ne a kijelzési átlagot.
- Nap = helyi naptári nap, éjféli forduló. Út ettől független. Demó soha ne írja felül a valódi napot, tankot vagy útnaplót.
- Friss RPM hiányában 180 s az út türelmi ideje; folyamatos 0 RPM esetén 8 s. A szolgáltatás-élettartam ne előzze meg a késleltetett lezárást.
- Automatikus OBD/radio indulásnál a felhasználó kézi leállítása/szüneteltetése elsőbbséget kap.
- Telefonos alsó menü: Műszerfal, Napló, Rádió. Kapcsolat a jobb felső fogaskeréken. Napi adatok és útnapló egy oldalon.
- Az AA dashboard médiafelületen kísérleti integráció. Ne ígérj hivatalos támogatást, garantált 1/3-os elrendezést vagy Play-elfogadást.

## Munkavégzés és ellenőrzés

- Számítási/kapcsolati/mentési változtatásnál futtasd a megfelelő egység- és regressziós teszteket; AA-változtatásnál ellenőrizd a `mediaDebug` változatot is.
- Változatszétválasztás: build után `python3 scripts/verify_variants.py`. A CI-ben van a teljes parancs.
- UI-átrendezést telefonon vagy emulátoron is nézz meg; önmagában a build nem bizonyítja az olvashatóságot.
- Teszthez ne rögzíts kitalált tankolást/utat a felhasználó valódi adattárába. Meglévő adatot ne törölj telepítési kerülőútként.
- Ne commitolj `local.properties`, kulcs, személyes ADB/BT-azonosító, logcat, telefonos adatmentés vagy felhasználói screenshot fájlt. A debug aláírókulcsot sem.
- Viselkedésváltoztatás után frissítsd az átadást és a VALIDATION jegyzőkönyvet; írd le azt is, amit még nem teszteltél valódi autóban.
- A felhasználóval magyarul kommunikálunk. A friss felhasználói kérés felülírhat korábbi termékpreferenciát; ne találj ki hiányzó konfigurációt.

## 0.11.1 felületi pontosítás

A felhasználó kérésére a „Demó indítása” gomb kikerült a telefonos Műszerfalról és a Kapcsolat oldalról. A belső szimulátor teszteléshez megmaradt. Ne tedd vissza a gombot új kérés nélkül.

## Útköltség (0.13, új felhasználói döntés)

A saját tankolás összege / betöltött liter adja a következő utak árát. Webes árlekérés és automatikus visszamenőleges kitöltés nincs. A régi útárakat megőrizzük. [TRIP_COST.md](docs/TRIP_COST.md) tetején az aktuális szabály, alatta a történeti 0.12-es specifikáció található. Ne kösd vissza az internetes árletöltést új kérés nélkül.
