package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.entities.Category
import com.ledge.cashbook.data.local.entities.CategoryKeyword
import com.ledge.cashbook.data.repo.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class CategorySuggestionViewModel @Inject constructor(
    private val repo: CategoryRepository
) : ViewModel() {

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    // Cached data
    private var categories: List<Category> = emptyList()
    private var keywords: List<CategoryKeyword> = emptyList()

    init { reload() }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                categories = repo.categoriesOnce()
                keywords = repo.allKeywords()
            } finally {
                _loaded.value = true
            }
        }
    }

    // Public API: Suggest a category name for the given description. Returns Pair<name, score> or null.
    suspend fun suggest(description: String): Pair<String, Double>? {
        val descNorm = normalize(description)
        if (descNorm.isBlank()) return null
        val catById = categories.associateBy { it.id }
        // Group keywords by category
        val map = keywords.groupBy { it.categoryId }
        var best: Pair<Long, Double>? = null
        map.forEach { (catId, list) ->
            val score = scoreKeywords(descNorm, list)
            if (score > (best?.second ?: 0.0)) {
                best = catId to score
            }
        }
        val (cid, score) = best ?: return null
        val name = catById[cid]?.name ?: return null
        // Thresholds: 0.9 for auto, 0.75 for suggest; let caller decide behavior via score
        return name to score
    }

    private fun scoreKeywords(descNorm: String, kws: List<CategoryKeyword>): Double {
        var best = 0.0
        val tokens = descNorm.split(' ').filter { it.isNotBlank() }
        for (kw in kws) {
            val k = normalize(kw.keyword)
            if (k.isBlank()) continue
            // Exact contains (full description)
            if (descNorm.contains(k)) {
                best = max(best, 1.0)
                continue
            }
            // Token equals / starts-with
            if (tokens.any { it == k }) best = max(best, 0.95)
            if (tokens.any { it.startsWith(k) || k.startsWith(it) }) best = max(best, 0.9)

            // Fuzzy: compare against each token and take the best token similarity
            if (tokens.isNotEmpty()) {
                var tokenBest = 0.0
                for (t in tokens) {
                    val ts = levenshteinRatio(t, k)
                    if (ts > tokenBest) tokenBest = ts
                }
                // Boost near-miss for short keywords (e.g., "milk" vs "mikl")
                if (k.length <= 5 && tokenBest >= 0.75) {
                    best = max(best, 0.92)
                } else if (tokenBest >= 0.9) {
                    best = max(best, 0.9)
                } else {
                    best = max(best, tokenBest * 0.85) // keep fuzzy below exact tiers
                }
            } else {
                // Fallback: fuzzy against the whole description
                val sim = levenshteinRatio(descNorm, k)
                best = max(best, sim * 0.85)
            }
        }
        return best
    }

    private fun normalize(s: String): String = s
        .lowercase()
        .trim()
        .replace("[\n\t]+".toRegex(), " ")
        .replace(Regex("""\p{Punct}+"""), " ")
        .replace("\\s+".toRegex(), " ")

    private fun levenshteinRatio(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,
                    d[i][j - 1] + 1,
                    d[i - 1][j - 1] + cost
                )
            }
        }
        val dist = d[a.length][b.length].toDouble()
        val maxLen = max(a.length, b.length).toDouble()
        return 1.0 - (dist / maxLen)
    }
}
