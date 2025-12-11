package com.justdial.ocr.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justdial.ocr.service.DocumentProcessorService
import com.justdial.ocr.validation.ValidationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MainViewModel : ViewModel() {

    sealed class OcrUiState {
        object Idle : OcrUiState()
        object Loading : OcrUiState()
        data class Success(val text: String) : OcrUiState()
        data class ChequeSuccess(val chequeData: ChequeOCRData, val validation: ChequeValidationResult) : OcrUiState()
        data class ENachSuccess(val enachData: ENachOCRData, val validation: ENachValidationResult) : OcrUiState()
        data class CrossValidationSuccess(val result: DocumentCrossValidationResult) : OcrUiState()
        data class Error(val message: String) : OcrUiState()
    }

    private val _ocrState = MutableLiveData<OcrUiState>(OcrUiState.Idle)
    val ocrState: LiveData<OcrUiState> = _ocrState

    private val documentProcessor = DocumentProcessorService()

    private var lastChequeData: ChequeOCRData? = null
    private var lastENachData: ENachOCRData? = null

    fun processCheque(context: Context, fullBitmap: Bitmap?, cropRect: Rect?) {
        _ocrState.value = OcrUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jpegBytes = bitmapToByteArray(fullBitmap, cropRect)
                val result = documentProcessor.processCheque(context, jpegBytes)

                result.onSuccess { chequeData ->
                    lastChequeData = chequeData
                    val validation = ValidationEngine().validateCheque(chequeData)
                    _ocrState.postValue(OcrUiState.ChequeSuccess(chequeData, validation))
                }.onFailure { error ->
                    _ocrState.postValue(OcrUiState.Error(error.message ?: "Failed to process cheque"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _ocrState.postValue(OcrUiState.Error("Failed to process cheque: ${e.message}"))
            }
        }
    }

    fun processENach(context: Context, fullBitmap: Bitmap?, cropRect: Rect?) {
        _ocrState.value = OcrUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jpegBytes = bitmapToByteArray(fullBitmap, cropRect)
                val result = documentProcessor.processENach(context, jpegBytes)

                result.onSuccess { enachData ->
                    lastENachData = enachData
                    val validation = ValidationEngine().validateENach(enachData)
                    _ocrState.postValue(OcrUiState.ENachSuccess(enachData, validation))
                }.onFailure { error ->
                    _ocrState.postValue(OcrUiState.Error(error.message ?: "Failed to process E-NACH"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _ocrState.postValue(OcrUiState.Error("Failed to process E-NACH: ${e.message}"))
            }
        }
    }

    fun processBothDocuments(context: Context, chequeBitmap: Bitmap?, enachBitmap: Bitmap?) {
        _ocrState.value = OcrUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chequeBytes = bitmapToByteArray(chequeBitmap, null)
                val enachBytes = bitmapToByteArray(enachBitmap, null)

                val result = documentProcessor.processBothDocuments(context, chequeBytes, enachBytes)

                result.onSuccess { crossValidationResult ->
                    lastChequeData = crossValidationResult.chequeData
                    lastENachData = crossValidationResult.enachData
                    _ocrState.postValue(OcrUiState.CrossValidationSuccess(crossValidationResult))
                }.onFailure { error ->
                    _ocrState.postValue(OcrUiState.Error(error.message ?: "Failed to process documents"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _ocrState.postValue(OcrUiState.Error("Failed to process documents: ${e.message}"))
            }
        }
    }

    fun performCrossValidation() {
        if (lastChequeData != null && lastENachData != null) {
            val validationEngine = ValidationEngine()
            val crossValidation = validationEngine.crossValidateDocuments(lastChequeData!!, lastENachData!!)
            _ocrState.postValue(OcrUiState.CrossValidationSuccess(crossValidation))
        } else {
            _ocrState.postValue(OcrUiState.Error("Both cheque and E-NACH documents must be processed first"))
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap?, cropRect: Rect?): ByteArray {
        var processedBitmap = bitmap ?: throw IllegalArgumentException("Bitmap cannot be null")

        cropRect?.let {
            processedBitmap = Bitmap.createBitmap(processedBitmap, it.left, it.top, it.width(), it.height())
        }

        val outputStream = ByteArrayOutputStream()
        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }

    fun resetState() {
        _ocrState.value = OcrUiState.Idle
        lastChequeData = null
        lastENachData = null
    }
}
