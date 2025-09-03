package com.justdial.ocr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.justdial.ocr.databinding.ActivityMainBinding
import com.justdial.ocr.model.MainViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val viewModel: MainViewModel by viewModels()

    private lateinit var objectDetector: ObjectDetector
    private var lastDetectedRect: Rect? = null

    // --- Google Sign-In Members ---
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<android.content.Intent>
    private var signedInAccount: GoogleSignInAccount? = null
    private var pendingCropRect: Rect? = null // To store the crop rect while signing in

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupGoogleSignIn()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        binding.captureButton.setOnClickListener { takePhoto() }
        binding.closeButton.setOnClickListener {
            viewModel.resetState()
            lastDetectedRect = null
            binding.boundingBoxOverlay.updateBoundingBox(null, null)
        }

        observeViewModel()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("134377649404-g8dl23e9f530g6khi4mkbf05jd46o6nd.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } else {
                Toast.makeText(this, "Google Sign-In was cancelled.", Toast.LENGTH_SHORT).show()
            }
        }
        signedInAccount = GoogleSignIn.getLastSignedInAccount(this)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            signedInAccount = completedTask.getResult(ApiException::class.java)
            Toast.makeText(this, "Signed in as ${signedInAccount?.email}", Toast.LENGTH_SHORT).show()

            val idToken = signedInAccount?.idToken
            if (idToken != null && pendingCropRect != null) {
                proceedWithPhotoCapture(idToken, pendingCropRect!!)
                pendingCropRect = null // Clear the pending rect
            } else {
                Toast.makeText(this, "Could not get ID Token or crop area after sign-in.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
            Toast.makeText(this, "Sign-in failed. Code: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.ocrState.observe(this) {
            when (it) {
                is MainViewModel.OcrUiState.Idle -> {
                    binding.resultView.visibility = View.GONE
                    binding.captureButton.isEnabled = true
                }
                is MainViewModel.OcrUiState.Loading -> {
                    binding.resultView.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.VISIBLE
                    binding.resultText.text = "Analyzing..."
                    binding.captureButton.isEnabled = false
                }
                is MainViewModel.OcrUiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.text = it.text
                }
                is MainViewModel.OcrUiState.ChequeSuccess -> {
                    binding.progressBar.visibility = View.GONE
                    val result = buildString {
                        append("✅ CHEQUE PROCESSED\n\n")
                        append("Bank: ${it.chequeData.bankName}\n")
                        append("Account: ${it.chequeData.accountNumber}\n")
                        append("IFSC: ${it.chequeData.ifscCode}\n")
                        append("Holder: ${it.chequeData.accountHolderName}\n")
                        if (it.validation.errors.isNotEmpty()) {
                            append("\n❌ Validation Errors:\n")
                            it.validation.errors.forEach { error ->
                                append("• ${error.message}\n")
                            }
                        }
                        if (it.validation.warnings.isNotEmpty()) {
                            append("\n⚠️ Warnings:\n")
                            it.validation.warnings.forEach { warning ->
                                append("• ${warning.message}\n")
                            }
                        }
                    }
                    binding.resultText.text = result
                }
                is MainViewModel.OcrUiState.ENachSuccess -> {
                    binding.progressBar.visibility = View.GONE
                    val result = buildString {
                        append("✅ E-NACH PROCESSED\n\n")
                        append("Utility: ${it.enachData.utilityName}\n")
                        append("Account: ${it.enachData.accountNumber}\n")
                        append("IFSC: ${it.enachData.ifscCode}\n")
                        append("Holder: ${it.enachData.accountHolderName}\n")
                        append("Max Amount: ${it.enachData.maxAmount}\n")
                        if (it.validation.errors.isNotEmpty()) {
                            append("\n❌ Validation Errors:\n")
                            it.validation.errors.forEach { error ->
                                append("• ${error.message}\n")
                            }
                        }
                        if (it.validation.warnings.isNotEmpty()) {
                            append("\n⚠️ Warnings:\n")
                            it.validation.warnings.forEach { warning ->
                                append("• ${warning.message}\n")
                            }
                        }
                    }
                    binding.resultText.text = result
                }
                is MainViewModel.OcrUiState.CrossValidationSuccess -> {
                    binding.progressBar.visibility = View.GONE
                    val result = buildString {
                        append("✅ CROSS-VALIDATION COMPLETE\n\n")
                        append("Overall Score: ${String.format("%.2f", it.result.overallScore)}\n")
                        append("Validation: ${if (it.result.crossValidationPassed) "PASSED" else "FAILED"}\n\n")
                        
                        if (it.result.conflicts.isNotEmpty()) {
                            append("❌ Conflicts Found:\n")
                            it.result.conflicts.forEach { conflict ->
                                append("• ${conflict.fieldName}: ${conflict.description}\n")
                            }
                        } else {
                            append("✅ No conflicts found between documents\n")
                        }
                        
                        append("\n📊 Field Matches:\n")
                        it.result.matchingFields.forEach { match ->
                            val status = if (match.isMatch) "✅" else "❌"
                            append("$status ${match.fieldName}: ${String.format("%.2f", match.confidence)}\n")
                        }
                    }
                    binding.resultText.text = result
                }
                is MainViewModel.OcrUiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.text = "Error:\n${it.message}"
                }
            }
        }
    }

    private fun takePhoto() {
        val cropRect = lastDetectedRect
        if (cropRect == null) {
            Toast.makeText(this, "No object detected to capture.", Toast.LENGTH_SHORT).show()
            return
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && account.idToken != null) {
            proceedWithPhotoCapture(account.idToken!!, cropRect)
        } else {
            pendingCropRect = cropRect
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun proceedWithPhotoCapture(idToken: String, cropRect: Rect) {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val fullBitmap = image.toBitmap()
                    image.close()
                    viewModel.analyzeImage(this@MainActivity, fullBitmap, cropRect)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(options)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1080, 1920))
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1080, 1920))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    objectDetector.process(image)
                        .addOnSuccessListener { detectedObjects ->
                            if (detectedObjects.isNotEmpty()) {
                                val detectedObject = detectedObjects[0]
                                val box = detectedObject.boundingBox
                                val transformedRect = transformRect(box, imageProxy.width, imageProxy.height)
                                val label = detectedObject.labels.firstOrNull()?.text ?: "Object"
                                runOnUiThread { binding.boundingBoxOverlay.updateBoundingBox(transformedRect, label) }
                                lastDetectedRect = box
                            } else {
                                runOnUiThread { binding.boundingBoxOverlay.updateBoundingBox(null, null) }
                                lastDetectedRect = null
                            }
                        }
                        .addOnFailureListener { e -> Log.e(TAG, "ML Kit detection failed", e) }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun transformRect(sourceRect: Rect, imageWidth: Int, imageHeight: Int): RectF {
        val overlay = binding.boundingBoxOverlay
        val scaleX = overlay.width.toFloat() / imageWidth.toFloat()
        val scaleY = overlay.height.toFloat() / imageHeight.toFloat()

        return RectF(
            sourceRect.left * scaleX,
            sourceRect.top * scaleY,
            sourceRect.right * scaleX,
            sourceRect.bottom * scaleY
        )
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val activityResultLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        objectDetector.close()
        googleSignInClient.signOut() // Sign out on destroy
    }

    companion object {
        private const val TAG = "ChequeScanner"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}