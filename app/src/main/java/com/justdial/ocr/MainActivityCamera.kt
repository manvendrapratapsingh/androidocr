package com.justdial.ocr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.justdial.ocr.model.MainViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class  MainActivityCamera : AppCompatActivity() {

    private lateinit var resultInfo: TextView
    private lateinit var firstPageView: ImageView
    private lateinit var pageLimitInputView: EditText
    private lateinit var progressOverlay: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var enableGalleryImport = true
    private val FULL_MODE = "FULL"
    private val BASE_MODE = "BASE"
    private val BASE_MODE_WITH_FILTER = "BASE_WITH_FILTER"
    private var selectedMode = FULL_MODE
    private var selectedDocumentType = "Cheque"

    private val viewModel: MainViewModel by viewModels()

    // --- Google Sign-In Members ---
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var consentLauncher: ActivityResultLauncher<Intent>
    private var signedInAccount: GoogleSignInAccount? = null
    private var pendingImageUri: Uri? = null // To store the image uri while signing in
    private var pendingBitmap: Bitmap? = null // To store the bitmap while getting consent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_camera)
        resultInfo = findViewById<TextView>(R.id.result_info)!!
        firstPageView = findViewById<ImageView>(R.id.first_page_view)!!
        pageLimitInputView = findViewById(R.id.page_limit_input)
        progressOverlay = findViewById(R.id.progress_overlay)
        progressText = findViewById(R.id.progress_text)

        scannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                handleActivityResult(result)
            }

        setupGoogleSignIn()
        populateModeSelector()
        populateDocumentTypeSelector()
        observeViewModel()
        setupDocumentVerificationButton()
    }

    private fun populateDocumentTypeSelector() {
        val documentTypeSpinner = findViewById<Spinner>(R.id.document_type_spinner)
        val documentTypes = arrayOf("Cheque", "eNACH")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, documentTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        documentTypeSpinner.adapter = adapter
        documentTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedDocumentType = parent.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
    }
    
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("134377649404-g8dl23e9f530g6khi4mkbf05jd46o6nd.apps.googleusercontent.com")
            .requestEmail()
            .requestScopes(
                com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/cloud-platform"),
                com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/cloud-platform.read-only"),
                com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/generative-language")
            )
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } else {
                Toast.makeText(this, "Google Sign-In failed.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // NOTE: Consent launcher kept for compatibility but not needed for direct Gemini API
        consentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Toast.makeText(this, "Not needed for direct Gemini API", Toast.LENGTH_SHORT).show()
        }
        
        signedInAccount = GoogleSignIn.getLastSignedInAccount(this)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            signedInAccount = completedTask.getResult(ApiException::class.java)
            android.widget.Toast.makeText(this, "Signed in as ${signedInAccount?.email}", android.widget.Toast.LENGTH_SHORT).show()

            val idToken = signedInAccount?.idToken
            if (idToken != null && pendingImageUri != null) {
                proceedWithAnalysis(idToken, pendingImageUri!!)
                pendingImageUri = null // Clear the pending uri
            } else {
                android.widget.Toast.makeText(this, "Could not get ID Token or image after sign-in.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
            hideProgress()
            android.widget.Toast.makeText(this, "Sign-in failed. Code: ${e.statusCode}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun onEnableGalleryImportCheckboxClicked(view: View) {
        enableGalleryImport = (view as CheckBox).isChecked
    }

    @Suppress("UNUSED_PARAMETER")
    fun onScanButtonClicked(unused: View) {
        resultInfo.text = null
        Glide.with(this).clear(firstPageView)

        val options =
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setGalleryImportAllowed(enableGalleryImport)

        // ... (rest of the scan options setup is the same)

        GmsDocumentScanning.getClient(options.build())
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender: IntentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener() { e: Exception ->
                resultInfo.setText(getString(R.string.error_default_message, e.message))
            }
    }

    private fun populateModeSelector() {
        // ... (no changes needed here)
    }

    private fun handleActivityResult(activityResult: ActivityResult) {
        val resultCode = activityResult.resultCode
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        if (resultCode == Activity.RESULT_OK && result != null) {
            resultInfo.setText(getString(R.string.scan_result, result))

            val pages = result.pages
            if (pages != null && pages.isNotEmpty()) {
                val imageUri = pages[0].imageUri
                Glide.with(this).load(imageUri).into(firstPageView)

                // SIMPLIFIED: No more complex authentication needed!
                // Just call the OCR directly with Gemini API
                proceedWithAnalysis("dummy_token_not_needed", imageUri)
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            resultInfo.text = getString(R.string.error_scanner_cancelled)
        } else {
            resultInfo.text = getString(R.string.error_default_message)
        }
    }

    private fun proceedWithAnalysis(unused: String, imageUri: Uri) {
        val bitmap = uriToBitmap(this, imageUri)
        if (bitmap != null) {
            showProgress("Starting OCR processing...")
            Log.d(TAG, "Starting OCR analysis for image: $imageUri")
            Log.d(TAG, "Image size: ${bitmap.width}x${bitmap.height}")
            if (selectedDocumentType == "Cheque") {
                viewModel.processCheque(this@MainActivityCamera, bitmap, null)
            } else {
                viewModel.processENach(this@MainActivityCamera, bitmap, null)
            }
        } else {
            hideProgress()
            Toast.makeText(this, "❌ Failed to load image.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProgress(message: String) {
        progressText.text = message
        progressOverlay.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        progressOverlay.visibility = View.GONE
    }

    private fun updateProgress(message: String) {
        progressText.text = message
    }

    fun uriToBitmap(context: Context, imageUri: Uri): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                return ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                return MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun observeViewModel() {
        viewModel.ocrState.observe(this) { state ->
            when (state) {
                is MainViewModel.OcrUiState.Idle -> {
                    Log.d(TAG, "ViewModel state: Idle")
                    hideProgress()
                }
                is MainViewModel.OcrUiState.Loading -> {
                    Log.d(TAG, "ViewModel state: Loading - showing progress...")
                    updateProgress("Processing image...")
                }
                is MainViewModel.OcrUiState.Success -> {
                    Log.d(TAG, "ViewModel state: Success - OCR completed")
                    hideProgress()
                    Toast.makeText(this, "✅ OCR completed successfully!", Toast.LENGTH_SHORT).show()
                    resultInfo.text = "✅ OCR Success:\n${state.text}"
                }
                is MainViewModel.OcrUiState.ChequeSuccess -> {
                    Log.d(TAG, "ViewModel state: ChequeSuccess - Cheque processed")
                    hideProgress()
                    Toast.makeText(this, "✅ Cheque processed successfully!", Toast.LENGTH_LONG).show()
                    val fraudList = state.chequeData.fraudIndicators
                    val fraudText = if (fraudList.isEmpty()) "None" else fraudList.joinToString(
                        separator = "\n  - ",
                        prefix = "\n  - "
                    )

                    // Format enhanced signature verification information
                    val signatureInfo = buildString {
                        append("Signature Present: ${if (state.chequeData.signaturePresent) "Yes" else "No"}")
                        
                        if (state.chequeData.rotationApplied != 0) {
                            append("\nRotation Applied: ${state.chequeData.rotationApplied}°")
                        }
                        
                        if (state.chequeData.signatureCount > 0) {
                            append("\nTotal Signatures: ${state.chequeData.signatureCount}")
                            
                            // Show signature regions details
                            if (state.chequeData.signatureRegions.isNotEmpty()) {
                                append("\nSignature Regions:")
                                state.chequeData.signatureRegions.forEachIndexed { index, region ->
                                    append("\n  ${index + 1}. ${region.group.name.lowercase().replaceFirstChar { it.uppercase() }}")
                                    if (region.anchorText.isNotEmpty()) {
                                        append(" (${region.anchorText})")
                                    }
                                    if (region.evidence.isNotEmpty()) {
                                        append(" - ${region.evidence}")
                                    }
                                }
                            }
                            
                            // Multi-signature consistency analysis
                            if (state.chequeData.signatureCount >= 2) {
                                val consistency = when (state.chequeData.signaturesConsistent) {
                                    true -> "✅ Consistent"
                                    false -> "❌ Inconsistent"
                                    null -> "❓ Unclear"
                                }
                                append("\nSignatures Match: $consistency")
                                append("\nMatch Score: ${state.chequeData.signaturesMatchScore}%")

                            }
                            if (state.chequeData.signaturesNotes.isNotEmpty()) {
                                append("\nNotes: ${state.chequeData.signaturesNotes}")
                            }
                        }
                    }

                    val chequeInfo = """
                        ✅ CHEQUE OCR SUCCESS:
                        Account Holder: ${displayValue(state.chequeData.accountHolderName)}
                        Bank: ${displayValue(state.chequeData.bankName)}
                        Account Number: ${displayValue(state.chequeData.accountNumber)}
                        IFSC: ${displayValue(state.chequeData.ifscCode)}
                        MICR: ${displayValue(state.chequeData.micrCode)}
                        $signatureInfo
                        Document Quality: ${displayValue(state.chequeData.document_quality)}
                        Document Type: ${displayValue(state.chequeData.document_type)}
                        Fraud Indicators: $fraudText
                    """.trimIndent()
                    resultInfo.text = chequeInfo
                   /* val resultIntent = Intent().apply {
                        putExtra("result_json", Json.encodeToString(state.chequeData))
                        putExtra("document_type", "cheque")
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()*/
                }
                is MainViewModel.OcrUiState.ENachSuccess -> {
                    Log.d(TAG, "ViewModel state: ENachSuccess - NACH processed")
                    hideProgress()
                    Toast.makeText(this, "✅ E-NACH processed successfully!", Toast.LENGTH_LONG).show()
                    val fraudList = state.enachData.fraud_indicators
                    val fraudText = if (fraudList.isEmpty()) "None" else fraudList.joinToString(
                        separator = "\n  - ",
                        prefix = "\n  - "
                    )

                    // Format enhanced signature verification information
                    val signatureInfo = buildString {
                        append("Signature Present: ${if (state.enachData.customerSignature) "Yes" else "No"}")
                        
                        if (state.enachData.rotation_applied != 0) {
                            append("\nRotation Applied: ${state.enachData.rotation_applied}°")
                        }
                        
                        if (state.enachData.signature_count > 0) {
                            append("\nTotal Signatures: ${state.enachData.signature_count}")
                            
                            // Show signature breakdown by group
                            val groupCounts = mutableListOf<String>()
                            if (state.enachData.signature_count_payer > 0) {
                                groupCounts.add("Payer: ${state.enachData.signature_count_payer}")
                            }
                            if (state.enachData.signature_count_sponsor > 0) {
                                groupCounts.add("Sponsor: ${state.enachData.signature_count_sponsor}")
                            }
                            if (state.enachData.signature_count_unknown > 0) {
                                groupCounts.add("Unknown: ${state.enachData.signature_count_unknown}")
                            }
                            if (groupCounts.isNotEmpty()) {
                                append("\nBreakdown: ${groupCounts.joinToString(", ")}")
                            }
                            
                            // Show signature regions details
                            if (state.enachData.signature_regions.isNotEmpty()) {
                                append("\nSignature Regions:")
                                state.enachData.signature_regions.forEachIndexed { index, region ->
                                    append("\n  ${index + 1}. ${region.group.name.lowercase().replaceFirstChar { it.uppercase() }}")
                                    if (region.anchorText.isNotEmpty()) {
                                        append(" (${region.anchorText})")
                                    }
                                    if (region.evidence.isNotEmpty()) {
                                        append(" - ${region.evidence}")
                                    }
                                }
                            }
                            
                            // Show expectations and missing signatures
                            if (state.enachData.expected_signatures.payer > 0 || state.enachData.expected_signatures.sponsor > 0) {
                                append("\nExpected: Payer(${state.enachData.expected_signatures.payer}) Sponsor(${state.enachData.expected_signatures.sponsor})")
                            }
                            
                            if (state.enachData.missing_expected_signatures.isNotEmpty()) {
                                append("\nMissing: ${state.enachData.missing_expected_signatures.joinToString(", ")}")
                            }
                            
                            // Multi-signature consistency analysis
                            if (state.enachData.signature_count >= 2) {
                                val consistency = when (state.enachData.signatures_consistent) {
                                    true -> "✅ Consistent"
                                    false -> "❌ Inconsistent"
                                    null -> "❓ Unclear"
                                }
                                append("\nSignatures Match: $consistency")
                                append("\nMatch Score: ${state.enachData.signatures_match_score}%")

                            }
                            if (state.enachData.signatures_notes.isNotEmpty()) {
                                append("\nNotes: ${state.enachData.signatures_notes}")
                            }
                        }
                    }

                    val nachInfo = """
                        ✅ E-NACH OCR SUCCESS:
                        Utility: ${displayValue(state.enachData.utilityName)}
                        Account: ${displayValue(state.enachData.accountNumber)}
                        Holder: ${displayValue(state.enachData.accountHolderName)}
                        Bank: ${displayValue(state.enachData.bankName)}
                        IFSC: ${displayValue(state.enachData.ifscCode)}
                        MICR: ${displayValue(state.enachData.micr_code)}
                        $signatureInfo
                        Document Quality: ${displayValue(state.enachData.document_quality)}
                        Document Type: ${displayValue(state.enachData.document_type)}
                        Fraud Indicators: $fraudText
                    """.trimIndent()
                    resultInfo.text = nachInfo
                }
                is MainViewModel.OcrUiState.CrossValidationSuccess -> {
                    Log.d(TAG, "ViewModel state: CrossValidationSuccess - Both documents processed")
                    hideProgress()
                    Toast.makeText(this, "✅ Cross-validation completed!", Toast.LENGTH_LONG).show()
                    resultInfo.text = "✅ Cross-validation completed successfully!"
                }
                is MainViewModel.OcrUiState.Error -> {
                    Log.e(TAG, "ViewModel state: Error - ${state.message}")
                    hideProgress()
                    Toast.makeText(this, "❌ OCR Error: ${state.message}", Toast.LENGTH_LONG).show()
                    resultInfo.text = "❌ ERROR: ${state.message}\n\nCheck logs for detailed debugging info."
                }
            }
        }
    }

    private fun setupDocumentVerificationButton() {
        findViewById<android.widget.Button>(R.id.btn_document_verification)?.setOnClickListener {
            val intent = Intent(this, com.justdial.ocr.documentverification.ui.DocumentVerificationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun displayValue(value: String?): String {
        val v = value?.trim()
        return if (v.isNullOrEmpty() || v.equals("null", ignoreCase = true)) "Not found" else v
    }

    companion object {
        private const val TAG = "MurashidTest"
    }
}
