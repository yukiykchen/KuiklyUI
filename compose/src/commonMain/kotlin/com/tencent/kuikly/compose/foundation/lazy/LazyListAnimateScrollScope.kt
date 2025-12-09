/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.kuikly.compose.foundation.lazy

import com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi
import com.tencent.kuikly.compose.foundation.gestures.ScrollScope
import com.tencent.kuikly.compose.foundation.lazy.layout.LazyLayoutAnimateScrollScope
import com.tencent.kuikly.compose.ui.util.fastFirstOrNull
import com.tencent.kuikly.compose.ui.util.fastSumBy

@OptIn(ExperimentalFoundationApi::class)
internal class LazyListAnimateScrollScope(
    private val state: LazyListState
) : LazyLayoutAnimateScrollScope {

    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex

    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset

    override val lastVisibleItemIndex: Int
        get() = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

    override val itemCount: Int
        get() = state.layoutInfo.totalItemsCount

    override fun ScrollScope.snapToItem(index: Int, scrollOffset: Int) {
        state.snapToItemIndexInternal(index, scrollOffset, forceRemeasure = true)
    }

    override fun calculateDistanceTo(targetIndex: Int): Float {
        val layoutInfo = state.layoutInfo
        println("[LazyList-BugDebug] calculateDistanceTo: targetIndex=$targetIndex, visibleItemsInfo.size=${layoutInfo.visibleItemsInfo.size}")
        if (layoutInfo.visibleItemsInfo.isEmpty()) {
            println("[LazyList-BugDebug] calculateDistanceTo: visibleItemsInfo is EMPTY! returning 0f (THIS IS PROBLEMATIC!)")
            return 0f
        }
        val visibleItem = layoutInfo.visibleItemsInfo.fastFirstOrNull { it.index == targetIndex }
        val result = if (visibleItem == null) {
            val averageSize = calculateVisibleItemsAverageSize(layoutInfo)
            val indexesDiff = targetIndex - firstVisibleItemIndex
            val distance = (averageSize * indexesDiff).toFloat() - firstVisibleItemScrollOffset
            println("[LazyList-BugDebug] calculateDistanceTo: targetIndex=$targetIndex NOT visible, averageSize=$averageSize, indexesDiff=$indexesDiff, distance=$distance")
            distance
        } else {
            // 修复 bug: 不能直接返回 offset 作为滚动距离
            // offset 是 item 相对于视口起点的位置，需要计算实际需要滚动的距离
            val viewportEndOffset = layoutInfo.viewportEndOffset
            val itemEndOffset = visibleItem.offset + visibleItem.size

            val distance = when {
                // item 完全在视口内，不需要滚动
                visibleItem.offset >= 0 && itemEndOffset <= viewportEndOffset -> {
                    println("[LazyList-BugDebug] calculateDistanceTo: targetIndex=$targetIndex IS FULLY visible, no scroll needed")
                    0f
                }
                // item 部分超出视口底部，只滚动让它刚好完全可见的距离
                itemEndOffset > viewportEndOffset -> {
                    val scrollDistance = (itemEndOffset - viewportEndOffset).toFloat()
                    println("[LazyList-BugDebug] calculateDistanceTo: targetIndex=$targetIndex PARTIALLY visible (bottom), scrollDistance=$scrollDistance")
                    scrollDistance
                }
                // item 部分超出视口顶部（offset < 0），需要往回滚动
                else -> {
                    println("[LazyList-BugDebug] calculateDistanceTo: targetIndex=$targetIndex PARTIALLY visible (top), scrollDistance=${visibleItem.offset}")
                    visibleItem.offset.toFloat()
                }
            }
            distance
        }
        return result
    }

    override suspend fun scroll(block: suspend ScrollScope.() -> Unit) {
        state.scroll(block = block)
    }

    private fun calculateVisibleItemsAverageSize(layoutInfo: LazyListLayoutInfo): Int {
        val visibleItems = layoutInfo.visibleItemsInfo
        val itemsSum = visibleItems.fastSumBy { it.size }
        return itemsSum / visibleItems.size + layoutInfo.mainAxisItemSpacing
    }
}
