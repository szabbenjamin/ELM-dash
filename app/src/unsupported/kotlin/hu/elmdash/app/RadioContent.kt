package hu.elmdash.app

import androidx.compose.runtime.Composable

fun radioContent(): (@Composable () -> Unit)? = null

fun initializeAutoConnection(context: android.content.Context) { hu.elmdash.connection.AutoObd.install(context) }
