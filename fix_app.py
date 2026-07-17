import re

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'r') as f:
    content = f.read()

new_content = '''package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.repository.ProjectRepository

class PassportPhotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setProjectId(getString(R.string.project_id))
                    .setApplicationId(getString(R.string.google_app_id))
                    .setApiKey(getString(R.string.google_api_key))
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(this, options)
            }
        
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build())
                .build()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
            com.google.firebase.firestore.FirebaseFirestore.getInstance().clearPersistence()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "passport_photo_print.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val repository by lazy {
        ProjectRepository(database.projectDao())
    }
}'''

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'w') as f:
    f.write(new_content)
