package com.ugf.yiffur1.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ugf.yiffur1.domain.model.ApiHost
import com.ugf.yiffur1.domain.model.ArtistInfo
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.TagCategory
import com.ugf.yiffur1.domain.model.WikiPage
import com.ugf.yiffur1.ui.theme.color
import com.ugf.yiffur1.util.Formatters

/**
 * Interactive Tag Builder & Inspector Sheet (remastered evolution of ArtistTagPopup.kt).
 * Allows building multi-tag queries (+include / -exclude), toggling blacklist per tag,
 * reading in-app wiki definitions, and opening artist profile links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBuilderSheet(
    post: Post,
    blacklistedTagStrings: Set<String>,
    onSearchQuery: (String) -> Unit,
    onToggleBlacklistTag: (String) -> Unit,
    onOpenWiki: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Map<tag, +1 include | -1 exclude>
    val selectedMap = remember(post.id) { mutableStateMapOf<String, Int>() }
    var customQuery by remember(post.id) { mutableStateOf("") }

    fun rebuildQuery() {
        customQuery = selectedMap.entries.joinToString(" ") { (tag, mode) ->
            if (mode < 0) "-$tag" else tag
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Tags & Search Builder", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tap tags below to build query…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        val q = customQuery.trim()
                        if (q.isNotEmpty()) {
                            onDismiss()
                            onSearchQuery(q)
                        }
                    },
                    enabled = customQuery.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                post.tags.byCategory().forEach { (category, tagList) ->
                    Text(
                        text = "${category.label} (${tagList.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = category.color()
                    )
                    tagList.forEach { tag ->
                        val state = selectedMap[tag] ?: 0
                        val isBlacklisted = tag.lowercase() in blacklistedTagStrings
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when (state) {
                                1 -> category.color().copy(alpha = 0.20f)
                                -1 -> MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (state == 1) selectedMap.remove(tag) else selectedMap[tag] = 1
                                        rebuildQuery()
                                    }
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tag.replace('_', ' '),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = category.color(),
                                    fontWeight = if (category == TagCategory.ARTIST) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (state == 1) selectedMap.remove(tag) else selectedMap[tag] = 1
                                        rebuildQuery()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Include tag",
                                        tint = if (state == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (state == -1) selectedMap.remove(tag) else selectedMap[tag] = -1
                                        rebuildQuery()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Exclude tag",
                                        tint = if (state == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleBlacklistTag(tag) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBlacklisted) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle blacklist",
                                        tint = if (isBlacklisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (category == TagCategory.ARTIST) {
                                    IconButton(
                                        onClick = { onOpenArtist(tag) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = "Artist links",
                                            tint = category.color(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onOpenWiki(tag) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = "Wiki info",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostInfoSheet(
    post: Post,
    apiHost: ApiHost,
    onOpenUrl: (String) -> Unit,
    onSearchUploader: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Post #${post.id} Details", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            InfoRow("Rating", post.rating.label)
            InfoRow("Resolution", "${post.width} × ${post.height}")
            InfoRow("Format", post.ext.uppercase())
            InfoRow("File Size", Formatters.formatFileSize(post.fileSize))
            if (post.duration != null && post.duration > 0.0) {
                InfoRow("Duration", Formatters.formatDuration(post.duration))
            }
            InfoRow("MD5", post.md5 ?: "Unavailable")
            InfoRow("Created", Formatters.formatIsoDate(post.createdAt))
            if (!post.updatedAt.isNullOrBlank()) {
                InfoRow("Updated", Formatters.formatIsoDate(post.updatedAt))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Uploader", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = post.uploaderName.ifBlank { "User #${post.uploaderId}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        if (post.uploaderName.isNotBlank()) {
                            onDismiss()
                            onSearchUploader("user:${post.uploaderName}")
                        }
                    }
                )
            }

            if (post.videoVariants.isNotEmpty()) {
                InfoRow(
                    "Video Streams",
                    post.videoVariants.joinToString(", ") { it.label }
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("Sources (${post.sources.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (post.sources.isEmpty()) {
                Text("None listed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                post.sources.forEach { src ->
                    val isInactive = src.startsWith("-")
                    val displayUrl = src.removePrefix("-")
                    Text(
                        text = displayUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInactive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        textDecoration = if (isInactive) TextDecoration.LineThrough else TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isInactive && displayUrl.startsWith("http")) {
                                onOpenUrl(displayUrl)
                            }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { onOpenUrl("https://${apiHost.domain}/posts/${post.id}") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open on ${apiHost.domain}")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WikiViewerSheet(
    title: String,
    wikiPage: WikiPage?,
    isLoading: Boolean,
    apiHost: ApiHost,
    onSearchTag: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPost: (Int) -> Unit,
    onOpenPool: (Int) -> Unit,
    onOpenWiki: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (wikiPage?.title ?: title).replace('_', ' '),
                    style = MaterialTheme.typography.titleLarge,
                    color = (wikiPage?.category ?: TagCategory.GENERAL).color(),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onDismiss()
                    onSearchTag(title)
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search tag")
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (wikiPage == null) {
                Text(
                    text = "No wiki page found for \"$title\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (wikiPage.otherNames.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        wikiPage.otherNames.forEach { alias ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = alias,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DTextView(
                    rawText = wikiPage.body,
                    onOpenUrl = onOpenUrl,
                    onOpenPost = {
                        onDismiss()
                        onOpenPost(it)
                    },
                    onOpenPool = {
                        onDismiss()
                        onOpenPool(it)
                    },
                    onOpenWiki = onOpenWiki,
                    onSearchTag = {
                        onDismiss()
                        onSearchTag(it)
                    }
                )
            }

            OutlinedButton(
                onClick = { onOpenUrl("https://${apiHost.domain}/wiki_pages/${title.replace(" ", "_")}") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("View Wiki on ${apiHost.domain}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtistInfoSheet(
    artistName: String,
    artistInfo: ArtistInfo?,
    isLoading: Boolean,
    onSearchArtist: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artistName.replace('_', ' '),
                    style = MaterialTheme.typography.titleLarge,
                    color = TagCategory.ARTIST.color(),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onDismiss()
                        onSearchArtist(artistName)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Posts")
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (artistInfo == null) {
                Text(
                    text = "No artist profile entry found on e621 for \"$artistName\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (artistInfo.otherNames.isNotEmpty()) {
                    Text("Also known as", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        artistInfo.otherNames.forEach { alt ->
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(alt, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                if (artistInfo.groupName.isNotBlank()) {
                    InfoRow("Group", artistInfo.groupName)
                }
                if (!artistInfo.notes.isNullOrBlank()) {
                    DTextView(rawText = artistInfo.notes, onOpenUrl = onOpenUrl)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Links (${artistInfo.urls.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (artistInfo.urls.isEmpty()) {
                    Text("No links listed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    artistInfo.urls.sortedByDescending { it.isActive }.forEach { link ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = link.isActive) { onOpenUrl(link.url) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (link.isActive) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.Block,
                                contentDescription = null,
                                tint = if (link.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = link.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (link.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = if (link.isActive) TextDecoration.Underline else TextDecoration.LineThrough,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
