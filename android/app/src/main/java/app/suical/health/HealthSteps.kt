package app.suical.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period

/**
 * Lecture des pas dans Health Connect. La montre (via Zepp, Google Fit, Samsung Health…) y écrit ;
 * Suical ne fait que lire, et Health Connect fusionne les sources sans compter deux fois.
 */
object HealthSteps {
    private const val PROVIDER = "com.google.android.apps.healthdata"

    val PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))

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

    suspend fun granted(context: Context): Boolean =
        availability(context) == Availability.READY &&
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    /** Pas par jour (epochDay -> nombre) de `fromDay` à `toDay` inclus, en heure locale. Les jours sans donnée sont absents. */
    suspend fun daily(context: Context, fromDay: Long, toDay: Long): Map<Long, Int> {
        val client = HealthConnectClient.getOrCreate(context)
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    LocalDate.ofEpochDay(fromDay).atStartOfDay(), LocalDate.ofEpochDay(toDay + 1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        return groups.mapNotNull { g ->
            g.result[StepsRecord.COUNT_TOTAL]?.let { g.startTime.toLocalDate().toEpochDay() to it.toInt() }
        }.toMap()
    }
}
