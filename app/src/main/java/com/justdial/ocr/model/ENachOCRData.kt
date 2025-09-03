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