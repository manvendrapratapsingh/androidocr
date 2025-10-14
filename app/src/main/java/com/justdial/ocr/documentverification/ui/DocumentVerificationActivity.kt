package com.justdial.ocr.documentverification.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.justdial.ocr.R
import com.justdial.ocr.documentverification.model.DocumentType
import com.justdial.ocr.documentverification.service.DocumentVerificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class DocumentVerificationActivity : AppCompatActivity() {

    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button
    private lateinit var btnAnalyze: Button
    private lateinit var imgPreview: ImageView
    private lateinit var previewCard: MaterialCardView
    private lateinit var progressContainer: MaterialCardView
    private lateinit var progressText: TextView
    private lateinit var tvResultsTitle: TextView
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var spinnerDocumentType: Spinner

    private lateinit var documentService: DocumentVerificationService
    private lateinit var adapter: DocumentResultAdapter

    private var currentBitmap: Bitmap? = null
    private var currentPhotoUri: Uri? = null

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                val bitmap = uriToBitmap(uri)
                if (bitmap != null) {
                    handleImageSelected(bitmap)
                } else {
                    Toast.makeText(this, "Failed to load captured image", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Camera capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val bitmap = uriToBitmap(uri)
                if (bitmap != null) {
                    handleImageSelected(bitmap)
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_verification)

        // Handle edge-to-edge display
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        initializeService()
        setupClickListeners()
    }

    private fun initializeViews() {
        btnCamera = findViewById(R.id.btn_camera)
        btnGallery = findViewById(R.id.btn_gallery)
        btnAnalyze = findViewById(R.id.btn_analyze)
        imgPreview = findViewById(R.id.img_preview)
        previewCard = findViewById(R.id.preview_card)
        progressContainer = findViewById(R.id.progress_card)
        progressText = findViewById(R.id.progress_text)
        tvResultsTitle = findViewById(R.id.tv_results_title)
        resultsRecyclerView = findViewById(R.id.results_recycler_view)
        spinnerDocumentType = findViewById(R.id.spinner_document_type)

        // Setup spinner
        val documentTypes = arrayOf("PAN Card", "Driving License", "Voter ID", "Passport")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, documentTypes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDocumentType.adapter = spinnerAdapter
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Document Verification"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupRecyclerView() {
        adapter = DocumentResultAdapter()
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = adapter
    }

    private fun initializeService() {
        try {
            documentService = DocumentVerificationService()
            documentService.initializeService(this)
            Log.d(TAG, "Document verification service initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service", e)
            Toast.makeText(this, "Service initialization failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClickListeners() {
        btnCamera.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            galleryLauncher.launch(intent)
        }

        btnAnalyze.setOnClickListener {
            currentBitmap?.let { bitmap ->
                analyzeDocument(bitmap)
            }
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, launch camera
                launchCamera()
            }
            else -> {
                // Request camera permission
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        try {
            Log.d(TAG, "Launching camera...")

            // Create a temporary file to store the photo
            val photoFile = File.createTempFile(
                "document_${System.currentTimeMillis()}",
                ".jpg",
                cacheDir
            )
            Log.d(TAG, "Photo file created: ${photoFile.absolutePath}")

            // Get URI using FileProvider
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            currentPhotoUri = photoUri
            Log.d(TAG, "Photo URI: $photoUri")

            // Launch camera with the URI
            cameraLauncher.launch(photoUri)
            Log.d(TAG, "Camera launcher called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera", e)
            Log.e(TAG, "Error details: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Failed to launch camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleImageSelected(bitmap: Bitmap) {
        currentBitmap = bitmap
        imgPreview.setImageBitmap(bitmap)
        previewCard.visibility = View.VISIBLE
        btnAnalyze.visibility = View.VISIBLE
    }

    private fun analyzeDocument(bitmap: Bitmap) {
        // Get selected document type from spinner
        val selectedDocType = when (spinnerDocumentType.selectedItemPosition) {
            0 -> DocumentType.PAN
            1 -> DocumentType.DRIVING_LICENSE
            2 -> DocumentType.VOTER_ID
            3 -> DocumentType.PASSPORT
            else -> DocumentType.PAN
        }

        Log.d(TAG, "Analyzing document as: ${selectedDocType.name}")
        showProgress("Analyzing ${selectedDocType.name}...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val imageBytes = bitmapToByteArray(bitmap)
                    documentService.analyzeDocument(
                        context = this@DocumentVerificationActivity,
                        imageBytes = imageBytes,
                        expectedType = selectedDocType
                    )
                }

                hideProgress()

                if (result.isSuccess) {
                    val analysisResult = result.getOrThrow()
                    Log.d(TAG, "Analysis successful: ${analysisResult.documentType}")

                    // Add result to RecyclerView
                    adapter.addResult(analysisResult, bitmap)
                    tvResultsTitle.visibility = View.VISIBLE

                    // Show success toast
                    val statusText = when (analysisResult.prediction) {
                        com.justdial.ocr.documentverification.model.DocumentStatus.PASS -> "✅ Document PASSED"
                        com.justdial.ocr.documentverification.model.DocumentStatus.FLAGGED -> "⚠️ Document FLAGGED"
                        com.justdial.ocr.documentverification.model.DocumentStatus.FAIL -> "❌ Document FAILED"
                    }
                    Toast.makeText(this@DocumentVerificationActivity, statusText, Toast.LENGTH_LONG).show()

                    // Clear preview for next document
                    previewCard.visibility = View.GONE
                    btnAnalyze.visibility = View.GONE
                    currentBitmap = null
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Analysis failed", error)
                    Toast.makeText(
                        this@DocumentVerificationActivity,
                        "Analysis failed: ${error?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                hideProgress()
                Log.e(TAG, "Exception during analysis", e)
                Toast.makeText(
                    this@DocumentVerificationActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showProgress(message: String) {
        progressText.text = message
        progressContainer.visibility = View.VISIBLE
        btnAnalyze.isEnabled = false
        btnCamera.isEnabled = false
        btnGallery.isEnabled = false
    }

    private fun hideProgress() {
        progressContainer.visibility = View.GONE
        btnAnalyze.isEnabled = true
        btnCamera.isEnabled = true
        btnGallery.isEnabled = true
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert URI to bitmap", e)
            null
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    companion object {
        private const val TAG = "DocumentVerification"
    }
}