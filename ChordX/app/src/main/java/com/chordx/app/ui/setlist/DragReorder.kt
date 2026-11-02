package com.chordx.app.ui.setlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Long-press-and-drag reordering for a LazyColumn whose items are keyed by id.
 *
 * Nothing is saved while dragging: the dragged row follows the finger and the rows it
 * passes slide out of the way. On release, [end] reports the move once.
 */
class DragReorderState(val listState: LazyListState) {
    var draggedId by mutableStateOf<Long?>(null)
        private set
    private var fromIndex by mutableIntStateOf(-1)

    /** How far the dragged row has been pulled from where it started, in px. */
    var offset by mutableFloatStateOf(0f)
        private set
    private var draggedSize by mutableFloatStateOf(0f)

    val isDragging: Boolean get() = draggedId != null

    fun start(id: Long, index: Int) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggedId = id
        fromIndex = index
        offset = 0f
        draggedSize = info.size.toFloat()
    }

    fun drag(deltaY: Float) {
        if (isDragging) offset += deltaY
    }

    /** The index the dragged row would land on if released now. */
    fun targetIndex(count: Int): Int {
        if (!isDragging || count == 0) return fromIndex
        val items = listState.layoutInfo.visibleItemsInfo
        val start = items.firstOrNull { it.index == fromIndex } ?: return fromIndex
        val center = start.offset + start.size / 2f + offset
        val hit = items.firstOrNull { center >= it.offset && center < it.offset + it.size }
        val index = when {
            hit != null -> hit.index
            center < (items.firstOrNull()?.offset ?: 0) -> items.first().index
            else -> items.last().index
        }
        return index.coerceIn(0, count - 1)
    }

    /** How far row [index] should slide to make room for the dragged row, in px. */
    fun shiftFor(index: Int, count: Int): Float {
        if (!isDragging) return 0f
        val target = targetIndex(count)
        return when {
            fromIndex < target && index in (fromIndex + 1)..target -> -draggedSize
            fromIndex > target && index in target until fromIndex -> draggedSize
            else -> 0f
        }
    }

    fun end(count: Int, onMove: (from: Int, to: Int) -> Unit) {
        val from = fromIndex
        val to = targetIndex(count)
        cancel()
        if (from >= 0 && to != from) onMove(from, to)
    }

    fun cancel() {
        draggedId = null
        fromIndex = -1
        offset = 0f
    }
}

@Composable
fun rememberDragReorderState(listState: LazyListState): DragReorderState = remember(listState) { DragReorderState(listState) }
