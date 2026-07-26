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
  let csjDebugLogListener: any = null;
  let isProcessing = false;
  let lastEcpm = 0;
  let slotTimeoutId: any = null;

  const delay = (ms: number): Promise<void> => {
    return new Promise(resolve => setTimeout(resolve, ms));
  };

  const isNativeApp = (): boolean => {
    return typeof window !== 'undefined' && (window.baidu || window._baidu) && typeof BaiduAd !== 'undefined' && BaiduAd && typeof BaiduAd.loadRewardVideoAd === 'function';
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

  const trySingleAdSlot = async (slotId: string): Promise<{ ecpm: number; slotId: string } | null> => {
    return new Promise((resolve, reject) => {
      let isResolved = false;
      let extremeTimeoutId: any = null;
      
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
        console.log(`❌ 广告位 ${slotId} 加载失败:`, error);
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
        console.log('📺 广告开始展示');
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
    initializeAdSdk
  };
}
