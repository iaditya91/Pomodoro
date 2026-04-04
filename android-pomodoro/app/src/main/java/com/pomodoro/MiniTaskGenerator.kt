package com.pomodoro

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

object MiniTaskGenerator {

    private const val TAG = "MiniTaskGenerator"

    private val model by lazy { Generation.getClient() }

    suspend fun generate(taskDescription: String): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking model availability...")
                val status = model.checkStatus()
                when (status) {
                    FeatureStatus.DOWNLOADABLE -> {
                        Log.d(TAG, "Model downloadable, starting download...")
                        model.download().collectLatest { }
                        Log.d(TAG, "Model download complete")
                    }
                    FeatureStatus.UNAVAILABLE -> {
                        Log.e(TAG, "Gemini Nano is not available on this device")
                        return@withContext Result.failure(
                            Exception("Gemini Nano is not available on this device")
                        )
                    }
                    FeatureStatus.AVAILABLE -> {
                        Log.d(TAG, "Model already available")
                    }
                }

                val prompt = """Break down this task into 3-5 tiny, easily achievable mini-steps that each take about 1-2 minutes. Keep each step short (under 10 words). Return ONLY a numbered list, nothing else.

Task: "$taskDescription"
"""
                Log.d(TAG, "Generating mini-tasks for: $taskDescription")
                val response = model.generateContent(prompt)
                val text = response.candidates.firstOrNull()?.text
                if (text.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Empty response"))
                }
                Log.d(TAG, "Generated response: $text")
                val tasks = parseNumberedList(text)
                if (tasks.isEmpty()) {
                    Result.failure(Exception("Could not generate tasks"))
                } else {
                    Result.success(tasks)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate mini-tasks", e)
                Result.failure(e)
            }
        }
    }

    private fun parseNumberedList(text: String): List<String> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                // Strip leading number/bullet: "1. ", "1) ", "- ", "* "
                line.replace(Regex("""^[\d]+[.)]\s*"""), "")
                    .replace(Regex("""^[-*]\s*"""), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(5)
    }
}
