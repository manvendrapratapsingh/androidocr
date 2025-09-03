package com.justdial.ocr.model

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// --- ⚠️ IMPORTANT SECURE CONFIGURATION ---
private const val API_KEY_GOOGLE_CLOUDE="AIzaSyDq5lEzYEk0SA1HNCahtFCcUPBOwtanKDQ"
private const val PROJECT_ID = "ambient-stack-467317-n7"
private const val REGION = "us-central1" // Changed to a supported region
private const val MODEL_ID = "gemini-1.5-flash-001"
private const val SERVICE_ACCOUNT_EMAIL = "android-ocr-invoker@ambient-stack-467317-n7.iam.gserviceaccount.com"

// Direct Gemini AI API (simpler alternative)
private const val GEMINI_API_KEY = "AIzaSyDdoFOVvnJOOnGyr3uslRrIitjNi1CwtNI"
private const val GEMINI_MODEL = "gemini-1.5-flash"
private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

object VertexAiClient {

    /**
     * SIMPLE HttpURLConnection approach - using API key with custom prompt support.
     * This avoids all Retrofit/authentication complexity.
     */
    suspend fun getOcrDataWithSimpleHttp(context: Context?, jpegBytes: ByteArray, customPrompt: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("VertexAiClient", "=== Starting OCR API Call ===")
                Log.d("VertexAiClient", "Using simple HttpURLConnection with custom prompt...")
                Log.d("VertexAiClient", "Custom prompt length: ${customPrompt.length} characters")
                Log.d("VertexAiClient", "Image size: ${jpegBytes.size} bytes")

                // Step 1: Prepare the API request payload
                Log.d("VertexAiClient", "Step 1: Encoding image to base64...")
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                Log.d("VertexAiClient", "Base64 encoding complete. Length: ${base64Image.length}")

                Log.d("VertexAiClient", "Step 2: Building JSON payload...")
                val jsonPayload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray()
                            .put(JSONObject().apply {
                                put("text", customPrompt)
                            })
                            .put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        )
                    }))
                }
                Log.d("VertexAiClient", "JSON payload built successfully")

                // Step 3: Create connection
                val endpointUrl = "https://${REGION}-aiplatform.googleapis.com/v1/projects/${PROJECT_ID}/locations/${REGION}/publishers/google/models/${MODEL_ID}:generateContent?key=${API_KEY_GOOGLE_CLOUDE}"
                Log.d("VertexAiClient", "Step 3: Creating connection to endpoint...")
                Log.d("VertexAiClient", "Endpoint: ${endpointUrl.substring(0, endpointUrl.indexOf("?key="))}?key=***")
                
                val url = URL(endpointUrl)
                val connection = url.openConnection() as HttpsURLConnection
                
                // Step 4: Configure connection
                Log.d("VertexAiClient", "Step 4: Configuring connection...")
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.connectTimeout = 30000 // 30 seconds
                connection.readTimeout = 60000 // 60 seconds
                connection.doOutput = true
                Log.d("VertexAiClient", "Connection configured with 30s connect timeout, 60s read timeout")

                // Step 5: Send request
                Log.d("VertexAiClient", "Step 5: Sending request...")
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonPayload.toString())
                writer.flush()
                writer.close()
                Log.d("VertexAiClient", "Request sent successfully")

                // Step 6: Get response
                Log.d("VertexAiClient", "Step 6: Reading response...")
                val responseCode = connection.responseCode
                Log.d("VertexAiClient", "Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("VertexAiClient", "Step 7: Success! Reading response body...")
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseBody = reader.readText()
                    reader.close()

                    Log.d("VertexAiClient", "Response body length: ${responseBody.length} characters")
                    Log.d("VertexAiClient", "Full API Response: $responseBody")

                    // Step 8: Parse response
                    Log.d("VertexAiClient", "Step 8: Parsing response...")
                    val jsonResponse = JSONObject(responseBody)
                    
                    if (!jsonResponse.has("candidates")) {
                        Log.e("VertexAiClient", "No 'candidates' in response")
                        return@withContext Result.failure(Exception("No candidates in API response"))
                    }
                    
                    val candidates = jsonResponse.getJSONArray("candidates")
                    if (candidates.length() == 0) {
                        Log.e("VertexAiClient", "Empty candidates array")
                        return@withContext Result.failure(Exception("Empty candidates in API response"))
                    }
                    
                    val text = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    Log.d("VertexAiClient", "=== OCR SUCCESS ===")
                    Log.d("VertexAiClient", "Extracted text length: ${text.length} characters")
                    Log.d("VertexAiClient", "Extracted text result: $text")
                    Result.success(text)
                } else {
                    Log.e("VertexAiClient", "Step 7: ERROR - Reading error response...")
                    val errorStream = connection.errorStream
                    val errorBody = if (errorStream != null) {
                        val errorReader = BufferedReader(InputStreamReader(errorStream))
                        val body = errorReader.readText()
                        errorReader.close()
                        body
                    } else {
                        "No error body available"
                    }

                    Log.e("VertexAiClient", "=== API ERROR ===")
                    Log.e("VertexAiClient", "HTTP Status: $responseCode")
                    Log.e("VertexAiClient", "Error body: $errorBody")
                    Result.failure(Exception("API call failed with HTTP $responseCode: $errorBody"))
                }
            } catch (e: Exception) {
                Log.e("VertexAiClient", "=== EXCEPTION OCCURRED ===")
                Log.e("VertexAiClient", "Exception type: ${e.javaClass.simpleName}")
                Log.e("VertexAiClient", "Exception message: ${e.message}")
                Log.e("VertexAiClient", "Exception occurred during API call", e)
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}
