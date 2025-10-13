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
Verify PAN Card authenticity. PAN is CRITICAL - be STRICT. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0,
  "personal_info": {
    "name": "string|null",
    "id_number": "PAN or null",
    "dob": "YYYY-MM-DD|null"
  }
}

ACCEPTABLE (ignore):
✓ Age wear, fading, creases, stains, yellowing
✓ Scan blur, shadows, glare, slight rotation
✓ Worn lamination, ink fading
✓ Natural physical damage, folded corners

FRAUD (flag immediately):
✗ Flat/uniform solid color background (no texture, no pattern variation)
✗ Computer-generated appearance (perfect fonts, no printing texture)
✗ Screen-printed or digitally fabricated (pixel halos, RGB sub-pixel artifacts)
✗ Text floating without background texture underneath
✗ Clone stamp patterns, copy-pasted rectangles with sharp edges
✗ Font inconsistencies (different fonts for similar fields)
✗ Wrong PAN format (not ^[A-Z]{5}[0-9]{4}[A-Z]$)
✗ Missing hologram/emblem/watermark in clear image
✗ Unnatural shadows or lighting inconsistencies
✗ White/grey rectangular boxes around text fields

MANDATORY SECURITY FEATURES (all 4 required for PASS):
1. Background texture/pattern visible (not flat solid color)
2. Government emblem/hologram present (even if faded)
3. "INCOME TAX DEPARTMENT" or "Permanent Account Number" text
4. Printed texture visible (ink dots, printing artifacts) - NOT screen pixels

PAN CHECKS (PASS needs ALL 5):
1. PAN format: ^[A-Z]{5}[0-9]{4}[A-Z]$ visible
2. Background has texture/pattern (NOT flat grey/white)
3. Text integrated with background (NOT floating on solid color)
4. Photo present in correct position (if e-PAN, photo field visible)
5. Signature field present

DIGITAL FABRICATION RED FLAGS (any 1 = FAIL):
❌ Entire background is uniform solid color (RGB values nearly identical)
❌ Text appears perfectly sharp but background is blurry (inconsistent quality)
❌ No printing artifacts (too perfect = computer-generated)
❌ Text has pixel halos or RGB sub-pixel fringing (screen capture/edit)
❌ Fields appear as white/grey rectangles pasted on background
❌ Background pattern suddenly stops behind text (masking artifact)

PERSONAL INFO (extract if visible):
- name: printed cardholder name (uppercase as-is; trim spaces)
- id_number: the 10-char PAN (uppercase, no spaces)
- dob: normalize to YYYY-MM-DD if full date is readable; else null

CRITICAL RULES:
1. If background is flat solid color with no texture → ela_score = 70, FAIL
2. If text appears digitally generated (no printing texture) → ela_score = 60, FAIL
3. If security features ALL missing in clear image → ela_score = 80, FAIL
4. If only PAN format correct but lacks 3+ security features → FLAGGED at minimum

SCORING:
- ela_tampering_score: 0-30 PASS, 31-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0 (how clearly you can assess)
- PASS: score ≤30 AND ALL 5 PAN checks passed AND 4 mandatory security features present
- FLAGGED: score 31-50 OR 3-4 checks passed OR minor concerns
- FAIL: score >50 OR digital fabrication detected OR <3 checks passed

IMPORTANT: Be STRICT for PAN. When in doubt between PASS and FLAGGED, choose FLAGGED.
Digitally generated/fabricated PANs should NEVER PASS.
""".trimIndent()

    private fun createDrivingLicensePrompt(): String = """
Verify Driving License authenticity. DL is CRITICAL - be STRICT. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "DRIVING_LICENSE|PAN|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0,
  "personal_info": {
    "name": "string|null",
    "id_number": "DL number or null",
    "dob": "YYYY-MM-DD|null"
  }
}

ACCEPTABLE (ignore):
✓ Wear, fading, creases, dirt, bent corners, scratches
✓ Scan artifacts, blur, shadows, rotation, glare
✓ Natural physical damage, lamination wear
✓ Damaged hologram (but area still visible)
✓ Ink fading, discoloration

FRAUD (flag immediately):
✗ Flat/uniform solid color background (no card texture/pattern)
✗ Computer-generated appearance (perfect fonts, no printing texture)
✗ Screen-printed or digitally fabricated (pixel halos, RGB artifacts)
✗ Text floating without background integration
✗ Clone stamp patterns, copy-pasted rectangles with sharp edges
✗ Font inconsistencies (different fonts for similar fields)
✗ Invalid license number format (state-specific patterns violated)
✗ Missing hologram/embossed seal area entirely in clear image
✗ Unnatural shadows or lighting inconsistencies
✗ White/grey rectangular boxes around text fields

MANDATORY SECURITY FEATURES (all 4 required for PASS):
1. Card background texture/pattern visible (not flat solid color)
2. Hologram/embossed seal area present (even if worn, area must be visible)
3. State transport authority logo/text visible
4. Printed card texture visible (ink/plastic texture) - NOT screen pixels

DL CHECKS - FRONT (PASS needs ALL 5):
1. License number format valid (state-specific format acceptable)
2. Background has card texture/pattern (NOT flat color)
3. Text integrated with card background (NOT floating on solid color)
4. Photo present in correct position
5. Vehicle class codes present (MC, LMV, etc.) OR validity dates visible

— BACK SIDE (if back image is provided) —
Use these as **equivalent checks** (valid when only back is available):
PASS needs ≥4 of these back-side checks:
B1. DL No. and Issuing Authority repeated/print present (e.g., "DTO/SDM + State")
B2. Vehicle Class table present (MCWG/LMV rows with codes, dates, category)
B3. QR code or machine-readable block present
B4. Security hologram/embossed seal area visible (even if worn)
B5. Officer/Authority signature or facsimile present
B6. Special validity fields present (e.g., "Hazardous/Hill validity")
B7. Emergency contact/administrative text block present

DIGITAL FABRICATION RED FLAGS (any 1 = FAIL):
❌ Entire background is uniform solid color (RGB values nearly identical)
❌ Text appears perfectly sharp but card texture is missing (inconsistent)
❌ No printing/card texture artifacts (too perfect = digital)
❌ Text has pixel halos or RGB sub-pixel fringing (screen/edit artifact)
❌ Fields appear as white/grey rectangles pasted on background
❌ Security pattern suddenly stops behind text (masking artifact)
❌ Hologram/embossed area completely absent in otherwise clear image

PERSONAL INFO (extract if visible):
- name: cardholder name from front; if only back provided and name absent, return null
- id_number: license number (uppercase, preserve state format like "DL-0420110012345")
- dob: date of birth if printed; normalize to YYYY-MM-DD; if only validity dates visible, keep dob=null

CRITICAL RULES:
1. If background is flat solid color with no texture → ela_score = 70, FAIL
2. If text appears digitally generated (no card texture) → ela_score = 60, FAIL
3. If hologram/embossed area completely missing in clear image → ela_score = 75, FAIL
4. If only DL format correct but lacks 3+ security features → FLAGGED at minimum

SCORING:
- ela_tampering_score: 0-30 PASS, 31-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0 (how clearly you can assess)
- PASS: score ≤30 AND ALL 5 front checks (or ≥4 back checks) passed AND 4 mandatory security features present
- FLAGGED: score 31-50 OR 3-4 checks passed OR minor concerns
- FAIL: score >50 OR digital fabrication detected OR <3 checks passed

IMPORTANT: Be STRICT for Driving License. When in doubt between PASS and FLAGGED, choose FLAGGED.
Digitally generated/fabricated DLs should NEVER PASS.
""".trimIndent()

    private fun createVoterIDPrompt(): String = """
Verify Voter ID authenticity. Voter ID is CRITICAL - be STRICT. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "VOTER_ID|PAN|DRIVING_LICENSE|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0,
  "personal_info": {
    "name": "string|null",
    "id_number": "EPIC or null",
    "dob": "YYYY-MM-DD|null"
  }
}

ACCEPTABLE (ignore):
✓ Heavy wear, discoloration, fading, yellowing
✓ Damaged/worn hologram (but area still visible)
✓ Scan quality issues, shadows, blur, glare
✓ Worn edges, creases, lamination damage
✓ Natural physical degradation

FRAUD (flag immediately):
✗ Flat/uniform solid color background (no security pattern)
✗ Computer-generated appearance (perfect fonts, no card texture)
✗ Screen-printed or digitally fabricated (pixel halos, RGB artifacts)
✗ Text floating without background integration
✗ Clone stamp patterns, copy-pasted rectangles with sharp edges
✗ Font inconsistencies (different fonts for similar fields)
✗ Wrong EPIC format (not ^[A-Z]{3}[0-9]{7}$)
✗ Missing hologram area entirely in clear image
✗ Unnatural shadows or lighting inconsistencies
✗ White/grey rectangular boxes around text fields

MANDATORY SECURITY FEATURES (all 4 required for PASS):
1. Card background texture/pattern visible (not flat solid color)
2. Hologram area present (even if worn/faded, area must be visible)
3. "ELECTION COMMISSION" text or logo visible
4. Printed card texture visible (ink/card texture) - NOT screen pixels

VOTER ID CHECKS - FRONT (PASS needs ALL 5):
1. EPIC format: ^[A-Z]{3}[0-9]{7}$ readable and valid
2. Background has security pattern/texture (NOT flat color)
3. Text integrated with card background (NOT floating on solid color)
4. Photo present in correct position
5. Hologram area visible (even if faded/damaged)

— BACK SIDE (if back image is provided) —
Use these as **equivalent checks** (valid when only back is available):
PASS needs ≥4 of these back-side checks:
B1. Address block present and structured (house/street, PS/Tehsil, District, PIN)
B2. Date of issue present (dd-mm-yyyy or locale variant)
B3. Assembly Constituency No. & Name present (e.g., "122 - TINSUKIA (GEN)")
B4. Part No. & Name/Booth location present
B5. Electoral Registration Officer text/signature or facsimile present
B6. Official "Note/नोट" instructions block present (multi-language)
B7. Watermark or repeating microtext visible (e.g., "ELECTION COMMISSION OF INDIA")

DIGITAL FABRICATION RED FLAGS (any 1 = FAIL):
❌ Entire background is uniform solid color (RGB values nearly identical)
❌ Text appears perfectly sharp but card texture is missing (inconsistent)
❌ No printing/card texture artifacts (too perfect = digital)
❌ Text has pixel halos or RGB sub-pixel fringing (screen/edit artifact)
❌ Fields appear as white/grey rectangles pasted on background
❌ Security pattern suddenly stops behind text (masking artifact)
❌ Hologram area completely absent in otherwise clear image

PERSONAL INFO (extract if visible):
- name: voter name from front; if only back uploaded and name absent, return null
- id_number: EPIC (uppercase, no spaces, format ^[A-Z]{3}[0-9]{7}$)
- dob: if printed on card; normalize to YYYY-MM-DD; if age-only shown (e.g., "Age: 20 Yrs"), return null

CRITICAL RULES:
1. If background is flat solid color with no pattern → ela_score = 70, FAIL
2. If text appears digitally generated (no card texture) → ela_score = 60, FAIL
3. If hologram area completely missing in clear image → ela_score = 75, FAIL
4. If only EPIC format correct but lacks 3+ security features → FLAGGED at minimum

SCORING:
- ela_tampering_score: 0-30 PASS, 31-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0 (how clearly you can assess)
- PASS: score ≤30 AND ALL 5 front checks (or ≥4 back checks) passed AND 4 mandatory security features present
- FLAGGED: score 31-50 OR 3-4 checks passed OR minor concerns
- FAIL: score >50 OR digital fabrication detected OR <3 checks passed

IMPORTANT: Be STRICT for Voter ID. When in doubt between PASS and FLAGGED, choose FLAGGED.
Digitally generated/fabricated Voter IDs should NEVER PASS.
""".trimIndent()

    private fun createPassportPrompt(): String = """
Verify Passport authenticity. Passport is CRITICAL - be STRICT. Return ONLY valid JSON.

OUTPUT:
{
  "document_type": "PASSPORT|PAN|DRIVING_LICENSE|VOTER_ID|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "max 5 words",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["max 2 items, 4 words each"],
  "confidence": 0.0,
  "personal_info": {
    "name": "string|null",
    "id_number": "File number if present; otherwise Passport no. or null",
    "dob": "YYYY-MM-DD|null"
  }
}

ACCEPTABLE (ignore):
✓ Natural wear, fading, creases, yellowing
✓ Photo angle, finger in frame, shadows
✓ Scan artifacts, blur, glare, slight rotation
✓ Worn lamination, page wear
✓ Screen capture indicators (moiré pattern from display)
✓ Photos taken from mobile/desktop screens (DigiLocker, mPassport apps)
✓ Physical damage from normal use

FRAUD (flag immediately):
✗ Flat/uniform solid background (no Guilloché pattern/texture)
✗ Computer-generated appearance (perfect fonts, no printing texture)
✗ Digitally fabricated (pixel halos, RGB artifacts)
✗ Text floating without page integration
✗ Clone stamp patterns, pasted elements with sharp edges
✗ Font mismatches in printed fields (different fonts)
✗ Wrong passport number format (not ^[A-Z][0-9]{7}$)
✗ Invalid MRZ format (two-line format violations)
✗ Missing ALL mandatory security features in clear image
✗ Unnatural shadows or lighting inconsistencies
✗ White/grey rectangular boxes around fields

MANDATORY SECURITY FEATURES (all 4 required for PASS):
1. Guilloché background pattern visible (security print, not flat)
2. Government emblem present (Ashoka pillar)
3. "REPUBLIC OF INDIA" text visible
4. MRZ (Machine Readable Zone) - two lines at bottom

PASSPORT BIO PAGE CHECKS (PASS needs ALL 6):
1. Passport number: ^[A-Z][0-9]{7}$ visible and valid
2. Guilloché background pattern visible (NOT flat color)
3. Text integrated with page (NOT floating on solid background)
4. "REPUBLIC OF INDIA" and emblem visible
5. MRZ two-line format present and valid
6. Bilingual text (English/Hindi) present

INNER/ADDRESS PAGE CHECKS (if address page provided, PASS needs ≥4):
1. Bilingual labels visible (Father/Mother/Spouse/Address in English/Hindi)
2. Guilloché background pattern visible
3. Perforation dots along page edge
4. Top-right barcode with human-readable code present
5. "File No." field with alphanumeric value visible

DIGITAL FABRICATION RED FLAGS (any 1 = FAIL):
❌ Entire background is uniform solid color (no Guilloché pattern)
❌ Text appears perfectly sharp but background texture missing (inconsistent)
❌ No printing/page texture artifacts (too perfect = digital)
❌ Text has pixel halos or RGB sub-pixel fringing (editing artifact)
❌ Fields appear as white/grey rectangles pasted on background
❌ Guilloché pattern suddenly stops behind text (masking artifact)
❌ MRZ completely absent or format completely wrong in bio page

PERSONAL INFO (extract if visible):
- name: full name as printed on bio page (preserve case/spacing as-is)
- id_number: prioritize "File No." if address page; otherwise passport number (uppercase, no spaces)
- dob: extract from bio page DOB field; normalize to YYYY-MM-DD; if partial/unreadable, return null

CRITICAL RULES:
1. If background is flat solid color (no Guilloché) → ela_score = 70, FAIL
2. If text appears digitally generated (no page texture) → ela_score = 60, FAIL
3. If MRZ completely missing in bio page → ela_score = 75, FAIL
4. If only passport format correct but lacks 3+ security features → FLAGGED at minimum

SCORING:
- ela_tampering_score: 0-30 PASS, 31-50 FLAGGED, 51+ FAIL
- confidence: 0.0-1.0 (how clearly you can assess)
- PASS: score ≤30 AND ALL 6 bio checks (or ≥4 address checks) passed AND 4 mandatory security features present
- FLAGGED: score 31-50 OR 4-5 checks passed OR minor concerns OR screen capture artifacts
- FAIL: score >50 OR digital fabrication detected OR <4 checks passed

IMPORTANT: Be STRICT for Passport, but ALLOW screen captures from official apps (DigiLocker/mPassport).
When in doubt between PASS and FLAGGED, choose FLAGGED.
Digitally generated/fabricated passports should NEVER PASS.
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