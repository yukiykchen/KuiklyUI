package com.tencent.kuikly.demo.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.layout.positionInParent
import com.tencent.kuikly.compose.ui.layout.positionInRoot
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.demo.pages.base.BackgroundTimerModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Page("ScrollPage")
internal class ScrollPage : ComposeContainer() {

    override fun createExternalModules(): Map<String, Module>? {
        val externalModules = hashMapOf<String, Module>()
        externalModules[BackgroundTimerModule.MODULE_NAME] = BackgroundTimerModule()
        return externalModules
    }

    private var timerId: String? = null
    private var autoScrollTimerId: String? = null  // 自动滚动定时器ID（模拟 ComposableTimer）
    private var itemIndex = 0

    // 在类级别定义状态，让生命周期方法可以访问
    private val itemList = mutableStateListOf<ScrollItemData>()

    // 控制是否限制item数量（用于复现bug：内容不满一屏的场景）
    private var limitItemCount = mutableStateOf(false)
    private val maxItemCountWhenLimited = 3  // 限制模式下最多3个item，不满一屏
    private val maxItemCount = 30  // 全局上限，最多30个item

    // 预定义颜色列表
    private val colors = listOf(
        Color.Red, Color.Blue, Color.Green, Color.Yellow,
    )

    override fun willInit() {
        super.willInit()
        setContent {
            ScrollPageContent()
        }
    }

    override fun created() {
        super.created()
        startBackgroundTimer()
    }

    private fun startBackgroundTimer() {
        val timerModule = acquireModule<BackgroundTimerModule>(BackgroundTimerModule.MODULE_NAME)
        // 改为1000ms间隔，让item添加更慢，给足够时间熄屏复现bug
        timerId = timerModule.start(0, 1000) {
            //println("[LazyList-BugDebug] === Adding new item, current count: ${itemList.size} ===")
            addNewItem()
            getPager().syncFlushUI()
        }
    }

    // 启动自动滚动定时器（模拟 AudioRecordDetailPager 中的 ComposableTimer）
    private fun startAutoScrollTimer(onTick: () -> Unit) {
        stopAutoScrollTimer()
        val timerModule = acquireModule<BackgroundTimerModule>(BackgroundTimerModule.MODULE_NAME)
        autoScrollTimerId = timerModule.start(500, 500) {
            onTick()
            getPager().syncFlushUI()
        }
    }

    // 停止自动滚动定时器
    private fun stopAutoScrollTimer() {
        autoScrollTimerId?.let {
            val timerModule = acquireModule<BackgroundTimerModule>(BackgroundTimerModule.MODULE_NAME)
            timerModule.cancel(it)
            autoScrollTimerId = null
        }
    }

    // 保存自动滚动定时器的回调，以便页面恢复时重新启动
    private var autoScrollCallback: (() -> Unit)? = null

    // 页面是否可见（用于控制熄屏时停止自动滚动）
    private var isPageVisible = true



    // 页面恢复到前台时重新检查是否需要启动定时器
//    override fun pageDidAppear() {
//        super.pageDidAppear()
//        isPageVisible = true
//        KLog.i("LazyList-BugDebug", ">>> pageDidAppear: page resumed, itemList.size=${itemList.size}, isPageVisible=$isPageVisible <<<")
//        // 打印当前所有 item 的 index，用于对比
//        val indices = itemList.map { it.index }.joinToString(", ")
//        KLog.i("LazyList-BugDebug", ">>> pageDidAppear: item indices=[$indices] <<<")
//        // 页面恢复时，如果之前有自动滚动回调且条件满足，可以考虑恢复
//        // 但更安全的做法是让 LaunchedEffect 中的 snapshotFlow 重新触发
//    }

    // 页面进入后台时记录状态
//    override fun pageDidDisappear() {
//        super.pageDidDisappear()
//        isPageVisible = false
//        // 关键修复：页面不可见时停止自动滚动定时器，防止滚动位置被错误更新
//        stopAutoScrollTimer()
//        KLog.i("LazyList-BugDebug", ">>> pageDidDisappear: itemList.size=${itemList.size}, isPageVisible=$isPageVisible, stopped auto scroll timer <<<")
//    }

    private fun addNewItem() {
        // 全局上限检查：最多30个item
        if (itemList.size >= maxItemCount) {
            return
        }
        // 如果开启了限制模式且已达到最大数量，则不再添加
        if (limitItemCount.value && itemList.size >= maxItemCountWhenLimited) {
            return
        }
        KLog.i("LazyList-BugDebug === Adding new item","current count: ${itemList.size} ===")
        val newItem = ScrollItemData().apply {
            index = itemIndex++
            bgColor = colors[index % colors.size]
        }
        itemList.add(newItem)
    }

    @Composable
    private fun ScrollPageContent() {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var scrollJob by remember { mutableStateOf<Job?>(null) }
        // 是否显示回到底部的悬浮球
        var showFloatBall by remember { mutableStateOf(false) }
        // 是否限制item数量（用于复现bug）
        val isLimited by limitItemCount

        // 【调试日志】每次重组时打印关键状态
        KLog.i("LazyList-BugDebug", "=== ScrollPageContent recompose ===")
        KLog.i("LazyList-BugDebug", "itemList.size=${itemList.size}, firstVisibleItemIndex=${listState.firstVisibleItemIndex}, firstVisibleItemScrollOffset=${listState.firstVisibleItemScrollOffset}")
        KLog.i("LazyList-BugDebug", "canScrollForward=${listState.canScrollForward}, canScrollBackward=${listState.canScrollBackward}")

        // 模拟 AudioRecordDetailPager 中的逻辑：
        // 监听滚动状态，当滚动到底部（或内容不满一屏）时启动自动滚动定时器
        LaunchedEffect(listState) {
            snapshotFlow {
                Triple(
                    listState.lastScrolledForward,
                    listState.lastScrolledBackward,
                    listState.canScrollForward,
                )
            }.collect { (lastScrolledForward, lastScrolledBackward, canScrollForward) ->
                KLog.i("LazyList-BugDebug", "=== snapshotFlow collect: canScrollForward=$canScrollForward, lastScrolledForward=$lastScrolledForward, lastScrolledBackward=$lastScrolledBackward ===")
                // 当内容不满一屏或滚动到底部时，canScrollForward = false
                // 此时启动自动滚动定时器（这是 bug 的关键触发点）
                if (!canScrollForward && isPageVisible) {
                    KLog.i("LazyList-BugDebug", ">>> canScrollForward=false && isPageVisible=true, starting auto scroll timer! <<<")
                    startAutoScrollTimer {
                        // 定时器回调：尝试滚动到底部
                        //KLog.i("LazyList-BugDebug", "Timer tick: canScrollForward=${listState.canScrollForward}, itemCount=${itemList.size}, firstVisibleItemIndex=${listState.firstVisibleItemIndex}")
//                        if (listState.canScrollForward && itemList.isNotEmpty()) {
//                            //KLog.i("LazyList-BugDebug", ">>> TRIGGERING animateScrollToItem(${itemList.size - 1}) <<<")
//                            scrollJob = scope.launch {
//                                listState.animateScrollToItem(itemList.size - 1)
//                            }
//                        }
                        // 【调试日志】打印定时器触发时的完整列表状态
                        val layoutInfo = listState.layoutInfo
                        val visibleItemsStr = layoutInfo.visibleItemsInfo.map { "${it.index}(offset=${it.offset},size=${it.size})" }.joinToString(", ")
                        KLog.i("LazyList-BugDebug", "=== Timer tick START ===")
                        KLog.i("LazyList-BugDebug", "Timer: canScrollForward=${listState.canScrollForward}, canScrollBackward=${listState.canScrollBackward}")
                        KLog.i("LazyList-BugDebug", "Timer: firstVisibleItemIndex=${listState.firstVisibleItemIndex}, firstVisibleItemScrollOffset=${listState.firstVisibleItemScrollOffset}")
                        KLog.i("LazyList-BugDebug", "Timer: itemList.size=${itemList.size}, totalItemsCount=${layoutInfo.totalItemsCount}")
                        KLog.i("LazyList-BugDebug", "Timer: viewportStartOffset=${layoutInfo.viewportStartOffset}, viewportEndOffset=${layoutInfo.viewportEndOffset}")
                        KLog.i("LazyList-BugDebug", "Timer: visibleItems=[$visibleItemsStr]")
                        KLog.i("LazyList-BugDebug", "Timer: isPageVisible=$isPageVisible")
                        
                        if (listState.canScrollForward && itemList.isNotEmpty()) {
                            val targetIndex = (itemList.size - 1).coerceAtMost(
                                maxOf(0, listState.layoutInfo.totalItemsCount - 1)
                            )
                            KLog.i("LazyList-BugDebug", "Timer: >>> CALLING scrollToItem(targetIndex=$targetIndex) <<<")
                            if (targetIndex >= 0) {
                                scrollJob = scope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                    KLog.i("LazyList-BugDebug", "Timer: scrollToItem($targetIndex) COMPLETED")
                                }
                            }
                        } else {
                            KLog.i("LazyList-BugDebug", "Timer: skipped scrollToItem (canScrollForward=${listState.canScrollForward}, itemList.isEmpty=${itemList.isEmpty()})")
                        }
                        KLog.i("LazyList-BugDebug", "=== Timer tick END ===")

                    }
                    showFloatBall = false
                }
                // 用户向上滚动时，停止自动滚动
                if (lastScrolledBackward) {
                    stopAutoScrollTimer()
                    scrollJob?.cancel()
                    showFloatBall = true
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 标题栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Scroll Test (Bug 复现)",
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }

            // 控制面板：用于切换是否限制item数量
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Item Count: ${itemList.size}",
                    fontSize = 16.sp
                )
            }

            // LazyColumn 列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 【调试日志】在 items 块中打印实际传入的数据
                // KLog.i("LazyList-BugDebug", "LazyColumn items block: itemList.size=${itemList.size}")
                items(
                    items = itemList,
                    key = { it.index }
                ) { item ->
                    ScrollItem(item)
                }
            }

        }
    }

    @Composable
    private fun ScrollItem(item: ScrollItemData) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(60.dp)
                .background(
                    color = item.bgColor,
                    shape = RoundedCornerShape(8.dp)
                )
            ,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Item ${item.index}",
                fontSize = 20.sp,
                color = Color.White
            )
        }
    }

    override fun pageWillDestroy() {
        super.pageWillDestroy()
        // 停止添加item的定时器
        timerId?.let {
            val timerModule = acquireModule<BackgroundTimerModule>(BackgroundTimerModule.MODULE_NAME)
            timerModule.cancel(it)
        }
        // 停止自动滚动定时器
        stopAutoScrollTimer()
    }
}

internal class ScrollItemData {
    var index: Int = 0
    var bgColor: Color = Color.White
}
