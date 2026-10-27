package app.suical.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.suical.MainActivity
import app.suical.R
import app.suical.data.Repository
import app.suical.domain.AppState
import app.suical.domain.Calc
import app.suical.domain.Dish
import app.suical.domain.dailyTotals
import app.suical.domain.planSlotToMeal
import app.suical.domain.todayEpochDay
import app.suical.ui.nf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Widget d'écran d'accueil : calories et protéines du jour, et un bouton « Valider » pour le repas prévu. */
class SuicalWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context, Repository(context).state().first()) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EAT) return super.onReceive(context, intent)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repository(context)
                val s = repo.state().first()
                val c = Calc.of(s)
                val slot = planSlotToMeal(planSlotNow())
                plannedDish(s)?.let { repo.logDish(s, c, it, todayEpochDay(), slot, fromStock = true) }
                refresh(context, repo.state().first())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EAT = "app.suical.widget.EAT"

        private fun planSlotNow(now: LocalTime = LocalTime.now()) = if (now.hour < 15) 0 else 1

        /** Repas prévu maintenant : midi avant 15 h, soir ensuite. Null si le jour est hors plan ou si c'est déjà noté. */
        fun plannedDish(s: AppState, now: LocalTime = LocalTime.now()): Dish? {
            val today = todayEpochDay()
            val idx = s.config.indexOf(today) ?: return null
            val planSlot = planSlotNow(now)
            val d = s.plan.getOrNull(idx)?.getOrNull(planSlot)?.let { s.dish(it) } ?: return null
            val slot = planSlotToMeal(planSlot)
            return d.takeIf { s.log.none { e -> e.day == today && e.slot == slot && e.dishId == d.id } }
        }

        /** Redessine tous les widgets posés. Sans effet s'il n'y en a aucun. */
        fun refresh(context: Context, s: AppState) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SuicalWidget::class.java))
            if (ids.isEmpty()) return

            val c = Calc.of(s)
            val today = todayEpochDay()
            val tot = c.dailyTotals(today, today)[today]
            val kcal = tot?.getValue("kcal") ?: 0.0
            val prot = tot?.getValue("p") ?: 0.0
            val target = s.target ?: (c.baseKcal / 50).toInt() * 50

            val v = RemoteViews(context.packageName, R.layout.widget)
            v.setTextViewText(R.id.widget_kcal, "${nf(kcal)} / ${nf(target.toDouble())} kcal")
            v.setTextViewText(R.id.widget_prot, "${nf(prot)} g de protéines")

            val dish = plannedDish(s)
            if (dish != null) {
                v.setViewVisibility(R.id.widget_eat, View.VISIBLE)
                v.setTextViewText(R.id.widget_eat, "Valider : ${dish.name}")
                val eat = Intent(context, SuicalWidget::class.java).setAction(ACTION_EAT)
                v.setOnClickPendingIntent(R.id.widget_eat,
                    PendingIntent.getBroadcast(context, 1, eat, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                v.setViewVisibility(R.id.widget_eat, View.GONE)
            }
            v.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            mgr.updateAppWidget(ids, v)
        }
    }
}
