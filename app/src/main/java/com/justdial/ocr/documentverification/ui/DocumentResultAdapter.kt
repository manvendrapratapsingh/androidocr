package com.justdial.ocr.documentverification.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.justdial.ocr.R
import com.justdial.ocr.documentverification.model.DocumentAnalysisResult
import com.justdial.ocr.documentverification.model.DocumentStatus
import com.justdial.ocr.documentverification.model.DocumentType

class DocumentResultAdapter : RecyclerView.Adapter<DocumentResultAdapter.ResultViewHolder>() {

    private val results = mutableListOf<Pair<DocumentAnalysisResult, Bitmap?>>()

    fun addResult(result: DocumentAnalysisResult, bitmap: Bitmap?) {
        results.add(0, Pair(result, bitmap)) // Add to top
        notifyItemInserted(0)
    }

    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val (result, bitmap) = results[position]
        holder.bind(result, bitmap)
    }

    override fun getItemCount(): Int = results.size

    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumbnail: ImageView = itemView.findViewById(R.id.img_thumbnail)
        private val tvDocumentType: TextView = itemView.findViewById(R.id.tv_document_type)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val tvConfidence: TextView = itemView.findViewById(R.id.tv_confidence)
        private val tvTamperingScore: TextView = itemView.findViewById(R.id.tv_tampering_score)
        private val tvReason: TextView = itemView.findViewById(R.id.tv_reason)
        private val tvFraudTitle: TextView = itemView.findViewById(R.id.tv_fraud_title)
        private val tvFraudIndicators: TextView = itemView.findViewById(R.id.tv_fraud_indicators)
        private val tvFieldsTitle: TextView = itemView.findViewById(R.id.tv_fields_title)
        private val tvExtractedFields: TextView = itemView.findViewById(R.id.tv_extracted_fields)

        fun bind(result: DocumentAnalysisResult, bitmap: Bitmap?) {
            // Set thumbnail
            if (bitmap != null) {
                imgThumbnail.setImageBitmap(bitmap)
            }

            // Document type
            tvDocumentType.text = formatDocumentType(result.documentType)

            // Status with color
            tvStatus.text = result.prediction.name
            tvStatus.setTextColor(getStatusColor(result.prediction))

            // Confidence
            val confidencePercent = (result.confidence * 100).toInt()
            tvConfidence.text = "Confidence: $confidencePercent%"

            // Tampering score with color
            tvTamperingScore.text = String.format("%.2f", result.elaTamperingScore)
            tvTamperingScore.setTextColor(getTamperingScoreColor(result.elaTamperingScore))

            // Reason
            tvReason.text = result.reason

            // Fraud indicators
            if (result.fraudIndicators.isNotEmpty()) {
                tvFraudTitle.visibility = View.VISIBLE
                tvFraudIndicators.visibility = View.VISIBLE
                tvFraudIndicators.text = result.fraudIndicators.joinToString("\n• ", prefix = "• ")
            } else {
                tvFraudTitle.visibility = View.GONE
                tvFraudIndicators.visibility = View.GONE
            }

            // Extracted fields
            if (result.extractedFields.isNotEmpty()) {
                tvFieldsTitle.visibility = View.VISIBLE
                tvExtractedFields.visibility = View.VISIBLE
                val fieldsText = result.extractedFields.entries.joinToString("\n") { (key, value) ->
                    "${formatFieldName(key)}: $value"
                }
                tvExtractedFields.text = fieldsText
            } else {
                tvFieldsTitle.visibility = View.GONE
                tvExtractedFields.visibility = View.GONE
            }
        }

        private fun formatDocumentType(type: DocumentType): String {
            return when (type) {
                DocumentType.PAN -> "PAN Card"
                DocumentType.DRIVING_LICENSE -> "Driving License"
                DocumentType.VOTER_ID -> "Voter ID"
                DocumentType.PASSPORT -> "Passport"
                DocumentType.UNKNOWN -> "Unknown Document"
            }
        }

        private fun formatFieldName(fieldName: String): String {
            return fieldName.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }

        private fun getStatusColor(status: DocumentStatus): Int {
            return when (status) {
                DocumentStatus.PASS -> 0xFF4CAF50.toInt() // Green
                DocumentStatus.FLAGGED -> 0xFFFF9800.toInt() // Orange
                DocumentStatus.FAIL -> 0xFFD32F2F.toInt() // Red
            }
        }

        private fun getTamperingScoreColor(score: Float): Int {
            return when {
                score < 30 -> 0xFF4CAF50.toInt() // Green
                score < 60 -> 0xFFFF9800.toInt() // Orange
                else -> 0xFFD32F2F.toInt() // Red
            }
        }
    }
}