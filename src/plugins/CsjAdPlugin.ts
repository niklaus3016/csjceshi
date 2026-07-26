import { registerPlugin, PluginListenerHandle } from '@capacitor/core';

export interface CsjAdPlugin {
  loadRewardVideoAd(options: { adId: string; userId?: string; extraData?: string }): Promise<void>;
  showRewardVideoAd(): Promise<void>;
  preloadRewardVideoAd(options: { adIds: string[]; userId?: string; extraData?: string; concurrent?: number; interval?: number }): Promise<void>;
  isReady(): Promise<{ ready: boolean }>;
  isSdkReady(): Promise<{ ready: boolean }>;
  addListener(eventName: string, listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
}

const CsjAd = registerPlugin<CsjAdPlugin>('CsjAd', {
  web: () => import('./CsjAdPluginWeb').then(m => new m.CsjAdPluginWeb() as any),
});

export default CsjAd;