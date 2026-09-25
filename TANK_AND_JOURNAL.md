# Teletankolás, becsült szint és útnapló (0.9)

## Kalos tankméret

A 2005-ös Chevrolet Kalos 1.2 tankjának névleges kapacitása **45 liter**. A Kalos kezelési kézikönyv „Fuel gauge” fejezete 45 litert ad meg; a Kalos 2005 1.2 SE típusadat is ezt támasztja alá.

- [Kalos kezelési kézikönyv, 41. oldal](https://www.manualslib.com/manual/813940/Daewoo-Kalos.html?page=41)
- [2005 Chevrolet Kalos 1.2 SE típusadat](https://www.km77.com/coches/chevrolet/kalos/2005/3-puertas/se/kalos-3p-12-se/datos)

Ez a névleges tankméret, nem azt jelenti, hogy minden tankoláskor 45 litert kell betölteni. A becsléshez a Kalos fix 45 l profilját használjuk.

## Használat a telefonon

1. Nyisd meg az új **Napló** lapot, vagy a Műszerfal **Tankolás és útnapló** gombját.
2. Amikor tényleg tele lett a tank, válaszd a **Teletankoltam** gombot.
3. Opcionálisan írd be a kúton betöltött litert; ez a tankolási naplóba kerül, nem állítja át automatikusan a fogyasztáskorrekciót.
4. A **Teletankolás mentése** után a becsült szint 45 l / 100%. A korábbi utak és napi számlálók megmaradnak.

Az első jelzés előtt a szint **ismeretlen**; a program nem feltételezi, hogy tele volt. A felhasználó helyett a fejlesztési/telepítési próba sem rögzít tankolást. Az utolsó 100 teletankolás dátuma, opcionális kúti mennyisége és az előző jelzés óta megfigyelt fogyasztás visszanézhető.

```text
maradék = clamp(45 l − teletankolás óta megfigyelt liter, 0 l, 45 l)
százalék = maradék / 45 l × 100
```

A levonás az eredeti üzemanyagáram-minták trapézszabályos integrálásából történik, a 10 másodperces kijelzési simítástól függetlenül. Az alapjárat beleszámít. A tankolás új kiindulópont, nem törli vagy számolja át a napi/útnapló összesítéseit. Az út kézi nullázása sem nullázza a tankot, és nem számítja kétszer a korábbi fogyasztást.

**A kijelzés becslés, nem tankszenzor.** A Kalos MAP-alapú fogyasztási modellje kalibrálandó. Adatvesztés, járó motor közbeni megszakítás vagy megszakadt folyamat után „bizonytalan” jelzés jelenik meg; hiányzó fogyasztást nem találunk ki. Az alkalmazás nélkül megtett utak és nem jelzett részleges tankolások nem követhetők. Emiatt a százalék nem helyettesíti az autó saját üzemanyagszint-jelzését. Nem számolunk garantált hátralévő hatótávot.

## Útnapló

Csak valódi OBD-mérést mentünk, a demó nem terheli a tankot és nem ír naplót. A következő új mérés már automatikusan naplózódik; a régi verziók részletes útjai nem rekonstruálhatók a napi összesítésből. Az utolsó 200 út marad az alkalmazásban, korábbi megőrzéshez CSV export használható.

Útonként:

- kezdés, befejezés és időtartam;
- megfigyelt kilométer, liter és l/100 km átlag;
- átlaghoz ténylegesen használt közös liter/km szakasz és adatok lefedettsége;
- átlagsebesség az érvényes sebességminták idejére, legnagyobb sebesség;
- megfigyelt mozgási idő, alapjárati idő és alapjárati liter;
- legnagyobb RPM, vízhőfok minimum/maximum, idővel súlyozott átlagos motorterhelés;
- fogyasztás forrása/profilja, becslés és adathiány jelzése;
- a lezárás oka.

Az út első érvényes, járó motort jelző RPM-nél indul. Kézi/AA-szolgáltatás leállítása vagy útnullázás lezárja. Nyolc másodperc folyamatosan megfigyelt nulla RPM motorleállítást jelez; új ECU-futásidő új motorindításként külön utat nyit, a 16 bites futásidő túlcsordulása nem. Rövid automatikus BT-újracsatlakozás ugyanazt az utat folytatja, adathiányjelzéssel. 180 s friss RPM nélkül lezárjuk a naplózott utat; a kapcsolatkezelő ettől még próbálkozhat. A kapcsolódás előtti szakasz nem ismert. Az időtartam a megfigyelési munkamenet ideje, nem GPS-szel mért vezetési idő.

A motoradatokból nincs térképes útvonal vagy cím; új GPS/helyengedélyt nem kérünk. Az üzemanyaggal párosított távolság 100 m alatt nem ad átlagot, és az üres érték nem nulla fogyasztást jelent.

## Mentés és újraindítás

A `trip/JourneyLog` tiszta Kotlin osztály kezeli a tankot, a naplóhatárokat és statisztikákat. A `connection/JourneyStore` egyetlen verziózott JSON-pillanatképbe menti a tankot, tankolásokat, lezárt utakat és az aktív út vázlatát. A mentés normálisan 5 másodpercenként, valamint leállításkor, tankoláskor és kézi útnullázáskor történik. Váratlan leállításnál a legutóbbi ellenőrzőpont óta keletkezett adatok elveszhetnek; az Android preferenciamentése aszinkron.

Új folyamatban a megmaradt aktív vázlat egyszer, „Alkalmazás megszakadt” jelzéssel lezárul, és adathiányosnak számít. Nem interpolálunk kikapcsolt telefonon keresztül, és a már mentett fogyasztást nem vonjuk le ismét. A szokásos leállítás nem duplázza meg a rekordot. Nincs hálózati feltöltés.

## CSV és Android Auto

A Napló **CSV mentése** gombja az Android fájlmentőjét nyitja. UTF-8 BOM, pontosvesszős, idézőjelezett mezők; a fájl időpontjai ISO-8601 UTC szerint szerepelnek, a telefonos lista helyi időt mutat. Az aktív vázlat külön „Folyamatban” sor lehet. A mentés a felhasználó által választott helyre történik, nem küldi automatikusan másnak.

Az AA **Műszerek / fogyasztás** listájában új **Becsült üzemanyagszint** lap van; a műszerlapváltó gombbal is elérhető. A tank százalék, liter és bizonytalanság ezen a lapon az alsó sorba kerül, a rádiónévvel felváltva. A borító és az alap fogyasztási főcím változatlan. Tankolást a telefon Napló lapján lehet rögzíteni.

## Út végi értesítés (0.10)

Az út nem egész nap: az első járó motoros mérésnél kezdődik. 8 másodperc folyamatos nulla RPM lezárja; ha a motor leállítása után az ECU már nem válaszol, a legutóbbi friss motoradat után 3 perc türelmi idővel zárul. Rövidebb kapcsolatkimaradás ugyanazt az utat folytatja. Az AA automatikus adatgyűjtése az AA bontása után 3 perc + 2 másodpercig életben marad, így az útlezárás meg tud történni. Visszakapcsolódó AA megszakítja ezt a leállítási időzítőt. A rádió külön, korábbi szabály szerint áll le. Kézi mérésleállítás azonnal lezár; kézi útnullázás ment, de nem küld út végi értesítést.

Lezáráskor az **Út végi összesítés** értesítési csatorna telefonos értesítést ad: megtett km, felhasznált liter, átlag l/100 km. A becsült és részleges adat külön jelzett, hiányzó fogyasztás/átlag helyén „—” látszik. Az átlag a közösen mért liter/távolság alapján számolódik, nem a kijelző 10 másodperces átlagából. Az értesítés a háttérszolgáltatás leállítása után is megmarad; ugyanaz az út nem küldi újra, ha törölted. Megnyomása az alkalmazást nyitja meg, részletek a Napló lapon.

Az app értesítési engedélye és az Út végi összesítés csatorna legyen engedélyezve. A háromperces várakozás háttérszolgáltatás mellett is fut, akkor is, ha a véges Bluetooth-próbálkozások előbb elfogynak. Android általi kényszerleállítás vagy telefonkikapcsolás közben nem tudunk értesíteni; az újranyitáskor helyreállított régi naplót nem jelentjük új utazásként. Az értesítés telefonra szól; AA felugró megjelenítést nem garantál.
