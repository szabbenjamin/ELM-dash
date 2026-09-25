# MVP ellenőrzési jegyzőkönyv

Dátum: **2026-09-15**, indítódiagnosztika és AA Média frissítve: **2026-09-16**. Csomagolt verzió: **0.13.0 / 0.13.0-auto-lab / 0.13.0-aa-media**.

## 0.13.0: saját tankolási ár

- Teletankoláskor opcionális betöltött liter és teljes fizetett Ft; számított literár előnézet. A következő utak a legutóbbi áras saját tankolást használják. Összeg nélküli tankolás megőrzi az előző saját árat, előzmény nélkül a költség ismeretlen. Aktív/korábbi utak ára változatlan.
- Internetes árletöltés és automatikus árkitöltés kivéve a controllerből; a forrásjelzés saját tankolásra és dátumára vált új árnál. Régi JSON/árak megmaradnak. Az INTERNET ismét csak a media változatban van.
- 138 teszt sikeres; teljes build, három debug lint és izoláció sikeres. Új tesztek: fizetett összeg/liter, invalid összegek, következő út és változatlan régi ár, összeg nélküli tankolás, controller számítás és JSON-visszatöltés. A korábbi automatikus webes controller-tesztek az eltávolított viselkedéssel együtt megszűntek.
- Samsungon 0.13.0-aa-media telepítve. Tankolási ablak mezői és elrendezése ellenőrizve, mentés nélkül bezárva. A meglévő napló és tankolások megmaradtak. A következő valódi, összeggel rögzített tankolás/út próba még hátravan.

## 0.12.1: hiányzó korábbi árak egyszeri rögzítése

- Kifejezett felhasználói pontosítás: az ár nélküli régi utak friss mai árat kapnak, utólagos jelzéssel. Meglévő ár nem változik, üzemanyag nélküli rekordhoz is menthető ár, de költség nem koholt.
- Új regressziók: csak hiányzó ár kitöltése, cache elutasítása, rögzített ár újrahíváskor változatlan, ár mentése és ismételt controllerindítás duplikált lekérés nélkül. A kapcsolódó trip/controller tesztek, az Auto tesztek, minden build és három debug lint sikeres. Összesen 139 teszt eredménye sikeres (elm/obd az előző változat eredménye).
- Telefonon tényleges HTTPS-lekérés sikeres: 635,5 Ft/l. A korábbi ár nélküli utak egyszer megkapták az árat, `afterStart=true`; a tankolási rekord megmaradt. Nem generáltunk új mesterséges utat vagy új értesítést.

## 0.12.0: induláskori benzinátlagár és útköltség (2026-09-18)

- Holtankoljak.hu aktuális 95 E10 átlagárkártya: helyi HTTPS-letöltés sikeres; az élő oldalból rögzített minimális kártyarészleten a Kotlin-parser 635,5 Ft/l-t választott ki. Ez tesztfixture, nem beégetett alkalmazásár.
- Útazonosítóhoz kötött egyszeri aszinkron lekérés; legfeljebb 7 napos cache hiba esetén; régi utak változatlanok. Becsült költség a Naplóban, út végi értesítésben és CSV-ben, ár/forrás/dátum/cache-jelző mentésével. Régi JSON mezőhiánnyal is betölthető.
- **137 teszt sikeres:** elm 7, obd 6, trip 50, connection 30, auto 6, auto-media 38. Tesztelt parser-szelekció/hibás HTML/valós forrásrészlet, költség, immutábilis útár, cache lejárat, demó, késői válasz, mentés, CSV, értesítés és teljes controllerút. Mindhárom debug lint 0 hiba; négy build és változatizoláció sikeres. A phone INTERNET-engedély most szándékos az árlekéréshez.
- Samsungra 0.12.0-aa-media telepítve. Meglévő utak/tankolás megőrzése és a korábbi utak ár nélküli megjelenítése az alkalmazás saját UI-ján ellenőrizve. Nem hoztunk létre mesterséges valódi utat vagy tankolást. A következő valódi autós indulás mobilhálózatos árlekérése és az abból következő költségértesítés még nincs visszaigazolva.
- Részletes működés: [docs/TRIP_COST.md](docs/TRIP_COST.md).

## 0.11.1: demógomb eltávolítása

- A telefonos Műszerfal és Kapcsolat felületéről eltávolítva a Demó indítása gomb. A műszerfal Csatlakozás gombja teljes szélességű. A tesztszimulátor megmaradt.
- Build (négy változat), media lint és izolációellenőrzés sikeres. Samsungra frissítve; UI-hierarchia igazolja a Csatlakozás gombot és a demógomb hiányát a Műszerfalon. Számítási/adatkezelési változás nem történt.

## 0.11.0: egyszerűbb telefonos navigáció

- Három alsó pont: Műszerfal, Napló, Rádió. Kapcsolat jobb felső, akadálymentes címkével ellátott fogaskerékről; fogyasztási és autóbeállítások innen külön elérhetők. Vissza nyíl és Android Back kezeli a visszalépést.
- Napi összesítés, aktív út, tankolás és mentett utak egy Napló felületen. Napi részletek lenyithatók, a valódi aktív út ugyanaz a rekord, mint amit lezáráskor a napló ment. Demó külön jelzett. Útnullázás megerősítéssel maradt elérhető aktív mérésnél.
- Samsungra telepítve, a tényleges képernyőképen a három alsó pont, a napi adatok és a fogaskerék ellenőrizve. Fogaskerék → Kapcsolat → Autó és fogyasztás → Android Back → Kapcsolat → Back → Napló útvonal sikeres. A napi adatok megmaradtak; nem indult demó vagy mérés, nem módosult tankolás.
- Mindhárom debug APK és phone release build sikeres; debug lint ellenőrzések és változatizoláció sikeres. E kizárólagos UI-átrendezéshez új egységteszt nem készült; a telefonos navigációt közvetlenül ellenőriztük.

## 0.10.0: út végi értesítés

- Külön, normál fontosságú „Út végi összesítés” csatorna: km, liter benzin, átlag l/100 km; részleges/becsült/hiányzó adatok jelölve. Stabil útazonosítóval egyszer küld, a szolgáltatás leállítása nem törli. Kézi útnullázás és demó nem küld ilyen értesítést.
- Kapcsolatvesztés határa 180 s friss RPM nélkül; a controller a véges újrapróbálkozás hibás befejezése után is kivárja. A háttérszolgáltatás megvárja az aktív út lezárását. AA-bontás után az automatikus OBD szolgáltatás 182 s türelmet ad, visszatérő AA megszakítja az időzítőt. Motorleállítás továbbra is 8 s folyamatos 0 RPM, kézi leállítás azonnali.
- Új regressziók: 180 s határ, rövid újracsatlakozás, duplikációmentes értesítés új értesítőpéldánnyal és értesítés törlése után, lokalizált és hiányzó adatok, teljes controller út a hibás kapcsolattól a késleltetett lezárásig. Az AA-élettartam tesztje az új türelmi időt ellenőrzi.
- **128 teszt sikeres**, a három debug változat lintje 0 hiba; három debug APK és phone release build sikeres, négyváltozatos izoláció ellenőrizve.
- Samsungra frissítve: `versionCode=10`, `versionName=0.10.0-aa-media`, POST_NOTIFICATIONS engedély megadva. Az út végi értesítés automatizált Android/Robolectric tesztben ellenőrzött; új valódi autós útlezárást ezen a verzión még nem teszteltünk. Nem hoztunk létre próbaút- vagy tankolási rekordot a telefonon.

## 0.9.0: teletankolás és automatikus útnapló

- A kezelési kézikönyv szerint 45 l névleges tank. Első teletankolási jelzésig ismeretlen szint, utána a valós OBD-ből számított fogyasztás csökkenti a becsült maradékot. Tankolás, út, napi összesítés és kijelzési simítás külön számlálás. A demó nem írja a valódi tankot vagy naplót.
- Egységtesztek: alaphelyzeti ismeretlen tank, 45 l indulás, pontos literlevonás, duplikált időpont, tankolás és opcionális kúti mennyiség, 0–100% határ, adathiány és rövid újracsatlakozás, motorleállítás, motor-újraindítás és runtime wrap, 120 s kapcsolatvesztés, egyszeri folyamat-helyreállítás, duplikációmentes lezárás, kézi útnullázás melletti folyamatos tankkövetés, 200 rekordos korlát és CSV.
- A teljes controller tesztje igazolja a valódi adatúton létrejövő naplót, tankfogyást, visszatöltést, napi adatok megőrzését és demó elkülönítését. A JSON-tár körbejárási tesztje lefedi az aktív vázlatot, nullable adatokat, tankolásokat és lezárt rekordokat. Az AA teszt ellenőrzi az ismeretlen és becsült tankoldalt.
- A fogyasztási modell korábbi, valódi autós ellenőrzése nem jelenti a tank becslésének tankolással végzett kalibrálását. A szint és az út fogyasztása továbbra is becslés, a kimaradás külön jelölt.

- **Samsungon ellenőrizve:** sikeres frissítés `versionCode=9`, `versionName=0.9.0-aa-media`; csak a média változat van telepítve. A Napló lap és a teletankolási űrlap vizuálisan ellenőrizve. Az űrlapot mentés nélkül bezártuk: a tank kiinduló szintje továbbra is ismeretlen, nincs próbából rögzített tankolás vagy út. A korábbi napi adatok megmaradtak. A 0.9 ellenőrzésekor nem volt aktív autós OBD/AA-kapcsolat; a tényleges új út és tankfogyás autós próbája még hátravan.

## 0.8.0: fix fogyasztási egység, rádiónév és magasabb műszerblokk

- A 10 másodperces fogyasztás egysége minden fogyasztási kártyán **l/100 km**. 5 km/h alatt „—” látszik, nincs l/h-ra váltás. Adathiánynál továbbra is szürke marad az utolsó ismert szám. A belső üzemanyagáram és a napi integrálás változatlan.
- A médiafőcím például „≈5,8 • Ma 7,0 l/100 km”; a „Most” előtag megszűnt. Az alsó sor 8 másodpercenként vált az aktuális műszerlap és a rádió neve között. A szüneteltetett vagy kapcsolódó állomást nem jelöli szóló adásnak. A váltás változatlan OBD-adatok mellett is publikálódik; a borítót önmagában a sorváltás nem generálja újra.
- A borító adatblokkja y=44..277, tehát **233 px magas a korábbi 202 helyett (+15,3%)**. A számok is nagyobbak. A y=288..511 terület teljesen adatmentes, natív pixelellenőrzéssel igazolva.
- Új regressziók: álló/lassú jármű és elindulás fix egysége; a 8 s sorváltás határai, rádióállapotok és a médiaszolgáltatás változatlan adatok melletti metaadat-frissítése.
- **Telepítve a Samsungra:** újracsatlakoztatás után sikeres frissítés, `versionCode=8`, `versionName=0.8.0-aa-media`. A telefonos felületen a fix l/100 km egység és a megmaradt napi összesítés ellenőrizve. Ekkor nem volt aktív OBD/AA-kapcsolat; a 0.8 fizikai Carpuride-olvashatósága és a sorváltás fejegységes próbája még nincs visszaigazolva. A sorváltást Robolectricben a szolgáltatás szintjén is ellenőriztük.

## 0.7.0: valódi Kalos fogyasztás és kis AA-kártya

- **Hiba igazolva valódi autóban:** az élő OBD-kapcsolat RPM/MAP/sebesség/hűtőfolyadék adatot adott, de MAF (`0110`) és ECU üzemanyagáram (`015E`) nem támogatott. Korábban nem volt kiválasztott benzines profil, így az ECU_ONLY alapérték lépett életbe. A korábbi mentés fogyasztással párosított szakasz nélkül nem adott értelmezhető átlagot.
- **Javítás utáni élő próba:** friss IAT-adattal a MAP-becslés működött, a telefon és az AA média-session fogyasztást jelzett. Ez működési ellenőrzés, nem tankolással hitelesített pontosságmérés. A konkrét személyes telemetriaminták nem részei a nyilvános dokumentációnak.
- A felhasználó ezután **10 másodperces simítást** kért a Most értékre. A `FuelWindow` csak a megjelenítést simítja, azonos időablakból származó liter/távolság aránnyal; a napi integrálás nyers mintái változatlanok. Tesztelve a hirtelen ugrás, régi minták kiesése, sebességváltás, alapjárat/elindulás, ablakhatár, duplikált időpont és adathiány.
- **10 s simítás:** telefonon és élő AA-sessionben ellenőrizve; a rádió és OBD-adatfolyam megmaradt. A konkrét személyes mérési sor helyett a működési eredményt dokumentáljuk.
- A forrásválasztást és a MAP/IAT-képletet külön egységteszt és MAF/015E nélküli ELM-válaszfixture ellenőrzi a teljes parser → repository → controller → fogyasztás → mentett nap útvonalon. A korábban explicit kiválasztott profil nem íródik felül. A korábbi adathiányos napi szakasz nem kap visszamenőleg kitalált fogyasztást.
- A felhasználó fotói igazolják, hogy a rádió **már megjelent a kis AA-kártyán**, de a host a borító alsó részére rajzolja a feliratot és vezérlőket. Az új 512 px-es kép minden adatot a középső felső félbe tesz. Natív Canvas-teszt ellenőrzi a színeket, a szürke értékmegőrzést és az alsó fél teljes adatmentességét. A módosított kártya végső Carpuride-olvashatóságához felhasználói visszajelzés szükséges.

## 0.6.0: rádió-helyreállítás és AA-indítás (korábbi próba)

- Az utoljára kiválasztott rádió automatikusan indul visszaigazolt AA-projekciós kapcsolatnál; a Rádió lapon ez kikapcsolható. A puszta Bluetooth-kapcsolat vagy az AA nélküli alkalmazásmegnyitás nem indít adást.
- Új automatizált esetek: internet nélküli várakozás, visszatérő hálózat, növekvő retry és 30 s felső határ, beragadt puffer, streamváltás, váratlan streamvég, hangfókusz, végleges hibák, kézi szünet és megsemmisítés utáni retry-tiltás. Az AA-politika külön teszteli az egyszeri indítást, dupla eseményt, kikapcsolt automatikát, megőrzött kézi szünetet, rövid AA-kimaradást és új munkamenetet. A médiaszolgáltatás offline is életben tartja a lejátszási szándékot; az AA bontása nem állítja le a telefonról indított rádiót.
- **Valódi telefonos megszakítási próba:** a Samsungra telepített 0.6.0 Oxygen-adást játszott egy ideiglenes, USB/ADB-alagúton elért helyi relayen keresztül. A relay megszakította a streamet, majd HTTP 503 válaszokat adott. A puffer kifogyása után a session `BUFFERING`, a felirat „Megszakadt az adás • automatikus újracsatlakozás…” lett. A relay visszaállítása után **új lejátszásparancs nélkül `PLAYING / speed=1.0`** állapotba jutott. Az ezt megelőző külön leállítást a felhasználó saját kézi szüneteltetésként igazolta vissza.
- **Kézi szünet elsőbbsége a telefonon:** újabb streammegszakítás után Szünet, majd a relay helyreállítása; több mint 60 másodperc után sem jött létre új média-session. A próbaállomást és az ADB-alagutat eltávolítottuk, a kiválasztott állomás ismét Oxygen; rádió nem maradt bekapcsolva.
- A relayteszt a valódi ExoPlayer és szolgáltatás streamhiba utáni helyreállítását igazolja. **Nem mobilhálózati térerőteszt:** a telefon teljes internetkapcsolatát nem kapcsoltuk ki; a hálózati események logikáját automatizált tesztek fedik. Nem volt jelen AA-fejegység, ezért a 0.6 automatikus rádióindítása és a rádióval aktív kis AA-kártya továbbra is valódi Carpuride-próbára vár.
- Következő autós próba: telefonos app bezárva, AA csatlakozik → utolsó rádió és automatikus OBD; hosszabb térerőkimaradás → várakozás, internet visszatér → élő adás; kézi Szünet → csend marad; AA bontása → csak az AA által indított rádió áll le 5 másodperc után.

## 0.5.0: automatikus OBD-kapcsolat (korábbi változat)

- A hivatalos CarConnection API projekciós állapota és a kiválasztott Bluetooth-fejegység ACL-eseménye indítja az automatikát. Csak a mentett OBD-adapterhez kapcsolódik. Az ébresztő fejegység külön mentett beállítás.
- Automatizált tesztek: egyszeri indítás/dupla esemény, kikapcsolt automatika, hiányzó adapterbeállítás, kézi megállítás elsőbbsége, idegen Bluetooth-eszköz kizárása, később (a szokásos 5 próbán túl) elérhető adapter, megállítás utáni retry-tiltás, rövid AA-kimaradás áthidalása, tartós AA-bontás és kézzel indított mérés védelme. Az autótól távoli kézi leállítás nem tiltja a következő AA-kapcsolatot.
- **Telefonos beállítás:** Samsung Galaxy Z Fold4 / Android 16; megmaradt a korábbi OBDII-adapter és a mérési előzmény. A megfigyelt `W113-01e20b` eszköz lett a fejegység-ébresztő, automatika bekapcsolva. Az Android akkumulátor-optimalizálás alóli kivétel a rendszer engedélyező felületén megadva, a kivétellistában ellenőrizve.
- **Telefonos futáspróba:** fejlesztői paranccsal, az app saját UID-jával és saját Android-felhasználóján indított automatikus ObdService. A telefonos Activity háttérbe küldése után is sikeres connectedDevice foreground indítás. A külön végső próbán a folyamat indulás előtt `19 (CACHED_EMPTY)` állapotú; a rendszer naplója `uidState: CEM`, `code:SYSTEM_ALLOW_LISTED`, majd `isForeground=true`, `types=connectedDevice` állapotot rögzített. Így ezt a próbát már nem az Activity előtéri állapota engedte. Távol lévő OBD-adapternél RETRY állapot és folyamatos újrapróbálkozás. A 329-es saját értesítés címe „Nincs kapcsolat az autóval”, a 327-es foreground értesítés szövege ugyanezt és az automatikus újrapróbálkozást mutatta. A végén a tesztszolgáltatást leállítottuk.
- Ez **nem valódi AA-csatlakozási esemény és nem igazolt autós hidegindítás**. A Carpuride és az autó nem volt jelen; az AA/ACL indítási politika Robolectric tesztben ellenőrzött. A következő autós próba: telefon zárolva, W113 vezeték nélküli AA csatlakozik, OBD bekapcsolva → automatikus élő adatok; majd gyújtás/adapter megszakítása → figyelmeztetés és újrapróbálkozás.
- Nincs demó vagy rádió automatikus indítása, nincs képernyő előtérbe kényszerítése, nincs bootkori folyamatos keresés. Kézi Leállítás szünetelteti az automatikát; az AA-bontás csak az automatika saját mérését állítja le.

## 0.4.0: rádió és naptári napi fogyasztás

- 14 állomás streamjének HTTP 200 / audio Content-Type / 2048 bájt ellenőrzése sikeres; a megadott Oxygen URL AAC-adást szolgáltat. A forráslista: [RADIO_STATIONS.md](RADIO_STATIONS.md).
- Media3 ExoPlayerrel tényleges rádiólejátszás, mentett állomásválasztás, kedvencek, keresés és saját streamlista készült. A média-session állapota a valódi lejátszót követi.
- A napi liter/km és súlyozott fogyasztási átlag a helyi naptári naphoz kötött. Az éjféli váltás, motor-újraindítás, kézi útnullázás, újranyitás, adathiány és a demó valódi mentéstől való elkülönítése automatizált teszttel ellenőrzött.
- A négyrészes 320 × 320 pixeles AA műszerkép natív Canvas renderrel ellenőrizve: RPM, vízhőfok, MOST l/100 km, MAI ÁTLAG l/100 km, színes jelzés és nyíl.
- **Samsungon telepítve és ellenőrizve:** Oxygen (AAC) és Retro (MP3) sikeresen jutott tényleges `PLAYING / speed=1.0` állapotba. A következő gomb Oxygenről Retróra, az előző vissza Oxygenre váltott. Szünet után a rádiólejátszó felszabadult, a demó tovább gyűjtötte a napi liter/km adatot. Fordítva is ellenőrizve: az adatfolyam leállítása után az Oxygen `PLAYING` maradt, a műszeradatok „utolsó” jelzést kaptak. A telefonos rádiólap és a négyértékes grafika vizuálisan ellenőrizve. A rádió az Activity háttérbe küldése után is PLAYING maradt. A próba végén mind a rádiót, mind a demót leállítottuk; a kiválasztott állomás Oxygen maradt.
- **A 0.4.0 jobb oldali kis AA médiakártyáját még nem teszteltük tényleges fejegységen:** a felhasználó időközben eljött az autótól. A 0.3.0 alábbi autós visszaigazolása nem bizonyítja ezt az új elrendezést.

## Korábbi autós eredmény

**Valódi autós megjelenítés visszaigazolva:** a felhasználó a vezeték nélkül kapcsolódó Carpuride-on előbb a statikus médiafelületet, majd az új **ELM Dash • AA Média 0.3.0** változó demóadatait is látta. Az Android Auto ténylegesen a `hu.elmdash.app.media/hu.elmdash.media.DashboardMediaService` szolgáltatáshoz kötődik. Ez a médiafelület kísérleti használata; az alábbi korábbi Car App Library / DHU eredményektől külön kezelendő. A valódi ELM-adatkapcsolat a fenti 0.7-es autós próbában már igazolt.

**Megmaradó korlát:** az eredeti Car App Library / Auto Lab csomagot a telefon normál AA-indítója elutasítja. Ez nem akadályozta az új, külön média változat indítását.

Az Auto Lab **0.2.0** telepítve és futtatva a Samsung Galaxy Z Fold4 (SM-F936B) telefonon, Android 16 / API 36 rendszerrel. A telefon Android Auto **17.6.663454-release** verziója a Google **Desktop Head Unit 2.0 macOS ARM64** szoftveres fejegységével ténylegesen megjelenítette és frissítette az alkalmazás adatait. A képernyőképek erről a futásról készültek; az OBD-adatok demó adatok.

**A nagy fordulatszám–vízhőfok grafika telefonos/DHU-ellenőrzése sikeres.** A színes háttér, a becsült fokozat, a fel- és visszaváltási nyíl, valamint a leállítás utáni szürke értékmegőrzés látható a valódi Auto hoston. A számértékek a grafika mellett a négy stabil adatsorban is olvashatók.

## Build és automatizált ellenőrzés

JDK 17, Gradle Wrapper 8.13, AGP 8.13.2, Kotlin 2.2.21, compile/target SDK 36.

| Ellenőrzés | Eredmény |
|---|---|
| `:elm:test` | 7 sikeres teszt |
| `:obd:test` | 6 sikeres teszt |
| `:trip:test` | 43 sikeres teszt: fogyasztás/út, tank és napló, MAP-becslés, 10 s simítás, vezetési jelzés, napi összesítés |
| `:connection:testDebugUnitTest` | 23 sikeres teszt |
| `:auto:testDebugUnitTest` | 6 sikeres Robolectric teszt |
| `:auto-media:testDebugUnitTest` | 38 sikeres teszt, rádió-helyreállítással, AA-munkamenettel, állomástárral és natív grafikai ellenőrzéssel |
| Összesen | **123 teszt, 0 hiba, 0 sikertelen teszt** |
| `:app:lintPhoneDebug`, `:app:lintUnsupportedDebug`, `:app:lintMediaDebug` | 0 hiba; változatonként 17, újabb függőségverziót jelző figyelmeztetés |
| `assemblePhoneDebug`, `assembleUnsupportedDebug`, `assembleMediaDebug` | Sikeres, aláírt debug APK-k |
| `assemblePhoneRelease` | Sikeres, aláíratlan telefonos release APK |
| `scripts/verify_variants.py` | Mind a négy változat manifest-ellenőrzése sikeres |
| `unsupportedRelease`, `mediaRelease` | Letiltva; nincs ilyen assemble task |

A régebbi Car App Library fejléc-setterek fordítási deprecation figyelmeztetést adnak; a minimum Car API 1 kompatibilitást szolgálják. Nem építési hibák. A GitHub Actions munkafolyamat ugyanezekre az ellenőrzésekre van beállítva; távoli CI-futást ebben a munkában nem indítottunk.

### Lényeges regressziós esetek

- AA Média: adathiánykor nincsenek kitalált számok, minden kért PID megjelenik, a demó eredete leállítás után is látható; a korábbi értékek szürkék és a nyíl megszűnik. Az öreg vízhőfok képpontszíne natív Canvas tesztben is szürke.
- AA Média: változatlan számértékeknél az időbélyeg önmagában nem vált ki új médiafrissítést; hamis csomag/UID pár és idegen kliens elutasítva. A session csak tényleges hanglejátszáskor jelez PLAYING állapotot; a médiaböngésző megnyitása önmagában nem indít rádiót vagy adatgyűjtést; a visszaigazolt AA-esemény külön indítja az engedélyezett automatikát.

- CAN 11/29 bit, tömör és szóközös ELM-válaszok, legacy fejléc, echo, hibás és hiányzó adatok.
- ECU-választás, PID-dekódolás, támogatási maszk, soros hozzáférés és feszültséghelyettesítés.
- Friss adat időbélyege és állapotideje együtt publikálódik: nincs két frissítés közötti hamis adatkimaradás.
- Megszakadás és újracsatlakozás közben a kijelzett utolsó számérték megmarad; a számítás továbbra is csak friss adatot fogad el.
- Hiányzó fogyasztás vagy sebesség nem változtatja meg a megtartott fogyasztási értéket és mértékegységet.
- Fogyasztási átlag azonos lefedettségű szakaszokból, alapjárati fogyasztás, MAF-becslés csak választott benzines profilnál, adatrések, motor-újraindulás és futásidő-túlcsordulás.
- A főlap és a három részletes Auto sablon négy sorral érvényes. Car API 4+ nagy kép és Car API 3 soronkénti képes fallback, állandó sorcímek, demójelzés és stale színszakasz ellenőrzött.
- Kalos váltási jelzés: stabil fokozatbecslés, nagy terhelés miatti felváltás-tiltás, ötödikben nincs felváltás, elsőben nincs visszaváltás, hideg/melegedő vagy túl magas vízhőfoknál nincs nyíl. Hiányzó/öreg adat és kuplungolásra utaló arányváltozás visszavonja a nyilat; rövid zaj nem vált ki javaslatot.

## Készüléken és emulátorban végzett próba

Az Auto főlapra vonatkozó megfigyelések a 0.2.0 grafikus változat tényleges telefonos futásából származnak. A telefonos/emulátoros kísérőfelület és az alap részletes adatlapok a korábbi MVP-ellenőrzésen is átmentek.

| Felület / helyzet | Megfigyelés |
|---|---|
| Samsung telefon, Compose dashboard | A telepítés és a demó adatfolyama működik |
| Android 35 emulátor | Demó, leállítás, útadatok és üzemanyagprofil felület működik |
| Széles emulátorkijelző | A dashboard rácsa a szélességhez alkalmazkodik |
| Telefonos adatfolyam leállítása | Számok megmaradnak szürkén, „Utolsó ismert adat” jelzéssel |
| Android Auto indító, tényleges telefon + DHU | Az ELM Dash Auto Lab ikon látható és elindítható |
| Auto főlap | Nagy színes RPM/vízhőfok grafika; RPM/terhelés, vízhőfok, fogyasztás/útátlag és sebesség/kapcsolat sorok |
| Auto melegedés | Kék háttér, nincs váltási nyíl |
| Auto visszaváltás | 1691 rpm, 82% terhelés, becsült 4. fokozat mellett sárga háttér és ↓ |
| Auto felváltás | 3209 rpm, 35% terhelés, becsült 4. fokozat mellett sárga háttér és ↑ |
| Auto leállított grafika | Megmaradó RPM és vízhőfok szürkén, váltási nyíl nélkül |
| Auto Motor lap | RPM, sebesség, terhelés és hűtőfolyadék frissül |
| Auto Szenzorok lap | MAP, TPS, feszültség és MAF megjelenik |
| Auto fejléc Demó gomb | A szimuláció közvetlenül az autós felületről elindul |
| Auto fejléc Leállítás gomb | Az adatfolyam leáll; az RPM/sebesség/hőmérséklet utolsó értéke szürkére vált, megmarad |
| Auto újraindított demó | A mért értékek ismét frissülnek |

A DHU 1280 × 720, 160 dpi, érintéses konfigurációban futott. Az Android Auto a jobb oldali térképet és a többablakos elrendezést maga kezelte. Az alkalmazás a host által rajzolt PaneTemplate felületen futott.

**Kapcsolat:** Android Auto fejlesztői mód → head unit server → `adb forward tcp:5277 tcp:5277` → `desktop-head-unit --headless --adb=5277`. A közvetlen USB accessory mód szinkronizációs hibával (`-251`) leállt; az ADB-s út működött. Az Unknown sources kapcsoló nem kellett ehhez a sikeres futáshoz.

## Android Auto indító-elutasítás (2026-09-16)

Készülék: Samsung Galaxy Z Fold4, Android 16; Android Auto 17.6.663454-release. A head unit server nem futott a vizsgálat alatt.

- Az `hu.elmdash.app.unsupported` 0.2.0-auto-lab csomag telepítve, engedélyezve és megnyitva volt. Az Android csomagkezelő megtalálta az exportált `hu.elmdash.auto.DashboardCarService` szolgáltatást és az IOT kategóriát.
- A normál AA alkalmazásindító látható és rejtett alkalmazásai között sem szerepelt az ELM Dash.
- Az AA naplójának ismétlődő, csak a saját csomagra szűrt üzenete: `CAR.VALIDATOR: Package DENIED; failed all other checks [hu.elmdash.app.unsupported]`.
- A változatlan APK újratelepítése `com.android.vending` telepítő-metaadattal, majd az Android Auto újraindítása után is fennmaradt az elutasítás. Ez nem valódi Play-telepítés. A próbát visszaállítottuk: a telepítő ismét `com.android.shell`.
- Az AA fejlesztői menüjében a „Fejlesztői” alkalmazási mód próbája és az AA újraindítása sem jelenítette meg az alkalmazást; az új folyamat naplója is elutasítást jelzett.

**Következtetés:** a napló az Android Auto csomagelfogadási ellenőrzésének elutasítását bizonyítja, annak pontos belső feltételét nem nevezi meg. A manifest szolgáltatása felderíthető; az OBD-kapcsolat vagy a dashboard rajzolása ennél a hibánál még el sem indul. A Google [valódi járműves tesztelési dokumentációja](https://developer.android.com/training/cars/testing#test-in-real-vehicles) megbízható telepítési forrást ír elő. Az „Unknown sources” kivétel a Car App Library alkalmazásaira nem vonatkozik.

**A Car App Library ág lehetséges terjesztési próbája:** a meglévő Auto Lab APK tényleges telepítése saját Play Console Internal App Sharing linkről. Ehhez fejlesztői fiók és feltöltés szükséges; ilyen feltöltés vagy telepítési próba még nem történt. Ez a terjesztési út önmagában nem teszi az OBD-dashboardot hivatalosan támogatott kategóriává, és nem garantálja a fizikai fejegységes sikert.

### Külön, régi projection API kompatibilitási próba

A Play-fiók nélküli lehetőség vizsgálatához a fő projekten kívül készült egy kisméretű `hu.elmdash.legacyprobe` tesztalkalmazás. Az [AA Torque forrásában](https://github.com/agronick/aa-torque/blob/master/app/src/main/AndroidManifest.xml) látható `CarActivityService`, `CATEGORY_PROJECTION` / `CATEGORY_PROJECTION_OEM`, valamint `service` / `projection` descriptor útját használta. A próba nem olvasott OBD-adatot, és nem helyettesítette az Auto Lab alkalmazást.

- A külön debug build sikeresen lefordult és települt a Samsungra; a telefonos indítóképernyője elindult.
- Az AA „Ismeretlen források” kapcsolóját a próba idejére bekapcsoltuk, majd újraindítottuk az AA-t. A tesztapp a látható/rejtett indítólistában sem jelent meg.
- A napló: `Package DENIED; failed all other checks [hu.elmdash.legacyprobe]`.
- A próbacsomag `com.android.vending` telepítő-metaadattal történő újratelepítése után az új AA-folyamat ugyanígy elutasította. Ez sem volt valódi Play-telepítés.
- A sikertelen próbacsomagot eltávolítottuk, az „Ismeretlen források” kapcsolót visszaállítottuk kikapcsoltra. Az Auto Lab telepítője továbbra is `com.android.shell`. A külön teszt nem módosította a kiadott APK-kat vagy a projekt moduljait.

Ez a vizsgálat kizárólag ennek a helyi telepítési módszernek és SDK-nak az eredményét igazolja a teszttelefonon. Nem bizonyítja, hogy minden nem hivatalos út lehetetlen, és nem igazolja a projection API autós felületének elindulását. A felhasználó azóta Carpuride fejegységet és vezeték nélküli kapcsolatot jelzett. A felhasználó később W113 jelölést adott meg; ehhez egyértelmű gyártói adatlapot nem sikerült azonosítani. A W103/W103 Pro műszaki adatait ezért nem vetítjük automatikusan erre a készülékre. A [gyártó szerint](https://carpuride.com/blogs/guide/carpuride-device-android-phone-compatibility-statement) a vezetékes Android Auto támogatása modellenként eltér; USB-s köztes adapter ezért a pontos típus ellenőrzése nélkül nem javasolható. A telefonon megfigyelt csomagelutasítás továbbra is fennáll.

### Médiafelület: sikeres valódi Carpuride megjelenítés

A Car App Library és a régi projection út elutasítása után külön `hu.elmdash.mediaprobe` tesztapp készült a hagyományos `android.media.browse.MediaBrowserService` felületre. Statikus, „TESZTADAT” feliratú 2400 rpm / 89 °C adatot mutatott. A felhasználó ezt a **valódi Carpuride médialejátszójában** visszaigazolta; ez már nem DHU-s megfigyelés.

- A telefonon ellenőrzött AA beállítások: **Fejlesztői alkalmazásmód**, **Ismeretlen források bekapcsolva**. A vezeték nélküli AA aktív maradt, a head unit server nem futott.
- A statikus próba normál ADB-telepítéssel jelent meg, hamis Play-telepítőazonosító nélkül. Az Auto Lab ugyanebben az állapotban továbbra is `Package DENIED` választ kapott.
- Ezután a fő projektbe külön `auto-media` és közös `dashboard-graphics` modul, valamint `mediaDebug` változat került. A Canvas műszerkép és a Kalos tanácsadó közös a két autós UI-val.
- A **0.3.0-aa-media** APK a Samsungra települt; az Auto Lab helyi beállításait átmásoltuk, az eredeti app adatait meghagytuk. Az új csomag Bluetooth- és értesítési engedélyt kapott; a korábbi alkalmazást leállítottuk, hogy ne foglalhassa ugyanazt az adaptert.
- Az új telefonos UI-n a demó működött: például 1694 rpm, 89 °C, 82% terhelés, 46 km/h; a számok a transport–parser–PID–számítás adatútból származtak.
- A felhasználó visszajelzése: **„látszódnak mocorgó adatok … az AA képernyőn”**. A teljes alkalmazás változó demóadatainak megjelenése fizikai fejegységen ezzel visszaigazolt.
- A saját média-session állapota a telefonon `active=true`, `PAUSED`, `speed=0.0`. A metadata például **3623 rpm • 92 °C**, **DEMÓ • 35% terhelés • 126 km/h • ≈5. fokozat**. A kliens a valódi `com.google.android.projection.gearhead:projection` folyamat.
- Leállításkor a valódi telefonon az AA-nak küldött metadata **1712 rpm • 92 °C** maradt, a terhelés és sebesség „utolsó” jelölést kapott. Egy későbbi kiolvasás ugyanezeket az értékeket adta vissza; a telefonos UI is „Utolsó ismert adat” állapotot mutatott. Ezután a demót újraindítottuk.
- A színes műszerkép autós olvashatóságának és a szürkülés láthatóságának felhasználói visszajelzése még hiányzik. Az automatizált tesztek az értékmegtartást, nyílelvételt és a bitmap szürke színét ellenőrzik.
- A külön statikus `hu.elmdash.mediaprobe` csomagot az új változat sikeres megjelenése után eltávolítottuk. A régi Auto Lab app és annak saját beállításai megmaradtak.

Az AA Média nem játszik hangot, nem kér hangfókuszt és nem állít `PLAYING` állapotot. A médiafelület dashboardként használata továbbra is nem hivatalos kísérlet, nem Play-kompatibilitási állítás. Az Android Auto médianézetének elrendezése és frissítési korlátja a host döntése.

## Még szükséges valódi autós próba

A fizikai Carpuride-on a médiaút és a változó demóadatok megjelenése már visszaigazolt. A következők **még nincsenek hardveren igazolva**:

- Bluetooth SPP kapcsolat és újracsatlakozás konkrét adapterrel; K-line / CAN busz időzítések és klónkompatibilitás.
- Az autó tényleges PID-támogatása, ECU-választása, fogyasztási adatainak pontossága és észlelt úthatárai.
- Hosszú háttérmérés Samsung akkukezelés mellett és tényleges kapcsolatkimaradásból helyreállás.
- Színes műszerkép olvashatósága, tartós frissítési viselkedés és együttműködés zenével / navigációval a médiafelületen; más telefonok / fejegységek elfogadása.

Az `unsupportedDebug` IOT kategóriát használó fejlesztői kísérlet. A sikeres DHU-próba nem jelent Play-jóváhagyást vagy minden fejegységre érvényes kompatibilitást. A pontos párosítási, számítási és Android Auto korlátok a [README-ben](README.md) szerepelnek.
