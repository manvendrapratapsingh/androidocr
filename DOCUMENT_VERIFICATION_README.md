# Document Verification Module

## Overview
Complete document verification system for Indian identity documents (PAN, Driving License, Voter ID, Passport) using Firebase Vertex AI with India region compliance.

## Architecture

### Core Components

```
/documentverification
  /model
    - DocumentType.kt          (Enums and data models)
  /service
    - DocumentVerificationService.kt      (Main orchestrator)
    - FirebaseDocumentAIService.kt        (Firebase AI integration)
  /ui
    - DocumentVerificationActivity.kt     (Main UI)
    - DocumentResultAdapter.kt            (RecyclerView adapter)
```

### Key Features

✅ **Zero Breaking Changes**: Completely isolated from existing cheque/e-NACH code
✅ **AI-Powered Fraud Detection**: Comprehensive fraud analysis using Gemini 2.5 Flash
✅ **ELA Tampering Score**: AI-estimated tampering score (0-100)
✅ **Multi-Document Support**: PAN, DL, Voter ID, Passport
✅ **Real-Time Results**: In-memory display with no persistence
✅ **Region Compliance**: Uses asia-south1 (India) region

## Fraud Detection Capabilities

### Physical Tampering
- Color inconsistencies
- Mismatched fonts/text alignment
- Visible cut-and-paste artifacts
- Shadow inconsistencies
- JPEG compression artifacts
- Blurred regions from digital editing

### Printing & Material
- Handwritten/home-printed documents
- Missing security features (holograms, watermarks, microprints)
- Incorrect document dimensions
- Wrong paper quality

### Content Validation
- Document number format verification
- Invalid dates
- Photo quality analysis
- Photo alignment check

### Document-Specific Checks

**PAN Card:**
- PAN format: 5 letters + 4 digits + 1 letter
- Income Tax logo verification
- Background color validation
- QR code presence

**Driving License:**
- State-specific format
- Transport authority logo
- Hologram verification
- License classes validation

**Voter ID:**
- EPIC number format: 3 letters + 7 digits
- Election Commission hologram
- State design elements

**Passport:**
- Passport number: Letter + 7 digits
- MRZ (Machine Readable Zone) validation
- Republic of India emblem
- Lamination quality

## Usage

### Launch from MainActivityCamera

```kotlin
// Button added to main screen
"Document Verification" button → Opens DocumentVerificationActivity
```

### Flow

1. **Upload Document**
   - Camera capture
   - Gallery selection

2. **Analyze**
   - Click "Analyze Document"
   - AI processes image
   - Returns structured result

3. **View Results**
   - Document type (auto-detected)
   - Status: PASS / FLAGGED / FAIL
   - ELA tampering score
   - Detailed reason
   - Fraud indicators list
   - Extracted fields (name, number, etc.)

## API Response Structure

```json
{
  "image_url": "",
  "document_type": "PAN|DRIVING_LICENSE|VOTER_ID|PASSPORT",
  "prediction": "PASS|FLAGGED|FAIL",
  "reason": "Detailed explanation",
  "ela_tampering_score": 22.35,
  "fraud_indicators": ["List of issues found"],
  "extracted_fields": {
    "field_name": "value"
  },
  "confidence": 0.95
}
```

## ELA Score Interpretation

- **0-20**: Authentic document, no tampering
- **21-40**: Minor inconsistencies, likely genuine
- **41-60**: Moderate concerns, flagged for review
- **61-80**: High probability of tampering
- **81-100**: Definite forgery

## Prediction Logic

- **PASS**: Score 0-30, no fraud indicators
- **FLAGGED**: Score 31-60, some inconsistencies
- **FAIL**: Score 61+, clear fraud indicators

## Integration Points

### New Files Created
```
app/src/main/java/com/justdial/ocr/documentverification/
  ├── model/DocumentType.kt
  ├── service/DocumentVerificationService.kt
  ├── service/FirebaseDocumentAIService.kt
  └── ui/DocumentVerificationActivity.kt
  └── ui/DocumentResultAdapter.kt

app/src/main/res/layout/
  ├── activity_document_verification.xml
  └── item_document_result.xml
```

### Modified Files
```
app/src/main/AndroidManifest.xml
  - Added DocumentVerificationActivity

app/src/main/java/com/justdial/ocr/MainActivityCamera.kt
  - Added setupDocumentVerificationButton()

app/src/main/res/layout/activity_main_camera.xml
  - Added btn_document_verification button
```

## Testing

### Test with Sample Documents

1. **PAN Card**
   - Valid PAN format: ABCDE1234F
   - Blue/maroon background
   - Income Tax logo

2. **Driving License**
   - State transport authority logo
   - License classes visible
   - Hologram present

3. **Voter ID**
   - EPIC number format
   - Election Commission hologram
   - State-specific design

4. **Passport**
   - MRZ visible at bottom
   - Republic of India emblem
   - Valid passport number format

### Expected Behavior

✅ **Genuine Documents**: Low ELA score (0-30), PASS status
⚠️ **Suspicious Documents**: Medium score (31-60), FLAGGED
❌ **Forged Documents**: High score (61+), FAIL with fraud indicators

## Logging

All operations logged with tag:
- `DocumentVerificationService`
- `FirebaseDocumentAI`
- `DocumentVerification` (Activity)

Check Logcat for detailed debugging info.

## Troubleshooting

### Firebase Not Initialized
```kotlin
// Service auto-initializes on first use
// If fails, check Firebase configuration
```

### Image Loading Failed
- Check camera/gallery permissions
- Verify image file is readable
- Check image format (JPEG/PNG supported)

### AI Response Parsing Error
- Response format logged in Logcat
- Check for markdown formatting in response
- Verify JSON structure

## Future Enhancements

- [ ] Batch document processing
- [ ] CSV/JSON export functionality
- [ ] Local database persistence
- [ ] Aadhaar card support
- [ ] Real ELA algorithm implementation
- [ ] Compare with reference database
- [ ] Historical analysis trends

## Security & Compliance

✅ India region (asia-south1)
✅ No data stored locally
✅ Firebase App Check integration ready
✅ No API keys in plaintext

---

**Status**: Production Ready
**Last Updated**: 2025-01-08
**Version**: 1.0.0