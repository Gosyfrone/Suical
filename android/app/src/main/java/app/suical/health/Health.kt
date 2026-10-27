package app.suical.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Lecture dans Health Connect. La montre (via Zepp, Google Fit, Samsung Health…) y écrit ; Suical ne fait que lire,
 * et seulement les types déclarés dans le manifeste : pas, poids, et l'accès à l'historique pour l'import.
 */
object Health {
    private const val PROVIDER = "com.google.android.apps.healthdata"

    private val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    private val READ_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)

    /** Suivi courant : les pas seulement. */
    val STEPS = setOf(READ_STEPS)

    /** Import ponctuel : pas et poids, au-delà des 30 jours qu'Health Connect donne par défaut. */
    val IMPORT = setOf(READ_STEPS, READ_WEIGHT, HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)

    enum class Availability { READY, NEEDS_INSTALL, UNSUPPORTED }

    fun availability(context: Context): Availability = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.READY
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL
        else -> Availability.UNSUPPORTED
    }

    /** Fiche Play Store de Health Connect (Android 13 et moins, ou mise à jour requise). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract(PROVIDER)

    suspend fun granted(context: Context): Set<String> =
        if (availability(context) != Availability.READY) emptySet()
        else HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    suspend fun stepsGranted(context: Context) = granted(context).contains(READ_STEPS)
    fun canReadSteps(granted: Set<String>) = READ_STEPS in granted
    fun canReadWeight(granted: Set<String>) = READ_WEIGHT in granted
    fun canReadHistory(granted: Set<String>) = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted

    /** Pas par jour (epochDay -> nombre) de `fromDay` à `toDay` inclus, en heure locale, par tranches de 90 jours. */
    suspend fun dailySteps(context: Context, fromDay: Long, toDay: Long): Map<Long, Int> {
        val client = HealthConnectClient.getOrCreate(context)
        val out = HashMap<Long, Int>()
        var from = fromDay
        while (from <= toDay) {
            val to = minOf(toDay, from + 89)
            val groups = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDate.ofEpochDay(from).atStartOfDay(), LocalDate.ofEpochDay(to + 1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
            for (g in groups) g.result[StepsRecord.COUNT_TOTAL]?.let { out[g.startTime.toLocalDate().toEpochDay()] = it.toInt() }
            from = to + 1
        }
        return out
    }

    /** Pesées par jour (epochDay -> kg) : la première de la journée, comme une pesée du matin. */
    suspend fun dailyWeights(context: Context, fromDay: Long, toDay: Long): Map<Long, Double> {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val first = HashMap<Long, Pair<java.time.Instant, Double>>()
        var token: String? = null
        do {
            val res = client.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    TimeRangeFilter.between(LocalDate.ofEpochDay(fromDay).atStartOfDay(), LocalDate.ofEpochDay(toDay + 1).atStartOfDay()),
                    pageToken = token,
                )
            )
            for (r in res.records) {
                val day = r.time.atZone(zone).toLocalDate().toEpochDay()
                val kg = r.weight.inKilograms
                if (kg in 20.0..400.0 && (first[day]?.first?.isAfter(r.time) != false)) first[day] = r.time to kg
            }
            token = res.pageToken
        } while (!token.isNullOrEmpty())
        return first.mapValues { Math.round(it.value.second * 10) / 10.0 }
    }
}
