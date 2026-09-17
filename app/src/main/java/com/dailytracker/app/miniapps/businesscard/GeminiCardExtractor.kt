package com.dailytracker.app.miniapps.businesscard

import android.util.Base64
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ExtractedCardData(
    val businessName: String = "",
    val personName: String = "",
    val address: String = "",
    val phoneNumber: String = "",
    val supportedBrands: List<BrandInfo> = emptyList()
)

sealed class ExtractionResult {
    data class Success(val data: ExtractedCardData) : ExtractionResult()
    data class Failure(val message: String) : ExtractionResult()
}

/**
 * Sends a business card photo straight to Gemini's multimodal endpoint and
 * asks it to return structured JSON in one round trip - no separate on-device
 * OCR step needed. Uses OkHttp/Moshi directly (both already app
 * dependencies) rather than pulling in the Gemini SDK.
 */
object GeminiCardExtractor {

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()

    private val prompt = """
        You are reading a photo of a business card. Extract the following
        fields and reply with ONLY raw JSON (no markdown fences, no
        commentary), matching this exact shape:

        {
          "businessName": "",
          "personName": "",
          "address": "",
          "phoneNumber": "",
          "supportedBrands": [ { "name": "", "domain": "" } ]
        }

        Rules:
        - "supportedBrands" is the list of product brands this business is a
          vendor or official distributor for, if the card lists any (look for
          brand names/logos printed on the card).
        - For each brand, guess its most likely official website domain (e.g.
          "Samsung" -> "samsung.com") so a logo can be looked up later. Leave
          "domain" as an empty string if you are not reasonably sure.
        - If a field cannot be read from the card, use an empty string ("")
          or an empty list, but always return every key above.
        - Keep the phone number exactly as printed (preserve country code /
          punctuation if present).
    """.trimIndent()

    suspend fun extract(apiKey: String, imageBytes: ByteArray): ExtractionResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext ExtractionResult.Failure(
                    "No Gemini API key set. Add one from the settings icon on this mini-app."
                )
            }
            try {
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                val textPart = JSONObject().put("text", prompt)
                val imagePart = JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", base64Image)
                )
                val content = JSONObject().put("parts", JSONArray().put(textPart).put(imagePart))
                val requestJson = JSONObject()
                    .put("contents", JSONArray().put(content))
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.2)
                            .put("responseMimeType", "application/json")
                    )
                    .toString()

                val request = Request.Builder()
                    .url("$ENDPOINT?key=$apiKey")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext ExtractionResult.Failure(
                            "Gemini request failed (${response.code}): ${responseBody.take(200)}"
                        )
                    }

                    val text = JSONObject(responseBody)
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")

                    if (text.isNullOrBlank()) {
                        return@withContext ExtractionResult.Failure("Empty response from Gemini.")
                    }

                    val cleaned = text.trim()
                        .removePrefix("```json").removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    val parsed = moshi.adapter(ExtractedCardData::class.java).fromJson(cleaned)
                        ?: return@withContext ExtractionResult.Failure("Could not parse Gemini's response.")

                    ExtractionResult.Success(parsed)
                }
            } catch (e: Exception) {
                ExtractionResult.Failure(e.message ?: "Unknown error while contacting Gemini.")
            }
        }
}
