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
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
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
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.*
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // initialize the variables
    val paint = Paint()
    var colors = listOf<Int>(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    lateinit var labels : List<String>
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap : Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView : TextureView
    lateinit var model : MobilenetTflite
    lateinit var tts: TextToSpeech
    val ttsQueue = LinkedBlockingQueue<Runnable>()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Start a coroutine to process tasks from the queue
        GlobalScope.launch {
            while (isActive) {
                // Take the next task from the queue and run it
                // This will block until a task is available
                val task = ttsQueue.take()
                task.run()
            }
        }

        get_permission()
        tts = TextToSpeech(this, this)
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()
        model = MobilenetTflite.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)

        predict()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.ENGLISH
            tts.setSpeechRate(0.7f)
        }
    }

    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA),101)
        }
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){
    cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback(){
        override fun onOpened(p0: CameraDevice) {
            cameraDevice = p0

            val surfaceTexture = textureView.surfaceTexture
            val surface = Surface(surfaceTexture)

            val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            captureRequest.addTarget(surface)
            cameraDevice.createCaptureSession(listOf(surface),object : CameraCaptureSession.StateCallback(){
                override fun onConfigured(p0: CameraCaptureSession) {

                    val rotation = windowManager.defaultDisplay.rotation

                    // Calculate the correct orientation for the camera preview
                    val sensorOrientation = cameraManager.getCameraCharacteristics(cameraDevice.id).get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                    val newOrientation = (sensorOrientation - rotation * 90 + 360) % 360

                    // Set the calculated orientation to the camera preview
                    captureRequest.set(CaptureRequest.JPEG_ORIENTATION, newOrientation)

                    p0.setRepeatingRequest(captureRequest.build(),null,null)
                }

                override fun onConfigureFailed(p0: CameraCaptureSession) {
                }
            }, handler)

        }

        override fun onDisconnected(p0: CameraDevice) {
        }

        override fun onError(p0: CameraDevice, p1: Int) {
        }
    }, handler)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }

    fun speakOut(text: String) {
        ttsQueue.add(Runnable {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        })
    }

    fun predict(){
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener{

            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int){
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

                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888,true)
                val canvas = Canvas(mutableBitmap)

                val h = mutableBitmap.height
                val w = mutableBitmap.width
                println("Height: $h, Width: $w")


                paint.textSize = h/15f
                paint.strokeWidth = h/85f

                var x = 0
                scores.forEachIndexed{ index, conf ->
                    val conf = roundOff(conf)
                    if(conf > 0.65){

                        val text = labels.get(classes.get(index).toInt()) // + " at: " + locations.get(x+1)*w + " " + locations.get(x)*h + " " +locations.get(x+3)*w + " " + locations.get(x+2)*h

                        if (!tts.isSpeaking){
                            print("Speaking")
                            speakOut(text)
                        }
                        println(text)

                        // Draw the bounding box
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(locations.get(x+1)*w, locations.get(x)*h, locations.get(x+3)*w, locations.get(x+2)*h, paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labels.get(classes.get(index).toInt()) + " " + conf.toString(), locations.get(x+1)*w, locations.get(x)*h, paint)
                    }
                }
                imageView.setImageBitmap(mutableBitmap)
            }
        }
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


