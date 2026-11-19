package com.ugf.yiffur1.ui.theme

import androidx.compose.ui.graphics.Color
import com.ugf.yiffur1.domain.model.Rating
import com.ugf.yiffur1.domain.model.TagCategory

// Core Yiffur 2.0 Midnight Palette (evolved from Yiffur 1.0 #202124 + #4DABFF + e621 deep indigo)
val YiffurSkyBlue = Color(0xFF4DABFF)
val YiffurSkyBlueLight = Color(0xFF8CCBFF)
val YiffurDeepOcean = Color(0xFF0B2545)
val YiffurGold = Color(0xFFF2AC08)
val YiffurAmber = Color(0xFFFFB74D)

// Midnight Surfaces
val MidnightBg = Color(0xFF0E121B)
val MidnightSurface = Color(0xFF151B28)
val MidnightSurfaceVariant = Color(0xFF1E2638)
val MidnightSurfaceHigh = Color(0xFF263046)
val MidnightOutline = Color(0xFF33415C)

// Pitch Black OLED Surfaces
val OledBg = Color(0xFF000000)
val OledSurface = Color(0xFF0A0D14)
val OledSurfaceVariant = Color(0xFF141923)
val OledSurfaceHigh = Color(0xFF1C2331)

// Semantic Colors
val TextPrimary = Color(0xFFF3F6FC)
val TextSecondary = Color(0xFFA8B5CC)
val FavoritePink = Color(0xFFFF4081)
val VoteUpGreen = Color(0xFF3DDC84)
val VoteDownRed = Color(0xFFFF5252)

// Official e621 Tag Category Colors
val TagArtistColor = Color(0xFFF2AC08)
val TagContributorColor = Color(0xFF90CAF9)
val TagCopyrightColor = Color(0xFFD65CFF)
val TagCharacterColor = Color(0xFF2EE06B)
val TagSpeciesColor = Color(0xFFED6A2C)
val TagGeneralColor = Color(0xFF7BB8FF)
val TagMetaColor = Color(0xFFE2E8F0)
val TagLoreColor = Color(0xFF66BB6A)
val TagInvalidColor = Color(0xFFFF6B6B)

// Rating Colors
val RatingSafeColor = Color(0xFF3DDC84)
val RatingQuestionableColor = Color(0xFFFFC107)
val RatingExplicitColor = Color(0xFFFF5252)

fun TagCategory.color(): Color = when (this) {
    TagCategory.ARTIST -> TagArtistColor
    TagCategory.CONTRIBUTOR -> TagContributorColor
    TagCategory.COPYRIGHT -> TagCopyrightColor
    TagCategory.CHARACTER -> TagCharacterColor
    TagCategory.SPECIES -> TagSpeciesColor
    TagCategory.GENERAL -> TagGeneralColor
    TagCategory.META -> TagMetaColor
    TagCategory.LORE -> TagLoreColor
    TagCategory.INVALID -> TagInvalidColor
}

fun Rating.color(): Color = when (this) {
    Rating.SAFE -> RatingSafeColor
    Rating.QUESTIONABLE -> RatingQuestionableColor
    Rating.EXPLICIT -> RatingExplicitColor
}
