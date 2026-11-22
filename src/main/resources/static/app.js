// 지출 전용 가계부(#13). 서버 API의 type은 남아 있지만 화면은 EXPENSE만 쓴다.
// 분류: [이름, 아이콘, 색]. 서버는 이름만 저장하고 아이콘·색은 화면에서 붙인다.
const CATEGORIES = [
  ['식비', '🍚', '#ffd8a8'], ['카페', '☕', '#e9d8c4'], ['간식', '🍪', '#ffe8cc'], ['술', '🍺', '#fff3bf'],
  ['교통', '🚌', '#d0ebff'], ['쇼핑', '🛍️', '#fcc2d7'], ['생활', '🧻', '#e9ecef'], ['공과금', '💡', '#d3f9d8'],
  ['통신', '📱', '#c5f6fa'], ['의료', '💊', '#ffc9c9'], ['문화', '🎬', '#e5dbff'], ['운동', '🏋️', '#d8f5a2'],
  ['교육', '📚', '#dbe4ff'], ['선물', '🎁', '#f3d9fa'], ['경조사', '💐', '#ffdeeb'], ['기타', '📦', '#dee2e6'],
];
const catInfo = name => CATEGORIES.find(c => c[0] === name) || [name, '•', '#dee2e6'];
// 세부 분류: { 큰 분류: [[이름, 아이콘], ...] }. 여기 목록만 추가하면 다른 분류도 한 단계 더 고르게 된다.
const SUBCATEGORIES = {
  '교통': [['버스', '🚌'], ['지하철', '🚇'], ['택시', '🚕'], ['기차', '🚆'], ['비행기', '✈️'], ['주유', '⛽'], ['주차·통행료', '🅿️'], ['기타', '📦']],
};

const $ = id => document.getElementById(id);
const pad = n => String(n).padStart(2, '0');
const won = n => n.toLocaleString('ko-KR') + '원';
const iso = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;
const now = new Date();
const todayIso = iso(now.getFullYear(), now.getMonth() + 1, now.getDate());

const state = { year: now.getFullYear(), month: now.getMonth() + 1, selected: todayIso, items: [] };

function iconEl(category, subcategory) {
  const [, catEmoji, color] = catInfo(category);
  const emoji = SUBCATEGORIES[category]?.find(s => s[0] === subcategory)?.[1] || catEmoji;
  const span = document.createElement('span');
  span.className = 'icon';
  span.style.background = color;
  span.textContent = emoji;
  span.setAttribute('aria-hidden', 'true');
  return span;
}

// 서버 오류 안내. 빈 문자열이면 숨김.
function notice(msg) {
  $('notice').textContent = msg;
  $('notice').hidden = !msg;
}

async function load() {
  $('month-label').textContent = `${state.year}년 ${state.month}월`;
  try {
    const res = await fetch(`/api/expenses?month=${state.year}-${pad(state.month)}`);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    state.items = (await res.json()).filter(e => e.type !== 'INCOME'); // 예전에 적은 수입은 숨김
    notice('');
  } catch {
    // 서버가 꺼져 있어도 달력은 그린다
    state.items = [];
    notice('서버에 연결하지 못했어요. 앱(서버)이 켜져 있는지 확인해 주세요.');
  }
  render();
}

const sum = items => items.reduce((s, e) => s + e.amount, 0);

function render() {
  $('sum-expense').textContent = won(sum(state.items));
  renderCalendar();
  renderDay();
  renderStats();
}

function renderCalendar() {
  const first = new Date(state.year, state.month - 1, 1).getDay();
  const days = new Date(state.year, state.month, 0).getDate();
  const cells = [];
  for (let i = 0; i < first; i++) cells.push(document.createElement('span'));
  for (let d = 1; d <= days; d++) {
    const date = iso(state.year, state.month, d);
    const dayItems = state.items.filter(e => e.spentAt === date);
    const ex = sum(dayItems);
    const weekday = (first + d - 1) % 7;
    const b = document.createElement('button');
    b.setAttribute('aria-pressed', date === state.selected);
    b.setAttribute('aria-label', `${state.month}월 ${d}일` + (ex ? `, 지출 ${won(ex)}` : ''));
    if (date === todayIso) b.classList.add('today');
    b.innerHTML = `<span class="d ${weekday === 0 ? 'sun' : weekday === 6 ? 'sat' : ''}">${d}</span>`
      + (ex ? `<small class="expense">${ex.toLocaleString('ko-KR')}</small>` : '');
    b.onclick = () => { state.selected = date; renderCalendar(); renderDay(); };
    cells.push(b);
  }
  $('calendar').replaceChildren(...cells);
}

function renderDay() {
  const [, m, d] = state.selected.split('-').map(Number);
  const items = state.items.filter(e => e.spentAt === state.selected);
  $('day-label').textContent = `${m}월 ${d}일`;
  const ex = sum(items);
  $('day-total').textContent = ex ? `지출 ${won(ex)}` : '';
  if (!items.length) {
    $('day-list').innerHTML = '<li class="empty">이날 적은 내역이 없어요</li>';
    return;
  }
  $('day-list').replaceChildren(...items.map(e => {
    const li = document.createElement('li');
    const b = document.createElement('button');
    const name = document.createElement('span');
    name.className = 'name';
    // 이름: 내역 > 세부 분류 > 분류 순. 아랫줄: 이름에 안 쓴 분류 경로 + 메모
    const label = e.title || e.subcategory || e.category;
    const path = e.title ? [e.category, e.subcategory].filter(Boolean).join(' › ') : e.subcategory ? e.category : null;
    name.textContent = label;
    if (path || e.memo) {
      const sub = document.createElement('small');
      sub.textContent = [path, e.memo].filter(Boolean).join(' · ');
      name.append(sub);
    }
    const amt = document.createElement('span');
    amt.className = 'amt expense';
    amt.textContent = won(e.amount);
    b.append(iconEl(e.category, e.subcategory), name, amt);
    b.setAttribute('aria-label', `${label} ${won(e.amount)}, 눌러서 수정`);
    b.onclick = () => openSheet(e);
    li.append(b);
    return li;
  }));
}

function renderStats() {
  const items = state.items;
  const total = sum(items);
  const groups = Object.entries(items.reduce((g, e) => ((g[e.category] = (g[e.category] || 0) + e.amount), g), {}))
    .sort((a, b) => b[1] - a[1]);
  $('stats-caption').textContent = `${state.month}월에 쓴 돈`;
  $('stats-total').textContent = won(total);

  let acc = 0;
  const stops = groups.map(([cat, amt]) => {
    const start = acc / total * 100;
    acc += amt;
    return `${catInfo(cat)[2]} ${start}% ${acc / total * 100}%`;
  });
  $('donut').style.background = total ? `conic-gradient(${stops.join(',')})` : '';
  const pct = amt => Math.round(amt / total * 100) + '%';
  $('donut-label').textContent = total ? '가장 큰 지출' : '';
  $('donut-name').textContent = total ? groups[0][0] : '내역 없음';
  $('donut-pct').textContent = total ? pct(groups[0][1]) : '';

  if (!groups.length) {
    $('legend').innerHTML = '<li class="empty">이번 달 내역이 없어요</li>';
    return;
  }
  $('legend').replaceChildren(...groups.map(([cat, amt]) => {
    const li = document.createElement('li');
    const name = document.createElement('span');
    name.innerHTML = '<span class="pct"></span>';
    name.prepend(cat);
    name.querySelector('.pct').textContent = pct(amt);
    const value = document.createElement('span');
    value.className = 'amt';
    value.textContent = won(amt);
    const bar = document.createElement('span');
    bar.className = 'bar';
    bar.innerHTML = `<i style="width:${amt / total * 100}%;background:${catInfo(cat)[2]}"></i>`;
    li.append(iconEl(cat), name, value, bar);
    return li;
  }));
}

// 월 이동
function moveMonth(delta) {
  const d = new Date(state.year, state.month - 1 + delta, 1);
  state.year = d.getFullYear();
  state.month = d.getMonth() + 1;
  state.selected = todayIso.startsWith(`${state.year}-${pad(state.month)}`) ? todayIso : iso(state.year, state.month, 1);
  load();
}
$('prev').onclick = () => moveMonth(-1);
$('next').onclick = () => moveMonth(1);

// 탭
document.querySelectorAll('.tabbar button').forEach(b => b.onclick = () => {
  document.querySelectorAll('.tabbar button').forEach(x =>
    x === b ? x.setAttribute('aria-current', 'page') : x.removeAttribute('aria-current'));
  $('view-calendar').hidden = b.dataset.view !== 'calendar';
  $('view-stats').hidden = b.dataset.view !== 'stats';
});

// 입력 시트
const amountInput = $('entry-amount');
const amountValue = () => Number(amountInput.value.replace(/\D/g, ''));

let editing = null; // 수정 중인 내역, 새로 적을 때는 null

// entry를 주면 그 내역을 수정, 없으면 새로 적기
function openSheet(entry = null) {
  editing = entry;
  $('entry-date').value = entry?.spentAt || state.selected;
  amountInput.value = entry ? entry.amount.toLocaleString('ko-KR') : '';
  $('entry-title').value = entry?.title || '';
  $('entry-memo').value = entry?.memo || '';
  $('error').textContent = '';
  $('delete-entry').hidden = !entry;
  $('sheet').setAttribute('aria-label', entry ? '내역 수정' : '새로 적기');
  showStep(1);
  $('sheet').hidden = false;
  updateNextButton();
  amountInput.focus();
}
const closeSheet = () => { $('sheet').hidden = true; $('add').focus(); };
function showStep(n) {
  $('step1').hidden = n !== 1;
  $('step2').hidden = n !== 2;
}
function updateNextButton() {
  $('next-step').disabled = !amountValue();
}

$('add').onclick = () => openSheet(); // 클릭 이벤트가 entry로 넘어가지 않게
document.querySelectorAll('[data-close]').forEach(b => b.onclick = closeSheet);
document.addEventListener('keydown', e => { if (e.key === 'Escape' && !$('sheet').hidden) closeSheet(); });
amountInput.oninput = () => {
  const v = amountValue();
  amountInput.value = v ? v.toLocaleString('ko-KR') : '';
  updateNextButton();
};
amountInput.onkeydown = e => { if (e.key === 'Enter' && amountValue()) goStep2(); };

let pickedCategory = null; // 세부 분류를 고르는 중이면 그 큰 분류

const saveWord = () => editing ? '수정' : '저장';

function showCategories() {
  pickedCategory = null;
  $('cat-hint').textContent = `분류를 누르면 ${saveWord()}돼요`;
  $('cat-grid').replaceChildren(...CATEGORIES.map(([name]) => {
    const b = document.createElement('button');
    b.setAttribute('aria-pressed', editing?.category === name); // 수정할 때 지금 분류 표시
    b.append(iconEl(name), name);
    b.onclick = () => SUBCATEGORIES[name] ? showSubcategories(name) : save(name);
    return b;
  }));
}

function showSubcategories(category) {
  pickedCategory = category;
  $('cat-hint').textContent = `${category} › 세부 분류를 누르면 ${saveWord()}돼요`;
  $('cat-grid').replaceChildren(...SUBCATEGORIES[category].map(([name]) => {
    const b = document.createElement('button');
    b.setAttribute('aria-pressed', editing?.category === category && editing?.subcategory === name);
    b.append(iconEl(category, name), name);
    b.onclick = () => save(category, name);
    return b;
  }));
  $('cat-grid').querySelector('button').focus();
}

function goStep2() {
  $('step2-amount').textContent = won(amountValue());
  showCategories();
  showStep(2);
  $('entry-title').focus();
}
$('next-step').onclick = goStep2;
// 뒤로: 세부 분류 → 분류 목록 → 금액
$('back').onclick = () => {
  if (pickedCategory) { showCategories(); return; }
  showStep(1);
  amountInput.focus();
};

async function save(category, subcategory = null) {
  const spentAt = $('entry-date').value;
  const res = await fetch(editing ? `/api/expenses/${editing.id}` : '/api/expenses', {
    method: editing ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      type: 'EXPENSE', title: $('entry-title').value, amount: amountValue(),
      category, subcategory, memo: $('entry-memo').value, spentAt,
    }),
  }).catch(() => null);
  if (!res) { $('error').textContent = '서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.'; return; }
  if (!res.ok) { $('error').textContent = `${saveWord()}하지 못했어요. 입력을 확인해 주세요.`; return; }
  closeSheet();
  // 다른 달 날짜로 적었으면 그 달로 이동
  const [y, m] = spentAt.split('-').map(Number);
  state.year = y; state.month = m; state.selected = spentAt;
  load();
}

$('delete-entry').onclick = async () => {
  const label = editing.title || editing.subcategory || editing.category;
  if (!confirm(`'${label}' ${won(editing.amount)}을 삭제할까요?`)) return;
  const res = await fetch('/api/expenses/' + editing.id, { method: 'DELETE' }).catch(() => null);
  closeSheet();
  if (!res?.ok) { notice('삭제하지 못했어요. 잠시 뒤 다시 시도해 주세요.'); return; }
  load();
};

load();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js');
