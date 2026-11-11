/*
 * Copyright 2022 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.chethan616.clearpdf.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Compose's OffsetOverscrollEffect sample, with increasing resistance and a bounded pull.
 * Source: androidx/compose/foundation/samples/OverscrollSample.kt (Apache-2.0).
 */
internal class RecentOverscroll(private val scope: CoroutineScope, private val limit: Float) : OverscrollEffect {
    private val offset = Animatable(0f)

    // LazyColumn skips its built-in overscroll dispatch when all items fit. A nested-scroll
    // parent still receives the unused drag, so short and long lists use the same effect.
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!isInProgress) return Offset.Zero
            var left = available
            applyToScroll(available, source) { left = it; it }
            return available - left
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            applyToScroll(available, source) { Offset.Zero }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!isInProgress) return Velocity.Zero
            applyToFling(available) { Velocity.Zero }
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!isInProgress) return Velocity.Zero
            applyToFling(available) { Velocity.Zero }
            return available
        }
    }

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        val before = offset.value
        val pre = if (abs(before) > 0.5f && sign(delta.y) != sign(before)) {
            val next = before + delta.y
            val consumed = if (sign(next) != sign(before)) -before else delta.y
            scope.launch { offset.snapTo(before + consumed) }
            Offset(0f, consumed)
        } else Offset.Zero
        val consumed = performScroll(delta - pre)
        val remaining = delta - pre - consumed
        if (abs(remaining.y) > 0.5f && source == NestedScrollSource.UserInput) {
            val next = (offset.value + remaining.y * 0.45f / (1f + abs(offset.value) / limit)).coerceIn(-limit, limit)
            scope.launch { offset.snapTo(next) }
            return pre + consumed + remaining.copy(x = 0f)
        }
        return pre + consumed
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        performFling(velocity)
        offset.animateTo(0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 220f))
    }

    override val isInProgress: Boolean get() = offset.value != 0f
    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints) = measurable.measure(constraints).let { placeable ->
            layout(placeable.width, placeable.height) { placeable.placeRelativeWithLayer(0, offset.value.roundToInt()) }
        }
    }
}
