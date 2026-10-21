# Útinform első változat – 0.16.0

## NAP regisztráció és beállítás

1. Nyisd meg a [NAP portált](https://napportal.kozut.hu/), és regisztrálj adatfelhasználóként. Magánszemély is regisztrálhat; nem kell közútkezelő/adatszolgáltató fiók vagy Google Play-regisztráció.
2. Erősítsd meg az e-mail-címedet; ha a portál jóváhagyásra váró állapotot jelez, várd meg az aktiválást.
3. Keresd meg az **Útinform datex** országos adatcsomagot: [26-os profil](https://napportal.kozut.hu/#/configuration/data-provider/profile/26).
4. Iratkozz fel az adatcsomagra. Választások, ahol a portál felkínálja: **HTTP**, **Pull**, **DATEX II 2.3 / XML**, **teljes adathalmaz**, **delta kikapcsolva**. Ne SOAP-ot vagy Push-t válassz. A portál verziójától függhet a mezők neve.
5. Az előfizetéseknél másold ki a generált **adatlekérési URL-t**. Nem a profil böngészőcíme kell. Az URL érzékeny hozzáférési tokent tartalmazhat: ne tedd GitHubra vagy képernyőképre.
6. ELM Dash: **fogaskerék → Útinform**. Add meg a HTTPS-linket, kapcsold be a funkciót, engedélyezd a pontos helyet. Automatikus AA-induláshoz a rendszer helyengedélye legyen **Mindig engedélyezett**.
7. **Mentés és próba indítása**. Az állapot mutatja a feldolgozott és kihagyott események számát, a GPS-várakozást és a forrás korát. NAP-hiba esetén az URL nem kerül hibaüzenetbe.

A hivatalos [használati útmutató](https://napportal.kozut.hu/#/download/useful-download) 17–18. oldala írja le az előfizetést és a Pull-linket. A konkrét fiók és éles feed még nem lett kipróbálva. Ha a kapott hozzáférés nem HTTPS GET XML, külön hitelesítést igényel, vagy csak TMC-helyeket ad, az adaptert valódi mintához kell igazítani. A portál jelszavát az app nem kéri és nem kezeli.

## Működés

- Külön location foreground service, OBD- és útvonalnapló-függetlenül. Helyadat csak memóriában; a NAP-kérés nem tartalmaz GPS/OBD-adatot. Nem készül WebDAV-export ebből a figyelésből.
- AA-csatlakozáskor indul, leváláskor leáll. A telefonos kézi próba AA nélkül legfeljebb 30 percig fut. Értesítésből és beállításból is leállítható; új AA-csatlakozás újraindíthatja, ha engedélyezett maradt.
- GNSS-frissítés kérése 3 másodpercenként, legfeljebb 20 másodperces és 100 méternél nem rosszabb pontosságú helyek. Az Android tényleges ütemezése eltérhet.
- HTTP letöltés 5 percenként, legfeljebb 25 másodperces hívással és 24 MiB válasszal, átirányítás nélkül. Hálózati hiba után a még friss memóriabeli adat használható; a forrás időbélyege és az utolsó letöltés is legfeljebb 15 perces lehet. Újrainduláskor új lekérés kell.
- DATEX II 2.x SituationPublication teljes pillanatképek. Koordinátás pont vagy explicit `locationForDisplay`; utóbbi csak reprezentatív pont, nem az egész útszakasz geometriája. TMC/OpenLR, többértelmű többpontos hely, ismeretlen eseménytípus, tesztadat, felfüggesztett esemény és ismétlődő érvényességi időszak kihagyva. Nincs kitalált koordináta.
- Mozgáskor legfeljebb 3 percnyi légvonalbeli előretekintés, 2–8 km közötti sugár; ±55° a haladás irányához képest. Ha az esemény menetiránya ismert, ±65° egyezés is szükséges. Megbízható irány nélkül legfeljebb 1,5 km környezet.
- Nincs térképi útillesztés és Waze/Maps-útvonal. Párhuzamos/közeli úton is lehet találat; a kijelzés nem állítja, hogy biztosan az esemény felé navigálsz. A távolság légvonalbeli becslés.
- Egy esemény 20 másodpercre kapja meg az AA alsó metaadatsorát. A szöveg ezalatt stabil, nem változik minden GPS-fixszel. Legfeljebb percenként új esemény, ugyanaz 30 percen belül nem ismétlődik. Ha eltűnik a releváns találatok közül vagy elavul a hely/feed, azonnal megszűnik a jelzés.
- A fogyasztási főcím és borító megmarad. Jelzés után visszatér az RPM/vízhőfok/rádió sor. Demómotoradatokkal nem keverünk valódi közlekedési eseményt.
- Nincs hangos figyelmeztetés vagy Waze fölé rajzolt popup. Csak az AA által megjelenített ELM médiafelületen látszik; kísérleti, hostfüggő integráció.

## Biztonság és fejlesztői térkép

`traffic` tiszta Kotlin modul: DatexParser, TrafficFilter, TrafficPresenter. `connection`: TrafficSettings (Android Keystore AES/GCM, titkosított teljes URL), TrafficService/TrafficRuntime. `dashboard`: TrafficSettingsScreen. `auto-media`: AutoRadio kapcsolati események és MediaDashboard alsó sor.

Az XML méretkorlátos, UTF-8; DTD és külső entitás tiltott. HTML bejelentkezési oldal nem számít sikeres üres adatfolyamnak. Előfizetési URL, nyers XML és GPS nem kerül naplóba, mentett UI-állapotba vagy Gitbe. Az app biztonsági mentése továbbra is tiltott. Az endpoint IP-címet természetesen lát, mint minden hálózati lekérésnél.

Az első éles ellenőrzéshez fontos: feed formátuma és publikációs idő, koordináták elérhetősége, kihagyott rekordok aránya, érvényességi ablakok, frissítés és törlés. Sikeres build/fixture teszt nem bizonyít NAP-kompatibilitást vagy Carpuride-olvashatóságot.

## 0.16.18 — éles NAP-próba

A 2026-10-20-i válasz 12 575 682 bájt volt; a korábbi 5 MiB korlát elutasította. Új korlát 24 MiB, külön méret-, HTTP-, TLS-, hálózati és formátumhiba. Az éles XML feldolgozható, de 3145 rekordból jelenleg 0 támogatott, 3145 kihagyott. A megvizsgált helyhivatkozás lineáris útelem referenciapontja és métereltolása, nem közvetlen eseménykoordináta. Ezekhez további helyfeloldás szükséges; referenciapontot nem tekintünk eseményhelynek. A hozzáférési URL és a nyers feed nem része a repónak.
