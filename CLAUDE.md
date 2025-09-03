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

## Files touched in this repo
- `app/src/main/java/com/justdial/ocr/service/DocumentProcessorService.kt` — holds `createChequePrompt` and `createENachPrompt`.
- `app/src/main/java/com/justdial/ocr/service/FirebaseAIService.kt` — image→model call, JSON parsing.
- `app/src/main/java/com/justdial/ocr/model/ChequeOCRData.kt` — data model (now includes `fraudIndicators`).

## Build & Test
```bash
./gradlew assembleDebug
./gradlew lint
./gradlew compileDebugKotlin
```

## Status
- Region: `asia-south1` (Mumbai)
- Path: Client→Firebase AI Logic→Vertex AI (no custom backend)
- Next: Validate App Check in production and monitor fraud indicator rate

---
Last updated: Sept 3, 2025
Status: Firebase Vertex AI (client‑to‑client, India region)
