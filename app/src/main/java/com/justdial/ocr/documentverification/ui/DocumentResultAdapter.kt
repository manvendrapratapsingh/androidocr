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
import com.justdial.ocr.documentverification.model.ReviewRecommendation

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

        // Personal Info views
        private val personalInfoSection: LinearLayout = itemView.findViewById(R.id.personal_info_section)
        private val nameContainer: LinearLayout = itemView.findViewById(R.id.name_container)
        private val tvPersonalName: TextView = itemView.findViewById(R.id.tv_personal_name)
        private val idNumberContainer: LinearLayout = itemView.findViewById(R.id.id_number_container)
        private val tvPersonalIdNumber: TextView = itemView.findViewById(R.id.tv_personal_id_number)
        private val dobContainer: LinearLayout = itemView.findViewById(R.id.dob_container)
        private val tvPersonalDob: TextView = itemView.findViewById(R.id.tv_personal_dob)

        // Review Decision views
        private val reviewDecisionSection: LinearLayout = itemView.findViewById(R.id.review_decision_section)
        private val tvReviewRecommendation: TextView = itemView.findViewById(R.id.tv_review_recommendation)
        private val tvRiskScore: TextView = itemView.findViewById(R.id.tv_risk_score)
        private val tvAutoProcessable: TextView = itemView.findViewById(R.id.tv_auto_processable)
        private val tvReviewReason: TextView = itemView.findViewById(R.id.tv_review_reason)

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

            // Personal Information
            val personalInfo = result.personalInfo
            if (personalInfo != null && (personalInfo.name != null || personalInfo.idNumber != null || personalInfo.dob != null)) {
                personalInfoSection.visibility = View.VISIBLE

                // Name
                if (personalInfo.name != null) {
                    nameContainer.visibility = View.VISIBLE
                    tvPersonalName.text = personalInfo.name
                } else {
                    nameContainer.visibility = View.GONE
                }

                // ID Number
                if (personalInfo.idNumber != null) {
                    idNumberContainer.visibility = View.VISIBLE
                    tvPersonalIdNumber.text = personalInfo.idNumber
                } else {
                    idNumberContainer.visibility = View.GONE
                }

                // DOB
                if (personalInfo.dob != null) {
                    dobContainer.visibility = View.VISIBLE
                    tvPersonalDob.text = personalInfo.dob
                } else {
                    dobContainer.visibility = View.GONE
                }
            } else {
                personalInfoSection.visibility = View.GONE
            }

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

            // ✅ Review Decision Section (NEW)
            val reviewDecision = result.reviewDecision
            if (reviewDecision != null) {
                reviewDecisionSection.visibility = View.VISIBLE

                // Recommendation badge with color-coded background
                val recommendationText = when (reviewDecision.recommendation) {
                    ReviewRecommendation.AUTO_ACCEPT -> "✓ AUTO ACCEPT"
                    ReviewRecommendation.MANUAL_REVIEW_RECOMMENDED -> "⚠ REVIEW RECOMMENDED"
                    ReviewRecommendation.MANUAL_REVIEW_REQUIRED -> "⚠ REVIEW REQUIRED"
                    ReviewRecommendation.AUTO_REJECT -> "✗ AUTO REJECT"
                }
                tvReviewRecommendation.text = recommendationText

                val recommendationBackground = when (reviewDecision.recommendation) {
                    ReviewRecommendation.AUTO_ACCEPT -> R.drawable.bg_status_pass
                    ReviewRecommendation.MANUAL_REVIEW_RECOMMENDED -> R.drawable.bg_status_flagged
                    ReviewRecommendation.MANUAL_REVIEW_REQUIRED -> R.drawable.bg_status_flagged
                    ReviewRecommendation.AUTO_REJECT -> R.drawable.bg_status_fail
                }
                tvReviewRecommendation.setBackgroundResource(recommendationBackground)

                // Risk score with color-coded text
                val riskScoreText = String.format("%.1f", reviewDecision.riskScore)
                tvRiskScore.text = riskScoreText

                val riskColor = when {
                    reviewDecision.riskScore <= 30 -> 0xFF388E3C.toInt()  // Green
                    reviewDecision.riskScore <= 60 -> 0xFFF57C00.toInt()  // Orange
                    else -> 0xFFD32F2F.toInt()  // Red
                }
                tvRiskScore.setTextColor(riskColor)

                // Auto-processable flag
                tvAutoProcessable.text = if (reviewDecision.autoProcessable) "Yes" else "No"
                tvAutoProcessable.setTextColor(
                    if (reviewDecision.autoProcessable) 0xFF388E3C.toInt() else 0xFFD32F2F.toInt()
                )

                // Decision reason
                tvReviewReason.text = reviewDecision.reason
            } else {
                reviewDecisionSection.visibility = View.GONE
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