package com.nod.realtime_objectdetection

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import android.os.Handler


class SpeechActivity : AppCompatActivity(), OnInitListener {
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(0.7f)
            tts.speak("NOD Ready to go ", TextToSpeech.QUEUE_FLUSH, null, "")
        }
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 1000)
    }


}

