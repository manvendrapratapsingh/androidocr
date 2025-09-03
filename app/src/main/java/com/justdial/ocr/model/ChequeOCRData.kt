package com.justdial.ocr.model

import kotlinx.serialization.Serializable

@Serializable
data class ChequeOCRData(
    val bankName: String = "",
    val branchAddress: String = "",
    val ifscCode: String = "",
    val accountHolderName: String = "",
    val accountNumber: String = "",
    val chequeNumber: String = "",
    val micrCode: String = "",
    val date: String = "",
    val amountInWords: String = "",
    val amountInNumbers: String = "",
    val payToName: String = "",
    val signaturePresent: Boolean = false,
    val authorizationPresent: Boolean = false,
    val document_quality: String="",
    val document_type: String="",
    // Quality and confidence metrics
    val processingConfidence: Float = 0.0f,
    val imageQuality: String = "",
    val extractionErrors: List<String> = emptyList()
)

@Serializable
data class ChequeValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList(),
    val correctedData: ChequeOCRData? = null
)

@Serializable
data class ValidationError(
    val field: String,
    val errorType: ErrorType,
    val message: String,
    val suggestedFix: String? = null
)

@Serializable
data class ValidationWarning(
    val field: String,
    val warningType: WarningType,
    val message: String
)

@Serializable
enum class ErrorType {
    MISSING_FIELD,
    INVALID_FORMAT,
    INVALID_CHECKSUM,
    INCONSISTENT_DATA,
    POOR_IMAGE_QUALITY
}

@Serializable
enum class WarningType {
    LOW_CONFIDENCE,
    UNCLEAR_TEXT,
    PARTIAL_OCCLUSION,
    UNUSUAL_FORMAT
}