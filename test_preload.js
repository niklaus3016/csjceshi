// 智能预加载完整流程模拟测试脚本（优化后）
// 运行方式：node test_preload.js

const localStorageMock = {
  data: {},
  getItem: function(key) {
    return this.data[key] || null;
  },
  setItem: function(key, value) {
    this.data[key] = value;
  },
  clear: function() {
    this.data = {};
  }
};

global.localStorage = localStorageMock;

const AD_GROUPS = {
  A: ['19987128', '19987133', '19987142'],
  B: ['19987151', '19987153', '19987156'],
  C: ['19987162', '19987165', '19987173'],
  D: ['19987186', '19987188', '19987193'],
  E: ['19987198', '19987202', '19987205'],
  F: ['19987207', '19987214', '19987220']
};

const GROUP_ORDER = ['A', 'B', 'C', 'D', 'E', 'F'];
const SCHEDULER_STATE_KEY = 'ad_scheduler_state';

class SchedulerState {
  constructor() {
    this.start_group = 'A';
    this.hit_streak = 0;
    this.expose_count = 0;
    this.last_expose_time = Date.now();
  }
}

function loadSchedulerState() {
  try {
    const stored = localStorage.getItem(SCHEDULER_STATE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored);
      const state = new SchedulerState();
      state.start_group = parsed.start_group || 'A';
      state.hit_streak = parsed.hit_streak || 0;
      state.expose_count = parsed.expose_count || 0;
      state.last_expose_time = parsed.last_expose_time || Date.now();
      return state;
    }
  } catch (e) {
    console.error('加载调度器状态失败:', e);
  }
  return new SchedulerState();
}

function saveSchedulerState(state) {
  try {
    localStorage.setItem(SCHEDULER_STATE_KEY, JSON.stringify(state));
  } catch (e) {
    console.error('保存调度器状态失败:', e);
  }
}

function findGroupBySlotId(slotId) {
  for (const [group, slots] of Object.entries(AD_GROUPS)) {
    if (slots.includes(slotId)) {
      return group;
    }
  }
  return null;
}

function updateSchedulerStateOnHit(state, hitGroup) {
  const startIndex = GROUP_ORDER.indexOf(state.start_group);
  
  if (hitGroup) {
    const hitIndex = GROUP_ORDER.indexOf(hitGroup);
    
    if (hitGroup === state.start_group) {
      state.hit_streak += 1;
      if (state.hit_streak >= 2 && state.start_group !== 'A') {
        state.start_group = GROUP_ORDER[startIndex - 1];
        state.hit_streak = 0;
      }
    } else if (hitIndex < startIndex) {
      state.start_group = hitGroup;
      state.hit_streak = 0;
    } else {
      state.hit_streak = 0;
      if (state.start_group !== 'E') {
        state.start_group = GROUP_ORDER[startIndex + 1];
      }
    }
  } else {
    state.hit_streak = 0;
    if (state.start_group !== 'E') {
      state.start_group = GROUP_ORDER[startIndex + 1];
    }
  }
}

function simulatePreload(startGroup, hitPattern) {
  let startIndex = GROUP_ORDER.indexOf(startGroup);
  if (startIndex === -1) startIndex = 0;
  
  for (let i = startIndex; i < GROUP_ORDER.length; i++) {
    const group = GROUP_ORDER[i];
    const slots = AD_GROUPS[group];
    
    for (const slotId of slots) {
      if (hitPattern.includes(slotId)) {
        return { slotId, group };
      }
    }
  }
  return null;
}

function updateSchedulerStateAfterPlaySuccess(slotId) {
  const state = loadSchedulerState();
  state.expose_count += 1;
  state.last_expose_time = Date.now();
  const isFirstExpose = state.expose_count === 1;
  
  if (!isFirstExpose) {
    const hitGroup = findGroupBySlotId(slotId);
    updateSchedulerStateOnHit(state, hitGroup);
  }
  
  saveSchedulerState(state);
  return {
    currentState: state,
    isFirstExpose: isFirstExpose,
    hitGroup: findGroupBySlotId(slotId)
  };
}

async function runTestScenario(scenarioName, hitPatterns) {
  console.log(`\n${'='.repeat(60)}`);
  console.log(`🧪 测试场景: ${scenarioName}`);
  console.log(`命中模式: ${hitPatterns.join(' → ')}`);
  console.log('='.repeat(60));
  
  localStorage.clear();
  
  let preloadedSlot = null;
  let successCount = 0;
  
  for (let i = 0; i < hitPatterns.length; i++) {
    const hitSlot = hitPatterns[i];
    
    console.log(`\n--- 第 ${i + 1} 次广告请求 ---`);
    
    if (preloadedSlot) {
      console.log(`🚀 使用预加载广告: ${preloadedSlot.slotId} (分组${preloadedSlot.group})`);
      preloadedSlot = null;
    } else {
      console.log(`🔄 无预加载广告，开始预加载...`);
      const state = loadSchedulerState();
      preloadedSlot = simulatePreload(state.start_group, [hitSlot]);
      
      if (preloadedSlot) {
        console.log(`✅ 预加载成功: ${preloadedSlot.slotId} (分组${preloadedSlot.group})`);
      } else {
        console.log(`❌ 预加载失败`);
        continue;
      }
    }
    
    console.log(`📺 播放广告: ${hitSlot}`);
    const updateResult = updateSchedulerStateAfterPlaySuccess(hitSlot);
    const state = updateResult.currentState;
    
    let statusText = '';
    if (updateResult.isFirstExpose) {
      statusText = `⚠️ 首次曝光，不更新调度状态`;
    } else {
      const hitGroup = updateResult.hitGroup;
      const startIndex = GROUP_ORDER.indexOf(state.start_group);
      const hitIndex = GROUP_ORDER.indexOf(hitGroup);
      
      if (hitGroup === state.start_group) {
        statusText = state.hit_streak >= 2 && state.start_group !== 'A'
          ? `📈 命中起始分组(${state.start_group})，连续命中=${state.hit_streak}，上浮`
          : `📈 命中起始分组(${state.start_group})，连续命中=${state.hit_streak}`;
      } else if (hitIndex < startIndex) {
        statusText = `⬆️ 命中更高价值分组(${hitGroup})，跳转到${hitGroup}`;
      } else {
        statusText = `📉 命中更低价值分组(${hitGroup})，下沉到${state.start_group}`;
      }
    }
    
    console.log(`📊 状态: start_group=${state.start_group}, hit_streak=${state.hit_streak}, expose_count=${state.expose_count}`);
    console.log(statusText);
    successCount++;
    
    console.log(`🔄 广告播放成功，触发智能预加载...`);
    const nextPreload = simulatePreload(state.start_group, hitPatterns.slice(i + 1));
    
    if (nextPreload) {
      preloadedSlot = nextPreload;
      console.log(`✅ 智能预加载完成，已预加载: ${preloadedSlot.slotId} (分组${preloadedSlot.group})`);
    } else {
      console.log(`❌ 智能预加载未找到可用广告`);
    }
  }
  
  console.log(`\n✅ ${scenarioName} 测试完成（成功${successCount}/${hitPatterns.length}）`);
  return successCount === hitPatterns.length;
}

async function runAllTests() {
  console.log('🚀 开始运行智能预加载模拟测试\n');
  
  await runTestScenario('场景1: 连续命中起始分组上浮', [
    '19987133', 
    '19987133', 
    '19987133', 
    '19987133', 
    '19987205', 
  ]);
  
  await runTestScenario('场景2: 命中更高价值分组跳转', [
    '19987186', 
    '19987133', 
    '19987205', 
  ]);
  
  await runTestScenario('场景3: 命中更低价值分组下沉', [
    '19987133', 
    '19987186', 
    '19987198', 
  ]);
  
  await runTestScenario('场景4: 冷启动+命中更高价值分组', [
    '19987162', 
    '19987205', 
    '19987142', 
  ]);
  
  console.log(`\n${'='.repeat(60)}`);
  console.log('🎉 所有测试场景完成！');
  console.log('='.repeat(60));
}

runAllTests();