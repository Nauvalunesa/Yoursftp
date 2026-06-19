package com.yoursftp.app

import android.app.Application
import com.yoursftp.app.data.AppDatabase
import com.yoursftp.app.data.ConnectionRepository

class YoursFtpApp : Application() {
    lateinit var repository: ConnectionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = ConnectionRepository(db.connectionDao())
    }
}
