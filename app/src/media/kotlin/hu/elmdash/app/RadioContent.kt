package hu.elmdash.app

import androidx.compose.runtime.Composable
import hu.elmdash.media.RadioPanel

fun radioContent(): (@Composable () -> Unit)? = { RadioPanel() }

fun initializeAutoConnection(context: android.content.Context) { hu.elmdash.media.AndroidAutoConnection.install(context) }
