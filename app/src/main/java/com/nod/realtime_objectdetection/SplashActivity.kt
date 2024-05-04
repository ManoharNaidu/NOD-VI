package com.nod.realtime_objectdetection

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class SplashActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private val SPLASH_TIME_OUT: Long = 3000 // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        // Display splash screen for a few seconds and then start MainActivity
        Handler().postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_TIME_OUT)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language for TextToSpeech (here, using default locale)
            val result = tts.setLanguage(Locale.getDefault())

            // Check if TextToSpeech is available and the language is supported
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Language not supported
                // Handle error or choose a different language
            } else {
                // Language supported, speak a greeting or message
                tts.speak("You're ready to go", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        } else {
            // TextToSpeech initialization failed
            // Handle initialization error
        }
    }

    override fun onDestroy() {
        // Shutdown TextToSpeech when activity is destroyed
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
