package com.justdial.ocr.model

import kotlinx.serialization.Serializable

@Serializable
data class ENachOCRData(
    val utilityName: String = "",
    val utilityCode: String = "",
    val customerRefNumber: String = "",
    val accountHolderName: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val micr_code: String = "",
    val accountType: String = "",
    val maxAmount: String = "",
    val frequency: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val primaryAccountRef: String = "",
    val sponsorBankName: String = "",
    val umrn: String = "", // Unique Mandate Reference Number
    val mandateType: String = "",
    val authMode: String = "",
    val customerSignature: Boolean = false,
    val dateOfMandate: String = "",

    val document_quality: String="",
    val document_type: String="",
    val fraud_indicators: List<String> = emptyList(),
    // Enhanced signature verification fields
    val rotation_applied: Int = 0,
    val signature_count: Int = 0,
    val signatures_consistent: Boolean? = null,
    val signatures_match_score: Int = 0,
    val signatures_notes: String = "",
    val payer_signatures_match: Boolean? = null,
    val sponsor_signatures_match: Boolean? = null,
    val payer_match_score: Int = 0,
    val sponsor_match_score: Int = 0,
    val signature_regions: List<SignatureRegion> = emptyList(),
    val signature_count_payer: Int = 0,
    val signature_count_sponsor: Int = 0,
    val signature_count_unknown: Int = 0,
    val expected_signatures: ExpectedSignatures = ExpectedSignatures(),
    val missing_expected_signatures: List<String> = emptyList(),

    // Quality and confidence metrics
    val processingConfidence: Float = 0.0f,
    val imageQuality: String = "",
    val extractionErrors: List<String> = emptyList()
)

@Serializable
data class ENachValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList(),
    val correctedData: ENachOCRData? = null
)

@Serializable
data class DocumentCrossValidationResult(
    val chequeData: ChequeOCRData,
    val enachData: ENachOCRData,
    val crossValidationPassed: Boolean,
    val matchingFields: List<FieldMatch>,
    val conflicts: List<DataConflict>,
    val overallScore: Float
)

@Serializable
data class FieldMatch(
    val fieldName: String,
    val chequeValue: String,
    val enachValue: String,
    val isMatch: Boolean,
    val confidence: Float
)

@Serializable
data class DataConflict(
    val fieldName: String,
    val chequeValue: String,
    val enachValue: String,
    val severity: ConflictSeverity,
    val description: String
)

@Serializable
enum class ConflictSeverity {
    CRITICAL,    // Must be resolved before processing
    HIGH,        // Strong recommendation to resolve
    MEDIUM,      // Review recommended
    LOW          // Minor discrepancy
}