// 验证所有场景的测试脚本
// 运行方式：node test_all_scenarios.js

const localStorageMock = {
  data: {},
  getItem: function(key) { return this.data[key] || null; },
  setItem: function(key, value) { this.data[key] = value; },
  clear: function() { this.data = {}; }
};

global.localStorage = localStorageMock;

const GROUP_ORDER = ['A', 'B', 'C', 'D', 'E', 'F'];
const SCHEDULER_STATE_KEY = 'ad_scheduler_state';

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
      if (state.start_group !== 'F') {
        state.start_group = GROUP_ORDER[startIndex + 1];
      }
    }
  } else {
    state.hit_streak = 0;
    if (state.start_group !== 'F') {
      state.start_group = GROUP_ORDER[startIndex + 1];
    }
  }
}

function testScenario(name, initialState, hitGroup, expectedState) {
  console.log(`\n🧪 ${name}`);
  console.log(`   初始状态: start_group=${initialState.start_group}, hit_streak=${initialState.hit_streak}`);
  console.log(`   命中分组: ${hitGroup || '全空'}`);
  
  const state = { ...initialState };
  updateSchedulerStateOnHit(state, hitGroup);
  
  const passed = 
    state.start_group === expectedState.start_group &&
    state.hit_streak === expectedState.hit_streak;
  
  console.log(`   实际结果: start_group=${state.start_group}, hit_streak=${state.hit_streak}`);
  console.log(`   期望结果: start_group=${expectedState.start_group}, hit_streak=${expectedState.hit_streak}`);
  console.log(`   ${passed ? '✅ 通过' : '❌ 失败'}`);
  
  return passed;
}

function runAllScenarios() {
  console.log('🚀 开始验证所有场景\n');
  
  let passedCount = 0;
  let totalCount = 0;
  
  console.log('='.repeat(60));
  console.log('场景1：起始分组=C，验证各种命中情况');
  console.log('='.repeat(60));
  
  // 起始分组=C，命中C（起始分组）
  totalCount++;
  passedCount += testScenario(
    '起始C，命中C → 连续命中+1',
    { start_group: 'C', hit_streak: 0 },
    'C',
    { start_group: 'C', hit_streak: 1 }
  );
  
  // 起始分组=C，命中D（更低价值）
  totalCount++;
  passedCount += testScenario(
    '起始C，命中D → 下沉到D',
    { start_group: 'C', hit_streak: 0 },
    'D',
    { start_group: 'D', hit_streak: 0 }
  );
  
  // 起始分组=C，命中E（更低价值）
  totalCount++;
  passedCount += testScenario(
    '起始C，命中E → 下沉到D',
    { start_group: 'C', hit_streak: 0 },
    'E',
    { start_group: 'D', hit_streak: 0 }
  );
  
  // 起始分组=C，命中F（更低价值）
  totalCount++;
  passedCount += testScenario(
    '起始C，命中F → 下沉到D',
    { start_group: 'C', hit_streak: 0 },
    'F',
    { start_group: 'D', hit_streak: 0 }
  );
  
  // 起始分组=C，全空
  totalCount++;
  passedCount += testScenario(
    '起始C，全空 → 下沉到D',
    { start_group: 'C', hit_streak: 0 },
    null,
    { start_group: 'D', hit_streak: 0 }
  );
  
  console.log('\n' + '='.repeat(60));
  console.log('场景2：起始分组=A，验证各种命中情况');
  console.log('='.repeat(60));
  
  // 起始分组=A，命中A
  totalCount++;
  passedCount += testScenario(
    '起始A，命中A → 连续命中+1',
    { start_group: 'A', hit_streak: 0 },
    'A',
    { start_group: 'A', hit_streak: 1 }
  );
  
  // 起始分组=A，连续命中2次
  totalCount++;
  passedCount += testScenario(
    '起始A，命中A(连续2次) → A组不上浮',
    { start_group: 'A', hit_streak: 1 },
    'A',
    { start_group: 'A', hit_streak: 2 }
  );
  
  // 起始分组=A，命中B
  totalCount++;
  passedCount += testScenario(
    '起始A，命中B → 下沉到B',
    { start_group: 'A', hit_streak: 0 },
    'B',
    { start_group: 'B', hit_streak: 0 }
  );
  
  console.log('\n' + '='.repeat(60));
  console.log('场景3：连续命中上浮验证');
  console.log('='.repeat(60));
  
  // 起始分组=B，连续命中2次
  totalCount++;
  passedCount += testScenario(
    '起始B，命中B(连续2次) → 上浮到A',
    { start_group: 'B', hit_streak: 1 },
    'B',
    { start_group: 'A', hit_streak: 0 }
  );
  
  // 起始分组=C，连续命中2次
  totalCount++;
  passedCount += testScenario(
    '起始C，命中C(连续2次) → 上浮到B',
    { start_group: 'C', hit_streak: 1 },
    'C',
    { start_group: 'B', hit_streak: 0 }
  );
  
  console.log('\n' + '='.repeat(60));
  console.log('场景4：F组边界验证');
  console.log('='.repeat(60));
  
  // 起始分组=F，命中F
  totalCount++;
  passedCount += testScenario(
    '起始F，命中F → 连续命中+1',
    { start_group: 'F', hit_streak: 0 },
    'F',
    { start_group: 'F', hit_streak: 1 }
  );
  
  // 起始分组=F，全空
  totalCount++;
  passedCount += testScenario(
    '起始F，全空 → F组不下沉',
    { start_group: 'F', hit_streak: 0 },
    null,
    { start_group: 'F', hit_streak: 0 }
  );
  
  // 起始分组=F，连续命中2次
  totalCount++;
  passedCount += testScenario(
    '起始F，命中F(连续2次) → 上浮到E',
    { start_group: 'F', hit_streak: 1 },
    'F',
    { start_group: 'E', hit_streak: 0 }
  );
  
  console.log('\n' + '='.repeat(60));
  console.log('场景5：极端边界情况（理论上不会发生）');
  console.log('='.repeat(60));
  
  // 起始分组=C，命中A（更高价值）- 理论上不会发生
  totalCount++;
  passedCount += testScenario(
    '起始C，命中A(极端情况) → 跳转到A',
    { start_group: 'C', hit_streak: 0 },
    'A',
    { start_group: 'A', hit_streak: 0 }
  );
  
  // 起始分组=D，命中B（更高价值）- 理论上不会发生
  totalCount++;
  passedCount += testScenario(
    '起始D，命中B(极端情况) → 跳转到B',
    { start_group: 'D', hit_streak: 0 },
    'B',
    { start_group: 'B', hit_streak: 0 }
  );
  
  console.log('\n' + '='.repeat(60));
  console.log(`📊 测试结果: ${passedCount}/${totalCount} 通过`);
  console.log('='.repeat(60));
  
  return passedCount === totalCount;
}

runAllScenarios();