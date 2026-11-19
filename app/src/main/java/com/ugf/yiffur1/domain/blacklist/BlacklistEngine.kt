package com.ugf.yiffur1.domain.blacklist

import com.ugf.yiffur1.domain.model.Post

enum class CompoundOperator(val prefix: String, val chipLabel: String) {
    AND("", "AND"),
    NOT("-", "EXCEPT"),
    OR("~", "ANY")
}

data class BlacklistTerm(
    val operator: CompoundOperator,
    val term: String
) {
    fun toTokenString(): String = "${operator.prefix}${term.trim()}"
}

/**
 * Full e621-compatible client-side blacklist evaluator & compound rule builder.
 *
 * Supports:
 * - Single tag lines: `gore`
 * - Compound multi-tag AND lines: `realistic_feral rating:e`
 * - Compound negated terms: `young -rating:s`
 * - Compound OR terms: `~cat ~dog rating:e`
 * - Metatags: `rating:s|q|e`, `type:mp4|webm|gif|png|jpg|swf`, `id:1234`, `user:name`,
 *   `score:<0`, `score:<=5`, `score:>10`, `favcount:<5`, `pool:123`, `status:pending|flagged|deleted`,
 *   `width:>4000`, `height:<500`, `duration:>60`, `tagcount:<10`
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

    /**
     * Parses a single blacklist rule line into structured [BlacklistTerm] tokens for visual compounding UI.
     */
    fun parseTerms(rawLine: String): List<BlacklistTerm> {
        return rawLine.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val clean = token.trim()
                when {
                    clean.startsWith("-") && clean.length > 1 ->
                        BlacklistTerm(CompoundOperator.NOT, clean.drop(1))
                    clean.startsWith("~") && clean.length > 1 ->
                        BlacklistTerm(CompoundOperator.OR, clean.drop(1))
                    clean.isNotEmpty() && clean != "-" && clean != "~" ->
                        BlacklistTerm(CompoundOperator.AND, clean)
                    else -> null
                }
            }
    }

    fun formatTerms(terms: List<BlacklistTerm>): String {
        return terms
            .filter { it.term.isNotBlank() }
            .distinctBy { "${it.operator.prefix}${it.term.trim().lowercase()}" }
            .joinToString(" ") { it.toTokenString() }
    }

    fun isCompoundRule(rawLine: String): Boolean = parseTerms(rawLine).size >= 2

    /**
     * Compounds 2+ existing blacklist rules into a single deduplicated compound rule.
     * - `useOr = false`: requires ALL terms (`tagA tagB -rating:s`)
     * - `useOr = true`: converts plain AND terms into `~OR` terms (`~tagA ~tagB`) while keeping any `-NOT` exclusions intact
     */
    fun compoundRules(rules: List<String>, useOr: Boolean = false): String {
        val merged = mutableListOf<BlacklistTerm>()
        val seen = HashSet<String>()

        for (line in rules) {
            for (term in parseTerms(line)) {
                val effectiveOp = if (useOr && term.operator == CompoundOperator.AND) {
                    CompoundOperator.OR
                } else {
                    term.operator
                }
                val key = "${effectiveOp.prefix}${term.term.lowercase()}"
                if (seen.add(key)) {
                    merged += BlacklistTerm(effectiveOp, term.term)
                }
            }
        }
        return formatTerms(merged)
    }

    /**
     * Returns a plain-English explanation of what a single or compound rule hides.
     */
    fun describeRule(rawLine: String): String {
        val terms = parseTerms(rawLine)
        if (terms.isEmpty()) return ""
        val required = terms.filter { it.operator == CompoundOperator.AND }.map { it.term }
        val excluded = terms.filter { it.operator == CompoundOperator.NOT }.map { it.term }
        val anyOf = terms.filter { it.operator == CompoundOperator.OR }.map { it.term }

        val clauses = mutableListOf<String>()
        if (required.size == 1) {
            clauses += "has ${required[0]}"
        } else if (required.size > 1) {
            clauses += "has ALL of (${required.joinToString(" + ")})"
        }
        if (anyOf.isNotEmpty()) {
            clauses += "has ANY of (${anyOf.joinToString(" or ")})"
        }
        if (excluded.isNotEmpty()) {
            clauses += "EXCEPT (${excluded.joinToString(", ")})"
        }
        return "Hides posts that " + clauses.joinToString(", ")
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
            term.startsWith("-~") || term.startsWith("~-") -> {
                negated = true
                isOr = true
                term = term.drop(2)
            }
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
            "width" -> buildNumericPredicate(value) { it.width }
            "height" -> buildNumericPredicate(value) { it.height }
            "duration" -> buildNumericPredicate(value) { (it.duration ?: 0.0).toInt() }
            "tagcount" -> buildNumericPredicate(value) { it.tags.allTagSet.size }
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
