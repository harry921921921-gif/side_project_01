// 模擬 WorkoutPlanService.accessoryPoolFor(user, dayDef, mainNames, week) 的旋轉演算法，
// 用真實的「拉日」動作清單（跟 Exercise 表 seed 資料一致），印出「拉A/拉B × 第1/2/3週」的配件池，
// 證明同一天型態換週、換 A/B 都會排出不同的配件順序，但主項（硬舉）完全不受影響。
//
// 跑法：node rotation_simulation.js

// ── 跟 ExerciseDataInitializer 一致的「拉」(PULL movement) 動作清單 ──
const PULL_COMPOUND = ['硬舉', '引體向上', '滑輪下拉', '正手滑輪下拉', '槓鈴划船', '坐姿划船', '啞鈴單臂划船', 'T槓划船'];
const PULL_ISOLATION = ['直臂下壓', '反向飛鳥', '面拉', '二頭彎舉', '啞鈴彎舉', '錘式彎舉', '斜托彎舉', '繩索彎舉', '集中彎舉'];

const MAIN_NAMES = ['硬舉']; // 拉A、拉B 的主項——旋轉不應該動到這個

// ── 跟 WorkoutPlanService.rotate() 逐行對應 ──
function rotate(list, weekOffset, addHalfTurnForB) {
  const n = list.length;
  if (n === 0) return list;
  const offset = weekOffset + (addHalfTurnForB ? Math.floor(n / 2) : 0);
  const shift = ((offset % n) + n) % n;
  if (shift === 0) return list.slice();
  const rotated = [];
  for (let i = 0; i < n; i++) rotated.push(list[(i + shift) % n]);
  return rotated;
}

// ── 跟 WorkoutPlanService.accessoryPoolFor(user, dayDef, mainNames, week) 逐行對應（假設本週還沒練過任何配件） ──
function accessoryPoolFor(dayName, week, usedThisWeek = new Set()) {
  const compounds = PULL_COMPOUND.filter(n => !MAIN_NAMES.includes(n));
  const isolations = PULL_ISOLATION.filter(n => !MAIN_NAMES.includes(n));

  const weekOffset = Math.max(week, 1) - 1;
  const isB = dayName.endsWith('B');

  const pool = [...rotate(compounds, weekOffset, isB), ...rotate(isolations, weekOffset, isB)];

  const preferred = pool.filter(n => !usedThisWeek.has(n));
  return preferred.length === 0 ? pool : preferred;
}

function composeDay(dayName, week) {
  return { dayName, mainNames: MAIN_NAMES, accessoryPool: accessoryPoolFor(dayName, week) };
}

// ── 印出證明 ──
console.log('='.repeat(70));
console.log('拉A / 拉B × 第1/2/3週 —— 配件輪替證明');
console.log('='.repeat(70));

const results = {};
for (const week of [1, 2, 3]) {
  for (const day of ['拉 A', '拉 B']) {
    const dc = composeDay(day, week);
    results[`${day}-w${week}`] = dc;
    console.log(`\n【${day}｜第 ${week} 週】`);
    console.log('  主項：', dc.mainNames.join('、'));
    console.log('  配件：', dc.accessoryPool.join(' → '));
  }
}

console.log('\n' + '='.repeat(70));
console.log('比對結果');
console.log('='.repeat(70));

function assertDiff(labelA, labelB) {
  const a = results[labelA].accessoryPool.join(',');
  const b = results[labelB].accessoryPool.join(',');
  const same = a === b;
  console.log(`  ${labelA}  vs  ${labelB}  →  ${same ? '❌ 一樣（不應該）' : '✅ 不同'}`);
  return !same;
}
function assertSame(labelA, labelB) {
  const a = results[labelA].mainNames.join(',');
  const b = results[labelB].mainNames.join(',');
  const same = a === b;
  console.log(`  ${labelA} 主項  vs  ${labelB} 主項  →  ${same ? '✅ 一樣（正確，進階追蹤穩定）' : '❌ 不同（不應該）'}`);
  return same;
}

let allPass = true;
allPass &= assertDiff('拉 A-w1', '拉 A-w2');
allPass &= assertDiff('拉 A-w2', '拉 A-w3');
allPass &= assertDiff('拉 A-w1', '拉 A-w3');
allPass &= assertDiff('拉 A-w1', '拉 B-w1');
allPass &= assertDiff('拉 A-w2', '拉 B-w2');
allPass &= assertDiff('拉 B-w1', '拉 B-w2');
allPass &= assertSame('拉 A-w1', '拉 B-w1');
allPass &= assertSame('拉 A-w1', '拉 A-w3');

// 同一週重複算，結果要一模一樣（決定性）
const repeat1 = accessoryPoolFor('拉 A', 2).join(',');
const repeat2 = accessoryPoolFor('拉 A', 2).join(',');
const deterministic = repeat1 === repeat2;
console.log(`  拉 A 第2週 重複計算兩次  →  ${deterministic ? '✅ 結果一致（決定性）' : '❌ 結果不一致'}`);
allPass &= deterministic;

console.log('\n' + '='.repeat(70));
console.log(allPass ? '🎉 全部通過：週與週、A跟B 配件都不同；主項全程穩定；同一週結果決定性一致。' : '⚠️ 有項目沒過，請檢查演算法。');
console.log('='.repeat(70));
