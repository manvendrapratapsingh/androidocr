package com.justdial.ocr.documentverification.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.firebase.app
import com.justdial.ocr.documentverification.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseDocumentAIService {
    companion object {
        private const val TAG = "FirebaseDocumentAI"
        private const val REGION = "asia-south1"
    }

    private lateinit var modelStrict: GenerativeModel
    private lateinit var modelFlexible: GenerativeModel
    private var isInitialized = false

    fun initializeService(
        context: Context,
        thinkingBudget: Int = 2048
    ) {
        try {
            Log.d(TAG, "Initializing Firebase AI for Document Verification")
            Log.d(TAG, "Region: $REGION (India compliance)")
            Log.d(TAG, "Thinking budget: $thinkingBudget (higher for better analysis)")

            val app = Firebase.app
            Log.d(TAG, "Firebase app initialized: ${app.name}")

            // Strict config for PAN/DL/Voter ID (deterministic, consistent)
            val strictConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    this.thinkingBudget = thinkingBudget
                }
                temperature = 0.0f // Lower temperature for more deterministic/strict responses
                topP = 1.0f
                topK = 1
                responseMimeType = "application/json"
            }

            // Flexible config for Passport (better at detecting anime/illustrations)
            val flexibleConfig = generationConfig {
                /*thinkingConfig = thinkingConfig {
                    this.thinkingBudget = thinkingBudget
                }*/
                temperature = 0.0f // Higher temperature for better pattern recognition
                topP = 1.0f        // More diverse sampling
                topK = 1         // Wider token selection
                responseMimeType = "application/json"
            }

            val backend = GenerativeBackend.vertexAI(location = REGION)

            modelStrict = Firebase.ai(backend = backend).generativeModel(
                modelName = "gemini-2.5-flash",
                generationConfig = strictConfig
            )

            modelFlexible = Firebase.ai(backend = backend).generativeModel(
                modelName = "gemini-2.5-flash",
                generationConfig = flexibleConfig
            )

            isInitialized = true
            Log.d(TAG, "✅ Firebase Document AI initialized successfully (strict + flexible models)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Document AI", e)
            throw e
        }
    }

    fun isServiceInitialized(): Boolean = isInitialized

    suspend fun analyzeDocument(
        context: Context,
        imageBytes: ByteArray,
        prompt: String,
        useFlexibleModel: Boolean = false
    ): Result<DocumentAnalysisResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    throw Exception("Firebase Document AI not initialized")
                }

                Log.d(TAG, "Starting document analysis (${if (useFlexibleModel) "FLEXIBLE" else "STRICT"} model)")
                Log.d(TAG, "Image size: ${imageBytes.size} bytes")

                val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    imageBytes, 0, imageBytes.size
                ) ?: throw Exception("Failed to decode image")

                val response = generateContent(prompt, bitmap, useFlexibleModel)
                val result = parseJsonToDocumentResult(response)

                Log.d(TAG, "Document analysis completed")
                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Document analysis failed", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun generateContent(
        prompt: String,
        imageBitmap: Bitmap,
        useFlexibleModel: Boolean = false
    ): String {
        return try {
            Log.d(TAG, "Sending request to Gemini API (${if (useFlexibleModel) "FLEXIBLE" else "STRICT"} config)")

            val inputContent = content {
                text(prompt)
                image(imageBitmap)
            }

            val selectedModel = if (useFlexibleModel) modelFlexible else modelStrict
            val response = selectedModel.generateContent(inputContent)
            val text = response.text ?: throw Exception("No response from AI")

            Log.d(TAG, "AI Response received: ${text.length} characters")
            Log.d(TAG, "RAW RESPONSE: $text")

            text
        } catch (e: Exception) {
            Log.e(TAG, "AI Processing Error", e)
            throw e
        }
    }

    private fun parseJsonToDocumentResult(jsonString: String): DocumentAnalysisResult {
        try {
            val cleanJson = extractJsonFromResponse(jsonString)
            Log.d(TAG, "Parsing JSON: $cleanJson")

            val jsonObject = org.json.JSONObject(cleanJson)

            // Parse document type
            val docTypeStr = jsonObject.optString("document_type", "UNKNOWN")
            val documentType = when (docTypeStr.uppercase()) {
                "PAN" -> DocumentType.PAN
                "DRIVING_LICENSE", "DL" -> DocumentType.DRIVING_LICENSE
                "VOTER_ID", "VOTERID" -> DocumentType.VOTER_ID
                "PASSPORT" -> DocumentType.PASSPORT
                else -> DocumentType.UNKNOWN
            }

            // Parse prediction status
            val predictionStr = jsonObject.optString("prediction", "FAIL")
            val prediction = when (predictionStr.uppercase()) {
                "PASS" -> DocumentStatus.PASS
                "FLAGGED" -> DocumentStatus.FLAGGED
                else -> DocumentStatus.FAIL
            }

            // Parse fraud indicators
            val fraudArray = jsonObject.optJSONArray("fraud_indicators")
            val fraudIndicators = mutableListOf<String>()
            if (fraudArray != null) {
                for (i in 0 until fraudArray.length()) {
                    fraudArray.optString(i)?.let { fraudIndicators.add(it) }
                }
            }

            // Parse personal_info
            val personalInfo = if (jsonObject.has("personal_info")) {
                val personalInfoObj = jsonObject.getJSONObject("personal_info")
                PersonalInfo(
                    name = personalInfoObj.optString("name").takeIf { it.isNotEmpty() && it != "null" },
                    idNumber = personalInfoObj.optString("id_number").takeIf { it.isNotEmpty() && it != "null" },
                    dob = personalInfoObj.optString("dob").takeIf { it.isNotEmpty() && it != "null" }
                )
            } else {
                null
            }

            return DocumentAnalysisResult(
                imageUrl = "",
                documentType = documentType,
                prediction = prediction,
                reason = jsonObject.optString("reason", ""),
                elaTamperingScore = jsonObject.optDouble("ela_tampering_score", 0.0).toFloat(),
                fraudIndicators = fraudIndicators,
                extractedFields = emptyMap(),
                confidence = jsonObject.optDouble("confidence", 0.0).toFloat(),
                timestamp = System.currentTimeMillis(),
                personalInfo = personalInfo
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON", e)
            // Return a failed result with error info
            return DocumentAnalysisResult(
                documentType = DocumentType.UNKNOWN,
                prediction = DocumentStatus.FAIL,
                reason = "JSON parsing error: ${e.message}",
                elaTamperingScore = 100.0f,
                fraudIndicators = listOf("Failed to parse AI response"),
                confidence = 0.0f
            )
        }
    }

    private fun extractJsonFromResponse(response: String): String {
        // Remove markdown code blocks if present
        var cleaned = response.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").removeSuffix("```").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").removeSuffix("```").trim()
        }

        val jsonStart = cleaned.indexOf("{")
        val jsonEnd = cleaned.lastIndexOf("}")

        return if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            cleaned.substring(jsonStart, jsonEnd + 1)
        } else {
            "{}"
        }
    }
}