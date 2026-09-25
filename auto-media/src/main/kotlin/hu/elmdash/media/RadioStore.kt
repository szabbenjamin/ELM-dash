package hu.elmdash.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.text.Normalizer
import java.util.Locale

internal data class RadioStation(val id: String, val name: String, val url: String, val builtIn: Boolean = false)
internal data class RadioState(
    val stations: List<RadioStation>, val selectedId: String,
    val playing: Boolean = false, val buffering: Boolean = false, val error: String? = null,
    val favorites: Set<String> = emptySet(),
    val recoveryMessage: String? = null, val autoPlayOnAa: Boolean = true
) { val selected: RadioStation get() = stations.firstOrNull { it.id == selectedId } ?: stations.first() }

internal class RadioStore(context: Context) {
    private val prefs = context.getSharedPreferences("elm-radio", Context.MODE_PRIVATE)
    private val defaults = RadioCatalog.stations
    private val custom = runCatching {
        val array = JSONArray(prefs.getString("stations", "[]"))
        (0 until array.length()).mapNotNull { i -> runCatching {
            val j = array.getJSONObject(i)
            RadioStation(j.getString("id"), j.getString("name"), validateUrl(j.getString("url")))
        }.getOrNull() }.take(20)
    }.getOrDefault(emptyList())
    private val mutable = MutableStateFlow(RadioState(defaults + custom, prefs.getString("selected", "oxygen")!!,
        favorites = prefs.getStringSet("favorites", setOf("oxygen"))!!.toSet(),
        autoPlayOnAa = prefs.getBoolean("autoPlayOnAa", true)))
    val state = mutable.asStateFlow()

    var pausedForAa: Boolean
        get() = prefs.getBoolean("pausedForAa", false)
        set(value) { prefs.edit().putBoolean("pausedForAa", value).apply() }
    fun autoPlay(enabled: Boolean) {
        mutable.value = mutable.value.copy(autoPlayOnAa = enabled)
        prefs.edit().putBoolean("autoPlayOnAa", enabled).apply()
    }
    fun toggleFavorite(id: String) {
        require(mutable.value.stations.any { it.id == id })
        val favorites = mutable.value.favorites.let { if (id in it) it - id else it + id }
        mutable.value = mutable.value.copy(favorites = favorites)
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    fun select(id: String) {
        require(mutable.value.stations.any { it.id == id })
        mutable.value = mutable.value.copy(selectedId = id, error = null)
        prefs.edit().putString("selected", id).apply()
    }
    fun status(playing: Boolean, buffering: Boolean, error: String? = null, recoveryMessage: String? = null) {
        mutable.value = mutable.value.copy(playing = playing, buffering = buffering, error = error, recoveryMessage = recoveryMessage)
    }
    fun add(name: String, input: String) {
        val url = validateUrl(input)
        require(name.trim().isNotEmpty() && name.trim().length <= 40) { "Adj meg 1–40 karakteres nevet." }
        require(mutable.value.stations.none { it.url == url }) { "Ez a stream már szerepel a listában." }
        require(mutable.value.stations.count { !it.builtIn } < 20) { "Legfeljebb 20 saját állomás menthető." }
        mutable.value = mutable.value.copy(stations = mutable.value.stations + RadioStation(UUID.randomUUID().toString(), name.trim(), url))
        persist()
    }
    fun remove(id: String) {
        val s = mutable.value
        require(!(s.selectedId == id && (s.playing || s.buffering))) { "Törlés előtt szüneteltesd a rádiót." }
        val stations = s.stations.filter { it.id != id || it.builtIn }
        mutable.value = s.copy(stations = stations, favorites = s.favorites.filterTo(mutableSetOf()) { favorite -> stations.any { it.id == favorite } }, selectedId = s.selectedId.takeIf { id -> stations.any { it.id == id } } ?: defaults.first().id)
        persist()
    }
    private fun persist() {
        val array = JSONArray()
        mutable.value.stations.filter { !it.builtIn }.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url))
        }
        prefs.edit().putString("stations", array.toString()).putString("selected", mutable.value.selectedId).putStringSet("favorites", mutable.value.favorites).apply()
    }
    companion object {
        fun matches(name: String, query: String): Boolean {
            fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
            return plain(name).contains(plain(query.trim()))
        }
        fun validateUrl(input: String): String {
            val clean = input.trim()
            val uri = runCatching { URI(clean) }.getOrNull()
            require(clean.length <= 2048 && uri != null && uri.scheme?.lowercase() in setOf("https", "http") &&
                !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
                "Közvetlen HTTP/HTTPS streamcímet adj meg, bejelentkezési adatok nélkül."
            }
            return clean
        }
    }
}

internal object RadioGraph {
    private var store: RadioStore? = null
    @Synchronized fun get(context: Context): RadioStore = store ?: RadioStore(context.applicationContext).also { store = it }
}
