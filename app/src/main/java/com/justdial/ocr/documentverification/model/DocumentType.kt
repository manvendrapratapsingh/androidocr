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
    val personalInfo: PersonalInfo? = null
)

@Serializable
data class DocumentAnalysisRequest(
    val imageBase64: String,
    val documentType: DocumentType? = null // null for auto-detection
)