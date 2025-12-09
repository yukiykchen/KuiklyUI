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

import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { wantAgent, WantAgent } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';

interface TimerParams {
  timerId: string;
  delay: number;
  period: number;
}

interface CancelParams {
  timerId: string;
}

/**
 * 后台定时器模块 - 支持熄屏后继续运行
 * 使用鸿蒙长时任务 + 原生setInterval实现
 */
export class KRBackgroundTimerModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRBackgroundTimerModule';

  private static readonly METHOD_START = 'start';
  private static readonly METHOD_CANCEL = 'cancel';
  private static readonly METHOD_CANCEL_ALL = 'cancelAll';

  // 存储所有定时器: timerId -> intervalId
  private timerMap: Map<string, number> = new Map();
  // 存储回调: timerId -> callback
  private callbackMap: Map<string, KuiklyRenderCallback> = new Map();
  // 长时任务是否已启动
  private isBackgroundRunning: boolean = false;
  // WantAgent实例
  private wantAgentInstance: WantAgent | null = null;

  syncMode(): boolean {
    // 异步模式，运行在UI线程
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    console.log('KRBackgroundTimerModule call method:', method, 'params type:', typeof params);
    switch (method) {
      case KRBackgroundTimerModule.METHOD_START:
        this.startTimer(params as string, callback);
        break;
      case KRBackgroundTimerModule.METHOD_CANCEL:
        this.cancelTimer(params as string);
        break;
      case KRBackgroundTimerModule.METHOD_CANCEL_ALL:
        this.cancelAllTimers();
        break;
    }
    return null;
  }

  private async startBackgroundRunning(): Promise<void> {
    if (this.isBackgroundRunning) {
      return;
    }

    try {
      // 创建WantAgent
      const wantAgentInfo: wantAgent.WantAgentInfo = {
        wants: [
          {
            bundleName: 'com.tencent.kuiklyohosdemo',
            abilityName: 'EntryAbility'
          }
        ],
        actionType: wantAgent.OperationType.START_ABILITY,
        requestCode: 0,
        actionFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
      };

      this.wantAgentInstance = await wantAgent.getWantAgent(wantAgentInfo);

      // 启动长时任务
      await backgroundTaskManager.startBackgroundRunning(
        getContext(),
        backgroundTaskManager.BackgroundMode.DATA_TRANSFER,
        this.wantAgentInstance
      );

      this.isBackgroundRunning = true;
      console.log('KRBackgroundTimerModule: Background running started successfully');
    } catch (error) {
      const err = error as BusinessError;
      console.error('KRBackgroundTimerModule: Failed to start background running, code:', err.code, 'message:', err.message);
    }
  }

  private async stopBackgroundRunning(): Promise<void> {
    if (!this.isBackgroundRunning) {
      return;
    }

    try {
      await backgroundTaskManager.stopBackgroundRunning(getContext());
      this.isBackgroundRunning = false;
      this.wantAgentInstance = null;
      console.log('KRBackgroundTimerModule: Background running stopped');
    } catch (error) {
      const err = error as BusinessError;
      console.error('KRBackgroundTimerModule: Failed to stop background running, code:', err.code, 'message:', err.message);
    }
  }

  private startTimer(params: string, callback: KuiklyRenderCallback | null): void {
    if (!params || !callback) {
      console.log('KRBackgroundTimerModule startTimer: params or callback is null');
      return;
    }

    console.log('KRBackgroundTimerModule startTimer params:', params);

    const timerParams: TimerParams = JSON.parse(params);
    const timerId = timerParams.timerId;
    const delay = timerParams.delay;
    const period = timerParams.period;

    console.log('KRBackgroundTimerModule startTimer timerId:', timerId, 'delay:', delay, 'period:', period);

    // 如果已存在同ID的定时器，先取消
    this.cancelTimerById(timerId);

    // 保存回调
    this.callbackMap.set(timerId, callback);

    // 启动长时任务（异步）
    this.startBackgroundRunning().then(() => {
      // 使用setTimeout处理首次延迟
      setTimeout(() => {
        // 首次触发
        const cb = this.callbackMap.get(timerId);
        if (cb) {
          console.log('KRBackgroundTimerModule first trigger timerId:', timerId);
          cb(JSON.stringify({ timerId: timerId, timestamp: Date.now() }));
        }

        // 设置周期性定时器
        const intervalId = setInterval(() => {
          const cb = this.callbackMap.get(timerId);
          if (cb) {
            console.log('KRBackgroundTimerModule interval trigger timerId:', timerId);
            cb(JSON.stringify({ timerId: timerId, timestamp: Date.now() }));
          }
        }, period);

        this.timerMap.set(timerId, intervalId);
      }, delay);
    });
  }

  private cancelTimer(params: string): void {
    if (!params) {
      return;
    }

    const cancelParams: CancelParams = JSON.parse(params);
    this.cancelTimerById(cancelParams.timerId);
  }

  private cancelTimerById(timerId: string): void {
    const intervalId = this.timerMap.get(timerId);
    if (intervalId !== undefined) {
      clearInterval(intervalId);
      this.timerMap.delete(timerId);
    }
    this.callbackMap.delete(timerId);

    // 如果没有定时器了，停止长时任务
    if (this.timerMap.size === 0 && this.callbackMap.size === 0) {
      this.stopBackgroundRunning();
    }
  }

  private cancelAllTimers(): void {
    this.timerMap.forEach((intervalId) => {
      clearInterval(intervalId);
    });
    this.timerMap.clear();
    this.callbackMap.clear();

    // 停止长时任务
    this.stopBackgroundRunning();
  }

  onDestroy(): void {
    this.cancelAllTimers();
  }
}
