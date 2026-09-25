package hu.elmdash.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Watch the default internet route, not AA's local Wi-Fi link. Callbacks may arrive on a binder thread. */
internal class RadioNetwork(context: Context, private val changed: (Boolean) -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var observed: Network? = null
    private var registered = false
    val online: Boolean get() = runCatching { valid(manager.getNetworkCapabilities(manager.activeNetwork)) }.getOrDefault(false)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { observed = network } // Wait for capabilities, not a racy synchronous query.
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (observed == network) changed(valid(caps))
        }
        override fun onLost(network: Network) {
            if (observed == network) { observed = null; changed(false) }
        }
    }
    fun start() { manager.registerDefaultNetworkCallback(callback); registered = true }
    fun close() { if (registered) { manager.unregisterNetworkCallback(callback); registered = false } }
    private fun valid(caps: NetworkCapabilities?) = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
