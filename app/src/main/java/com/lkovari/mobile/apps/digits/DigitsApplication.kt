package com.lkovari.mobile.apps.digits

import android.app.Application
import com.google.firebase.FirebaseApp

class DigitsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}
