# GitHubra feltöltés és másik gépre átvitel

## Mi a repository gyökere?

A **`elm-dashboard` mappa**, amelyben a `settings.gradle.kts`, `gradlew`, `app/`, `AGENTS.md` és ez a `docs/` könyvtár található. A körülötte lévő Codex munkamappát, `work/` könyvtárat, képernyőfotókat és generált APK-kat ne töltsd fel forrásként.

A projekt GitHub repositoryja: [szabbenjamin/ELM-dash](https://github.com/szabbenjamin/ELM-dash), alapértelmezett ága `main`. A repository meglévő előzménye és Apache-2.0 licence megmarad. A dokumentációban nincs személyes telefon-sorozatszám, BT-cím vagy helyi abszolút felhasználói útvonal.

## Új repository létrehozása esetén (a meglévő repóhoz nem szükséges)

1. Hozz létre egy üres GitHub repositoryt (például `elm-dashboard`); ne generáltass hozzá külön README-t. A public/private beállítást te választod.
2. Terminálban lépj a projekt gyökerébe, majd:

```sh
git status --short
git add .
git diff --cached --stat
# Szükség esetén saját neved/e-mailed lokális git config beállítása.
git commit -m "Initial ELM Dash Android project"
# A GitHub által megadott URL-lel; ez az érték csak helykitöltő:
git remote add origin https://github.com/szabbenjamin/ELM-dash.git
git push -u origin main
```

Hitelesítést GitHub saját bejelentkezésével/SSH-val/credential managerrel használj; jelszót vagy tokent ne írj a remote URL-be vagy forrásfájlba. Ha már van `origin`, ellenőrizd a `git remote -v` kimenetét, ne add hozzá újra.

A projekt meglévő workflow-ja push/PR esetén buildel és tesztel, majd debug APK-kat tesz a futás artifactjai közé. Ez nem Play-publikálás. A kísérleti AA-változatoknak nincs release változata.

## Mi marad helyben?

A `.gitignore` kizárja az SDK helyét (`local.properties`), Gradle/IDE cache-eket, buildkönyvtárakat, APK/AAB/ZIP eredményeket, aláírókulcsokat, `.env` fájlokat és helyi diagnosztikai könyvtárakat. A Gradle wrapper JAR szándékosan része a forrásnak. Ne használd a `git add -f` parancsot a kizárt gépi adatok feltöltésére.

A forrás nem tartalmazza a telefon SharedPreferences adatát: eszközcímek, útnapló, tankolások, napi statisztikák és saját rádiók a telefonon maradnak. CSV és képernyőfotó személyes adatot tartalmazhat, nem kerül automatikusan a repóba.

A repository licence [Apache-2.0](../LICENSE), a tulajdonos által létrehozott LICENSE alapján. A külső függőségek saját licencei ettől függetlenek.

## Fejlesztés másik gépen

```sh
git clone https://github.com/szabbenjamin/ELM-dash.git
cd ELM-dash
chmod +x gradlew
# JDK 17 + Android SDK 36 / Build Tools 35.0.0 telepítése után:
./gradlew :app:assembleMediaDebug
```

Az Android SDK útvonalát `ANDROID_HOME` vagy saját `local.properties` adja meg. Nyisd meg Android Studióban a projekt gyökerét. Agentnek elsőként az `AGENTS.md`-t add; az a részletes átadáshoz vezet.

### Fontos: debug aláírás és meglévő telefonos adatok

A debug APK a buildgép debug kulcsával készül. Másik laptop vagy GitHub Actions eltérő kulccsal aláírt APK-ja **nem feltétlenül frissíthető rá** a jelenlegi telefonos telepítésre. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` esetén ne töröld automatikusan a régi appot: az eltávolítás a helyi adatokat is törölheti. Tervezd meg a kulcs biztonságos átvitelét vagy az adatmigrációt. Kulcsot ne commitolj; a debug kulcs nem Play kiadási kulcs.

A kész forrás-ZIP kényelmi snapshot, a Git-repo a hosszú távú fejlesztési hely. Az APK és felhasználói screenshot külön eredmény, nem forrás.
