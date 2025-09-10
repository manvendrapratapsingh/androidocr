package com.justdial.ocr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.justdial.ocr.service.DocumentProcessorService
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OCRResultActivity : AppCompatActivity() {

    private lateinit var documentProcessorService: DocumentProcessorService

    companion object {
        private const val TAG = "OCRResultActivity"
        const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val EXTRA_IMAGE_PATH = "image_path"
        const val DOCUMENT_TYPE_CHEQUE = "cheque"
        const val DOCUMENT_TYPE_ENACH = "enach"
        const val RESULT_JSON = "result_json"
        const val RESULT_ERROR = "result_error"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "OCRResultActivity started")

        // Initialize the document processor service
        documentProcessorService = DocumentProcessorService()
        documentProcessorService.initializeService(this)

        // Start processing immediately
        startProcessing()
    }

    private fun startProcessing() {
        lifecycleScope.launch {
            try {
                // Get document type and image path from intent
                val documentType = intent.getStringExtra(EXTRA_DOCUMENT_TYPE) ?: DOCUMENT_TYPE_CHEQUE
                val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)

                if (imagePath.isNullOrEmpty()) {
                    // For demo purposes, create mock data
                    processMockData(documentType)
                } else {
                    // Process actual image file
                    processImageFile(imagePath, documentType)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start processing", e)
                returnError("Failed to start processing: ${e.message}")
            }
        }
    }

    private suspend fun processMockData(documentType: String) {
        Log.d(TAG, "No image provided - launching camera to capture image")
        
        // Launch the main camera activity to capture image
        val cameraIntent = Intent(this, MainActivityCamera::class.java).apply {
            putExtra("document_type", documentType)
        }
        startActivityForResult(cameraIntent, 2001)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 2001) { // Camera capture result
            if (resultCode == Activity.RESULT_OK) {
                val jsonResult = data?.getStringExtra("result_json")
                
                if (!jsonResult.isNullOrEmpty()) {
                    Log.d(TAG, "Received OCR result from camera: $jsonResult")
                    returnSuccess(jsonResult)
                } else {
                    returnError("No OCR result received from camera")
                }
            } else {
                val errorMessage = data?.getStringExtra("result_error") ?: "Camera processing cancelled or failed"
                returnError(errorMessage)
            }
        }
    }

    private suspend fun processImageFile(imagePath: String, documentType: String) {
        try {
            Log.d(TAG, "Processing image file: $imagePath")

            // Read image file and convert to bytes
            val imageBytes = java.io.File(imagePath).readBytes()

            when (documentType) {
                DOCUMENT_TYPE_CHEQUE -> processChequeDocument(imageBytes)
                DOCUMENT_TYPE_ENACH -> processEnachDocument(imageBytes)
                else -> processChequeDocument(imageBytes)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image file", e)
            returnError("Failed to process image file: ${e.message}")
        }
    }

    private suspend fun processChequeDocument(imageBytes: ByteArray) {
        try {
            Log.d(TAG, "Processing cheque document")

            val result = documentProcessorService.processCheque(this, imageBytes)

            if (result.isSuccess) {
                val chequeData = result.getOrThrow()
                val jsonResult = Json.encodeToString(chequeData)

                Log.d(TAG, "Cheque processing successful: $jsonResult")
                returnSuccess(jsonResult)
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Cheque processing failed", error)
                returnError("Cheque processing failed: ${error?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during cheque processing", e)
            returnError("Exception during processing: ${e.message}")
        }
    }

    private suspend fun processEnachDocument(imageBytes: ByteArray) {
        try {
            Log.d(TAG, "Processing E-NACH document")

            val result = documentProcessorService.processENach(this, imageBytes)

            if (result.isSuccess) {
                val enachData = result.getOrThrow()
                val jsonResult = Json.encodeToString(enachData)

                Log.d(TAG, "E-NACH processing successful: $jsonResult")
                returnSuccess(jsonResult)
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "E-NACH processing failed", error)
                returnError("E-NACH processing failed: ${error?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during E-NACH processing", e)
            returnError("Exception during processing: ${e.message}")
        }
    }

    private fun returnSuccess(jsonResult: String) {
        Log.d(TAG, "Returning success result: $jsonResult")
        val resultIntent = Intent().apply {
            putExtra(RESULT_JSON, jsonResult)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun returnError(errorMessage: String) {
        Log.d(TAG, "Returning error result: $errorMessage")
        val resultIntent = Intent().apply {
            putExtra(RESULT_ERROR, errorMessage)
        }
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }
}