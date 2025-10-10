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

    private lateinit var model: GenerativeModel
    private var isInitialized = false

    fun initializeService(
        context: Context,
        thinkingBudget: Int = 1024,
        maxOutputTokens: Int = 1024
    ) {
        try {
            Log.d(TAG, "Initializing Firebase AI for Document Verification")
            Log.d(TAG, "Region: $REGION (India compliance)")
            Log.d(TAG, "Thinking budget: $thinkingBudget, Max output tokens: $maxOutputTokens")

            val app = Firebase.app
            Log.d(TAG, "Firebase app initialized: ${app.name}")

            val genConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    this.thinkingBudget = thinkingBudget
                }
               // this.maxOutputTokens = maxOutputTokens
                responseMimeType = "application/json"
            }

            model = Firebase.ai(backend = GenerativeBackend.vertexAI(
                location = REGION
            )).generativeModel(
                modelName = "gemini-2.5-flash",
                generationConfig = genConfig
            )

            isInitialized = true
            Log.d(TAG, "✅ Firebase Document AI initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Document AI", e)
            throw e
        }
    }

    fun isServiceInitialized(): Boolean = isInitialized

    suspend fun analyzeDocument(
        context: Context,
        imageBytes: ByteArray,
        prompt: String
    ): Result<DocumentAnalysisResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    throw Exception("Firebase Document AI not initialized")
                }

                Log.d(TAG, "Starting document analysis")
                Log.d(TAG, "Image size: ${imageBytes.size} bytes")

                val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    imageBytes, 0, imageBytes.size
                ) ?: throw Exception("Failed to decode image")

                val response = generateContent(prompt, bitmap)
                val result = parseJsonToDocumentResult(response)

                Log.d(TAG, "Document analysis completed")
                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Document analysis failed", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun generateContent(prompt: String, imageBitmap: Bitmap): String {
        return try {
            Log.d(TAG, "Sending request to Gemini API")

            val inputContent = content {
                text(prompt)
                image(imageBitmap)
            }

            val response = model.generateContent(inputContent)
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

            return DocumentAnalysisResult(
                imageUrl = "",
                documentType = documentType,
                prediction = prediction,
                reason = jsonObject.optString("reason", ""),
                elaTamperingScore = jsonObject.optDouble("ela_tampering_score", 0.0).toFloat(),
                fraudIndicators = fraudIndicators,
                extractedFields = emptyMap(),
                confidence = jsonObject.optDouble("confidence", 0.0).toFloat(),
                timestamp = System.currentTimeMillis()
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