# GPS, részletes menetnapló és WebDAV (0.15)

## Bekapcsolás a telefonon

Fogaskerék → **Útvonal és WebDAV**. A rögzítés és a feltöltés alapból kikapcsolt.

1. Kapcsold be az útvonal- és részletes adatnaplót.
2. Engedélyezd a pontos helyet, majd Android 10-től a háttérbeli, **Mindig engedélyezett** helyhozzáférést is. Ez az implementáció így biztosítja az Android Auto által háttérből indított mérést. Kapcsold be a telefon helymeghatározását.
3. Mentsd a beállítást és indítsd újra az OBD-kapcsolatot. A rádió önmagában nem indít GPS-rögzítést.
4. Feltöltéshez add meg a saját HTTPS WebDAV alap-URL-t (a szolgáltató DAV-végpontját, nem a böngészős kezelőoldalt), a célmappát, a felhasználónevet és lehetőleg alkalmazásjelszót. Példa: `https://dav.example.org/remote.php/dav/files/account/`, mappa: `ELM-Dash/utak`. A példa nem működő kiszolgáló.
5. Kapcsold be az automatikus feltöltést és ments. A szervernek MKCOL és PUT írási jogosultságot kell biztosítania. Beágyazott hitelesítésű URL, HTTP és átirányítás nem támogatott.

A helyengedély hiánya nem akadályozza az OBD-naplót. A hiányzó GPS-hely null értékkel és státusszal szerepel, nem kitalált koordinátával.

## Mikor, mi kerül a naplóba?

Csak valódi LIVE kapcsolat, aktív út és friss, nullánál nagyobb RPM esetén készül minta. Demóadatból nem készül fájl. Másodpercenként egy OBD-pillanatkép készül; az egyes PID-ek saját kiolvasási gyakorisága nem változott. Minden érték mellé bekerül az egység, az adat kora, minősége és forrása; az elavult érték külön `lastKnownValue`, a friss `value` null.

A támogatott adatok: sebesség (OBD), RPM, motorterhelés, vízhőfok, MAP, IAT, MAF, TPS, motor üzemideje, feszültség, ECU üzemanyagáram; továbbá a számított nyers fogyasztás és a 10 másodperces l/100 km átlag, a becslés forrásával. A Kaloson több PID nem támogatott, a fogyasztás MAP-alapú becslés lehet. **Ez nem teljes szervizdiagnosztika:** nincs ABS/légzsák/gyártóspecifikus modul, hibakód-olvasás vagy törlés.

A GPS első kérelme a járó motor észlelésekor, majd 60 másodpercenként történik; egy kérelem legfeljebb 20 másodpercig vár. A kapott koordináta mellett időpont, pontosság, kor, valamint ha elérhető, GPS-sebesség és magasság szerepel. A legutóbbi pont legfeljebb 90 másodpercig ismétlődhet az egy másodperces mintákban, azonos fix-időponttal és növekvő korral. A köztes helyeket nem interpoláljuk. Alagútban, beltérben vagy gyenge vételnél nem garantálható percenként új fix.

Az út lezárása a meglévő szabályokat követi: 8 másodperc folyamatos 0 RPM; 180 másodperc friss RPM nélküli kapcsolatvesztés; kézi leállítás/új mérés, illetve új motorindítás észlelése. A zárórekord tartalmazza az út összesítő távolságát, literét, átlagfogyasztását és becsült költségét. A részletes rögzítés útközbeni kikapcsolása lezárja a fájlt, de az összesítő út ettől tovább futhat.

## Fájl és tárolás

- Egy JSONL + HTML fájlpár egy rögzítési szakasz: `<útazonosító>-<kezdési idő milliszekundumban>.jsonl`. Ugyanaz az út több szakaszra válhat, ha ki/be kapcsolod a részletes rögzítést.
- UTF-8 JSONL: fejléc (`header`, schema 1), minták (`sample`), lezárás (`end`). UTC időbélyegek; a mintákban monotón idő is szerepel.
- Aktív fájl `.open`, lezárt `.jsonl`. Folyamatmegszakadás után a következő appindítás helyreállítja a befejezett sorokat, a félbehagyott sort eldobja, és `APP_RESTART`, `partial: true` lezárást ír. Ez nem pótolja a kimaradt mérést.
- Android privát `noBackupFilesDir/routes-v1` könyvtár; nem kerül Android-mentésbe. A normál összesítő napló/CSV nem tartalmaz koordinátát.
- 200 MB felett nem indul új fájl; az aktív fájl 50 MB-nál szünetelteti a részletes mintavételt, részlegesként zárul. Emiatt a teljes tároló átmenetileg kb. 250 MB lehet. Nincs automatikus régiút-törlés.
- A beállításokban a lezárt fájlok ZIP-be exportálhatók és megerősítéssel törölhetők. Az export is pontos helyadatokat tartalmazhat. Az aktív fájl és az összesítő útnapló nem törlődik.

## Feltöltés és hibaállapotok

WorkManager végzi a lezárt fájlok feltöltését elérhető hálózatnál, mobilinterneten is. Az Android energiatakarékossága késleltetheti a munkát; az út vége ütemezést jelent, nem garantált azonnali feltöltést. Offline helyben várakozik; I/O-, HTTP 408/429/5xx hibánál növekvő várakozással újrapróbál. Egyéb HTTP-hiba (például 401/403) beállításjavítást és a **Feltöltés újrapróbálása** gombot igényli. Siker után `.sent` jelölő akadályozza meg az ismételt normál feltöltést. A 0.15-ben a siker a JSONL és a HTML feltöltését együttesen jelenti; fél siker után mindkét fájl idempotensen újrapróbálódik. A 0.14-es csak-JSONL sikerekhez következő appindításkor, változatlan engedélyezett célhely mellett a HTML is pótlódik. A távoli azonos nevű fájl PUT-tal cserélődik, ezért az ismétlés idempotens; megszakadt feltöltés ideiglenesen hiányos fájlt hagyhat a szerveren a sikeres ismétlésig.

Csak a feltöltés engedélyezésekor megkezdett rögzítési szakaszok tölthetők fel automatikusan. A cél-URL, mappa és felhasználó hash-e a fejlécbe kerül: más célhely beállítása nem küldi át oda a régi utakat. A régi célhely visszaállítása és újrapróbálás helyreállítja a várakozó munkát. Jelszócsere nem változtat célazonosítót.

A jelszó és felhasználónév Android Keystore AES-GCM titkosítással tárolódik; nem kerül fájlnévbe, naplóba vagy WorkManager-adatba. TLS tanúsítványellenőrzés aktív, átirányítást nem követünk. A feltöltés kikapcsolása törli az ütemezett munkákat; már elküldött adatok visszavonására nem képes. Saját megbízható WebDAV-fiókot használj. GitHubra soha ne kerüljön valódi útvonalfájl, ZIP vagy hitelesítő adat.

## Ellenőrzési határ

Az automatizált teszt helyettesített WebDAV-válaszokat és szintetikus adatokat használ. Saját szerveren, valós mozgó autóban és vezeték nélküli Android Auto mellett külön próba szükséges; ezek hiányában a teljes végpontok közötti működés nem tekinthető autóban igazoltnak.


## 0.15 – teljes képernyős útrészletek és HTML-térkép

Napló → **Út részletei és térkép**: teljes képernyős, bezárható ablak. A nagyítható/mozgatható Leaflet-térkép az összes helyben meglevő rögzítési szakaszt megmutatja; a Teljes út gomb visszaállítja a nézetet. Indulás kék, érkezés/utolsó pont narancs. A perces sebesség- és fogyasztáslisták helyett összesítő kártyák vannak (távolság, liter, átlagfogyasztás, költség, idő, átlag/max sebesség, max RPM, hőfok, terhelés, lefedettség, mozgás/alapjárat). Az aktív út a megnyitás pillanatának állapotát mutatja; újranyitva frissül.

GPS nélkül a régi út összesítője megjelenik, de útvonalat nem találunk ki. Ismétlődő fix-időpontok kiszűrve; két percnél nagyobb időrés és külön fájlok között nincs összekötés. Ez pontok közötti vonal, nem útra illesztés vagy navigáció. A helyi fájl törlése után a térképet nem töltjük vissza automatikusan a WebDAV-ról.

Útlezáráskor a telefon ugyanazon néven `.html` társfájlt készít. A WebDAV-ra helyes `text/html; charset=utf-8` típussal kerül fel, a JSONL mellé. A ZIP-export mindkettőt tartalmazza, a helyi törlés mindkettőt eltávolítja. A feltöltés továbbra is kizárólag az eredeti célhelyhez kötött. A HTML ugyanúgy érzékeny helyadat, mint a JSONL. A HTML-fájl egy rögzítési szakasz térképét tartalmazza; végső lezárásnál az összesítő a teljes útra vonatkozik.

A Leaflet 1.9.4 JS/CSS és a GPS-pontok a HTML-be vannak ágyazva; nem kell CDN vagy a JSONL mellé a megnyitáshoz. Az útvonal és a számok internet nélkül is megjelennek. Az **OSM alaptérkép betöltése** gomb tölt le csak a megtekintett nézethez térképcsempéket. Nincs előtöltés vagy offline csempetérkép-csomag. Attribution mindig szerepel; az app azonosított User-Agentet, a HTML origin referrert és a böngésző szokásos HTTP-cache-ét használja. Az OSM a kért csempék földrajzi területét és az IP-t látja, az OBD/JSONL adatokat nem küldjük oda.

A helyi `file://` HTML-ben alaptérkép-letöltés nincs (nem biztosítható a webes OSM Referer-követelmény); az útvonal nagyítható marad. OSM alaptérképhez az appban vagy HTTP(S)-en kiszolgálva nyisd meg. Egyes WebDAV-szerverek letöltést kényszerítenek vagy CSP-vel tiltják a JavaScriptet: az automatikus feltöltés ezt nem írja felül. A szervernek böngészhető HTML-t kell engednie, vagy külön privát webes megnyitás szükséges. A HTML-t ne tedd nyilvános mappába, ha az útvonalat privátnak szánod.

Források: [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/), [Leaflet](https://leafletjs.com/download.html). A Leaflet licence az assets/leaflet/LICENSE fájlban és az exportált HTML-ben is szerepel.
