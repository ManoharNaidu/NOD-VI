package com.nod.realtime_objectdetection
// Importing the required libraries
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nod.realtime_objectdetection.ml.MobilenetTflite
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.Locale

data class DetectedObject(
    val classLabel: String,
    val confidence: Float,
    val boundingBox: RectF
)

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Declaring the variables
    private var previousOutcomes = mutableListOf<String>()
    private val detectedObjects = mutableListOf<DetectedObject>()
    private val positionCounts = mutableMapOf<String, MutableList<String>>()

    val paint = Paint()
    private var colors = listOf(Color.BLUE, Color.GREEN, Color.RED,Color.CYAN,Color.GRAY,Color.BLACK,Color.DKGRAY,Color.MAGENTA,Color.YELLOW,Color.RED)
    lateinit var textureView: TextureView
    lateinit var imageView: ImageView
    private lateinit var warning : TextView
    lateinit var cameraDevice: CameraDevice
    private lateinit var cameraManager: CameraManager
    lateinit var handler: Handler
    lateinit var bitmap: Bitmap

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent

    lateinit var model: MobilenetTflite
    private lateinit var tts: TextToSpeech
    lateinit var imageProcessor: ImageProcessor
    lateinit var labels: List<String>
    private val CONFIDENCE_THRESHOLD = 0.65f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.packageName)

        getPermission()

        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)
        warning = findViewById(R.id.warning)
        model = MobilenetTflite.newInstance(this)
        tts = TextToSpeech(this, this)
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        labels = FileUtil.loadLabels(this, "labels.txt")

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        predict()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH)
            tts.setSpeechRate(0.7f)
        }
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Use TextToSpeech to ask for permission
            tts.speak("This app needs camera permission to work, please say yes to grant permission", TextToSpeech.QUEUE_FLUSH, null, "")

            // Use SpeechRecognizer to listen to the user's response
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {}

                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null) {
                        for (result in matches) {
                            if (result.contains("yes", true)) {
                                // If the user's response is affirmative, request the permission
                                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
                                break
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle) {}

                override fun onEvent(eventType: Int, params: Bundle) {}
            })
            speechRecognizer.startListening(speechRecognizerIntent)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        cameraManager.openCamera(
            cameraManager.cameraIdList[0],
            object : CameraDevice.StateCallback() {
                override fun onOpened(p0: CameraDevice) {
                    cameraDevice = p0

                    val surfaceTexture = textureView.surfaceTexture
                    val surface = Surface(surfaceTexture)

                    val captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                    captureRequest.addTarget(surface)
                    cameraDevice.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(p0: CameraCaptureSession) {
                                p0.setRepeatingRequest(captureRequest.build(), null, null)
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                            }
                        },
                        handler
                    )

                }

                override fun onDisconnected(p0: CameraDevice) {
                }

                override fun onError(p0: CameraDevice, p1: Int) {
                }
            },
            handler
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission()
        }
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun predict() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

                bitmap = textureView.bitmap?: return
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
//                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)

                val h = mutableBitmap.height
                val w = mutableBitmap.width

                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f

                detectedObjects.clear()
                var x = 0
                scores.forEachIndexed { index, conf ->
                    val top = locations[x] * h
                    val left = locations[x + 1] * w
                    val bottom = locations[x + 2] * h
                    val right = locations[x + 3] * w
                    x += 4

                    val confidence = roundOff(conf)
                    if (confidence > CONFIDENCE_THRESHOLD) {
                        val classLabel = labels[classes[index].toInt()]

                        detectedObjects.add(DetectedObject(classLabel, confidence, RectF(left, top, right, bottom)))

                        // Draw bounding box and label on the bitmap
                        setPaintProperties(index)
                        canvas.drawRect(left, top, right, bottom, paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText("$classLabel $confidence", left, top, paint)
                    }
                }

                imageView.setImageBitmap(mutableBitmap)
                summarizeFrame(detectedObjects, w, h)
            }
        }
    }

    private fun warning() {

        println("Position Counts: $positionCounts")

        if (positionCounts.isEmpty()) {
            return
        }

        val warning = when {
            positionCounts.containsKey("bottom center") -> {

                    if (positionCounts.containsKey("bottom left") && positionCounts.containsKey("bottom right")) {
                        "Stay still"
                    } else if (positionCounts.containsKey("bottom left")) {
                        "Move right"
                    } else if (positionCounts.containsKey("bottom right")) {
                        "Move left"
                    } else {
                        "Move left or right"
                    }
            }

            positionCounts.containsKey("middle center") -> {
                if (!positionCounts.containsKey("bottom center")){
                    "Move forward"     //warn about the obj that will be in ahead of the user
                } else if (positionCounts.containsKey("bottom left") && positionCounts.containsKey("bottom right")){
                    "Stay still"
                } else if (positionCounts.containsKey("bottom left")) {
                    "Move right"
                } else if (positionCounts.containsKey("bottom right")) {
                    "Move left"
                } else {
                    "Move left or right"
                }
            }

            positionCounts.containsKey("middle left")  || positionCounts.containsKey("bottom left")-> {
                "Move little-bit right"
            }

            positionCounts.containsKey("middle right") || positionCounts.containsKey("bottom right") -> {
                "Move little-bit left"
            }
            else -> ""
        }

        println("Warning: $warning")

        if (previousOutcomes.isNotEmpty() && previousOutcomes.last() == warning) {
            return
        }

        previousOutcomes.add(warning)
        if(previousOutcomes.size >= 10){
            previousOutcomes.removeAt(0)
        }

        if(!tts.isSpeaking){
        this.warning.text = warning
            speakOut(warning)
        }
    }

    private fun summarizeFrame(detectedObjects: List<DetectedObject>, width: Int = bitmap.width, height: Int = bitmap.height) {
        detectedObjects.forEachIndexed { _, obj ->
            val position = objPos(obj.boundingBox, width, height)
            positionCounts.getOrPut(position) { mutableListOf() }.add(obj.classLabel)
        }
        warning()
        positionCounts.clear()
    }

    private fun objPos(boundBox: RectF, w: Int, h: Int): String {
        val x = (boundBox.left + boundBox.right) / 2
        val y = (boundBox.top + boundBox.bottom) / 2

        var position = ""

        // for height
        position += if (y < h / 3) {
            "top"
        } else if (y > 2 * h / 3) {
            "bottom"
        } else{
            "middle"
        }

        // for width
        position += if (x < w / 3) {
            " left"
        } else if (x > 2 * w / 3) {
            " right"
        } else {
            " center"
        }
        return position.trim()
    }

    private fun setPaintProperties(index: Int) {
        paint.color = colors[index % colors.size]
        paint.style = Paint.Style.STROKE
    }

    fun roundOff(x: Float): Float {
        val scale = 100f
        return Math.round(x * scale) / scale
    }

    override fun onDestroy() {
        cameraDevice.close()
        model.close()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}