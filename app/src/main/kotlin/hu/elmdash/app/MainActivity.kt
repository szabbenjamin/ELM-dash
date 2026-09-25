package hu.elmdash.app

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { ElmDashboard(DashboardGraph.get(this), BuildConfig.FLAVOR, radioContent()) }
    }
}
