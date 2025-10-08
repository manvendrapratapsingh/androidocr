package com.justdial.ocr.documentverification.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        private val fraudSection: LinearLayout = itemView.findViewById(R.id.fraud_section)
        private val fraudIndicatorsContainer: LinearLayout = itemView.findViewById(R.id.fraud_indicators_container)
        private val noFraudSection: LinearLayout = itemView.findViewById(R.id.no_fraud_section)

        fun bind(result: DocumentAnalysisResult, bitmap: Bitmap?) {
            val context = itemView.context

            // Set thumbnail
            if (bitmap != null) {
                imgThumbnail.setImageBitmap(bitmap)
            }

            // Document type
            tvDocumentType.text = formatDocumentType(result.documentType)

            // Status with color-coded chip background
            tvStatus.text = result.prediction.name
            val statusBackground = when (result.prediction) {
                DocumentStatus.PASS -> R.drawable.bg_status_pass
                DocumentStatus.FLAGGED -> R.drawable.bg_status_flagged
                DocumentStatus.FAIL -> R.drawable.bg_status_fail
            }
            tvStatus.setBackgroundResource(statusBackground)

            // Confidence
            val confidencePercent = (result.confidence * 100).toInt()
            tvConfidence.text = "Confidence: $confidencePercent%"

            // Tampering score with dynamic background and color
            val scoreInt = result.elaTamperingScore.toInt()
            tvTamperingScore.text = scoreInt.toString()

            val (scoreBackground, scoreColor) = when {
                result.elaTamperingScore <= 35 -> Pair(R.drawable.bg_score_low, 0xFF388E3C.toInt())
                result.elaTamperingScore <= 50 -> Pair(R.drawable.bg_score_medium, 0xFFF57C00.toInt())
                else -> Pair(R.drawable.bg_score_high, 0xFFD32F2F.toInt())
            }
            tvTamperingScore.setBackgroundResource(scoreBackground)
            tvTamperingScore.setTextColor(scoreColor)

            // Reason
            tvReason.text = result.reason

            // Fraud indicators with bullet points
            if (result.fraudIndicators.isNotEmpty()) {
                fraudSection.visibility = View.VISIBLE
                noFraudSection.visibility = View.GONE

                // Clear previous indicators
                fraudIndicatorsContainer.removeAllViews()

                // Add each fraud indicator as a separate TextView with bullet
                result.fraudIndicators.forEach { indicator ->
                    val indicatorView = TextView(context).apply {
                        text = "• $indicator"
                        textSize = 14f
                        setTextColor(0xFFD32F2F.toInt())
                        setPadding(0, dpToPx(4), 0, dpToPx(4))
                        setTextIsSelectable(true)
                    }
                    fraudIndicatorsContainer.addView(indicatorView)
                }
            } else {
                fraudSection.visibility = View.GONE
                noFraudSection.visibility = View.VISIBLE
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

        private fun dpToPx(dp: Int): Int {
            val density = itemView.context.resources.displayMetrics.density
            return (dp * density).toInt()
        }
    }
}