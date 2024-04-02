package com.nod.realtime_objectdetection
// Importing the required libraries
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
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
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Decalring the variables
    val paint = Paint()
    var colors = listOf(Color.BLUE, Color.GREEN, Color.RED,Color.CYAN,Color.GRAY,Color.BLACK,Color.DKGRAY,Color.MAGENTA,Color.YELLOW,Color.RED)
    lateinit var textureView: TextureView
    lateinit var imageView: ImageView
    lateinit var warning : TextView
    lateinit var cameraDevice: CameraDevice
    lateinit var cameraManager: CameraManager
    lateinit var handler: Handler
    lateinit var model: MobilenetTflite
    lateinit var tts: TextToSpeech
    lateinit var bitmap: Bitmap
    lateinit var imageProcessor: ImageProcessor
    lateinit var labels: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        get_permission()
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

    fun get_permission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
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
            get_permission()
        }
    }

    fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    fun predict() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

                bitmap = textureView.bitmap!!
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
                println("Height: $h, Width: $w")

                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f

                var x = 0
                val stringBuilder = StringBuilder()
                scores.forEachIndexed { index, conf ->

                    val top = locations.get(x) * h
                    val left = locations.get(x + 1) * w
                    val bottom = locations.get(x + 2) * h
                    val right = locations.get(x + 3) * w

                    x = index * 4
                    val conf = roundOff(conf)

                    if (conf > 0.65) {
                        val text = obj_pos(left, top, right, bottom, w, h, labels.get(classes.get(index).toInt()))
                        stringBuilder.append(text + "\n")
                        
                        // Draw the bounding box
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(left,top,right,bottom,paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labels.get(classes.get(index).toInt()) + " " + conf.toString(),left,top,paint)
                    }
                }
                imageView.setImageBitmap(mutableBitmap)
                val allText = stringBuilder.toString()
                if(!tts.isSpeaking && allText.isNotEmpty()){
                    warning.setText(allText)
                    speakOut(allText)
                }
            }
        }
    }

    fun obj_pos(left: Float, top: Float, right: Float, bottom: Float, w: Int, h: Int, obj_class : String) : String{
        val x = (left + right) / 2
        val y = (top + bottom) / 2

        var text = obj_class

        if (x < w / 3) {
            text += " at left"
        } else if (x > 2 * w / 3) {
            text += " at right"
        }

        if ( (x > w / 3 && x < 2 * w / 3) && (y > h / 3 && y < 2 * h / 3) ) {
            text += " ahead"
        }
        return text
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

    override fun onResume() {
        super.onResume()
    }
}