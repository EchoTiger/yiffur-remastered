package com.ugf.yiffur1.domain.blacklist

import com.ugf.yiffur1.domain.model.Post

/**
 * Full e621-compatible client-side blacklist evaluator.
 *
 * Supports:
 * - Single tag lines: `gore`
 * - Multi-tag AND lines: `realistic_feral rating:e`
 * - Negated terms: `young -rating:s`
 * - OR terms: `~cat ~dog`
 * - Metatags: `rating:s|q|e`, `type:mp4|webm|gif|png|jpg|swf`, `id:1234`, `user:name`,
 *   `score:<0`, `score:<=5`, `score:>10`, `favcount:<5`, `pool:123`, `status:pending|flagged|deleted`
 * - Wildcard tags: `*_vore`, `feral*`
 * - Comment lines starting with `#` and blank lines (ignored)
 */
object BlacklistEngine {

    data class CompiledRule(
        val rawLine: String,
        val tokens: List<Token>
    )

    sealed interface Token {
        data class Match(val negated: Boolean, val isOr: Boolean, val predicate: (Post, Set<String>) -> Boolean) : Token
    }

    fun parseLines(rawBlock: String): List<String> {
        return rawBlock
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
            .toList()
    }

    fun compile(rules: Collection<String>): List<CompiledRule> {
        return rules.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.isEmpty()) return@mapNotNull null
            val tokens = parts.mapNotNull { parseToken(it) }
            if (tokens.isEmpty()) null else CompiledRule(trimmed, tokens)
        }
    }

    fun isBlacklisted(post: Post, compiledRules: List<CompiledRule>): Boolean {
        if (compiledRules.isEmpty()) return false
        val tagSet = post.tags.allTagSet
        return compiledRules.any { rule -> matchesRule(post, tagSet, rule) }
    }

    fun matchingRules(post: Post, compiledRules: List<CompiledRule>): List<String> {
        if (compiledRules.isEmpty()) return emptyList()
        val tagSet = post.tags.allTagSet
        return compiledRules.filter { matchesRule(post, tagSet, it) }.map { it.rawLine }
    }

    private fun matchesRule(post: Post, tagSet: Set<String>, rule: CompiledRule): Boolean {
        val andTokens = mutableListOf<Token.Match>()
        val orTokens = mutableListOf<Token.Match>()

        for (token in rule.tokens) {
            when (token) {
                is Token.Match -> if (token.isOr) orTokens.add(token) else andTokens.add(token)
            }
        }

        val andPass = andTokens.all { token ->
            val raw = token.predicate(post, tagSet)
            if (token.negated) !raw else raw
        }
        if (!andPass) return false

        if (orTokens.isNotEmpty()) {
            val orPass = orTokens.any { token ->
                val raw = token.predicate(post, tagSet)
                if (token.negated) !raw else raw
            }
            if (!orPass) return false
        }

        return true
    }

    private fun parseToken(rawToken: String): Token.Match? {
        var term = rawToken.trim().lowercase()
        if (term.isEmpty()) return null

        var negated = false
        var isOr = false

        when {
            term.startsWith("-") -> {
                negated = true
                term = term.drop(1)
            }
            term.startsWith("~") -> {
                isOr = true
                term = term.drop(1)
            }
        }
        if (term.isEmpty()) return null

        val colonIdx = term.indexOf(':')
        if (colonIdx > 0) {
            val key = term.substring(0, colonIdx)
            val value = term.substring(colonIdx + 1)
            val metaPredicate = buildMetatagPredicate(key, value)
            if (metaPredicate != null) {
                return Token.Match(negated, isOr, metaPredicate)
            }
        }

        // Standard tag or wildcard tag
        val predicate: (Post, Set<String>) -> Boolean = if (term.contains('*')) {
            val pattern = "^" + term.split('*').joinToString(".*") { Regex.escape(it) } + "$"
            val wildcardRegex = Regex(pattern)
            val matchFn: (Post, Set<String>) -> Boolean = { _, tagSet ->
                tagSet.any { tag -> wildcardRegex.matches(tag) }
            }
            matchFn
        } else {
            { _, tagSet -> tagSet.contains(term) }
        }

        return Token.Match(negated, isOr, predicate)
    }

    private fun buildMetatagPredicate(key: String, value: String): ((Post, Set<String>) -> Boolean)? {
        return when (key) {
            "rating" -> {
                val expected = when (value) {
                    "s", "safe" -> "s"
                    "q", "questionable" -> "q"
                    "e", "explicit" -> "e"
                    else -> value
                }
                { post, _ -> post.rating.code == expected }
            }
            "type" -> { post, _ -> post.ext.equals(value, ignoreCase = true) }
            "id" -> {
                val id = value.toIntOrNull()
                if (id != null) { post, _ -> post.id == id } else null
            }
            "user", "uploader" -> { post, _ -> post.uploaderName.equals(value, ignoreCase = true) }
            "userid", "uploaderid" -> {
                val uid = value.toIntOrNull()
                if (uid != null) { post, _ -> post.uploaderId == uid } else null
            }
            "pool" -> {
                val pid = value.toIntOrNull()
                if (pid != null) { post, _ -> post.pools.contains(pid) } else null
            }
            "score" -> buildNumericPredicate(value) { it.scoreTotal }
            "favcount" -> buildNumericPredicate(value) { it.favCount }
            "status" -> {
                { post, _ ->
                    when (value) {
                        "pending" -> post.isPending
                        "flagged" -> post.isFlagged
                        "deleted" -> post.isDeleted
                        "active" -> !post.isPending && !post.isDeleted
                        else -> false
                    }
                }
            }
            else -> null
        }
    }

    private fun buildNumericPredicate(expr: String, selector: (Post) -> Int): ((Post, Set<String>) -> Boolean)? {
        return when {
            expr.startsWith("<=") -> expr.drop(2).toIntOrNull()?.let { n -> { p, _ -> selector(p) <= n } }
            expr.startsWith(">=") -> expr.drop(2).toIntOrNull()?.let { n -> { p, _ -> selector(p) >= n } }
            expr.startsWith("<") -> expr.drop(1).toIntOrNull()?.let { n -> { p, _ -> selector(p) < n } }
            expr.startsWith(">") -> expr.drop(1).toIntOrNull()?.let { n -> { p, _ -> selector(p) > n } }
            expr.contains("..") -> {
                val bounds = expr.split("..")
                val min = bounds.getOrNull(0)?.toIntOrNull()
                val max = bounds.getOrNull(1)?.toIntOrNull()
                if (min != null && max != null) {
                    { p, _ -> selector(p) in min..max }
                } else null
            }
            else -> expr.toIntOrNull()?.let { n -> { p, _ -> selector(p) == n } }
        }
    }
}
