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
  let timeoutId: any = null;
  let retryTimeoutId: any = null;
  let slotTimeoutId: any = null;
  let currentResolve: any = null;
  let currentReject: any = null;
  let currentSlotIndex = 0;
  let triedSlots = 0;
  let isProcessing = false;
  let hasShownAd = false;
  let lastEcpm = 0;
  
  let preloadedAd: {
    slotId: string;
    isReady: boolean;
    loadedAt: number;
  } | null = null;
  let isPreloading = false;
  let preloadingPromise: Promise<void> | null = null;
  
  const AD_GROUPS = {
    A: ['104282400']
  };
  
  const GROUP_ORDER = ['A'];
  
  const SCHEDULER_STATE_KEY = 'ad_scheduler_state';
  
  const MAX_EXPOSES_WITHOUT_FLOAT = 30;
  const EXPIRY_HOURS = 24;
  
  const PARALLEL_TIMEOUT = 60000;
  const PRELOAD_TOTAL_TIMEOUT = 120000;
  const GROUP_DELAY = 500;
  const GROUP5_SLOT_DELAY = 200;
  const REWARD_WAIT_TIMEOUT = 3000;
  
  const delay = (ms: number): Promise<void> => {
    return new Promise(resolve => setTimeout(resolve, ms));
  };
  
  interface SchedulerState {
    start_group: string;
    hit_streak: number;
    expose_count: number;
    last_expose_time: number;
  }
  
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
  
  const saveSchedulerState = (state: SchedulerState): void => {
    try {
      localStorage.setItem(SCHEDULER_STATE_KEY, JSON.stringify(state));
    } catch (e) {
      console.error('保存调度器状态失败:', e);
    }
  };
  
  const updateSchedulerStateOnHit = (state: SchedulerState, hitGroup: string | null): void => {
    const startIndex = GROUP_ORDER.indexOf(state.start_group);
    const lastIndex = GROUP_ORDER.length - 1;

    if (hitGroup) {
      const hitIndex = GROUP_ORDER.indexOf(hitGroup);

      if (hitGroup === state.start_group) {
        state.hit_streak += 1;
        console.log(`📈 连续命中起始分组计数: ${state.hit_streak}`);

        if (state.hit_streak >= 2 && startIndex > 0) {
          state.start_group = GROUP_ORDER[startIndex - 1];
          state.hit_streak = 0;
          console.log(`⬆️ 连续命中2次，上浮到分组 ${state.start_group}`);
        }
      } else if (hitIndex < startIndex) {
        state.start_group = hitGroup;
        state.hit_streak = 0;
        console.log(`⬆️ 命中更高价值分组 ${hitGroup}，下次从 ${hitGroup} 开始`);
      } else {
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
  
  const checkAndCleanupExpiredState = (state: SchedulerState): boolean => {
    const now = Date.now();
    
    if (now - state.last_expose_time > EXPIRY_HOURS * 3600 * 1000) {
      console.log('⏰ 调度器状态过期（超过24小时），重置');
      state.start_group = 'A';
      state.hit_streak = 0;
      return true;
    }
    
    if (state.expose_count >= MAX_EXPOSES_WITHOUT_FLOAT) {
      console.log('⏰ 调度器状态过期（累计30次曝光），重置');
      state.start_group = 'A';
      state.hit_streak = 0;
      return true;
    }
    
    return false;
  };
  
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

  let globalSessionId = 0;
  let currentAdType: 'preload' | 'normal' | 'preloading' = 'normal';
  let currentAdSlotId = '';
  let currentAdSuccess = false;
  let rewardVerifyCallback: ((result: any) => void) | null = null;
  let adCloseCallback: (() => void) | null = null;
  let adShowCallback: ((data: any) => void) | null = null;
  let adFailedCallback: ((error: any) => void) | null = null;
  let videoDownloadSuccessCallback: (() => void) | null = null;
  let adLoadedCallback: (() => void) | null = null;

  const resetAdSession = (adType: 'preload' | 'normal' | 'preloading', slotId: string) => {
    globalSessionId++;
    currentAdType = adType;
    currentAdSlotId = slotId;
    currentAdSuccess = false;
    rewardVerifyCallback = null;
    adCloseCallback = null;
    adShowCallback = null;
    adFailedCallback = null;
    videoDownloadSuccessCallback = null;
    adLoadedCallback = null;
    console.log(`🆕 新广告会话开始，ID: ${globalSessionId}，类型: ${adType}，广告位: ${slotId}`);
  };

  const onRewardVerify = (result: any) => {
    console.log(`[CSJ][REWARD] 获得奖励: isRewardValid=${result.isRewardValid}, ECPM=${result.ecpm}`);
    
    if (result.ecpm) {
      lastEcpm = result.ecpm;
    }
    
    currentAdSuccess = true;
    
    if (rewardVerifyCallback) {
      rewardVerifyCallback(result);
    }
  };

  const onAdShow = (data: any) => {
    console.log(`📺 广告页面已打开 (${currentAdSlotId})`);
    
    if (data && data.ecpm) {
      lastEcpm = data.ecpm;
      console.log(`📺 广告页面已打开 (${currentAdSlotId})，ECPM=${data.ecpm}`);
    }
    
    if (adShowCallback) {
      adShowCallback(data);
    }
  };

  const onAdClose = () => {
    console.log(`⏳ 广告关闭，等待奖励回调... (${currentAdSlotId})`);
    
    if (adCloseCallback) {
      adCloseCallback();
    }
  };

  const onAdFailed = (error: any) => {
    console.log(`❌ 广告展示失败 (${currentAdSlotId}):`, error);
    
    if (adFailedCallback) {
      adFailedCallback(error);
    }
  };

  const onVideoDownloadSuccess = () => {
    console.log(`✅ 广告缓存成功 (${currentAdSlotId})`);
    
    if (videoDownloadSuccessCallback) {
      videoDownloadSuccessCallback();
    }
  };

  const onAdLoaded = () => {
    console.log(`✅ 广告加载成功 (${currentAdSlotId})`);
    
    if (adLoadedCallback) {
      adLoadedCallback();
    }
  };

  const onCsjDebugLog = (data: any) => {
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
    
    if (tag === 'ECPM') {
      const ecpmMatch = message.match(/ECPM=([\d.]+)/);
      if (ecpmMatch && ecpmMatch[1]) {
        lastEcpm = parseFloat(ecpmMatch[1]);
        console.log(`🔄 全局ECPM已更新: ${lastEcpm}`);
      }
    }
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
  
  const triggerPreloadAfterDelay = () => {
    setTimeout(() => {
      preloadNextAd();
    }, 1000);
  };
  
  const smartPreload = () => {
    if (preloadedAd) {
      console.log('📋 已有预加载广告，跳过预加载');
      return;
    }
    
    if (isPreloading && preloadingPromise) {
      console.log('⏳ 预加载进行中，跳过重复触发');
      return;
    }
    
    console.log('🚀 开始新的预加载任务');
    preloadNextAd();
  };

  const preloadSingleSlot = (slotId: string): Promise<boolean> => {
    return new Promise((resolve) => {
      let isResolved = false;
      
      resetAdSession('preloading', slotId);
      
      videoDownloadSuccessCallback = () => {
        if (!isResolved) {
          isResolved = true;
          console.log(`✅ 串行预加载成功: ${slotId}`);
          resolve(true);
        }
      };
      
      adFailedCallback = () => {
        if (!isResolved) {
          isResolved = true;
          console.log(`❌ 串行预加载失败: ${slotId}`);
          resolve(false);
        }
      };
      
      setTimeout(() => {
        if (!isResolved) {
          isResolved = true;
          console.log(`⏱️ 串行预加载超时: ${slotId}`);
          resolve(false);
        }
      }, PARALLEL_TIMEOUT);
      
      const employeeId = localStorage.getItem('employeeId') || '';
      const userId = localStorage.getItem('userId') || ('user_' + employeeId + '_' + Date.now());
      let deviceId = localStorage.getItem('deviceId');
      if (!deviceId) {
        deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('deviceId', deviceId);
      }
      
      BaiduAd.loadRewardVideoAd({ 
        adId: slotId,
        userId: userId,
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
          resolve(false);
        }
      });
    });
  };

  const preloadNextAd = async (): Promise<void> => {
    if (isPreloading && preloadingPromise) {
      return preloadingPromise;
    }
    
    if (preloadedAd) {
      console.log('📋 已有预加载广告，跳过预加载');
      return;
    }
    
    isPreloading = true;
    console.log('🚀 开始预加载任务（策略：智能瀑布流分组轮询）');
    
    preloadingPromise = (async () => {
      const totalStartTime = Date.now();
      let foundAd = false;
      let totalTimeoutId: any = null;
      let timeoutReached = false;
      
      totalTimeoutId = setTimeout(() => {
        console.log(`⏱️ 预加载总超时（${PRELOAD_TOTAL_TIMEOUT}ms），强制终止`);
        timeoutReached = true;
      }, PRELOAD_TOTAL_TIMEOUT);
      
      try {
        const schedulerState = loadSchedulerState();
        checkAndCleanupExpiredState(schedulerState);
        let startIndex = GROUP_ORDER.indexOf(schedulerState.start_group);
        if (startIndex === -1) startIndex = 0;
        
        console.log(`🎯 预加载起始分组: ${schedulerState.start_group}（索引: ${startIndex}）`);
        
        for (let i = startIndex; i < GROUP_ORDER.length; i++) {
          if (timeoutReached) break;
          
          const group = GROUP_ORDER[i];
          const slots = AD_GROUPS[group as keyof typeof AD_GROUPS];
          
          console.log(`🔍 预加载分组 ${group}，广告位：${slots.join(', ')}`);
          
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
            
            if (j < slots.length - 1) {
              await delay(500);
            }
          }
          
          if (foundAd) break;
          
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

  const showPreloadedAd = async (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
    if (!preloadedAd || !preloadedAd.isReady) {
      console.log('预加载广告未就绪，开始正常加载');
      reject(new Error('预加载广告未就绪'));
      return;
    }
    
    const slotId = preloadedAd.slotId;
    console.log(`🚀 使用预加载的广告位: ${slotId}`);
    
    preloadedAd = null;
    hasShownAd = true;
    
    resetAdSession('preload', slotId);
    
    let isResolved = false;
    let localEcpm = lastEcpm;
    
    const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
      if (!isResolved) {
        isResolved = true;
        if (result) {
          resolve(result);
        } else {
          reject(new Error('广告显示失败'));
        }
        setTimeout(() => {
          smartPreload();
        }, 500);
      }
    };
    
    rewardVerifyCallback = (result: any) => {
      if (isResolved) return;
      
      currentAdSuccess = true;
      
      const ecpm = result.ecpm || localEcpm || 0;
      
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
    
    adShowCallback = (data: any) => {
      if (data && data.ecpm) {
        localEcpm = data.ecpm;
      }
    };
    
    adCloseCallback = () => {
      setTimeout(() => {
        if (!currentAdSuccess && !isResolved) {
          console.log(`❌ 预加载广告关闭后未获得奖励 (${slotId})，标记为失败`);
          resolveOnce(null);
        }
      }, REWARD_WAIT_TIMEOUT);
    };
    
    adFailedCallback = (error: any) => {
      if (isResolved) return;
      console.log(`❌ 预加载广告展示失败 (${slotId}):`, error);
      resolveOnce(null);
    };
    
    try {
      await BaiduAd.showRewardVideoAd();
      console.log(`✅ 预加载广告显示命令已发送 (${slotId})`);
    } catch (error) {
      console.error(`❌ 显示预加载广告失败 (${slotId}):`, error);
      reject(new Error('广告显示失败'));
    }
  };

  const getUserId = (): string | null => {
    return localStorage.getItem('userId') || null;
  };

  const getEmployeeId = (): string | null => {
    return localStorage.getItem('empId') || null;
  };

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
    return new Promise((resolve) => {
      let isResolved = false;
      
      resetAdSession('normal', slotId);
      
      const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
        if (!isResolved) {
          isResolved = true;
          resolve(result);
          setTimeout(() => {
            smartPreload();
          }, 500);
        }
      };
      
      rewardVerifyCallback = (result: any) => {
        if (isResolved) return;
        
        const ecpm = result.ecpm || lastEcpm || 0;
        resolveOnce({ ecpm, slotId });
      };
      
      adFailedCallback = (error: any) => {
        if (isResolved) return;
        console.log(`❌ 广告位 ${slotId} 加载失败:`, error);
        resolveOnce(null);
      };
      
      adCloseCallback = () => {
        setTimeout(() => {
          if (!isResolved) {
            console.log(`❌ 广告关闭后未收到奖励回调，标记为失败`);
            resolveOnce(null);
          }
        }, REWARD_WAIT_TIMEOUT);
      };
      
      videoDownloadSuccessCallback = () => {
        if (isResolved) return;
        console.log(`✅ 广告位 ${slotId} 缓存成功，准备显示`);
        try {
          BaiduAd.showRewardVideoAd();
        } catch (e) {
          console.error('❌ 显示广告失败:', e);
          resolveOnce(null);
        }
      };
      
      const employeeId = localStorage.getItem('employeeId') || '';
      const userId = localStorage.getItem('userId') || ('user_' + employeeId + '_' + Date.now());
      let deviceId = localStorage.getItem('deviceId');
      if (!deviceId) {
        deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('deviceId', deviceId);
      }
      
      BaiduAd.loadRewardVideoAd({ 
        adId: slotId,
        userId: userId,
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

  const trySerialAdGroup = async (slotIds: string[], slotDelay: number = 0): Promise<{ ecpm: number; slotId: string } | null> => {
    console.log(`========== 开始串行请求广告组（共${slotIds.length}个广告位） ==========`);
    
    for (let i = 0; i < slotIds.length; i++) {
      const slotId = slotIds[i];
      const slotIndex = i + 1;
      const totalSlots = slotIds.length;
      
      if (hasShownAd) {
        console.log('🛑 已显示过广告，停止尝试其他广告位');
        return null;
      }
      
      if (i > 0 && slotDelay > 0) {
        console.log(`等待 ${slotDelay}ms 后尝试下一个广告位...`);
        await delay(slotDelay);
      }
      
      console.log(`尝试加载广告位 [${slotIndex}/${totalSlots}]: ${slotId}`);
      
      const result = await new Promise<{ ecpm: number; slotId: string } | null>((resolve) => {
        let isResolved = false;
        let slotTimeoutId: any = null;
        let currentAdSuccess = false;
        
        resetAdSession('normal', slotId);
        
        const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
          if (!isResolved) {
            isResolved = true;
            if (slotTimeoutId) clearTimeout(slotTimeoutId);
            resolve(result);
          }
        };
        
        rewardVerifyCallback = (result: any) => {
          if (currentAdSuccess || isResolved) return;
          
          currentAdSuccess = true;
          if (slotTimeoutId) clearTimeout(slotTimeoutId);
          
          const ecpm = result.ecpm || 0;
          
          console.log(`✅ 广告成功 (${slotId})，ECPM:`, ecpm);
          
          resolveOnce({ ecpm, slotId });
        };
        
        adFailedCallback = (error: any) => {
          if (currentAdSuccess || isResolved) return;
          console.warn(`⚠️ 广告加载失败 (${slotId}):`, error?.error || error);
          resolveOnce(null);
        };
        
        videoDownloadSuccessCallback = async () => {
          if (currentAdSuccess || isResolved) return;
          
          console.log(`✅ 视频下载成功 (${slotId})，准备显示广告`);
          try {
            if (slotTimeoutId) clearTimeout(slotTimeoutId);
            
            console.log(`✅ 广告位加载成功且已就绪 (${slotId})，准备播放`);
            await BaiduAd.showRewardVideoAd();
            console.log(`✅ 广告显示命令已发送 (${slotId})`);
          } catch (error) {
            console.error(`❌ 显示广告失败 (${slotId}):`, error);
            resolveOnce(null);
          }
        };
        
        adCloseCallback = () => {
          if (!currentAdSuccess) {
            console.log(`广告关闭但未获得奖励 (${slotId})，标记为失败`);
            setTimeout(() => {
              if (!currentAdSuccess && !isResolved) {
                resolveOnce(null);
              }
            }, REWARD_WAIT_TIMEOUT);
          }
        };
        
        const employeeId = localStorage.getItem('employeeId') || '';
        const userId = localStorage.getItem('userId') || ('user_' + employeeId + '_' + Date.now());
        let deviceId = localStorage.getItem('deviceId');
        if (!deviceId) {
          deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
          localStorage.setItem('deviceId', deviceId);
        }
        
        BaiduAd.loadRewardVideoAd({ 
          adId: slotId,
          userId: userId,
          extraData: JSON.stringify({ 
            employeeId: employeeId,
            deviceId: deviceId,
            timestamp: Date.now(),
            slotId: slotId,
            appId: config.appId
          })
        }).then(() => console.log(`✅ 广告加载请求已发送 (${slotId})`))
          .catch((err: any) => {
            console.error(`❌ 加载广告请求失败 (${slotId}):`, err);
            resolveOnce(null);
          });
        
        slotTimeoutId = setTimeout(() => {
          if (!currentAdSuccess || !isResolved) return;
          console.warn(`⏱️ 广告加载超时 (${slotId})`);
          resolveOnce(null);
        }, PARALLEL_TIMEOUT);
      });
      
      if (result) {
        console.log(`🎉 串行请求成功，使用广告位: ${result.slotId}，ECPM: ${result.ecpm}`);
        return result;
      }
      
      if (hasShownAd) {
        console.log('🛑 已显示过广告，停止尝试其他广告位');
        return null;
      }
      
      console.log(`广告位 ${slotId} 失败，尝试下一个...`);
    }
    
    console.log('❌ 串行请求组所有广告位均失败');
    return null;
  };

  const executeSmartWaterfall = async (): Promise<{ ecpm: number; slotId: string } | null> => {
    console.log('========== 智能瀑布流调度开始 ==========');
    
    const state = loadSchedulerState();
    console.log(`📊 当前状态: start_group=${state.start_group}, hit_streak=${state.hit_streak}, expose_count=${state.expose_count}`);
    
    checkAndCleanupExpiredState(state);
    
    state.expose_count += 1;
    state.last_expose_time = Date.now();
    const isFirstExpose = state.expose_count === 1;
    console.log(`🔢 曝光次数: ${state.expose_count}${isFirstExpose ? '（首次曝光，不计入调度）' : ''}`);
    
    let startIndex = GROUP_ORDER.indexOf(state.start_group);
    if (startIndex === -1) startIndex = 0;
    console.log(`🎯 起始分组: ${state.start_group}（索引: ${startIndex}）`);
    
    let hitGroup: string | null = null;
    let result: { ecpm: number; slotId: string } | null = null;
    
    for (let i = startIndex; i < GROUP_ORDER.length; i++) {
      const group = GROUP_ORDER[i];
      const slots = AD_GROUPS[group as keyof typeof AD_GROUPS];
      
      console.log(`🔍 遍历分组 ${group}，广告位：${slots.join(', ')}`);
      
      const groupResult = await trySerialAdGroup(slots);
      
      if (groupResult) {
        hitGroup = group;
        result = groupResult;
        console.log(`🎉 分组 ${group} 命中，广告位：${result.slotId}，eCPM：${result.ecpm}`);
        break;
      }
      
      console.log(`❌ 分组 ${group} 无广告，继续下一分组`);
      
      if (i < GROUP_ORDER.length - 1) {
        await delay(GROUP_DELAY);
      }
    }
    
    if (!isFirstExpose) {
      updateSchedulerStateOnHit(state, hitGroup);
    } else {
      console.log('⚠️ 首次曝光，不更新调度状态');
    }
    
    saveSchedulerState(state);
    
    console.log(`📋 智能瀑布流调度结束: 起始分组=${state.start_group}, 命中分组=${hitGroup}, 命中广告位=${result?.slotId || '无'}, eCPM=${result?.ecpm || 0}`);
    
    return result;
  };

  const resetAdState = () => {
    currentSlotIndex = 0;
    triedSlots = 0;
    isAdLoading.value = false;
    isAdReady.value = false;
    hasShownAd = false;
    globalSessionId++;
    console.log(`🆕 新会话开始，会话ID: ${globalSessionId}`);
  };

  const cleanupListeners = () => {
    console.log('🔄 清理广告监听器...');
    
    rewardVerifyCallback = null;
    adCloseCallback = null;
    adShowCallback = null;
    adFailedCallback = null;
    videoDownloadSuccessCallback = null;
    adLoadedCallback = null;
    
    [timeoutId, retryTimeoutId, slotTimeoutId].forEach(id => {
      if (id) {
        clearTimeout(id);
        id = null;
      }
    });
    
    console.log('✅ 监听器回调清理完成');
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
        
        try {
          BaiduAd.addListener('onCsjDebugLog', onCsjDebugLog);
          BaiduAd.addListener('onRewardVerify', onRewardVerify);
          BaiduAd.addListener('onAdShow', onAdShow);
          BaiduAd.addListener('onAdClose', onAdClose);
          BaiduAd.addListener('onAdFailed', onAdFailed);
          BaiduAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
          BaiduAd.addListener('onAdLoaded', onAdLoaded);
          console.log('🔍 全局广告监听器已注册');
        } catch (e) {
          console.warn('注册广告监听器失败:', e);
        }
        
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

  const showNativeAd = async (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
    let result = await executeSmartWaterfall();
    if (result) {
      isAdLoading.value = false;
      isAdReady.value = false;
      isProcessing = false;
      resolve(result);
      return;
    }
    
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
          
          const isAdReadyToShow = rewardVideoAd.isReady ? rewardVideoAd.isReady() : true;
          console.log('📊 H5 广告就绪状态:', isAdReadyToShow);
          
          if (!isAdReadyToShow) {
            console.warn('⚠️ H5 广告未就绪');
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

  onMounted(() => initializeAdSdk());
  onUnmounted(() => cleanupListeners());

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