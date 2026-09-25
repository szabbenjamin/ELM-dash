package hu.elmdash.elm

data class ObdFrame(val ecu: String?, val pid: Int, val bytes: List<Int>)

sealed interface ElmReply {
    data class Data(val frames: List<ObdFrame>) : ElmReply
    data object NoData : ElmReply
    data class Error(val message: String) : ElmReply
}

/** Mode 01 single-frame responses only. ISO-TP multi-frame services are deliberately out of scope. */
object ElmParser {
    private val errors = listOf("UNABLE TO CONNECT", "BUS ERROR", "CAN ERROR", "BUFFER FULL", "STOPPED", "DATA ERROR", "RX ERROR", "BUS INIT: ERROR")

    fun mode01(raw: String, pid: Int): ElmReply {
        val upper = raw.uppercase()
        val frames = upper.replace("SEARCHING...", "").replace("BUS INIT: OK", "")
            .split('\r', '\n', '>').mapNotNull { line -> parseLine(line.trim(), pid) }
        if (frames.isNotEmpty()) return ElmReply.Data(frames)
        errors.firstOrNull { it in upper }?.let { return ElmReply.Error(it) }
        if ("NO DATA" in upper) return ElmReply.NoData
        if ('?' in upper) return ElmReply.Error("Nem támogatott ELM parancs")
        if (Regex("7F\\s*01").containsMatchIn(upper)) return ElmReply.Error("ECU negatív válasz")
        return ElmReply.Error("Hiányos vagy ismeretlen válasz")
    }

    private fun parseLine(line: String, pid: Int): ObdFrame? {
        if (line.isBlank()) return null
        val parts = line.split(Regex("\\s+"))
        var ecu: String? = null
        var hex: String
        val spaced29 = parts.size >= 7 && parts.take(4).all { it.length == 2 && it.toIntOrNull(16) != null } && parts[5] == "41" && parts[4].toIntOrNull(16) in 2..8
        if (spaced29 || (parts.first().length in listOf(3, 8) && parts.first().all { it.digitToIntOrNull(16) != null } && parts.size > 1)) {
            ecu = if (spaced29) parts.take(4).joinToString("") else parts.first()
            hex = parts.drop(if (spaced29) 4 else 1).joinToString("")
            // CAN single-frame PCI / DLC. Reject first/consecutive multi-frame packets.
            val length = hex.take(2).toIntOrNull(16) ?: return null
            if (length !in 2..8 || hex.length < 2 + length * 2) return null
            hex = hex.drop(2).take(length * 2)
        } else {
            hex = parts.joinToString("")
            if (hex.any { it.digitToIntOrNull(16) == null } || hex.length % 2 != 0) return null
            // ATH1 ISO 9141 / KWP / J1850: three header bytes, service, PID, data, checksum.
            if (!hex.startsWith("41") && hex.length >= 12 && hex.substring(6, 8) == "41") {
                ecu = hex.substring(4, 6)
                hex = hex.drop(6).dropLast(2)
            }
        }
        if (hex.length < 4 || hex.length % 2 != 0 || hex.any { it.digitToIntOrNull(16) == null }) return null
        val bytes = hex.chunked(2).map { it.toInt(16) }
        if (bytes[0] != 0x41 || bytes[1] != pid) return null
        return ObdFrame(ecu, pid, bytes.drop(2))
    }

    fun voltage(raw: String): Double? = Regex("(?i)(\\d{1,2}(?:\\.\\d+)?)\\s*V")
        .find(raw)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it in 0.0..32.0 }
}
