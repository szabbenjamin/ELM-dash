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
        RadioStation("oxygen-rock", "Oxygen Classic Rock", "https://oxygenmusic.hu:8443/oxygenclassicrock_128", true)
    )
}
