package app.suical.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.suical.domain.slotOfHour
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

@Entity(tableName = "food")
data class FoodE(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val cat: String,
    val pack: Double,
    val packLabel: String,
    val price: Double,
    val sort: Int,
)

/** Une valeur nutritionnelle d'un aliment. Un nouveau nutriment n'exige aucune migration. */
@Entity(tableName = "food_nut", primaryKeys = ["foodId", "nutKey"])
data class FoodNutE(val foodId: String, val nutKey: String, val value: Double)

@Entity(tableName = "dish")
data class DishE(
    @PrimaryKey val id: String,
    val name: String,
    val grp: String,
    val sauce: Boolean,
    val fresh: Boolean,
    val sort: Int,
    @ColumnInfo(defaultValue = "2") val slot: Int = 2,
)

@Entity(tableName = "dish_ing", primaryKeys = ["dishId", "foodId"])
data class DishIngE(val dishId: String, val foodId: String, val qty: Double)

@Entity(tableName = "plan", primaryKeys = ["day", "slot"])
data class PlanE(val day: Int, val slot: Int, val dishId: String)

@Entity(tableName = "stock")
data class StockE(@PrimaryKey val foodId: String, val qty: Double)

@Entity(tableName = "ready")
data class ReadyE(@PrimaryKey val dishId: String, val n: Int)

/** `ing` : "id=qté;id=qté". `day` = epochDay, `slot` = moment de la journée. */
@Entity(tableName = "log")
data class LogE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dishId: String,
    val t: Long,
    val ready: Boolean,
    val ing: String,
    @ColumnInfo(defaultValue = "''") val name: String = "",
    @ColumnInfo(defaultValue = "''") val custom: String = "",
    @ColumnInfo(defaultValue = "0") val day: Long = 0,
    @ColumnInfo(defaultValue = "0") val slot: Int = 0,
    @ColumnInfo(defaultValue = "0") val fromStock: Boolean = false,
)

@Entity(tableName = "weight")
data class WeightE(@PrimaryKey val day: Long, val kg: Double)

/** Pas d'un jour (epochDay), recopiés depuis Health Connect. */
@Entity(tableName = "steps")
data class StepsE(@PrimaryKey val day: Long, val count: Int)

@Entity(tableName = "setting")
data class SettingE(@PrimaryKey val k: String, val v: String)

@Dao
interface SuicalDao {
    @Query("SELECT * FROM food ORDER BY sort, id") fun foods(): Flow<List<FoodE>>
    @Query("SELECT * FROM food_nut") fun foodNuts(): Flow<List<FoodNutE>>
    @Query("SELECT * FROM dish ORDER BY sort, id") fun dishes(): Flow<List<DishE>>
    @Query("SELECT * FROM dish_ing ORDER BY rowid") fun dishIngs(): Flow<List<DishIngE>>
    @Query("SELECT * FROM plan") fun plan(): Flow<List<PlanE>>
    @Query("SELECT * FROM stock") fun stock(): Flow<List<StockE>>
    @Query("SELECT * FROM ready") fun ready(): Flow<List<ReadyE>>
    @Query("SELECT * FROM log ORDER BY day DESC, slot, t, id") fun log(): Flow<List<LogE>>
    @Query("SELECT * FROM setting") fun settings(): Flow<List<SettingE>>
    @Query("SELECT * FROM weight ORDER BY day") fun weights(): Flow<List<WeightE>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: WeightE)
    @Query("DELETE FROM weight WHERE day = :day") suspend fun deleteWeight(day: Long)
    @Query("DELETE FROM weight") suspend fun clearWeights()
    @Query("SELECT * FROM steps ORDER BY day") fun steps(): Flow<List<StepsE>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSteps(e: List<StepsE>)
    @Query("DELETE FROM steps") suspend fun clearSteps()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: FoodE)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNuts(e: List<FoodNutE>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: DishE)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putIngs(e: List<DishIngE>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: PlanE)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: StockE)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: ReadyE)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: LogE)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: SettingE)

    @Query("SELECT COALESCE(MAX(sort), 0) + 1 FROM food") suspend fun nextFoodSort(): Int
    @Query("SELECT COALESCE(MAX(sort), 0) + 1 FROM dish") suspend fun nextDishSort(): Int
    @Query("SELECT sort FROM food WHERE id = :id") suspend fun foodSort(id: String): Int?
    @Query("SELECT sort FROM dish WHERE id = :id") suspend fun dishSort(id: String): Int?
    @Query("SELECT v FROM setting WHERE k = :k") suspend fun setting(k: String): String?

    @Query("DELETE FROM food WHERE id = :id") suspend fun deleteFood(id: String)
    @Query("DELETE FROM food_nut WHERE foodId = :id") suspend fun deleteNuts(id: String)
    @Query("DELETE FROM dish WHERE id = :id") suspend fun deleteDish(id: String)
    @Query("DELETE FROM dish_ing WHERE dishId = :id") suspend fun deleteIngsOfDish(id: String)
    @Query("DELETE FROM dish_ing WHERE foodId = :id") suspend fun deleteIngsOfFood(id: String)
    @Query("DELETE FROM stock WHERE foodId = :id") suspend fun deleteStock(id: String)
    @Query("DELETE FROM ready WHERE dishId = :id") suspend fun deleteReady(id: String)
    @Query("UPDATE plan SET dishId = '' WHERE dishId = :id") suspend fun clearPlanOf(id: String)
    @Query("DELETE FROM log WHERE id = :id") suspend fun deleteLog(id: Long)
    @Query("DELETE FROM setting WHERE k = :k") suspend fun deleteSetting(k: String)

    @Query("DELETE FROM food") suspend fun clearFoods()
    @Query("DELETE FROM food_nut") suspend fun clearNuts()
    @Query("DELETE FROM dish") suspend fun clearDishes()
    @Query("DELETE FROM dish_ing") suspend fun clearIngs()
    @Query("DELETE FROM plan") suspend fun clearPlan()
    @Query("DELETE FROM stock") suspend fun clearStock()
    @Query("DELETE FROM ready") suspend fun clearReady()
    @Query("DELETE FROM log") suspend fun clearLog()
}

@Database(
    entities = [FoodE::class, FoodNutE::class, DishE::class, DishIngE::class, PlanE::class,
        StockE::class, ReadyE::class, LogE::class, SettingE::class, WeightE::class, StepsE::class],
    version = 4,
    exportSchema = false,
)
abstract class SuicalDb : RoomDatabase() {
    abstract fun dao(): SuicalDao

    companion object {
        /** v2 : repas hors plan (nom + valeurs libres) et suivi du poids. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE log ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE log ADD COLUMN custom TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS weight (day INTEGER NOT NULL, kg REAL NOT NULL, PRIMARY KEY(day))")
            }
        }

        /**
         * v3 : le journal est rangé par jour et par moment au lieu de l'heure de saisie, et retient si le stock
         * a été utilisé. Les anciennes lignes reçoivent le jour et le moment de leur heure d'enregistrement.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE log ADD COLUMN day INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE log ADD COLUMN slot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE log ADD COLUMN fromStock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dish ADD COLUMN slot INTEGER NOT NULL DEFAULT 2")
                db.execSQL("UPDATE dish SET slot = 0 WHERE grp = 'jour' AND (id = 'petit_dej' OR lower(name) LIKE '%petit%')")
                val zone = ZoneId.systemDefault()
                db.query("SELECT id, t, ready, dishId, custom FROM log").use { c ->
                    while (c.moveToNext()) {
                        val at = Instant.ofEpochMilli(c.getLong(1)).atZone(zone)
                        val fromStock = c.getInt(2) == 0 && c.getString(3).isNotEmpty() && c.getString(4).isEmpty()
                        db.execSQL("UPDATE log SET day = ?, slot = ?, fromStock = ? WHERE id = ?",
                            arrayOf(at.toLocalDate().toEpochDay(), slotOfHour(at.hour), if (fromStock) 1 else 0, c.getLong(0)))
                    }
                }
            }
        }

        /** v4 : pas par jour (Health Connect). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS steps (day INTEGER NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(day))")
            }
        }

        @Volatile private var instance: SuicalDb? = null

        /** Une seule instance partagée par l'app, le widget et les rappels. */
        fun get(context: Context): SuicalDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SuicalDb::class.java, "suical.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
