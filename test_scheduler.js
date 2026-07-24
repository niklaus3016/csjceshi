// 智能瀑布流模拟测试脚本（优化后）
// 运行方式：node test_scheduler.js

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
const MAX_EXPOSES_WITHOUT_FLOAT = 30;
const EXPIRY_HOURS = 24;

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

function checkAndCleanupExpiredState(state) {
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
}

function updateSchedulerStateOnHit(state, hitGroup) {
  const startIndex = GROUP_ORDER.indexOf(state.start_group);
  
  if (hitGroup) {
    const hitIndex = GROUP_ORDER.indexOf(hitGroup);
    
    if (hitGroup === state.start_group) {
      state.hit_streak += 1;
      console.log(`📈 连续命中起始分组计数: ${state.hit_streak}`);
      
      if (state.hit_streak >= 2 && state.start_group !== 'A') {
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
      
      if (state.start_group !== 'F') {
        state.start_group = GROUP_ORDER[startIndex + 1];
        console.log(`⬇️ 下沉到分组 ${state.start_group}`);
      }
    }
  } else {
    state.hit_streak = 0;
    console.log(`📉 全空，重置连续命中计数`);
    
    if (state.start_group !== 'F') {
      state.start_group = GROUP_ORDER[startIndex + 1];
      console.log(`⬇️ 下沉到分组 ${state.start_group}`);
    }
  }
}

let exposureIndex = 0;
let hitPattern = [];

function simulateAdRequest(startGroup) {
  if (exposureIndex >= hitPattern.length) {
    return { hit: false, group: null, slotId: null, ecpm: 0 };
  }
  
  const pattern = hitPattern[exposureIndex];
  exposureIndex++;
  
  if (pattern === 'NULL') {
    return { hit: false, group: null, slotId: null, ecpm: 0 };
  }
  
  const groupIndex = GROUP_ORDER.indexOf(pattern);
  if (groupIndex === -1) {
    return { hit: false, group: null, slotId: null, ecpm: 0 };
  }
  
  const slots = AD_GROUPS[pattern];
  return {
    hit: true,
    group: pattern,
    slotId: slots[0],
    ecpm: getEcpmForGroup(pattern)
  };
}

function getEcpmForGroup(group) {
  const ecpmMap = {
    'A': 1500,
    'B': 650,
    'C': 350,
    'D': 200,
    'E': 100,
    'F': 40
  };
  return ecpmMap[group] || 40;
}

function executeSmartWaterfall() {
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
  
  const result = simulateAdRequest(state.start_group);
  
  let hitGroup = result.hit ? result.group : null;
  console.log(result.hit 
    ? `🎉 分组 ${hitGroup} 命中，广告位：${result.slotId}，eCPM：${result.ecpm}` 
    : '❌ 本轮全空');
  
  if (!isFirstExpose) {
    updateSchedulerStateOnHit(state, hitGroup);
  } else {
    console.log('⚠️ 首次曝光，不更新调度状态');
  }
  
  saveSchedulerState(state);
  
  console.log(`📋 调度结束: start_group=${state.start_group}, hit_streak=${state.hit_streak}, expose_count=${state.expose_count}`);
  console.log('');
  
  return result;
}

function runTest(testName, pattern, expectedStates) {
  console.log(`\n\n========================================`);
  console.log(`🧪 测试场景: ${testName}`);
  console.log(`========================================`);
  console.log(`🎯 输入模式: ${pattern.join(' → ')}`);
  
  localStorage.clear();
  exposureIndex = 0;
  hitPattern = pattern;
  
  for (let i = 0; i < pattern.length; i++) {
    console.log(`\n--- 第 ${i + 1} 次曝光 ---`);
    executeSmartWaterfall();
  }
  
  const finalState = loadSchedulerState();
  console.log(`\n✅ 最终状态验证:`);
  console.log(`   start_group: ${finalState.start_group} (期望: ${expectedStates.start_group})`);
  console.log(`   hit_streak: ${finalState.hit_streak} (期望: ${expectedStates.hit_streak})`);
  console.log(`   expose_count: ${finalState.expose_count} (期望: ${expectedStates.expose_count})`);
  
  const passed = 
    finalState.start_group === expectedStates.start_group &&
    finalState.hit_streak === expectedStates.hit_streak &&
    finalState.expose_count === expectedStates.expose_count;
  
  console.log(`\n${passed ? '✅ 测试通过' : '❌ 测试失败'}`);
  return passed;
}

function testColdStart() {
  return runTest(
    '冷启动（首次曝光不计入调度）',
    ['A', 'A', 'A'],
    { start_group: 'A', hit_streak: 2, expose_count: 3 }
  );
}

function testFloatUp() {
  return runTest(
    '连续命中起始分组上浮（B→A）',
    ['B', 'B', 'B', 'B'],
    { start_group: 'A', hit_streak: 0, expose_count: 4 }
  );
}

function testSinkDown() {
  return runTest(
    '全空下沉（A→B→C）',
    ['NULL', 'NULL', 'NULL'],
    { start_group: 'C', hit_streak: 0, expose_count: 3 }
  );
}

function testSinkToF() {
  return runTest(
    '全空下沉到F（首次全空→A→B→C→D→E→F）',
    ['NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL'],
    { start_group: 'F', hit_streak: 0, expose_count: 8 }
  );
}

function testBoundaryProtectionA() {
  return runTest(
    '边界保护（A组命中不上浮）',
    ['A', 'A', 'A'],
    { start_group: 'A', hit_streak: 2, expose_count: 3 }
  );
}

function testBoundaryProtectionF() {
  return runTest(
    '边界保护（F组全空不下沉）',
    ['NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL'],
    { start_group: 'F', hit_streak: 0, expose_count: 9 }
  );
}

function testHitHigherValueGroupAfterSink() {
  return runTest(
    '命中更高价值分组（全空下沉到C→命中A→下次从A开始）',
    ['NULL', 'NULL', 'A'],
    { start_group: 'A', hit_streak: 0, expose_count: 3 }
  );
}

function testHitHigherValueGroupAfterMultipleSinks() {
  return runTest(
    '命中更高价值分组（全空下沉到D→命中B→下次从B开始）',
    ['NULL', 'NULL', 'NULL', 'B'],
    { start_group: 'B', hit_streak: 0, expose_count: 4 }
  );
}

function testHitLowerValueGroupFromCToD() {
  return runTest(
    '命中更低价值分组（首次命中C→再次命中D→下沉到B）',
    ['C', 'D'],
    { start_group: 'B', hit_streak: 0, expose_count: 2 }
  );
}

function testHitLowerValueGroupFromCToE() {
  return runTest(
    '命中更低价值分组（首次命中C→再次命中E→下沉到B）',
    ['C', 'E'],
    { start_group: 'B', hit_streak: 0, expose_count: 2 }
  );
}

function testHitHigherValueGroupFromDToA() {
  return runTest(
    '命中更高价值分组（全空下沉到D→命中A→下次从A开始）',
    ['NULL', 'NULL', 'NULL', 'A'],
    { start_group: 'A', hit_streak: 0, expose_count: 4 }
  );
}

function testHitHigherValueGroupFromEToB() {
  return runTest(
    '命中更高价值分组（全空下沉到E→命中B→下次从B开始）',
    ['NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'B'],
    { start_group: 'B', hit_streak: 0, expose_count: 6 }
  );
}

function testHitNonStartGroupContinue() {
  return runTest(
    '连续命中更低价值分组逐级下沉（起始A→命中E→B→命中E→C）',
    ['E', 'E', 'E'],
    { start_group: 'C', hit_streak: 0, expose_count: 3 }
  );
}

function testSingleHitStartGroup() {
  return runTest(
    '单次命中起始分组不满足上浮',
    ['B', 'B', 'B'],
    { start_group: 'B', hit_streak: 1, expose_count: 3 }
  );
}

function testConsecutiveHitsUp() {
  return runTest(
    '连续命中起始分组逐级上浮（C→B→A）',
    ['C', 'C', 'C', 'C', 'C', 'B', 'B', 'A', 'A'],
    { start_group: 'A', hit_streak: 2, expose_count: 9 }
  );
}

function testSinkThenFloat() {
  return runTest(
    '先下沉后上浮',
    ['NULL', 'NULL', 'C', 'C', 'C'],
    { start_group: 'B', hit_streak: 0, expose_count: 5 }
  );
}

function testExpiryAfter24Hours() {
  localStorage.clear();
  const state = new SchedulerState();
  state.start_group = 'C';
  state.hit_streak = 5;
  state.expose_count = 10;
  state.last_expose_time = Date.now() - 25 * 3600 * 1000;
  saveSchedulerState(state);
  
  exposureIndex = 0;
  hitPattern = ['A'];
  
  console.log(`\n\n========================================`);
  console.log(`🧪 测试场景: 24小时过期清理`);
  console.log(`========================================`);
  
  executeSmartWaterfall();
  
  const finalState = loadSchedulerState();
  console.log(`\n✅ 最终状态验证:`);
  console.log(`   start_group: ${finalState.start_group} (期望: A)`);
  console.log(`   hit_streak: ${finalState.hit_streak} (期望: 1)`);
  
  const passed = finalState.start_group === 'A' && finalState.hit_streak === 1;
  console.log(`\n${passed ? '✅ 测试通过' : '❌ 测试失败'}`);
  return passed;
}

function testExpiryAfter30Exposes() {
  localStorage.clear();
  const state = new SchedulerState();
  state.start_group = 'D';
  state.hit_streak = 3;
  state.expose_count = 30;
  state.last_expose_time = Date.now();
  saveSchedulerState(state);
  
  exposureIndex = 0;
  hitPattern = ['NULL'];
  
  console.log(`\n\n========================================`);
  console.log(`🧪 测试场景: 30次曝光过期清理`);
  console.log(`========================================`);
  
  executeSmartWaterfall();
  
  const finalState = loadSchedulerState();
  console.log(`\n✅ 最终状态验证:`);
  console.log(`   start_group: ${finalState.start_group} (期望: B)`);
  console.log(`   hit_streak: ${finalState.hit_streak} (期望: 0)`);
  
  const passed = finalState.start_group === 'B' && finalState.hit_streak === 0;
  console.log(`\n${passed ? '✅ 测试通过' : '❌ 测试失败'}`);
  return passed;
}

function testFloatFromDToC() {
  return runTest(
    'D组连续命中上浮到C',
    ['D', 'D', 'D', 'D', 'D', 'D'],
    { start_group: 'C', hit_streak: 0, expose_count: 6 }
  );
}

function testHitAtAThenNonA() {
  return runTest(
    'A组命中后非起始分组命中',
    ['A', 'A', 'B'],
    { start_group: 'B', hit_streak: 0, expose_count: 3 }
  );
}

function testMultipleSinksThenFloat() {
  return runTest(
    '多次下沉后命中上浮',
    ['NULL', 'NULL', 'NULL', 'D', 'D', 'D'],
    { start_group: 'C', hit_streak: 0, expose_count: 6 }
  );
}

function testFGroupBoundary() {
  return runTest(
    'F组边界：命中F组保持，连续命中上浮',
    ['NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'NULL', 'F', 'F'],
    { start_group: 'E', hit_streak: 0, expose_count: 8 }
  );
}

function testHigherValueGroupChain() {
  return runTest(
    '连续命中更高价值分组跳转（下沉到D→命中B→下沉到E→命中A）',
    ['NULL', 'NULL', 'NULL', 'B', 'NULL', 'NULL', 'NULL', 'NULL', 'A'],
    { start_group: 'A', hit_streak: 0, expose_count: 9 }
  );
}

function runAllTests() {
  console.log('🚀 开始运行智能瀑布流模拟测试\n');
  
  const tests = [
    testColdStart,
    testFloatUp,
    testSinkDown,
    testSinkToF,
    testBoundaryProtectionA,
    testBoundaryProtectionF,
    testHitHigherValueGroupAfterSink,
    testHitHigherValueGroupAfterMultipleSinks,
    testHitLowerValueGroupFromCToD,
    testHitLowerValueGroupFromCToE,
    testHitHigherValueGroupFromDToA,
    testHitHigherValueGroupFromEToB,
    testHitNonStartGroupContinue,
    testSingleHitStartGroup,
    testConsecutiveHitsUp,
    testSinkThenFloat,
    testExpiryAfter24Hours,
    testExpiryAfter30Exposes,
    testFloatFromDToC,
    testHitAtAThenNonA,
    testMultipleSinksThenFloat,
    testFGroupBoundary,
    testHigherValueGroupChain
  ];
  
  let passedCount = 0;
  tests.forEach((test, index) => {
    const passed = test();
    if (passed) passedCount++;
    console.log('\n'.repeat(2));
  });
  
  console.log(`========================================`);
  console.log(`📊 测试结果: ${passedCount}/${tests.length} 通过`);
  console.log(`========================================`);
}

runAllTests();