package hu.elmdash.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import hu.elmdash.trip.*
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun StatisticsPanel(log: JourneyLogState) {
    var expanded by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(StatisticsPeriod.DAY) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember { mutableIntStateOf(11) }
    val rows=remember(log,period,end) { JournalStatistics.buckets(log,period,end) }
    fun number(v:Double?)=v?.let { String.format(Locale.forLanguageTag("hu-HU"),"%.2f",it) } ?: "—"
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Statisztikák bezárása" else "Tankolási és közlekedési diagramok") }
        if(expanded) {
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { StatisticsPeriod.entries.forEach { p ->
            FilterChip(selected=period==p,onClick={period=p;selected=11},label={Text(p.label)})
        } }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={end=period.shift(end,-12);selected=11}) {Text("← Korábbi")}
            TextButton(onClick={end=LocalDate.now();selected=11}) {Text("Ma")}
            TextButton(enabled=period.start(end)<period.start(LocalDate.now()),onClick={end=minOf(period.shift(end,12),LocalDate.now());selected=11}) {Text("Újabb →")}
        }
        Text("${rows.first().start} – ${period.shift(rows.last().start,1).minusDays(1)}",style=MaterialTheme.typography.bodySmall)
        Text("Érints meg egy oszlopot. Kijelölve: ${rows[selected].start} • ${rows[selected].trips} út • ${rows[selected].refills} tankolás")
        Slider(value=selected.toFloat(),onValueChange={selected=it.toInt().coerceIn(rows.indices)},valueRange=0f..11f,steps=10)
        val metrics=listOf<Pair<String,(StatisticsBucket)->Double?>>("Megtett út · km" to {it.distanceKm},
            "Elfogyasztott benzin · l" to {it.consumedLiters},"Átlagfogyasztás · l/100 km" to {it.averageL100},
            "Tankolt benzin · l" to {it.pumpedLiters},"Tankolás költsége · Ft" to {it.paidHuf})
        metrics.forEach { (title,get) ->
            val values=rows.map(get);val max=values.filterNotNull().maxOrNull()?.coerceAtLeast(0.01) ?: 1.0
            val color=MaterialTheme.colorScheme.primary;val muted=MaterialTheme.colorScheme.outline
            Text("$title: ${number(values[selected])}",style=MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(90.dp).pointerInput(rows) {
                detectTapGestures { selected=(it.x/size.width*rows.size).toInt().coerceIn(rows.indices) }
            }) {
                val step=size.width/rows.size
                drawLine(muted,Offset(0f,size.height),Offset(size.width,size.height))
                values.forEachIndexed { i,v ->
                    if(v!=null) {
                        val height=(v/max*(size.height-6)).toFloat().coerceAtLeast(2f)
                        drawRect(color.copy(alpha=if(i==selected)1f else .45f),Offset(i*step+3,size.height-height),Size((step-6).coerceAtLeast(1f),height))
                    } else drawCircle(muted,2f,Offset(i*step+step/2,size.height-2))
                }
            }
        }
        Text(if(rows[selected].incomplete) "A kijelölt időszak részleges / becsült adatokat tartalmaz." else "Csak a rögzített adatok szerepelnek.",style=MaterialTheme.typography.bodySmall)
        Text("Az utak az indulás helyi napjához tartoznak, a hét hétfőn kezdődik. Az átlag az együtt mért liter / kilométer arányából készül. A pont (—) adathiány, nem nulla. Az összesítés a megőrzött 200 út és 100 tankolás alapján készül; régebbi törölt adatok nem állíthatók vissza.",style=MaterialTheme.typography.bodySmall)
        }
    } }
}
