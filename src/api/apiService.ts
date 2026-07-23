// API服务层，封装后端接口调用

const API_BASE_URL = 'https://wfqmaepvjkdd.sealoshzh.site'; // 生产环境后端服务地址
const USE_MOCK_DATA = false; // 生产模式下使用真实后端API

interface ApiResponse<T = any> {
  success: boolean;
  message?: string;
  data?: T;
}

interface EmployeeInfo {
  _id: string;
  userId: string;
  employeeId: string;
  name: string;
  phone: string;
  area: string;
  status: number;
  __v: number;
}

interface UserInfo {
  userId: string;
  employeeId: string;
  currentMonthGold: number;
  lastMonthGold: number;
  todayTarget: number;
  bonusGold: number;
  hasClaimedBonus: boolean;
  _id: string;
  __v: number;
}

interface GoldReward {
  gold: number;
  currentMonthGold: number;
}

interface GoldLog {
  _id: string;
  userId: string;
  employeeId: string;
  ecpm: number;
  gold: number;
  createTime: string;
  __v: number;
}

/**
 * 员工登录校验接口
 * @param employeeId 员工号
 * @returns 员工信息或错误信息
 */
export async function checkEmployee(employeeId: string): Promise<ApiResponse<EmployeeInfo>> {
  // 开发模式下使用模拟数据
  if (USE_MOCK_DATA) {
    return new Promise((resolve) => {
      // 模拟网络延迟
      setTimeout(() => {
        // 模拟员工号验证逻辑
        if (employeeId === '8202') {
          // 测试员工，登录成功
          resolve({
            success: true,
            message: '登录成功',
            data: {
              _id: '699c87e89ad7757d16c8b9e1',
              userId: 'test123',
              employeeId: '8202',
              name: '测试员工',
              phone: '13800138000',
              area: '北京',
              status: 1,
              __v: 0
            }
          });
        } else if (employeeId === '1111') {
          // 测试员工，登录成功
          resolve({
            success: true,
            message: '登录成功',
            data: {
              _id: '699c87e89ad7757d16c8b9e2',
              userId: 'user_1111',
              employeeId: '1111',
              name: '测试员工1111',
              phone: '13800138001',
              area: '上海',
              status: 1,
              __v: 0
            }
          });
        } else {
          // 员工号不存在
          resolve({
            success: false,
            message: '员工号不存在或已禁用'
          });
        }
      }, 1000);
    });
  }

  // 生产模式下调用真实后端API
  try {
    // 快速检查后端服务是否可用
    const pingController = new AbortController();
    const pingTimeout = setTimeout(() => pingController.abort(), 2000); // 2秒超时
    
    try {
      const pingResponse = await fetch(`${API_BASE_URL}`, {
        method: 'GET',
        signal: pingController.signal,
      });
      clearTimeout(pingTimeout);
      
      if (!pingResponse.ok) {
        throw new Error('后端服务不可用');
      }
    } catch (pingError) {
      clearTimeout(pingTimeout);
      console.error('后端服务检查失败:', pingError);
      return {
        success: false,
        message: '后端服务未运行，请启动后端服务后重试',
      };
    }

    // 添加超时设置
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000); // 5秒超时

    const response = await fetch(`${API_BASE_URL}/api/employee/check`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ employeeId }),
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    // 检查响应状态
    if (!response.ok) {
      console.error('登录接口响应错误:', response.status, response.statusText);
      return {
        success: false,
        message: `服务器响应错误: ${response.statusText}`,
      };
    }

    // 尝试解析响应体
    try {
      const data = await response.json();
      return data;
    } catch (jsonError) {
      console.error('解析响应体失败:', jsonError);
      return {
        success: false,
        message: '服务器返回的数据格式错误',
      };
    }
  } catch (error) {
    console.error('员工登录校验失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

/**
 * 获取金币信息接口
 * @param userId 用户ID
 * @param employeeId 员工号
 * @returns 用户金币信息
 */
export async function getUserInfo(userId: string, employeeId: string): Promise<ApiResponse<UserInfo>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/user/info?userId=${userId}&employeeId=${employeeId}`);
    return await response.json();
  } catch (error) {
    console.error('获取金币信息失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

/**
 * 发放金币接口
 * @param userId 用户ID
 * @param employeeId 员工号
 * @param ecpm 广告ECPM值
 * @param slotId 广告位ID
 * @returns 发放的金币数量和当前月金币总数
 */
export async function rewardGold(userId: string, employeeId: string, ecpm: number, slotId: string): Promise<ApiResponse<GoldReward>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/gold/reward`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId, employeeId, ecpm, slotId }),
    });
    return await response.json();
  } catch (error) {
    console.error('发放金币失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

/**
 * 获取金币记录接口
 * @param userId 用户ID
 * @param limit 限制数量，默认200
 * @returns 金币发放记录列表
 */
export async function getGoldLogs(userId: string, limit: number = 200): Promise<ApiResponse<GoldLog[]>> {
  try {
    console.log('🔧 API - getGoldLogs 开始');
    console.log('   URL:', `${API_BASE_URL}/api/gold/log?userId=${userId}&limit=${limit}`);
    
    // 添加超时机制
    const controller = new AbortController();
    const timeoutId = setTimeout(() => {
      console.warn('⚠️ API请求超时，正在中止...');
      controller.abort();
    }, 10000); // 10秒超时
    
    console.log('   发送请求...');
    const response = await fetch(`${API_BASE_URL}/api/gold/log?userId=${userId}&limit=${limit}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-cache'
      },
      signal: controller.signal
    });
    
    clearTimeout(timeoutId);
    
    console.log('   收到响应:', {
      status: response.status,
      statusText: response.statusText
    });
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    console.log('   解析响应数据...');
    const data = await response.json();
    console.log('   响应数据:', {
      success: data.success,
      message: data.message,
      dataLength: data.data ? data.data.length : 0
    });
    
    console.log('✅ API - getGoldLogs 完成');
    return data;
  } catch (error) {
    console.error('❌ API - getGoldLogs 失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
      data: [] // 返回空数组，确保前端有默认值
    };
  }
}

/**
 * 添加员工接口（用于测试）
 * @param name 员工姓名
 * @param phone 员工电话
 * @param area 员工地区
 * @returns 新添加的员工信息
 */
export async function addEmployee(name: string, phone: string, area: string): Promise<ApiResponse<EmployeeInfo>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/employee/add`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ name, phone, area }),
    });
    return await response.json();
  } catch (error) {
    console.error('添加员工失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

// 登录统计相关接口
interface LoginRecord {
  loginDays: number;
  todayLogin: boolean;
  isNewLogin: boolean;
  consecutiveDays: number;
}

interface LoginStats {
  totalLoginDays: number;
  firstLoginDate: string;
  lastLoginDate: string;
  consecutiveDays: number;
  loginDates: string[];
}

/**
 * 记录用户登录
 * @param userId 用户ID
 * @param employeeId 员工号
 * @returns 登录记录结果
 */
export async function recordLogin(userId: string, employeeId: string): Promise<ApiResponse<LoginRecord>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/user/login-record`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId, employeeId }),
    });
    return await response.json();
  } catch (error) {
    console.error('记录登录失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

/**
 * 获取用户登录统计
 * @param userId 用户ID
 * @param employeeId 员工号
 * @returns 登录统计信息
 */
export async function getLoginStats(userId: string, employeeId: string): Promise<ApiResponse<LoginStats>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/user/login-stats?userId=${userId}&employeeId=${employeeId}`);
    return await response.json();
  } catch (error) {
    console.error('获取登录统计失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

// 提现相关接口
interface WithdrawRequest {
  userId: string;
  employeeId: string;
  amount: number;
  goldAmount: number;
  alipayAccount: string;
  alipayName: string;
}

export interface WithdrawRecord {
  _id: string;
  userId: string;
  employeeId: string;
  amount: number;
  goldAmount: number;
  alipayAccount: string;
  alipayName: string;
  status: number; // 0:提现成功（简化逻辑，提交即成功）
  statusText: string;
  createTime: string;
  remainingGold?: number; // 提现后剩余金币
}

/**
 * 提交提现申请
 * @param data 提现申请数据
 * @returns 提现申请结果
 */
export async function submitWithdrawRequest(data: WithdrawRequest): Promise<ApiResponse<WithdrawRecord>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/withdraw/submit`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });
    return await response.json();
  } catch (error) {
    console.error('提交提现申请失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

/**
 * 获取提现记录列表
 * @param userId 用户ID
 * @returns 提现记录列表
 */
export async function getWithdrawRecords(userId: string): Promise<ApiResponse<WithdrawRecord[]>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/withdraw/list?userId=${userId}`);
    return await response.json();
  } catch (error) {
    console.error('获取提现记录失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

/**
 * 领取今日目标额外金币奖励
 * @param userId 用户ID
 * @param employeeId 员工号
 * @returns 领取结果
 */
export async function claimDailyBonus(userId: string, employeeId: string): Promise<ApiResponse<{ gold: number; currentMonthGold: number }>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/daily-bonus/claim`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId, employeeId }),
    });
    return await response.json();
  } catch (error) {
    console.error('领取额外金币失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

// 提现开关状态
interface WithdrawStatus {
  enabled: boolean;
  message?: string;
}

/**
 * 获取提现开关状态
 * @returns 提现开关状态
 */
export async function getWithdrawStatus(): Promise<ApiResponse<WithdrawStatus>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/settings/withdraw-status`);
    return await response.json();
  } catch (error) {
    console.error('获取提现状态失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}

// 活动记录相关接口
interface ActivityRecord {
  userId: string;
  employeeId: string;
  deviceId: string;
  ip: string;
}

/**
 * 记录用户活动
 * @param userId 用户ID
 * @param employeeId 员工号
 * @param deviceId 设备ID
 * @returns 活动记录结果
 */
export async function recordActivity(userId: string, employeeId: string, deviceId: string): Promise<ApiResponse<ActivityRecord>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/activity/record`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId, employeeId, deviceId }),
    });
    return await response.json();
  } catch (error) {
    console.error('记录活动失败:', error);
    return {
      success: false,
      message: '网络错误，请稍后重试',
    };
  }
}
