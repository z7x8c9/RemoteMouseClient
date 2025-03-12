package com.example.remotemouse

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var webSocket: WebSocket
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var scrollBar: SeekBar
    private lateinit var connectButton: Button
    private lateinit var ipEditText: EditText
    private lateinit var sensitivityBar: SeekBar
    private lateinit var moveButton: Button

    private lateinit var sensorManager: SensorManager
    private lateinit var gyroSensor: Sensor
    private var lastGyroEventTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var sensitivity = 100f  // Чувствительность по умолчанию
    private var isCalibrating = false
    private var calibrationOffsetX = 0f
    private var calibrationOffsetY = 0f
    private var isMoving = false  // Флаг для перемещения мыши

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация элементов
        leftButton = findViewById(R.id.leftButton)
        rightButton = findViewById(R.id.rightButton)
        scrollBar = findViewById(R.id.scrollBar)
        connectButton = findViewById(R.id.connectButton)
        ipEditText = findViewById(R.id.ipEditText)
        sensitivityBar = findViewById(R.id.sensitivityBar)
        moveButton = findViewById(R.id.moveButton)

        // Инициализация гироскопа
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)!!

        // Обработка нажатий на левую кнопку
        leftButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> sendMouseAction("left", true)  // Нажатие
                MotionEvent.ACTION_UP -> sendMouseAction("left", false)   // Отпускание
            }
            true
        }

        // Обработка нажатий на правую кнопку
        rightButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> sendMouseAction("right", true)  // Нажатие
                MotionEvent.ACTION_UP -> sendMouseAction("right", false)   // Отпускание
            }
            true
        }

        // Обработка прокрутки
        scrollBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val delta = progress - 50
                if (delta != 0) {
                    sendMouseScroll(delta)
                    seekBar.progress = 50  // Сброс положения
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Настройка чувствительности
        sensitivityBar.progress = sensitivity.toInt()
        sensitivityBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                sensitivity = progress.toFloat()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Кнопка для перемещения мыши
        moveButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isMoving = true
                    lastX = 0f  // Сброс предыдущих значений
                    lastY = 0f
                }
                MotionEvent.ACTION_UP -> {
                    isMoving = false
                }
            }
            true
        }

        // Калибровка гироскопа
        val calibrateButton: Button = findViewById(R.id.calibrateButton)
        calibrateButton.setOnClickListener {
            isCalibrating = true
            calibrationOffsetX = lastX
            calibrationOffsetY = lastY
            Toast.makeText(this, "Калибровка начата", Toast.LENGTH_SHORT).show()
        }

        // Подключение к серверу
        connectButton.setOnClickListener {
            val ip = ipEditText.text.toString()
            if (ip.isNotEmpty()) {
                connectToServer(ip)
            } else {
                Toast.makeText(this, "Введите IP сервера", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToServer(ip: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("ws://$ip:5000")  // WebSocket URL
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Подключено к серверу", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                runOnUiThread {
                    val errorMessage = "Ошибка подключения: ${t.message}\nПодробности: ${t.stackTraceToString()}"
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Соединение закрыто", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun sendMouseAction(button: String, state: Boolean) {
        if (!::webSocket.isInitialized) return

        val json = JSONObject().apply {
            put("action", "click")
            put("button", button)
            put("state", state)
        }
        webSocket.send(json.toString())
    }

    private fun sendMouseMove(dx: Float, dy: Float) {
        if (!::webSocket.isInitialized) return

        val json = JSONObject().apply {
            put("action", "move")
            put("dx", dx)
            put("dy", dy)
        }
        webSocket.send(json.toString())
    }

    private fun sendMouseScroll(delta: Int) {
        if (!::webSocket.isInitialized) return

        val json = JSONObject().apply {
            put("action", "scroll")
            put("delta", delta)
        }
        webSocket.send(json.toString())
    }

    // Обработка данных гироскопа
    override fun onSensorChanged(event: SensorEvent?) {
        if (!::webSocket.isInitialized || !isMoving) return

        event?.let {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGyroEventTime > 150) {  // Дебаунсинг
                val deltaX = event.values[0] - lastX
                val deltaY = event.values[1] - lastY

                if (isCalibrating) {
                    calibrationOffsetX = event.values[0]
                    calibrationOffsetY = event.values[1]
                    isCalibrating = false
                    Toast.makeText(this, "Калибровка завершена", Toast.LENGTH_SHORT).show()
                }

                // Масштабирование данных гироскопа
                val adjustedX = (event.values[1] - calibrationOffsetY) * sensitivity / 25  // Ось Y для движения по X
                val adjustedY = (event.values[0] - calibrationOffsetX) * sensitivity / 25  // Ось X для движения по Y

                if (abs(adjustedX) > 0.01 || abs(adjustedY) > 0.01) {
                    sendMouseMove(adjustedX, adjustedY)
                    lastX = event.values[0]
                    lastY = event.values[1]
                    lastGyroEventTime = currentTime
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        // Регистрация слушателя гироскопа
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        // Отмена регистрации слушателя гироскопа
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Приложение закрыто")
        }
    }
}