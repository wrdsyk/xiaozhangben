package com.jizhang

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.jizhang.data.AppDatabase
import com.jizhang.data.MIGRATION_1_2
import com.jizhang.data.MIGRATION_2_3
import com.jizhang.data.MIGRATION_3_4
import com.jizhang.data.MIGRATION_4_5
import com.jizhang.data.ImageStore
import com.jizhang.data.SettingsRepository
import com.jizhang.data.UpdateController

class JizhangApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "jizhang.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()
    val settings = SettingsRepository(context)
    val images = ImageStore(context)

    /** Checks GitHub for a newer release and installs it; lives here so a download outlives screens. */
    val updater = UpdateController(context.applicationContext, settings)
}
