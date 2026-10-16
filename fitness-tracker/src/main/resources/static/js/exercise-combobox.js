/* 動作名稱搜尋輸入框：取代原本的 <input list><datalist>，多了「打的動作不存在時可以直接新增」
   （Notion/GitHub 標籤那種 combobox）。找不到完全符合的名稱時，選單最後會多一列「＋ 新增動作『XXX』」，
   點下去會呼叫 POST /api/exercises/mine 建一筆只有自己看得到的個人自訂動作，然後直接套用。
   全站共用（不限單一頁面）：/workout 跟 /plan 編輯彈窗的配件動作都用同一份。 */
(function () {
  function ensureStyles() {
    if (document.getElementById('ex-combo-styles')) return;
    var style = document.createElement('style');
    style.id = 'ex-combo-styles';
    style.textContent =
      '.ex-combo-wrap{position:relative}' +
      '.ex-combo-list{position:absolute;left:0;right:0;top:100%;z-index:80;background:#fff;' +
        'border:1px solid #d8dee8;border-radius:8px;box-shadow:0 8px 24px rgba(15,23,42,.14);' +
        'max-height:220px;overflow-y:auto;margin-top:4px;display:none;text-align:left}' +
      '.ex-combo-list.on{display:block}' +
      '.ex-combo-opt{padding:8px 12px;font-size:.88rem;cursor:pointer;white-space:nowrap;' +
        'overflow:hidden;text-overflow:ellipsis;color:#1a202c}' +
      '.ex-combo-opt:hover,.ex-combo-opt.hl{background:#f1f5f9}' +
      '.ex-combo-new{padding:8px 12px;font-size:.86rem;cursor:pointer;color:#059669;font-weight:700;' +
        'border-top:1px solid #eef2f7}' +
      '.ex-combo-new:hover,.ex-combo-new.hl{background:#f0fdf9}' +
      '.ex-combo-empty{padding:8px 12px;font-size:.82rem;color:#94a3b8}';
    document.head.appendChild(style);
  }

  // 呼叫 POST /api/exercises/mine 建立個人自訂動作。CSRF token 從頁面的 <meta name="_csrf">/
  // <meta name="_csrf_header"> 讀（Spring Security 預設的 CSRF 防護只認表單/header 帶 token 的請求，
  // 這兩個 meta 要各自加在會用到這個 combobox 的頁面 <head> 裡，見 workout/index.html、plan/index.html）
  window.createPersonalExercise = function (name, bodyPart, category) {
    var tokenMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    var headers = { 'Content-Type': 'application/json' };
    if (tokenMeta && headerMeta) headers[headerMeta.content] = tokenMeta.content;
    return fetch('/api/exercises/mine', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({ name: name, bodyPart: bodyPart, category: category })
    }).then(function (res) {
      if (!res.ok) throw new Error('create exercise failed: ' + res.status);
      return res.json();
    }).then(function (data) {
      return data.name;
    });
  };

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  // input: 要接管的文字輸入框
  // opts.getOptions(): () => string[]，目前可選的動作名稱（呼叫端自己依上下文過濾，例如依訓練部位）
  // opts.onSelect(name): 選到（或新增完）一個動作時呼叫
  // opts.onCreate(name): 點「＋新增動作」時呼叫，要回傳 Promise<建立後的名稱字串|null>；
  //                      沒有給這個 callback 的話（例如主要動作那種鎖定 4 選 1 的欄位）就不會顯示新增列
  window.attachExerciseCombobox = function (input, opts) {
    ensureStyles();
    opts = opts || {};

    if (!input.parentElement || !input.parentElement.classList.contains('ex-combo-wrap')) {
      var wrap = document.createElement('div');
      wrap.className = 'ex-combo-wrap';
      input.parentElement.insertBefore(wrap, input);
      wrap.appendChild(input);
    }
    var list = document.createElement('div');
    list.className = 'ex-combo-list';
    input.parentElement.appendChild(list);

    function render() {
      var q = input.value.trim();
      var all = opts.getOptions ? (opts.getOptions() || []) : [];
      var filtered = q ? all.filter(function (n) { return n.indexOf(q) !== -1; }) : all.slice(0, 30);
      var exact = q && all.indexOf(q) !== -1;
      var html = '';
      filtered.slice(0, 30).forEach(function (n) {
        html += '<div class="ex-combo-opt">' + escapeHtml(n) + '</div>';
      });
      if (q && !exact && opts.onCreate) {
        html += '<div class="ex-combo-new" data-create="1">＋ 新增動作「' + escapeHtml(q) + '」</div>';
      }
      if (!html) {
        if (!q) { list.classList.remove('on'); return; }
        html = '<div class="ex-combo-empty">沒有符合的動作</div>';
      }
      list.innerHTML = html;
      list.classList.add('on');
    }

    function close() { list.classList.remove('on'); }

    input.addEventListener('input', render);
    input.addEventListener('focus', render);
    // 使用者打完剛好精準符合某個既有名稱、直接點掉（不是點下拉選項）也算選到——不然打對字卻沒點
    // 選單，onSelect 永遠不會被觸發，值看起來對了但呼叫端其實沒收到「這是哪個動作」的通知
    input.addEventListener('blur', function () {
      setTimeout(function () {
        close();
        var val = input.value.trim();
        var all = opts.getOptions ? (opts.getOptions() || []) : [];
        if (val && all.indexOf(val) !== -1 && opts.onSelect) opts.onSelect(val);
      }, 150);
    });

    list.addEventListener('mousedown', function (e) {
      var newEl = e.target.closest('.ex-combo-new');
      var optEl = e.target.closest('.ex-combo-opt');
      if (newEl) {
        e.preventDefault();
        var name = input.value.trim();
        if (!name) return;
        var originalText = newEl.textContent;
        newEl.textContent = '新增中…';
        Promise.resolve(opts.onCreate(name)).then(function (created) {
          close();
          if (created) {
            input.value = created;
            opts.onSelect && opts.onSelect(created);
          } else {
            newEl.textContent = originalText;
          }
        }).catch(function () {
          close();
          alert('新增動作失敗，請稍後再試一次');
        });
      } else if (optEl) {
        e.preventDefault();
        input.value = optEl.textContent;
        close();
        opts.onSelect && opts.onSelect(input.value);
      }
    });

    document.addEventListener('click', function (e) {
      if (!input.parentElement.contains(e.target)) close();
    });
  };
})();
