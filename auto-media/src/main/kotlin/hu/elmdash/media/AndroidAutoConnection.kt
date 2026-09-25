package hu.elmdash.media

import android.content.Context
import androidx.car.app.connection.CarConnection
import hu.elmdash.connection.AutoObd
import hu.elmdash.connection.SessionStore

/** Official connection API, independent of whether our radio is the selected media source. */
object AndroidAutoConnection {
    private var connection: CarConnection? = null
    fun install(context: Context) {
        if (connection != null) return
        val app = context.applicationContext
        AutoObd.install(app)
        SessionStore(app).enableAutoByDefaultOnce()
        connection = CarConnection(app).also { car ->
            car.type.observeForever {
                val connected = it == CarConnection.CONNECTION_TYPE_PROJECTION
                AutoObd.projectionChanged(connected)
                AutoRadio.connectionChanged(app, connected)
            }
        }
    }
}
