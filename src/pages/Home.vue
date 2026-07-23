<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { Coins, History, PlayCircle, LogOut, TrendingUp, Wallet, CreditCard } from 'lucide-vue-next';
import { getUserInfo, rewardGold, getGoldLogs, recordLogin, getLoginStats, submitWithdrawRequest, getWithdrawStatus, getWithdrawRecords, claimDailyBonus, recordActivity, type WithdrawRecord } from '../api/apiService';
import { useAdManager } from '../composables/useAdManager';
import { TTSPlugin } from '../plugins/TTSPlugin';
import { Capacitor } from '@capacitor/core';

interface Record {
  id: string;
  time: string;
  amount: number;
}

const router = useRouter();
const empId = ref(localStorage.getItem('empId') || '');
const userId = ref(localStorage.getItem('userId') || '');

// 状态管理
const currentMonthGold = ref(0);
const lastMonthGold = ref(0);
const todayCoins = ref(0);
const todayRecordCount = ref(0);
const todayTarget = ref(100000);
const bonusGold = ref(0);  // 额外金币奖励
const hasClaimedBonus = ref(false);  // 是否已领取额外金币
const isClaimingBonus = ref(false);  // 是否正在领取额外金币


const records = ref<Record[]>([]);
const isLoading = ref(false);
const isLoadingRecords = ref(false);
const error = ref('');

const isWatching = ref(false);
const showAllRecords = ref(false);

// 金币奖励弹窗和语音
const showRewardPopup = ref(false);
const rewardAmount = ref(0);
let rewardTimeout: ReturnType<typeof setTimeout> | null = null;

// 播放金币到账语音
const playRewardSound = async (amount: number) => {
  console.log('========== playRewardSound 被调用 ==========');
  console.log('金币数量:', amount);
  
  try {
    const gold = Math.floor(amount);
    let message = '';
    
    if (gold >= 500) {
      message = `哇塞！太厉害了！恭喜你赚了${gold}金币！`;
    } else if (gold >= 300) {
      message = `太棒了！恭喜你赚了${gold}金币！`;
    } else if (gold >= 100) {
      message = `恭喜你赚了${gold}金币！`;
    } else {
      message = `恭喜你又赚了${gold}金币！`;
    }
    
    console.log('语音内容:', message);
    
    // 检查是否在 Android 平台
    if (Capacitor.getPlatform() === 'android') {
      console.log('使用原生 Android TTS');
      try {
        const result = await TTSPlugin.speak({ text: message });
        console.log('原生 TTS 播放成功:', result);
      } catch (err) {
        console.error('原生 TTS 播放失败:', err);
        // 回退到 Web Speech API
        playWebSpeech(message);
      }
    } else {
      // 在浏览器中使用 Web Speech API
      console.log('使用 Web Speech API');
      playWebSpeech(message);
    }
  } catch (err) {
    console.error('语音播放失败:', err);
  }
};

// 使用 Web Speech API 播放语音
const playWebSpeech = (message: string) => {
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    console.error('浏览器不支持语音合成');
    return;
  }
  
  // 取消之前的语音
  window.speechSynthesis.cancel();
  
  const utterance = new SpeechSynthesisUtterance(message);
  utterance.lang = 'zh-CN';
  utterance.rate = 1.0;
  utterance.pitch = 1.2;
  utterance.volume = 1.0;
  
  // 等待语音列表加载完成
  const speak = () => {
    const voices = window.speechSynthesis.getVoices();
    console.log('可用语音数量:', voices.length);
    
    const zhVoice = voices.find(v => v.lang.includes('zh'));
    if (zhVoice) {
      utterance.voice = zhVoice;
      console.log('使用中文语音:', zhVoice.name);
    } else {
      console.log('未找到中文语音，使用默认语音');
    }
    
    window.speechSynthesis.speak(utterance);
    console.log('语音播放命令已发送');
  };
  
  // 检查语音列表是否已加载
  if (window.speechSynthesis.getVoices().length > 0) {
    speak();
  } else {
    window.speechSynthesis.onvoiceschanged = () => {
      window.speechSynthesis.onvoiceschanged = null;
      speak();
    };
  }
};

// 显示金币奖励
const showRewardAnimation = async (amount: number) => {
  console.log('========== showRewardAnimation 被调用 ==========');
  console.log('金币数量:', amount);
  
  // 清除之前的定时器
  if (rewardTimeout) {
    clearTimeout(rewardTimeout);
  }
  
  rewardAmount.value = amount;
  showRewardPopup.value = true;
  console.log('showRewardPopup 已设置为 true');
  
  // 播放语音
  await playRewardSound(amount);
  console.log('playRewardSound 已调用');
  
  // 1秒后隐藏
  rewardTimeout = setTimeout(() => {
    showRewardPopup.value = false;
    console.log('showRewardPopup 已设置为 false');
  }, 1000);
};
const showWithdrawModal = ref(false);
const withdrawAmount = ref(0);
const alipayAccount = ref('');
const alipayName = ref('');
const isSubmittingWithdraw = ref(false);
const withdrawSuccess = ref(false);
const withdrawEnabled = ref(false); // 提现开关状态
const showWithdrawRecordsModal = ref(false);
const withdrawRecords = ref<WithdrawRecord[]>([]);
const isLoadingWithdrawRecords = ref(false);

// 登录天数统计
const loginDays = ref(0);

// 获取或生成设备ID
const getDeviceId = (): string => {
  let deviceId = localStorage.getItem('deviceId');
  if (!deviceId) {
    deviceId = 'device_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    localStorage.setItem('deviceId', deviceId);
  }
  return deviceId;
};

// 记录用户活动
const recordUserActivity = async () => {
  if (!userId.value || !empId.value) return;
  
  try {
    const deviceId = getDeviceId();
    await recordActivity(userId.value, empId.value, deviceId);
    console.log('活动记录成功');
  } catch (err) {
    console.error('记录活动失败:', err);
  }
};

// 加载登录统计
const loadLoginStats = async () => {
  if (!userId.value || !empId.value) return;

  try {
    // 检查今天是否已经记录过登录
    const today = new Date().toISOString().split('T')[0];
    const lastLoginDate = localStorage.getItem('lastLoginDate');

    // 如果今天还没有记录过登录，则记录本次登录
    if (lastLoginDate !== today) {
      await recordLogin(userId.value, empId.value);
      localStorage.setItem('lastLoginDate', today);
    }

    // 获取登录统计
    const response = await getLoginStats(userId.value, empId.value);
    if (response.success && response.data) {
      loginDays.value = response.data.totalLoginDays;
    }
  } catch (err) {
    console.error('获取登录统计失败:', err);
  }
};

// 加载提现开关状态
const loadWithdrawStatus = async () => {
  try {
    const response = await getWithdrawStatus();
    console.log('提现状态响应:', response);
    // 后端返回的数据结构: {success: true, enabled: true}
    // enabled字段在根级别，不在data对象中
    if (response.success) {
      // 处理后端返回的enabled字段，可能是布尔值或字符串
      const enabledValue = (response as any).enabled;
      console.log('提现开关原始值:', enabledValue, '类型:', typeof enabledValue);
      // 转换为布尔值
      withdrawEnabled.value = enabledValue === true || enabledValue === 'true' || enabledValue === 1 || enabledValue === '1';
      console.log('提现开关最终状态:', withdrawEnabled.value);
    } else {
      console.log('提现状态获取失败');
      withdrawEnabled.value = false;
    }
  } catch (err) {
    console.error('获取提现状态失败:', err);
    withdrawEnabled.value = false;
  }
};

const adConfig = {
  appId: '5793939',
  slotIds: [
    '985678631', // 保价200 (190-200)
    '985678626', // 保价100 (90-100)
    '985678630'  // 保价30 (20-30)
  ], // 按优先级从高到低排列
};

const { showRewardVideo } = useAdManager(adConfig);

// 初始化数据
onMounted(async () => {
  if (!empId.value || !userId.value) {
    router.push('/login');
    return;
  }
  
  await loadLoginStats();
  await loadWithdrawStatus();
  await loadUserInfo();
  await loadGoldRecords();
  
  // 记录用户活动（进入首页）
  await recordUserActivity();
  
  // 启动数据同步
  startDataSync();
});

// 清理资源
onUnmounted(() => {
  // 停止数据同步
  stopDataSync();
  
  // 移除页面聚焦事件监听
  if (typeof window !== 'undefined') {
    window.removeEventListener('focus', handlePageFocus);
  }
  
  // 清除奖励定时器
  if (rewardTimeout) {
    clearTimeout(rewardTimeout);
  }
});

// 加载用户金币信息
const loadUserInfo = async () => {
  if (!empId.value || !userId.value) return;  
  isLoading.value = true;
  error.value = '';
  
  try {
    console.log('加载用户信息:', { userId: userId.value, empId: empId.value });
    const response = await getUserInfo(userId.value, empId.value);
    console.log('用户信息响应:', response);
    if (response.success && response.data) {
      currentMonthGold.value = Number(response.data.currentMonthGold) || 0;
      lastMonthGold.value = Number(response.data.lastMonthGold) || 0;
      todayTarget.value = Number(response.data.todayTarget) || 0;
      bonusGold.value = response.data.bonusGold !== undefined && response.data.bonusGold !== null ? Number(response.data.bonusGold) : 0;
      hasClaimedBonus.value = Boolean(response.data.hasClaimedBonus);
    } else {
      error.value = response.message || '获取金币信息失败';
    }
  } catch (err) {
    console.error('获取金币信息失败:', err);
    error.value = '网络错误，请稍后重试';
  } finally {
    isLoading.value = false;
  }
};

// 加载金币记录
const loadGoldRecords = async () => {
  if (!userId.value) {
    console.log('❌ 加载金币记录失败：userId为空');
    return;
  }

  console.log('🔄 开始加载金币记录...');
  isLoadingRecords.value = true;
  
  // 重置数据，避免显示旧数据
  records.value = [];
  todayCoins.value = 0;
  todayRecordCount.value = 0;

  try {
    console.log('📡 发送API请求获取金币记录...');
    console.log('   userId:', userId.value);
    console.log('   limit:', 10000);
    
    // 获取足够多的记录用于计算今日统计（使用较大limit）
    const response = await getGoldLogs(userId.value, 10000);
    
    console.log('✅ API请求完成，响应:', {
      success: response.success,
      message: response.message,
      dataLength: response.data ? response.data.length : 0
    });
    
    if (response.success && response.data && Array.isArray(response.data)) {
      console.log('📊 开始处理数据，原始数据量:', response.data.length);
      
      // 只保留最近200条用于显示
      const displayData = response.data.slice(0, 200);
      console.log('📋 显示数据量:', displayData.length);
      
      // 转换并排序记录（按时间倒序，最新的在前面）
      records.value = displayData
        .map((log: any, index: number) => {
          // 安全处理时间字段
          const createTime = log.createTime || Date.now();
          const recordTime = new Date(createTime);
          
          const record = {
            id: log._id || `record-${index}-${Date.now()}`, // 确保ID唯一
            time: recordTime.toLocaleString('zh-CN', {
              year: 'numeric', month: '2-digit', day: '2-digit',
              hour: '2-digit', minute: '2-digit'
            }),
            amount: Number(log.gold) || 0, // 确保金额为数字且有默认值
            timestamp: recordTime.getTime() // 用于排序
          };
          
          // 每10条记录打印一次，避免日志过多
          if (index % 10 === 0) {
            console.log(`   处理记录 ${index + 1}:`, {
              id: record.id,
              time: record.time,
              amount: record.amount
            });
          }
          
          return record;
        })
        .sort((a, b) => b.timestamp - a.timestamp) // 按时间倒序排序
        .map((record) => ({
          id: record.id,
          time: record.time,
          amount: record.amount
        }));

      console.log('🔧 排序完成，最终记录数:', records.value.length);

      // 计算今日金币收益（使用全部数据，确保统计准确）
      const getLocalDate = (date: Date) => {
        return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '-');
      };
      
      const today = getLocalDate(new Date());
      console.log('📅 今日日期:', today);
      
      const todayRecords = response.data.filter((log: any) => {
        try {
          const logDate = getLocalDate(new Date(log.createTime || Date.now()));
          return logDate === today;
        } catch (error) {
          console.warn('⚠️ 日期处理错误:', error);
          return false;
        }
      });
      
      todayCoins.value = todayRecords.reduce((sum: number, log: any) => sum + (Number(log.gold) || 0), 0);
      todayRecordCount.value = todayRecords.length;
      
      console.log('💰 今日统计:', {
        coins: todayCoins.value,
        records: todayRecordCount.value
      });
    } else {
      console.warn('⚠️ API响应数据异常:', response);
    }
  } catch (err) {
    console.error('❌ 获取金币记录失败:', err);
  } finally {
    console.log('✅ 加载金币记录完成');
    isLoadingRecords.value = false;
  }
};

// 处理广告观看
const handleWatchAd = async () => {
  if (isWatching.value || !empId.value || !userId.value) return;
  
  isWatching.value = true;
  error.value = '';
  
  try {
    // 记录用户活动（观看广告）
    await recordUserActivity();
    
    // 调用广告管理逻辑
    const result = await showRewardVideo();
    console.log('广告观看成功，ECPM:', result.ecpm, '广告位ID:', result.slotId);
    
    // 调用后端发放金币接口，传递ecpm和广告位ID
    const rewardResponse = await rewardGold(userId.value, empId.value, result.ecpm, result.slotId);
    
    if (rewardResponse.success && rewardResponse.data) {
      const earned = rewardResponse.data.gold;
      console.log('获得金币数量:', earned);
      
      // 确保金币数量是有效的数字
      if (typeof earned === 'number' && earned > 0) {
        // 更新本地状态
        currentMonthGold.value = rewardResponse.data.currentMonthGold;
        // 显示金币奖励动画和语音
        showRewardAnimation(earned);
        // 重新加载金币记录（会自动计算今日金币）
        await loadGoldRecords();
      } else {
        console.error('金币数量无效:', earned);
        error.value = '金币发放失败';
      }
    } else {
      error.value = rewardResponse.message || '金币发放失败';
    }
  } catch (err) {
    console.error('广告观看失败:', err);
    error.value = '暂无合适广告匹配，请点击重试';
  } finally {
    isWatching.value = false;
  }
};

// 实时数据同步
let syncInterval: ReturnType<typeof setInterval> | null = null;

// 启动数据同步
const startDataSync = () => {
  // 清除之前的定时器
  if (syncInterval) {
    clearInterval(syncInterval);
  }
  
  // 每30秒自动刷新数据
  syncInterval = setInterval(async () => {
    if (userId.value) {
      console.log('🔄 自动同步数据...');
      try {
        await loadUserInfo();
        await loadGoldRecords();
      } catch (err) {
        console.warn('自动同步数据失败:', err);
      }
    }
  }, 30000); // 30秒
  
  console.log('✅ 数据同步已启动');
};

// 停止数据同步
const stopDataSync = () => {
  if (syncInterval) {
    clearInterval(syncInterval);
    syncInterval = null;
    console.log('✅ 数据同步已停止');
  }
};

// 页面聚焦时刷新数据
const handlePageFocus = async () => {
  console.log('🔄 页面聚焦，刷新数据...');
  if (userId.value) {
    try {
      await loadUserInfo();
      await loadGoldRecords();
    } catch (err) {
      console.warn('页面聚焦刷新数据失败:', err);
    }
  }
};

// 监听页面聚焦事件
if (typeof window !== 'undefined') {
  window.addEventListener('focus', handlePageFocus);
}

// 领取今日目标额外金币
const handleClaimBonus = async () => {
  if (!userId.value || !empId.value) return;
  
  isClaimingBonus.value = true;
  
  try {
    const response = await claimDailyBonus(userId.value, empId.value);
    if (response.success && response.data) {
      const earned = response.data.gold;
      currentMonthGold.value = response.data.currentMonthGold;
      hasClaimedBonus.value = true;
      // 显示金币奖励动画和语音
      showRewardAnimation(earned);
      await loadGoldRecords();
    } else {
      error.value = response.message || '领取失败';
    }
  } catch (err) {
    console.error('领取额外金币失败:', err);
    error.value = '网络错误，请稍后重试';
  } finally {
    isClaimingBonus.value = false;
  }
};

// 处理登出
const handleLogout = () => {
  localStorage.removeItem('empId');
  localStorage.removeItem('userId');
  localStorage.removeItem('employeeInfo');
  router.push('/login');
};

// 打开提现弹窗
const openWithdrawModal = () => {
  withdrawAmount.value = Number((lastMonthGold.value / 1000).toFixed(2));
  alipayAccount.value = '';
  alipayName.value = '';
  withdrawSuccess.value = false;
  showWithdrawModal.value = true;
};

// 关闭提现弹窗
const closeWithdrawModal = () => {
  showWithdrawModal.value = false;
};

// 打开提现记录弹窗
const openWithdrawRecordsModal = async () => {
  showWithdrawRecordsModal.value = true;
  await loadWithdrawRecords();
};

// 关闭提现记录弹窗
const closeWithdrawRecordsModal = () => {
  showWithdrawRecordsModal.value = false;
};

// 加载提现记录
const loadWithdrawRecords = async () => {
  if (!userId.value) return;
  isLoadingWithdrawRecords.value = true;
  try {
    const response = await getWithdrawRecords(userId.value);
    console.log('提现记录响应:', response);
    if (response.success && response.data) {
      withdrawRecords.value = response.data;
      // 打印第一条记录的详细信息
      if (response.data.length > 0) {
        console.log('第一条提现记录:', response.data[0]);
      }
    }
  } catch (err) {
    console.error('加载提现记录失败:', err);
  } finally {
    isLoadingWithdrawRecords.value = false;
  }
};

// 格式化日期（使用北京时间）
const formatDate = (dateStr: string) => {
  const date = new Date(dateStr);
  const beijingTime = new Date(date.getTime() + 8 * 60 * 60 * 1000);
  return `${beijingTime.getFullYear()}-${String(beijingTime.getMonth() + 1).padStart(2, '0')}-${String(beijingTime.getDate()).padStart(2, '0')} ${String(beijingTime.getHours()).padStart(2, '0')}:${String(beijingTime.getMinutes()).padStart(2, '0')}`;
};

// 获取状态样式
const getStatusStyle = (status: number) => {
  switch (status) {
    case 0:
      return { text: '待打款', class: 'text-amber-400 bg-amber-500/10 border-amber-500/20' };
    case 1:
      return { text: '已打款', class: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' };
    case 2:
      return { text: '已拒绝', class: 'text-red-400 bg-red-500/10 border-red-500/20' };
    default:
      return { text: '未知状态', class: 'text-zinc-400 bg-zinc-500/10 border-zinc-500/20' };
  }
};

// 提交提现申请
const submitWithdraw = async () => {
  if (!alipayAccount.value.trim() || !alipayName.value.trim()) {
    alert('请填写完整的支付宝信息');
    return;
  }

  isSubmittingWithdraw.value = true;

  try {
    // 调用后端提现接口
    const response = await submitWithdrawRequest({
      userId: userId.value,
      employeeId: empId.value,
      amount: withdrawAmount.value,
      alipayAccount: alipayAccount.value,
      alipayName: alipayName.value,
      goldAmount: lastMonthGold.value
    });

    if (response.success) {
      // 更新剩余金币
      if (response.data && response.data.remainingGold !== undefined) {
        lastMonthGold.value = response.data.remainingGold;
      }
      withdrawSuccess.value = true;
      // 3秒后关闭弹窗
      setTimeout(() => {
        closeWithdrawModal();
      }, 3000);
    } else {
      alert(response.message || '提现申请提交失败');
    }
  } catch (err) {
    console.error('提现申请失败:', err);
    alert('提现申请提交失败，请稍后重试');
  } finally {
    isSubmittingWithdraw.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-[#020205] text-white pb-12 relative overflow-hidden">
    <!-- 背景装饰光晕 -->
    <div class="absolute top-[-10%] right-[-10%] w-[60%] h-[60%] bg-purple-600/10 blur-[120px] rounded-full pointer-events-none" />
    <div class="absolute top-[20%] left-[-10%] w-[50%] h-[50%] bg-blue-600/10 blur-[120px] rounded-full pointer-events-none" />
    <div class="absolute bottom-[-10%] left-[20%] w-[40%] h-[40%] bg-emerald-500/10 blur-[120px] rounded-full pointer-events-none" />
    <div class="absolute top-[30%] right-[20%] w-[40%] h-[40%] bg-emerald-500/10 blur-[120px] rounded-full pointer-events-none" />

    <!-- Header -->
    <header class="bg-black/40 backdrop-blur-xl border-b border-white/5 pt-8 pb-5 px-6 flex justify-between items-center sticky top-0 z-20">
      <div class="flex items-center">
        <div class="w-8 h-8 bg-gradient-to-br from-emerald-400 to-blue-500 rounded-xl flex items-center justify-center shadow-lg shadow-emerald-500/20 mr-3">
          <TrendingUp class="text-white w-5 h-5" />
        </div>
        <div class="flex flex-col">
          <span class="font-bold text-sm tracking-widest uppercase bg-gradient-to-r from-emerald-400 to-blue-400 bg-clip-text text-transparent">广告变现系统</span>
          <span class="text-[10px] text-zinc-400 font-bold tracking-wider">员工ID：{{ empId }} · 已登录{{ loginDays }}天</span>
        </div>
      </div>
      <button @click="handleLogout" class="w-10 h-10 rounded-full bg-white/5 flex items-center justify-center text-zinc-500 hover:text-white hover:bg-white/10 transition-all">
        <LogOut class="w-4 h-4" />
      </button>
    </header>

    <main class="max-w-md mx-auto px-6 mt-6 space-y-6 relative z-10">
      <!-- Stats Section -->
      <div class="space-y-3">
        <div class="flex justify-between items-end px-2">
          <h2 class="text-[10px] uppercase tracking-[0.2em] text-zinc-500 font-medium">收益看板</h2>
          <div class="flex items-center">
            <div class="w-1 h-1 bg-emerald-500 rounded-full animate-pulse mr-1.5" />
            <span class="text-[10px] text-emerald-500 font-mono">实时同步</span>
          </div>
        </div>
        <!-- 错误信息显示 -->
          <div v-if="error" class="p-4 bg-red-500/10 border border-red-500/20 rounded-lg mb-3">
            <p class="text-red-400 text-sm">{{ error }}</p>
          </div>
          
          <!-- 加载状态 -->
          <div v-if="isLoading" class="grid grid-cols-2 gap-3">
            <div class="bg-white/3 border border-white/5 p-4 rounded-[1.25rem] backdrop-blur-md animate-pulse">
              <div class="h-3 bg-white/10 rounded w-1/2 mb-2"></div>
              <div class="h-6 bg-white/10 rounded w-3/4"></div>
            </div>
            <div class="bg-white/3 border border-white/5 p-4 rounded-[1.25rem] backdrop-blur-md animate-pulse">
              <div class="h-3 bg-white/10 rounded w-1/2 mb-2"></div>
              <div class="h-6 bg-white/10 rounded w-3/4"></div>
            </div>
            <div class="bg-white/3 border border-white/5 p-4 rounded-[1.25rem] backdrop-blur-md animate-pulse">
              <div class="h-3 bg-white/10 rounded w-1/2 mb-2"></div>
              <div class="h-6 bg-white/10 rounded w-3/4"></div>
            </div>
            <div class="bg-white/3 border border-white/5 p-4 rounded-[1.25rem] backdrop-blur-md animate-pulse">
              <div class="h-3 bg-white/10 rounded w-1/2 mb-2"></div>
              <div class="h-6 bg-white/10 rounded w-3/4"></div>
            </div>
          </div>
          
          <!-- 金币统计数据 -->
          <div v-else class="grid grid-cols-2 gap-3">
            <div class="group relative glass-card rounded-[1.25rem] overflow-hidden transition-all hover:bg-white/5">
              <div class="absolute top-0 right-0 w-16 h-16 bg-blue-500/10 blur-2xl rounded-full -mr-8 -mt-8" />
              <div class="p-4 flex justify-between items-start">
                <div>
                  <p class="text-zinc-500 text-[9px] uppercase tracking-wider mb-1">上月累计金币</p>
                  <p class="text-lg font-light tracking-tight text-blue-400">{{ Math.floor(lastMonthGold).toLocaleString() }}</p>
                </div>
                <div class="flex flex-col gap-2.5">
                  <button 
                    @click="withdrawEnabled ? openWithdrawModal() : null"
                    :disabled="!withdrawEnabled"
                    class="px-2 py-1 rounded-lg text-[9px] font-bold uppercase tracking-wider transition-all border flex items-center justify-center gap-1"
                    :class="withdrawEnabled 
                      ? 'bg-blue-500/20 text-blue-400 hover:bg-blue-500/30 border-blue-500/30 cursor-pointer' 
                      : 'bg-zinc-500/10 text-zinc-600 border-zinc-500/20 cursor-not-allowed'"
                  >
                    <Wallet class="w-3 h-3" />
                    提现
                  </button>
                  <button 
                    @click="openWithdrawRecordsModal"
                    class="px-2 py-1 rounded-lg text-[9px] font-bold uppercase tracking-wider transition-all border flex items-center justify-center gap-1 bg-blue-500/20 text-blue-400 hover:bg-blue-500/30 border-blue-500/30 cursor-pointer"
                  >
                    <CreditCard class="w-3 h-3" />
                    记录
                  </button>
                </div>
              </div>
            </div>
            <div class="group relative bg-gradient-to-br from-emerald-500 to-teal-700 rounded-[1.25rem] shadow-xl shadow-emerald-500/10 overflow-hidden transition-all hover:scale-[1.02]">
              <div class="absolute top-0 right-0 w-16 h-16 bg-white/10 blur-2xl rounded-full -mr-8 -mt-8" />
              <div class="p-4">
                <p class="text-emerald-100/60 text-[9px] uppercase tracking-wider mb-1">本月累计金币</p>
                <p class="text-lg font-bold text-white tracking-tight">{{ Math.floor(currentMonthGold).toLocaleString() }}</p>
              </div>
            </div>
            <div class="group relative glass-card rounded-[1.25rem] overflow-hidden transition-all hover:bg-white/5">
              <div class="absolute top-0 right-0 w-16 h-16 bg-purple-500/10 blur-2xl rounded-full -mr-8 -mt-8" />
              <div class="p-4">
                <div 
                  class="absolute top-4 right-4 px-2 py-0.5 rounded-full text-[8px] font-bold tracking-widest border"
                  :class="todayTarget > 0 ? (todayCoins >= todayTarget ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-red-500/10 text-red-400 border-red-500/20') : 'bg-zinc-500/10 text-zinc-400 border-zinc-500/20'"
                >
                  {{ todayTarget > 0 ? (todayCoins >= todayTarget ? '已完成' : '未完成') : '未设置' }}
                </div>
                <p class="text-zinc-500 text-[9px] uppercase tracking-wider mb-1">今日目标任务</p>
                <p class="text-lg font-light tracking-tight text-purple-400">{{ todayTarget > 0 ? todayTarget.toLocaleString() : '未设置' }}</p>
              </div>
            </div>
            <div class="group relative glass-card rounded-[1.25rem] overflow-hidden transition-all hover:bg-white/5">
              <div class="absolute top-0 right-0 w-16 h-16 bg-amber-500/10 blur-2xl rounded-full -mr-8 -mt-8" />
              <div class="p-4">
                <p class="text-zinc-500 text-[9px] uppercase tracking-wider mb-1">今日金币收益</p>
                <div class="flex items-center gap-2">
                  <p class="text-lg font-bold text-amber-400 tracking-tight whitespace-nowrap">{{ Math.floor(todayCoins).toLocaleString() }}</p>
                  <span class="text-[10px] text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded whitespace-nowrap flex-shrink-0">{{ todayRecordCount }}条</span>
                </div>
              </div>
            </div>
          </div>
      </div>

      <!-- 今日目标进度条和额外金币奖励 -->
      <div v-if="todayTarget >= 0" class="px-4 py-2">
        <div class="flex items-center">
          <!-- 进度条 - 占3/4 -->
          <div class="w-3/4 space-y-2 mr-3">
            <div class="flex justify-between items-center text-[9px]">
              <span class="text-zinc-500 uppercase tracking-wider">今日目标进度</span>
              <span class="text-purple-400 font-bold">{{ todayTarget > 0 ? `${Math.min(100, Math.floor((todayCoins / todayTarget) * 100))}%` : '未设置' }}</span>
            </div>
            <div class="h-2 bg-zinc-800/50 rounded-full overflow-hidden border border-white/5">
              <div 
              class="h-full bg-gradient-to-r from-purple-500 to-emerald-500 rounded-full transition-all duration-500 ease-out"
              :style="{ width: todayTarget > 0 ? `${Math.min(100, (todayCoins / todayTarget) * 100)}%` : '0%' }"
            />
            </div>
            <div class="flex justify-between text-[8px] text-zinc-600">
              <span>{{ Math.floor(todayCoins).toLocaleString() }} 金币</span>
              <span>目标 {{ todayTarget.toLocaleString() }} 金币</span>
            </div>
            <div v-if="todayTarget > 0 && !hasClaimedBonus" class="text-[7px] text-zinc-500 text-center mt-1">
              {{ todayCoins >= todayTarget ? '✓ 已达标可领取' : `✗ 还差 ${Math.floor(todayTarget - todayCoins).toLocaleString()} 金币` }}
            </div>
          </div>

          <!-- 领取额外金币按钮 - 占1/4 -->
          <div class="w-1/4 shrink-0">
            <!-- 已领取提示 -->
            <div
              v-if="hasClaimedBonus"
              class="w-full h-full py-1.5 px-2 rounded-lg text-center text-[8px] font-bold uppercase tracking-widest bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex flex-col items-center justify-center"
            >
              已领取
            </div>

            <!-- 领取按钮 -->
            <button
              v-else-if="bonusGold > 0"
              @click="handleClaimBonus"
              :disabled="isClaimingBonus || todayCoins < todayTarget || todayTarget === 0"
              class="w-full h-full py-1.5 px-2 rounded-lg font-bold text-[9px] uppercase tracking-widest transition-all border flex flex-col items-center justify-center relative overflow-hidden group"
              :class="[
                    isClaimingBonus || todayCoins < todayTarget || todayTarget === 0
                      ? 'bg-zinc-800/50 border-zinc-700 text-zinc-600 cursor-not-allowed' 
                      : 'bg-gradient-to-br from-amber-400 via-orange-500 to-red-500 text-white border-amber-300/50 shadow-[0_0_20px_rgba(245,158,11,0.4),0_0_40px_rgba(245,158,11,0.2)] hover:shadow-[0_0_30px_rgba(245,158,11,0.6),0_0_60px_rgba(245,158,11,0.3)] hover:scale-[1.05] active:scale-[0.95]'
                  ]"
            >
              <!-- 动态背景光效 -->
              <div 
                v-if="!isClaimingBonus && todayCoins >= todayTarget && todayTarget > 0"
                class="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent -translate-x-full animate-[shimmer_2s_infinite]"
              />
              <!-- 脉冲光圈 -->
              <div 
                v-if="!isClaimingBonus && todayCoins >= todayTarget && todayTarget > 0"
                class="absolute inset-0 rounded-lg animate-pulse bg-gradient-to-r from-amber-500/20 via-orange-500/30 to-red-500/20"
              />
              <!-- 灰色脉冲光圈（未完成目标或未设置目标时） -->
              <div 
                v-if="!isClaimingBonus && (todayCoins < todayTarget || todayTarget === 0)"
                class="absolute inset-0 rounded-lg animate-pulse bg-gradient-to-r from-zinc-500/10 via-zinc-600/20 to-zinc-500/10"
              />
              <!-- 图标 -->
              <Coins class="w-3 h-3 relative z-10 mb-0.5" :class="{ 'animate-spin': isClaimingBonus, 'animate-bounce': !isClaimingBonus && todayCoins >= todayTarget && todayTarget > 0 }" />
              <span class="text-center leading-tight relative z-10">{{ isClaimingBonus ? '领取中...' : `奖${bonusGold}金币` }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Ad Trigger - 大圆形按钮 -->
      <div class="flex flex-col items-center justify-center py-2 relative">
        <div class="relative">
          <!-- 按钮背景光晕 -->
          <div 
            class="absolute inset-0 blur-3xl rounded-full transition-all duration-1000"
            :class="[
              isWatching ? 'bg-blue-500 opacity-60 scale-110' : 'bg-emerald-500 opacity-30 scale-100',
              'animate-pulse'
            ]"
          />
          
          <button
            @click="handleWatchAd"
            :disabled="isWatching"
            class="relative w-48 h-48 rounded-full flex flex-col items-center justify-center transition-all active:scale-90 border-2"
            :class="[
              isWatching 
                ? 'bg-zinc-900/80 border-zinc-800 text-zinc-600 cursor-not-allowed' 
                : 'bg-black border-white/10 text-white shadow-[0_20px_50px_rgba(0,0,0,0.5)] hover:border-emerald-500/50'
            ]"
          >
            <div :class="{ 'animate-spin': isWatching }" class="mb-5">
              <PlayCircle class="w-16 h-16" :class="isWatching ? 'text-zinc-700' : 'text-emerald-400'" />
            </div>
            <div class="text-center">
              <span class="block text-base font-black uppercase tracking-widest leading-none">
                {{ isWatching ? '正在加载' : '点击赚取金币' }}
              </span>
            </div>
          </button>
        </div>


        <p class="mt-4 text-[10px] text-zinc-500 uppercase tracking-[0.3em] font-medium">
          {{ isWatching ? '正在为您匹配优质广告资源' : '广告激励已就绪' }}
        </p>
        
        <!-- 金币奖励弹窗 -->
        <transition name="reward-popup">
          <div v-if="showRewardPopup" class="fixed inset-0 flex items-center justify-center z-9999 pointer-events-none">
            <div class="bg-gradient-to-r from-amber-400 to-orange-500 text-white px-6 py-3 rounded-xl font-bold shadow-lg flex items-center border border-white/30 animate-bounce pointer-events-auto">
              <Coins class="w-6 h-6 text-white mr-2" />
              <span class="text-xl">+{{ Math.floor(rewardAmount) }} 金币</span>
            </div>
          </div>
        </transition>
        
        <!-- 测试按钮（已隐藏） -->
        <!-- <div class="flex gap-3 mt-4">
          <button
            @click="showRewardAnimation(100)"
            class="px-6 py-3 bg-blue-500 text-white rounded-full text-sm font-medium hover:bg-blue-600 transition-colors"
          >
            测试奖励弹窗
          </button>
          <button
            @click="playRewardSound(100)"
            class="px-6 py-3 bg-green-500 text-white rounded-full text-sm font-medium hover:bg-green-600 transition-colors"
          >
            测试语音播报
          </button>
        </div> -->
      </div>

      <!-- History Section -->
      <div class="space-y-3">
        <div class="flex items-center justify-between px-2">
          <div class="flex items-center">
            <History class="w-3 h-3 text-zinc-500 mr-2" />
            <h2 class="text-[10px] uppercase tracking-[0.2em] text-zinc-500 font-medium">最近收益（近50条）</h2>
          </div>
          <!-- 暂时隐藏查看全部按钮 -->
          <!-- <button 
            @click="showAllRecords = true"
            class="px-3 py-1 rounded-full bg-white/5 text-[9px] text-emerald-500 uppercase tracking-widest hover:bg-white/10 transition-all font-bold border border-emerald-500/20"
          >
            查看全部
          </button> -->
        </div>
        <div class="glass-card rounded-[2rem] overflow-hidden">
          <div class="divide-y divide-white/5 max-h-[400px] overflow-y-auto no-scrollbar">
            <!-- 加载状态 -->
            <div v-if="isLoadingRecords" class="py-16 text-center">
              <div class="w-12 h-12 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4 animate-spin">
                <History class="w-5 h-5 text-zinc-700" />
              </div>
              <p class="text-[10px] text-zinc-600 uppercase tracking-widest">加载中...</p>
            </div>
            <!-- 空状态 -->
            <div v-else-if="records.length === 0" class="py-16 text-center">
              <div class="w-12 h-12 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4">
                <History class="w-5 h-5 text-zinc-700" />
              </div>
              <p class="text-[10px] text-zinc-600 uppercase tracking-widest">暂无活动记录</p>
            </div>
            <!-- 记录列表 -->
            <div 
              v-else
              v-for="record in records.slice(0, 50)" 
              :key="record.id" 
              class="px-8 py-5 flex justify-between items-center hover:bg-white/2 transition-colors group"
            >
              <div class="flex flex-col">
                <span class="text-[11px] text-zinc-400 font-mono tracking-tighter">{{ record.time }}</span>
                <span class="text-[9px] text-zinc-600 uppercase tracking-widest mt-0.5">广告激励成功</span>
              </div>
              <div class="flex items-center">
                <span class="text-sm font-bold text-amber-400 font-mono group-hover:scale-110 transition-transform mr-2">+{{ Math.floor(record.amount) }}</span>
                <Coins class="w-3 h-3 text-amber-500/50" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- View All Records Modal -->
    <transition name="modal">
      <div v-if="showAllRecords" class="fixed inset-0 z-[9999] flex items-end justify-center sm:items-center p-0 sm:p-6 pointer-events-auto">
        <div class="absolute inset-0 bg-black/80 backdrop-blur-md z-[9998] pointer-events-auto" @click="showAllRecords = false" />
        <div class="relative w-full max-w-md bg-[#020205] border-t sm:border border-white/10 rounded-t-[3rem] sm:rounded-[3rem] overflow-hidden flex flex-col h-[90vh] max-h-[600px] z-[9999] shadow-2xl">
          <div class="px-8 py-6 border-b border-white/5 flex justify-between items-center bg-[#020205] z-10">
            <div class="flex items-center">
              <div class="w-8 h-8 bg-white/5 rounded-full flex items-center justify-center mr-3">
                <History class="w-4 h-4 text-zinc-400" />
              </div>
              <h3 class="text-sm font-bold uppercase tracking-widest">所有收益记录</h3>
            </div>
            <button 
              @click="showAllRecords = false"
              class="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-zinc-500 hover:text-white"
            >
              <LogOut class="w-4 h-4 rotate-90" />
            </button>
          </div>
          
          <div class="flex-1 overflow-y-auto no-scrollbar p-4">
            <div class="space-y-2">
              <!-- 加载状态 -->
              <div v-if="isLoadingRecords" class="py-20 text-center">
                <div class="w-12 h-12 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4 animate-spin">
                  <History class="w-5 h-5 text-zinc-700" />
                </div>
                <p class="text-xs text-zinc-600 uppercase tracking-widest">加载中...</p>
              </div>
              <!-- 空状态 -->
              <div v-else-if="records.length === 0" class="py-20 text-center">
                <p class="text-xs text-zinc-600 uppercase tracking-widest">暂无记录</p>
              </div>
              <!-- 记录列表 -->
              <div 
                v-else
                v-for="record in records" 
                :key="record.id" 
                class="px-6 py-4 rounded-2xl glass-card flex justify-between items-center min-h-[60px]"
              >
                <div class="flex flex-col">
                  <span class="text-[11px] text-zinc-400 font-mono tracking-tighter">{{ record.time }}</span>
                  <span class="text-[9px] text-zinc-600 uppercase tracking-widest mt-0.5">激励视频收益</span>
                </div>
                <div class="flex items-center">
                  <span class="text-sm font-bold text-amber-400 font-mono mr-2">+{{ Math.floor(record.amount) }}</span>
                  <Coins class="w-3 h-3 text-amber-500/50" />
                </div>
              </div>
              <!-- 如果记录达到200条，显示提示 -->
              <div v-if="records.length >= 200" class="text-center py-4">
                <p class="text-[10px] text-zinc-600">显示最近200条记录</p>
              </div>
            </div>
          </div>
          
          <div class="p-8 border-t border-white/5 text-center">
            <p class="text-[10px] text-zinc-600 uppercase tracking-[0.2em]">共计 {{ records.length }} 条记录</p>
          </div>
        </div>
      </div>
    </transition>

    <footer class="text-center mt-8 px-6 pb-8">
      <div class="inline-flex items-center px-4 py-2 rounded-full bg-white/2 border border-white/5">
        <div class="w-1 h-1 bg-zinc-700 rounded-full mr-2" />
        <p class="text-zinc-600 text-[9px] uppercase tracking-[0.4em]">
          安全加密连接已建立
        </p>
        <div class="w-1 h-1 bg-zinc-700 rounded-full ml-2" />
      </div>
    </footer>

    <!-- 提现弹窗 -->
    <transition name="modal">
      <div v-if="showWithdrawModal" class="fixed inset-0 z-[9999] flex items-end justify-center sm:items-center p-0 sm:p-6 pointer-events-auto">
        <div class="absolute inset-0 bg-black/80 backdrop-blur-md z-[9998] pointer-events-auto" @click="closeWithdrawModal" />
        <div class="relative w-full max-w-md bg-[#020205] border-t sm:border border-white/10 rounded-t-[3rem] sm:rounded-[3rem] overflow-hidden flex flex-col max-h-[85vh] z-[9999] shadow-2xl">
          <!-- 弹窗头部 -->
          <div class="px-8 py-6 border-b border-white/5 flex justify-between items-center sticky top-0 bg-[#020205] z-10">
            <div class="flex items-center">
              <div class="w-8 h-8 bg-blue-500/20 rounded-full flex items-center justify-center border border-blue-500/30 mr-3">
                <Wallet class="w-4 h-4 text-blue-400" />
              </div>
              <h3 class="text-sm font-bold uppercase tracking-widest">申请提现</h3>
            </div>
            <button 
              @click="closeWithdrawModal"
              class="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-zinc-500 hover:text-white"
            >
              <LogOut class="w-4 h-4 rotate-90" />
            </button>
          </div>
          
          <!-- 弹窗内容 -->
          <div class="flex-1 overflow-y-auto no-scrollbar p-6">
            <!-- 成功状态 -->
            <div v-if="withdrawSuccess" class="text-center py-12">
              <div class="w-16 h-16 bg-emerald-500/20 rounded-full flex items-center justify-center mx-auto mb-4 border border-emerald-500/30">
                <svg class="w-8 h-8 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <h4 class="text-lg font-bold text-white mb-2">提现申请已提交</h4>
              <p class="text-sm text-zinc-500">财务将在3个工作日内处理您的提现申请</p>
            </div>
            
            <!-- 提现表单 -->
            <div v-else class="space-y-6">
              <!-- 提现金额 -->
              <div class="glass-card rounded-2xl p-4">
                <p class="text-zinc-500 text-[10px] uppercase tracking-wider mb-2">可提现金额</p>
                <div class="flex items-baseline">
                  <span class="text-3xl font-bold text-blue-400">¥{{ withdrawAmount }}</span>
                  <span class="text-sm text-zinc-500 ml-1">元</span>
                </div>
                <p class="text-[10px] text-zinc-600 mt-2">
                  {{ Math.floor(lastMonthGold).toLocaleString() }} 金币 ÷ 1000 = ¥{{ withdrawAmount }}
                </p>
              </div>
              
              <!-- 支付宝账号 -->
              <div class="space-y-2">
                <label class="text-[10px] text-amber-400 uppercase tracking-wider">支付宝账号</label>
                <input 
                  v-model="alipayAccount"
                  type="text"
                  placeholder="请输入支付宝账号"
                  class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-amber-500/50 transition-all"
                />
              </div>
              
              <!-- 支付宝姓名 -->
              <div class="space-y-2">
                <label class="text-[10px] text-amber-400 uppercase tracking-wider">支付宝姓名</label>
                <input 
                  v-model="alipayName"
                  type="text"
                  placeholder="请输入支付宝实名姓名"
                  class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-amber-500/50 transition-all"
                />
              </div>
              
              <!-- 提示信息 -->
              <p class="text-[10px] text-amber-400/80 uppercase tracking-wider mt-4">
                提现申请将在3个工作日内处理，请确保支付宝信息准确无误
              </p>
            </div>
          </div>
          
          <!-- 弹窗底部 -->
          <div class="px-6 py-6 border-t border-white/5 sticky bottom-0 bg-[#020205]">
            <button 
              @click="submitWithdraw"
              :disabled="!alipayAccount || !alipayName || isSubmittingWithdraw || withdrawAmount <= 0"
              class="w-full font-bold py-4 rounded-xl transition-all text-white"
              :class="[!alipayAccount || !alipayName || isSubmittingWithdraw || withdrawAmount <= 0 ? 'bg-zinc-800 cursor-not-allowed' : 'bg-gradient-to-r from-blue-500 to-blue-600']"
            >
              {{ isSubmittingWithdraw ? '提交中...' : '提交提现申请' }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 提现记录弹窗 -->
    <transition name="modal">
      <div v-if="showWithdrawRecordsModal" class="fixed inset-0 z-[9999] flex items-end justify-center sm:items-center p-0 sm:p-6 pointer-events-auto">
        <div class="absolute inset-0 bg-black/80 backdrop-blur-md z-[9998] pointer-events-auto" @click="closeWithdrawRecordsModal" />
        <div class="relative w-full max-w-md bg-[#020205] border-t sm:border border-white/10 rounded-t-[3rem] sm:rounded-[3rem] overflow-hidden flex flex-col max-h-[85vh] z-[9999] shadow-2xl">
          <!-- 弹窗头部 -->
          <div class="px-8 py-6 border-b border-white/5 flex justify-between items-center sticky top-0 bg-[#020205] z-10">
            <div class="flex items-center">
              <div class="w-8 h-8 bg-blue-500/20 rounded-full flex items-center justify-center border border-blue-500/30 mr-3">
                <CreditCard class="w-4 h-4 text-blue-400" />
              </div>
              <h3 class="text-sm font-bold uppercase tracking-widest">提现记录</h3>
            </div>
            <button 
              @click="closeWithdrawRecordsModal"
              class="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-zinc-500 hover:text-white"
            >
              <LogOut class="w-4 h-4 rotate-90" />
            </button>
          </div>
          
          <!-- 弹窗内容 -->
          <div class="flex-1 overflow-y-auto no-scrollbar p-6">
            <!-- 加载状态 -->
            <div v-if="isLoadingWithdrawRecords" class="py-12 text-center">
              <div class="w-12 h-12 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4 animate-spin">
                <CreditCard class="w-5 h-5 text-zinc-700" />
              </div>
              <p class="text-xs text-zinc-600 uppercase tracking-widest">加载中...</p>
            </div>
            
            <!-- 空状态 -->
            <div v-else-if="withdrawRecords.length === 0" class="py-12 text-center">
              <div class="w-12 h-12 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4">
                <CreditCard class="w-5 h-5 text-zinc-700" />
              </div>
              <p class="text-xs text-zinc-600 uppercase tracking-widest">暂无提现记录</p>
            </div>
            
            <!-- 提现记录列表 -->
            <div v-else class="space-y-3">
              <div 
                v-for="record in withdrawRecords" 
                :key="record._id" 
                class="glass-card rounded-2xl p-4"
              >
                <div class="flex justify-between items-start mb-3">
                  <div class="flex-1">
                    <p class="text-sm font-bold text-white">{{ record.amount }} 元</p>
                    <p class="text-[10px] text-zinc-500 mt-1">{{ record.alipayAccount }} ({{ record.alipayName }})</p>
                  </div>
                  <div 
                    class="px-3 py-1 rounded-full text-[8px] font-bold tracking-wider border"
                    :class="getStatusStyle(record.status).class"
                  >
                    {{ getStatusStyle(record.status).text }}
                  </div>
                </div>
                <p class="text-[10px] text-zinc-600">{{ formatDate(record.createTime) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
/* 自定义样式 */
.no-scrollbar {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}

/* 弹窗动画 */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: translateY(30px);
}

/* 奖励弹窗动画 */
.reward-popup-enter-active,
.reward-popup-leave-active {
  transition: all 0.3s ease;
}

.reward-popup-enter-from,
.reward-popup-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

/* 玻璃卡片 */
.glass-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* 背景光晕动画 */
@keyframes shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

/* 自定义滚动条（隐藏） */
::-webkit-scrollbar {
  display: none;
}

/* 确保弹窗覆盖所有内容 */
.modal {
  z-index: 9999 !important;
}

/* 背景遮罩 */
.modal-backdrop {
  z-index: 9998 !important;
  background-color: rgba(0, 0, 0, 0.8) !important;
  backdrop-filter: blur(10px) !important;
}
</style>
