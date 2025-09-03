package com.justdial.ocr.validation

import com.justdial.ocr.model.*
import kotlin.math.abs

class ValidationEngine {
    
    companion object {
        // Indian IFSC code validation pattern
        private val IFSC_PATTERN = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
        
        // MICR code validation pattern (9 digits)
        private val MICR_PATTERN = Regex("^[0-9]{9}$")
        
        // Account number patterns (varies by bank, but generally 9-18 digits)
        private val ACCOUNT_NUMBER_PATTERN = Regex("^[0-9]{9,18}$")
        
        // Cheque number pattern (typically 6-8 digits)
        private val CHEQUE_NUMBER_PATTERN = Regex("^[0-9]{6,8}$")
        
        // UMRN pattern for E-NACH (varies but typically 20 characters)
        private val UMRN_PATTERN = Regex("^[A-Z0-9]{15,25}$")
        
        // Indian bank IFSC to bank name mapping (sample)
        private val IFSC_BANK_MAPPING = mapOf(
            "SBIN" to "State Bank of India",
            "HDFC" to "HDFC Bank",
            "ICIC" to "ICICI Bank",
            "AXIS" to "Axis Bank",
            "PUNB" to "Punjab National Bank",
            "CNRB" to "Canara Bank",
            "UBIN" to "Union Bank of India",
            "BARB" to "Bank of Baroda",
            "MAHB" to "Bank of Maharashtra",
            "INDB" to "Indian Bank"
        )
        
        // Common validation thresholds
        private const val MIN_CONFIDENCE_THRESHOLD = 0.7f
        private const val NAME_SIMILARITY_THRESHOLD = 0.8f
    }
    
    /**
     * Validate cheque data and return corrections if possible
     */
    fun validateCheque(chequeData: ChequeOCRData): ChequeValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        var correctedData = chequeData.copy()
        
        // Validate IFSC Code
        validateAndCorrectIFSC(chequeData.ifscCode)?.let { ifscResult ->
            if (ifscResult.isValid) {
                correctedData = correctedData.copy(ifscCode = ifscResult.correctedValue)
                if (ifscResult.correctedValue != chequeData.ifscCode) {
                    warnings.add(ValidationWarning(
                        field = "ifscCode",
                        warningType = WarningType.UNCLEAR_TEXT,
                        message = "IFSC code auto-corrected from ${chequeData.ifscCode} to ${ifscResult.correctedValue}"
                    ))
                }
            } else {
                errors.add(ValidationError(
                    field = "ifscCode",
                    errorType = ErrorType.INVALID_FORMAT,
                    message = "Invalid IFSC code format: ${chequeData.ifscCode}",
                    suggestedFix = "IFSC should be 11 characters: 4 letters, then 0, then 6 alphanumeric"
                ))
            }
        }
        
        // Validate MICR Code
        if (chequeData.micrCode.isNotBlank() && chequeData.micrCode != "NOT_FOUND") {
            if (!MICR_PATTERN.matches(chequeData.micrCode)) {
                errors.add(ValidationError(
                    field = "micrCode",
                    errorType = ErrorType.INVALID_FORMAT,
                    message = "Invalid MICR code format: ${chequeData.micrCode}",
                    suggestedFix = "MICR code should be exactly 9 digits"
                ))
            }
        }
        
        // Validate Account Number
        if (chequeData.accountNumber.isNotBlank() && chequeData.accountNumber != "NOT_FOUND") {
            if (!ACCOUNT_NUMBER_PATTERN.matches(chequeData.accountNumber)) {
                warnings.add(ValidationWarning(
                    field = "accountNumber",
                    warningType = WarningType.UNUSUAL_FORMAT,
                    message = "Account number format may be unusual: ${chequeData.accountNumber}"
                ))
            }
        }
        
        // Validate Cheque Number
        if (chequeData.chequeNumber.isNotBlank() && chequeData.chequeNumber != "NOT_FOUND") {
            if (!CHEQUE_NUMBER_PATTERN.matches(chequeData.chequeNumber)) {
                warnings.add(ValidationWarning(
                    field = "chequeNumber",
                    warningType = WarningType.UNUSUAL_FORMAT,
                    message = "Cheque number format may be unusual: ${chequeData.chequeNumber}"
                ))
            }
        }
        
        // Check for required fields
        if (chequeData.accountHolderName.isBlank() || chequeData.accountHolderName == "NOT_FOUND") {
            errors.add(ValidationError(
                field = "accountHolderName",
                errorType = ErrorType.MISSING_FIELD,
                message = "Account holder name is required"
            ))
        }
        
        if (chequeData.bankName.isBlank() || chequeData.bankName == "NOT_FOUND") {
            errors.add(ValidationError(
                field = "bankName",
                errorType = ErrorType.MISSING_FIELD,
                message = "Bank name is required"
            ))
        }
        
        // Validate confidence levels
        if (chequeData.processingConfidence < MIN_CONFIDENCE_THRESHOLD) {
            warnings.add(ValidationWarning(
                field = "overall",
                warningType = WarningType.LOW_CONFIDENCE,
                message = "Low OCR confidence: ${chequeData.processingConfidence}. Manual review recommended."
            ))
        }
        
        // Cross-validate IFSC with bank name
        if (correctedData.ifscCode.length >= 4 && correctedData.bankName.isNotBlank()) {
            val ifscPrefix = correctedData.ifscCode.substring(0, 4)
            val expectedBankName = IFSC_BANK_MAPPING[ifscPrefix]
            if (expectedBankName != null && !correctedData.bankName.contains(expectedBankName, ignoreCase = true)) {
                warnings.add(ValidationWarning(
                    field = "bankName",
                    warningType = WarningType.UNUSUAL_FORMAT,
                    message = "Bank name '${correctedData.bankName}' may not match IFSC '${correctedData.ifscCode}'. Expected: $expectedBankName"
                ))
            }
        }
        
        return ChequeValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            correctedData = if (correctedData != chequeData) correctedData else null
        )
    }
    
    /**
     * Validate E-NACH data and return corrections if possible
     */
    fun validateENach(enachData: ENachOCRData): ENachValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        var correctedData = enachData.copy()
        
        // Validate IFSC Code
        validateAndCorrectIFSC(enachData.ifscCode)?.let { ifscResult ->
            if (ifscResult.isValid) {
                correctedData = correctedData.copy(ifscCode = ifscResult.correctedValue)
                if (ifscResult.correctedValue != enachData.ifscCode) {
                    warnings.add(ValidationWarning(
                        field = "ifscCode",
                        warningType = WarningType.UNCLEAR_TEXT,
                        message = "IFSC code auto-corrected from ${enachData.ifscCode} to ${ifscResult.correctedValue}"
                    ))
                }
            } else {
                errors.add(ValidationError(
                    field = "ifscCode",
                    errorType = ErrorType.INVALID_FORMAT,
                    message = "Invalid IFSC code format: ${enachData.ifscCode}"
                ))
            }
        }
        
        // Validate UMRN
        if (enachData.umrn.isNotBlank() && enachData.umrn != "NOT_FOUND") {
            if (!UMRN_PATTERN.matches(enachData.umrn)) {
                warnings.add(ValidationWarning(
                    field = "umrn",
                    warningType = WarningType.UNUSUAL_FORMAT,
                    message = "UMRN format may be unusual: ${enachData.umrn}"
                ))
            }
        }
        
        // Validate required fields
        listOf(
            "accountHolderName" to enachData.accountHolderName,
            "bankName" to enachData.bankName,
            "accountNumber" to enachData.accountNumber,
            "utilityName" to enachData.utilityName
        ).forEach { (fieldName, value) ->
            if (value.isBlank() || value == "NOT_FOUND") {
                errors.add(ValidationError(
                    field = fieldName,
                    errorType = ErrorType.MISSING_FIELD,
                    message = "$fieldName is required"
                ))
            }
        }
        
        // Validate date formats
        if (enachData.startDate.isNotBlank() && enachData.startDate != "NOT_FOUND") {
            if (!isValidDateFormat(enachData.startDate)) {
                warnings.add(ValidationWarning(
                    field = "startDate",
                    warningType = WarningType.UNUSUAL_FORMAT,
                    message = "Start date format may be incorrect: ${enachData.startDate}"
                ))
            }
        }
        
        return ENachValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            correctedData = if (correctedData != enachData) correctedData else null
        )
    }
    
    /**
     * Cross-validate cheque and E-NACH data for consistency
     */
    fun crossValidateDocuments(chequeData: ChequeOCRData, enachData: ENachOCRData): DocumentCrossValidationResult {
        val fieldMatches = mutableListOf<FieldMatch>()
        val conflicts = mutableListOf<DataConflict>()
        
        // Compare account holder names
        val nameMatch = compareFields("accountHolderName", chequeData.accountHolderName, enachData.accountHolderName)
        fieldMatches.add(nameMatch)
        if (!nameMatch.isMatch && nameMatch.confidence < NAME_SIMILARITY_THRESHOLD) {
            conflicts.add(DataConflict(
                fieldName = "accountHolderName",
                chequeValue = chequeData.accountHolderName,
                enachValue = enachData.accountHolderName,
                severity = ConflictSeverity.CRITICAL,
                description = "Account holder names don't match between documents"
            ))
        }
        
        // Compare account numbers
        val accountMatch = compareFields("accountNumber", chequeData.accountNumber, enachData.accountNumber)
        fieldMatches.add(accountMatch)
        if (!accountMatch.isMatch) {
            conflicts.add(DataConflict(
                fieldName = "accountNumber",
                chequeValue = chequeData.accountNumber,
                enachValue = enachData.accountNumber,
                severity = ConflictSeverity.CRITICAL,
                description = "Account numbers don't match between documents"
            ))
        }
        
        // Compare IFSC codes
        val ifscMatch = compareFields("ifscCode", chequeData.ifscCode, enachData.ifscCode)
        fieldMatches.add(ifscMatch)
        if (!ifscMatch.isMatch) {
            conflicts.add(DataConflict(
                fieldName = "ifscCode",
                chequeValue = chequeData.ifscCode,
                enachValue = enachData.ifscCode,
                severity = ConflictSeverity.HIGH,
                description = "IFSC codes don't match between documents"
            ))
        }
        
        // Compare bank names
        val bankMatch = compareFields("bankName", chequeData.bankName, enachData.bankName)
        fieldMatches.add(bankMatch)
        if (!bankMatch.isMatch && bankMatch.confidence < 0.6f) {
            conflicts.add(DataConflict(
                fieldName = "bankName",
                chequeValue = chequeData.bankName,
                enachValue = enachData.bankName,
                severity = ConflictSeverity.MEDIUM,
                description = "Bank names may not match between documents"
            ))
        }
        
        // Calculate overall validation score
        val criticalConflicts = conflicts.count { it.severity == ConflictSeverity.CRITICAL }
        val overallScore = when {
            criticalConflicts > 0 -> 0.0f
            conflicts.count { it.severity == ConflictSeverity.HIGH } > 0 -> 0.5f
            conflicts.count { it.severity == ConflictSeverity.MEDIUM } > 0 -> 0.7f
            else -> fieldMatches.map { it.confidence }.average().toFloat()
        }
        
        return DocumentCrossValidationResult(
            chequeData = chequeData,
            enachData = enachData,
            crossValidationPassed = criticalConflicts == 0,
            matchingFields = fieldMatches,
            conflicts = conflicts,
            overallScore = overallScore
        )
    }
    
    private fun validateAndCorrectIFSC(ifscCode: String): IFSCValidationResult? {
        if (ifscCode.isBlank() || ifscCode == "NOT_FOUND") return null
        
        val cleaned = ifscCode.replace(" ", "").uppercase()
        
        // Try exact match first
        if (IFSC_PATTERN.matches(cleaned)) {
            return IFSCValidationResult(true, cleaned)
        }
        
        // Try common corrections
        val corrections = listOf(
            cleaned.replace("O", "0"), // O to 0
            cleaned.replace("I", "1"), // I to 1
            cleaned.replace("S", "5"), // S to 5
            cleaned.replace("B", "8")  // B to 8
        )
        
        corrections.forEach { corrected ->
            if (IFSC_PATTERN.matches(corrected)) {
                return IFSCValidationResult(true, corrected)
            }
        }
        
        return IFSCValidationResult(false, cleaned)
    }
    
    private fun compareFields(fieldName: String, value1: String, value2: String): FieldMatch {
        val cleanValue1 = value1.trim().uppercase()
        val cleanValue2 = value2.trim().uppercase()
        
        val isExactMatch = cleanValue1 == cleanValue2
        val similarity = calculateStringSimilarity(cleanValue1, cleanValue2)
        
        return FieldMatch(
            fieldName = fieldName,
            chequeValue = value1,
            enachValue = value2,
            isMatch = isExactMatch || similarity > 0.9f,
            confidence = if (isExactMatch) 1.0f else similarity
        )
    }
    
    private fun calculateStringSimilarity(str1: String, str2: String): Float {
        if (str1 == str2) return 1.0f
        if (str1.isEmpty() || str2.isEmpty()) return 0.0f
        
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        
        val editDistance = levenshteinDistance(longer, shorter)
        return (longer.length - editDistance).toFloat() / longer.length
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1) { it }
        
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            
            for (j in 1..s2.length) {
                val cj = minOf(
                    1 + minOf(costs[j], costs[j - 1]),
                    if (s1[i - 1] == s2[j - 1]) nw else nw + 1
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        
        return costs[s2.length]
    }
    
    private fun isValidDateFormat(dateString: String): Boolean {
        val datePatterns = listOf(
            Regex("^\\d{1,2}/\\d{1,2}/\\d{4}$"),      // DD/MM/YYYY
            Regex("^\\d{1,2}-\\d{1,2}-\\d{4}$"),      // DD-MM-YYYY
            Regex("^\\d{4}-\\d{1,2}-\\d{1,2}$"),      // YYYY-MM-DD
            Regex("^\\d{1,2}\\.\\d{1,2}\\.\\d{4}$")   // DD.MM.YYYY
        )
        
        return datePatterns.any { it.matches(dateString) }
    }
    
    private data class IFSCValidationResult(
        val isValid: Boolean,
        val correctedValue: String
    )
}