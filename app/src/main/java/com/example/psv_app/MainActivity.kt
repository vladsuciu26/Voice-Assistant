package com.example.psv_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var editText: EditText? = null
    private var startButton: ImageView? = null
    private var recognitionIntent: Intent? = null
    private var lastCommand: String? = null
    private var lastCommandPizza: String? = null
    private var lastCommandSoda: String? = null
    val pizzaList = mapOf("margarita" to 10.0, "pepperoni" to 12.0, "vegetarian" to 11.0)
    val sodaList = mapOf("coca-Cola" to 2.0, "pepsi" to 3.0, "sprite" to 1.5)
    val orderList = mutableListOf<String>()

    private var startTime: Long = 0
    private var endTime: Long = 0

    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        startButton = findViewById(R.id.buttonAssist)

        checkPermissions()
        initializeTextToSpeech()

        val jsonContent = loadJSONFromAsset("intents.json")
        val intentsArray = jsonContent?.let { JSONObject(it).getJSONArray("intents") }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognitionIntent!!.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognitionIntent!!.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        speechRecognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {
                editText!!.setText("")
                editText!!.setHint("Listening...")
                startTime = System.currentTimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {}

            override fun onResults(results: Bundle?) {
//                endTime = System.currentTimeMillis()
                startButton!!.setImageResource(R.drawable.ic_mic_green)
                val data = results!!.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                editText!!.setText(data!![0])

                val lowerCaseData = data[0].lowercase(Locale.ROOT)


                if (lastCommandPizza == "add_pizza") {
                    if (!pizzaList.containsKey(lowerCaseData)) {
                        speak("I'm sorry, we don't have ${data[0]}. Please choose from Margarita, Pepperoni, or Vegetarian.")
                    } else {
                        addItem(data[0])
                    }
                    lastCommandPizza = null
                } else if (lastCommandSoda == "add_soda") {
                    if (!sodaList.containsKey(lowerCaseData)) {
                        speak("I'm sorry, we don't have ${data[0]}. Please choose from Coca-Cola, Pepsi, or Sprite.")
                    } else {
                        addItem(data[0])
                    }
                    lastCommandSoda = null
                } else {
                    val response = processCommand(lowerCaseData, intentsArray!!)
                    Log.d("SpeechRecognition", "response: $response")
                }

                endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                Log.d("ResponseTime", "Timpul de raspuns: $responseTime ms")
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}

        })

        setupStartButton()

    }

    private fun processCommand(command: String, intentsArray: JSONArray): String {
        Log.d("HumanResponse", "Human says: $command")
        for (i in 0 until intentsArray.length()) {
            val intent = intentsArray.getJSONObject(i)
            val patterns = intent.getJSONArray("patterns")
            if (patterns.toString().contains(command)) {
                val tag = intent.getString("tag")
                when (tag) {
                    "hello" -> {
                        speak("Hello! Welcome to Order Assistant! How can I help you?")
                        return "Listening for the proper order"
                    }

                    "items_order_list" -> {
                        speak("We have pizzas and sodas available. You can ask me to show them.")
                        return "Listening for pizzas or sodas list"
                    }

                    "show_pizzas" -> {
                        speak("Here are the available pizzas:")
                        pizzaList.forEach { (name, price) ->
                            speak("$name: $$price")
                        }
                        return "Listening for pizza to add..."
                    }

                    "add_pizza" -> {
                        speak("What pizza do you want to add to your order?")
                        lastCommandPizza = "add_pizza"
                        return "Listening for task to add..."
                    }

                    "show_sodas" -> {
                        speak("Here are the available sodas:")
                        sodaList.forEach { (name, price) ->
                            speak("$name: $$price")
                        }
                        return "Listening for soda to add..."
                    }

                    "add_soda" -> {
                        speak("What soda do you want to add to your order?")
                        lastCommandSoda = "add_soda"
                        return "Listening for next soda to add..."
                    }

                    "finalize_order" -> {
                        finalizeOrder()
                        return "Order finalized."
                    }

                    "exit" -> {
                        speak("Application is closing")
                        finish()
                        return "App is closed."
                    }

                    else -> speak("I'm sorry, I didn't understand that.")
                }
            }
        }
        speak ("I'm sorry, I didn't understand that.")
        return "I'm sorry, I didn't understand that."
    }

    private fun finalizeOrder() {
        if (orderList.isEmpty()) {
            speak("Your order is empty.")
        } else {
            speak("Here is your order:")
            var total = 0.0
            orderList.forEach { item ->
                speak(item)
                total += pizzaList[item] ?: 0.0
                total += sodaList[item] ?: 0.0
            }
            speak("The total price is $$total.")
            orderList.clear()
        }
    }

    private fun addItem(item: String) {
        Log.d("HumanResponse", "Human says: $item")
        orderList.add(item)
        speak("$item has been added to your order.")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupStartButton() {
        startButton!!.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                speechRecognizer!!.stopListening()
                startButton!!.setImageResource(R.drawable.ic_mic_green)
            }
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                startButton!!.setImageResource(R.drawable.ic_mic_red)
                speechRecognizer!!.startListening(recognitionIntent)
            }
            false
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech!!.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Toast.makeText(this, "Language is not supported", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun speak(text: String) {
        Log.d("MachineResponse", "Machine response: $text")
        textToSpeech!!.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun loadJSONFromAsset(fileName: String): String? {
        return try {
            val inputStream = assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RecordAudioRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RecordAudioRequestCode && grantResults.isNotEmpty()) {
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val RecordAudioRequestCode = 1
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer!!.destroy()
    }
}