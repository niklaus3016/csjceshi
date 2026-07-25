import { ref, onMounted, onUnmounted } from 'vue';
import BaiduAd from '../plugins/BaiduAdPlugin';
import { sendRedPacket, recordAdView, getPoolStatus, getUserTickets } from '../api/apiService';

declare global {
  interface Window {
    baidu?: any;
    _baidu?: any;
  }
}

interface AdConfig {
  appId: string;
  slotIds: string[];
}

const isLoaded = ref(false);
const isAdSdkReady = ref(false);
const isAdLoading = ref(false);
const isAdReady = ref(false);
const lastError = ref('');
const preloadAd = ref(false);

export function useAdManager(config: AdConfig) {
  let rewardVerifyListener: any = null;
  let adFailedListener: any = null;
  let videoDownloadSuccessListener: any = null;
  let videoDownloadFailedListener: any = null;
  let adLoadedListener: any = null;
  let adCloseListener: any = null;
  let csjDebugLogListener: any = null;
  let timeoutId: any = null;
  let retryTimeoutId: any = null;
  let currentResolve: any = null;
  let currentReject: any = null;
  let currentSlotIndex = 0;
  let triedSlots = 0;
  let slotTimeoutId: any = null;
  let currentSessionId = 0;
  let isProcessing = false; // 是否正在处理广告，防止并发
  let hasShownAd = false; // 是否已经显示过广告（用于防止用户跳过后继续尝试其他广告位）
  let lastEcpm = 0; // 全局ECPM缓存，用于预加载广告展示时获取ECPM
  
  // 预加载状态管理
  let preloadedAd: {
    slotId: string;
    isReady: boolean;
    loadedAt: number;
  } | null = null;
  let isPreloading = false; // 是否正在预加载
  let preloadingPromise: Promise<void> | null = null; // 预加载Promise，用于等待预加载完成
  
  // 广告位分组配置（穿山甲）
  const AD_GROUPS = {
    A: ['104282400']    // 保价1500
  };

  // 分组顺序（用于遍历）
  const GROUP_ORDER = ['A'];
  
  // 本地存储键名
  const SCHEDULER_STATE_KEY = 'ad_scheduler_state';
  
  // 过期清理常量
  const MAX_EXPOSES_WITHOUT_FLOAT = 30; // 累计30次曝光未达成连续2次命中上浮
  const EXPIRY_HOURS = 24; // 设备距离上次曝光超过24小时
  
  // 单个广告位超时时间（毫秒）- 测试：60秒
  const PARALLEL_TIMEOUT = 60000;
  // 预加载总超时时间（毫秒）- 测试：120秒（保证单广告位60秒超时能生效）
  const PRELOAD_TOTAL_TIMEOUT = 120000;
  // 组间延迟时间（毫秒）
  const GROUP_DELAY = 500;
  // 广告位间隔时间（毫秒）
  const GROUP5_SLOT_DELAY = 200;
  
  const delay = (ms: number): Promise<void> => {
    return new Promise(resolve => setTimeout(resolve, ms));
  };
  
  // 调度器状态接口
  interface SchedulerState {
    start_group: string;      // 下一轮轮询起始分组，默认'A'
    hit_streak: number;       // 连续命中计数，默认0
    expose_count: number;     // 设备累计广告曝光次数，默认0
    last_expose_time: number; // 上次广告曝光时间戳
  }
  
  // 加载调度器状态
  const loadSchedulerState = (): SchedulerState => {
    try {
      const stored = localStorage.getItem(SCHEDULER_STATE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        return {
          start_group: parsed.start_group || 'A',
          hit_streak: parsed.hit_streak || 0,
          expose_count: parsed.expose_count || 0,
          last_expose_time: parsed.last_expose_time || Date.now()
        };
      }
    } catch (e) {
      console.error('加载调度器状态失败:', e);
    }
    
    return {
      start_group: 'A',
      hit_streak: 0,
      expose_count: 0,
      last_expose_time: Date.now()
    };
  };
  
  // 保存调度器状态
  const saveSchedulerState = (state: SchedulerState): void => {
    try {
      localStorage.setItem(SCHEDULER_STATE_KEY, JSON.stringify(state));
    } catch (e) {
      console.error('保存调度器状态失败:', e);
    }
  };
  
  // 更新调度器状态（公共函数）
  const updateSchedulerStateOnHit = (state: SchedulerState, hitGroup: string | null): void => {
    const startIndex = GROUP_ORDER.indexOf(state.start_group);
    const lastIndex = GROUP_ORDER.length - 1;

    if (hitGroup) {
      const hitIndex = GROUP_ORDER.indexOf(hitGroup);

      if (hitGroup === state.start_group) {
        // 命中起始分组：连续命中计数+1，累计2次且非A组则上浮
        state.hit_streak += 1;
        console.log(`📈 连续命中起始分组计数: ${state.hit_streak}`);

        if (state.hit_streak >= 2 && startIndex > 0) {
          state.start_group = GROUP_ORDER[startIndex - 1];
          state.hit_streak = 0;
          console.log(`⬆️ 连续命中2次，上浮到分组 ${state.start_group}`);
        }
      } else if (hitIndex < startIndex) {
        // 命中更高价值分组（索引更小）：直接跳转到该分组
        state.start_group = hitGroup;
        state.hit_streak = 0;
        console.log(`⬆️ 命中更高价值分组 ${hitGroup}，下次从 ${hitGroup} 开始`);
      } else {
        // 命中更低价值分组（索引更大）：下沉一组
        state.hit_streak = 0;
        console.log(`📉 命中更低价值分组 ${hitGroup}，重置连续命中计数`);

        if (startIndex < lastIndex) {
          state.start_group = GROUP_ORDER[startIndex + 1];
          console.log(`⬇️ 下沉到分组 ${state.start_group}`);
        } else {
          console.log(`⏸️ 已在最低分组 ${state.start_group}，不再下沉`);
        }
      }
    } else {
      // 全空：下沉一组
      state.hit_streak = 0;
      console.log(`📉 全空，重置连续命中计数`);

      if (startIndex < lastIndex) {
        state.start_group = GROUP_ORDER[startIndex + 1];
        console.log(`⬇️ 下沉到分组 ${state.start_group}`);
      } else {
        console.log(`⏸️ 已在最低分组 ${state.start_group}，不再下沉`);
      }
    }
  };
  
  // 检查并执行过期清理
  const checkAndCleanupExpiredState = (state: SchedulerState): boolean => {
    const now = Date.now();
    
    // 条件1：距离上次曝光超过24小时
    if (now - state.last_expose_time > EXPIRY_HOURS * 3600 * 1000) {
      console.log('⏰ 调度器状态过期（超过24小时），重置');
      state.start_group = 'A';
      state.hit_streak = 0;
      return true;
    }
    
    // 条件2：累计30次曝光未达成连续2次命中上浮条件
    if (state.expose_count >= MAX_EXPOSES_WITHOUT_FLOAT) {
      console.log('⏰ 调度器状态过期（累计30次曝光），重置');
      state.start_group = 'A';
      state.hit_streak = 0;
      return true;
    }
    
    return false;
  };
  
  // 获取或生成设备ID
  const getDeviceId = (): string => {
    let deviceId = localStorage.getItem('deviceId');
    if (!deviceId) {
      deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
      localStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
  };

  const isBiddingSlot = (slotId: string): boolean => {
    const biddingSlots: string[] = [];
    return biddingSlots.includes(slotId);
  };

  // 并行请求广告组
  const tryParallelAdGroup = async (slotIds: string[]): Promise<{ ecpm: number; slotId: string } | null> => {
    console.log(`========== 开始并行请求广告组: ${slotIds.join(', ')} ==========`);
    
    const sessionId = currentSessionId;
    const checkSession = () => sessionId === currentSessionId;
    
    const adPromises = slotIds.map(slotId => {
      return new Promise<{ ecpm: number; slotId: string } | null>((resolve) => {
        let isResolved = false;
        let slotTimeoutId: any = null;
        let currentAdSuccess = false;
        
        const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
          if (!isResolved && checkSession()) {
            isResolved = true;
            cleanupSlotListeners();
            if (slotTimeoutId) clearTimeout(slotTimeoutId);
            resolve(result);
          }
        };
        
        const onRewardVerify = (result: any) => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          
          currentAdSuccess = true;
          if (slotTimeoutId) clearTimeout(slotTimeoutId);
          
          const ecpm = result.ecpm || 0;
          
          console.log(`✅ 广告成功 (${slotId})，ECPM:`, ecpm);
          
          resolveOnce({ ecpm, slotId });
        };
        
        const onAdFailed = (error: any) => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          console.warn(`⚠️ 广告加载失败 (${slotId}):`, error?.error || error);
          resolveOnce(null);
        };
        
        const onVideoDownloadSuccess = async () => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          
          console.log(`✅ 视频下载成功 (${slotId})，准备显示广告`);
          
          // 立即设置广告显示标志，防止用户跳过后继续尝试其他广告位
          hasShownAd = true;
          
          try {
            if (slotTimeoutId) clearTimeout(slotTimeoutId);
            
            // 检查广告是否就绪
            console.log(`🔍 检查广告就绪状态 (${slotId})...`);
            try {
              const readyStatus = await BaiduAd.isReady();
              console.log(`📊 广告就绪状态 (${slotId}):`, readyStatus);
              
              if (!readyStatus.ready) {
                console.warn(`⚠️ 广告未就绪 (${slotId})，尝试强制显示...`);
              }
            } catch (error) {
              console.warn(`⚠️ 检查广告就绪状态失败 (${slotId}):`, error);
            }
            
            console.log(`✅ 广告位加载成功且已就绪 (${slotId})，准备播放`);
            
            // 显示广告
            BaiduAd.showRewardVideoAd();
            console.log(`✅ 广告显示命令已发送 (${slotId})`);
          } catch (error) {
            console.error(`❌ 显示广告失败 (${slotId}):`, error);
            resolveOnce(null);
          }
        };
        
        const onVideoDownloadFailed = () => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          console.warn(`⚠️ 视频下载失败 (${slotId})`);
          resolveOnce(null);
        };
        
        const onAdClose = () => {
          if (!checkSession()) return;
          console.log(`✅ 广告关闭回调 (${slotId})`);
          // 如果已经显示过广告（用户跳过），停止尝试其他广告位
          if (hasShownAd) {
            console.log(`🛑 已显示过广告，停止尝试其他广告位 (${slotId})`);
            resolveOnce(null);
            return;
          }
          if (!currentAdSuccess) {
            console.log(`广告关闭但未获得奖励 (${slotId})，标记为失败`);
            resolveOnce(null);
          }
        };
        
        // 注册监听器
        BaiduAd.addListener('onRewardVerify', onRewardVerify);
        BaiduAd.addListener('onAdFailed', onAdFailed);
        BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
        BaiduAd.addListener('onVideoDownloadFailed', onVideoDownloadFailed);
        BaiduAd.addListener('onAdClose', onAdClose);
        
        // 清理监听器的函数
        const cleanupSlotListeners = () => {
          try {
            BaiduAd.removeListener('onRewardVerify', onRewardVerify);
            BaiduAd.removeListener('onAdFailed', onAdFailed);
            BaiduAd.removeListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
            BaiduAd.removeListener('onVideoDownloadFailed', onVideoDownloadFailed);
            BaiduAd.removeListener('onAdClose', onAdClose);
          } catch (e) {
            console.warn(`清理监听器失败 (${slotId}):`, e);
          }
        };
        
        // 加载广告
        BaiduAd.loadRewardVideoAd({ adId: slotId })
          .then(() => console.log(`✅ 广告加载请求已发送 (${slotId})`))
          .catch((err: any) => {
            console.error(`❌ 加载广告请求失败 (${slotId}):`, err);
            resolveOnce(null);
          });
        
        // 广告位超时
        slotTimeoutId = setTimeout(() => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          console.warn(`⏱️ 广告加载超时 (${slotId})`);
          resolveOnce(null);
        }, PARALLEL_TIMEOUT);
      });
    });
    
    // 等待所有并行请求完成，返回第一个成功的结果
    const results = await Promise.all(adPromises);
    for (const result of results) {
      if (result && checkSession()) {
        console.log(`🎉 并行请求成功，使用广告位: ${result.slotId}，ECPM: ${result.ecpm}`);
        return result;
      }
    }
    
    console.log('❌ 并行请求组所有广告位均失败');
    return null;
  };
  
  const getNextSlotId = (): string => {
    if (!config.slotIds?.length) throw new Error('广告位配置为空');
    const slotId = config.slotIds[currentSlotIndex];
    const currentRound = Math.floor(triedSlots / config.slotIds.length) + 1;
    const positionInRound = (triedSlots % config.slotIds.length) + 1;
    console.log(`当前轮询广告位: ${slotId} (第${currentRound}轮 ${positionInRound}/${config.slotIds.length})`);
    currentSlotIndex = (currentSlotIndex + 1) % config.slotIds.length;
    triedSlots++;
    return slotId;
  };
  
  // 触发预加载（延迟1秒）
  const triggerPreloadAfterDelay = () => {
    setTimeout(() => {
      preloadNextAd();
    }, 1000);
  };
  
  // 智能预加载触发函数（方案C：避免重复触发）
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
  
  // 预加载一组并行广告位（第一个成功就返回）
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
        
        const onVideoDownloadFailed = () => {
          if (isSlotResolved || resolved) return;
          isSlotResolved = true;
          console.log(`❌ 并行预加载失败: ${slotId} (视频下载失败)`);
          cleanupSlotListeners(slotId);
        };
        
        const onAdFailed = (error: any) => {
          if (isSlotResolved || resolved) return;
          isSlotResolved = true;
          console.log(`❌ 并行预加载失败: ${slotId} (广告加载失败)`, error);
          cleanupSlotListeners(slotId);
        };
        
        const cleanupSlot = () => {
          try {
            BaiduAd.removeListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
            BaiduAd.removeListener('onVideoDownloadFailed', onVideoDownloadFailed);
            BaiduAd.removeListener('onAdFailed', onAdFailed);
          } catch (e) {
            // 忽略清理错误
          }
        };
        
        listeners.push({ slotId, cleanup: cleanupSlot });
        
        // 注册监听器
        BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
        BaiduAd.addListener('onVideoDownloadFailed', onVideoDownloadFailed);
        BaiduAd.addListener('onAdFailed', onAdFailed);
        
        // 设置超时（2秒）
        setTimeout(() => {
          if (!isSlotResolved && !resolved) {
            isSlotResolved = true;
            console.log(`⏱️ 并行预加载超时: ${slotId}`);
            cleanupSlotListeners(slotId);
          }
        }, 2000);
        
        // 获取用户信息
        const employeeId = localStorage.getItem('employeeId') || '';
        let deviceId = localStorage.getItem('deviceId');
        if (!deviceId) {
          deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
          localStorage.setItem('deviceId', deviceId);
        }
        
        // 发起请求（传递用户信息，确保ECPM正确）
        BaiduAd.loadRewardVideoAd({ 
          adId: slotId,
          userId: 'user_' + employeeId + '_' + Date.now(),
          extraData: JSON.stringify({ 
            employeeId: employeeId,
            deviceId: deviceId,
            timestamp: Date.now(),
            slotId: slotId,
            appId: config.appId
          })
        }).catch((error) => {
          if (!isSlotResolved && !resolved) {
            isSlotResolved = true;
            console.log(`❌ 并行预加载请求失败: ${slotId}`, error);
            cleanupSlotListeners(slotId);
          }
        });
      });
      
      // 清理单个广告位的监听器
      const cleanupSlotListeners = (slotId: string) => {
        const listener = listeners.find(l => l.slotId === slotId);
        if (listener) {
          listener.cleanup();
        }
      };
      
      // 清理所有监听器
      const cleanupAllListeners = () => {
        listeners.forEach(l => l.cleanup());
      };
      
      // 如果所有广告位都失败，返回失败
      setTimeout(() => {
        if (!resolved) {
          resolved = true;
          cleanupAllListeners();
          resolve({ success: false, slotId: null });
        }
      }, 3100);
    });
  };

  // 预加载单个广告位（串行用）
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
      
      const onVideoDownloadFailed = () => {
        if (!isResolved) {
          isResolved = true;
          console.log(`❌ 串行预加载失败: ${slotId} (视频下载失败)`);
          cleanupListeners();
          resolve(false);
        }
      };
      
      const onAdFailed = (error: any) => {
        if (!isResolved) {
          isResolved = true;
          console.log(`❌ 串行预加载失败: ${slotId} (广告加载失败)`, error);
          cleanupListeners();
          resolve(false);
        }
      };
      
      const cleanupListeners = () => {
        try {
          BaiduAd.removeListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
          BaiduAd.removeListener('onVideoDownloadFailed', onVideoDownloadFailed);
          BaiduAd.removeListener('onAdFailed', onAdFailed);
        } catch (e) {
          // 忽略清理错误
        }
      };
      
      // 注册监听器
      BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
      BaiduAd.addListener('onVideoDownloadFailed', onVideoDownloadFailed);
      BaiduAd.addListener('onAdFailed', onAdFailed);
      
      // 设置超时
      setTimeout(() => {
        if (!isResolved) {
          isResolved = true;
          console.log(`⏱️ 串行预加载超时: ${slotId}`);
          cleanupListeners();
          resolve(false);
        }
      }, PARALLEL_TIMEOUT);
      
      // 获取用户信息
      const employeeId = localStorage.getItem('employeeId') || '';
      let deviceId = localStorage.getItem('deviceId');
      if (!deviceId) {
        deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('deviceId', deviceId);
      }
      
      // 调用loadRewardVideoAd()加载广告（传递用户信息，确保ECPM正确）
      BaiduAd.loadRewardVideoAd({ 
        adId: slotId,
        userId: 'user_' + employeeId + '_' + Date.now(),
        extraData: JSON.stringify({ 
          employeeId: employeeId,
          deviceId: deviceId,
          timestamp: Date.now(),
          slotId: slotId,
          appId: config.appId
        })
      }).catch((error) => {
        if (!isResolved) {
          isResolved = true;
          console.log(`❌ 串行预加载请求失败: ${slotId}`, error);
          cleanupListeners();
          resolve(false);
        }
      });
    });
  };

  // 预加载下一个广告（策略：智能瀑布流分组轮询）
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
    console.log('🚀 开始预加载任务（策略：智能瀑布流分组轮询）');
    
    // 创建新的预加载Promise
    preloadingPromise = (async () => {
      const totalStartTime = Date.now();
      let foundAd = false;
      let totalTimeoutId: any = null;
      let timeoutReached = false;
      
      // 设置总超时定时器
      totalTimeoutId = setTimeout(() => {
        console.log(`⏱️ 预加载总超时（${PRELOAD_TOTAL_TIMEOUT}ms），强制终止`);
        timeoutReached = true;
      }, PRELOAD_TOTAL_TIMEOUT);
      
      try {
        // 读取调度器状态，确定起始分组
        const schedulerState = loadSchedulerState();
        // 检查过期清理
        checkAndCleanupExpiredState(schedulerState);
        let startIndex = GROUP_ORDER.indexOf(schedulerState.start_group);
        if (startIndex === -1) startIndex = 0;
        
        console.log(`🎯 预加载起始分组: ${schedulerState.start_group}（索引: ${startIndex}）`);
        
        // 从起始分组开始遍历
        for (let i = startIndex; i < GROUP_ORDER.length; i++) {
          if (timeoutReached) break;
          
          const group = GROUP_ORDER[i];
          const slots = AD_GROUPS[group as keyof typeof AD_GROUPS];
          
          console.log(`🔍 预加载分组 ${group}，广告位：${slots.join(', ')}`);
          
          // 组内串行预加载
          for (let j = 0; j < slots.length; j++) {
            if (timeoutReached) break;
            
            const slotId = slots[j];
            console.log(`🔄 预加载 [${j + 1}/${slots.length}]: ${slotId}`);
            
            const isReady = await preloadSingleSlot(slotId);
            
            if (isReady) {
              preloadedAd = {
                slotId: slotId,
                isReady: true,
                loadedAt: Date.now()
              };
              console.log(`🎉 预加载成功: ${slotId}`);
              foundAd = true;
              break;
            }
            
            // 广告位之间延迟500ms
            if (j < slots.length - 1) {
              await delay(500);
            }
          }
          
          if (foundAd) break;
          
          // 组间延迟（除了最后一组）
          if (i < GROUP_ORDER.length - 1) {
            await delay(GROUP_DELAY);
          }
        }
      } finally {
        if (totalTimeoutId) clearTimeout(totalTimeoutId);
        
        isPreloading = false;
        preloadingPromise = null;
        const totalTime = ((Date.now() - totalStartTime) / 1000).toFixed(1);
        console.log(`📋 预加载任务全部结束，${foundAd ? '成功' : '未找到广告'}，总耗时${totalTime}秒${timeoutReached ? '（超时终止）' : ''}`);
      }
    })();
    
    return preloadingPromise;
  };
  
  // 串行请求广告组
  const trySerialAdGroup = async (slotIds: string[], slotDelay: number = 0): Promise<{ ecpm: number; slotId: string } | null> => {
    console.log(`========== 开始串行请求广告组（共${slotIds.length}个广告位） ==========`);
    
    const sessionId = currentSessionId;
    const checkSession = () => sessionId === currentSessionId;
    
    for (let i = 0; i < slotIds.length; i++) {
      const slotId = slotIds[i];
      const slotIndex = i + 1; // 序号从1开始
      const totalSlots = slotIds.length;
      
      // 检查是否已经显示过广告（用户跳过后停止尝试其他广告位）
      if (hasShownAd) {
        console.log('🛑 已显示过广告，停止尝试其他广告位');
        return null;
      }
      
      if (!checkSession()) {
        console.log('会话已过期，停止加载');
        return null;
      }
      
      // 广告位间延迟（除了第一个）
      if (i > 0 && slotDelay > 0) {
        console.log(`等待 ${slotDelay}ms 后尝试下一个广告位...`);
        await delay(slotDelay);
      }
      
      console.log(`尝试加载广告位 [${slotIndex}/${totalSlots}]: ${slotId}`);
      
      const result = await new Promise<{ ecpm: number; slotId: string } | null>((resolve) => {
        let isResolved = false;
        let slotTimeoutId: any = null;
        let currentAdSuccess = false;
        
        const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
          if (!isResolved && checkSession()) {
            isResolved = true;
            cleanupSlotListeners();
            if (slotTimeoutId) clearTimeout(slotTimeoutId);
            resolve(result);
          }
        };
        
        const onRewardVerify = (result: any) => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          
          currentAdSuccess = true;
          if (slotTimeoutId) clearTimeout(slotTimeoutId);
          
          const ecpm = result.ecpm || 0;
          
          console.log(`✅ 广告成功 (${slotId})，ECPM:`, ecpm);
          
          resolveOnce({ ecpm, slotId });
        };
        
        const onAdFailed = (error: any) => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          console.warn(`⚠️ 广告加载失败 (${slotId}):`, error?.error || error);
          resolveOnce(null);
        };
        
        const onVideoDownloadSuccess = async () => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          
          console.log(`✅ 视频下载成功 (${slotId})，准备显示广告`);
          try {
            if (slotTimeoutId) clearTimeout(slotTimeoutId);
            
            // 检查广告是否就绪
            console.log(`🔍 检查广告就绪状态 (${slotId})...`);
            try {
              const readyStatus = await BaiduAd.isReady();
              console.log(`📊 广告就绪状态 (${slotId}):`, readyStatus);
              
              if (!readyStatus.ready) {
                console.warn(`⚠️ 广告未就绪 (${slotId})，尝试强制显示...`);
              }
            } catch (error) {
              console.warn(`⚠️ 检查广告就绪状态失败 (${slotId}):`, error);
            }
            
            console.log(`✅ 广告位加载成功且已就绪 (${slotId})，准备播放`);
            await BaiduAd.showRewardVideoAd();
            console.log(`✅ 广告显示命令已发送 (${slotId})`);
          } catch (error) {
            console.error(`❌ 显示广告失败 (${slotId}):`, error);
            resolveOnce(null);
          }
        };
        
        const onVideoDownloadFailed = () => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          console.warn(`⚠️ 视频下载失败 (${slotId})`);
          resolveOnce(null);
        };
        
        const onAdClose = () => {
          if (!checkSession()) return;
          console.log(`✅ 广告关闭回调 (${slotId})`);
          if (!currentAdSuccess) {
            console.log(`广告关闭但未获得奖励 (${slotId})，标记为失败`);
            resolveOnce(null);
          }
        };
        
        // 注册监听器
        BaiduAd.addListener('onRewardVerify', onRewardVerify);
        BaiduAd.addListener('onAdFailed', onAdFailed);
        BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
        BaiduAd.addListener('onVideoDownloadFailed', onVideoDownloadFailed);
        BaiduAd.addListener('onAdClose', onAdClose);
        
        // 清理监听器的函数
        const cleanupSlotListeners = () => {
          try {
            BaiduAd.removeListener('onRewardVerify', onRewardVerify);
            BaiduAd.removeListener('onAdFailed', onAdFailed);
            BaiduAd.removeListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
            BaiduAd.removeListener('onVideoDownloadFailed', onVideoDownloadFailed);
            BaiduAd.removeListener('onAdClose', onAdClose);
          } catch (e) {
            console.warn(`清理监听器失败 (${slotId}):`, e);
          }
        };
        
        // 加载广告
        BaiduAd.loadRewardVideoAd({ adId: slotId })
          .then(() => console.log(`✅ 广告加载请求已发送 (${slotId})`))
          .catch((err: any) => {
            console.error(`❌ 加载广告请求失败 (${slotId}):`, err);
            resolveOnce(null);
          });
        
        // 广告位超时
        slotTimeoutId = setTimeout(() => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          console.warn(`⏱️ 广告加载超时 (${slotId})`);
          resolveOnce(null);
        }, PARALLEL_TIMEOUT);
      });
      
      // 如果当前广告位成功，立即返回
      if (result) {
        console.log(`🎉 串行请求成功，使用广告位: ${result.slotId}，ECPM: ${result.ecpm}`);
        return result;
      }
      
      // 检查是否已经显示过广告（用户跳过后停止尝试其他广告位）
      if (hasShownAd) {
        console.log('🛑 已显示过广告，停止尝试其他广告位');
        return null;
      }
      
      console.log(`广告位 ${slotId} 失败，尝试下一个...`);
    }
    
    console.log('❌ 串行请求组所有广告位均失败');
    return null;
  };
  
  // 智能瀑布流核心调度逻辑
  const executeSmartWaterfall = async (): Promise<{ ecpm: number; slotId: string } | null> => {
    console.log('========== 智能瀑布流调度开始 ==========');
    
    // 1. 读取本地状态
    const state = loadSchedulerState();
    console.log(`📊 当前状态: start_group=${state.start_group}, hit_streak=${state.hit_streak}, expose_count=${state.expose_count}`);
    
    // 2. 检查过期清理
    checkAndCleanupExpiredState(state);
    
    // 3. 曝光计数自增
    state.expose_count += 1;
    state.last_expose_time = Date.now();
    const isFirstExpose = state.expose_count === 1;
    console.log(`🔢 曝光次数: ${state.expose_count}${isFirstExpose ? '（首次曝光，不计入调度）' : ''}`);
    
    // 4. 确定起始分组
    let startIndex = GROUP_ORDER.indexOf(state.start_group);
    if (startIndex === -1) startIndex = 0; // 默认从A组开始
    console.log(`🎯 起始分组: ${state.start_group}（索引: ${startIndex}）`);
    
    // 5. 从起始分组开始遍历
    let hitGroup: string | null = null;
    let result: { ecpm: number; slotId: string } | null = null;
    
    for (let i = startIndex; i < GROUP_ORDER.length; i++) {
      const group = GROUP_ORDER[i];
      const slots = AD_GROUPS[group as keyof typeof AD_GROUPS];
      
      console.log(`🔍 遍历分组 ${group}，广告位：${slots.join(', ')}`);
      
      // 组内串行请求（所有分组都是串行）
      const groupResult = await trySerialAdGroup(slots);
      
      if (groupResult) {
        hitGroup = group;
        result = groupResult;
        console.log(`🎉 分组 ${group} 命中，广告位：${result.slotId}，eCPM：${result.ecpm}`);
        break;
      }
      
      console.log(`❌ 分组 ${group} 无广告，继续下一分组`);
      
      // 组间延迟（除了最后一组）
      if (i < GROUP_ORDER.length - 1) {
        await delay(GROUP_DELAY);
      }
    }
    
    // 6. 更新状态（仅当不是第一条广告）
    if (!isFirstExpose) {
      updateSchedulerStateOnHit(state, hitGroup);
    } else {
      console.log('⚠️ 首次曝光，不更新调度状态');
    }
    
    // 7. 持久化状态
    saveSchedulerState(state);
    
    // 8. 埋点上报（可选）
    console.log(`📋 智能瀑布流调度结束: 起始分组=${state.start_group}, 命中分组=${hitGroup}, 命中广告位=${result?.slotId || '无'}, eCPM=${result?.ecpm || 0}`);
    
    return result;
  };
  
  const resetAdState = () => {
    currentSlotIndex = 0;
    triedSlots = 0;
    isAdLoading.value = false;
    isAdReady.value = false;
    hasShownAd = false; // 重置广告显示标志
    currentSessionId++;
    console.log(`🆕 新会话开始，会话ID: ${currentSessionId}`);
  };

  onMounted(() => initializeAdSdk());
  onUnmounted(() => cleanupListeners());

  const cleanupListeners = () => {
    console.log('🔄 清理广告监听器...');
    
    const listeners = [
      { name: 'onRewardVerify', handler: rewardVerifyListener },
      { name: 'onAdFailed', handler: adFailedListener },
      { name: 'onVideoDownloadSuccess', handler: videoDownloadSuccessListener },
      { name: 'onVideoDownloadFailed', handler: videoDownloadFailedListener },
      { name: 'onAdLoaded', handler: adLoadedListener },
      { name: 'onAdClose', handler: adCloseListener },
      { name: 'onCsjDebugLog', handler: csjDebugLogListener }
    ];
    
    listeners.forEach(({ name, handler }) => {
      if (handler) {
        try {
          BaiduAd.removeListener(name, handler);
        } catch (e) {
          console.warn(`移除 ${name} 监听器失败:`, e);
        }
      }
    });
    
    rewardVerifyListener = null;
    adFailedListener = null;
    videoDownloadSuccessListener = null;
    videoDownloadFailedListener = null;
    adLoadedListener = null;
    adCloseListener = null;
    csjDebugLogListener = null;
    
    [timeoutId, retryTimeoutId, slotTimeoutId].forEach(id => {
      if (id) {
        clearTimeout(id);
        id = null;
      }
    });
    
    console.log('✅ 监听器清理完成');
  };

  const isNativeApp = () => {
    return typeof window !== 'undefined' && 
           (window as any).Capacitor?.getPlatform() === 'android';
  };

  const initializeAdSdk = async () => {
    if (typeof window === 'undefined') return;

    try {
      if (isNativeApp()) {
        console.log('原生 Android 环境，使用穿山甲原生 SDK');
        
        try {
          const sdkReady = await BaiduAd.isSdkReady();
          if (sdkReady.ready) {
            console.log('📱 穿山甲SDK已就绪');
            isAdSdkReady.value = true;
          } else {
            console.log('📱 穿山甲SDK尚未就绪，等待初始化完成');
          }
        } catch (error) {
          console.warn('检查SDK就绪状态失败:', error);
        }
        
        isLoaded.value = true;
        preloadAd.value = true;
        
        csjDebugLogListener = (data: any) => {
          if (!data) return;
          const tag = data.tag || 'UNKNOWN';
          const message = data.message || '';
          const timestamp = data.timestamp || Date.now();
          
          const colors: { [key: string]: string } = {
            LOAD: 'color: #3B82F6',
            SUCCESS: 'color: #10B981',
            ERROR: 'color: #EF4444',
            ECPM: 'color: #8B5CF6',
            ECPM_ERROR: 'color: #F59E0B',
            AD: 'color: #06B6D4',
            REWARD: 'color: #EC4899',
            SHOW: 'color: #14B8A6',
            DOWNLOAD: 'color: #F97316',
            STATUS: 'color: #6B7280',
            AUTH: 'color: #84CC16',
            COUPON: 'color: #A855F7'
          };
          
          const color = colors[tag] || 'color: #6B7280';
          console.log(`%c[CSJ][${tag}] ${message}`, color);
          
          // 捕获ECPM值，用于预加载广告展示时获取ECPM
          if (tag === 'ECPM') {
            const ecpmMatch = message.match(/ECPM=([\d.]+)/);
            if (ecpmMatch && ecpmMatch[1]) {
              lastEcpm = parseFloat(ecpmMatch[1]);
              console.log(`🔄 全局ECPM已更新: ${lastEcpm}`);
            }
          }
        };
        
        try {
          BaiduAd.addListener('onCsjDebugLog', csjDebugLogListener);
          console.log('🔍 CsjAd调试日志监听器已注册');
        } catch (e) {
          console.warn('注册CsjAd调试日志监听器失败:', e);
        }
        
        // 注释掉自动预加载，改为手动点击时加载
        // setTimeout(() => {
        //   console.log('📱 原生环境开始预加载广告');
        //   preloadNextAd();
        // }, 500);
        
        return;
      }

      isLoaded.value = true;
      isAdSdkReady.value = false;
      preloadAd.value = true;
    } catch (error) {
      console.error('初始化广告 SDK 失败:', error);
      isLoaded.value = true;
      isAdSdkReady.value = false;
      preloadAd.value = true;
    }
  };
  
  // 显示预加载的广告
  const showPreloadedAd = async (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
    if (!preloadedAd || !preloadedAd.isReady) {
      console.log('预加载广告未就绪，开始正常加载');
      reject(new Error('预加载广告未就绪'));
      return;
    }
    
    const slotId = preloadedAd.slotId;
    console.log(`🚀 使用预加载的广告位: ${slotId}`);
    
    // 清除预加载状态（必须在这里清除，防止重复使用）
    preloadedAd = null;
    
    // 设置广告显示标志
    hasShownAd = true;
    
    // 注册监听器
    let isResolved = false;
    let currentAdSuccess = false;
    let currentEcpm = 0;
    
    const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
      if (!isResolved) {
        isResolved = true;
        cleanupSlotListeners();
        if (result) {
          resolve(result);
        } else {
          reject(new Error('广告显示失败'));
        }
      }
    };
    
    const onRewardVerify = (result: any) => {
      if (currentAdSuccess || isResolved) return;
      
      currentAdSuccess = true;
      
      const ecpm = result.ecpm || currentEcpm || lastEcpm || 0;
      
      console.log(`✅ 预加载广告成功 (${slotId})，ECPM:`, ecpm);
      
      const state = loadSchedulerState();
      checkAndCleanupExpiredState(state);
      state.expose_count += 1;
      state.last_expose_time = Date.now();
      const isFirstExpose = state.expose_count === 1;
      
      if (!isFirstExpose) {
        let hitGroup: string | null = null;
        for (const [group, slots] of Object.entries(AD_GROUPS)) {
          if (slots.includes(slotId)) {
            hitGroup = group;
            break;
          }
        }
        
        updateSchedulerStateOnHit(state, hitGroup);
      } else {
        console.log('⚠️ 首次曝光，不更新调度状态');
      }
      
      saveSchedulerState(state);
      console.log(`📋 预加载广告调度状态更新: start_group=${state.start_group}, hit_streak=${state.hit_streak}`);
      
      resolveOnce({ ecpm, slotId });
    };
    
    const onAdShow = (data: any) => {
      if (data && data.ecpm) {
        currentEcpm = data.ecpm;
        console.log(`📺 预加载广告页面已打开 (${slotId})，ECPM=${currentEcpm}，延迟1秒触发预加载`);
      } else {
        console.log(`📺 预加载广告页面已打开 (${slotId})，延迟1秒触发预加载`);
      }
      setTimeout(() => {
        smartPreload();
      }, 1000);
    };
    
    const onAdClose = () => {
      console.log(`⏳ 预加载广告关闭，等待奖励回调... (${slotId})`);
      setTimeout(() => {
        if (!currentAdSuccess && !isResolved) {
          console.log(`❌ 预加载广告关闭后未获得奖励 (${slotId})，标记为失败`);
          resolveOnce(null);
        }
      }, 3000);
    };
    
    const onAdFailed = (error: any) => {
      if (isResolved) return;
      console.log(`❌ 预加载广告展示失败 (${slotId}):`, error);
      resolveOnce(null);
    };
    
    const onVideoDownloadSuccess = () => {
      if (isResolved) return;
      console.log(`✅ 预加载广告缓存成功 (${slotId})`);
    };
    
    const onAdLoaded = () => {
      if (isResolved) return;
      console.log(`✅ 预加载广告加载成功 (${slotId})`);
    };
    
    const cleanupSlotListeners = () => {
      try {
        BaiduAd.removeListener('onRewardVerify', onRewardVerify);
        BaiduAd.removeListener('onAdClose', onAdClose);
        BaiduAd.removeListener('onAdShow', onAdShow);
        BaiduAd.removeListener('onAdFailed', onAdFailed);
        BaiduAd.removeListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
        BaiduAd.removeListener('onAdLoaded', onAdLoaded);
      } catch (e) {
        console.warn(`清理预加载广告监听器失败 (${slotId}):`, e);
      }
    };
    
    // 注册监听器
    BaiduAd.addListener('onRewardVerify', onRewardVerify);
    BaiduAd.addListener('onAdClose', onAdClose);
    BaiduAd.addListener('onAdShow', onAdShow);
    BaiduAd.addListener('onAdFailed', onAdFailed);
    BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
    BaiduAd.addListener('onAdLoaded', onAdLoaded);
    
    try {
      await BaiduAd.showRewardVideoAd();
      console.log(`✅ 预加载广告显示命令已发送 (${slotId})`);
    } catch (error) {
      console.error(`❌ 显示预加载广告失败 (${slotId}):`, error);
      cleanupSlotListeners();
      reject(new Error('广告显示失败'));
    }
  };

  // 获取用户ID
  const getUserId = (): string | null => {
    return localStorage.getItem('userId') || null;
  };

  // 获取员工ID
  const getEmployeeId = (): string | null => {
    return localStorage.getItem('empId') || null;
  };

  // 红包触发逻辑已移至后端处理

  const showAd = async (): Promise<{ ecpm: number; slotId: string }> => {
    return new Promise(async (resolve, reject) => {
      if (isProcessing) {
        console.log('⚠️ 已有广告正在处理，请等待');
        reject(new Error('已有广告正在处理'));
        return;
      }
      
      isProcessing = true;
      currentResolve = resolve;
      currentReject = reject;
      
      console.log('========== 开始加载激励视频广告（简单串行轮询）==========');
      console.log('所有广告位:', config.slotIds);
      console.log('是否原生环境:', isNativeApp());
      
      // 先检查是否有预加载的广告（不要先调用resetAdState，否则会清除预加载状态）
      console.log(`🔍 预加载广告检查: preloadedAd=${JSON.stringify(preloadedAd)}`);
      if (preloadedAd && preloadedAd.isReady) {
        console.log(`✅ 发现预加载广告，直接展示: ${preloadedAd.slotId}`);
        
        try {
          await showPreloadedAd(resolve, reject);
          isProcessing = false;
          return;
        } catch (error) {
          console.log(`❌ 预加载广告展示失败，回退到正常加载:`, error);
        }
      } else {
        console.log('📋 没有预加载广告，开始正常加载');
      }
      
      // 只有在需要重新加载时才重置状态
      resetAdState();
      
      for (let i = 0; i < config.slotIds.length; i++) {
        const slotId = config.slotIds[i];
        console.log(`🔄 尝试广告位 [${i + 1}/${config.slotIds.length}]: ${slotId}`);
        
        try {
          const result = await trySingleAdSlot(slotId);
          if (result) {
            console.log(`✅ 广告位 ${slotId} 成功，eCPM: ${result.ecpm}`);
            isProcessing = false;
            resolve(result);
            return;
          }
        } catch (error) {
          console.log(`❌ 广告位 ${slotId} 失败:`, error);
        }
        
        if (i < config.slotIds.length - 1) {
          console.log(`⏱️ 等待 500ms 后尝试下一个广告位...`);
          await delay(500);
        }
      }
      
      console.log('❌ 所有广告位都失败');
      isProcessing = false;
      reject(new Error('暂无广告'));
    });
  };
  
  const trySingleAdSlot = async (slotId: string): Promise<{ ecpm: number; slotId: string } | null> => {
    return new Promise((resolve, reject) => {
      let isResolved = false;
      
      const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
        if (!isResolved) {
          isResolved = true;
          cleanupListeners();
          resolve(result);
        }
      };
      
      const onRewardVerify = (result: any) => {
        if (isResolved) return;
        
        const ecpm = result.ecpm || 0;
        
        resolveOnce({ ecpm, slotId });
      };
      
      const onAdFailed = (error: any) => {
        if (isResolved) return;
        console.log(`❌ 广告位 ${slotId} 加载失败:`, error);
        resolveOnce(null);
      };
      
      const onAdClose = () => {
        if (isResolved) return;
        console.log(`⏳ 广告关闭，等待奖励回调...`);
        setTimeout(() => {
          if (!isResolved) {
            console.log(`❌ 广告关闭后未收到奖励回调，标记为失败`);
            resolveOnce(null);
          }
        }, 1500);
      };
      
      const onAdLoaded = (data: any) => {
        if (isResolved) return;
        console.log(`✅ 广告位 ${slotId} 加载成功`);
      };
      
      const onVideoDownloadSuccess = (data: any) => {
        if (isResolved) return;
        console.log(`✅ 广告位 ${slotId} 缓存成功，准备显示`);
        try {
          BaiduAd.showRewardVideoAd();
        } catch (e) {
          console.error('❌ 显示广告失败:', e);
          resolveOnce(null);
        }
      };
      
      const onAdShow = () => {
        if (!isResolved) {
          clearTimeout(slotTimeoutId);
          console.log('✅ 广告开始展示，清除加载超时定时器');
        }
        console.log('📺 广告开始展示，延迟1秒触发预加载');
        setTimeout(() => {
          smartPreload();
        }, 1000);
      };
      
      const cleanupListeners = () => {
        try {
          BaiduAd.removeListener('onRewardVerify', onRewardVerify);
          BaiduAd.removeListener('onAdFailed', onAdFailed);
          BaiduAd.removeListener('onAdClose', onAdClose);
          BaiduAd.removeListener('onAdLoaded', onAdLoaded);
          BaiduAd.removeListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
          BaiduAd.removeListener('onAdShow', onAdShow);
        } catch (e) {
          console.warn(`清理监听器失败 (${slotId}):`, e);
        }
      };
      
      BaiduAd.addListener('onRewardVerify', onRewardVerify);
      BaiduAd.addListener('onAdFailed', onAdFailed);
      BaiduAd.addListener('onAdClose', onAdClose);
      BaiduAd.addListener('onAdLoaded', onAdLoaded);
      BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
      BaiduAd.addListener('onAdShow', onAdShow);
      
      const employeeId = localStorage.getItem('employeeId') || '';
      let deviceId = localStorage.getItem('deviceId');
      if (!deviceId) {
        deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('deviceId', deviceId);
      }
      
      BaiduAd.loadRewardVideoAd({ 
        adId: slotId,
        userId: 'user_' + employeeId + '_' + Date.now(),
        extraData: JSON.stringify({ 
          employeeId: employeeId,
          deviceId: deviceId,
          timestamp: Date.now(),
          slotId: slotId,
          appId: config.appId
        })
      });
      
      slotTimeoutId = setTimeout(() => {
        if (!isResolved) {
          console.log(`⏱️ 广告位 ${slotId} 超时`);
          resolveOnce(null);
        }
      }, 60000);
    });
  };

  // 串行加载单个广告位
  const tryLoadAd = async (): Promise<'success' | 'failed' | 'session_expired'> => {
    const sessionId = currentSessionId;
    let currentAdSuccess = false; // 当前广告是否成功
    
    const checkSession = () => sessionId === currentSessionId;
    
    if (!checkSession()) {
      console.log('会话已过期，停止加载');
      return 'session_expired';
    }
    
    // 检查是否已尝试所有轮次
    const maxSlots = config.slotIds.length;
    if (triedSlots >= maxSlots) {
      console.log('所有广告位都已尝试');
      return 'failed';
    }
    
    // 清理之前的监听器
    cleanupListeners();
    
    const selectedSlotId = getNextSlotId();
    console.log(`尝试加载广告位: ${selectedSlotId}`);
    
    return new Promise((resolveLoad) => {
      let isResolved = false; // 标记当前加载是否已解决
      
      const resolveOnce = (result: 'success' | 'failed') => {
        if (!isResolved) {
          isResolved = true;
          resolveLoad(result);
        }
      };
      
      const onAdLoaded = (data: any) => {
        if (!checkSession()) return;
        console.log('✅ 广告加载成功回调');
        if (data && data.debug) {
          console.log('🔍 ECPM反射调试信息:', data.debug);
          console.log('   - mediationManager:', data.debug.mediationManager);
          console.log('   - loadEcpmObj:', data.debug.loadEcpmObj);
          console.log('   - ecpmObj:', data.debug.ecpmObj);
          console.log('   - ecpmType:', data.debug.ecpmType);
          console.log('   - ecpm:', data.debug.ecpm);
          if (data.debug.error) {
            console.error('   ❌ 反射错误:', data.debug.error);
          }
        }
      };

      const onRewardVerify = (result: any) => {
        if (!checkSession() || currentAdSuccess) return;
        
        console.log('========== 广告奖励回调 ==========');
        console.log('结果:', result);
        
        currentAdSuccess = true;
        if (slotTimeoutId) clearTimeout(slotTimeoutId);
        
        const ecpm = result.ecpm || 0;
        const currentSlotId = config.slotIds[(currentSlotIndex - 1 + config.slotIds.length) % config.slotIds.length];
        
        isAdLoading.value = false;
        isAdReady.value = false;
        
        console.log('✅ 广告成功，返回 ECPM:', ecpm, '广告位ID:', currentSlotId);
        cleanupListeners();
        currentResolve({ ecpm, slotId: currentSlotId });
        
        currentResolve = null;
        currentReject = null;
        isProcessing = false;
        resolveOnce('success');
      };
      
      const onAdFailed = (error: any) => {
        if (!checkSession() || currentAdSuccess || isResolved) return;
        
        console.warn('⚠️ 广告加载失败:', error?.error || error);
        lastError.value = '广告加载失败: ' + (error?.error || error || '未知错误');
        
        if (slotTimeoutId) clearTimeout(slotTimeoutId);
        cleanupListeners();
        resolveOnce('failed');
      };

      const onVideoDownloadSuccess = async () => {
        if (!checkSession() || currentAdSuccess || isResolved) return;
        
        console.log('✅ 视频下载成功，准备显示广告');
        try {
          if (slotTimeoutId) {
            clearTimeout(slotTimeoutId);
            console.log('✅ 清除单层超时定时器');
          }
          
          isAdReady.value = true;
          isAdLoading.value = false;
          
          // 检查广告是否就绪（未过期且缓存成功）
          console.log('🔍 检查广告就绪状态...');
          try {
            const readyStatus = await BaiduAd.isReady();
            console.log('📊 广告就绪状态:', readyStatus);
            
            if (!readyStatus.ready) {
              console.warn('⚠️ 广告未就绪（可能已过期或未缓存完成）');
              // 即使isReady返回false，也尝试显示广告，因为广告可能已经加载成功
              console.log('🔄 尝试强制显示广告...');
            }
          } catch (error) {
            console.warn('⚠️ 检查广告就绪状态失败:', error);
            // 检查失败时也尝试显示广告
          }
          
          console.log('✅ 广告位加载成功且已就绪，准备播放');
          await BaiduAd.showRewardVideoAd();
          console.log('✅ 广告显示命令已发送');
        } catch (error) {
          console.error('❌ 显示广告失败:', error);
          lastError.value = '显示广告失败: ' + (error?.message || error);
          cleanupListeners();
          resolveOnce('failed');
        }
      };

      const onVideoDownloadFailed = () => {
        if (!checkSession() || currentAdSuccess || isResolved) return;
        
        console.warn('⚠️ 视频下载失败');
        lastError.value = '视频下载失败，可能是广告填充不足';
        
        if (slotTimeoutId) clearTimeout(slotTimeoutId);
        cleanupListeners();
        resolveOnce('failed');
      };
      
      const onAdClose = () => {
        if (!checkSession()) return;
        
        console.log('✅ 广告关闭回调');
        if (slotTimeoutId) clearTimeout(slotTimeoutId);
        isAdReady.value = false;
        isAdLoading.value = false;
        cleanupListeners();
        
        // 如果广告未成功（用户跳过或未获得奖励），标记为失败
        if (!currentAdSuccess) {
          console.log('广告关闭但未获得奖励，标记为失败');
          resolveOnce('failed');
        }
      };
      
      adLoadedListener = onAdLoaded;
      rewardVerifyListener = onRewardVerify;
      adFailedListener = onAdFailed;
      videoDownloadSuccessListener = onVideoDownloadSuccess;
      videoDownloadFailedListener = onVideoDownloadFailed;
      adCloseListener = onAdClose;
      
      BaiduAd.addListener('onAdLoaded', onAdLoaded);
      BaiduAd.addListener('onRewardVerify', onRewardVerify);
      BaiduAd.addListener('onAdFailed', onAdFailed);
      BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
      BaiduAd.addListener('onVideoDownloadFailed', onVideoDownloadFailed);
      BaiduAd.addListener('onAdClose', onAdClose);
      
      BaiduAd.loadRewardVideoAd({ adId: selectedSlotId })
        .then(() => console.log('✅ 广告加载请求已发送'))
        .catch((err: any) => {
          console.error('❌ 加载广告请求失败:', err);
          if (!isResolved) {
            cleanupListeners();
            resolveOnce('failed');
          }
        });
      
      // 单层超时
      const SLOT_TIMEOUT = 3000;
      slotTimeoutId = setTimeout(() => {
        if (!checkSession() || currentAdSuccess || isResolved) return;
        
        console.warn(`⏱️ 单层广告加载超时（${SLOT_TIMEOUT}ms）`);
        cleanupListeners();
        resolveOnce('failed');
      }, SLOT_TIMEOUT);
    });
  };
  
  const showNativeAd = async (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
    const sessionId = currentSessionId;
    const checkSession = () => sessionId === currentSessionId;
    
    // 前4组并行请求已注释，改为全部串行
    // // 1. 第一组并行请求
    // let result = await tryParallelAdGroup(AD_GROUPS.group1);
    // if (result && checkSession()) {
    //   isAdLoading.value = false;
    //   isAdReady.value = false;
    //   isProcessing = false;
    //   resolve(result);
    //   return;
    // }
    
    // // 组间延迟
    // if (checkSession()) {
    //   console.log(`等待 ${GROUP_DELAY}ms 后尝试下一组...`);
    //   await delay(GROUP_DELAY);
    // }
    
    // // 2. 第二组并行请求
    // result = await tryParallelAdGroup(AD_GROUPS.group2);
    // if (result && checkSession()) {
    //   isAdLoading.value = false;
    //   isAdReady.value = false;
    //   isProcessing = false;
    //   resolve(result);
    //   return;
    // }
    
    // // 组间延迟
    // if (checkSession()) {
    //   console.log(`等待 ${GROUP_DELAY}ms 后尝试下一组...`);
    //   await delay(GROUP_DELAY);
    // }
    
    // // 3. 第三组并行请求
    // result = await tryParallelAdGroup(AD_GROUPS.group3);
    // if (result && checkSession()) {
    //   isAdLoading.value = false;
    //   isAdReady.value = false;
    //   isProcessing = false;
    //   resolve(result);
    //   return;
    // }
    
    // // 组间延迟
    // if (checkSession()) {
    //   console.log(`等待 ${GROUP_DELAY}ms 后尝试下一组...`);
    //   await delay(GROUP_DELAY);
    // }
    
    // // 4. 第四组并行请求
    // result = await tryParallelAdGroup(AD_GROUPS.group4);
    // if (result && checkSession()) {
    //   isAdLoading.value = false;
    //   isAdReady.value = false;
    //   isProcessing = false;
    //   resolve(result);
    //   return;
    // }
    
    // // 组间延迟
    // if (checkSession()) {
    //   console.log(`等待 ${GROUP_DELAY}ms 后尝试下一组...`);
    //   await delay(GROUP_DELAY);
    // }
    
    // 全部串行请求（使用智能瀑布流）
    let result = await executeSmartWaterfall();
    if (result && checkSession()) {
      isAdLoading.value = false;
      isAdReady.value = false;
      isProcessing = false;
      resolve(result);
      return;
    }
    
    // 所有广告位尝试失败
    isAdLoading.value = false;
    isAdReady.value = false;
    isProcessing = false;
    showNoAdAvailable(reject);
  };

  const showH5Ad = (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
    isAdLoading.value = true;

    try {
      const selectedSlotId = getNextSlotId();
      console.log('选择的H5广告位:', selectedSlotId);
      
      const rewardVideoAd = window.baidu.mobads.RewardVideoAd({
        slotId: selectedSlotId,
        appId: config.appId,
        onAdLoaded: async () => {
          console.log('H5 广告加载成功');
          isAdReady.value = true;
          isAdLoading.value = false;
          
          // 检查广告是否就绪（未过期且缓存成功）
          console.log('🔍 检查 H5 广告就绪状态...');
          const isAdReadyToShow = rewardVideoAd.isReady ? rewardVideoAd.isReady() : true;
          console.log('📊 H5 广告就绪状态:', isAdReadyToShow);
          
          if (!isAdReadyToShow) {
            console.warn('⚠️ H5 广告未就绪（可能已过期或未缓存完成）');
            isAdReady.value = false;
            isProcessing = false;
            showNoAdAvailable(reject);
            return;
          }
          
          console.log('✅ H5 广告已就绪，准备播放');
          rewardVideoAd.show();
        },
        onAdFailed: (error: any) => {
          console.error('H5 广告加载失败:', error);
          isAdReady.value = false;
          isAdLoading.value = false;
          isProcessing = false;
          showNoAdAvailable(reject);
        },
        onAdShow: () => console.log('H5 广告开始播放'),
        onAdClose: () => {
          console.log('H5 广告关闭');
          isProcessing = false;
        },
        onAdReward: (reward: any) => {
          console.log('获得 H5 广告奖励:', reward);
          const ecpm = reward?.ecpm || reward?.amount || 0;
          
          isAdReady.value = false;
          isProcessing = false;
          if (ecpm > 0) {
            resolve({ ecpm, slotId: selectedSlotId });
          } else {
            showNoAdAvailable(reject);
          }
        },
        onAdClick: () => console.log('用户点击了 H5 广告')
      });

      rewardVideoAd.load();
    } catch (error) {
      console.error('H5 广告初始化失败:', error);
      isAdReady.value = false;
      isAdLoading.value = false;
      isProcessing = false;
      showNoAdAvailable(reject);
    }
  };

  const showNoAdAvailable = (reject: (reason?: any) => void) => {
    console.log('⚠️ 所有广告位都已尝试，暂无合适广告');
    lastError.value = '暂无合适广告匹配，请稍后重试';
    isAdLoading.value = false;
    isAdReady.value = false;
    isProcessing = false;
    currentResolve = null;
    currentReject = null;
    cleanupListeners();
    reject(new Error('暂无合适广告匹配'));
  };

  return {
    isLoaded,
    isAdSdkReady,
    isAdLoading,
    isAdReady,
    lastError,
    preloadAd,
    showRewardVideo: showAd,
    initializeAdSdk,
    preloadNextAd,
    triggerPreloadAfterDelay,
    executeSmartWaterfall
  };
}
