package hu.elmdash.connection

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.widget.RemoteViews
import hu.elmdash.trip.*
import java.time.LocalDate
import java.util.Locale

class CarWidget : AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { update(context) }
    override fun onReceive(context:Context,intent:Intent) {
        super.onReceive(context,intent)
        if(intent.action==REFRESH || intent.action==AppWidgetManager.ACTION_APPWIDGET_UPDATE) refreshLocation(context)
    }
    @SuppressLint("MissingPermission")
    private fun refreshLocation(context:Context) {
        update(context)
        if(Build.VERSION.SDK_INT<30 || !allowed(context) || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
        val pending=goAsync();val signal=CancellationSignal();val handler=Handler(Looper.getMainLooper());var finished=false
        fun finish(location:Location?) {if(finished)return;finished=true;signal.cancel();update(context,location);pending.finish()}
        handler.postDelayed({finish(null)},8000)
        try {
            val manager=context.getSystemService(LocationManager::class.java)
            val provider=if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            manager.getCurrentLocation(provider,signal,context.mainExecutor) {finish(it)}
        } catch(_:Exception) {finish(null)}
    }
    companion object {
        private const val REFRESH="hu.elmdash.connection.REFRESH_WIDGET"
        private var lastUpdate=0L
        fun changed(context:Context,force:Boolean=false) {
            val now=SystemClock.elapsedRealtime()
            if(!force && now-lastUpdate<60_000)return
            lastUpdate=now;update(context)
        }
        private fun allowed(c:Context)=c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        @SuppressLint("MissingPermission")
        private fun phoneLocation(c:Context):Location? {
            if(!allowed(c))return null
            val m=c.getSystemService(LocationManager::class.java)
            return listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).mapNotNull {runCatching {m.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull {it.elapsedRealtimeNanos}
        }
        fun update(c:Context,fix:Location?=null) {
            val manager=AppWidgetManager.getInstance(c);val ids=manager.getAppWidgetIds(ComponentName(c,CarWidget::class.java));if(ids.isEmpty())return
            val journal=JourneyStore(c).load();val daily=SessionStore(c).days()[LocalDate.now()] ?: TripSummary()
            val range=RangeEstimate.calculate(journal.tank,daily,journal.journeys)
            val car=ParkingStore(c).load()?.takeIf {it.confirmed}
            val location=(fix ?: phoneLocation(c))?.takeIf {SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos in 0..600_000_000_000L && it.hasAccuracy() && it.accuracy<=200}
            val distance=if(car!=null && location!=null)location.distanceTo(car.location()).toDouble() else null
            val text=distance?.let {if(it<1000) "≈${(it/10).toInt()*10} m-re" else "≈${String.format(Locale.forLanguageTag("hu-HU"),"%.1f",it/1000)} km-re"}
                ?: if(car==null)"Nincs parkolóhely" else "Távolság: —"
            val status=when { car==null -> "Utolsó parkolás még nem ismert"
                !allowed(c) -> "Helyengedély szükséges • navigálás ↗"
                location==null -> "Nincs friss telefonhely • navigálás ↗"
                else -> "Utolsó parkolóhely • helymérés ${(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/60_000_000_000L} perce • ↗" }
            val launch=c.packageManager.getLaunchIntentForPackage(c.packageName) ?: return
            val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open=PendingIntent.getActivity(c,901,launch,flags)
            val fuelOpen=PendingIntent.getActivity(c,904,Intent(launch)
                .setAction("hu.elmdash.OPEN_FUEL")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),flags)
            val nav=car?.let {Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q=${it.latitude},${it.longitude}&mode=w")).setPackage("com.google.android.apps.maps")}
            val destination=if(nav!=null && nav.resolveActivity(c.packageManager)!=null)PendingIntent.getActivity(c,902,nav,flags)else open
            val views=RemoteViews(c.packageName,R.layout.car_widget)
            views.setTextViewText(R.id.widget_distance,text);views.setTextViewText(R.id.widget_status,status)
            views.setImageViewBitmap(R.id.widget_fuel,ring(journal.tank.percent,range))
            views.setContentDescription(R.id.widget_fuel,"Becsült benzinszint ${journal.tank.percent?.toInt() ?: "ismeretlen"} százalék. Hatótáv ${range.displayKm ?: "ismeretlen"} km. Üzemanyag oldal megnyitása.")
            views.setOnClickPendingIntent(R.id.widget_car,destination);views.setOnClickPendingIntent(R.id.widget_fuel,fuelOpen)
            views.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(c,903,Intent(c,CarWidget::class.java).setAction(REFRESH),flags))
            manager.updateAppWidget(ids,views)
        }
        internal fun ring(percent:Double?,range:RangeEstimate):Bitmap {
            val image=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);val canvas=Canvas(image)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=17f;strokeCap=Paint.Cap.ROUND;color=Color.rgb(48,64,76)}
            val rect=RectF(24f,24f,216f,216f);canvas.drawArc(rect,135f,270f,false,paint)
            paint.color=when(range.level){RangeLevel.CRITICAL->Color.rgb(235,77,83);RangeLevel.LOW->Color.rgb(245,170,65);RangeLevel.GOOD->Color.rgb(155,222,86);RangeLevel.NORMAL->Color.rgb(117,163,190);RangeLevel.UNKNOWN->Color.GRAY}
            if(percent!=null && percent>0)canvas.drawArc(rect,135f,(270*percent.coerceIn(0.0,100.0)/100).toFloat(),false,paint)
            paint.style=Paint.Style.FILL;paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=43f
            canvas.drawText(percent?.let {"≈${it.toInt()}%"} ?: "—",120f,117f,paint)
            paint.textSize=25f;paint.typeface=Typeface.DEFAULT;canvas.drawText(range.displayKm?.let {"≈$it km"} ?: "Tank",120f,151f,paint)
            paint.textSize=20f;paint.color=Color.LTGRAY;canvas.drawText(if(range.uncertain)"bizonytalan" else "becslés",120f,198f,paint)
            return image
        }
    }
}
