package com.justdial.ocr.service

import android.content.Context
import android.util.Log
import com.justdial.ocr.model.*
import com.justdial.ocr.service.FirebaseAIService
import com.justdial.ocr.validation.ValidationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentProcessorService {
    private val TAG = "DocumentProcessorService"
    private val firebaseAI = FirebaseAIService()
    private val validationEngine = ValidationEngine()
    
    fun initializeService(context: Context) {
        try {
            Log.d(TAG, "Initializing Firebase AI Logic for document processing")
            firebaseAI.initializeService(context)
            
            if (firebaseAI.validateRegionalCompliance()) {
                Log.d(TAG, "Regional compliance validated: India region (asia-south1)")
            } else {
                Log.w(TAG, "Regional compliance warning: Not using India region")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI Service", e)
            throw e
        }
    }

    /**
     * Process a cheque image using Firebase AI Logic with India region compliance
     */
    suspend fun processCheque(context: Context, imageBytes: ByteArray): Result<ChequeOCRData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing cheque with Firebase AI (India region)")
                
                // Ensure Firebase AI is initialized
                if (!firebaseAI.isServiceInitialized()) {
                    Log.d(TAG, "Initializing Firebase AI Service...")
                    firebaseAI.initializeService(context)
                }
                
                val prompt = createChequePrompt()
                val firebaseResult = firebaseAI.processCheque(context, imageBytes, prompt)

                Log.d(TAG, "Firebase Cheque Result: ${firebaseResult.getOrNull()}")

                if (firebaseResult.isSuccess) {
                    val chequeData = firebaseResult.getOrThrow()
                    Log.d(TAG, "Cheque processing successful")
                    Result.success(chequeData)
                } else {
                    val error = firebaseResult.exceptionOrNull() ?: Exception("Unknown error")
                    Log.e(TAG, "Cheque processing failed", error)
                    Result.failure(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in cheque processing", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Process an E-NACH mandate image using Firebase AI Logic with India region compliance
     */
    suspend fun processENach(context: Context, imageBytes: ByteArray): Result<ENachOCRData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing E-NACH with Firebase AI (India region)")
                
                // Ensure Firebase AI is initialized
                if (!firebaseAI.isServiceInitialized()) {
                    Log.d(TAG, "Initializing Firebase AI Service...")
                    firebaseAI.initializeService(context)
                }
                
                val prompt = createENachPrompt()
                val firebaseResult = firebaseAI.processENach(context, imageBytes, prompt)

                if (firebaseResult.isSuccess) {
                    val enachData = firebaseResult.getOrThrow()
                    Log.d(TAG, "E-NACH processing successful")
                    Result.success(enachData)
                } else {
                    val error = firebaseResult.exceptionOrNull() ?: Exception("Unknown error")
                    Log.e(TAG, "E-NACH processing failed", error)
                    Result.failure(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in E-NACH processing", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Process both documents using Firebase AI and perform cross-validation
     */
    suspend fun processBothDocuments(
        context: Context,
        chequeImageBytes: ByteArray,
        enachImageBytes: ByteArray
    ): Result<DocumentCrossValidationResult> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing both documents with Firebase AI (India region)")
                
                val chequeResult = processCheque(context, chequeImageBytes)
                val enachResult = processENach(context, enachImageBytes)

                if (chequeResult.isSuccess && enachResult.isSuccess) {
                    val chequeData = chequeResult.getOrThrow()
                    val enachData = enachResult.getOrThrow()

                    val crossValidation = validationEngine.crossValidateDocuments(chequeData, enachData)
                    Log.d(TAG, "Cross-validation completed successfully")
                    Result.success(crossValidation)
                } else {
                    val error = chequeResult.exceptionOrNull() ?: enachResult.exceptionOrNull()
                    Log.e(TAG, "Document processing failed", error)
                    Result.failure(error ?: Exception("Unknown processing error"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in cross-validation processing", e)
                Result.failure(e)
            }
        }
    }

    // Prompts and parsing functions remain the same...

    private fun createChequePrompt(): String {
        return """
        You are an expert OCR system for Indian bank cheques and Indian e-NACH forms. Extract ONLY the requested fields in valid JSON.
        CRITICAL INSTRUCTION: The "account_holder_name" MUST be the printed name of the account owner on the cheque, NOT the handwritten "Pay To" name. For company cheques, this is usually printed near the signature line or below the amount.
        {
            "account_holder_name": "printed owner name (not payee)",
            "bank_name": "Bank name",
            "account_number": "Account number",
            "ifsc_code": "IFSC code",
            "micr_code": "MICR code",
            "signature_present": true/false,
            "document_quality": "good/poor/blurry/glare/cropped",
            "document_type": "original/photocopy/handwritten/printed",
            "fraud_indicators": ["array of visible issues or empty"]
        }
        Rules:
        1. Return ONLY valid JSON. No extra text.
        2. Prioritize the printed "account_holder_name"; never return the handwritten payee.
        3. Populate "fraud_indicators" with visible reasons if the cheque looks handwritten/forged/fake. Examples: no MICR band; no bank logo/watermark; layout inconsistent with Indian cheques; MICR line missing/wrong font/length; IFSC/MICR/bank mismatch; overwrites/erasures in pre-printed areas; entire cheque appears handwritten on plain paper; signature looks copy-pasted.
        4. If no issues are visible, set "fraud_indicators": [].
        5. Be conservative and cite only what is visible in the image.
        6. The "account_holder_name" is the most important field. Find the printed name of the company or individual who owns the account. Do NOT extract the handwritten name in the 'Pay To' line.
       
        """
    }

    private fun createENachPrompt(): String {
        return """
        You are an expert OCR system for Indian e-NACH forms. Extract the following in JSON format:
        {
            "account_holder_name": "Customer name",
            "bank_name": "Bank name",
            "account_number": "Account number",
            "ifsc_code": "IFSC code",
            "micr_code": "MICR code",
            "signature_present": true/false,
            "document_quality": "good/poor/blurry/glare/cropped",
            "document_type": "original/photocopy/handwritten/printed"
        }
        Rules: Return only JSON. Customer signature is mandatory.
        """
    }

}
