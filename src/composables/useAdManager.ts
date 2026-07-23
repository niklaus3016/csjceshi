import { ref, onMounted, onUnmounted } from 'vue';
import CsjAd from '../plugins/BaiduAdPlugin';

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
  let timeoutId: any = null;
  let retryTimeoutId: any = null;
  let currentResolve: any = null;
  let currentReject: any = null;
  let currentSlotIndex = 0;
  let triedSlots = 0;
  let slotTimeoutId: any = null;
  let currentSessionId = 0;
  let isProcessing = false;
  const MAX_RETRY_ROUNDS = 1;
  const SLOT_TIMEOUT = 3000;
  
  const generateSimulatedEcpm = (slotId: string): number => {
    const ecpmRanges: { [key: string]: [number, number] } = {
      '985678631': [190, 200],
      '985678626': [90, 100],
      '985678630': [20, 30]
    };
    
    const range = ecpmRanges[slotId];
    if (!range) return 0;
    return Math.floor(Math.random() * (range[1] - range[0] + 1)) + range[0];
  };
  
  const getSlotTimeout = (slotId: string): number => {
    const ecpmRanges: { [key: string]: [number, number] } = {
      '985678631': [190, 200],
      '985678626': [90, 100],
      '985678630': [20, 30]
    };
    
    const range = ecpmRanges[slotId];
    if (!range) return 1500;
    
    const maxEcpm = range[1];
    if (maxEcpm >= 1000) {
      return 3000;
    } else if (maxEcpm >= 500) {
      return 3000;
    } else if (maxEcpm >= 200) {
      return 2000;
    } else {
      return 1500;
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
  
  const resetAdState = () => {
    currentSlotIndex = 0;
    triedSlots = 0;
    isAdLoading.value = false;
    isAdReady.value = false;
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
      { name: 'onAdClose', handler: adCloseListener }
    ];
    
    listeners.forEach(({ name, handler }) => {
      if (handler) {
        try {
          CsjAd.removeListener(name, handler);
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
        isAdSdkReady.value = true;
        isLoaded.value = true;
        preloadAd.value = true;
        return;
      }

      console.log('Web 环境，使用模拟广告');
      isAdSdkReady.value = true;
      isLoaded.value = true;
      preloadAd.value = true;
    } catch (error) {
      console.error('初始化广告 SDK 失败:', error);
      isLoaded.value = true;
      isAdSdkReady.value = false;
      preloadAd.value = true;
    }
  };

  const showRewardVideo = async (): Promise<{ ecpm: number; slotId: string }> => {
    return new Promise(async (resolve, reject) => {
      if (isProcessing) {
        console.log('⚠️ 已有广告正在处理，请等待');
        reject(new Error('已有广告正在处理'));
        return;
      }
      
      isProcessing = true;
      resetAdState();
      currentResolve = resolve;
      currentReject = reject;
      
      console.log('========== 开始加载激励视频广告 ==========');
      console.log('所有广告位:', config.slotIds);
      console.log('是否原生环境:', isNativeApp());
      
      try {
        if (isNativeApp()) {
          console.log('使用穿山甲原生广告插件');
          await showNativeAd(resolve, reject);
        } else {
          console.warn('非原生环境，使用模拟广告');
          await showSimulatedAd(resolve, reject);
        }
      } catch (error) {
        console.error('显示广告失败:', error);
        showNoAdAvailable(reject);
      }
    });
  };

  const showNativeAd = async (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
    const sessionId = currentSessionId;
    let currentAdSuccess = false;
    
    const checkSession = () => sessionId === currentSessionId;
    
    const tryLoadAd = async (): Promise<'success' | 'failed' | 'session_expired'> => {
      if (!checkSession()) {
        console.log('会话已过期，停止加载');
        return 'session_expired';
      }
      
      const maxSlots = config.slotIds.length * MAX_RETRY_ROUNDS;
      if (triedSlots >= maxSlots) {
        console.log(`所有${MAX_RETRY_ROUNDS}轮广告位都已尝试`);
        return 'failed';
      }
      
      cleanupListeners();
      
      const selectedSlotId = getNextSlotId();
      console.log(`尝试加载广告位: ${selectedSlotId}`);
      
      return new Promise((resolveLoad) => {
        let isResolved = false;
        
        const resolveOnce = (result: 'success' | 'failed') => {
          if (!isResolved) {
            isResolved = true;
            resolveLoad(result);
          }
        };
        
        const onAdLoaded = () => {
          if (!checkSession()) return;
          console.log('✅ 广告加载成功回调');
        };

        const onRewardVerify = (result: any) => {
          if (!checkSession() || currentAdSuccess) return;
          
          console.log('========== 广告奖励回调 ==========');
          console.log('结果:', result);
          
          currentAdSuccess = true;
          if (slotTimeoutId) clearTimeout(slotTimeoutId);
          
          let ecpm = result.ecpm || 0;
          const currentSlotId = config.slotIds[(currentSlotIndex - 1 + config.slotIds.length) % config.slotIds.length];
          
          if (ecpm === 0) {
            console.log('穿山甲广告 ECPM 为 0，生成模拟 ECPM');
            ecpm = generateSimulatedEcpm(currentSlotId);
          }
          
          isAdLoading.value = false;
          isAdReady.value = false;
          
          console.log('✅ 广告成功，返回 ECPM:', ecpm, '广告位ID:', currentSlotId);
          cleanupListeners();
          resolve({ ecpm, slotId: currentSlotId });
          
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
            
            isAdLoading.value = false;
            
            console.log('🔍 检查广告就绪状态...');
            try {
              let readyStatus = await CsjAd.isReady();
              console.log('📊 广告就绪状态:', readyStatus);
              
              if (!readyStatus.ready) {
                console.warn('⚠️ 广告未就绪（可能未缓存完成），等待200ms后重试...');
                await new Promise(resolve => setTimeout(resolve, 200));
                readyStatus = await CsjAd.isReady();
                console.log('📊 广告就绪状态（重试）:', readyStatus);
              }
              
              if (!readyStatus.ready) {
                console.warn('⚠️ 广告仍然未就绪，无法显示');
                lastError.value = '广告未就绪，请重新尝试';
                cleanupListeners();
                resolveOnce('failed');
                return;
              }
            } catch (error) {
              console.warn('⚠️ 检查广告就绪状态失败:', error);
            }
            
            isAdReady.value = true;
            console.log('✅ 广告位加载成功且已就绪，准备播放');
            await CsjAd.showRewardVideoAd();
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
        
        CsjAd.addListener('onAdLoaded', onAdLoaded);
        CsjAd.addListener('onRewardVerify', onRewardVerify);
        CsjAd.addListener('onAdFailed', onAdFailed);
        CsjAd.addListener('onVideoDownloadSuccess', onVideoDownloadSuccess);
        CsjAd.addListener('onVideoDownloadFailed', onVideoDownloadFailed);
        CsjAd.addListener('onAdClose', onAdClose);
        
        CsjAd.loadRewardVideoAd({ adId: selectedSlotId })
          .then(() => console.log('✅ 广告加载请求已发送'))
          .catch((err: any) => {
            console.error('❌ 加载广告请求失败:', err);
            if (!isResolved) {
              cleanupListeners();
              resolveOnce('failed');
            }
          });
        
        const timeoutDuration = getSlotTimeout(selectedSlotId);
        slotTimeoutId = setTimeout(() => {
          if (!checkSession() || currentAdSuccess || isResolved) return;
          
          console.warn(`⏱️ 单层广告加载超时（${timeoutDuration}ms）`);
          cleanupListeners();
          resolveOnce('failed');
        }, timeoutDuration);
      });
    };
    
    isAdLoading.value = true;
    
    while (true) {
      const result = await tryLoadAd();
      
      if (result === 'success') {
        return;
      }
      
      if (result === 'session_expired') {
        console.log('会话已过期，停止轮询');
        isAdLoading.value = false;
        isAdReady.value = false;
        isProcessing = false;
        return;
      }
      
      const maxSlots = config.slotIds.length * MAX_RETRY_ROUNDS;
      if (triedSlots >= maxSlots) {
        console.log(`所有${MAX_RETRY_ROUNDS}轮广告位都已尝试，暂无合适广告`);
        isAdLoading.value = false;
        isAdReady.value = false;
        isProcessing = false;
        showNoAdAvailable(reject);
        return;
      }
      
      console.log('当前广告位失败，尝试下一个...');
    }
  };

  const showSimulatedAd = async (resolve: (value: { ecpm: number; slotId: string }) => void, reject: (reason?: any) => void) => {
    isAdLoading.value = true;
    
    await new Promise(resolveTimeout => setTimeout(resolveTimeout, 1500));
    
    const selectedSlotId = getNextSlotId();
    const ecpm = generateSimulatedEcpm(selectedSlotId);
    
    isAdLoading.value = false;
    isProcessing = false;
    
    if (ecpm > 0) {
      resolve({ ecpm, slotId: selectedSlotId });
    } else {
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
    showRewardVideo,
    initializeAdSdk
  };
}