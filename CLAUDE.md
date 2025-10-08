# JustdialOCR — Firebase Vertex AI (India Region, Client-to-Client)

## Overview
Android OCR for Indian bank Cheques and e‑NACH mandates using Firebase AI Logic on Google Vertex AI. Runs client‑to‑client with India data residency (asia‑south1) and App Check protection.

## Why This Architecture
- Secure India region: Vertex AI via `asia-south1` using Firebase AI Logic.
- Direct client‑to‑client: No custom backend needed; Firebase handles auth.
- Lower risk: App Check blocks abuse; no API keys shipped in plaintext.
- Multimodal: Image + prompt to Gemini for structured JSON extraction.

## Dependencies
```gradle
implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
implementation("com.google.firebase:firebase-ai")
implementation("com.google.firebase:firebase-appcheck")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("com.google.mlkit:document-scanner:16.0.0-beta1")
```

## Data Residency & Security
- Region: `asia-south1` (Mumbai) configured via `GenerativeBackend.vertexAI(location = "asia-south1")`.
- App integrity: Firebase App Check required for all model calls.
- No server proxy: All processing occurs on-device → Firebase → Vertex AI in India region.

## Service Skeleton
```kotlin
class FirebaseAIService {
  fun initializeService(context: Context)
  fun isServiceInitialized(): Boolean
  fun validateRegionalCompliance(): Boolean // expects true for asia-south1
  suspend fun processCheque(context: Context, image: ByteArray, prompt: String): Result<ChequeOCRData>
  suspend fun processENach(context: Context, image: ByteArray, prompt: String): Result<ENachOCRData>
}
```

## Model & Region
- Model: `gemini-2.5-flash` (speed) or `gemini-2.5-pro` (accuracy).
- Backend: `GenerativeBackend.vertexAI(location = "asia-south1")`.

## Prompt: Cheque OCR + Fraud Detection
Return only valid JSON. Focus on the printed account owner name (not the handwritten payee). Detect handwritten/fake cheques and list concrete reasons.

JSON schema (added fraud field):
```json
{
  "account_holder_name": "printed owner name (not payee)",
  "bank_name": "Bank name",
  "account_number": "Account number",
  "ifsc_code": "IFSC code",
  "micr_code": "MICR code",
  "signature_present": true,
  "document_quality": "good|poor|blurry|glare|cropped",
  "document_type": "original|photocopy|handwritten|printed",
  "fraud_indicators": ["array of visible issues or empty"]
}
```

Fraud detection guidance (examples to consider; list only those visible):
- Entire cheque appears handwritten on plain paper (no MICR band, no bank logo, no pre‑printed fields).
- Missing/forged MICR line; MICR font/spacing inconsistent or 9‑digit code absent.
- Bank logo/watermark/microprint absent or low‑quality copy; background pattern inconsistent.
- Layout mismatch with Indian cheque standards; misaligned boxes/fields; unusual margins.
- IFSC/MICR/bank name inconsistency (e.g., IFSC bank ≠ printed bank, MICR region mismatch).
- Overwrites, erasures, inconsistent ink or multiple handwriting styles in fixed printed areas.
- Signature looks copy‑pasted or floating without pen pressure artifacts.

Output rules:
- Return ONLY valid JSON (no markdown or prose).
- If no fraud is suspected, set "fraud_indicators": [].
- Be conservative: cite only evidence visible in the image.

The app’s `createChequePrompt` includes the above schema and rules.

## e‑NACH Prompt
Similar schema without fraud indicators (optional), with mandatory signature presence and standard fields.

## React Native Integration

### Overview
The OCR app now supports integration with React Native apps via intent-based communication. External React Native apps can launch the OCR processing and receive JSON results.

### Integration Flow
1. **React Native app** calls native module `OCRNativeModule.processCheque()`
2. **OCRNativeModule** launches `OCRResultActivity` via intent action `com.justdial.ocr.PROCESS_DOCUMENT`
3. **OCRResultActivity** launches `MainActivityCamera` for image capture
4. **MainActivityCamera** captures image, processes with Firebase AI, returns JSON result
5. **OCRResultActivity** passes result back to React Native app
6. **React Native app** receives and displays OCR results

### React Native Setup (JdReactNativeSample)
Key files for React Native integration:
- `android/app/src/main/java/com/jdreactnativesample/OCRNativeModule.kt` — Native bridge module
- `android/app/src/main/java/com/jdreactnativesample/OCRNativePackage.kt` — Module registration  
- `android/app/src/main/java/com/jdreactnativesample/MainApplication.kt` — Package registration
- `App.tsx` — React Native UI with OCR buttons

### Usage from React Native
```javascript
import { NativeModules } from 'react-native';
const { OCRNative } = NativeModules;

// Process cheque
const result = await OCRNative.processCheque();
console.log('OCR Result:', result); // JSON string with cheque data

// Process e-NACH  
const enachResult = await OCRNative.processENach();
```

### Intent Configuration
OCRResultActivity is configured to receive external intents:
```xml
<activity android:name=".OCRResultActivity" android:exported="true">
    <intent-filter>
        <action android:name="com.justdial.ocr.PROCESS_DOCUMENT" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### Result Format
Returns JSON string with complete cheque/e-NACH data:
```json
{
  "account_holder_name": "JOHN DOE",
  "bank_name": "STATE BANK OF INDIA",
  "account_number": "1234567890",
  "ifsc_code": "SBIN0001234",
  "micr_code": "110002001",
  "signature_present": true,
  "document_quality": "good",
  "document_type": "printed",
  "fraud_indicators": []
}
```

## Document Verification Module (NEW)

### Overview
Standalone document verification system for Indian identity documents (PAN, DL, Voter ID, Passport) using Firebase Vertex AI with comprehensive fraud detection.

### Architecture
```
/documentverification
  /model
    - DocumentType.kt          (Enums: PAN, DL, VOTER_ID, PASSPORT)
    - DocumentAnalysisResult   (Fraud analysis results)
  /service
    - DocumentVerificationService.kt    (Main orchestrator)
    - FirebaseDocumentAIService.kt      (Separate Firebase AI client)
  /ui
    - DocumentVerificationActivity.kt   (ML Kit scanner integration)
    - DocumentResultAdapter.kt          (RecyclerView results display)
```

### Key Features
- **Zero Breaking Changes**: Completely isolated from cheque/e-NACH code
- **ML Kit Scanner**: Same document scanner as MainActivityCamera (boundary detection, auto-crop)
- **AI Fraud Detection**: Gemini 2.5-flash with optimized prompt (70% token reduction)
- **ELA Tampering Score**: AI-estimated score (0-100) for digital manipulation
- **Multi-Document Support**: Auto-detect PAN/DL/Voter ID/Passport (including inner pages)
- **Real-time Results**: In-memory display with card-based RecyclerView
- **Region Compliant**: Uses asia-south1 endpoint

### JSON Output Schema
```json
{
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT|UNKNOWN",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "specific explanation",
  "ela_tampering_score": 0.0,
  "fraud_indicators": ["specific issues"],
  "confidence": 0.0
}
```

### Fraud Detection Focus
- **Digital Tampering**: Clone stamps, pasted rectangles, font mismatches, inconsistent compression
- **Fabricated Documents**: Missing security features, wrong formats, flat backgrounds
- **Content Validation**: Document number regex, date logic, field alignment
- **Does NOT penalize**: Blur, glare, shadows, age wear, fading, physical damage, scan artifacts

### Scoring Rules
- **PASS**: Score ≤35, no fraud indicators, document type identified
- **FLAGGED**: Score 36-50, 1-2 fraud indicators
- **FAIL**: Score >50, 3+ fraud indicators, or format violations

### Usage
1. Launch from MainActivityCamera → "Document Verification" button
2. Scan with ML Kit (camera) or select from gallery
3. Auto-analyze with Gemini 2.5-flash
4. View results: thumbnail, type, status (color-coded), tampering score, fraud indicators

### Files Added
- `app/src/main/java/com/justdial/ocr/documentverification/model/DocumentType.kt`
- `app/src/main/java/com/justdial/ocr/documentverification/service/DocumentVerificationService.kt`
- `app/src/main/java/com/justdial/ocr/documentverification/service/FirebaseDocumentAIService.kt`
- `app/src/main/java/com/justdial/ocr/documentverification/ui/DocumentVerificationActivity.kt`
- `app/src/main/java/com/justdial/ocr/documentverification/ui/DocumentResultAdapter.kt`
- `app/src/main/res/layout/activity_document_verification.xml`
- `app/src/main/res/layout/item_document_result.xml`

## Files touched in this repo
- `app/src/main/java/com/justdial/ocr/service/DocumentProcessorService.kt` — holds `createChequePrompt` and `createENachPrompt`.
- `app/src/main/java/com/justdial/ocr/service/FirebaseAIService.kt` — image→model call, JSON parsing.
- `app/src/main/java/com/justdial/ocr/model/ChequeOCRData.kt` — data model (now includes `fraudIndicators`).
- `app/src/main/java/com/justdial/ocr/OCRResultActivity.kt` — external intent handler, returns JSON results.
- `app/src/main/java/com/justdial/ocr/MainActivityCamera.kt` — camera capture, OCR processing, result serialization, document verification button.
- `app/src/main/AndroidManifest.xml` — intent filter configuration for external access, DocumentVerificationActivity registration.

## Build & Test
```bash
./gradlew assembleDebug
./gradlew lint
./gradlew compileDebugKotlin
```

## Status
- Region: `asia-south1` (Mumbai)
- Path: Client→Firebase AI Logic→Vertex AI (no custom backend)
- React Native Integration: ✅ Complete (real OCR processing with camera capture)
- External API: Intent-based via `com.justdial.ocr.PROCESS_DOCUMENT`
- Document Verification: ✅ Complete (PAN/DL/Voter ID/Passport fraud detection)

---
Last updated: January 8, 2025
Status: Firebase Vertex AI (client‑to‑client, India region) + React Native Integration + Document Verification
- to memorise