package hu.elmdash.connection

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import hu.elmdash.elm.ElmSession
import hu.elmdash.elm.ElmTransport
import hu.elmdash.obd.*
import hu.elmdash.trip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

enum class Phase(val label: String) {
    STOPPED("Nincs kapcsolat"), CONNECTING("Csatlakozás…"), DISCOVERING("PID-ek keresése…"),
    LIVE("Élő adatok"), RETRY("Újracsatlakozás…"), DEMO("Demó • szimulált adatok"), ERROR("Kapcsolati hiba")
}
data class DashboardState(
    val phase: Phase = Phase.STOPPED, val telemetry: Telemetry = Telemetry(),
    val nowMs: Long = 0, val trip: TripSummary = TripSummary(),
    val today: LocalDate = LocalDate.now(), val daily: TripSummary = TripSummary(),
    val fuel: FuelReading = FuelReading(null, null, FuelSource.UNAVAILABLE),
    val fuelDisplay: FuelDisplay = FuelDisplay(),
    val driving: DrivingAdvice = DrivingAdvice(),
    val message: String = "Párosítsd az adaptert a telefon Bluetooth-beállításaiban.",
    val journal: JourneyLogState = JourneyLogState(),
    val adapterName: String = "ELM327", val simulated: Boolean = false, val lastTrip: TripSummary? = null
) {
    val active: Boolean get() = phase !in listOf(Phase.STOPPED, Phase.ERROR)
    val carUnavailable: Boolean get() = !simulated && phase != Phase.DEMO && phase != Phase.LIVE
}

class DashboardController(
    private val context: Context,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val calendarDate: () -> LocalDate = { LocalDate.now() },
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val transportFactory: ((Boolean, String, Boolean) -> ElmTransport)? = null
) {
    val store = SessionStore(context)
    private val journeyStore = JourneyStore(context)
    private val journeyLog = JourneyLog(journeyStore.load())
    private val journeyNotifications = JourneyNotifications(context)
    private var publishedJourneyIds = journeyLog.state.journeys.map { it.id }.toSet()
    private val realDaily = DailyComputer(store.days())
    private var demoDaily = DailyComputer()
    private val mutableState = MutableStateFlow(DashboardState(lastTrip = store.lastTrip(),
        today = calendarDate(), daily = realDaily.summary(calendarDate()), journal = journeyLog.state))
    val state: StateFlow<DashboardState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var session: ElmSession? = null
    private var computer = TripComputer()
    private val fuelWindow = FuelWindow()
    private val drivingAdvisor = DrivingAdvisor()
    private var demo = false
    private var settings = store.fuelSettings

    init {
        journeyStore.save(journeyLog.state) // Finalize any interrupted draft once, before accepting new samples.
        scope.launch {
            while (isActive) {
                delay(1_000)
                if (!demo && journeyLog.checkTimeout(clock(), wallClock())) {
                    saveJournal()
                    mutableState.update { it.copy(journal = journeyLog.state) }
                }
                val today = calendarDate()
                if (mutableState.value.today != today) mutableState.update {
                    it.copy(today = today, daily = dailyComputer().summary(today))
                }
            }
        }
    }

    private fun dailyComputer() = if (demo) demoDaily else realDaily
    private fun saveJournal() {
        journeyStore.save(journeyLog.state)
        val records = journeyLog.state.journeys
        records.filter { it.id !in publishedJourneyIds }.asReversed().forEach { journeyNotifications.show(it) }
        publishedJourneyIds = records.map { it.id }.toSet()
    }
    private fun saveReal() { if (!demo) { store.saveTrip(computer.summary); store.saveDays(realDaily.snapshot()); saveJournal() } }
    private fun gapComputers() { computer.gap(); dailyComputer().gap(); if (!demo) journeyLog.gap() }

    fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<Pair<String, String>> {
        if (!hasBluetoothPermission()) return emptyList()
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .map { it.address to (it.name ?: "Bluetooth adapter") }.sortedBy { it.second }
        }.getOrDefault(emptyList())
    }

    fun error(message: String) { stop(); mutableState.update { it.copy(phase = Phase.ERROR, message = message) } }

    fun startDemo() = launchConnection(true, "", false)
    fun startLive(address: String, insecure: Boolean, retryForever: Boolean = false) {
        if (!hasBluetoothPermission()) { error("Engedélyezd a Közeli eszközök hozzáférést a telefonon."); return }
        store.address = address
        store.insecure = insecure
        launchConnection(false, address, insecure, retryForever)
    }

    private fun launchConnection(isDemo: Boolean, address: String, insecure: Boolean, retryForever: Boolean = false) {
        stop()
        demo = isDemo
        computer = TripComputer()
        fuelWindow.reset()
        if (isDemo) demoDaily = DailyComputer()
        drivingAdvisor.reset()
        val name = if (isDemo) "ELM327 Simulator" else pairedDevices().firstOrNull { it.first == address }?.second ?: "ELM327"
        mutableState.value = DashboardState(phase = if (isDemo) Phase.DEMO else Phase.CONNECTING, adapterName = name, simulated = isDemo,
            today = calendarDate(), daily = dailyComputer().summary(calendarDate()), journal = journeyLog.state,
            message = if (isDemo) "Szimulált OBD-adatok, valódi feldolgozási lánccal." else "Bluetooth SPP kapcsolat felépítése…", lastTrip = store.lastTrip())
        job = scope.launch {
            val ticker = launch {
                var lastSaved = clock()
                while (isActive) {
                    val now = clock()
                    val current = mutableState.value
                    val fuel = FuelCalculator.calculate(current.telemetry, now, settings)
                    val speed = current.telemetry.value(Pid.SPEED, now)
                    val displayFuel = fuelWindow.update(fuel, speed, now)
                    val trip = computer.update(current.telemetry, fuel, now)
                    val today = calendarDate()
                    val daily = dailyComputer().update(today, current.telemetry, fuel, now)
                    if (!demo) {
                        journeyLog.update(current.telemetry, fuel, now, wallClock(), settings.profile.label)
                        if (journeyLog.state.journeys.firstOrNull()?.id !in publishedJourneyIds && journeyLog.state.journeys.isNotEmpty()) saveJournal()
                    }
                    val driving = drivingAdvisor.update(current.telemetry, now)
                    mutableState.update { it.copy(nowMs = now, fuel = fuel, trip = trip, today = today, daily = daily,
                        driving = driving, journal = journeyLog.state,
                        fuelDisplay = it.fuelDisplay.update(displayFuel, speed)) }
                    if (!demo && now - lastSaved >= 5_000) { saveReal(); lastSaved = now }
                    delay(250)
                }
            }
            var attempts = 0
            try {
                while (isActive) {
                    var liveSince = 0L
                    val currentSession = ElmSession(transportFactory?.invoke(isDemo, address, insecure) ?: if (isDemo) DemoTransport(clock) else {
                        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                            ?: throw IllegalStateException("A telefonon nincs Bluetooth")
                        BluetoothElmTransport(adapter, address, insecure)
                    })
                    session = currentSession
                    try {
                        currentSession.initialize()
                        mutableState.update { it.copy(phase = if (isDemo) Phase.DEMO else Phase.DISCOVERING) }
                        val repository = PidRepository(currentSession, clock)
                        val telemetry = repository.discover()
                        mutableState.update { it.copy(phase = if (isDemo) Phase.DEMO else Phase.LIVE,
                            telemetry = telemetry.withLastKnown(it.telemetry), nowMs = clock(),
                            message = if (isDemo) "Szimulált adatok • nincs szükség adapterre" else "ECU: ${telemetry.ecu ?: "automatikus"} • Bluetooth SPP") }
                        liveSince = clock()
                        while (isActive) {
                            val sample = repository.pollNext()
                            // Publish the sample and its clock together. Otherwise a new sample can
                            // appear to be from the future until the next 250 ms trip tick.
                            mutableState.update { it.copy(telemetry = sample.withLastKnown(it.telemetry), nowMs = clock()) }
                            if (clock() - liveSince >= 30_000) attempts = 0
                            delay(30)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        gapComputers()
                        drivingAdvisor.reset()
                        attempts++
                        mutableState.update { it.copy(telemetry = it.telemetry.clearValues(clock()),
                            fuel = FuelReading(null, null, FuelSource.UNAVAILABLE), fuelDisplay = it.fuelDisplay.copy(fresh = false),
                            nowMs = clock(), trip = computer.summary,
                            today = calendarDate(), daily = dailyComputer().summary(calendarDate()),
                            driving = DrivingAdvice(),
                            phase = if (retryForever || attempts <= 5) Phase.RETRY else Phase.ERROR,
                            message = if (retryForever) "A mentett OBD-adapter vagy az ECU nem érhető el. Automatikusan újrapróbálkozom." else "${e.message ?: "Kapcsolat megszakadt"}${if (attempts <= 5) " • próbálkozás $attempts/5" else " • Indítsd újra a kapcsolatot."}") }
                        if ((!retryForever && attempts > 5) || isDemo) break
                    } finally { currentSession.close(); if (session === currentSession) session = null }
                    delay(if (retryForever && attempts > 5) 30_000 else (1_000L shl (attempts - 1).coerceIn(0, 4)).coerceAtMost(15_000))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                gapComputers()
                drivingAdvisor.reset()
                mutableState.update { it.copy(phase = Phase.ERROR, message = e.message.orEmpty(),
                    telemetry = it.telemetry.clearValues(clock()), nowMs = clock(), trip = computer.summary,
                            today = calendarDate(), daily = dailyComputer().summary(calendarDate()),
                    driving = DrivingAdvice(),
                    fuel = FuelReading(null, null, FuelSource.UNAVAILABLE), fuelDisplay = it.fuelDisplay.copy(fresh = false)) }
            }
            finally {
                ticker.cancel()
                saveReal()
            }
        }
    }

    fun stopLiveOnly() { if (!demo) stop() }
    fun stop() {
        job?.cancel(); job = null
        session?.close(); session = null
        gapComputers()
        if (!demo) journeyLog.finish(JourneyEnd.STOPPED, wallClock())
        saveReal()
        drivingAdvisor.reset()
        val now = clock()
        mutableState.update { it.copy(phase = Phase.STOPPED, telemetry = it.telemetry.clearValues(now), nowMs = now,
            driving = DrivingAdvice(),
            fuel = FuelReading(null, null, FuelSource.UNAVAILABLE), fuelDisplay = it.fuelDisplay.copy(fresh = false),
            message = "A kapcsolat leállítva.", lastTrip = store.lastTrip(), journal = journeyLog.state,
            today = calendarDate(), daily = dailyComputer().summary(calendarDate())) }
    }

    fun resetTrip() {
        if (!demo) { journeyLog.finish(JourneyEnd.RESET, wallClock()); saveJournal() }
        computer.reset(); fuelWindow.reset()
        mutableState.update { it.copy(trip = computer.summary, journal = journeyLog.state) }
    }
    fun markFullTank(pumpedLiters: Double? = null, totalPaidHuf: Double? = null) {
        check(!demo) { "Demóban nem módosítható a valódi tank." }
        journeyLog.fullTank(wallClock(), pumpedLiters, totalPaidHuf)
        journeyStore.save(journeyLog.state)
        mutableState.update { it.copy(journal = journeyLog.state) }
    }
    fun saveSettings(value: FuelSettings) { settings = value; store.fuelSettings = value; resetTrip() }
}

object DashboardGraph {
    @Volatile private var controller: DashboardController? = null
    fun get(context: Context): DashboardController = controller ?: synchronized(this) {
        controller ?: DashboardController(context.applicationContext).also { controller = it }
    }
}
