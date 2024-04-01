package com.nod.realtime_objectdetection

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.os.Handler
import android.speech.tts.TextToSpeech
import java.util.Locale

class SplashActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    val phrases = listOf(
        "Let's get started!",
        "Ready, set, go!",
        "Onwards and upwards!",
        "Dive right in!",
        "Adventure awaits!",
        "Your journey begins now!",
        "You are good to move"
    )

    val randomPhrase = phrases.random()

    private lateinit var tts: TextToSpeech
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH)
            tts.setSpeechRate(0.7f)
            tts.speak(randomPhrase, TextToSpeech.QUEUE_FLUSH, null, "")
        }
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 2000)

    }
}
