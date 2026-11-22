// 분류: [이름, 아이콘, 색]. 서버는 이름만 저장하고 아이콘·색은 화면에서 붙인다.
const CATEGORIES = {
  EXPENSE: [
    ['식비', '🍚', '#ffd8a8'], ['카페', '☕', '#e9d8c4'], ['간식', '🍪', '#ffe8cc'], ['술', '🍺', '#fff3bf'],
    ['교통', '🚌', '#d0ebff'], ['쇼핑', '🛍️', '#fcc2d7'], ['생활', '🧻', '#e9ecef'], ['공과금', '💡', '#d3f9d8'],
    ['통신', '📱', '#c5f6fa'], ['의료', '💊', '#ffc9c9'], ['문화', '🎬', '#e5dbff'], ['운동', '🏋️', '#d8f5a2'],
    ['교육', '📚', '#dbe4ff'], ['선물', '🎁', '#f3d9fa'], ['경조사', '💐', '#ffdeeb'], ['기타', '📦', '#dee2e6'],
  ],
  INCOME: [
    ['월급', '💰', '#d3f9d8'], ['용돈', '🪙', '#fff3bf'], ['부수입', '💵', '#c3fae8'], ['이자', '🏦', '#d0ebff'], ['기타', '📦', '#dee2e6'],
  ],
};
const catInfo = (type, name) =>
  (CATEGORIES[type] || CATEGORIES.EXPENSE).find(c => c[0] === name) || [name, '•', '#dee2e6'];

const $ = id => document.getElementById(id);
const pad = n => String(n).padStart(2, '0');
const won = n => n.toLocaleString('ko-KR') + '원';
const iso = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;
const now = new Date();
const todayIso = iso(now.getFullYear(), now.getMonth() + 1, now.getDate());

const state = { year: now.getFullYear(), month: now.getMonth() + 1, selected: todayIso, items: [], statsType: 'EXPENSE', entryType: null };

function iconEl(type, category) {
  const [, emoji, color] = catInfo(type, category);
  const span = document.createElement('span');
  span.className = 'icon';
  span.style.background = color;
  span.textContent = emoji;
  span.setAttribute('aria-hidden', 'true');
  return span;
}

async function load() {
  $('month-label').textContent = `${state.year}년 ${state.month}월`;
  const res = await fetch(`/api/expenses?month=${state.year}-${pad(state.month)}`);
  state.items = res.ok ? await res.json() : [];
  render();
}

function sumBy(items, type) {
  return items.filter(e => e.type === type).reduce((s, e) => s + e.amount, 0);
}

function render() {
  const income = sumBy(state.items, 'INCOME'), expense = sumBy(state.items, 'EXPENSE');
  $('sum-income').textContent = won(income);
  $('sum-expense').textContent = won(expense);
  $('sum-net').textContent = won(income - expense);
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
    const ex = sumBy(dayItems, 'EXPENSE'), inc = sumBy(dayItems, 'INCOME');
    const weekday = (first + d - 1) % 7;
    const b = document.createElement('button');
    b.setAttribute('aria-pressed', date === state.selected);
    b.setAttribute('aria-label', `${state.month}월 ${d}일` + (ex ? `, 지출 ${won(ex)}` : '') + (inc ? `, 수입 ${won(inc)}` : ''));
    if (date === todayIso) b.classList.add('today');
    b.innerHTML = `<span class="d ${weekday === 0 ? 'sun' : weekday === 6 ? 'sat' : ''}">${d}</span>`
      + (inc ? `<small class="income">${inc.toLocaleString('ko-KR')}</small>` : '')
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
  const ex = sumBy(items, 'EXPENSE');
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
    name.textContent = e.title || e.category;
    if (e.memo || e.title) {
      const sub = document.createElement('small');
      sub.textContent = [e.title ? e.category : null, e.memo].filter(Boolean).join(' · ');
      name.append(sub);
    }
    const amt = document.createElement('span');
    amt.className = 'amt ' + (e.type === 'INCOME' ? 'income' : 'expense');
    amt.textContent = (e.type === 'INCOME' ? '+' : '') + won(e.amount);
    b.append(iconEl(e.type, e.category), name, amt);
    b.setAttribute('aria-label', `${e.title || e.category} ${won(e.amount)}, 눌러서 삭제`);
    b.onclick = async () => {
      if (!confirm(`'${e.title || e.category}' ${won(e.amount)}을 삭제할까요?`)) return;
      await fetch('/api/expenses/' + e.id, { method: 'DELETE' });
      load();
    };
    li.append(b);
    return li;
  }));
}

function renderStats() {
  const type = state.statsType;
  const items = state.items.filter(e => e.type === type);
  const total = items.reduce((s, e) => s + e.amount, 0);
  const groups = Object.entries(items.reduce((g, e) => ((g[e.category] = (g[e.category] || 0) + e.amount), g), {}))
    .sort((a, b) => b[1] - a[1]);
  const label = type === 'EXPENSE' ? '쓴 돈' : '번 돈';
  $('stats-caption').textContent = `${state.month}월에 ${label}`;
  $('stats-total').textContent = won(total);

  let acc = 0;
  const stops = groups.map(([cat, amt]) => {
    const start = acc / total * 100;
    acc += amt;
    return `${catInfo(type, cat)[2]} ${start}% ${acc / total * 100}%`;
  });
  $('donut').style.background = total ? `conic-gradient(${stops.join(',')})` : '';
  const pct = amt => Math.round(amt / total * 100) + '%';
  $('donut-label').textContent = total ? `가장 큰 ${type === 'EXPENSE' ? '지출' : '수입'}` : '';
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
    bar.innerHTML = `<i style="width:${amt / total * 100}%;background:${catInfo(type, cat)[2]}"></i>`;
    li.append(iconEl(type, cat), name, value, bar);
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
document.querySelectorAll('.toggle button').forEach(b => b.onclick = () => {
  state.statsType = b.dataset.type;
  document.querySelectorAll('.toggle button').forEach(x => x.setAttribute('aria-selected', x === b));
  renderStats();
});

// 입력 시트
const amountInput = $('entry-amount');
const amountValue = () => Number(amountInput.value.replace(/\D/g, ''));

function openSheet() {
  $('entry-date').value = state.selected;
  amountInput.value = '';
  $('entry-title').value = '';
  $('entry-memo').value = '';
  $('error').textContent = '';
  showStep(1);
  $('sheet').hidden = false;
  updateTypeButtons();
  amountInput.focus();
}
const closeSheet = () => { $('sheet').hidden = true; $('add').focus(); };
function showStep(n) {
  $('step1').hidden = n !== 1;
  $('step2').hidden = n !== 2;
}
function updateTypeButtons() {
  document.querySelectorAll('.type-btns button').forEach(b => b.disabled = !amountValue());
}

$('add').onclick = openSheet;
document.querySelectorAll('[data-close]').forEach(b => b.onclick = closeSheet);
document.addEventListener('keydown', e => { if (e.key === 'Escape' && !$('sheet').hidden) closeSheet(); });
amountInput.oninput = () => {
  const v = amountValue();
  amountInput.value = v ? v.toLocaleString('ko-KR') : '';
  updateTypeButtons();
};
amountInput.onkeydown = e => { if (e.key === 'Enter' && amountValue()) chooseType('EXPENSE'); };

function chooseType(type) {
  state.entryType = type;
  const head = $('step2-amount');
  head.textContent = (type === 'INCOME' ? '+' : '') + won(amountValue());
  head.style.color = `var(--${type === 'INCOME' ? 'income' : 'expense'})`;
  $('cat-grid').replaceChildren(...CATEGORIES[type].map(([name]) => {
    const b = document.createElement('button');
    b.append(iconEl(type, name), name);
    b.onclick = () => save(name);
    return b;
  }));
  showStep(2);
  $('entry-title').focus();
}
document.querySelectorAll('.type-btns button').forEach(b => b.onclick = () => chooseType(b.dataset.type));
$('back').onclick = () => { showStep(1); amountInput.focus(); };

async function save(category) {
  const spentAt = $('entry-date').value;
  const res = await fetch('/api/expenses', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      type: state.entryType, title: $('entry-title').value, amount: amountValue(),
      category, memo: $('entry-memo').value, spentAt,
    }),
  });
  if (!res.ok) { $('error').textContent = '저장하지 못했어요. 입력을 확인해 주세요.'; return; }
  closeSheet();
  // 다른 달 날짜로 적었으면 그 달로 이동
  const [y, m] = spentAt.split('-').map(Number);
  state.year = y; state.month = m; state.selected = spentAt;
  load();
}

load();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js');
