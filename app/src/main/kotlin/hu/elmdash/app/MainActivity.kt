package hu.elmdash.app

import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import hu.elmdash.connection.DashboardGraph
import hu.elmdash.dashboard.ElmDashboard

class ElmApplication : Application() {
    override fun onCreate() { super.onCreate(); DashboardGraph.get(this); initializeAutoConnection(this) }
}

class MainActivity : ComponentActivity() {
    private val fuelRequest = mutableIntStateOf(0)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "hu.elmdash.OPEN_FUEL") fuelRequest.intValue++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == "hu.elmdash.OPEN_FUEL") fuelRequest.intValue++
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { ElmDashboard(DashboardGraph.get(this), BuildConfig.FLAVOR, radioContent(), openFuelRequest = fuelRequest.intValue, parkingPreview = BuildConfig.DEBUG && intent.getBooleanExtra("parking_preview", false)) }
    }
}
