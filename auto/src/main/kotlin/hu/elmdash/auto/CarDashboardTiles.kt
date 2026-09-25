package hu.elmdash.auto

import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import hu.elmdash.connection.DashboardState
import hu.elmdash.graphics.DashboardTiles

internal object CarDashboardTiles {
    fun create(s: DashboardState) = DashboardTiles.create(s)
    fun overview(s: DashboardState): CarIcon =
        CarIcon.Builder(IconCompat.createWithBitmap(DashboardTiles.overview(s))).build()
    fun indicator(s: DashboardState, index: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithBitmap(DashboardTiles.indicator(s, index))).build()
}
