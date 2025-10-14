package com.justdial.ocr.documentverification.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import kotlin.math.max
import kotlin.math.min

class FirebaseDocumentAIService {
    companion object {
        private const val TAG = "FirebaseDocumentAI"
        private const val REGION = "asia-south1"

        // Image normalization constants for consistency
        private const val MAX_IMAGE_DIMENSION = 1536  // Consistent max dimension
        private const val MIN_IMAGE_DIMENSION = 768   // Minimum quality threshold
        private const val TARGET_ASPECT_RATIO_TOLERANCE = 0.15f  // 15% tolerance for aspect ratio

        // ✅ CONFIDENCE THRESHOLDS for decision making
        private const val CONFIDENCE_HIGH_THRESHOLD = 0.85f     // >= 85%: High confidence
        private const val CONFIDENCE_MEDIUM_THRESHOLD = 0.60f   // 60-85%: Medium confidence
        private const val CONFIDENCE_LOW_THRESHOLD = 0.40f      // 40-60%: Low confidence
        // < 40%: Very low confidence

        // ✅ ELA TAMPERING SCORE THRESHOLDS
        private const val ELA_SAFE_THRESHOLD = 35f         // <= 35: Safe
        private const val ELA_SUSPICIOUS_THRESHOLD = 50f   // 35-50: Suspicious
        // > 50: High tampering risk

        // ✅ FRAUD INDICATOR THRESHOLDS
        private const val FRAUD_MINOR_THRESHOLD = 1        // 1 indicator: Minor concern
        private const val FRAUD_MAJOR_THRESHOLD = 2        // 2+ indicators: Major concern
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
                Log.d(TAG, "Original image size: ${imageBytes.size} bytes")

                val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    imageBytes, 0, imageBytes.size
                ) ?: throw Exception("Failed to decode image")

                Log.d(TAG, "Original bitmap dimensions: ${bitmap.width}x${bitmap.height}")

                // ✅ CONSISTENCY FIX: Normalize image dimensions and quality
                val normalizedBitmap = normalizeImageForConsistency(bitmap)
                Log.d(TAG, "Normalized bitmap dimensions: ${normalizedBitmap.width}x${normalizedBitmap.height}")

                val response = generateContent(prompt, normalizedBitmap, useFlexibleModel)
                val result = parseJsonToDocumentResult(response)

                // Clean up if we created a new bitmap
                if (normalizedBitmap != bitmap) {
                    normalizedBitmap.recycle()
                }

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

            val confidence = jsonObject.optDouble("confidence", 0.0).toFloat()
            val elaTamperingScore = jsonObject.optDouble("ela_tampering_score", 0.0).toFloat()

            // ✅ Calculate review decision based on all risk factors
            val reviewDecision = calculateReviewDecision(
                prediction = prediction,
                confidence = confidence,
                elaTamperingScore = elaTamperingScore,
                fraudIndicators = fraudIndicators,
                documentType = documentType
            )

            Log.d(TAG, "Review Decision: ${reviewDecision.recommendation} (Risk: ${reviewDecision.riskScore}, Auto-processable: ${reviewDecision.autoProcessable})")

            return DocumentAnalysisResult(
                imageUrl = "",
                documentType = documentType,
                prediction = prediction,
                reason = jsonObject.optString("reason", ""),
                elaTamperingScore = elaTamperingScore,
                fraudIndicators = fraudIndicators,
                extractedFields = emptyMap(),
                confidence = confidence,
                timestamp = System.currentTimeMillis(),
                personalInfo = personalInfo,
                reviewDecision = reviewDecision
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

    /**
     * Normalize image for consistent AI processing across different sources (camera, gallery).
     *
     * This improves consistency by:
     * 1. Standardizing image dimensions
     * 2. Maintaining aspect ratio
     * 3. Using consistent quality settings
     * 4. Ensuring minimum quality threshold
     *
     * @param bitmap Original bitmap from camera or gallery
     * @return Normalized bitmap with consistent dimensions
     */
    private fun normalizeImageForConsistency(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val originalAspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

        Log.d(TAG, "Input: ${originalWidth}x${originalHeight}, aspect ratio: $originalAspectRatio")

        // Calculate target dimensions maintaining aspect ratio
        val longerSide = max(originalWidth, originalHeight)
        val shorterSide = min(originalWidth, originalHeight)

        // If image is already within acceptable range, return as-is
        if (longerSide <= MAX_IMAGE_DIMENSION && shorterSide >= MIN_IMAGE_DIMENSION) {
            Log.d(TAG, "Image already within optimal dimensions, using original")
            return bitmap
        }

        // Calculate scale factor to fit within MAX_IMAGE_DIMENSION
        val scaleFactor = if (longerSide > MAX_IMAGE_DIMENSION) {
            MAX_IMAGE_DIMENSION.toFloat() / longerSide.toFloat()
        } else {
            // Upscale if image is too small (below MIN_IMAGE_DIMENSION)
            MIN_IMAGE_DIMENSION.toFloat() / shorterSide.toFloat()
        }

        val targetWidth = (originalWidth * scaleFactor).toInt()
        val targetHeight = (originalHeight * scaleFactor).toInt()

        Log.d(TAG, "Scaling with factor $scaleFactor to ${targetWidth}x${targetHeight}")

        // Use high-quality filtering for consistent results
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Calculate review decision based on confidence, tampering score, and fraud indicators.
     *
     * Decision Logic:
     * - AUTO_ACCEPT: High confidence (>=85%), low ELA (<=35), PASS status, 0 fraud indicators
     * - AUTO_REJECT: FAIL status or very low confidence (<40%) or critical fraud (3+ indicators)
     * - MANUAL_REVIEW_REQUIRED: Low confidence (40-60%) or suspicious ELA (>50) or 2+ fraud indicators
     * - MANUAL_REVIEW_RECOMMENDED: Medium confidence (60-85%) or moderate ELA (35-50) or 1 fraud indicator
     *
     * @return ReviewDecision with recommendation, reason, risk score, and auto-processable flag
     */
    private fun calculateReviewDecision(
        prediction: DocumentStatus,
        confidence: Float,
        elaTamperingScore: Float,
        fraudIndicators: List<String>,
        documentType: DocumentType
    ): ReviewDecision {
        val fraudCount = fraudIndicators.size

        // Calculate composite risk score (0-100, higher = more risky)
        val confidenceRisk = (1.0f - confidence) * 40f  // Max 40 points
        val elaRisk = (elaTamperingScore / 100f) * 40f  // Max 40 points
        val fraudRisk = min(fraudCount * 10f, 20f)      // Max 20 points (2+ indicators)
        val riskScore = min(confidenceRisk + elaRisk + fraudRisk, 100f)

        Log.d(TAG, "Risk calculation: confidence=$confidence (risk=${confidenceRisk}), ELA=$elaTamperingScore (risk=${elaRisk}), frauds=$fraudCount (risk=${fraudRisk})")

        // Decision tree based on multiple factors
        val (recommendation, reason, autoProcessable) = when {
            // ❌ AUTO_REJECT: Critical failures
            prediction == DocumentStatus.FAIL -> Triple(
                ReviewRecommendation.AUTO_REJECT,
                "Document failed fraud detection",
                false
            )

            confidence < CONFIDENCE_LOW_THRESHOLD -> Triple(
                ReviewRecommendation.AUTO_REJECT,
                "Very low confidence (${(confidence * 100).toInt()}%)",
                false
            )

            fraudCount >= 3 -> Triple(
                ReviewRecommendation.AUTO_REJECT,
                "Critical fraud indicators detected ($fraudCount issues)",
                false
            )

            documentType == DocumentType.UNKNOWN -> Triple(
                ReviewRecommendation.AUTO_REJECT,
                "Unable to identify document type",
                false
            )

            // 🚨 MANUAL_REVIEW_REQUIRED: High risk, must review
            elaTamperingScore > ELA_SUSPICIOUS_THRESHOLD -> Triple(
                ReviewRecommendation.MANUAL_REVIEW_REQUIRED,
                "High tampering risk detected (ELA score: ${elaTamperingScore.toInt()})",
                false
            )

            fraudCount >= FRAUD_MAJOR_THRESHOLD -> Triple(
                ReviewRecommendation.MANUAL_REVIEW_REQUIRED,
                "$fraudCount fraud indicators require verification",
                false
            )

            confidence < CONFIDENCE_MEDIUM_THRESHOLD -> Triple(
                ReviewRecommendation.MANUAL_REVIEW_REQUIRED,
                "Low confidence (${(confidence * 100).toInt()}%) requires review",
                false
            )

            // ⚠️ MANUAL_REVIEW_RECOMMENDED: Medium risk, review advised
            prediction == DocumentStatus.FLAGGED -> Triple(
                ReviewRecommendation.MANUAL_REVIEW_RECOMMENDED,
                "Document flagged for potential issues",
                false
            )

            elaTamperingScore > ELA_SAFE_THRESHOLD -> Triple(
                ReviewRecommendation.MANUAL_REVIEW_RECOMMENDED,
                "Moderate tampering score (${elaTamperingScore.toInt()}), review recommended",
                false
            )

            fraudCount == FRAUD_MINOR_THRESHOLD -> Triple(
                ReviewRecommendation.MANUAL_REVIEW_RECOMMENDED,
                "1 fraud indicator detected: ${fraudIndicators.firstOrNull() ?: ""}",
                false
            )

            confidence < CONFIDENCE_HIGH_THRESHOLD -> Triple(
                ReviewRecommendation.MANUAL_REVIEW_RECOMMENDED,
                "Medium confidence (${(confidence * 100).toInt()}%), review advised",
                false
            )

            // ✅ AUTO_ACCEPT: Low risk, high confidence
            else -> Triple(
                ReviewRecommendation.AUTO_ACCEPT,
                "High confidence (${(confidence * 100).toInt()}%), no fraud indicators",
                true
            )
        }

        return ReviewDecision(
            recommendation = recommendation,
            reason = reason,
            riskScore = riskScore,
            autoProcessable = autoProcessable
        )
    }
}