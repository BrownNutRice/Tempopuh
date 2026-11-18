package me.avinas.tempo.ui.spotlight.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import me.avinas.tempo.ui.spotlight.SpotlightCardData

internal const val MAX_CANVAS_TEXT_LENGTH = 500
private const val MAX_DRAFT_CHARS = 24_000
private const val CARD_WIDTH_DP = 320f
private const val CARD_HEIGHT_DP = 560f

internal data class CanvasViewport(val width: Float, val height: Float, val density: Float) {
    fun normalize(x: Float, y: Float, scale: Float, rotation: Float, isCard: Boolean): CanvasPlacement {
        val originX = if (isCard) CARD_WIDTH_DP * density / 2 else 0f
        val originY = if (isCard) CARD_HEIGHT_DP * density / 2 else 0f
        return CanvasPlacement(
            (x + originX) / width,
            (y + originY) / height,
            scale * density / width,
            rotation,
        )
    }

    fun project(value: CanvasPlacement, isCard: Boolean): CanvasPlacement {
        val originX = if (isCard) CARD_WIDTH_DP * density / 2 else 0f
        val originY = if (isCard) CARD_HEIGHT_DP * density / 2 else 0f
        return CanvasPlacement(
            value.x * width - originX,
            value.y * height - originY,
            value.scale * width / density,
            value.rotation,
        )
    }
}

@JsonClass(generateAdapter = true)
internal data class CanvasPlacement(val x: Float, val y: Float, val scale: Float, val rotation: Float)

@JsonClass(generateAdapter = true)
internal data class DraftCard(val id: String, val cardId: String, val position: CanvasPlacement, val order: Int)

@JsonClass(generateAdapter = true)
internal data class DraftText(
    val id: String,
    val text: String,
    val position: CanvasPlacement,
    val order: Int,
    val style: DraftTextStyle,
)

@JsonClass(generateAdapter = true)
internal data class DraftTextStyle(
    val font: String,
    val size: Int,
    val color: Int,
    val bold: Boolean,
    val italic: Boolean,
    val outlined: Boolean,
    val outline: Int,
    val shadow: Boolean,
    val alignment: String,
    val hasBackground: Boolean,
    val background: Int,
) {
    fun restore() = TextStyle(
        fontPreset = FontPreset.entries.firstOrNull { it.name == font } ?: FontPreset.Modern,
        fontSize = size,
        color = Color(color),
        isBold = bold,
        isItalic = italic,
        hasOutline = outlined,
        outlineColor = Color(outline),
        hasShadow = shadow,
        alignment = when (alignment) {
            "left" -> TextAlign.Left
            "right" -> TextAlign.Right
            else -> TextAlign.Center
        },
        hasBackground = hasBackground,
        backgroundColor = Color(background),
    )

    companion object {
        fun from(style: TextStyle) = DraftTextStyle(
            style.fontPreset.name, style.fontSize, style.color.toArgb(), style.isBold, style.isItalic,
            style.hasOutline, style.outlineColor.toArgb(), style.hasShadow,
            when (style.alignment) {
                TextAlign.Left -> "left"
                TextAlign.Right -> "right"
                else -> "center"
            },
            style.hasBackground, style.backgroundColor.toArgb(),
        )
    }
}

@JsonClass(generateAdapter = true)
internal data class CanvasDraft(
    val backgroundId: String,
    val cards: List<DraftCard>,
    val texts: List<DraftText>,
    val editingTextId: String? = null,
) {
    fun restore(viewport: CanvasViewport, availableCards: List<SpotlightCardData>): CanvasState {
        val byId = availableCards.associateBy { it.id }
        val restoredText = texts.map { text ->
            val item = viewport.project(text.position, isCard = false)
            CanvasTextItem(text.id, text.text, item.x, item.y, item.scale, item.rotation, text.order, text.style.restore())
        }
        val editing = editingTextId?.takeIf { id -> restoredText.any { it.id == id } }
        return CanvasState(
            selectedBackground = CanvasBackground.getById(backgroundId),
            canvasItems = cards.mapNotNull { card ->
                val data = byId[card.cardId] ?: return@mapNotNull null
                val item = viewport.project(card.position, isCard = true)
                CanvasCardItem(card.id, data, item.x, item.y, item.scale, item.rotation, card.order)
            },
            textItems = restoredText,
            availableCards = availableCards,
            showTextEditor = editing != null,
            editingTextId = editing,
        )
    }

    companion object {
        fun from(state: CanvasState, viewport: CanvasViewport) = CanvasDraft(
            backgroundId = state.selectedBackground.id,
            cards = state.canvasItems.take(state.maxCardLimit).map {
                DraftCard(it.id, it.cardData.id, viewport.normalize(it.offsetX, it.offsetY, it.scale, it.rotation, true), it.zIndex)
            },
            texts = state.textItems.take(state.maxTextLimit).map {
                DraftText(it.id, it.text.take(MAX_CANVAS_TEXT_LENGTH),
                    viewport.normalize(it.offsetX, it.offsetY, it.scale, it.rotation, false), it.zIndex, DraftTextStyle.from(it.style))
            },
            editingTextId = state.editingTextId.takeIf { state.showTextEditor },
        )
    }
}

internal object CanvasDraftCodec {
    private val adapter by lazy { Moshi.Builder().build().adapter(CanvasDraft::class.java) }

    fun encode(draft: CanvasDraft): String? =
        runCatching { adapter.toJson(draft).takeIf { it.length <= MAX_DRAFT_CHARS } }.getOrNull()

    fun decode(json: String?): CanvasDraft? {
        if (json == null || json.length > MAX_DRAFT_CHARS) return null
        return runCatching { adapter.fromJson(json) }.getOrNull()
            ?.takeIf { it.cards.size <= 5 && it.texts.size <= 5 && it.texts.all { text -> text.text.length <= MAX_CANVAS_TEXT_LENGTH } }
    }
}
