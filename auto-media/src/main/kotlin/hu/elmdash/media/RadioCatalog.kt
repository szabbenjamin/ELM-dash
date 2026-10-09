package hu.elmdash.media

/** Public live streams; sources and verification date: RADIO_STATIONS.md. */
internal object RadioCatalog {
    val stations = listOf(
        RadioStation("oxygen", "Oxygen Music", "https://oxygenmusic.hu:8443/oxygenmusic", true),
        RadioStation("retro", "Retro Rádió", "https://icast.connectmedia.hu/5001/live.mp3", true),
        RadioStation("radio1", "Rádió 1", "https://icast.connectmedia.hu/5201/live.mp3", true),
        RadioStation("best", "Best FM", "https://icast.connectmedia.hu/5101/live.mp3/", true),
        RadioStation("slager", "Sláger FM", "https://slagerfm.netregator.hu:7813/slagerfm128.mp3", true),
        RadioStation("jazzy", "Jazzy", "https://radio.musorok.org/listen/jazzy/jazzy.mp3", true),
        RadioStation("kossuth", "Kossuth Rádió", "https://mr-stream.connectmedia.hu/4736/mr1.mp3", true),
        RadioStation("petofi", "Petőfi Rádió", "https://mr-stream.connectmedia.hu/4738/mr2.mp3", true),
        RadioStation("bartok", "Bartók Rádió", "https://mr-stream.connectmedia.hu/4741/mr3.mp3", true),
        RadioStation("danko", "Dankó Rádió", "https://mr-stream.connectmedia.hu/4748/mr7.mp3", true),
        RadioStation("hir", "Hír FM", "https://stream.rcs.revma.com/wevb267khf9uv", true),
        RadioStation("rock", "103.9 a ROCK", "https://stream.rockradio.hu", true),
        RadioStation("info", "InfoRádió", "https://stream.infostart.hu/stream", true),
        RadioStation("oxygen-rock", "Oxygen Classic Rock", "https://oxygenmusic.hu:8443/oxygenclassicrock_128", true),
        RadioStation("maria-hu", "Mária Rádió", "https://stream.mariaradio.hu:8000/mr", true),
        RadioStation("katolikus-hu", "Magyar Katolikus Rádió", "https://katolikusradio.hu:8001/live_hi.mp3", true),
        RadioStation("ucb1", "UCB 1 (angol)", "https://listen-ucb.sharp-stream.com/55_ucb_1_128_mp3", true),
        RadioStation("ucb2", "UCB 2 (angol)", "https://listen-ucb.sharp-stream.com/55_ucb_2_128_mp3", true),
        RadioStation("erf-plus", "ERF Plus (német)", "https://stream.erfplus.de/erf-1a64/mp3-128?ar-distributor=ffa5", true),
        RadioStation("erf-jess", "ERF Jess (német)", "https://stream.erfjess.de/erf-1068/mp3-128?ar-distributor=ffa5", true)
    )
}
