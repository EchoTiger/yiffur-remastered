package com.ugf.yiffur1.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ugf.yiffur1.domain.model.TagCategory
import com.ugf.yiffur1.ui.theme.color

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagChip(
    tag: String,
    category: TagCategory,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    excluded: Boolean = false,
    trailingBadge: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val catColor = category.color()
    val containerColor = when {
        excluded -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        selected -> catColor.copy(alpha = 0.28f)
        else -> catColor.copy(alpha = 0.12f)
    }
    val borderColor = when {
        excluded -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        selected -> catColor.copy(alpha = 0.85f)
        else -> catColor.copy(alpha = 0.30f)
    }
    val shape = RoundedCornerShape(10.dp)

    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val prefix = when {
                excluded -> "− "
                selected -> "+ "
                else -> ""
            }
            Text(
                text = prefix + tag.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (category == TagCategory.ARTIST || selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (excluded) MaterialTheme.colorScheme.error else catColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!trailingBadge.isNullOrBlank()) {
                Text(
                    text = trailingBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
