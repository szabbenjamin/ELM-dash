# ChatGPT-s tankolásiblokk-feldolgozás — 0.19.0

A Napló → Teletankoltam képernyőn a ChatGPT-fiókkal történő belépés az alapértelmezett AI-lehetőség. A helyi ML Kit OCR külön gombbal továbbra is választható, kézi adatbevitel belépés nélkül is működik.

## Felhasználói folyamat

1. **Continue with ChatGPT**: az Android rendszerböngészőjében OpenAI-fiókválasztás és az ELM Dash engedélyezése. A használat a választott ChatGPT-csomag/engedélyezett keret szabályait követi. Nincs külön API-kulcs vagy fizetős API-ra automatikus váltás.
2. Visszatérés az appba. A modelleket a belépett fiók katalógusából kérjük, alapértelmezés az első megjeleníthető modell; a **ChatGPT-fiók és modell** alatt módosítható. Ha egy modell nem fogad képet, válassz másikat; a jogosultság és keret a szolgáltatótól függ.
3. Fotózás vagy képválasztás. Ettől még nem történik feltöltés.
4. **Feldolgozás ChatGPT-vel**: az átméretezett JPEG közvetlenül `api.openai.com` felé megy. Saját köztes szerver nincs. A fotó újrakódolása eltávolítja az eredeti EXIF-adatokat, de a képen látható személyes adatokat nem takarja ki.
5. Üzemanyagnév, liter és kedvezmény utáni, kizárólag üzemanyagra jutó Ft kerül a szerkeszthető mezőkbe. Más termékek nem számítanak bele. Nem egyértelmű összkedvezménynél/olvashatatlan tételnél az érintett adat null/üres maradhat, magyarázattal.
6. A felhasználó ellenőrzi az adatokat, majd külön megerősíti a teletankolást. Az AI nem állapítja meg a tele tank tényét, nem ír közvetlenül naplóba vagy kalibrációba.

A felhős képelőkészítés Android 9+-on érhető el. Régebbi rendszeren helyi OCR/kézi bevitel marad. Hálózati, modell-, keret- vagy felismerési hiba esetén nincs csendes fizetős fallback.

## Belépés és tárolás

- Dokumentált Sign in with ChatGPT OSS dinamikus kliensregisztráció: `dynamic_agent_client`, állandó `urn:uuid` hostazonosító, `agent_name_hint=ELM Dash`. Az Apache-2.0 projekt forrása nyilvános.
- Rövid életű loopback listener kizárólag `127.0.0.1` címen, véletlen porton, `/auth/callback` útvonallal. Legfeljebb 5 perc; az app/ablak megszűnésekor megszakad. Elveszett belépés újraindítható.
- Véletlen state + nonce, PKCE S256; a code exchange a kapott issued client_id-val történik. Visszatérő bejelentkezés megőrzi a kliens/fiók összerendelését. Másik fiók nem írja felül a régi regisztrációt.
- RS256 ID-token aláírás ellenőrzése a hivatalos JWKS ellen, issuer/audience/azp/expiry/nonce/sub ellenőrzés. A képfeldolgozáshoz a visszaadott `resource.invoke` és `chatgpt.tokens.use.direct` scope is kötelező.
- Tokenek és fiókrekordok külön Android Keystore AES-GCM kulccsal titkosított helyi tárolóban, az app backupja tiltva. Nincs token/jelszó/receipt naplózás, Git, WebDAV vagy analitika.
- Sorosított refresh a lejárat előtt; kijelentkezéskor revokációs kísérlet és helyi tokentörlés. Nem igazolt távoli visszavonás esetén a felület jelzi a ChatGPT-beállításokban szükséges ellenőrzést. Regisztráció és hostazonosító későbbi belépéshez megmarad.
- `POST /v1/responses`, `store:false`, `stream:true`, kép + strukturált JSON-kimenet. A szöveg a `response.output_text.done/delta` eseményekből is összeállhat; kizárólag sikeres `response.completed` lezárás után fogadunk eredményt; részleges/refused/hibás válasz nem menthető. Ez a kapcsoló nem ígéret a szolgáltató minden adatkezelésének hiányára.
- Az inference nem kap eszközöket, fájlrendszer- vagy hálózati hozzáférést. A blokk szövege adat, nem végrehajtandó utasítás.

## Ellenőrzés / nyitott próba

Automatizált: JWT érvényes/hibás aláírás és claim-ek, PKCE tesztvektor, scope-hiány, összeg/liter/pénznem ellenőrzés, befejezett és csonka SSE. A modell felismerési minősége csak valódi, felhasználó által engedélyezetten feltöltött blokkokkal mérhető. A tesztek szintetikus blokkszöveget használnak, nem valódi tankolási rekordot.

Hivatalos források (2026-10-26):
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
- https://developers.openai.com/siwc/ui-ux-guidelines


## 0.23.0 — Tankoltam, részleges tankolás és automatikus blokkfeldolgozás
Felhasználói kérés: Tankoltam gomb, alapértelmezetten üres „Lekattanásig tankoltam (tele tank)” jelölő. Liter/ár/fajta ellenőrzése és mentés továbbra is szükséges; részleges tankolás is menthető. Ismeretlen kezdőszint részleges tankolástól nem válik ismertté. Külön addedLiters tartós számláló, a nyers fogyasztást nem csökkentjük; következő teletank tanulási arányába az összes köztes részleges liter is beleszámít. Részleges rekord nem kalibrál; régi rekordok teletankként olvashatók. Statisztikában minden rekord saját liter/ár mezője számít, a ciklusösszeg nem adódik hozzá újra.

ChatGPT módban képválasztás/fotózás automatikusan elküldi a fotót elemzésre (új, explicit felhasználói engedély); előtte a felület ezt jelzi. Nincs külön Feldolgozás gomb. Hiba után kézi Újrapróbálás, nincs automatikus ismételt keretfogyasztás. Helyi OCR továbbra is választható.


## 0.25.0 — egyetlen blokk-CTA és tankolási hely
Tankolás oldalon egyetlen Blokk beolvasása CTA, modal fájlválasztás/befotózás. ChatGPT feldolgozás automatikus, a mentett modell használatával; érvénytelen modellnél elérhető modell választódik. Nincs fiók/modell/keret/helyi OCR menüsor a tankolási oldalon. Bejelentkezés szükség esetén ugyanebből a CTA-ból indul. Kézi bevitel továbbra is lehetséges.

A felhasználó kifejezetten kérte a GPS és blokk alapú kútazonosítást. Csak blokkfeldolgozáskor, meglévő helyengedéllyel kér egyszeri helyfixet (5 s), vagy legfeljebb 5 perces, 150 m-nél pontosabb utolsó helyet. A koordináta és visszageokódolt cím a fotóval együtt az AI-hoz kerül; a CTA mellett ez jelezve van. A visszageokódolást az Android helyszolgáltatója végzi. GPS önmagában nem igazolja a kutat, régi/feltöltött blokk helyét nem helyettesítheti az aktuális hely. Nem kitalált POI-adatbázis: a blokk az elsődleges, hiányzó adat null. Szerkeszthető Benzinkút/cím mező, ellenőrző összesítő és tankolási napló; opcionális station mező tartósan mentve, régi rekordok változatlanok. Nyers GPS külön nem kerül a tankolási rekordba.
