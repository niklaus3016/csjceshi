import { WebPlugin } from '@capacitor/core';

export class CsjAdPluginWeb extends WebPlugin {
  async loadRewardVideoAd(_options: { adId: string; userId?: string; extraData?: string }): Promise<void> {
    console.log('Web 环境不支持穿山甲原生广告，请使用 H5 SDK');
    return Promise.resolve();
  }

  async showRewardVideoAd(): Promise<void> {
    console.log('Web 环境不支持穿山甲原生广告，请使用 H5 SDK');
    return Promise.resolve();
  }

  async preloadRewardVideoAd(_options: { adIds: string[]; userId?: string; extraData?: string; concurrent?: number; interval?: number }): Promise<void> {
    console.log('Web 环境不支持穿山甲原生广告预缓存');
    return Promise.resolve();
  }

  async isReady(): Promise<{ ready: boolean }> {
    return { ready: false };
  }

  async isSdkReady(): Promise<{ ready: boolean }> {
    return { ready: true };
  }

  async addListener(_eventName: string, _listenerFunc: (data: any) => void): Promise<any> {
    console.log('Web 环境不支持穿山甲原生广告，请使用 H5 SDK');
    return Promise.resolve();
  }
}