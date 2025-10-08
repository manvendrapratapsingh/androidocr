package com.justdial.ocr.documentverification.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.justdial.ocr.R
import com.justdial.ocr.documentverification.service.DocumentVerificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class DocumentVerificationActivity : AppCompatActivity() {

    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button
    private lateinit var btnAnalyze: Button
    private lateinit var imgPreview: ImageView
    private lateinit var progressContainer: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var tvResultsTitle: TextView
    private lateinit var resultsRecyclerView: RecyclerView

    private lateinit var documentService: DocumentVerificationService
    private lateinit var adapter: DocumentResultAdapter
    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>

    private var currentBitmap: Bitmap? = null

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

        scannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            handleScannerResult(result)
        }

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
        progressContainer = findViewById(R.id.progress_container)
        progressText = findViewById(R.id.progress_text)
        tvResultsTitle = findViewById(R.id.tv_results_title)
        resultsRecyclerView = findViewById(R.id.results_recycler_view)
    }

    private fun setupToolbar() {
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
            launchDocumentScanner()
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

    private fun launchDocumentScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender: IntentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Failed to start document scanner", e)
                Toast.makeText(this, "Failed to start scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleScannerResult(activityResult: ActivityResult) {
        val resultCode = activityResult.resultCode
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)

        if (resultCode == Activity.RESULT_OK && result != null) {
            val pages = result.pages
            if (pages != null && pages.isNotEmpty()) {
                val imageUri = pages[0].imageUri
                val bitmap = uriToBitmap(imageUri)
                if (bitmap != null) {
                    handleImageSelected(bitmap)
                } else {
                    Toast.makeText(this, "Failed to load scanned image", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Scan failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImageSelected(bitmap: Bitmap) {
        currentBitmap = bitmap
        imgPreview.setImageBitmap(bitmap)
        imgPreview.visibility = View.VISIBLE
        btnAnalyze.visibility = View.VISIBLE
    }

    private fun analyzeDocument(bitmap: Bitmap) {
        showProgress("Analyzing document...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val imageBytes = bitmapToByteArray(bitmap)
                    documentService.analyzeDocument(
                        context = this@DocumentVerificationActivity,
                        imageBytes = imageBytes,
                        expectedType = null // Auto-detect
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
                    imgPreview.visibility = View.GONE
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