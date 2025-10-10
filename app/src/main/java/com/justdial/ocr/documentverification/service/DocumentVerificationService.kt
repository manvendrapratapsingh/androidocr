package com.justdial.ocr.documentverification.service

import android.content.Context
import android.util.Log
import com.justdial.ocr.documentverification.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentVerificationService {
    private val TAG = "DocumentVerificationService"
    private val firebaseAI = FirebaseDocumentAIService()

    fun initializeService(context: Context) {
        try {
            Log.d(TAG, "Initializing Document Verification Service")
            firebaseAI.initializeService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Document Verification Service", e)
            throw e
        }
    }

    suspend fun analyzeDocument(
        context: Context,
        imageBytes: ByteArray,
        expectedType: DocumentType
    ): Result<DocumentAnalysisResult> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Analyzing document with Firebase AI")
                Log.d(TAG, "Expected document type: $expectedType")

                if (!firebaseAI.isServiceInitialized()) {
                    firebaseAI.initializeService(context)
                }

                val prompt = when (expectedType) {
                    DocumentType.PAN -> createPANPrompt()
                    DocumentType.DRIVING_LICENSE -> createDrivingLicensePrompt()
                    DocumentType.VOTER_ID -> createVoterIDPrompt()
                    DocumentType.PASSPORT -> createPassportPrompt()
                    DocumentType.UNKNOWN -> createGenericPrompt()
                }

                Log.d(TAG, "Using ${expectedType.name} specific prompt")
                val result = firebaseAI.analyzeDocument(context, imageBytes, prompt)

                if (result.isSuccess) {
                    Log.d(TAG, "Document analysis successful")
                    result
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown error")
                    Log.e(TAG, "Document analysis failed", error)
                    Result.failure(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in document analysis", e)
                Result.failure(e)
            }
        }
    }

    private fun createPANPrompt(): String = """
Verify PAN Card authenticity. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0
}

ACCEPTABLE (ignore):
✓ Age wear, fading, creases, stains, yellowing
✓ Scan blur, shadows, glare, slight rotation
✓ Worn lamination, ink fading

FRAUD (flag):
✗ Clone stamp patterns, pasted rectangles
✗ Font mismatches in printed text
✗ Wrong PAN format (not ^[A-Z]{5}[0-9]{4}[A-Z]$)
✗ Missing security features in clear image
✗ Impossible shadows, unnatural edges

PAN CHECKS (PASS needs ≥3):
1. PAN format: ^[A-Z]{5}[0-9]{4}[A-Z]$ visible
2. "INCOME TAX DEPARTMENT" or "Permanent Account Number" text
3. Government emblem visible
4. Photo present in correct position
5. Signature field present

SCORING:
- ela_tampering_score: 0-35 PASS, 36-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0 (clarity of assessment)
- PASS: score ≤35, ≥3 checks passed, no fraud indicators
- FLAGGED: score 36-50, 2 checks passed, minor concerns
- FAIL: score >50, <2 checks, or clear digital manipulation

Be specific in fraud_indicators. Prefer PASS for worn but legitimate documents.
""".trimIndent()

    private fun createDrivingLicensePrompt(): String = """
Verify Driving License authenticity. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0
}

ACCEPTABLE (ignore):
✓ Wear, fading, creases, dirt, bent corners
✓ Scan artifacts, blur, shadows, rotation
✓ Natural physical damage

FRAUD (flag):
✗ Clone stamp patterns, pasted elements
✗ Font mismatches, misaligned text
✗ Invalid license number format
✗ Missing mandatory features
✗ Digital manipulation artifacts

DL CHECKS (PASS needs ≥3):
1. License number format valid (state-specific)
2. State transport authority logo/text visible
3. Vehicle class codes present (MC, LMV, etc.)
4. Photo in correct position
5. Validity dates present

SCORING:
- ela_tampering_score: 0-35 PASS, 36-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0
- PASS: score ≤35, ≥3 checks passed, no fraud indicators
- FLAGGED: score 36-50, 2 checks passed
- FAIL: score >50, <2 checks, clear tampering

Be specific. Don't penalize legitimate wear.
""".trimIndent()

    private fun createVoterIDPrompt(): String = """
Verify Voter ID authenticity. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0
}

ACCEPTABLE (ignore):
✓ Heavy wear, discoloration, fading
✓ Damaged hologram, worn edges
✓ Scan quality issues, shadows, blur

FRAUD (flag):
✗ Digital tampering, pasted elements
✗ Wrong EPIC format (not ^[A-Z]{3}[0-9]{7}$)
✗ Font mismatches
✗ Missing all security features
✗ Clone stamp patterns

VOTER ID CHECKS (PASS needs ≥3):
1. EPIC format: ^[A-Z]{3}[0-9]{7}$ readable
2. "ELECTION COMMISSION" text or logo visible
3. Photo present
4. Hologram area present (even if faded)
5. Officer signature/name present

SCORING:
- ela_tampering_score: 0-35 PASS, 36-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0
- PASS: score ≤35, ≥3 checks passed, no fraud indicators
- FLAGGED: score 36-50, 2 checks passed
- FAIL: score >50, <2 checks, clear manipulation

Be specific. Accept aged but legitimate documents.
""".trimIndent()

    private fun createPassportPrompt(): String = """
Verify Passport authenticity. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0
}

ACCEPTABLE (ignore):
✓ Natural wear, fading, creases
✓ Photo angle, finger in frame, shadows
✓ Scan artifacts, blur, glare
✓ Worn lamination

FRAUD (flag):
✗ Digital manipulation, pasted elements
✗ Wrong passport number (not ^[A-Z][0-9]{7}$)
✗ Invalid MRZ format
✗ Font mismatches
✗ Missing security features
✗ Screen capture indicators (moiré)

PASSPORT CHECKS (PASS needs ≥4):
1. Passport number: ^[A-Z][0-9]{7}$ visible
2. "REPUBLIC OF INDIA" and emblem visible
3. MRZ (two lines at bottom) present and format-valid
4. Bilingual text (English/Hindi) present
5. Guilloché background pattern visible
6. Mandatory fields (name, DOB, dates) present

INNER/ADDRESS PAGE (PASS needs ≥3):
- Bilingual labels (Father/Mother/Spouse/Address)
- Guilloché background pattern
- Perforation dots along edge
- Top-right barcode
- "File No." field

SCORING:
- ela_tampering_score: 0-35 PASS, 36-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0
- PASS: score ≤35, required checks passed, no fraud
- FLAGGED: score 36-50, minimum checks passed
- FAIL: score >50, insufficient checks, clear tampering

Be specific. Don't penalize natural wear or photo quality.
""".trimIndent()

    private fun createGenericPrompt(): String = """
Detect and verify Indian identity document. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0
}

DETECT TYPE:
- PAN: Blue/maroon background, Income Tax Dept, 10-char PAN
- DL: Transport logo, vehicle classes, validity
- VOTER_ID: Election Commission logo, EPIC number
- PASSPORT: "Republic of India", MRZ, passport number

ACCEPTABLE: Age wear, physical damage, scan artifacts
FRAUD: Digital tampering, pasted elements, format violations

SCORING:
- ela_tampering_score: 0-35 PASS, 36-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0
- PASS: Detected type, no fraud indicators
- FLAGGED: Detected type, minor concerns
- FAIL: Cannot detect or clear fraud

Be specific. Accept worn legitimate documents.
""".trimIndent()

//    private fun createDocumentVerificationPrompt(expectedType: DocumentType?): String {
//        return """
//You are an expert document verification and fraud detection system specializing in Indian identity documents. Your task is to analyze the document image and provide a comprehensive fraud analysis.
//
//CRITICAL INSTRUCTIONS:
//1. Automatically detect the document type (PAN, DL, Voter ID, or Passport)
//2. Perform authenticity verification
//3. Detect tampering, forgery, and fraud indicators
//4. Calculate ELA (Error Level Analysis) tampering score
//5. Return ONLY valid JSON (no markdown, no extra text)
//
//OUTPUT SCHEMA (return ONLY this JSON):
//{
//  "image_url": "",
//  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
//  "prediction": "PASS|FLAGGED|FAIL",
//  "reason": "Detailed explanation of the prediction",
//  "ela_tampering_score": 0.0,
//  "fraud_indicators": ["list of specific fraud indicators found"],
//  "extracted_fields": {
//    "field_name": "field_value"
//  },
//  "confidence": 0.0
//}
//
//DOCUMENT TYPE DETECTION:
//Analyze visual characteristics to identify:
//- PAN: Permanent Account Number card with blue/maroon background, Income Tax Department logo
//- DRIVING_LICENSE: State transport authority logo, license classes, validity dates
//- VOTER_ID: Election Commission of India logo, hologram, voter ID number format
//- PASSPORT: "Republic of India" text, passport number (A-Z followed by 7 digits), emblem
//
//FRAUD DETECTION RULES (Check ALL of these):
//
//1. PHYSICAL TAMPERING INDICATORS:
//   - Color inconsistencies or unnatural color shifts in background
//   - Mismatched fonts or font sizes in printed text
//   - Uneven text alignment or spacing
//   - Visible cut-and-paste marks or edge artifacts
//   - Shadow inconsistencies around text or photo
//   - Blurred or pixelated regions suggesting digital editing
//   - Compression artifacts around edited areas (JPEG ghosts)
//
//2. PRINTING & MATERIAL FRAUD:
//   - Document appears handwritten or home-printed (not official)
//   - Missing security features: holograms, watermarks, microprints, UV patterns
//   - Poor paper quality or wrong texture
//   - Incorrect document dimensions
//   - Missing or fake embossing/laser engraving
//   - Color of official logos doesn't match standard (e.g., wrong blue shade in PAN)
//
//3. CONTENT INCONSISTENCIES:
//   - Document number format doesn't match official pattern
//   - Invalid date formats or impossible dates (future DOB, expired validity)
//   - Name contains unusual characters or formatting
//   - Photo quality mismatch with document quality
//   - Photo edges show copy-paste artifacts
//   - Photo doesn't align with photo box boundaries
//
//4. SPECIFIC DOCUMENT CHECKS:
//
//   PAN CARD:
//   - PAN format: 5 letters + 4 digits + 1 letter (e.g., ABCDE1234F)
//   - Income Tax Department logo clarity and color accuracy
//   - Background color: Standard blue or maroon
//   - Signature present and realistic
//   - QR code present (for new PAN cards)
//   - Font: Must be standard government font (not handwritten)
//
//   DRIVING LICENSE:
//   - License number format varies by state but follows pattern
//   - State transport authority logo present
//   - Hologram with state emblem visible
//   - Blood group field present
//   - License classes (e.g., MC, LMV) match format
//   - Issue and validity dates logical
//   - Photo with embossed state seal
//
//   VOTER ID:
//   - EPIC number format: 3 letters + 7 digits
//   - Election Commission hologram visible
//   - State-specific design elements
//   - Signature of Electoral Registration Officer
//   - Issue date present
//   - Holographic strip integrity
//
//   PASSPORT:
//   - Passport number: Letter + 7 digits
//   - Machine Readable Zone (MRZ) at bottom with correct format
//   - Republic of India emblem clarity
//   - Page number format
//   - Issue and expiry dates format
//   - Lamination quality for photo page
//   - UV reactive features (if detectable)
//
//5. DIGITAL MANIPULATION DETECTION:
//   - Clone stamp tool usage (repeated patterns)
//   - Healing brush artifacts
//   - Layer misalignment
//   - Resolution inconsistencies between regions
//   - Metadata manipulation signs
//   - Screenshot or photo-of-photo indicators (screen pixels, moiré patterns)
//
//6. BLACK & WHITE / LOW QUALITY INDICATORS:
//   - Document scanned in pure black & white (suspicious for color documents)
//   - Extreme contrast suggesting photocopied document
//   - Missing color security features
//   - Overly bright or washed out appearance
//   - Heavy noise or grain suggesting multiple generation copy
//
//ELA TAMPERING SCORE CALCULATION:
//Calculate a score from 0 to 100 based on:
//- 0-20: No tampering detected, authentic document
//- 21-40: Minor inconsistencies, possibly genuine with wear/scan artifacts
//- 41-60: Moderate concerns, document flagged for review
//- 61-80: High probability of tampering, multiple fraud indicators
//- 81-100: Definite forgery, multiple critical fraud indicators
//
//The score should reflect:
//- Number and severity of fraud indicators found
//- Compression artifacts and inconsistencies
//- Color/texture manipulation evidence
//- Missing or fake security features
//- Overall document quality mismatch
//
//PREDICTION RULES:
//- PASS: Score 0-30, no fraud indicators, all security features present
//- FLAGGED: Score 31-60, minor concerns, some inconsistencies, or missing optional features
//- FAIL: Score 61+, clear fraud indicators, or multiple critical failures
//
//EXTRACTED FIELDS (extract if readable):
//PAN: pan_number, name, father_name, date_of_birth
//DL: license_number, name, date_of_birth, validity, blood_group, state
//VOTER_ID: epic_number, name, father_name, date_of_birth, state
//PASSPORT: passport_number, name, date_of_birth, date_of_issue, date_of_expiry, place_of_birth
//
//CONFIDENCE SCORE:
//Calculate overall confidence (0.0-1.0) based on:
//- Image quality and clarity
//- Completeness of security features
//- Text readability
//- Detection certainty
//
//CRITICAL RULES:
//1. Be thorough - check ALL fraud indicators listed above
//2. Be conservative - if unsure, mark as FLAGGED rather than PASS
//3. Provide specific, actionable reasons (e.g., "PAN number format invalid: ABCD12345" not "number wrong")
//4. List ALL fraud indicators found, not just the first one
//5. Calculate ELA score based on actual visual evidence, not guessing
//6. Return ONLY the JSON object, no markdown formatting
//
//${if (expectedType != null) "EXPECTED DOCUMENT TYPE: $expectedType (verify this matches actual document)" else "AUTO-DETECT document type from image"}
//
//Return ONLY the JSON object.
//""".trimIndent()
//    }
private fun createDocumentVerificationPrompt(expectedType: DocumentType?): String = """
You are an expert document fraud detection system for Indian identity documents. Your goal is to detect FORGERIES and TAMPERING, not reject legitimate old or worn documents.

OUTPUT FORMAT (valid JSON only, no markdown):
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "specific explanation",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["specific issues"],
  "confidence": 0.0
}

=== CRITICAL: ACCEPTABLE vs FRAUDULENT ===

ACCEPTABLE (DO NOT FLAG):
✓ Age-related wear: fading, discoloration, yellowing
✓ Physical damage: creases, scratches, bent corners, stains
✓ Photo quality: blur from scanning, shadows, hand/finger visible in frame
✓ Lighting variations: uneven lighting, glare, shadows
✓ Scan artifacts: slight rotation, perspective distortion
✓ Natural degradation: ink fading, lamination wear
✓ Background in photo: wall, fabric, hand holding document

FRAUDULENT (FLAG THESE):
✗ Digital manipulation artifacts: clone stamp patterns, inconsistent pixels
✗ Pasted elements: rectangular overlays with sharp unnatural edges
✗ Font mismatches: different fonts for similar text fields
✗ Impossible shadows: shadows pointing in different directions
✗ Quality jumps: one region crisp while similar region is blurry
✗ Color banding: unnatural color transitions in logos/photos
✗ Format violations: wrong document number format, missing mandatory fields
✗ Screen capture indicators: visible pixels, moiré patterns
✗ Completely fabricated: flat backgrounds with no security features

=== FRAUD DETECTION FOCUS ===

1. DIGITAL TAMPERING (Primary Focus)
   - Clone stamp repetition patterns
   - Inconsistent JPEG compression between regions of same type
   - Unnatural edges around modified areas (NOT scan edges)
   - Color/lighting mismatches that defy physics
   - Fonts that don't match official templates

2. FABRICATED DOCUMENTS
   - Completely missing security features (when image quality is good)
   - Wrong templates or layouts
   - Format violations (document number, field positions)
   - Text misalignment that's not from physical wear

3. CONTENT VALIDATION
   - Document number format: must match regex patterns
   - Date logic: issue date before expiry, age matches DOB
   - Impossible combinations: future dates, invalid codes

=== DOCUMENT-SPECIFIC CHECKS ===

**PAN CARD** (Need 3+ passed for PASS, 2 for FLAGGED):
1. PAN format: ^[A-Z]{5}[0-9]{4}[A-Z]$ visible and readable
2. "INCOME TAX DEPARTMENT" or "Permanent Account Number" text present
3. Emblem visible (texture quality doesn't matter if aged/faded)
4. Photo present in correct position (quality doesn't matter)
5. Signature field present
⚠️ Ignore: fading, stains, discoloration, worn lamination

**DRIVING LICENSE** (Need 3+ passed):
1. License number format valid (state-specific)
2. State transport logo/text visible
3. Vehicle class codes present
4. Photo in correct position
5. Validity dates present
⚠️ Ignore: wear, fading, creases, dirt

**VOTER ID** (Need 3+ passed):
1. EPIC format: ^[A-Z]{3}[0-9]{7}$ readable
2. "ELECTION COMMISSION" text or logo visible
3. Photo present
4. Hologram area present (even if faded/damaged)
5. Officer signature/name present
⚠️ Ignore: heavy wear, discoloration, fading

**PASSPORT** (Need 4+ passed):
1. Passport number: ^[A-Z][0-9]{7}$ visible
2. "REPUBLIC OF INDIA" and emblem visible
3. MRZ (two lines of text/numbers at bottom) present and format-valid
4. Bilingual text (English/Hindi) present
5. Guilloché background pattern visible
6. All mandatory fields present (name, DOB, dates)
⚠️ Ignore: finger in photo, photo angle, lighting, shadows, natural wear.

INNER/ADDRESS page (set page_type="INNER"; PASS ≥3):
- Bilingual labels such as "Name of Father / Legal Guardian", "Name of Mother", "Name of Spouse", "Address".
- Guilloché background / micro-pattern.
- Perforation dots along page edge.
- Top-right barcode with human-readable code.
- "File No." field with alphanumeric value.
Extract if visible: father_name, mother_name, spouse_name, address, pin_code, state, file_no, barcode.

=== SCORING LOGIC ===

**ela_tampering_score** (0-100):
- 0-20: Clean authentic document
- 21-35: Normal wear/scan artifacts only
- 36-50: Minor concerns requiring human review
- 51-75: Moderate tampering indicators
- 76-100: Clear digital manipulation

**confidence** (0.0-1.0):
Rate how clearly you can assess (NOT document quality):
- 0.9-1.0: All critical features clearly visible
- 0.7-0.8: Most features visible despite wear/blur
- 0.5-0.6: Some features obscured but assessable
- 0.3-0.4: Poor image quality affecting assessment
- 0.0-0.2: Cannot assess reliably

**prediction**:
- PASS: 
  * ela_score ≤35 AND
  * Required document checks passed AND
  * No clear fraud indicators AND
  * confidence ≥0.5
  
- FLAGGED:
  * ela_score 36-50 OR
  * Minimum checks passed but quality concerns OR
  * Minor inconsistencies needing human review OR
  * confidence 0.4-0.5
  
- FAIL:
  * ela_score >50 AND clear digital manipulation evidence OR
  * Format validation failed (document number) OR
  * <minimum required checks passed OR
  * Critical fraud indicators present OR
  * confidence <0.4 with quality preventing assessment

=== FRAUD INDICATORS - BE SPECIFIC ===

Good examples:
✓ "PAN number shows two different font styles: 'ABCDE' in Arial vs '1234F' in Times New Roman"
✓ "Photo has perfect rectangular edges with no shadow/bleed, but document shows natural wear elsewhere"
✓ "Emblem shows clone stamp pattern - identical pixel blocks repeated in 3x3 grid"
✓ "Background behind photo is solid #FFFFFF while rest of card has textured pattern"

Bad examples (too vague):
✗ "Document appears old" (age is normal!)
✗ "Low quality image" (not fraud!)
✗ "Possible tampering" (be specific!)
✗ "Faded colors" (normal wear!)

=== FAIL TRIGGERS (High Confidence Required) ===

Only FAIL if you have STRONG evidence:
- Document number format completely wrong (impossible format)
- Clear copy-paste artifacts with pixel-level evidence
- Multiple text fields in obviously different fonts
- Photo shows screen pixels/moiré pattern (photo of screen)
- Shadows on photo/emblem point different directions than document shadows
- Missing ALL security features in otherwise clear image

DO NOT FAIL for:
- Old/worn appearance
- Stains, creases, discoloration
- Faded text/colors
- Photo taken at angle
- Hand/finger visible
- Poor scanning quality
- Natural physical damage

${if (expectedType != null) "\n=== EXPECTED: $expectedType ===\nBias toward passing if matches expected type and shows no clear manipulation.\n" else ""}

IMPORTANT: Err on the side of PASS for worn but legitimate documents. Only FAIL with strong evidence of digital forgery or format violations.
""".trimIndent()

/*
    private fun createDocumentVerificationPrompt(expectedType: DocumentType?): String = """
You are an expert document fraud detector for Indian IDs. Goal: catch FORGERIES/TAMPERING while NOT rejecting legitimately old, worn, or poorly scanned documents.

RETURN ONLY VALID JSON (no markdown, no backticks).
OUTPUT FORMAT (valid JSON only, no markdown):
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "specific explanation",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["specific issues"],
  "confidence": 0.0
}

ACCEPTABLE (do NOT flag): normal scan blur/shadows, slight rotation, age wear (fading/creases/yellowing), perspective distortion, hand/finger in frame, uneven lighting, lamination wear.
FRAUDULENT (flag/failed): clone-stamp patterns, pasted rectangles with sharp edges, mismatched fonts/kerning, impossible shadows, region-specific quality jumps, unnatural color banding, wrong number format, missing ALL security features in otherwise clear image.

PRIMARY CHECKS
1) DIGITAL TAMPERING: repeated patches, inconsistent JPEG compression, unnatural edges, lighting that defies physics.
2) FABRICATION: wrong template/layout, missing/hard-wrong security features, text alignment that cannot be due to wear.
3) CONTENT VALIDATION: number/date regexes and logic.

DOCUMENT-SPECIFIC (count feature_score from items visibly satisfied)

PAN  (PASS needs ≥3; FLAGGED if 2)
- PAN regex ^[A-Z]{5}[0-9]{4}[A-Z]$ (no spaces).
- "INCOME TAX DEPARTMENT" or "Permanent Account Number" present.
- Govt emblem/hologram present (texture may be faint).
- Photo present in expected zone.
- Signature field present.
IMMEDIATE FAIL if: flat single-color background with no texture; clearly pasted rectangles; PAN regex fails.

DRIVING_LICENSE (PASS ≥3)
- Valid license number (state-specific pattern acceptable).
- State transport logo/text.
- Vehicle class codes.
- Photo in expected zone.
- Issue/validity dates.

VOTER_ID (PASS ≥3)
- EPIC regex ^[A-Z]{3}[0-9]{7}$ readable.
- ECI logo/text.
- Photo present.
- Hologram area (even if faded).
- Officer signature/name.

PASSPORT
BIO page (PASS ≥4):
- Passport number regex ^[A-Z][0-9]{7}$.
- "REPUBLIC OF INDIA" + emblem.
- MRZ (two lines) format-valid.
- Bilingual text (English/Hindi).
- Guilloché background.
- Mandatory fields (name, DOB, dates) visible.

INNER/ADDRESS page (set page_type="INNER"; PASS ≥3):
- Bilingual labels such as "Name of Father / Legal Guardian", "Name of Mother", "Name of Spouse", "Address".
- Guilloché background / micro-pattern.
- Perforation dots along page edge.
- Top-right barcode with human-readable code.
- "File No." field with alphanumeric value.
Extract if visible: father_name, mother_name, spouse_name, address, pin_code, state, file_no, barcode.

SCORING & DECISION
- ela_tampering_score (0–100): 0–20 clean; 21–35 normal wear; 36–50 minor concerns; 51–75 moderate tampering; 76–100 clear manipulation.
- confidence (0.0–1.0): how certain you are in the assessment (not image beauty).

prediction:
PASS if:
- ela_tampering_score ≤ 35 AND
- feature_score meets PASS threshold for the detected doc/page AND
- fraud_indicators is empty AND
- confidence ≥ 0.5

FLAGGED if:
- ela_tampering_score 36–50 OR
- minimum checks barely met with quality concerns OR
- minor inconsistencies needing human review OR
- confidence 0.4–0.5 OR
- document_type is UNKNOWN but features suggest an Indian ID (no strong tampering)

FAIL only if (high confidence):
- ela_tampering_score > 50 WITH concrete manipulation evidence OR
- critical format validation fails (e.g., PAN regex fails) OR
- feature_score below minimum AND no credible ID features OR
- clear pasted rectangles/flat fabricated background OR
- multiple hard fraud indicators.
Note: “photo of a screen / moiré” alone is FLAGGED, not FAIL (unless combined with other evidence).

${if (expectedType != null) "EXPECTED: $expectedType — if detected type matches and no strong manipulation, prefer PASS/FLAGGED over FAIL." else ""}

IMPORTANT: Do not penalize natural wear or routine scanning issues. Be specific in fraud_indicators (what, where, why).
""".trimIndent()
*/
}