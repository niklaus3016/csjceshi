import { ref } from 'vue';
import { type PluginListenerHandle, Capacitor } from '@capacitor/core';
import CsjAd from '../plugins/CsjAdPlugin';

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
  let csjDebugLogHandle: PluginListenerHandle | null = null;
  let isProcessing = false;
  let lastEcpm = 0;
  let preloadedSlotId: string | null = null;
  let preloadListenerHandles: PluginListenerHandle[] = [];

  const delay = (ms: number): Promise<void> => {
    return new Promise(resolve => setTimeout(resolve, ms));
  };

  const isNativeApp = (): boolean => {
    return Capacitor.isNativePlatform() && typeof CsjAd !== 'undefined' && CsjAd && typeof CsjAd.loadRewardVideoAd === 'function';
  };

  const getUserId = (): string => {
    const employeeId = localStorage.getItem('employeeId') || '';
    return localStorage.getItem('userId') || ('user_' + employeeId + '_' + Date.now());
  };

  const getDeviceId = (): string => {
    let deviceId = localStorage.getItem('deviceId');
    if (!deviceId) {
      deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).slice(2, 11);
      localStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
  };

  const getExtraData = (slotId: string): string => {
    return JSON.stringify({
      employeeId: localStorage.getItem('employeeId') || '',
      deviceId: getDeviceId(),
      slotId: slotId,
      appId: config.appId
    });
  };

  const initializeAdSdk = async () => {
    if (typeof window === 'undefined') return;

    try {
      if (isNativeApp()) {
        console.log('原生 Android 环境，使用穿山甲原生 SDK');
        
        try {
          const sdkReady = await CsjAd.isSdkReady();
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
        
        const csjDebugLogListener = (data: any) => {
          if (!data) return;
          const tag = data.tag || 'UNKNOWN';
          const message = data.message || '';
          
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
        
        try {
          csjDebugLogHandle = await CsjAd.addListener('onCsjDebugLog', csjDebugLogListener);
          console.log('🔍 CsjAd调试日志监听器已注册');
        } catch (e) {
          console.warn('注册CsjAd调试日志监听器失败:', e);
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

  const cleanupDebugLogListener = () => {
    if (csjDebugLogHandle) {
      try {
        csjDebugLogHandle.remove();
        console.log('🔍 CsjAd调试日志监听器已清理');
        csjDebugLogHandle = null;
      } catch (e) {
        console.warn('清理调试日志监听器失败:', e);
      }
    }
  };

  const cleanupPreloadListeners = () => {
    for (const handle of preloadListenerHandles) {
      try {
        handle.remove();
      } catch (e) {
        console.warn('清理预缓存监听器失败:', e);
      }
    }
    preloadListenerHandles.length = 0;
  };

  const preloadRewardVideoAd = async (): Promise<void> => {
    if (!isNativeApp()) {
      console.log('非原生环境，跳过预缓存');
      return;
    }
    
    console.log('🚀 开始冷启动预缓存广告...');
    
    cleanupPreloadListeners();
    
    const slotId = config.slotIds[0];
    const userId = getUserId();
    const extraData = getExtraData(slotId);
    
    const onAdLoaded = (data: any) => {
      console.log(`✅ 预缓存 - 广告位 ${slotId} 加载成功`);
      preloadedSlotId = slotId;
      isAdReady.value = true;
    };
    
    const onVideoDownloadSuccess = (data: any) => {
      console.log(`✅ 预缓存 - 广告位 ${slotId} 缓存成功`);
      preloadedSlotId = slotId;
      isAdReady.value = true;
    };
    
    const onAdFailed = (error: any) => {
      const code = error?.code || 'unknown';
      const msg = error?.error || error?.message || '未知错误';
      console.log(`❌ 预缓存 - 广告位 ${slotId} 加载失败 [code=${code}]: ${msg}`);
      console.log('   完整错误详情:', error);
      preloadedSlotId = null;
      isAdReady.value = false;
    };
    
    CsjAd.addListener('onAdLoaded', onAdLoaded).then(handle => preloadListenerHandles.push(handle)).catch(console.warn);
    CsjAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess).then(handle => preloadListenerHandles.push(handle)).catch(console.warn);
    CsjAd.addListener('onAdFailed', onAdFailed).then(handle => preloadListenerHandles.push(handle)).catch(console.warn);
    
    try {
      await CsjAd.loadRewardVideoAd({
        adId: slotId,
        userId: userId,
        extraData: extraData
      });
      console.log('✅ 预缓存 - 加载命令已发送');
    } catch (error) {
      console.error('❌ 预缓存 - 加载命令发送失败:', error);
      cleanupPreloadListeners();
    }
  };

  const trySingleAdSlot = async (slotId: string): Promise<{ ecpm: number; slotId: string } | null> => {
    return new Promise((resolve, reject) => {
      let isResolved = false;
      let extremeTimeoutId: any = null;
      const listenerHandles: PluginListenerHandle[] = [];
      
      const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
        if (!isResolved) {
          isResolved = true;
          if (extremeTimeoutId) clearTimeout(extremeTimeoutId);
          cleanupListeners();
          resolve(result);
        }
      };
      
      const rejectWithError = (error: Error) => {
        if (!isResolved) {
          isResolved = true;
          if (extremeTimeoutId) clearTimeout(extremeTimeoutId);
          cleanupListeners();
          reject(error);
        }
      };
      
      extremeTimeoutId = setTimeout(() => {
        if (!isResolved) {
          console.log(`⏱️ 广告极端超时（5分钟），强制清理监听器 (${slotId})`);
          resolveOnce(null);
        }
      }, 5 * 60 * 1000);
      
      const onRewardVerify = (result: any) => {
        if (isResolved) return;
        
        const ecpm = result.ecpm || lastEcpm || 0;
        
        resolveOnce({ ecpm, slotId });
      };
      
      const onAdFailed = (error: any) => {
        if (isResolved) return;
        const code = error?.code || 'unknown';
        const msg = error?.error || error?.message || '未知错误';
        console.log(`❌ 广告位 ${slotId} 加载失败 [code=${code}]: ${msg}`);
        console.log('   完整错误详情:', error);
        resolveOnce(null);
      };
      
      const onAdClose = () => {
        if (isResolved) return;
        console.log(`⏳ 广告关闭，等待奖励回调...`);
        setTimeout(() => {
          if (!isResolved) {
            console.log(`❌ 广告关闭后5秒未收到奖励回调，用户中途返回`);
            rejectWithError(new Error('用户中途返回'));
          }
        }, 5000);
      };
      
      const onAdLoaded = (data: any) => {
        if (isResolved) return;
        console.log(`✅ 广告位 ${slotId} 加载成功`);
      };
      
      const onVideoDownloadSuccess = (data: any) => {
        if (isResolved) return;
        console.log(`✅ 广告位 ${slotId} 缓存成功，准备显示`);
        try {
          CsjAd.showRewardVideoAd();
        } catch (e) {
          console.error('❌ 显示广告失败:', e);
          resolveOnce(null);
        }
      };
      
      const onAdShow = () => {
        console.log('📺 广告开始展示');
      };
      
      const cleanupListeners = () => {
        for (const handle of listenerHandles) {
          try {
            handle.remove();
          } catch (e) {
            console.warn(`清理监听器失败 (${slotId}):`, e);
          }
        }
        listenerHandles.length = 0;
      };
      
      CsjAd.addListener('onRewardVerify', onRewardVerify).then(handle => listenerHandles.push(handle)).catch(console.warn);
      CsjAd.addListener('onAdFailed', onAdFailed).then(handle => listenerHandles.push(handle)).catch(console.warn);
      CsjAd.addListener('onAdClose', onAdClose).then(handle => listenerHandles.push(handle)).catch(console.warn);
      CsjAd.addListener('onAdLoaded', onAdLoaded).then(handle => listenerHandles.push(handle)).catch(console.warn);
      CsjAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess).then(handle => listenerHandles.push(handle)).catch(console.warn);
      CsjAd.addListener('onAdShow', onAdShow).then(handle => listenerHandles.push(handle)).catch(console.warn);
      
      const userId = getUserId();
      const extraData = getExtraData(slotId);
      
      CsjAd.loadRewardVideoAd({ 
        adId: slotId,
        userId: userId,
        extraData: extraData
      }).catch((error: any) => {
        if (!isResolved) {
          isResolved = true;
          console.log(`❌ 广告位 ${slotId} 请求失败:`, error);
          cleanupListeners();
          resolve(null);
        }
      });
    });
  };

  const showPreloadedAd = async (): Promise<{ ecpm: number; slotId: string } | null> => {
    return new Promise((resolve, reject) => {
      let isResolved = false;
      let extremeTimeoutId: any = null;
      const listenerHandles: PluginListenerHandle[] = [];
      
      const resolveOnce = (result: { ecpm: number; slotId: string } | null) => {
        if (!isResolved) {
          isResolved = true;
          if (extremeTimeoutId) clearTimeout(extremeTimeoutId);
          cleanupListeners();
          resolve(result);
        }
      };
      
      const rejectWithError = (error: Error) => {
        if (!isResolved) {
          isResolved = true;
          if (extremeTimeoutId) clearTimeout(extremeTimeoutId);
          cleanupListeners();
          reject(error);
        }
      };
      
      extremeTimeoutId = setTimeout(() => {
        if (!isResolved) {
          console.log(`⏱️ 预缓存广告极端超时（5分钟）`);
          resolveOnce(null);
        }
      }, 5 * 60 * 1000);
      
      const onRewardVerify = (result: any) => {
        if (isResolved) return;
        
        const ecpm = result.ecpm || lastEcpm || 0;
        
        resolveOnce({ ecpm, slotId: preloadedSlotId! });
      };
      
      const onAdClose = () => {
        if (isResolved) return;
        console.log(`⏳ 预缓存广告关闭，等待奖励回调...`);
        setTimeout(() => {
          if (!isResolved) {
            console.log(`❌ 预缓存广告关闭后5秒未收到奖励回调，用户中途返回`);
            rejectWithError(new Error('用户中途返回'));
          }
        }, 5000);
      };
      
      const onAdShow = () => {
        console.log('📺 预缓存广告开始展示');
      };
      
      const cleanupListeners = () => {
        for (const handle of listenerHandles) {
          try {
            handle.remove();
          } catch (e) {
            console.warn(`清理预缓存广告监听器失败:`, e);
          }
        }
        listenerHandles.length = 0;
        preloadedSlotId = null;
        isAdReady.value = false;
        cleanupPreloadListeners();
      };
      
      CsjAd.addListener('onRewardVerify', onRewardVerify).then(handle => listenerHandles.push(handle)).catch(console.warn);
      CsjAd.addListener('onAdClose', onAdClose).then(handle => listenerHandles.push(handle)).catch(console.warn);
      CsjAd.addListener('onAdShow', onAdShow).then(handle => listenerHandles.push(handle)).catch(console.warn);
      
      try {
        CsjAd.showRewardVideoAd();
        console.log('✅ 开始展示预缓存广告');
      } catch (error) {
        console.error('❌ 显示预缓存广告失败:', error);
        cleanupListeners();
        resolveOnce(null);
      }
    });
  };

  const showAd = async (): Promise<{ ecpm: number; slotId: string }> => {
    return new Promise(async (resolve, reject) => {
      if (isProcessing) {
        console.log('⚠️ 已有广告正在处理，请等待');
        reject(new Error('已有广告正在处理'));
        return;
      }
      
      isProcessing = true;
      
      console.log('========== 开始加载激励视频广告 ==========');
      console.log('所有广告位:', config.slotIds);
      console.log('是否原生环境:', isNativeApp());
      console.log('是否有预缓存广告:', !!preloadedSlotId);
      
      if (preloadedSlotId && isAdReady.value) {
        console.log(`🎯 使用预缓存广告: ${preloadedSlotId}`);
        try {
          const result = await showPreloadedAd();
          if (result) {
            console.log(`✅ 预缓存广告成功，eCPM: ${result.ecpm}`);
            isProcessing = false;
            resolve(result);
            return;
          }
        } catch (error) {
          console.log(`❌ 预缓存广告失败:`, error);
        }
      }
      
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
          console.log(`❌ 广告位 ${slotId} 失败（用户中途返回或其他原因）:`, error);
          isProcessing = false;
          reject(error);
          return;
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

  return {
    isLoaded,
    isAdSdkReady,
    isAdLoading,
    isAdReady,
    lastError,
    preloadAd,
    showRewardVideo: showAd,
    initializeAdSdk,
    preloadRewardVideoAd,
    cleanupDebugLogListener
  };
}