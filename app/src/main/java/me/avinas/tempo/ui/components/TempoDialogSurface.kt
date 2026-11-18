package me.avinas.tempo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.avinas.tempo.ui.theme.*

/**
 * Unified dialog surface for Tempo. Solid, opaque, no glass.
 * Consistent shape (24dp), hairline border, soft shadow.
 */
object TempoDialogShape {
    val radius = 24.dp
    val shape = RoundedCornerShape(radius)
}

@Composable
fun TempoDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val appearancePalette = LocalTempoPalette.current
    Box(
        modifier = modifier
            .safeDrawingPadding()
            .fillMaxWidth()
            .shadow(
                elevation = 28.dp,
                shape = TempoDialogShape.shape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
            .clip(TempoDialogShape.shape)
            .background(
                Brush.verticalGradient(
                    listOf(appearancePalette.elevatedSurface, appearancePalette.elevatedSurface)
                )
            )
            .border(
                width = 1.dp,
                color = appearancePalette.border.copy(alpha = 0.45f),
                shape = TempoDialogShape.shape
            )
    ) {
        // Specular top highlight edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}
/**
 * Header icon badge for dialogs.
 */
@Composable
fun TempoDialogIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val shape = RoundedCornerShape((size * 0.30).dp)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.22f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5).dp)
        )
    }
}

/**
 * Standard dialog title.
 */
@Composable
fun TempoDialogTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    val appearancePalette = LocalTempoPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = appearancePalette.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Standard dialog body text.
 */
@Composable
fun TempoDialogBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTempoPalette.current.mutedText
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5,
        modifier = modifier
    )
}

/** Vertical sheen for accent buttons: subtle top lift, deeper base. */
private fun accentFill(containerColor: Color, enabled: Boolean): Brush =
    if (enabled) {
        Brush.verticalGradient(
            listOf(
                lerp(containerColor, Color.White, 0.08f),
                containerColor,
                lerp(containerColor, Color.Black, 0.16f)
            )
        )
    } else {
        val flat = containerColor.copy(alpha = 0.4f)
        Brush.verticalGradient(listOf(flat, flat))
    }

/** Grounded drop shadow + subtle sheen shared by all accent buttons. */
private fun Modifier.accentButtonChrome(
    containerColor: Color,
    enabled: Boolean,
    shape: RoundedCornerShape
): Modifier = this
    .shadow(
        elevation = if (enabled) 6.dp else 0.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.35f),
        spotColor = Color.Black.copy(alpha = if (enabled) 0.45f else 0f)
    )
    .background(accentFill(containerColor, enabled), shape)

/**
 * Primary action button — full width, TempoPrimary, 12dp radius.
 */
@Composable
fun TempoDialogPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = LocalTempoPalette.current.accent,
    contentColor: Color = LocalTempoPalette.current.onAccent
) {
    val shape = RoundedCornerShape(12.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .accentButtonChrome(containerColor, enabled, shape),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        shape = shape
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Secondary/dismiss action — tonal pill, quiet but tappable.
 */
@Composable
fun TempoDialogSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = LocalTempoPalette.current.mutedText
) {
    val appearancePalette = LocalTempoPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = appearancePalette.mutedText,
            disabledContainerColor = color.copy(alpha = 0.07f),
            disabledContentColor = appearancePalette.mutedText
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Two-button row: secondary left, primary right.
 */
@Composable
fun TempoDialogButtonRow(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    primaryColor: Color = LocalTempoPalette.current.accent,
    primaryContentColor: Color = LocalTempoPalette.current.onAccent
) {
    val appearancePalette = LocalTempoPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onSecondary,
            enabled = secondaryEnabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = appearancePalette.mutedText.copy(alpha = 0.14f),
                contentColor = appearancePalette.mutedText,
                disabledContainerColor = appearancePalette.mutedText.copy(alpha = 0.07f),
                disabledContentColor = appearancePalette.mutedText
            ),
            shape = shape
        ) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .accentButtonChrome(primaryColor, primaryEnabled, shape),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = primaryContentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = primaryContentColor.copy(alpha = 0.6f)
            ),
            shape = shape
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Destructive action button using [TempoError] color.
 */
@Composable
fun TempoDialogDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    TempoDialogPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        containerColor = TempoError,
        contentColor = Color.White
    )
}

/**
 * Text field for dialog inputs such as renaming, editing titles, or custom tags.
 */
@Composable
fun TempoDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    maxLines: Int = 1
) {
    val appearancePalette = LocalTempoPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = appearancePalette.mutedText) } },
        singleLine = singleLine,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) TempoError else appearancePalette.mutedText
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = appearancePalette.onSurface,
            unfocusedTextColor = appearancePalette.onSurface,
            focusedBorderColor = appearancePalette.accent,
            unfocusedBorderColor = appearancePalette.border.copy(alpha = 0.45f),
            focusedLabelColor = appearancePalette.accent,
            unfocusedLabelColor = appearancePalette.mutedText,
            cursorColor = appearancePalette.accent,
            focusedContainerColor = appearancePalette.surfaceSunken,
            unfocusedContainerColor = appearancePalette.surfaceSunken,
            errorBorderColor = TempoError,
            errorContainerColor = appearancePalette.surfaceSunken
        )
    )
}
