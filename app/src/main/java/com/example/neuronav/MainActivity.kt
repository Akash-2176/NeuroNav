package com.example.neuronav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_SPEECH_INPUT = 100
    private val REQUEST_MIC_PERMISSION = 200
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, "TTS Init Failed", Toast.LENGTH_SHORT).show()
            }
        }

        val listenBtn = findViewById<Button>(R.id.btnListen)

        listenBtn.setOnClickListener {
            if (checkMicPermission()) {
                startVoiceRecognition()
            } else {
                requestMicPermission()
            }
        }
    }

    private fun checkMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MIC_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition()
            } else {
                Toast.makeText(this, "Mic permission is required to use voice commands", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command...")

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = result?.get(0) ?: ""
            Toast.makeText(this, "Command: $spokenText", Toast.LENGTH_LONG).show()
            handleVoiceCommand(spokenText.lowercase())
        }
    }

    private fun handleVoiceCommand(command: String) {
        if (command.contains("join class with")) {
            val contactName = command.substringAfter("join class with").trim()

            // Save the contact name for AccessibilityService
            val prefs = getSharedPreferences("NeuroNavPrefs", MODE_PRIVATE)
            prefs.edit().putString("targetContact", contactName).apply()

            speak("Okay, joining class with $contactName")
            showPopup("Opening WhatsApp...")

            Handler(Looper.getMainLooper()).postDelayed({
                openWhatsApp()
            }, 1500)

            // Prompt for Accessibility if not enabled
            if (!isAccessibilityServiceEnabled()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    speak("Please enable NeuroNav Accessibility Service for automation")
                    openAccessibilitySettings()
                }, 4000)
            }

        } else {
            speak("Sorry, I didn't understand that command.")
            showPopup("Command not recognized.")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "${packageName}/${MyAccessibilityService::class.java.name}"
        val enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { it.equals(expectedService, ignoreCase = true) }
    }

    private fun openWhatsApp() {
        val packageManager = applicationContext.packageManager
        val whatsappPackage = "com.whatsapp"

        try {
            val info = packageManager.getApplicationInfo(whatsappPackage, 0)
            Log.d("WA_CHECK", "WhatsApp found: ${info.packageName}")

            val intent = packageManager.getLaunchIntentForPackage(whatsappPackage)
            if (intent != null) {
                startActivity(intent)
                showPopup("WhatsApp launched. Hold tight...")
            } else {
                Log.e("WA_CHECK", "Intent was null for com.whatsapp")
                showPopup("Couldn't launch WhatsApp.")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("WA_CHECK", "WhatsApp not installed.")
            showPopup("WhatsApp not installed on this device.")
        }
    }




    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun showPopup(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        playStepSound()
    }

    private fun playStepSound() {
        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}
