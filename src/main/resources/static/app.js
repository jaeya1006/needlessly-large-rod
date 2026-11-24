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

// ---------- 로그인 ----------
// 토큰은 HttpOnly 쿠키라 자바스크립트에서 볼 수 없다. 401이 오면 리프레시로 한 번 갱신해 보고, 안 되면 로그인 화면.
let refreshing = null; // 여러 요청이 동시에 401을 받아도 갱신은 한 번만
let session = 0; // 로그인·로그아웃할 때마다 +1. 이전 계정으로 보낸 요청의 응답이 늦게 와도 화면에 넣지 않는다
function refreshOnce() {
  // 409: 다른 탭이 방금 갱신해서 브라우저에 이미 새 쿠키가 있다 → 성공처럼 보고 다시 요청
  refreshing ??= fetch('/api/auth/refresh', { method: 'POST' })
    .then(r => r.ok || r.status === 409).catch(() => false).finally(() => { refreshing = null; });
  return refreshing;
}

async function api(url, opts) {
  let res = await fetch(url, opts);
  if (res.status !== 401) return res;
  if (await refreshOnce()) res = await fetch(url, opts);
  if (res.status === 401) showLogin('로그인이 만료됐어요. 다시 로그인해 주세요.');
  return res;
}
const pad = n => String(n).padStart(2, '0');
const won = n => n.toLocaleString('ko-KR') + '원';
const iso = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;
const now = new Date();
const todayIso = iso(now.getFullYear(), now.getMonth() + 1, now.getDate());

const state = { year: now.getFullYear(), month: now.getMonth() + 1, selected: todayIso, items: [], prevItems: [], watched: [] }; // prevItems: 지난달, watched: 항상 비교할 분류(서버 저장)

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

async function fetchMonth(y, m) {
  const res = await api(`/api/expenses?month=${y}-${pad(m)}`);
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return (await res.json()).filter(e => e.type !== 'INCOME'); // 예전에 적은 수입은 숨김
}

async function load() {
  const mine = session;
  $('month-label').textContent = `${state.year}년 ${state.month}월`;
  const prev = new Date(state.year, state.month - 2, 1);
  try {
    const [items, prevItems] = await Promise.all([
      fetchMonth(state.year, state.month),
      fetchMonth(prev.getFullYear(), prev.getMonth() + 1),
    ]);
    if (mine !== session) return;
    [state.items, state.prevItems] = [items, prevItems];
    notice('');
  } catch {
    if (mine !== session) return;
    // 서버가 꺼져 있어도 달력은 그린다
    state.items = [];
    state.prevItems = [];
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

const prevMonthNum = () => (state.month + 10) % 12 + 1;

// 작은 비교 줄: 아이콘 · 분류 · "10월 X원 → 11월 Y원" · ▲/▼
function compareRow(cat, now, before) {
  const li = document.createElement('li');
  const name = document.createElement('span');
  name.className = 'name';
  name.textContent = cat;
  const sub = document.createElement('small');
  sub.textContent = `${prevMonthNum()}월 ${won(before)} → ${state.month}월 ${won(now)}`;
  name.append(sub);
  li.append(iconEl(cat), name, diffEl(now - before));
  return li;
}

function emptyRow(text) {
  const li = document.createElement('li');
  li.className = 'empty';
  li.textContent = text;
  return li;
}

// 가장 많이 늘어난 2개 vs 가장 많이 줄어든 2개
function renderMovers(cur, old) {
  const diffs = [...new Set([...Object.keys(cur), ...Object.keys(old)])]
    .map(c => [c, (cur[c] || 0) - (old[c] || 0)]);
  const up = diffs.filter(d => d[1] > 0).sort((a, b) => b[1] - a[1]).slice(0, 2);
  const down = diffs.filter(d => d[1] < 0).sort((a, b) => a[1] - b[1]).slice(0, 2);
  const rows = list => list.length ? list.map(([c]) => compareRow(c, cur[c] || 0, old[c] || 0)) : [emptyRow('없어요')];
  $('movers-up').replaceChildren(...rows(up));
  $('movers-down').replaceChildren(...rows(down));
}

// 체크한 분류: 두 달 다 0원이어도 항상 보여 준다
function renderWatched(cur, old) {
  $('watch-list').replaceChildren(...(state.watched.length
    ? state.watched.map(c => compareRow(c, cur[c] || 0, old[c] || 0))
    : [emptyRow('아래 \'분류 고르기\'에서 체크하면 매달 여기서 비교해요')]));
  $('watch-checks').replaceChildren(...CATEGORIES.map(([name]) => {
    const label = document.createElement('label');
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.value = name;
    box.checked = state.watched.includes(name);
    box.onchange = saveWatched;
    label.append(box, iconEl(name), name);
    return label;
  }));
}

async function loadWatched() {
  const mine = session;
  const res = await api('/api/watched-categories').catch(() => null);
  if (res?.ok) { const list = await res.json(); if (mine !== session) return; state.watched = list; renderStats(); }
}

async function saveWatched() {
  const picked = [...document.querySelectorAll('#watch-checks input:checked')].map(b => b.value);
  const res = await api('/api/watched-categories', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(picked),
  }).catch(() => null);
  if (res?.ok) { state.watched = await res.json(); notice(''); }
  else notice('체크한 분류를 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
  const open = document.querySelector('.watch-pick').open;
  renderStats(); // 실패하면 저장된 상태로 체크가 되돌아간다
  document.querySelector('.watch-pick').open = open;
}

// 분류별 합계 { 교통: 18500, ... }. 세부 분류는 큰 분류로 묶는다.
const byCategory = items => items.reduce((g, e) => ((g[e.category] = (g[e.category] || 0) + e.amount), g), {});

// 지난달 대비 증감 표시. 더 쓰면 ▲(빨강), 덜 쓰면 ▼(초록)
function diffEl(diff, long = false) {
  const el = document.createElement(long ? 'p' : 'small');
  el.className = 'diff ' + (diff > 0 ? 'up' : diff < 0 ? 'down' : 'same');
  const amount = won(Math.abs(diff));
  el.textContent = long
    ? (diff > 0 ? `지난달보다 ${amount} 더 썼어요` : diff < 0 ? `지난달보다 ${amount} 덜 썼어요` : '지난달과 같아요')
    : (diff > 0 ? `▲ ${amount}` : diff < 0 ? `▼ ${amount}` : '변화 없음');
  if (!long) el.setAttribute('aria-label', diff > 0 ? `지난달보다 ${amount} 늘었어요` : diff < 0 ? `지난달보다 ${amount} 줄었어요` : '지난달과 같아요');
  return el;
}

function renderStats() {
  const items = state.items;
  const total = sum(items), prevTotal = sum(state.prevItems);
  const cur = byCategory(items), old = byCategory(state.prevItems);
  // 이번 달에 쓴 분류(큰 순) + 지난달에만 쓴 분류(이번 달 0원)
  const groups = Object.entries(cur).sort((a, b) => b[1] - a[1])
    .concat(Object.keys(old).filter(c => !(c in cur)).sort((a, b) => old[b] - old[a]).map(c => [c, 0]));
  $('stats-caption').textContent = `${state.month}월에 쓴 돈`;
  $('stats-total').textContent = won(total);
  $('stats-diff').replaceChildren(total || prevTotal ? diffEl(total - prevTotal, true) : '');
  renderMovers(cur, old);
  renderWatched(cur, old);

  let acc = 0;
  const stops = groups.map(([cat, amt]) => {
    const start = acc / total * 100;
    acc += amt;
    return `${catInfo(cat)[2]} ${start}% ${acc / total * 100}%`;
  });
  $('donut').style.background = total ? `conic-gradient(${stops.join(',')})` : '';
  const pct = amt => (total ? Math.round(amt / total * 100) : 0) + '%';
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
    value.append(diffEl(amt - (old[cat] || 0)));
    const bar = document.createElement('span');
    bar.className = 'bar';
    bar.innerHTML = `<i style="width:${total ? amt / total * 100 : 0}%;background:${catInfo(cat)[2]}"></i>`;
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
document.addEventListener('keydown', e => {
  if (e.key !== 'Escape') return;
  if (!$('account').hidden) closeAccount();
  else if (!$('sheet').hidden) closeSheet();
});
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
  const res = await api(editing ? `/api/expenses/${editing.id}` : '/api/expenses', {
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
  const res = await api('/api/expenses/' + editing.id, { method: 'DELETE' }).catch(() => null);
  closeSheet();
  if (!res?.ok) { notice('삭제하지 못했어요. 잠시 뒤 다시 시도해 주세요.'); return; }
  load();
};

// 정부 혜택 추천: 조건(출생연도·시도·상태) 저장 → 서버가 공공데이터 + Claude로 추천
const profileForm = $('profile-form');
profileForm.birthYear.max = now.getFullYear(); // 서버도 올해보다 늦은 연도는 거부한다

async function loadProfile() {
  const mine = session;
  const res = await api('/api/profile').catch(() => null);
  if (!res?.ok || mine !== session) return; // 아직 입력 안 함(404) 또는 서버 꺼짐 → 빈 칸 그대로
  const p = await res.json();
  if (mine !== session) return;
  profileForm.birthYear.value = p.birthYear ?? '';
  profileForm.region.value = p.region ?? '';
  profileForm.status.value = p.status ?? '';
}

profileForm.onsubmit = async ev => {
  ev.preventDefault();
  const body = { birthYear: Number(profileForm.birthYear.value), region: profileForm.region.value, status: profileForm.status.value };
  const res = await api('/api/profile', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }).catch(() => null);
  $('benefit-msg').textContent = res?.ok ? '조건을 저장했어요.' : '조건을 저장하지 못했어요. 입력을 확인해 주세요.';
};

const BENEFIT_MESSAGES = {
  NO_KEYS: m => `API 키가 아직 없어요. 프로젝트 폴더의 .env에 ${m.join(', ')} 값을 넣고 앱을 다시 실행해 주세요.`,
  NO_PROFILE: () => '위 조건을 먼저 저장해 주세요.',
  NO_WATCHED: () => "'항상 비교하는 분류'에서 분류를 먼저 체크해 주세요.",
};

$('recommend-btn').onclick = async () => {
  const btn = $('recommend-btn');
  btn.disabled = true;
  $('benefit-msg').textContent = '정부 혜택을 찾고 있어요. 1분쯤 걸릴 수 있어요…';
  $('benefit-list').replaceChildren();
  const res = await api('/api/recommendations').catch(() => null);
  btn.disabled = false;
  if (res?.status === 429) { $('benefit-msg').textContent = '이미 추천을 찾는 중이에요. 잠시 뒤 다시 시도해 주세요.'; return; }
  if (!res?.ok) { $('benefit-msg').textContent = '혜택 정보를 가져오지 못했어요. 잠시 뒤 다시 시도해 주세요.'; return; }
  const r = await res.json();
  if (BENEFIT_MESSAGES[r.status]) { $('benefit-msg').textContent = BENEFIT_MESSAGES[r.status](r.missing); return; }
  const skipped = r.unsupported.length ? ` (${r.unsupported.join(', ')}은 혜택을 찾지 않는 분류예요)` : '';
  $('benefit-msg').textContent = (r.items.length ? `${r.items.length}개를 찾았어요. 신청 전 원문에서 대상을 꼭 확인하세요.` : '조건에 맞는 혜택을 찾지 못했어요.') + skipped;
  $('benefit-list').replaceChildren(...r.items.map(b => {
    const li = document.createElement('li');
    const name = document.createElement('span');
    name.className = 'name';
    // 링크는 공공데이터 원본 주소. http(s)만 허용
    const title = /^https?:\/\//.test(b.url || '') ? Object.assign(document.createElement('a'), { href: b.url, target: '_blank', rel: 'noopener' }) : document.createElement('strong');
    title.textContent = b.name;
    const why = document.createElement('small');
    why.textContent = `${b.category} · ${b.reason}${b.agency ? ' · ' + b.agency : ''}`;
    name.append(title, why);
    li.append(iconEl(b.category), name);
    return li;
  }));
};

// ---------- 로그인 화면 ----------
let signupMode = false;
const authForm = $('auth-form');

function setAuthMode(signup) {
  signupMode = signup;
  $('auth-title').textContent = signup ? '회원가입' : '로그인';
  $('auth-submit').textContent = signup ? '가입하기' : '로그인';
  $('auth-toggle').textContent = signup ? '이미 계정이 있으면 로그인' : '처음이면 회원가입';
  authForm.password.autocomplete = signup ? 'new-password' : 'current-password';
  $('auth-error').textContent = '';
}

function showLogin(message = '') {
  $('auth').hidden = false;
  $('auth-error').textContent = message;
  authForm.email.focus();
}

$('auth-toggle').onclick = () => setAuthMode(!signupMode);

authForm.onsubmit = async ev => {
  ev.preventDefault();
  const res = await fetch(signupMode ? '/api/auth/signup' : '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: authForm.email.value, password: authForm.password.value }),
  }).catch(() => null);
  if (!res) { $('auth-error').textContent = '서버에 연결하지 못했어요.'; return; }
  if (res.status === 409) { $('auth-error').textContent = '이미 가입한 이메일이에요. 로그인해 주세요.'; return; }
  if (res.status === 400) { $('auth-error').textContent = '이메일 형식과 비밀번호(8~64자)를 확인해 주세요.'; return; }
  if (res.status === 429) { $('auth-error').textContent = '시도가 너무 많아요. 15분 뒤에 다시 해 주세요.'; return; }
  if (!res.ok) { $('auth-error').textContent = '이메일 또는 비밀번호가 맞지 않아요.'; return; }
  authForm.password.value = '';
  start();
};

// 계정이 바뀔 때 이전 사람의 화면 값(지출·체크·혜택 조건·추천 결과·입력 시트)을 모두 비운다
function resetMemberUi() {
  session++;
  state.items = []; state.prevItems = []; state.watched = [];
  profileForm.reset();
  $('benefit-list').replaceChildren();
  $('benefit-msg').textContent = '';
  $('sheet').hidden = true;
  $('account').hidden = true;
  $('password-form').reset();
  $('delete-form').reset();
  $('account-msg').textContent = '';
  notice('');
  render();
}

$('logout').onclick = async () => {
  const res = await fetch('/api/auth/logout', { method: 'POST' }).catch(() => null);
  // 서버가 못 받았으면 쿠키가 살아 있어서 새로고침하면 다시 로그인된다 → 성공처럼 보이지 않게
  if (!res?.ok) { $('account-msg').textContent = '로그아웃하지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.'; return; }
  resetMemberUi();
  setAuthMode(false);
  showLogin();
};

async function start() {
  // 처음 열 때는 '만료' 안내 없이: 액세스 토큰이 없거나 만료면 리프레시를 조용히 한 번 시도
  let res = await fetch('/api/auth/me').catch(() => null);
  if (res?.status === 401 && await refreshOnce()) res = await fetch('/api/auth/me').catch(() => null);
  if (!res?.ok) { showLogin(); return; }
  const me = await res.json();
  resetMemberUi(); // 다른 계정으로 다시 로그인한 경우 대비
  $('auth').hidden = true;
  $('account-email').textContent = me.email;
  load();
  loadWatched();
  loadProfile();
}

// ---------- 계정 ----------
function closeAccount() {
  $('account').hidden = true;
  $('account-btn').focus();
}
$('account-btn').onclick = () => {
  $('account-msg').textContent = '';
  $('account').hidden = false;
  $('password-form').currentPassword.focus();
};
$('account-close').onclick = closeAccount;

const ACCOUNT_ERRORS = {
  403: '현재 비밀번호가 맞지 않아요.',
  429: '시도가 너무 많아요. 15분 뒤에 다시 해 주세요.',
  400: '새 비밀번호는 8~64자로 정해 주세요(한글은 24자까지).',
};

$('password-form').onsubmit = async ev => {
  ev.preventDefault();
  const f = ev.target;
  if (f.newPassword.value !== f.confirm.value) { $('account-msg').textContent = '새 비밀번호 두 칸이 서로 달라요.'; return; }
  const res = await api('/api/auth/password', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentPassword: f.currentPassword.value, newPassword: f.newPassword.value }),
  }).catch(() => null);
  if (res?.status === 401) return; // 로그인 만료 → api()가 로그인 화면을 띄움
  $('account-msg').textContent = res?.ok ? '비밀번호를 바꿨어요. 다른 기기에서는 다시 로그인해야 해요.'
    : ACCOUNT_ERRORS[res?.status] || '비밀번호를 바꾸지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  if (res?.ok) f.reset();
};

$('delete-form').onsubmit = async ev => {
  ev.preventDefault();
  if (!confirm('정말 탈퇴할까요? 모든 지출 기록이 지워지고 되돌릴 수 없어요.')) return;
  const res = await api('/api/auth/me', {
    method: 'DELETE', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: ev.target.password.value }),
  }).catch(() => null);
  if (res?.status === 401) return;
  if (!res?.ok) { $('account-msg').textContent = ACCOUNT_ERRORS[res?.status] || '탈퇴하지 못했어요. 잠시 뒤 다시 시도해 주세요.'; return; }
  resetMemberUi();
  setAuthMode(false);
  showLogin('탈퇴했어요. 그동안 고마웠어요.');
};

start();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js');
