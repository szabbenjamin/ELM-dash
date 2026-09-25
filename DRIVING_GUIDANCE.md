# Kalos 1.2: grafikus fordulatszám- és váltásjelzés

Profil: **Chevrolet Kalos, 2005, 1.2 8V E-TEC / SOHC, szívó benzines, ötfokozatú kézi váltó**. A jelzés a friss RPM, sebesség, számított motorterhelés, TPS és vízhőfok adataiból készül. Ez tájékoztató becslés, nem gyári váltásjelző és nem az ECU által számított optimális fokozat.

## Az Android Auto főképernyője

Nagy, színes hátterű fordulatszám–vízhőfok műszergrafika, mellette négy stabil adatsor:

1. **Fordulatszám / terhelés:** aktuális rpm, motorterhelés és — ha stabilan becsülhető — `≈4.` fokozatjelzés. A nagy grafika színe és nyila adja a javaslatot.
2. **Vízhőfok:** hűtőfolyadék °C, hőmérő, színes háttér.
3. **Fogyasztás / útátlag:** aktuális l/100 km vagy l/h; mellette az útátlag.
4. **Sebesség / kapcsolat:** aktuális km/h és élő/demó/kapcsolati állapot.

Az alsó Motor / szenzorok gomb a motoradatokat nyitja; onnan a Szenzorok gombbal érhető el a MAP/TPS/feszültség/MAF lap. Az Út és fogyasztás gomb a fogyasztás, útátlag és távolság/üzemanyag részleteit nyitja. A fejlécben Demó / Leállítás.

| Fordulatszám-grafika | Jelentés |
|---|---|
| Zöld, pipa | A profil szerinti kedvező tartomány, mérsékelt terheléssel |
| Sárga, ↑ | Felváltás megfontolható |
| Sárga, ↓ | Terhelt, alacsony fordulatnál visszaváltás megfontolható |
| Sárga, ! | Nagy terhelés vagy az alap takarékos tartományon kívüli fordulat; nincs biztos váltási javaslat |
| Kék | Melegedő motor; nincs váltási nyíl |
| Piros | Magas vízhőfok; nincs váltási nyíl |
| Sötét semleges | Alapjárat, gurulás vagy nincs külön javaslat |
| Szürke | Nincs elegendő friss adat a jelzéshez |

Adatkimaradáskor az utolsó számérték megmarad és elszürkül. A váltási nyíl megszűnik; régi adatra nem adunk tanácsot. A különböző jelek segítik a színek megkülönböztetését is.

## Az MVP szabályai

A [Kalos gyári kezelési kézikönyv másolata](https://pdfcoffee.com/chevrolet-kalos-manual-english-pdf-free.html) a 2–6. oldalon **2000–3000 rpm** közötti takarékos fordulatszám-tartományt említ. A többi alábbi határ saját, konzervatív MVP-beállítás, amelyet valódi autós mérés alapján kell pontosítani.

- Zöld jelzés: melegedési szakaszon túli vízhőfok, 2000–3000 rpm, 70% alatti számított terhelés, nem zárt fojtószelep.
- Felváltás: legalább 3000 rpm, legfeljebb 45% terhelés, legalább 20 km/h, stabilan becsült 1–4. fokozat. A következő fokozatban becsült fordulatnak legalább 1900 rpm-nek kell maradnia.
- Visszaváltás: 2000 rpm alatti fordulat, legalább 70% terhelés, legalább 8% TPS, stabilan becsült 2–5. fokozat. Az egy fokozattal lejjebb becsült fordulat 4000 rpm alatti legyen.
- 8 km/h alatt, hideg/melegedő motornál, magas vízhőfoknál, zárt fojtószelepnél vagy hiányos adatnál nincs váltási nyíl. Ötödikben nincs fel-, elsőben nincs visszaváltási nyíl.
- A felváltási jelzés visszakapcsolási határa 2850 rpm / 55% terhelés; a visszaváltásé 2150 rpm / 62% terhelés. Ez a kis holtsáv és a 2 másodperces stabilitási idő csökkenti az ide-oda váltó jelzést.

A nagy fordulat önmagában nem bizonyít motorhibát vagy túlpörgetést: erősebb gyorsításkor szükséges is lehet. A grafika takarékos haladáshoz ad támpontot; nem ismeri a forgalmi helyzetet, az emelkedőt vagy a vezető gyorsítási szándékát. A vízhőfok nem olajhőmérséklet.

### Fokozatbecslés

A kézikönyv 8–8. oldalán szereplő 1.2 SOHC áttételek: **3,416 / 1,950 / 1,280 / 0,971 / 0,757**, végáttétel **4,105**. A megadott két kerékmérethez közeli, **1,81 m** kerületből számolunk. Ezek nem az OBD-ből olvasott konfigurációs adatok.

```text
fordulat / sebesség ≈ fokozatáttétel × végáttétel × 1000 / (60 × kerékkerület)
```

Csak 12 km/h és 1000 rpm felett, legfeljebb 8% arányeltéréssel, 3 másodpercig változatlan jelöltből fogadunk el fokozatbecslést. Megváltozó vagy bizonytalan aránynál a korábbi nyíl azonnal eltűnik. Nem olvasunk kuplungkapcsolót; a becslés ezt nem helyettesíti. Eltérő váltó, kerék vagy csúszó kuplung esetén a profil nem feltétlenül alkalmazható.

### Vízhőfok színezése

Kezdő besorolás: 60 °C alatt hideg/kék, 60–79 °C melegedő/kék, 80–102 °C zöld, 103–109 °C sárga, 110 °C-tól piros. A visszaváltási holtsáv néhány fok, hogy a szín ne villogjon a határon. **Ezek alkalmazásbeli jelzési határok, nem igazolt Kalos ventilátorkapcsolási vagy gyári túlmelegedési küszöbök.**

## Fejegység és tesztelés

Az Auto a `PaneTemplate` elrendezését és méretezését kezeli. A színes háttereket az alkalmazás a műszergrafikán belül rajzolja, nem a host teljes hátterét módosítja. Car API 4-től közös nagy kép, korábbi hoston soronkénti grafika jelenik meg. A sablon- és sorcímek nem változnak, a frissítési kérés legfeljebb 2 másodpercenként történik. [PaneTemplate referencia](https://developer.android.com/reference/androidx/car/app/model/PaneTemplate)

A 120 másodperces demóciklus alapjáratot, zöld tartományt, terhelt alacsony fordulatot, felváltási helyzetet, ötödik fokozatot és nagy terhelést mutat. Az ASCII OBD-feldolgozási lánc ugyanaz, mint élő kapcsolatnál. A váltási logikát külön JVM-tesztek ellenőrzik; a valódi autós validálás még szükséges.
