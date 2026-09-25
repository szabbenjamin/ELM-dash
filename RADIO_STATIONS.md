# Rádióállomások

Ellenőrzés: **2026-09-16**. A közvetlen streamek GET-kérésre HTTP 200 választ, audio Content-Type-ot és legalább 2048 bájt adatot adtak. Ez pillanatnyi elérhetőségi ellenőrzés, nem folyamatos rendelkezésre állási garancia. Nem készül hangfelvétel vagy letöltési gyorsítótár.

A válogatás alapja az [NMHH 2026. március–május országos és budapesti napi rádióhallgatottsági kimutatása](https://nmhh.hu/cikk/260314/Budapesti_es_orszagos_napi_radiohallgatottsag_2026_marciusmajus), kiegészítve a kért Oxygen adásokkal és Jazzyvel. A sorrend az alkalmazásban nem hallgatottsági rangsor. Hálózatos rádióknál egy központi/budapesti adást választunk.

| Állomás | Stream | Forrás | Ellenőrzött formátum |
|---|---|---|---|
| Retro Rádió | `https://icast.connectmedia.hu/5001/live.mp3` | [Hivatalos oldal](https://retroradio.hu/) | audio/mpeg |
| Rádió 1 | `https://icast.connectmedia.hu/5201/live.mp3` | [Hivatalos oldal](https://radio1.hu/) | audio/mpeg |
| Best FM | `https://icast.connectmedia.hu/5101/live.mp3/` | [Hivatalos oldal](https://bestfm.hu/) | audio/mpeg |
| Sláger FM | `https://slagerfm.netregator.hu:7813/slagerfm128.mp3` | [Hivatalos oldal](https://slagerfm.hu/) | audio/mpeg |
| Jazzy | `https://radio.musorok.org/listen/jazzy/jazzy.mp3` | [Hivatalos oldal](https://jazzy.hu/) | audio/mpeg |
| Kossuth Rádió | `https://mr-stream.connectmedia.hu/4736/mr1.mp3` | [Hivatalos oldal](https://mediaklikk.hu/kossuth/) | audio/mpeg |
| Petőfi Rádió | `https://mr-stream.connectmedia.hu/4738/mr2.mp3` | [Hivatalos oldal](https://mediaklikk.hu/petofi/) | audio/mpeg |
| Bartók Rádió | `https://mr-stream.connectmedia.hu/4741/mr3.mp3` | [Hivatalos oldal](https://mediaklikk.hu/bartok/) | audio/mpeg |
| Dankó Rádió | `https://mr-stream.connectmedia.hu/4748/mr7.mp3` | [Hivatalos oldal](https://mediaklikk.hu/danko/) | audio/mpeg |
| Oxygen Music | `https://oxygenmusic.hu:8443/oxygenmusic` | [Hivatalos oldal](https://oxygenmusic.hu/) | audio/aac |
| Oxygen Classic Rock | `https://oxygenmusic.hu:8443/oxygenclassicrock_128` | [Hivatalos oldal](https://oxygenmusic.hu/csatorna/oxygen-classic-rock) | audio/mpeg |
| Hír FM | `https://stream.rcs.revma.com/wevb267khf9uv` | [Hivatalos oldal](https://hirfm.hu/stream/) | audio/mpeg |
| 103.9 a ROCK | `https://stream.rockradio.hu` | [Hivatalos oldal](https://rockradio.hu/) | audio/mpeg |
| InfoRádió | `https://stream.infostart.hu/stream` | [Hivatalos oldal](https://infostart.hu/) | audio/mpeg |

Az Oxygen Music címe a felhasználó által megadott pontos URL. A többi állomás streamje a rádió saját oldalának lejátszójából/nyilvános streamkiszolgálójából származik. A címek a `RadioCatalog.kt` fájlban módosíthatók. A saját állomásokat és kedvenceket a telefon helyben menti; az állomások adatfolyamai közvetlenül a szolgáltatókhoz kapcsolódnak.

A **Roxy** pontos linkje még hiányzik, ezért nincs találomra felvett Roxy állomás. Megadható a telefon Rádió lapján saját állomásként, vagy beépíthető a katalógusba, amint a felhasználó elküldi.
