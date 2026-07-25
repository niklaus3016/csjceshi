# 预加载逻辑分析文档

## 1. 状态变量

| 变量 | 类型 | 作用 |
|------|------|------|
| `preloadedAd` | `{slotId, isReady, loadedAt} \| null` | 存储预加载成功的广告信息 |
| `isPreloading` | `boolean` | 标记是否正在进行预加载 |
| `preloadingPromise` | `Promise<void> \| null` | 当前预加载任务的Promise，用于等待预加载完成 |
| `preloadAd` | `ref<boolean>` | 响应式状态，表示是否已触发预加载 |

**代码位置**: [useAdManager.ts#L43-L49](file:///home/devbox/project/src/composables/useAdManager.ts#L43)

---

## 2. 核心函数

### 2.1 triggerPreloadAfterDelay - 延迟触发预加载

```typescript
const triggerPreloadAfterDelay = () => {
  setTimeout(() => {
    preloadNextAd();
  }, 1000);
};
```

**作用**: 延迟1秒后触发预加载，用于页面初始化时避免立即请求。

**代码位置**: [useAdManager.ts#L371](file:///home/devbox/project/src/composables/useAdManager.ts#L371)

---

### 2.2 smartPreload - 智能预加载触发

```typescript
const smartPreload = () => {
  // 条件1：已有预加载广告，跳过
  if (preloadedAd) {
    console.log('📋 已有预加载广告，跳过预加载');
    return;
  }
  
  // 条件2：正在预加载中，跳过
  if (isPreloading && preloadingPromise) {
    console.log('⏳ 预加载进行中，跳过重复触发');
    return;
  }
  
  // 条件3：没有预加载，也未在预加载，开始新的预加载
  console.log('🚀 开始新的预加载任务');
  preloadNextAd();
};
```

**触发条件**:
- 没有已预加载的广告 (`!preloadedAd`)
- 不在预加载过程中 (`!isPreloading`)

**代码位置**: [useAdManager.ts#L378](file:///home/devbox/project/src/composables/useAdManager.ts#L378)

---

### 2.3 preloadParallelGroup - 并行预加载广告组

**策略**: 同时请求多个广告位，第一个成功即返回。

```typescript
const preloadParallelGroup = (slotIds: string[]): Promise<{ success: boolean; slotId: string | null }> => {
  return new Promise((resolve) => {
    let resolved = false;
    const listeners: { slotId: string; cleanup: () => void }[] = [];
    
    // 为每个广告位创建监听器
    slotIds.forEach(slotId => {
      let isSlotResolved = false;
      
      const onVideoDownloadSuccess = () => {
        if (isSlotResolved || resolved) return;
        isSlotResolved = true;
        console.log(`✅ 并行预加载成功: ${slotId}`);
        cleanupAllListeners();
        
        if (!resolved) {
          resolved = true;
          resolve({ success: true, slotId });
        }
      };
      
      const onVideoDownloadFailed = () => { ... };
      const onAdFailed = (error: any) => { ... };
      
      // 设置超时（2秒）
      setTimeout(() => { ... }, 2000);
      
      // 发起请求
      BaiduAd.loadRewardVideoAd({ adId: slotId }).catch((error) => { ... });
    });
    
    // 总超时（3.1秒）
    setTimeout(() => {
      if (!resolved) {
        resolved = true;
        cleanupAllListeners();
        resolve({ success: false, slotId: null });
      }
    }, 3100);
  });
};
```

**关键参数**:
| 参数 | 值 | 说明 |
|------|-----|------|
| 单个广告位超时 | 2秒 | 每个广告位独立超时 |
| 总超时 | 3.1秒 | 超过此时间返回失败 |
| 成功条件 | 任意广告位下载成功 | 第一个成功即返回 |

**代码位置**: [useAdManager.ts#L397](file:///home/devbox/project/src/composables/useAdManager.ts#L397)

---

### 2.4 preloadSingleSlot - 串行预加载单个广告位

**策略**: 逐个请求广告位，等待结果后再进行下一个。

```typescript
const preloadSingleSlot = (slotId: string): Promise<boolean> => {
  return new Promise((resolve) => {
    let isResolved = false;
    
    const onVideoDownloadSuccess = () => {
      if (!isResolved) {
        isResolved = true;
        console.log(`✅ 串行预加载成功: ${slotId}`);
        cleanupListeners();
        resolve(true);
      }
    };
    
    const onVideoDownloadFailed = () => { ... };
    const onAdFailed = (error: any) => { ... };
    
    // 注册监听器
    BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
    BaiduAd.addListener('onVideoDownloadFailed', onVideoDownloadFailed);
    BaiduAd.addListener('onAdFailed', onAdFailed);
    
    // 设置超时（2秒）
    setTimeout(() => {
      if (!isResolved) {
        isResolved = true;
        console.log(`⏱️ 串行预加载超时: ${slotId}`);
        cleanupListeners();
        resolve(false);
      }
    }, 2000);
    
    // 调用loadRewardVideoAd()加载广告
    BaiduAd.loadRewardVideoAd({ adId: slotId }).catch((error) => { ... });
  });
};
```

**关键参数**:
| 参数 | 值 | 说明 |
|------|-----|------|
| 单个广告位超时 | 2秒 | 请求超时时间 |
| 返回值 | `boolean` | `true` 表示成功，`false` 表示失败/超时 |

**代码位置**: [useAdManager.ts#L494](file:///home/devbox/project/src/composables/useAdManager.ts#L494)

---

### 2.5 preloadNextAd - 主预加载流程

**策略**: 一轮串行轮询所有广告位（当前12个）。

```typescript
const preloadNextAd = async (): Promise<void> => {
  // 如果已经在预加载，返回现有的Promise
  if (isPreloading && preloadingPromise) {
    return preloadingPromise;
  }
  
  // 如果已经有预加载的广告，直接返回
  if (preloadedAd) {
    console.log('📋 已有预加载广告，跳过预加载');
    return;
  }
  
  isPreloading = true;
  console.log('🚀 开始预加载任务（策略：一轮轮询所有12个广告位）');
  
  preloadingPromise = (async () => {
    const totalStartTime = Date.now();
    let foundAd = false;
    
    // 获取所有广告位列表
    const allSlots = Object.values(AD_GROUPS).flat();
    console.log(`📊 广告位总数：${allSlots.length}个`);
    
    // ========== 一轮轮询所有广告位 ==========
    console.log(`\n🔄 预加载尝试 1/1`);
    const startTime = Date.now();
    const TOTAL_TIMEOUT = 15000; // 总超时15秒
    
    for (let i = 0; i < allSlots.length; i++) {
      // 检查总超时
      if (Date.now() - startTime > TOTAL_TIMEOUT) {
        console.log('⏱️ 预加载总超时（15秒），终止任务');
        break;
      }
      
      const slotId = allSlots[i];
      console.log(`🔄 串行 [${i + 1}/${allSlots.length}]: ${slotId}`);
      
      const isReady = await preloadSingleSlot(slotId);
      
      if (isReady) {
        preloadedAd = {
          slotId: slotId,
          isReady: true,
          loadedAt: Date.now()
        };
        console.log(`🎉 串行预加载成功: ${slotId}`);
        foundAd = true;
        break;
      }
      
      // 广告位之间延迟500ms
      if (i < allSlots.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 500));
      }
    }
    
    isPreloading = false;
    preloadingPromise = null;
    const totalTime = ((Date.now() - totalStartTime) / 1000).toFixed(1);
    console.log(`📋 预加载任务全部结束，${foundAd ? '成功' : '未找到广告'}，总耗时${totalTime}秒`);
  })();
  
  return preloadingPromise;
};
```

**流程**:
```
开始 → 检查状态 → 串行轮询所有广告位 → 第一个成功即保存 → 结束
```

**关键参数**:
| 参数 | 值 | 说明 |
|------|-----|------|
| 总超时 | 15秒 | 超过此时间终止轮询 |
| 广告位间隔 | 500ms | 每个广告位之间的延迟 |
| 单个超时 | 2秒 | `preloadSingleSlot` 内设置 |
| 成功条件 | 任意一个广告位加载成功 | 成功后立即停止 |

**代码位置**: [useAdManager.ts#L563](file:///home/devbox/project/src/composables/useAdManager.ts#L563)

---

### 2.6 showPreloadedAd - 显示预加载的广告

```typescript
const showPreloadedAd = async (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
  if (!preloadedAd || !preloadedAd.isReady) {
    console.log('预加载广告未就绪，开始正常加载');
    reject(new Error('预加载广告未就绪'));
    return;
  }
  
  const slotId = preloadedAd.slotId;
  console.log(`🚀 使用预加载的广告位: ${slotId}`);
  
  // 清除预加载状态
  preloadedAd = null;
  
  // 设置广告显示标志
  hasShownAd = true;
  
  // 注册监听器
  const onRewardVerify = (result: any) => {
    // 使用模拟 ECPM 值
    const simulatedEcpm = generateSimulatedEcpm(slotId);
    const ecpm = calculateActualEcpm(simulatedEcpm);
    
    resolveOnce({ ecpm, slotId });
  };
  
  const onAdShow = () => {
    console.log(`📺 预加载广告页面已打开 (${slotId})，智能触发预加载`);
    smartPreload();
  };
  
  const onAdClose = () => { ... };
  
  // 显示广告
  await BaiduAd.showRewardVideoAd();
};
```

**流程**:
```
检查预加载状态 → 清除预加载 → 注册监听器 → 显示广告 → 触发新预加载
```

**代码位置**: [useAdManager.ts#L923](file:///home/devbox/project/src/composables/useAdManager.ts#L923)

---

## 3. 预加载触发时机

| 场景 | 触发方式 | 代码位置 |
|------|---------|---------|
| 原生SDK初始化成功 | 直接调用 `preloadNextAd()` | [第869行](file:///home/devbox/project/src/composables/useAdManager.ts#L869) |
| H5 SDK加载成功 | 直接调用 `preloadNextAd()` | [第884行](file:///home/devbox/project/src/composables/useAdManager.ts#L884) |
| 广告页面打开时 | `smartPreload()` | [第975行](file:///home/devbox/project/src/composables/useAdManager.ts#L975) |
| 广告使用成功后 | `smartPreload()` | [第1053行](file:///home/devbox/project/src/composables/useAdManager.ts#L1053) |
| 广告显示失败时 | `preloadNextAd()` | [第1084行](file:///home/devbox/project/src/composables/useAdManager.ts#L1084) |

---

## 4. 广告加载完整流程

```
用户触发广告 → 检查预加载状态
                    │
                    ├── 有预加载广告 ───→ showPreloadedAd() → smartPreload()
                    │
                    ├── 正在预加载 ───→ 等待预加载完成 → showPreloadedAd() → smartPreload()
                    │
                    └── 无预加载 ───→ preloadNextAd()
                                            │
                                            ├── 预加载成功 ───→ showPreloadedAd() → smartPreload()
                                            │
                                            └── 预加载失败 ───→ 串行紧急加载所有广告位
```

---

## 5. 超时策略

| 层级 | 超时时间 | 说明 |
|------|---------|------|
| 单个广告位 | 2秒 | `preloadSingleSlot` 内设置 |
| 并行组总超时 | 3.1秒 | `preloadParallelGroup` 内设置 |
| 串行轮询总超时 | 15秒 | `preloadNextAd` 内设置 |
| 广告位间隔 | 500ms | 串行轮询时的延迟 |

---

## 6. 当前配置参数汇总

| 参数 | 值 | 位置 |
|------|-----|------|
| 广告位总数 | 12个（4组） | AD_GROUPS |
| 轮询方式 | 串行（逐个尝试） | preloadNextAd |
| 轮询次数 | 1轮 | preloadNextAd |
| 总超时 | 15秒 | TOTAL_TIMEOUT |
| 广告位间隔 | 500ms | preloadNextAd |
| 单个广告位超时 | 2秒 | preloadSingleSlot |
| 成功条件 | 任意一个广告位加载成功 | preloadNextAd |