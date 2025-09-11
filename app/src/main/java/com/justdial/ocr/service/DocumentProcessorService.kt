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
                    Log.d(TAG, "E-NACH processing successful"+enachData.toString())
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

    private fun createChequePrompt(): String {
        return """
You are an expert OCR system for Indian bank cheques and Indian e-NACH forms. You are also an expert signature detection system for Indian e-NACH forms and cheques. Extract ONLY the requested fields in valid JSON.

CRITICAL INSTRUCTION: The "account_holder_name" MUST be the printed name of the account owner on the cheque, NOT the handwritten "Pay To" name. For company cheques, this is usually printed near the signature line or below the amount.

OUTPUT SCHEMA (return ONLY this JSON; no extra text):
{
  "account_holder_name": "",
  "bank_name": "",
  "account_number": "",
  "ifsc_code": "",
  "micr_code": "",
  "signature_present": false,
  "document_quality": "good",
  "document_type": "printed",
  "fraud_indicators": [],

  "rotation_applied": 0,

  "signature_count": 0,
  "signatures_consistent": null,
  "signatures_match_score": 0,
  "signatures_notes": "",
  
  // CRITICAL: For e-NACH forms, separate matching of payer vs sponsor signatures
  "payer_signatures_match": null,      // true/false/null - do payer signatures match each other?
  "sponsor_signatures_match": null,    // true/false/null - do sponsor signatures match each other?
  "payer_match_score": 0,             // 0-100 score for payer signature similarity
  "sponsor_match_score": 0,           // 0-100 score for sponsor signature similarity

  // Each detected handwritten signature must have one entry here.
  // bbox values are normalized to the full (rotation-corrected) image in [0..1].
  "signature_regions": [
    {
      "bbox": {"x":0.0,"y":0.0,"w":0.0,"h":0.0},
      "group": "unknown",              // one of: payer | sponsor | unknown
      "position_number": 0,            // 1=left payer, 2=left sponsor, 3=right payer, 4=right sponsor
      "anchor_text": "",               // nearest printed text like "For <Company>", "PARTNER", "Authorised Signatory"
      "evidence": ""                   // short note: "cursive above PARTNER (left)", "partial due to crop", etc.
    }
  ],

  // Per-group counts for reliability when forms have two signers each side
  "signature_count_payer": 0,
  "signature_count_sponsor": 0,
  "signature_count_unknown": 0,

  // Optional expectations if anchors imply multiple signers
  "expected_signatures": {"payer": 0, "sponsor": 0},
  "missing_expected_signatures": []
}

Rules:
1) Return ONLY valid JSON exactly matching the schema above. Use "" (empty string) or [] when unreadable/unknown.
2) Prioritize the printed "account_holder_name"; never return the handwritten payee.
3) "fraud_indicators" only for visible issues (e.g., no MICR band; no bank logo/watermark; layout inconsistent).
4) Be conservative and cite only what is visible.

CRITICAL: CANCELLED CHEQUE DETECTION
5) NEVER treat "CANCELLED", "CANCEL", "VOID", or similar text as signatures, even if handwritten.
6) If you detect "CANCELLED" or "CANCEL" text on the cheque:
   - Do NOT include it in signature_regions
   - Do NOT count it as a signature
   - Add note in signatures_notes: "Cheque is marked as CANCELLED"
7) Only count actual personal name signatures as valid signatures.

ORIENTATION & SWEEP:
8) Normalize orientation so the form reads horizontally; set "rotation_applied" to 0/90/180/270.
9) Scan the entire image edge-to-edge, with EXTRA attention to the bottom strip where signatures appear.

CRITICAL e-NACH SIGNATURE DETECTION PATTERN:
10) MANDATORY for e-NACH forms: You MUST find exactly 4 handwritten signatures arranged as follows:
    
    STANDARD e-NACH SIGNATURE LAYOUT (from left to right):
    ┌─────────────────────────────────────────────────────────────┐
    │  Position 1          Position 2          Position 3          Position 4    │
    │  [PAYER SIG]        [SPONSOR SIG]       [PAYER SIG]        [SPONSOR SIG]  │
    │  Above text:        Above text:         Above text:         Above text:    │
    │  "PARTNER" or       Company name        "PARTNER" or       Company name   │
    │  Customer label     "For [COMPANY]"     Customer label     "For [COMPANY]"│
    └─────────────────────────────────────────────────────────────┘
    
    The pattern is: PAYER-SPONSOR-PAYER-SPONSOR (alternating)
    - Positions 1 & 3: Customer/Payer signatures (should match each other)
    - Positions 2 & 4: Company/Sponsor signatures (should match each other)

11) SIGNATURE POSITION IDENTIFICATION:
    Position 1 (Leftmost): PAYER signature - look for "PARTNER", "Authorised Signatory" without company name
    Position 2 (Left-Center): SPONSOR signature - look for "For [COMPANY NAME]" or company-specific text
    Position 3 (Right-Center): PAYER signature - similar anchor text as Position 1
    Position 4 (Rightmost): SPONSOR signature - similar anchor text as Position 2
    
    Common anchor texts for PAYER: "PARTNER", "Authorised Signatory", "Customer"
    Common anchor texts for SPONSOR: "For SHAREHOLDER AND SERVICES", "For JUSTDIAL LIMITED", company names

12) SIGNATURE IDENTIFICATION CHARACTERISTICS:
    - Signatures are HANDWRITTEN CURSIVE text above the printed labels
    - They look like flowing, connected handwriting (not printed block letters)
    - Each signature is typically a person's name written in cursive/script style
    - Look ABOVE the printed text labels, not below
    - EXCLUDE any "CANCELLED", "CANCEL", "VOID" text - these are NOT signatures

13) SCAN METHOD - Check these 4 positions systematically:
    a) Scan leftmost area → Find PAYER signature → Set position_number=1
    b) Scan left-center → Find SPONSOR signature → Set position_number=2
    c) Scan right-center → Find PAYER signature → Set position_number=3
    d) Scan rightmost area → Find SPONSOR signature → Set position_number=4

14) FOR EACH OF THE 4 SIGNATURES:
    - Add entry to "signature_regions" array
    - Set "position_number" (1-4) based on left-to-right position
    - Set bbox to approximate location
    - Set "anchor_text" to the printed text below the signature
    - Set "evidence" to describe location and appearance
    - Set "group" to "payer" for positions 1 & 3, "sponsor" for positions 2 & 4

15) VALIDATION: 
    - signature_count MUST equal 4 for e-NACH forms
    - signature_count_payer MUST equal 2 (positions 1 & 3)
    - signature_count_sponsor MUST equal 2 (positions 2 & 4)
    - If you find fewer, re-scan more carefully

GROUPING AND POSITION ASSIGNMENT:
16) Use the position and anchor text to determine group:
    - Positions 1 & 3 → "payer" group (customer signatures)
    - Positions 2 & 4 → "sponsor" group (company signatures)
    - If unclear from position alone, use anchor text as secondary indicator

17) Set per-group counts:
    - "signature_count_payer" = count of payer signatures (should be 2)
    - "signature_count_sponsor" = count of sponsor signatures (should be 2)
    - "signature_count" = total signatures (should be 4)
    - "signature_present" = true (if signature_count ≥ 1)

MULTI-SIGNATURE MATCHING (e-NACH specific):
18) PAYER SIGNATURE MATCHING (positions 1 & 3):
    - Compare the two payer signatures to each other
    - Check handwriting style, slant, loops, baseline, stroke patterns
    - Set "payer_match_score": 0-100 (90-100=very likely same, 75-89=likely same, <60=different)
    - Set "payer_signatures_match": true if ≥75, false if <60, null if unclear

19) SPONSOR SIGNATURE MATCHING (positions 2 & 4):
    - Compare the two sponsor signatures to each other
    - Check handwriting style, slant, loops, baseline, stroke patterns
    - Set "sponsor_match_score": 0-100 (90-100=very likely same, 75-89=likely same, <60=different)
    - Set "sponsor_signatures_match": true if ≥75, false if <60, null if unclear

20) OVERALL SIGNATURE CONSISTENCY:
    - "signatures_consistent" = true only if BOTH payer_signatures_match AND sponsor_signatures_match are true
    - "signatures_match_score" = average of payer_match_score and sponsor_match_score
    - Add detailed notes in "signatures_notes" about the matching results

EXPECTATION HEURISTICS:
21) For e-NACH forms, always expect:
    - Exactly 4 signatures total
    - 2 payer signatures that should match each other
    - 2 sponsor signatures that should match each other
    - Set "expected_signatures": {"payer": 2, "sponsor": 2}

22) If fewer than 4 signatures found:
    - List missing positions in "missing_expected_signatures"
    - Re-scan the image more carefully before finalizing

QUALITY & FRAUD:
23) Only mark fraud if there are tampering signs. Pure blur/glare is NOT fraud.
24) Common fraud indicators for e-NACH: mismatched signatures within same group, copy-pasted signatures, digitally added signatures

FINAL VALIDATIONS:
25) For e-NACH forms:
    - Verify signature_count == 4
    - Verify signature_count_payer == 2
    - Verify signature_count_sponsor == 2
    - Verify each signature has correct position_number (1-4)
    - Verify payer_signatures_match and sponsor_signatures_match are evaluated
26) Double-check you found ALL 4 signatures before returning
27) Ensure "CANCELLED" text is never counted as a signature

EDGE CASES:
28) If no MICR band visible, set "micr_code": "" and add "no MICR band" to "fraud_indicators"
29) If IFSC/MICR unreadable, leave "" rather than guessing
30) If printed account holder name not visible, set "account_holder_name": ""
31) If "CANCELLED" is written on form, mention in signatures_notes but do not count as signature

CRITICAL OUTPUT REQUIREMENT:
32) The system consuming this JSON expects separate matching scores for payer vs sponsor signatures
33) DO NOT compare payer signatures to sponsor signatures - only compare within each group
34) The key business logic is: payer signatures (1&3) should match, sponsor signatures (2&4) should match

Return ONLY the JSON object.
""".trimIndent()
    }
   /* private fun createChequePrompt(): String {
        return """
You are an expert OCR system for Indian bank cheques and Indian e-NACH forms. You are also an expert signature detection system for Indian e-NACH forms and cheques. Extract ONLY the requested fields in valid JSON.

CRITICAL INSTRUCTION: The "account_holder_name" MUST be the printed name of the account owner on the cheque, NOT the handwritten "Pay To" name. For company cheques, this is usually printed near the signature line or below the amount.

OUTPUT SCHEMA (return ONLY this JSON; no extra text):
{
  "account_holder_name": "",
  "bank_name": "",
  "account_number": "",
  "ifsc_code": "",
  "micr_code": "",
  "signature_present": false,
  "document_quality": "good",
  "document_type": "printed",
  "fraud_indicators": [],

  "rotation_applied": 0,

  "signature_count": 0,
  "signatures_consistent": null,
  "signatures_match_score": 0,
  "signatures_notes": "",

  // Each detected handwritten signature must have one entry here.
  // bbox values are normalized to the full (rotation-corrected) image in [0..1].
  "signature_regions": [
    {
      "bbox": {"x":0.0,"y":0.0,"w":0.0,"h":0.0},
      "group": "unknown",              // one of: payer | sponsor | unknown
      "anchor_text": "",               // nearest printed text like "For <Company>", "PARTNER", "Authorised Signatory", "JUSTDIAL LIMITED"
      "evidence": ""                   // short note: "cursive above PARTNER (left)", "partial due to crop", etc.
    }
  ],

  // Per-group counts for reliability when forms have two signers each side
  "signature_count_payer": 0,
  "signature_count_sponsor": 0,
  "signature_count_unknown": 0,

  // Optional expectations if anchors imply multiple signers
  "expected_signatures": {"payer": 0, "sponsor": 0},
  "missing_expected_signatures": []
}

Rules:
1) Return ONLY valid JSON exactly matching the schema above. Use "" (empty string) or [] when unreadable/unknown.
2) Prioritize the printed "account_holder_name"; never return the handwritten payee.
3) "fraud_indicators" only for visible issues (e.g., no MICR band; no bank logo/watermark; layout inconsistent; MICR line missing/wrong font/length; IFSC/MICR/bank mismatch; overwrites/erasures in pre-printed areas; entire cheque appears handwritten on plain paper; signature looks copy-pasted). If none, use [].
4) Be conservative and cite only what is visible.

CRITICAL: CANCELLED CHEQUE DETECTION
5) NEVER treat "CANCELLED", "CANCEL", "VOID", or similar text as signatures, even if handwritten.
6) If you detect "CANCELLED" or "CANCEL" text on the cheque:
   - Do NOT include it in signature_regions
   - Do NOT count it as a signature
   - Add note in signatures_notes: "Cheque is marked as CANCELLED - cancel text ignored for signature detection"
7) Only count actual personal name signatures as valid signatures.

ORIENTATION & SWEEP:
8) Normalize orientation so the form reads horizontally; set "rotation_applied" to 0/90/180/270.
9) Scan the entire image edge-to-edge, with EXTRA attention to the bottom strip where multiple "For <Company>" + "PARTNER/Authorised Signatory" lines appear. Re-check the extreme left and right edges before finalizing.

CRITICAL SIGNATURE DETECTION FOR E-NACH:
10) MANDATORY: You MUST find exactly 4 handwritten signatures in this e-NACH form. They are located at:
   Position 1 (Left): Above "For SHAREHOLDER AND SERVICES" - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT
   Position 2 (Left-Center): Above first "PARTNER" label - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT  
   Position 3 (Right-Center): Above second "For SHAREHOLDER AND SERVICES" label - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT
   Position 4 (Right): Above third "PARTNER" label - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT

11) SIGNATURE IDENTIFICATION:
   - Signatures are HANDWRITTEN CURSIVE text above the printed labels
   - They look like flowing, connected handwriting (not printed block letters)
   - Each signature is a person's name written in cursive/script style
   - Ignore the printed text below - only look for handwriting ABOVE each label
   - EXCLUDE any text that says "CANCELLED", "CANCEL", "VOID" - these are NOT signatures

12) SCAN METHOD - Check these 4 locations in order:
   a) Look ABOVE "For SHAREHOLDER AND SERVICES" text → Record signature found here
   b) Look ABOVE first "PARTNER" text → Record signature found here  
   c) Look ABOVE second "For SHAREHOLDER AND SERVICES" text → Record signature found here
   d) Look ABOVE third "PARTNER" text → Record signature found here

13) FOR EACH OF THE 4 SIGNATURES:
    - Add entry to "signature_regions" array
    - Set bbox to approximate location (left signatures x<0.5, right signatures x>0.5)
    - Set "anchor_text" to the printed text below (e.g., "PARTNER", "For SHAREHOLDER AND SERVICES")
    - Set "evidence" to describe location (e.g., "handwritten signature above leftmost PARTNER")
    - Set "group" to "payer" for customer signatures

14) VALIDATION: signature_count MUST equal 4 for e-NACH forms. If you find fewer, look again more carefully.

GROUPING (payer vs sponsor/receiver):
15) For each signature box, set "group":
   - "payer" if the nearest anchor text includes the account holder name or phrases like "For <ACCOUNT HOLDER>", "Partner", "Authorised Signatory" (without "JUSTDIAL"/sponsor nearby).
   - "sponsor" if the nearest anchor text includes the sponsor/originator (e.g., "JUSTDIAL LIMITED", "Sponsor", "Utility Code" region's sign line).
   - "unknown" if anchors are unclear.

16) Set per-group counts and totals:
   - "signature_count_payer", "signature_count_sponsor", "signature_count_unknown"
   - "signature_count" = signature_regions.length 
   - "signature_present" = (signature_count ≥ 1)

EXPECTATION HEURISTICS (common on NACH forms):
17) If you see multiple "PARTNER" labels and "For [Company]" areas, expect multiple signatures.
18) For e-NACH forms with 2+ "PARTNER" labels, expect 4 signatures total.
19) If you find fewer signatures than expected, re-scan more carefully.

MULTI-SIGNATURE VERIFICATION:
20) If signature_count ≤ 1:
    - "signatures_consistent": null, "signatures_match_score": 0, "signatures_notes": ""
21) If signature_count ≥ 2:
    - Compare handwriting style, slant, loops, baseline, stroke patterns
    - Score 0–100: 90-100=very likely same, 75-89=likely same, 60-74=unclear, <60=different
    - "signatures_consistent" = true if ≥75, false if <60, null if unclear
    - Brief notes about comparison

QUALITY & FRAUD:
22) Only mark fraud if there are tampering signs. Pure blur/glare is NOT fraud.

FINAL VALIDATIONS:
24) "signature_count" MUST equal signature_regions.length.
25) Double-check you found ALL signatures before returning - e-NACH forms typically have 4!
26) Ensure "CANCELLED" text is never counted as a signature.

EDGE CASES:
27) If no MICR band visible, set "micr_code": "" and add "no MICR band" to "fraud_indicators".
28) If IFSC/MICR unreadable, leave "" rather than guessing.
29) If printed account holder name not visible, set "account_holder_name": "" (do NOT use handwritten names).
30) If "CANCELLED" is written on cheque, mention this in signatures_notes and fraud_indicators but do not count as signature.

Return ONLY the JSON object.
""".trimIndent()
    }*/

    // Prompts and parsing functions remain the same...
    /*private fun createChequePrompt(): String {
        return """
You are an expert OCR system for Indian bank cheques and Indian e-NACH forms. You are also an expert signature detection system for Indian e-NACH forms and cheques. Extract ONLY the requested fields in valid JSON.

CRITICAL INSTRUCTION: The "account_holder_name" MUST be the printed name of the account owner on the cheque, NOT the handwritten "Pay To" name. For company cheques, this is usually printed near the signature line or below the amount.

OUTPUT SCHEMA (return ONLY this JSON; no extra text):
{
  "account_holder_name": "",
  "bank_name": "",
  "account_number": "",
  "ifsc_code": "",
  "micr_code": "",
  "signature_present": false,
  "document_quality": "good",
  "document_type": "printed",
  "fraud_indicators": [],

  "rotation_applied": 0,

  "signature_count": 0,
  "signatures_consistent": null,
  "signatures_match_score": 0,
  "signatures_notes": "",

  // Each detected handwritten signature must have one entry here.
  // bbox values are normalized to the full (rotation-corrected) image in [0..1].
  "signature_regions": [
    {
      "bbox": {"x":0.0,"y":0.0,"w":0.0,"h":0.0},
      "group": "unknown",              // one of: payer | sponsor | unknown
      "anchor_text": "",               // nearest printed text like "For <Company>", "PARTNER", "Authorised Signatory", "JUSTDIAL LIMITED"
      "evidence": ""                   // short note: "cursive above PARTNER (left)", "partial due to crop", etc.
    }
  ],

  // Per-group counts for reliability when forms have two signers each side
  "signature_count_payer": 0,
  "signature_count_sponsor": 0,
  "signature_count_unknown": 0,

  // Optional expectations if anchors imply multiple signers
  "expected_signatures": {"payer": 0, "sponsor": 0},
  "missing_expected_signatures": []
}

Rules:
1) Return ONLY valid JSON exactly matching the schema above. Use "" (empty string) or [] when unreadable/unknown.
2) Prioritize the printed "account_holder_name"; never return the handwritten payee.
3) "fraud_indicators" only for visible issues (e.g., no MICR band; no bank logo/watermark; layout inconsistent; MICR line missing/wrong font/length; IFSC/MICR/bank mismatch; overwrites/erasures in pre-printed areas; entire cheque appears handwritten on plain paper; signature looks copy-pasted). If none, use [].
4) Be conservative and cite only what is visible.

ORIENTATION & SWEEP:
5) Normalize orientation so the form reads horizontally; set "rotation_applied" to 0/90/180/270.
6) Scan the entire image edge-to-edge, with EXTRA attention to the bottom strip where multiple "For <Company>" + "PARTNER/Authorised Signatory" lines appear. Re-check the extreme left and right edges before finalizing.

CRITICAL SIGNATURE DETECTION FOR E-NACH:
7) MANDATORY: You MUST find exactly 4 handwritten signatures in this e-NACH form. They are located at:
   Position 1 (Left): Above "For SHAREHOLDER AND SERVICES" - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT
   Position 2 (Left-Center): Above first "PARTNER" label - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT  
   Position 3 (Right-Center): Above second "PARTNER" label - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT
   Position 4 (Right): Above third "PARTNER" label - LOOK FOR CURSIVE HANDWRITING ABOVE THIS TEXT

8) SIGNATURE IDENTIFICATION:
   - Signatures are HANDWRITTEN CURSIVE text above the printed labels
   - They look like flowing, connected handwriting (not printed block letters)
   - Each signature is a person's name written in cursive/script style
   - Ignore the printed text below - only look for handwriting ABOVE each label

9) SCAN METHOD - Check these 4 locations in order:
   a) Look ABOVE "For SHAREHOLDER AND SERVICES" text → Record signature found here
   b) Look ABOVE first "PARTNER" text → Record signature found here  
   c) Look ABOVE second "PARTNER" text → Record signature found here
   d) Look ABOVE third "PARTNER" text → Record signature found here

10) FOR EACH OF THE 4 SIGNATURES:
    - Add entry to "signature_regions" array
    - Set bbox to approximate location (left signatures x<0.5, right signatures x>0.5)
    - Set "anchor_text" to the printed text below (e.g., "PARTNER", "For SHAREHOLDER AND SERVICES")
    - Set "evidence" to describe location (e.g., "handwritten signature above leftmost PARTNER")
    - Set "group" to "payer" for customer signatures

11) VALIDATION: signature_count MUST equal 4 for e-NACH forms. If you find fewer, look again more carefully.

GROUPING (payer vs sponsor/receiver):
11) For each signature box, set "group":
   - "payer" if the nearest anchor text includes the account holder name or phrases like "For <ACCOUNT HOLDER>", "Partner", "Authorised Signatory" (without "JUSTDIAL"/sponsor nearby).
   - "sponsor" if the nearest anchor text includes the sponsor/originator (e.g., "JUSTDIAL LIMITED", "Sponsor", "Utility Code" region's sign line).
   - "unknown" if anchors are unclear.

12) Set per-group counts and totals:
   - "signature_count_payer", "signature_count_sponsor", "signature_count_unknown"
   - "signature_count" = signature_regions.length 
   - "signature_present" = (signature_count ≥ 1)

EXPECTATION HEURISTICS (common on NACH forms):
13) If you see multiple "PARTNER" labels and "For [Company]" areas, expect multiple signatures.
14) For e-NACH forms with 3+ "PARTNER" labels, expect 4 signatures total.
15) If you find fewer signatures than expected, re-scan more carefully.

MULTI-SIGNATURE VERIFICATION:
16) If signature_count ≤ 1:
    - "signatures_consistent": null, "signatures_match_score": 0, "signatures_notes": ""
17) If signature_count ≥ 2:
    - Compare handwriting style, slant, loops, baseline, stroke patterns
    - Score 0–100: 90-100=very likely same, 75-89=likely same, 60-74=unclear, <60=different
    - "signatures_consistent" = true if ≥75, false if <60, null if unclear
    - Brief notes about comparison

QUALITY & FRAUD:
18) Only mark fraud if there are tampering signs. Pure blur/glare is NOT fraud.

FINAL VALIDATIONS:
19) "signature_count" MUST equal signature_regions.length.
20) Double-check you found ALL signatures before returning - e-NACH forms typically have 4!

EDGE CASES:
21) If no MICR band visible, set "micr_code": "" and add "no MICR band" to "fraud_indicators".
22) If IFSC/MICR unreadable, leave "" rather than guessing.
23) If printed account holder name not visible, set "account_holder_name": "" (do NOT use handwritten names).

Return ONLY the JSON object.
""".trimIndent()
    }*/


    /* private fun createChequePrompt(): String {
         return """
         You are an expert OCR system for Indian bank cheques and Indian e-NACH forms. Extract ONLY the requested fields in valid JSON.

         CRITICAL INSTRUCTION: The "account_holder_name" MUST be the printed name of the account owner on the cheque, NOT the handwritten "Pay To" name. For company cheques, this is usually printed near the signature line or below the amount.

         OUTPUT SCHEMA (return ONLY this JSON; no extra text):
         {
           "account_holder_name": "printed owner name (not payee)",
           "bank_name": "Bank name",
           "account_number": "Account number",
           "ifsc_code": "IFSC code",
           "micr_code": "MICR code",
           "signature_present": true/false,
           "document_quality": "good/poor/blurry/glare/cropped",
           "document_type": "original/photocopy/handwritten/printed",
           "fraud_indicators": ["array of visible issues or empty"],

           // NEW: multi-signature verification
           "signature_count": 0,
           "signatures_consistent": true/false/null,
           "signatures_match_score": 0,
           "signatures_notes": "short reason if mismatch/unclear, else empty"
         }

         Rules:
         1) Return ONLY valid JSON exactly matching the schema above. No extra keys or text.
         2) Prioritize the printed "account_holder_name"; never return the handwritten payee.
         3) Populate "fraud_indicators" with visible reasons if the cheque looks handwritten/forged/fake. Examples: no MICR band; no bank logo/watermark; layout inconsistent with Indian cheques; MICR line missing/wrong font/length; IFSC/MICR/bank mismatch; overwrites/erasures in pre-printed areas; entire cheque appears handwritten on plain paper; signature looks copy-pasted. If no issues are visible, set "fraud_indicators": [].
         4) Be conservative and cite only what is visible in the image.

         Signature handling (VERY IMPORTANT):
         5) Set "signature_present" = true if at least one handwritten signature is visible anywhere on the cheque (including "Authorised Signatory" area). Ignore printed/stamped text and background watermarks.
         6) Count distinct handwritten signatures you can see on the instrument and set "signature_count".
         7) If signature_count ≤ 1:
            - "signatures_consistent": null
            - "signatures_match_score": 0
            - "signatures_notes": "" (empty)
         8) If signature_count ≥ 2, verify whether all visible handwritten signatures are from the SAME signer:
            - Compare overall shape, stroke order cues, slant, loop and hook shapes, baseline alignment, unique glyphs in the name, pen pressure patterns, and connecting strokes.
            - Ignore stamps ("for XYZ"), "A/c Payee", seals, and printed authorisation text. Compare only handwritten signatures.
            - Compute a single overall % match across the set (0–100). Suggested interpretation:
                90–100 = Very likely same signer
                75–89  = Likely same signer (minor variation)
                60–74  = Unclear/low confidence
                <60    = Likely different signers
            - Set:
                • "signatures_consistent" = true if overall score ≥ 75 and no pair is clearly divergent
                • "signatures_consistent" = false if any pair appears different (overall score < 60) or there are strong inconsistencies (different names/scripts)
                • "signatures_consistent" = null if image quality prevents reliable judgement (blur/glare/crop)
            - Place the numeric overall score in "signatures_match_score" (integer 0–100).
            - Briefly explain in "signatures_notes" (e.g., "two signatures; matching loops on 'h' and baseline; minor slant change", or "mismatch: different surname letters; one Devanagari, one Latin", or "unclear due to blur/glare").
         9) If quality issues impede signature comparison, reflect that in "document_quality" and add a relevant item to "fraud_indicators" only if it suggests tampering (e.g., copy-pasted signature artifacts). Pure blur/glare without tampering signs should NOT be marked as fraud.

         Edge cases:
         10) If no MICR line is visible, set "micr_code": "" and add "no MICR band" to "fraud_indicators" only if the instrument purports to be a standard Indian cheque.
         11) If IFSC/MICR cannot be read, leave the field "" (empty string) rather than guessing.
         12) If the account holder's printed name is not visible, set "account_holder_name": "" and DO NOT use the handwritten payee.

         Return ONLY the JSON object.
         """
     }*/

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
