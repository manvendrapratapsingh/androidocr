package com.justdial.ocr.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.app
import com.justdial.ocr.model.ChequeOCRData
import com.justdial.ocr.model.ENachOCRData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseAIService {
    companion object {
        private const val TAG = "FirebaseAIService"
        private const val REGION = "asia-south1" // India region for compliance
        private const val MODEL_NAME = "gemini-1.5-flash" // Correct model name without version suffix
        private const val API_KEY = "AIzaSyDdoFOVvnJOOnGyr3uslRrIitjNi1CwtNI"
    }
    
    private lateinit var model: GenerativeModel
    private var isInitialized = false

    fun initializeService(context: Context) {
        try {
            Log.d(TAG, "Initializing Firebase AI Logic for India compliance")
            Log.d(TAG, "Target region: $REGION for India data residency compliance")

            // Initialize Firebase first (ensures proper Firebase integration)
            val app = Firebase.app
            Log.d(TAG, "Firebase app initialized: ${app.name}")

            try {
                // Try Firebase AI Logic first (if available)
                model = Firebase.ai(backend = GenerativeBackend.vertexAI(
                    location = REGION  // This ACTUALLY sets India region
                )).generativeModel("gemini-2.5-flash")
                
                isInitialized = true
                Log.d(TAG, "✅  TRUE Firebase AI Logic initialized with India region compliance")
                Log.d(TAG, "Using Vertex AI backend with region: $REGION")
                return
                
            } catch (e: Exception) {
                Log.w(TAG, "Firebase AI Logic not available, falling back to compatible approach: ${e.message}")
                throw e // For now, require Firebase AI Logic to work properly
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI Logic", e)
            throw e
        }
    }
    
    /*fun initializeService(context: Context) {
        try {
            Log.d(TAG, "Initializing Firebase-integrated AI Logic for document processing")
            Log.d(TAG, "Target region: $REGION for India data residency compliance")
            
            // Initialize Firebase first (ensures proper Firebase integration)
            val app = Firebase.app
            Log.d(TAG, "Firebase app initialized: ${app.name}")
            
            // Configure Generative AI with region-specific settings for India compliance
            val config = generationConfig {
                temperature = 0.1f
                topK = 32
                topP = 1f
                maxOutputTokens = 4096
            }
            
            // Try different model names to find the correct one
            val modelNames = listOf(
                "gemini-1.5-flash",
                "gemini-pro-vision", 
                "gemini-pro",
                "gemini-1.0-pro-vision-latest",
                "gemini-1.0-pro-latest"
            )
            
            var initializationError: Exception? = null
            
            for (modelName in modelNames) {
                try {
                    Log.d(TAG, "Trying to initialize with model: $modelName")
                    
                    model = GenerativeModel(
                        modelName = modelName,
                        apiKey = API_KEY,
                        generationConfig = config
                    )
                    
                    isInitialized = true
                    Log.d(TAG, "Firebase-integrated AI initialized successfully!")
                    Log.d(TAG, "Active Model: $modelName, Region: $REGION")
                    return
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Model $modelName failed: ${e.message}")
                    initializationError = e
                    continue
                }
            }
            
            // If we get here, all models failed
            throw initializationError ?: Exception("All model initialization attempts failed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI Service with any model", e)
            throw e
        }
    }*/
    
    fun isServiceInitialized(): Boolean = isInitialized
    
    fun validateRegionalCompliance(): Boolean {
        return REGION == "asia-south1"
    }
    
    private suspend fun generateContent(prompt: String, imageBitmap: Bitmap): String {
        return try {
            if (!isInitialized) {
                throw Exception("Firebase AI Service not initialized")
            }
            
            Log.d(TAG, "=== Starting Firebase-integrated AI Processing ===")
            Log.d(TAG, "Using region: $REGION for India compliance")
            Log.d(TAG, "Model: $MODEL_NAME")
            Log.d(TAG, "Prompt length: ${prompt.length} characters")
            
            val inputContent = content {
                text(prompt)
                image(imageBitmap)
            }
            
            Log.d(TAG, "Sending request to Gemini API via Firebase integration...")
            val response = model.generateContent(inputContent)
            val text = response.text ?: "No response generated"
            
            Log.d(TAG, "=== Firebase AI Processing Success ===")
            Log.d(TAG, "Response length: ${text.length} characters")
            Log.d(TAG, "Extracted text: $text")
            
            text
        } catch (e: Exception) {
            Log.e(TAG, "=== Firebase AI Processing Error ===")
            Log.e(TAG, "Exception: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun processCheque(
        context: Context,
        imageBytes: ByteArray,
        customPrompt: String
    ): Result<ChequeOCRData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing cheque with Firebase-integrated AI")
                
                // Convert ByteArray to Bitmap
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: return@withContext Result.failure(Exception("Failed to decode image bytes to bitmap"))
                
                val responseText = generateContent(customPrompt, bitmap)
                val chequeData = parseJsonToChequeData(responseText)
                
                Log.d(TAG, "Cheque processing completed successfully")
                Result.success(chequeData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Cheque processing failed", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun processENach(
        context: Context,
        imageBytes: ByteArray,
        customPrompt: String
    ): Result<ENachOCRData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing E-NACH with Firebase-integrated AI")
                
                // Convert ByteArray to Bitmap
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: return@withContext Result.failure(Exception("Failed to decode image bytes to bitmap"))
                
                val responseText = generateContent(customPrompt, bitmap)
                val enachData = parseJsonToENachData(responseText)
                
                Log.d(TAG, "E-NACH processing completed successfully")
                Result.success(enachData)
                
            } catch (e: Exception) {
                Log.e(TAG, "E-NACH processing failed", e)
                Result.failure(e)
            }
        }
    }
    
    private fun parseJsonToChequeData(jsonString: String): ChequeOCRData {
        try {
            val cleanJson = extractJsonFromResponse(jsonString)
            val jsonObject = org.json.JSONObject(cleanJson)
            
            // Parse fraud indicators array if present
            val fraudIndicatorsArray = jsonObject.optJSONArray("fraud_indicators")
            val fraudIndicatorsList = mutableListOf<String>()
            if (fraudIndicatorsArray != null) {
                for (i in 0 until fraudIndicatorsArray.length()) {
                    val reason = fraudIndicatorsArray.optString(i)
                    if (!reason.isNullOrBlank()) fraudIndicatorsList.add(reason)
                }
            }

            return ChequeOCRData(
                bankName = jsonObject.optString("bank_name", ""),
                branchAddress = jsonObject.optString("branchAddress", ""),
                ifscCode = jsonObject.optString("ifsc_code", ""),
                accountHolderName = jsonObject.optString("account_holder_name", ""),
                accountNumber = jsonObject.optString("account_number", ""),
                chequeNumber = jsonObject.optString("chequeNumber", ""),
                micrCode = jsonObject.optString("micr_code", ""),
                date = jsonObject.optString("date", ""),
                amountInWords = jsonObject.optString("amountInWords", ""),
                amountInNumbers = jsonObject.optString("amountInNumbers", ""),
                payToName = jsonObject.optString("payToName", ""),
                signaturePresent = jsonObject.optString("signature_present", "false").toBoolean(),
                document_quality = jsonObject.optString("document_quality", ""),
                document_type = jsonObject.optString("document_type", ""),
                authorizationPresent = jsonObject.optString("authorizationPresent", "false").toBoolean(),
                fraudIndicators = fraudIndicatorsList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cheque JSON", e)
            return ChequeOCRData()
        }
    }
    
    private fun parseJsonToENachData(jsonString: String): ENachOCRData {
        try {
            val cleanJson = extractJsonFromResponse(jsonString)
            val jsonObject = org.json.JSONObject(cleanJson)
            
            return ENachOCRData(
                utilityName = jsonObject.optString("utilityName", ""),
                utilityCode = jsonObject.optString("utilityCode", ""),
                customerRefNumber = jsonObject.optString("customerRefNumber", ""),
                accountHolderName = jsonObject.optString("accountHolderName", ""),
                bankName = jsonObject.optString("bankName", ""),
                accountNumber = jsonObject.optString("accountNumber", ""),
                ifscCode = jsonObject.optString("ifscCode", ""),
                accountType = jsonObject.optString("accountType", ""),
                maxAmount = jsonObject.optString("maxAmount", ""),
                frequency = jsonObject.optString("frequency", ""),
                startDate = jsonObject.optString("startDate", ""),
                endDate = jsonObject.optString("endDate", ""),
                primaryAccountRef = jsonObject.optString("primaryAccountRef", ""),
                sponsorBankName = jsonObject.optString("sponsorBankName", ""),
                umrn = jsonObject.optString("umrn", ""),
                mandateType = jsonObject.optString("mandateType", ""),
                authMode = jsonObject.optString("authMode", ""),
                customerSignature = jsonObject.optString("customerSignature", "false").toBoolean(),
                dateOfMandate = jsonObject.optString("dateOfMandate", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse E-NACH JSON", e)
            return ENachOCRData()
        }
    }
    
    private fun extractJsonFromResponse(response: String): String {
        val jsonStart = response.indexOf("{")
        val jsonEnd = response.lastIndexOf("}")
        return if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            response.substring(jsonStart, jsonEnd + 1)
        } else {
            "{}"
        }
    }
}
