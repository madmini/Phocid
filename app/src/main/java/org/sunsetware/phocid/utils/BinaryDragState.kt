package org.sunsetware.phocid.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sunsetware.phocid.DRAG_THRESHOLD
import org.sunsetware.phocid.ui.theme.emphasizedStandard

/**
 * Reminder: [androidx.compose.foundation.gestures.AnchoredDraggableState] and other official APIs
 * are unusable traps, don't bother "migrating".
 */
@Stable
class BinaryDragState(
    /**
     * Must be a [CoroutineScope] from a composition context (i.e. not the view model scope).
     *
     * Reassign this property on activity recreation.
     */
    var coroutineScope: WeakReference<CoroutineScope> = WeakReference(null),
    initialValue: Float = 0f,
    val onSnapToZero: () -> Unit = {},
    val onSnapToOne: () -> Unit = {},
    val reversed: Boolean = false,
    val animationSpec: AnimationSpec<Float> = emphasizedStandard<Float>(),
) {
    private val _position = Animatable(initialValue)
    val position by _position.asState()

    private val _targetValue = MutableStateFlow(initialValue)
    val targetValue = _targetValue.asStateFlow()

    var length by mutableFloatStateOf(0f)

    @Volatile private var dragTotal = 0f
    @Volatile private var dragInitialPosition = initialValue

    fun onDragStart(lock: DragLock) {
        dragTotal = 0f
        dragInitialPosition = _position.value
        lock.isDragging.set(true)
    }

    fun onDrag(lock: DragLock, delta: Float) {
        dragTotal += delta * (if (reversed) 1 else -1)
        coroutineScope.get()?.launch {
            if (lock.isDragging.get()) {
                _position.snapTo(
                    (dragInitialPosition + dragTotal / length).coerceIn(0f, 1f).takeIf {
                        it.isFinite()
                    } ?: dragInitialPosition
                )
            }
        }
    }

    fun onDragEnd(lock: DragLock, density: Density) {
        lock.isDragging.set(false)
        if (dragTotal == 0f) return
        with(density) {
            if (dragTotal > DRAG_THRESHOLD.toPx()) {
                animateTo(1f)
            } else if (dragTotal < -DRAG_THRESHOLD.toPx()) {
                animateTo(0f)
            } else {
                val target = round(_position.value)
                animateTo(target)
            }
        }
        dragTotal = 0f
    }

    fun animateTo(value: Float) {
        coroutineScope.get()?.launch {
            _position.animateTo(value, animationSpec)
            if (value == 0f) onSnapToZero() else if (value == 1f) onSnapToOne()
        }
        _targetValue.update { value }
    }

    fun snapTo(value: Float) {
        coroutineScope.get()?.launch {
            _position.snapTo(value)
            if (value == 0f) onSnapToZero() else if (value == 1f) onSnapToOne()
        }
        _targetValue.update { value }
    }
}

/** This is used to prevent out-of-order execution of [BinaryDragState.onDragEnd] etc. */
@Stable
class DragLock {
    val isDragging = AtomicBoolean(false)
}
