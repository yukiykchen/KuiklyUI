/*
 * Tencent is pleased to support the open source community by making KuiklyUI
 * available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 * Licensed under the License of KuiklyUI;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://github.com/Tencent-TDS/KuiklyUI/blob/main/LICENSE
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.kuikly.demo.pages.base

import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 后台定时器模块 - 支持熄屏后继续运行
 * 通过桥接调用鸿蒙原生的setInterval实现
 */
class BackgroundTimerModule : Module() {

    override fun moduleName(): String = MODULE_NAME

    companion object {
        const val MODULE_NAME = "KRBackgroundTimerModule"
    }

    private var timerIdCounter = 0

    /**
     * 启动后台定时器
     * @param delay 首次执行延迟（毫秒）
     * @param period 执行周期（毫秒）
     * @param callback 定时回调
     * @return 定时器ID，用于取消定时器
     */
    fun start(delay: Int, period: Int, callback: () -> Unit): String {
        val timerId = "timer_${timerIdCounter++}"
        
        val params = JSONObject().apply {
            put("timerId", timerId)
            put("delay", delay)
            put("period", period)
        }

        toNative(
            keepCallbackAlive = true, // 保持回调存活，支持多次触发
            methodName = "start",
            param = params.toString(),
            callback = { _ ->
                callback()
            },
            syncCall = false
        )

        return timerId
    }

    /**
     * 取消指定定时器
     * @param timerId 定时器ID
     */
    fun cancel(timerId: String) {
        val params = JSONObject().apply {
            put("timerId", timerId)
        }

        toNative(
            keepCallbackAlive = false,
            methodName = "cancel",
            param = params.toString(),
            callback = null,
            syncCall = false
        )
    }

    /**
     * 取消所有定时器
     */
    fun cancelAll() {
        toNative(
            keepCallbackAlive = false,
            methodName = "cancelAll",
            param = null,
            callback = null,
            syncCall = false
        )
    }
}
