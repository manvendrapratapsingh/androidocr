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
    val fraudIndicators: List<String> = emptyList(),
    // Enhanced signature verification fields
    val rotationApplied: Int = 0,
    val signatureCount: Int = 0,
    val signaturesConsistent: Boolean? = null,
    val signaturesMatchScore: Int = 0,
    val signaturesNotes: String = "",
    val signatureRegions: List<SignatureRegion> = emptyList(),
    val signatureCountPayer: Int = 0,
    val signatureCountSponsor: Int = 0,
    val signatureCountUnknown: Int = 0,
    val expectedSignatures: ExpectedSignatures = ExpectedSignatures(),
    val missingExpectedSignatures: List<String> = emptyList(),
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

@Serializable
data class SignatureRegion(
    val bbox: BoundingBox,
    val group: SignatureGroup = SignatureGroup.UNKNOWN,
    val anchorText: String = "",
    val evidence: String = ""
)

@Serializable
data class BoundingBox(
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val w: Float = 0.0f,
    val h: Float = 0.0f
)

@Serializable
enum class SignatureGroup {
    PAYER,
    SPONSOR,
    UNKNOWN
}

@Serializable
data class ExpectedSignatures(
    val payer: Int = 0,
    val sponsor: Int = 0
)
