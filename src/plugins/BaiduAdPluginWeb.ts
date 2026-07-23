import { WebPlugin } from '@capacitor/core';
import type { CsjAdPlugin } from './BaiduAdPlugin';

export class BaiduAdPluginWeb extends WebPlugin implements CsjAdPlugin {
  async loadRewardVideoAd(options: { adId: string }): Promise<void> {
    console.log('Web 环境，使用模拟广告，广告位ID:', options.adId);
    return Promise.resolve();
  }

  async showRewardVideoAd(): Promise<void> {
    console.log('Web 环境，模拟广告展示');
    return Promise.resolve();
  }

  async isReady(): Promise<{ ready: boolean }> {
    return { ready: false };
  }

  async addListener(eventName: string, listenerFunc: (data: any) => void): Promise<any> {
    console.log('Web 环境，注册监听器:', eventName);
    return Promise.resolve();
  }

  async removeListener(eventName: string, listenerFunc: (data: any) => void): Promise<void> {
    console.log('Web 环境，移除监听器:', eventName);
    return Promise.resolve();
  }
}