package com.ugf.yiffur1.domain.perf

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.ugf.yiffur1.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
enum class PerformanceMode(val label: String, val subtitle: String) {
    ADAPTIVE_AUTO(
        label = "Adaptive (Auto)",
        subtitle = "Dynamically adjusts decode size, caching & video streams when device heats up, slows down, or runs low on RAM"
    ),
    MAX_QUALITY(
        label = "Max Quality",
        subtitle = "Sharpest thumbnails & immediate HD tiles; still protects against unsupported software video codecs & OOM crashes"
    ),
    BALANCED(
        label = "Balanced",
        subtitle = "Crisp visuals with RGB_565 thumbnails, bounded threads & steady battery efficiency"
    ),
    ECO_LEGACY(
        label = "Eco / Legacy",
        subtitle = "Lightweight 256px WebP previews, compact video buffers & low-RAM budgets built for older devices"
    )
}

enum class PerformanceTier(val rank: Int, val label: String) {
    ULTRA(0, "Ultra"),
    BALANCED(1, "Balanced"),
    POWER_SAVER(2, "Power Saver"),
    SURVIVAL(3, "Survival");

    companion object {
        fun fromRank(rank: Int): PerformanceTier =
            entries.firstOrNull { it.rank == rank.coerceIn(0, SURVIVAL.rank) } ?: BALANCED
    }
}

enum class ThermalPressure(val label: String) {
    NOMINAL("Cool"),
    LIGHT("Warm"),
    MODERATE("Hot"),
    SEVERE("Overheating"),
    CRITICAL("Critical")
}

enum class MemoryPressure(val label: String) {
    NORMAL("Healthy"),
    MODERATE("Tight"),
    HIGH("Low RAM"),
    CRITICAL("Critical RAM")
}

enum class JankPressure(val label: String) {
    SMOOTH("Smooth"),
    MILD("Busy"),
    HEAVY("Struggling")
}

data class DeviceHardwareProfile(
    val totalRamMb: Int,
    val memoryClassMb: Int,
    val cpuCores: Int,
    val apiLevel: Int,
    val isLowRamDevice: Boolean,
    val hasHwAv1: Boolean,
    val hasHwVp9: Boolean,
    val hasHwH264: Boolean
) {
    val baselineTier: PerformanceTier
        get() = when {
            isLowRamDevice || totalRamMb in 1..2560 || cpuCores <= 4 || apiLevel < 26 -> PerformanceTier.POWER_SAVER
            totalRamMb >= 5600 && cpuCores >= 6 && apiLevel >= 29 -> PerformanceTier.ULTRA
            else -> PerformanceTier.BALANCED
        }

    val initialDecodeThreads: Int
        get() = when {
            isLowRamDevice || cpuCores <= 4 || totalRamMb in 1..3072 -> 2
            cpuCores >= 8 && totalRamMb >= 6144 -> 4
            else -> 3
        }

    val memoryCachePercent: Double
        get() = when {
            isLowRamDevice || totalRamMb in 1..2560 -> 0.12
            totalRamMb in 2561..4608 -> 0.15
            else -> 0.18
        }

    val imageDiskCacheBytes: Long
        get() = if (isLowRamDevice || totalRamMb in 1..2560) {
            150L * 1024L * 1024L
        } else {
            300L * 1024L * 1024L
        }

    val videoDiskCacheBytes: Long
        get() = if (isLowRamDevice || totalRamMb in 1..2560) {
            80L * 1024L * 1024L
        } else {
            160L * 1024L * 1024L
        }

    val supportedHwCodecSummary: String
        get() = buildList {
            if (hasHwH264) add("H.264")
            if (hasHwVp9) add("VP9")
            if (hasHwAv1) add("AV1")
        }.ifEmpty { listOf("SW Fallback") }.joinToString(" · ")
}

data class AdaptiveBudget(
    val tier: PerformanceTier,
    val maxThumbnailPx: Int,
    val preferSampleThumbnails: Boolean,
    val useRgb565Thumbnails: Boolean,
    val enableCrossfade: Boolean,
    val prefetchCount: Int,
    val pagerOffscreenPages: Int,
    val autoUpgradeZoomToOriginal: Boolean,
    val allowImmediateHdFullscreen: Boolean,
    val maxGifDecodePx: Int,
    val tapToPlayAnimatedMedia: Boolean,
    val recommendedVideoMaxHeight: Int,
    val exoPlayerMinBufferMs: Int,
    val exoPlayerMaxBufferMs: Int,
    val exoPlayerBufferForPlaybackMs: Int
) {
    companion object {
        fun forTier(tier: PerformanceTier, conserveBandwidth: Boolean): AdaptiveBudget {
            val base = when (tier) {
                PerformanceTier.ULTRA -> AdaptiveBudget(
                    tier = tier,
                    maxThumbnailPx = 720,
                    preferSampleThumbnails = true,
                    useRgb565Thumbnails = false,
                    enableCrossfade = true,
                    prefetchCount = 10,
                    pagerOffscreenPages = 1,
                    autoUpgradeZoomToOriginal = true,
                    allowImmediateHdFullscreen = true,
                    maxGifDecodePx = 1080,
                    tapToPlayAnimatedMedia = false,
                    recommendedVideoMaxHeight = 2160,
                    exoPlayerMinBufferMs = 5_000,
                    exoPlayerMaxBufferMs = 25_000,
                    exoPlayerBufferForPlaybackMs = 1_000
                )

                PerformanceTier.BALANCED -> AdaptiveBudget(
                    tier = tier,
                    maxThumbnailPx = 512,
                    preferSampleThumbnails = false,
                    useRgb565Thumbnails = true,
                    enableCrossfade = true,
                    prefetchCount = 6,
                    pagerOffscreenPages = 1,
                    autoUpgradeZoomToOriginal = true,
                    allowImmediateHdFullscreen = true,
                    maxGifDecodePx = 720,
                    tapToPlayAnimatedMedia = false,
                    recommendedVideoMaxHeight = 1080,
                    exoPlayerMinBufferMs = 3_500,
                    exoPlayerMaxBufferMs = 15_000,
                    exoPlayerBufferForPlaybackMs = 1_000
                )

                PerformanceTier.POWER_SAVER -> AdaptiveBudget(
                    tier = tier,
                    maxThumbnailPx = 360,
                    preferSampleThumbnails = false,
                    useRgb565Thumbnails = true,
                    enableCrossfade = false,
                    prefetchCount = 2,
                    pagerOffscreenPages = 0,
                    autoUpgradeZoomToOriginal = false,
                    allowImmediateHdFullscreen = false,
                    maxGifDecodePx = 480,
                    tapToPlayAnimatedMedia = false,
                    recommendedVideoMaxHeight = 720,
                    exoPlayerMinBufferMs = 2_000,
                    exoPlayerMaxBufferMs = 8_000,
                    exoPlayerBufferForPlaybackMs = 750
                )

                PerformanceTier.SURVIVAL -> AdaptiveBudget(
                    tier = tier,
                    maxThumbnailPx = 256,
                    preferSampleThumbnails = false,
                    useRgb565Thumbnails = true,
                    enableCrossfade = false,
                    prefetchCount = 0,
                    pagerOffscreenPages = 0,
                    autoUpgradeZoomToOriginal = false,
                    allowImmediateHdFullscreen = false,
                    maxGifDecodePx = 360,
                    tapToPlayAnimatedMedia = true,
                    recommendedVideoMaxHeight = 480,
                    exoPlayerMinBufferMs = 1_500,
                    exoPlayerMaxBufferMs = 5_000,
                    exoPlayerBufferForPlaybackMs = 500
                )
            }

            return if (conserveBandwidth) {
                base.copy(
                    preferSampleThumbnails = false,
                    prefetchCount = (base.prefetchCount / 2).coerceAtMost(2),
                    allowImmediateHdFullscreen = false,
                    recommendedVideoMaxHeight = base.recommendedVideoMaxHeight.coerceAtMost(720)
                )
            } else {
                base
            }
        }
    }
}

data class AdaptivePerformanceState(
    val mode: PerformanceMode = PerformanceMode.ADAPTIVE_AUTO,
    val effectiveTier: PerformanceTier = PerformanceTier.BALANCED,
    val budget: AdaptiveBudget = AdaptiveBudget.forTier(PerformanceTier.BALANCED, false),
    val hardware: DeviceHardwareProfile = DeviceHardwareProfile(
        totalRamMb = 4096,
        memoryClassMb = 256,
        cpuCores = 6,
        apiLevel = Build.VERSION.SDK_INT,
        isLowRamDevice = false,
        hasHwAv1 = false,
        hasHwVp9 = true,
        hasHwH264 = true
    ),
    val thermal: ThermalPressure = ThermalPressure.NOMINAL,
    val memory: MemoryPressure = MemoryPressure.NORMAL,
    val jank: JankPressure = JankPressure.SMOOTH,
    val isPowerSaveMode: Boolean = false,
    val isMeteredNetwork: Boolean = false,
    val dataSaverActive: Boolean = false,
    val smoothnessPercent: Int = 100,
    val activeReason: String? = null
) {
    val isAutoThrottled: Boolean
        get() = mode == PerformanceMode.ADAPTIVE_AUTO && effectiveTier.rank > hardware.baselineTier.rank

    fun toUiBudget(): PerformanceUiBudget = PerformanceUiBudget(
        effectiveTier = effectiveTier,
        budget = budget,
        hardware = hardware,
        isAutoThrottled = isAutoThrottled,
        activeReason = activeReason
    )
}

/**
 * Stable UI-facing performance snapshot excluding fast-changing telemetry (such as `smoothnessPercent`)
 * so feeds and media viewers ONLY recompose when the active tier, budget, or throttle reason changes.
 */
data class PerformanceUiBudget(
    val effectiveTier: PerformanceTier = PerformanceTier.BALANCED,
    val budget: AdaptiveBudget = AdaptiveBudget.forTier(PerformanceTier.BALANCED, false),
    val hardware: DeviceHardwareProfile = DeviceHardwareProfile(
        totalRamMb = 4096,
        memoryClassMb = 256,
        cpuCores = 6,
        apiLevel = Build.VERSION.SDK_INT,
        isLowRamDevice = false,
        hasHwAv1 = false,
        hasHwVp9 = true,
        hasHwH264 = true
    ),
    val isAutoThrottled: Boolean = false,
    val activeReason: String? = null
)

/**
 * Pure, deterministic tier solver that computes the target [PerformanceTier] and user-facing
 * adaptation reason from hardware capabilities and live pressure signals.
 */
object AdaptiveTierCalculator {

    data class Decision(
        val tier: PerformanceTier,
        val reason: String?
    )

    fun compute(
        mode: PerformanceMode,
        hardware: DeviceHardwareProfile,
        thermal: ThermalPressure,
        memory: MemoryPressure,
        jank: JankPressure,
        isPowerSaveMode: Boolean,
        isMeteredDataSaver: Boolean
    ): Decision {
        return when (mode) {
            PerformanceMode.MAX_QUALITY -> {
                // Even in Max Quality, guard against imminent OOM or critical overheating
                when {
                    memory == MemoryPressure.CRITICAL -> Decision(PerformanceTier.POWER_SAVER, "Emergency RAM protection")
                    thermal == ThermalPressure.CRITICAL -> Decision(PerformanceTier.POWER_SAVER, "Emergency thermal protection")
                    memory == MemoryPressure.HIGH -> Decision(PerformanceTier.BALANCED, "Low RAM protection")
                    else -> Decision(PerformanceTier.ULTRA, null)
                }
            }

            PerformanceMode.BALANCED -> {
                when {
                    memory == MemoryPressure.CRITICAL || thermal == ThermalPressure.CRITICAL ->
                        Decision(PerformanceTier.SURVIVAL, "Critical device load")
                    memory == MemoryPressure.HIGH || thermal == ThermalPressure.SEVERE ->
                        Decision(PerformanceTier.POWER_SAVER, "Stepped down to keep app smooth")
                    else -> Decision(PerformanceTier.BALANCED, if (isMeteredDataSaver) "Metered data saver" else null)
                }
            }

            PerformanceMode.ECO_LEGACY -> {
                val tier = if (
                    memory == MemoryPressure.CRITICAL ||
                    thermal == ThermalPressure.SEVERE ||
                    thermal == ThermalPressure.CRITICAL ||
                    jank == JankPressure.HEAVY
                ) {
                    PerformanceTier.SURVIVAL
                } else {
                    PerformanceTier.POWER_SAVER
                }
                Decision(tier, "Eco / Legacy mode")
            }

            PerformanceMode.ADAPTIVE_AUTO -> {
                var rank = hardware.baselineTier.rank
                var reason: String? = null

                fun raiseTo(targetRank: Int, cause: String) {
                    if (targetRank > rank) {
                        rank = targetRank
                        reason = cause
                    }
                }

                // Battery saver caps to Power Saver
                if (isPowerSaveMode) {
                    raiseTo(PerformanceTier.POWER_SAVER.rank, "Battery Saver active")
                }

                // Thermal pressure
                when (thermal) {
                    ThermalPressure.NOMINAL -> Unit
                    ThermalPressure.LIGHT -> raiseTo(max(rank, PerformanceTier.BALANCED.rank), "Device warming up")
                    ThermalPressure.MODERATE -> raiseTo(PerformanceTier.POWER_SAVER.rank, "Thermal throttling")
                    ThermalPressure.SEVERE, ThermalPressure.CRITICAL ->
                        raiseTo(PerformanceTier.SURVIVAL.rank, "High thermal load")
                }

                // Memory trim pressure
                when (memory) {
                    MemoryPressure.NORMAL -> Unit
                    MemoryPressure.MODERATE -> raiseTo(max(rank, PerformanceTier.BALANCED.rank), "Conserving RAM")
                    MemoryPressure.HIGH -> raiseTo(PerformanceTier.POWER_SAVER.rank, "Low device memory")
                    MemoryPressure.CRITICAL -> raiseTo(PerformanceTier.SURVIVAL.rank, "Critical memory pressure")
                }

                // Live UI frame-jank pressure
                when (jank) {
                    JankPressure.SMOOTH -> Unit
                    JankPressure.MILD -> raiseTo(
                        (hardware.baselineTier.rank + 1).coerceAtMost(PerformanceTier.POWER_SAVER.rank),
                        "High frame load"
                    )
                    JankPressure.HEAVY -> raiseTo(
                        (hardware.baselineTier.rank + 2).coerceAtMost(PerformanceTier.SURVIVAL.rank),
                        "Heavy CPU / frame load"
                    )
                }

                val computedTier = PerformanceTier.fromRank(rank)
                val finalReason = reason ?: when {
                    isMeteredDataSaver -> "Metered data saver"
                    hardware.baselineTier == PerformanceTier.POWER_SAVER -> "Tuned for compact hardware"
                    else -> null
                }
                Decision(computedTier, finalReason)
            }
        }
    }
}

/**
 * Inspects Android device RAM, CPU cores, API level, and hardware video codecs (AV1/VP9/H.264)
 * once at startup with zero main-thread blocking.
 */
object DeviceHardwareProbe {

    fun detect(context: Context): DeviceHardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        val totalRamMb = runCatching {
            am?.getMemoryInfo(memInfo)
            (memInfo.totalMem / (1024L * 1024L)).toInt()
        }.getOrDefault(4096)

        val memoryClassMb = runCatching { am?.memoryClass ?: 256 }.getOrDefault(256)
        val isLowRam = runCatching { am?.isLowRamDevice ?: false }.getOrDefault(false)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val codecs = probeHardwareVideoDecoders()

        return DeviceHardwareProfile(
            totalRamMb = totalRamMb,
            memoryClassMb = memoryClassMb,
            cpuCores = cores,
            apiLevel = Build.VERSION.SDK_INT,
            isLowRamDevice = isLowRam,
            hasHwAv1 = codecs.hasAv1,
            hasHwVp9 = codecs.hasVp9,
            hasHwH264 = codecs.hasH264
        )
    }

    private data class CodecSupport(
        val hasAv1: Boolean,
        val hasVp9: Boolean,
        val hasH264: Boolean
    )

    private fun probeHardwareVideoDecoders(): CodecSupport {
        var av1 = false
        var vp9 = false
        var h264 = false

        runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated && !info.isSoftwareOnly
                } else {
                    val lower = info.name.lowercase()
                    !lower.startsWith("omx.google.") &&
                        !lower.startsWith("c2.android.") &&
                        !lower.startsWith("omx.ffmpeg.") &&
                        !lower.startsWith("c2.ffmpeg.") &&
                        !lower.contains(".sw.") &&
                        !lower.contains("dav1d") &&
                        !lower.contains("libgav1")
                }
                if (!isHardware) continue

                for (type in info.supportedTypes) {
                    when (type.lowercase()) {
                        "video/av01" -> av1 = true
                        "video/x-vnd.on2.vp9" -> vp9 = true
                        "video/avc" -> h264 = true
                    }
                }
            }
        }

        return CodecSupport(hasAv1 = av1, hasVp9 = vp9, hasH264 = h264)
    }
}

/**
 * Live runtime engine monitoring thermal status, memory trim signals, Android Battery Saver,
 * metered network state, and rolling UI frame timings (FrameMetrics) with anti-oscillation
 * hysteresis so Yiffur silently adapts before the user feels stutter or OOM crashes.
 */
class AdaptivePerformanceEngine(
    context: Context,
    settingsRepository: SettingsRepository,
    val hardwareProfile: DeviceHardwareProfile = DeviceHardwareProbe.detect(context)
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val lock = Any()
    private var currentMode: PerformanceMode = PerformanceMode.ADAPTIVE_AUTO
    private var dataSaverOnMetered: Boolean = true
    private var thermalPressure: ThermalPressure = ThermalPressure.NOMINAL
    private var memoryPressure: MemoryPressure = MemoryPressure.NORMAL
    private var jankPressure: JankPressure = JankPressure.SMOOTH
    private var isPowerSaveMode: Boolean = false
    private var isMeteredNetwork: Boolean = false
    private var smoothnessPercent: Int = 100

    // Hysteresis timestamps (elapsedRealtime ms)
    private var lastStepDownAtMs: Long = 0L
    private var currentEffectiveTier: PerformanceTier = hardwareProfile.baselineTier

    // Rolling ring buffer of recent frame jank flags (true = janky frame)
    private val frameWindow = BooleanArray(FRAME_WINDOW_SIZE)
    private var frameCursor = 0
    private var frameSamples = 0
    private var jankyCount = 0

    private var memoryDecayJob: Job? = null
    private var recoveryJob: Job? = null

    private val _state = MutableStateFlow(
        AdaptivePerformanceState(
            mode = currentMode,
            effectiveTier = hardwareProfile.baselineTier,
            budget = AdaptiveBudget.forTier(hardwareProfile.baselineTier, false),
            hardware = hardwareProfile
        )
    )
    val state: StateFlow<AdaptivePerformanceState> = _state.asStateFlow()

    private val _budgetState = MutableStateFlow(_state.value.toUiBudget())
    val budgetState: StateFlow<PerformanceUiBudget> = _budgetState.asStateFlow()

    init {
        attachSystemObservers()
        scope.launch {
            settingsRepository.settingsFlow
                .map { it.performanceMode to it.dataSaverOnMetered }
                .distinctUntilChanged()
                .collect { (mode, dataSaver) ->
                    synchronized(lock) {
                        val modeChanged = currentMode != mode
                        currentMode = mode
                        dataSaverOnMetered = dataSaver
                        recomputeLocked(bypassHysteresis = modeChanged)
                    }
                }
        }
    }

    private fun attachSystemObservers() {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null) {
            synchronized(lock) {
                isPowerSaveMode = runCatching { powerManager.isPowerSaveMode }.getOrDefault(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    thermalPressure = mapThermalStatus(
                        runCatching { powerManager.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
                    )
                }
                recomputeLocked(bypassHysteresis = true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    powerManager.addThermalStatusListener { status ->
                        synchronized(lock) {
                            thermalPressure = mapThermalStatus(status)
                            recomputeLocked(bypassHysteresis = false)
                        }
                    }
                }
            }
        }

        // Observe Battery Saver toggles
        runCatching {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                    synchronized(lock) {
                        isPowerSaveMode = pm.isPowerSaveMode
                        recomputeLocked(bypassHysteresis = false)
                    }
                }
            }
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
            }
        }

        // Observe metered vs unmetered network (cellular / metered Wi-Fi)
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            synchronized(lock) {
                isMeteredNetwork = runCatching { cm.isActiveNetworkMetered }.getOrDefault(false)
                recomputeLocked(bypassHysteresis = true)
            }
            runCatching {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        synchronized(lock) {
                            if (isMeteredNetwork != metered) {
                                isMeteredNetwork = metered
                                recomputeLocked(bypassHysteresis = true)
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        val metered = runCatching { cm.isActiveNetworkMetered }.getOrDefault(false)
                        synchronized(lock) {
                            if (isMeteredNetwork != metered) {
                                isMeteredNetwork = metered
                                recomputeLocked(bypassHysteresis = true)
                            }
                        }
                    }
                })
            }
        }
    }

    /**
     * Called from [android.app.Application.onTrimMemory] and [android.app.Application.onLowMemory].
     */
    @Suppress("DEPRECATION")
    fun onMemoryTrim(level: Int) {
        val nextPressure = when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryPressure.CRITICAL
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> MemoryPressure.HIGH
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryPressure.MODERATE
            else -> return
        }

        synchronized(lock) {
            if (nextPressure.ordinal >= memoryPressure.ordinal) {
                memoryPressure = nextPressure
                recomputeLocked(bypassHysteresis = false)
            }
        }
        scheduleMemoryPressureDecay()
    }

    fun onLowMemory() {
        synchronized(lock) {
            memoryPressure = MemoryPressure.CRITICAL
            recomputeLocked(bypassHysteresis = false)
        }
        scheduleMemoryPressureDecay()
    }

    /**
     * Records a UI frame total duration (nanoseconds) from [android.view.Window.OnFrameMetricsAvailableListener].
     * Runs on a dedicated background HandlerThread — zero UI thread cost.
     */
    fun recordFrameDuration(frameDurationNs: Long, refreshRateHz: Float) {
        if (frameDurationNs <= 0L) return
        val safeHz = refreshRateHz.coerceIn(30f, 240f)
        val frameBudgetNs = (1_000_000_000.0 / safeHz).toLong()
        // Count a frame as janky only if it exceeds 1.5x frame budget (e.g. >25ms at 60Hz)
        val isJanky = frameDurationNs > (frameBudgetNs * 3L) / 2L

        synchronized(lock) {
            if (frameSamples == FRAME_WINDOW_SIZE && frameWindow[frameCursor]) {
                jankyCount--
            }
            frameWindow[frameCursor] = isJanky
            if (isJanky) jankyCount++
            frameCursor = (frameCursor + 1) % FRAME_WINDOW_SIZE
            if (frameSamples < FRAME_WINDOW_SIZE) frameSamples++

            val smooth = if (frameSamples > 0) {
                (((frameSamples - jankyCount).toFloat() / frameSamples.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            } else {
                100
            }

            val nextJank = if (frameSamples < MIN_JANK_SAMPLES) {
                JankPressure.SMOOTH
            } else {
                val jankRatio = jankyCount.toFloat() / frameSamples.toFloat()
                when {
                    jankRatio >= 0.52f -> JankPressure.HEAVY
                    jankRatio >= 0.30f -> JankPressure.MILD
                    jankRatio <= 0.14f -> JankPressure.SMOOTH
                    else -> jankPressure // deadband prevents chatter
                }
            }

            val smoothnessDelta = kotlin.math.abs(smooth - smoothnessPercent)
            if (nextJank != jankPressure || smoothnessDelta >= 5) {
                jankPressure = nextJank
                smoothnessPercent = smooth
                recomputeLocked(bypassHysteresis = false)
            }
        }
    }

    private fun scheduleMemoryPressureDecay() {
        memoryDecayJob?.cancel()
        memoryDecayJob = scope.launch {
            delay(MEMORY_DECAY_MS)
            synchronized(lock) {
                memoryPressure = when (memoryPressure) {
                    MemoryPressure.CRITICAL -> MemoryPressure.HIGH
                    MemoryPressure.HIGH -> MemoryPressure.MODERATE
                    MemoryPressure.MODERATE -> MemoryPressure.NORMAL
                    MemoryPressure.NORMAL -> MemoryPressure.NORMAL
                }
                recomputeLocked(bypassHysteresis = false)
            }
            if (memoryPressure != MemoryPressure.NORMAL) {
                scheduleMemoryPressureDecay()
            }
        }
    }

    private fun scheduleRecoveryCheck(remainingMs: Long) {
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(remainingMs.coerceAtLeast(1_000L))
            synchronized(lock) {
                recomputeLocked(bypassHysteresis = false)
            }
        }
    }

    private fun recomputeLocked(bypassHysteresis: Boolean) {
        val conserveBandwidth = isMeteredNetwork && dataSaverOnMetered
        val decision = AdaptiveTierCalculator.compute(
            mode = currentMode,
            hardware = hardwareProfile,
            thermal = thermalPressure,
            memory = memoryPressure,
            jank = jankPressure,
            isPowerSaveMode = isPowerSaveMode,
            isMeteredDataSaver = conserveBandwidth
        )

        val now = SystemClock.elapsedRealtime()
        val chosenTier = when {
            bypassHysteresis -> {
                currentEffectiveTier = decision.tier
                decision.tier
            }
            decision.tier.rank > currentEffectiveTier.rank -> {
                // Step down IMMEDIATELY when the device begins struggling
                lastStepDownAtMs = now
                currentEffectiveTier = decision.tier
                decision.tier
            }
            decision.tier.rank < currentEffectiveTier.rank -> {
                // Step back up ONLY after recovery cooldown so quality doesn't ping-pong
                val elapsed = now - lastStepDownAtMs
                if (elapsed >= RECOVERY_COOLDOWN_MS) {
                    // Recover gracefully one tier at a time
                    val nextUp = PerformanceTier.fromRank(currentEffectiveTier.rank - 1)
                    currentEffectiveTier = if (nextUp.rank < decision.tier.rank) decision.tier else nextUp
                    if (currentEffectiveTier.rank > decision.tier.rank) {
                        lastStepDownAtMs = now
                        scheduleRecoveryCheck(RECOVERY_COOLDOWN_MS)
                    }
                    currentEffectiveTier
                } else {
                    scheduleRecoveryCheck(RECOVERY_COOLDOWN_MS - elapsed)
                    currentEffectiveTier
                }
            }
            else -> currentEffectiveTier
        }

        val reason = if (chosenTier.rank > decision.tier.rank) {
            "Stabilizing after high load"
        } else {
            decision.reason
        }

        val nextFullState = AdaptivePerformanceState(
            mode = currentMode,
            effectiveTier = chosenTier,
            budget = AdaptiveBudget.forTier(chosenTier, conserveBandwidth),
            hardware = hardwareProfile,
            thermal = thermalPressure,
            memory = memoryPressure,
            jank = jankPressure,
            isPowerSaveMode = isPowerSaveMode,
            isMeteredNetwork = isMeteredNetwork,
            dataSaverActive = conserveBandwidth,
            smoothnessPercent = smoothnessPercent,
            activeReason = reason
        )
        _state.value = nextFullState
        val nextUiBudget = nextFullState.toUiBudget()
        if (_budgetState.value != nextUiBudget) {
            _budgetState.value = nextUiBudget
        }
    }

    private fun mapThermalStatus(status: Int): ThermalPressure = when {
        status >= PowerManager.THERMAL_STATUS_CRITICAL -> ThermalPressure.CRITICAL
        status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalPressure.SEVERE
        status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalPressure.MODERATE
        status >= PowerManager.THERMAL_STATUS_LIGHT -> ThermalPressure.LIGHT
        else -> ThermalPressure.NOMINAL
    }

    companion object {
        private const val FRAME_WINDOW_SIZE = 45
        private const val MIN_JANK_SAMPLES = 15
        private const val RECOVERY_COOLDOWN_MS = 20_000L
        private const val MEMORY_DECAY_MS = 35_000L
    }
}
