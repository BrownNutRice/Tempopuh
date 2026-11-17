package me.avinas.tempo.ui.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import me.avinas.tempo.ui.theme.AppFontFamily
import me.avinas.tempo.ui.theme.DisplayFontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import me.avinas.tempo.R
import me.avinas.tempo.ui.components.DeepOceanBackground

/** Preview in place; tapping a card does not persist anything until Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val loaded by viewModel.settings.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    // Do not initialize an editable draft from placeholder preferences.
    val saved = loaded ?: run {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    var draft by rememberSaveable(saved) { mutableStateOf(saved.preset) }
    var draftFont by rememberSaveable(saved) { mutableStateOf(saved.fontStyle) }
    var draftMotion by rememberSaveable(saved) { mutableStateOf(saved.reduceMotion) }
    val pending = saved.copy(preset = draft, fontStyle = draftFont, reduceMotion = draftMotion)
    val modified = pending != saved
    val foreground = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    titleContentColor = foreground,
                    navigationIconContentColor = foreground,
                ),
            )
        },
        bottomBar = {
            if (modified) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { draft = saved.preset; draftFont = saved.fontStyle; draftMotion = saved.reduceMotion },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.appearance_cancel)) }
                        Button(
                            onClick = { viewModel.apply(pending) },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppearancePaletteResolver.forPreset(draft).accent,
                                contentColor = AppearancePaletteResolver.forPreset(draft).onAccent,
                            ),
                        ) { Text(stringResource(R.string.appearance_apply)) }
                    }
                }
            }
        },
    ) { padding ->
        DeepOceanBackground {
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.appearance_gallery_heading),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = foreground,
                )
                Text(
                    stringResource(R.string.appearance_gallery_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                val presets = AppearancePreset.entries
                presets.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { preset ->
                            AppearancePresetCard(
                                preset = preset,
                                active = preset == saved.preset,
                                selected = draft == preset,
                                onClick = { if (!saving) draft = preset },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Text(
                    if (modified) stringResource(R.string.appearance_preview_pending)
                    else stringResource(R.string.appearance_preview_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.appearance_font_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = foreground,
                )
                Text(
                    stringResource(R.string.appearance_font_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                AppearanceFontStyle.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { style ->
                            FontStyleCard(
                                style = style,
                                selected = draftFont == style,
                                onClick = { if (!saving) draftFont = style },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.appearance_accessibility_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = foreground,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.appearance_reduce_motion), color = foreground)
                            Text(
                                stringResource(R.string.appearance_reduce_motion_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                        Switch(
                            checked = draftMotion,
                            enabled = !saving,
                            onCheckedChange = { draftMotion = it },
                        )
                    }
                }
                Text(
                    stringResource(R.string.appearance_light_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                OutlinedButton(enabled = !saving, onClick = {
                    draft = AppearancePreset.ABYSS
                    draftFont = AppearanceFontStyle.TEMPO
                    draftMotion = false
                }) {
                    Text(stringResource(R.string.appearance_restore))
                }
                if (saveFailed) {
                    Text(stringResource(R.string.appearance_save_failed), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun AppearancePresetCard(
    preset: AppearancePreset,
    active: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AppearancePaletteResolver.forPreset(preset)
    val shape = RoundedCornerShape(22.dp)
    val label = when (preset) {
        AppearancePreset.ABYSS -> stringResource(R.string.appearance_abyss)
        AppearancePreset.OBSIDIAN_GOLD -> stringResource(R.string.appearance_gold)
        AppearancePreset.AURORA -> stringResource(R.string.appearance_aurora)
        AppearancePreset.GLACIER -> stringResource(R.string.appearance_glacier)
    }
    val description = when (preset) {
        AppearancePreset.ABYSS -> stringResource(R.string.appearance_abyss_desc)
        AppearancePreset.OBSIDIAN_GOLD -> stringResource(R.string.appearance_gold_desc)
        AppearancePreset.AURORA -> stringResource(R.string.appearance_aurora_desc)
        AppearancePreset.GLACIER -> stringResource(R.string.appearance_glacier_desc)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) palette.accent else MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(listOf(palette.topBackground, palette.background))
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(8.dp))
                        .background(palette.accent.copy(alpha = 0.77f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("♫", color = palette.onAccent,
                            style = MaterialTheme.typography.headlineSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.appearance_preview_song),
                            color = palette.onSurface, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.appearance_preview_artist),
                            color = palette.mutedText,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(palette.elevatedSurface),
                ) {
                    Box(Modifier.fillMaxWidth(0.64f).height(4.dp)
                        .background(palette.accent))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(39.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    listOf(0.36f, 0.70f, 0.48f, 0.9f, 0.60f, 0.80f).forEach { proportion ->
                        Box(
                            Modifier.weight(1f)
                                .fillMaxHeight(proportion)
                                .background(palette.accent.copy(alpha = 0.60f)),
                        )
                    }
                }
            }
            if (active) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(9.dp)
                        .size(23.dp).clip(RoundedCornerShape(30.dp))
                        .background(palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = palette.onAccent)
                }
            }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}
 
@Composable
private fun FontStyleCard(
    style: AppearanceFontStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val scheme = MaterialTheme.colorScheme
    val label = when (style) {
        AppearanceFontStyle.TEMPO -> stringResource(R.string.appearance_font_tempo)
        AppearanceFontStyle.NEO_SANS -> stringResource(R.string.appearance_font_neo)
        AppearanceFontStyle.ARCHIVE_SERIF -> stringResource(R.string.appearance_font_archive)
        AppearanceFontStyle.STUDIO_MONO -> stringResource(R.string.appearance_font_studio)
    }
    val family = when (style) {
        AppearanceFontStyle.TEMPO -> DisplayFontFamily
        AppearanceFontStyle.NEO_SANS -> AppFontFamily
        AppearanceFontStyle.ARCHIVE_SERIF -> FontFamily.Serif
        AppearanceFontStyle.STUDIO_MONO -> FontFamily.Monospace
    }
    Row(
        modifier = modifier
            .clip(shape)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) scheme.primary else scheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Aa", fontFamily = family,
            color = if (selected) scheme.primary else scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = scheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Text(
                stringResource(R.string.appearance_font_selected),
                color = scheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
