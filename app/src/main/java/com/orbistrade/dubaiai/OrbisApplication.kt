package com.orbistrade.dubaiai

import android.app.Application

class OrbisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: OrbisApplication
            private set
    }
}
