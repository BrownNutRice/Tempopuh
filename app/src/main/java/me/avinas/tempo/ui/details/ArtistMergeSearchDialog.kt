package me.avinas.tempo.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import me.avinas.tempo.data.local.entities.Artist
import me.avinas.tempo.ui.components.CachedAsyncImage
import me.avinas.tempo.ui.components.TempoDialogButtonRow
import me.avinas.tempo.ui.components.TempoDialogShape
import me.avinas.tempo.ui.theme.*

/**
 * Dialog for searching and selecting an artist to merge into.
 */
@Composable
fun ArtistMergeSearchDialog(
    sourceArtistId: Long,
    sourceArtistName: String,
    onDismiss: () -> Unit,
    onMergeComplete: () -> Unit,
    viewModel: MergeArtistViewModel = hiltViewModel()
) {
    val appearancePalette = LocalTempoPalette.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sourceArtistId) {
        viewModel.setSourceArtistId(sourceArtistId)
    }

    LaunchedEffect(uiState.mergeStatus) {
        if (uiState.mergeStatus is ArtistMergeStatus.Success) {
            onMergeComplete()
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(TempoDialogShape.shape)
                .background(appearancePalette.elevatedSurface)
                .border(1.dp, appearancePalette.border.copy(alpha = 0.45f), TempoDialogShape.shape)
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Merge Artist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = appearancePalette.onSurface
                    )
                    Text(
                        text = "Merging: $sourceArtistName",
                        style = MaterialTheme.typography.bodySmall,
                        color = appearancePalette.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(GlassFrostSoft)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appearancePalette.mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Search for the correct artist to merge into. All listening history will be combined.",
                style = MaterialTheme.typography.bodySmall,
                color = appearancePalette.mutedText
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Input
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search for target artist...", color = appearancePalette.mutedText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = appearancePalette.mutedText) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = appearancePalette.onSurface,
                    unfocusedTextColor = appearancePalette.onSurface,
                    cursorColor = appearancePalette.accent,
                    focusedBorderColor = appearancePalette.accent,
                    unfocusedBorderColor = TextQuaternary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Content area
            when {
                uiState.isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = appearancePalette.accent, strokeWidth = 2.dp)
                    }
                }
                uiState.searchResults.isEmpty() && uiState.query.length >= 2 -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No artists found", color = appearancePalette.mutedText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                uiState.searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Type to search for artists",
                            color = appearancePalette.mutedText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = uiState.searchResults,
                            key = { artist -> artist.id }
                        ) { artist ->
                            ArtistSearchItem(
                                artist = artist,
                                onClick = { viewModel.selectArtistForMerge(artist) }
                            )
                        }
                    }
                }
            }

            // Confirmation panel for pending merge
            if (uiState.pendingMergeTarget != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(appearancePalette.surfaceSunken)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Confirm Merge",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = appearancePalette.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Merge \"$sourceArtistName\" into \"${uiState.pendingMergeTarget!!.name}\"?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appearancePalette.mutedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This action cannot be undone. All listening history will be combined.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appearancePalette.mutedText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TempoDialogButtonRow(
                        primaryText = "Merge",
                        onPrimary = { viewModel.confirmMerge() },
                        secondaryText = "Cancel",
                        onSecondary = { viewModel.cancelMerge() }
                    )
                }
            }

            // Error message
            if (uiState.mergeStatus is ArtistMergeStatus.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (uiState.mergeStatus as ArtistMergeStatus.Error).message,
                    color = TempoError,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Processing indicator
            if (uiState.mergeStatus is ArtistMergeStatus.Processing) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = appearancePalette.accent,
                        trackColor = GlassFrostSoft
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Merging artists...",
                        color = appearancePalette.mutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Individual artist item in search results.
 */
@Composable
private fun ArtistSearchItem(
    artist: Artist,
    onClick: () -> Unit
) {
    val appearancePalette = LocalTempoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFrostSoft)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artist image or placeholder
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GlassFrostMedium),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.imageUrl.isNullOrBlank()) {
                CachedAsyncImage(
                    imageUrl = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetSizeDp = 44,
                    error = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = appearancePalette.mutedText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = appearancePalette.mutedText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appearancePalette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.genres.isNotEmpty()) {
                Text(
                    text = artist.genres.take(3).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = appearancePalette.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (artist.country != null) {
                Text(
                    text = artist.country,
                    style = MaterialTheme.typography.bodySmall,
                    color = appearancePalette.mutedText
                )
            }
        }

        Text(
            text = "→",
            color = appearancePalette.mutedText,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
