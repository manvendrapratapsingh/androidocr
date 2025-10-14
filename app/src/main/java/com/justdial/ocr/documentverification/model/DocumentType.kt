package com.justdial.ocr.documentverification.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentType {
    PAN,
    DRIVING_LICENSE,
    VOTER_ID,
    PASSPORT,
    UNKNOWN
}

@Serializable
enum class DocumentStatus {
    PASS,
    FLAGGED,
    FAIL
}

/**
 * Recommendation for manual review based on confidence and risk factors
 */
@Serializable
enum class ReviewRecommendation {
    /** Auto-accept: High confidence, no fraud indicators, proceed to next step */
    AUTO_ACCEPT,

    /** Manual review recommended: Medium confidence or minor concerns */
    MANUAL_REVIEW_RECOMMENDED,

    /** Manual review required: Low confidence or significant fraud indicators */
    MANUAL_REVIEW_REQUIRED,

    /** Auto-reject: Clear fraud or very low confidence */
    AUTO_REJECT
}

/**
 * Detailed reasoning for review recommendation
 */
@Serializable
data class ReviewDecision(
    val recommendation: ReviewRecommendation,
    val reason: String,
    val riskScore: Float,  // 0-100: Higher = more risky
    val autoProcessable: Boolean  // Can proceed without human review
)

@Serializable
data class PersonalInfo(
    val name: String? = null,
    val idNumber: String? = null,
    val dob: String? = null
)

@Serializable
data class DocumentAnalysisResult(
    val imageUrl: String = "",
    val documentType: DocumentType = DocumentType.UNKNOWN,
    val prediction: DocumentStatus = DocumentStatus.FAIL,
    val reason: String = "",
    val elaTamperingScore: Float = 0.0f,
    val fraudIndicators: List<String> = emptyList(),
    val extractedFields: Map<String, String> = emptyMap(),
    val confidence: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val personalInfo: PersonalInfo? = null,
    val reviewDecision: ReviewDecision? = null  // NEW: Decision for manual review
)

@Serializable
data class DocumentAnalysisRequest(
    val imageBase64: String,
    val documentType: DocumentType? = null // null for auto-detection
)