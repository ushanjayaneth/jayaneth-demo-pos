
'use strict';

'use strict';

const RTDB_URL = 'https://janith-mobile-default-rtdb.asia-southeast1.firebasedatabase.app';
const GEMINI_KEY_DEFAULT = 'AIzaSyAf_B3yNPqUZt399ohiUyZSBZFcLTX8kfU';

const S = {
  categories:[], items:[], sales:[],
  settings:{shopName:'Janith Mobile',adminPin:'1234',currency:'Rs.',geminiKey:GEMINI_KEY_DEFAULT,lowStockAlert:5,receiptNote:'Thank you for shopping at Janith Mobile!'},
  cart:[], cartDisc:0, cartDiscType:'percent', cartMode:'retail',
  activeCat:'all', adminUnlocked:false, pollTimer:null,
  isOnline: navigator.onLine
};

// ── IndexedDB LOCAL STORE ────────────────
const IDB = {
  _db: null,
  async open(){
    if(this._db) return this._db;
    return new Promise((res,rej)=>{
      const req = indexedDB.open('JanithMobilePOS', 2);
      req.onupgradeneeded = e => {
        const db = e.target.result;
        // cache: stores full Firebase path → data snapshots
        if(!db.objectStoreNames.contains('cache'))
          db.createObjectStore('cache', {keyPath:'path'});
        // queue: pending writes to sync when back online
        if(!db.objectStoreNames.contains('queue'))
          db.createObjectStore('queue', {keyPath:'id', autoIncrement:true});
      };
      req.onsuccess = e => { this._db = e.target.result; res(this._db); };
      req.onerror = e => rej(e);
    });
  },
  async cacheGet(path){
    const db = await this.open();
    return new Promise(res=>{
      const tx = db.transaction('cache','readonly');
      const req = tx.objectStore('cache').get(path);
      req.onsuccess = () => res(req.result?.data ?? null);
      req.onerror = () => res(null);
    });
  },
  async cacheSet(path, data){
    const db = await this.open();
    return new Promise(res=>{
      const tx = db.transaction('cache','readwrite');
      tx.objectStore('cache').put({path, data, ts: Date.now()});
      tx.oncomplete = () => res();
      tx.onerror = () => res();
    });
  },
  async cachePatch(path, patch){
    // merge patch into cached object
    const existing = await this.cacheGet(path) || {};
    const merged = {...existing, ...patch};
    await this.cacheSet(path, merged);
  },
  async cacheDel(path){
    // set cached value to null so get returns null
    await this.cacheSet(path, null);
    // also remove child from parent path
    const parts = path.split('/');
    if(parts.length >= 2){
      const parentPath = parts.slice(0,-1).join('/');
      const key = parts[parts.length-1];
      const parent = await this.cacheGet(parentPath);
      if(parent && typeof parent === 'object'){
        const updated = {...parent};
        delete updated[key];
        await this.cacheSet(parentPath, Object.keys(updated).length ? updated : null);
      }
    }
  },
  async cachePush(path, data, localId){
    // add to parent object in cache
    const parent = await this.cacheGet(path) || {};
    parent[localId] = data;
    await this.cacheSet(path, parent);
  },
  async queueAdd(op){
    const db = await this.open();
    return new Promise(res=>{
      const tx = db.transaction('queue','readwrite');
      const req = tx.objectStore('queue').add({...op, ts: Date.now()});
      req.onsuccess = () => res(req.result);
      req.onerror = () => res(null);
    });
  },
  async queueGetAll(){
    const db = await this.open();
    return new Promise(res=>{
      const tx = db.transaction('queue','readonly');
      const req = tx.objectStore('queue').getAll();
      req.onsuccess = () => res(req.result || []);
      req.onerror = () => res([]);
    });
  },
  async queueDelete(id){
    const db = await this.open();
    return new Promise(res=>{
      const tx = db.transaction('queue','readwrite');
      tx.objectStore('queue').delete(id);
      tx.oncomplete = () => res();
    });
  },
  async queueCount(){
    const db = await this.open();
    return new Promise(res=>{
      const tx = db.transaction('queue','readonly');
      const req = tx.objectStore('queue').count();
      req.onsuccess = () => res(req.result);
      req.onerror = () => res(0);
    });
  }
};

// ── SYNC ENGINE ─────────────────────────
const Sync = {
  _running: false,
  _pendingCount: 0,

  async flush(){
    if(this._running || !navigator.onLine) return;
    const items = await IDB.queueGetAll();
    if(!items.length){ this._updateBadge(0); return; }
    this._running = true;
    DB._sync('syncing');
    let failed = 0;
    for(const op of items){
      try{
        if(op.method==='POST'){
          const r = await fetch(`${RTDB_URL}/${op.path}.json`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(op.data)});
          const j = await r.json();
          if(j.error) throw new Error(j.error);
          // remap local ID → real Firebase ID in cache
          if(op.localId && j.name && j.name !== op.localId){
            await this._remapCacheId(op.path, op.localId, j.name, op.data);
          }
        } else if(op.method==='PUT'){
          await fetch(`${RTDB_URL}/${op.path}.json`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(op.data)});
        } else if(op.method==='PATCH'){
          await fetch(`${RTDB_URL}/${op.path}.json`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify(op.data)});
        } else if(op.method==='DELETE'){
          await fetch(`${RTDB_URL}/${op.path}.json`,{method:'DELETE'});
        }
        await IDB.queueDelete(op.id);
      } catch(e){
        failed++;
        console.warn('Sync failed for op', op, e);
      }
    }
    this._running = false;
    const remaining = await IDB.queueCount();
    this._updateBadge(remaining);
    if(!failed){
      DB._sync('ok');
      // refresh data from Firebase to get real IDs
      await loadAll();
      if(remaining === 0) toast('✅ All changes synced to Firebase!','success');
    } else {
      DB._sync('offline');
    }
  },

  async _remapCacheId(parentPath, localId, realId, data){
    const parent = await IDB.cacheGet(parentPath) || {};
    if(parent[localId]){
      parent[realId] = {...parent[localId], id: realId};
      delete parent[localId];
      await IDB.cacheSet(parentPath, parent);
    }
  },

  _updateBadge(count){
    this._pendingCount = count;
    const dot = document.getElementById('syncDot');
    if(!dot) return;
    if(count > 0){
      dot.title = `${count} change(s) pending sync`;
    }
  },

  start(){
    // Listen for online/offline events
    window.addEventListener('online', async ()=>{
      S.isOnline = true;
      DB._sync('syncing');
      toast('🌐 Back online — syncing...','info');
      await this.flush();
    });
    window.addEventListener('offline', ()=>{
      S.isOnline = false;
      DB._sync('offline');
      toast('📴 Offline — changes saved locally','info');
    });
    // Periodic sync every 30s when online
    setInterval(()=>{ if(navigator.onLine) this.flush(); }, 30000);
  }
};

// ── OFFLINE-AWARE DB ─────────────────────
const DB = {
  _sync(s){
    const d=document.getElementById('syncDot');if(!d)return;
    d.className='sync-dot'+(s==='ok'?'':s==='syncing'?' syncing':' offline');
  },

  async get(path){
    if(navigator.onLine){
      try{
        this._sync('syncing');
        const r = await fetch(`${RTDB_URL}/${path}.json`,{signal:AbortSignal.timeout(8000)});
        const data = await r.json();
        if(data && data.error) throw new Error('Firebase: '+data.error);
        this._sync('ok');
        // Cache the fresh data
        await IDB.cacheSet(path, data);
        return data;
      }catch(e){
        this._sync('offline');
        // Fallback to cache
        const cached = await IDB.cacheGet(path);
        if(cached !== null) return cached;
        throw e;
      }
    } else {
      // Offline: read from cache
      this._sync('offline');
      const cached = await IDB.cacheGet(path);
      return cached;
    }
  },

  async push(path, data){
    const localId = 'local_' + Date.now() + '_' + Math.random().toString(36).slice(2,7);
    // Always update local cache immediately
    await IDB.cachePush(path, data, localId);
    if(navigator.onLine){
      try{
        const r = await fetch(`${RTDB_URL}/${path}.json`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
        const j = await r.json();
        if(j.error) throw new Error(j.error);
        // Replace local ID with real ID in cache
        if(j.name !== localId){
          await Sync._remapCacheId(path, localId, j.name, data);
        }
        return j.name;
      }catch(e){
        // Queue for later sync
        await IDB.queueAdd({method:'POST',path,data,localId});
        Sync._updateBadge((await IDB.queueCount()));
        return localId;
      }
    } else {
      await IDB.queueAdd({method:'POST',path,data,localId});
      Sync._updateBadge((await IDB.queueCount()));
      return localId;
    }
  },

  async set(path, data){
    await IDB.cacheSet(path, data);
    if(navigator.onLine){
      try{
        await fetch(`${RTDB_URL}/${path}.json`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
        return;
      }catch(e){}
    }
    await IDB.queueAdd({method:'PUT',path,data});
    Sync._updateBadge((await IDB.queueCount()));
  },

  async patch(path, data){
    await IDB.cachePatch(path, data);
    if(navigator.onLine){
      try{
        await fetch(`${RTDB_URL}/${path}.json`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
        return;
      }catch(e){}
    }
    await IDB.queueAdd({method:'PATCH',path,data});
    Sync._updateBadge((await IDB.queueCount()));
  },

  async del(path){
    await IDB.cacheDel(path);
    if(navigator.onLine){
      try{
        await fetch(`${RTDB_URL}/${path}.json`,{method:'DELETE'});
        return;
      }catch(e){}
    }
    await IDB.queueAdd({method:'DELETE',path});
    Sync._updateBadge((await IDB.queueCount()));
  },

  obj2arr(o){if(!o)return[];return Object.entries(o).map(([id,v])=>({id,...v}));}
};

// ── INIT ────────────────────────────────
async function init(){
  // Open IndexedDB first
  await IDB.open();
  Sync.start();

  const isOnline = navigator.onLine;
  txt(isOnline ? 'Connecting to Firebase...' : '📴 Loading offline data...');

  try{
    let st=null; try{st=await DB.get('settings/main');}catch(e){st=await IDB.cacheGet('settings/main');} if(st)Object.assign(S.settings,st);
    await loadAll();
    document.getElementById('loader').classList.add('hide');
    startClock(); startPoll(); setupPWA();

    const pending = await IDB.queueCount();
    if(!isOnline){
      toast('📴 Offline mode — data loaded from local cache','info');
    } else if(pending > 0){
      toast(`🔄 Syncing ${pending} offline change(s)...`,'info');
      Sync.flush();
    } else {
      toast('Connected! 🔥','success');
    }
  }catch(e){
    // Try loading from cache even if Firebase failed
    const cachedItems = await IDB.cacheGet('items');
    const cachedCats = await IDB.cacheGet('categories');
    if(cachedItems || cachedCats){
      S.categories = DB.obj2arr(await IDB.cacheGet('categories')).sort((a,b)=>(a.name||'').localeCompare(b.name||''));
      S.items = DB.obj2arr(cachedItems);
      S.sales = DB.obj2arr(await IDB.cacheGet('sales')||{}).sort((a,b)=>new Date(b.date)-new Date(a.date)).slice(0,100);
      POS.renderCats(); POS.renderGrid(); InvTable.render();
      document.getElementById('loader').classList.add('hide');
      startClock(); startPoll(); setupPWA();
      DB._sync('offline');
      toast('📴 Offline — cached data loaded','info');
    } else {
      // First time with no cache — show Firebase setup guide
      txt('❌ '+e.message);
      document.getElementById('loader').innerHTML=`<div style="text-align:center;padding:30px;max-width:420px"><div style="font-size:40px;margin-bottom:16px">❌</div><div style="font-size:18px;font-weight:700;color:#ef4444;margin-bottom:10px">Firebase Connection Failed</div><div style="color:#94a3b8;font-size:13px;line-height:1.7;margin-bottom:20px">First time setup needs internet.<br><br>1️⃣ <a href="https://console.firebase.google.com" target="_blank" style="color:#00d4ff">console.firebase.google.com</a><br>2️⃣ Project → Realtime Database<br>3️⃣ Rules tab → Paste:<br></div><div style="background:#101322;border:1px solid #1e293b;border-radius:8px;padding:14px;text-align:left;font-family:monospace;font-size:12px;color:#00d4ff;margin-bottom:16px">{<br>&nbsp;&nbsp;"rules":{<br>&nbsp;&nbsp;&nbsp;&nbsp;".read":true,<br>&nbsp;&nbsp;&nbsp;&nbsp;".write":true<br>&nbsp;&nbsp;}<br>}</div>4️⃣ Publish<br><br><button onclick="location.reload()" style="margin-top:16px;padding:10px 24px;background:linear-gradient(135deg,#00d4ff,#00a8cc);color:#000;border:none;border-radius:8px;font-weight:700;cursor:pointer;font-size:14px">🔄 Retry</button></div>`;
    }
  }
}

async function loadAll(){
  const[cats,items,sales]=await Promise.all([DB.get('categories'),DB.get('items'),DB.get('sales')]);
  S.categories=DB.obj2arr(cats).sort((a,b)=>(a.name||'').localeCompare(b.name||''));
  S.items=DB.obj2arr(items);
  S.sales=DB.obj2arr(sales).sort((a,b)=>new Date(b.date)-new Date(a.date)).slice(0,100);
  POS.renderCats();POS.renderGrid();InvTable.render();
  if(S.adminUnlocked)Admin.render();
  if(document.getElementById('barcodesTab')?.classList.contains('active')){BarcodeTab.render();}
}
function startPoll(){
  if(S.pollTimer)clearInterval(S.pollTimer);
  // Poll every 8s when online, skip when offline
  S.pollTimer=setInterval(()=>{ if(navigator.onLine) loadAll(); },8000);
}
function startClock(){const el=document.getElementById('clock');const t=()=>{if(el)el.textContent=new Date().toLocaleTimeString('en-US',{hour12:false});};t();setInterval(t,1000);}
function txt(t){const el=document.getElementById('loaderText');if(el)el.textContent=t;}
function toast(msg,type='info'){const d=document.createElement('div');d.className=`toast toast-${type}`;d.textContent=msg;document.getElementById('toasts').appendChild(d);setTimeout(()=>d.remove(),3500);}

// ── NAV / MODAL ─────────────────────────
const Nav={go(tab){document.querySelectorAll('.nav-tab').forEach(t=>t.classList.toggle('active',t.dataset.tab===tab));document.querySelectorAll('.tab-panel').forEach(p=>p.classList.toggle('active',p.id===tab+'Tab'));if(tab==='admin')Admin.render();if(tab==='inventory')InvTable.render();if(tab==='barcodes')BarcodeTab.render();if(tab==='loans')LoanMgr.render();if(tab==='repairs')RepairMgr.render();if(tab==='returns')ReturnMgr.switchTab('customer');}};
const Modal={open(id){document.getElementById(id).classList.add('open');},close(id){document.getElementById(id).classList.remove('open');}};

// ── CATEGORIES ──────────────────────────
const Cats={
  async add(n){if(!n.trim())return;await DB.push('categories',{name:n.trim(),createdAt:new Date().toISOString()});await loadAll();toast('Category added ✅','success');},
  async del(id){if(!confirm('Delete category?'))return;await DB.del('categories/'+id);await loadAll();toast('Deleted','info');}
};
const CatMgr={
  open(){this.render();Modal.open('catModal');},
  render(){const b=document.getElementById('catBody');if(!b)return;let h=`<div class="flex gap-8 mb-8"><input id="newCatName" type="text" placeholder="Category name..." style="flex:1"><button class="btn btn-primary btn-sm" onclick="Cats.add(document.getElementById('newCatName').value).then(()=>CatMgr.render())">Add</button></div>`;if(!S.categories.length)h+=`<div style="text-align:center;color:var(--t3);padding:20px">No categories yet</div>`;else S.categories.forEach(c=>{h+=`<div style="display:flex;align-items:center;justify-content:space-between;padding:7px 10px;background:var(--glass);border:1px solid var(--border);border-radius:var(--r8);margin-bottom:5px"><span style="font-weight:600">${esc(c.name)}</span><button class="btn btn-danger btn-xs" onclick="Cats.del('${c.id}').then(()=>CatMgr.render())">Delete</button></div>`;});b.innerHTML=h;}
};

// ── POS ─────────────────────────────────
const POS={
  renderCats(){const bar=document.getElementById('catBar');if(!bar)return;const cur=S.activeCat;let h=`<div class="cat-chip ${cur==='all'?'active':''}" onclick="POS.setCat('all')">🌐 All</div>`;S.categories.forEach(c=>{h+=`<div class="cat-chip ${cur===c.id?'active':''}" onclick="POS.setCat('${c.id}')">${esc(c.name)}</div>`;});h+=`<div class="cat-chip cat-chip-add" id="addCatChip">+ Category</div>`;bar.innerHTML=h;bar.querySelector('#addCatChip').onclick=async()=>{const n=prompt('New category:');if(n)await Cats.add(n);};},
  setCat(id){S.activeCat=id;this.renderCats();this.renderGrid();},
  setMode(mode){
    S.cartMode = mode;
    const ids = {retail:'gModeRetail', wholesale:'gModeWholesale', loan:'gModeLoan', ws_loan:'gModeWsLoan'};
    for(const k in ids) {
      const el = document.getElementById(ids[k]);
      if(el) el.className = 'btn btn-sm ' + (mode === k ? 'active' : 'btn-ghost');
    }
    S.cart.forEach((ci, idx) => Cart.setSaleMode(idx, mode, true));
    Cart.render();
  },
  filter(){this.renderGrid();},
  getItems(){let items=[...S.items];if(S.activeCat!=='all')items=items.filter(i=>i.categoryId===S.activeCat);const q=document.getElementById('posSearch')?.value.trim();if(q){const f=new Fuse(items,{keys:['name','barcode','description'],threshold:0.4});items=f.search(q).map(r=>r.item);}return items;},
  renderGrid(){
    const grid=document.getElementById('itemGrid');if(!grid)return;
    const items=this.getItems();
    if(!items.length){grid.innerHTML=`<div class="empty-state"><div class="empty-state-icon">🔍</div><div>No products found</div></div>`;return;}
    const cur=S.settings.currency;
    grid.innerHTML=items.map(item=>{
      const stock=getStock(item);const isOut=stock<=0;const isLow=stock>0&&stock<=(item.lowStockThreshold||S.settings.lowStockAlert);
      const ce=getCatEmoji(item.categoryId);
      const img=item.image?`<div class="item-img-wrap"><img src="${esc(item.image)}" alt="" loading="lazy" onerror="this.parentNode.innerHTML='${ce}'"></div>`:`<div class="item-emoji">${ce}</div>`;
      const badge=isOut?`<span class="badge badge-red">Out</span>`:isLow?`<span class="badge badge-orange">Low</span>`:`<span class="badge badge-green">✓</span>`;
      const st=(item.unit==='kg'||item.unit==='L')?stock.toFixed(2):Math.floor(stock);
      const wsP=item.wholesalePrice?`<div class="item-card-ws">W: ${cur} ${parseFloat(item.wholesalePrice).toFixed(2)}</div>`:'';
      return `<div class="item-card ${isOut?'out-stock':isLow?'low-stock':''}" onclick="POS.addFlow('${item.id}')">${img}<div class="item-card-name">${esc(item.name)}</div><div class="item-card-price">${cur} ${(item.price||0).toFixed(2)}<span style="font-size:9px;color:var(--t3)"> /${item.unit||'u'}</span></div>${wsP}<div class="item-card-meta">${badge}<span class="item-card-stock mono">${st} ${item.unit||''}</span></div></div>`;
    }).join('');
  },
  addFlow(itemId){
    const item=S.items.find(i=>i.id===itemId);
    if(!item||getStock(item)<=0){toast('Out of stock!','error');return;}
    if(item.type==='weighted'){this.showWeightedModal(item);}
    else if(item.type==='liquid'&&item.liquidOptions?.length){this.showLiquidModal(item);}
    else{Cart.add(item, 1, null, null, S.cartMode || 'retail');}
  },
  showSaleTypePicker(item,qty,label=null,fixedPrice=null){
    const cur=S.settings.currency;
    const retP=fixedPrice!==null?fixedPrice:qty*(item.price||0);
    const wsP=qty*(parseFloat(item.wholesalePrice)||item.price||0);
    document.getElementById('saleTypeContent').innerHTML=`
      <div class="modal-head"><div class="modal-title">📱 ${esc(item.name)}</div><button class="modal-x" onclick="Modal.close('saleTypeModal')">✕</button></div>
      <p style="color:var(--t2);font-size:12px;margin-bottom:14px">Select sale type:</p>
      <div class="sale-type-grid three">
        <div class="sale-type-card retail" onclick="POS.confirmSale('${item.id}',${qty},'retail','${label||''}',${fixedPrice??'null'})">
          <div class="stc-icon">🏪</div>
          <div class="stc-label">Retail</div>
          <div class="stc-sublabel">Normal price</div>
          <div class="stc-price">${cur} ${retP.toFixed(2)}</div>
        </div>
        <div class="sale-type-card wholesale" onclick="POS.confirmSale('${item.id}',${qty},'wholesale','${label||''}',${fixedPrice??'null'})">
          <div class="stc-icon">📦</div>
          <div class="stc-label">Wholesale</div>
          <div class="stc-sublabel">Bulk / dealer</div>
          <div class="stc-price">${cur} ${wsP.toFixed(2)}</div>
        </div>
        <div class="sale-type-card loan" onclick="POS.confirmSale('${item.id}',${qty},'loan','${label||''}',${fixedPrice??'null'})">
          <div class="stc-icon">💳</div>
          <div class="stc-label">Loan</div>
          <div class="stc-sublabel">Nayata aragena yanawa</div>
          <div class="stc-price">${cur} ${retP.toFixed(2)}</div>
        </div>
      </div>`;
    Modal.open('saleTypeModal');
  },
  confirmSale(itemId,qty,mode,label,fp){
    const item=S.items.find(i=>i.id===itemId);if(!item)return;
    Cart.add(item,qty,label||null,fp!==null&&fp!=='null'?parseFloat(fp):null,S.cartMode || 'retail');
    Modal.close('saleTypeModal');
  },
  showWeightedModal(item){
    const cur=S.settings.currency;
    document.getElementById('optContent').innerHTML=`
      <div class="modal-head"><div class="modal-title">⚖️ ${esc(item.name)}</div><button class="modal-x" onclick="Modal.close('optModal')">✕</button></div>
      <div class="opt-tabs"><div class="opt-tab active" id="wt1" onclick="POS.wTab(1)">💰 By Amount (${cur})</div><div class="opt-tab" id="wt2" onclick="POS.wTab(2)">⚖️ By ${item.unit==='kg'?'Kg':'Qty'}</div></div>
      <div id="wBody1">
        <div class="form-group"><label>Customer pays (${cur})</label><input type="number" id="wAmt" placeholder="100.00" min="0" step="0.5" autofocus></div>
        <p style="font-size:11px;color:var(--t2);margin-bottom:12px">${cur} ${item.price} / ${item.unit} — weight auto calculated</p>
        <button class="btn btn-primary w-full" onclick="POS.confirmWAmt('${item.id}')">Next →</button>
      </div>
      <div id="wBody2" style="display:none">
        <div class="form-group"><label>${item.unit==='kg'?'Weight (kg)':'Quantity'}</label><input type="number" id="wQty" placeholder="1.0" min="0.001" step="0.001"></div>
        <button class="btn btn-primary w-full" onclick="POS.confirmWQty('${item.id}')">Next →</button>
      </div>`;
    Modal.open('optModal');
  },
  wTab(n){document.getElementById('wt1').classList.toggle('active',n===1);document.getElementById('wt2').classList.toggle('active',n===2);document.getElementById('wBody1').style.display=n===1?'':'none';document.getElementById('wBody2').style.display=n===2?'':'none';},
  confirmWAmt(id){const item=S.items.find(i=>i.id===id);const amt=parseFloat(document.getElementById('wAmt').value);if(!amt||amt<=0){toast('Enter amount','error');return;}const qty=amt/item.price;Modal.close('optModal');this.showSaleTypePicker(item,qty,null,amt);},
  confirmWQty(id){const item=S.items.find(i=>i.id===id);const qty=parseFloat(document.getElementById('wQty').value);if(!qty||qty<=0){toast('Enter quantity','error');return;}Modal.close('optModal');this.showSaleTypePicker(item,qty);},
  showLiquidModal(item){
    const cur=S.settings.currency;
    const oh=item.liquidOptions.map((o,i)=>`<div class="liq-opt" onclick="POS.selectLiq('${item.id}',${i})"><div class="lbl">${esc(o.label)}</div><div class="prc">${cur} ${(o.price||0).toFixed(2)}</div><div class="amt">${o.amount} ${item.unit}</div></div>`).join('');
    document.getElementById('optContent').innerHTML=`
      <div class="modal-head"><div class="modal-title">🫙 ${esc(item.name)}</div><button class="modal-x" onclick="Modal.close('optModal')">✕</button></div>
      <p style="color:var(--t2);font-size:12px;margin-bottom:10px">Select option:</p>
      <div class="liq-grid">${oh}</div>
      <hr class="divider">
      <div class="form-group"><label>Custom (${item.unit})</label><div class="flex gap-8"><input type="number" id="custLiq" placeholder="0.5" step="0.01" style="flex:1"><button class="btn btn-ghost btn-sm" onclick="POS.custLiq('${item.id}')">Next →</button></div></div>`;
    Modal.open('optModal');
  },
  selectLiq(id,idx){const item=S.items.find(i=>i.id===id);const o=item.liquidOptions[idx];Modal.close('optModal');this.showSaleTypePicker(item,o.amount,o.label,o.price);},
  custLiq(id){const item=S.items.find(i=>i.id===id);const amt=parseFloat(document.getElementById('custLiq').value);if(!amt||amt<=0){toast('Enter amount','error');return;}Modal.close('optModal');this.showSaleTypePicker(item,amt,null,amt*item.price);},
  scanBarcode(code) {
    if (!code) return;
    const item = S.items.find(i => (i.barcode||'').split(',').map(b=>b.trim()).includes(code.trim()));
    if (item) {
      if(item.type==='weighted'||item.type==='liquid') this.addFlow(item.id);
      else Cart.add(item, 1, null, null, S.cartMode || 'retail');
    } else toast('Barcode not found: ' + code, 'error');
  }
};

// ── CART ────────────────────────────────
const Cart={
  add(item,qty,label=null,fixedPrice=null,mode='retail'){
    const ret=item.price||0;const ws=parseFloat(item.wholesalePrice)||ret;
    let unitPrice,price;
    if(mode==='wholesale'||mode==='ws_loan'){unitPrice=ws;price=fixedPrice!==null?fixedPrice:qty*ws;}
    else{unitPrice=ret;price=fixedPrice!==null?fixedPrice:qty*ret;}
    // loan mode: uses retail or ws price but marked as loan
    if(item.type!=='weighted'&&item.type!=='liquid'&&mode!=='loan'&&mode!=='ws_loan'){
      const ex=S.cart.findIndex(c=>c.itemId===item.id&&!c.label&&c.saleMode===mode);
      if(ex>=0){S.cart[ex].qty+=qty;S.cart[ex].price=S.cart[ex].qty*S.cart[ex].unitPrice;this.render();return;}
    }
    S.cart.push({itemId:item.id,name:item.name,qty,unit:item.unit||'pcs',retailP:ret,wsP:ws,unitPrice,price,label,discount:item.defaultDiscount||0,discType:'percent',saleMode:mode});
    this.render();
    const ml={'retail':'🏪 Retail','wholesale':'📦 Wholesale','loan':'💳 R.Loan','ws_loan':'💳 W.Loan'};
    toast(`${item.name} [${ml[mode]||mode}] added ✓`,'success');
  },
  setSaleMode(i,mode,silent=false){
    const ci=S.cart[i];ci.saleMode=mode;
    if(mode==='wholesale'||mode==='ws_loan'){ci.unitPrice=ci.wsP;ci.price=ci.qty*ci.wsP;}
    else{ci.unitPrice=ci.retailP;ci.price=ci.qty*ci.retailP;}
    ci.discount=0;if(!silent)this.render();
  },
  remove(i){S.cart.splice(i,1);this.render();},
  updateQty(i,delta){const ci=S.cart[i];const isW=ci.unit==='kg'||ci.unit==='L';const nq=Math.max(0.001,ci.qty+delta);ci.qty=nq;ci.price=nq*ci.unitPrice;this.render();},
  clear(){if(!S.cart.length)return;if(!confirm('Clear cart?'))return;S.cart=[];S.cartDisc=0;this.render();},
  sub(){return S.cart.reduce((s,ci)=>{const d=ci.discType==='percent'?ci.price*(ci.discount||0)/100:(ci.discount||0);return s+ci.price-d;},0);},
  total(){const sub=this.sub();const d=S.cartDiscType==='percent'?sub*S.cartDisc/100:S.cartDisc;return Math.max(0,sub-d);},
  render(){
    const cur=S.settings.currency;
    const body=document.getElementById('cartBody');const count=document.getElementById('cartCount');
    const btn=document.getElementById('checkoutBtn');const subEl=document.getElementById('cartSub');
    const totEl=document.getElementById('cartTotal');const dr=document.getElementById('discRow');const da=document.getElementById('discAmt');
    count.textContent=S.cart.length;
    const sub=this.sub();const total=this.total();
    subEl.textContent=`${cur} ${sub.toFixed(2)}`;totEl.textContent=`${cur} ${total.toFixed(2)}`;
    const dv=sub-total;dr.style.display=dv>0?'':'none';da.textContent=`-${cur} ${dv.toFixed(2)}`;
    
    const loanItems=S.cart.filter(ci=>ci.saleMode==='loan'||ci.saleMode==='ws_loan');
    const nonLoanItems=S.cart.filter(ci=>ci.saleMode!=='loan'&&ci.saleMode!=='ws_loan');
    const loanRow=document.getElementById('loanRow');
    const loanItemCount=document.getElementById('loanItemCount');
    const loanBtn=document.getElementById('loanCheckoutBtn');
    if(loanRow){loanRow.style.display=loanItems.length?'':'none';}
    if(loanItemCount)loanItemCount.textContent=loanItems.length+' item'+(loanItems.length!==1?'s':'');
    if(loanBtn)loanBtn.style.display=loanItems.length?'':'none';
    if(loanBtn)loanBtn.disabled=!loanItems.length;
    btn.disabled=nonLoanItems.length===0&&loanItems.length>0?false:S.cart.length===0;
    
    if(nonLoanItems.length===0&&loanItems.length>0)btn.disabled=true;
    if(!S.cart.length){body.innerHTML=`<div class="cart-empty"><div style="font-size:38px;opacity:.3">🛒</div><div>Cart is empty</div><div style="font-size:11px">Click items to add</div></div>`;return;}
    
    body.innerHTML=S.cart.map((ci,idx)=>{
      const isW=ci.unit==='kg'||ci.unit==='L';
      const d=ci.discType==='percent'?ci.price*(ci.discount||0)/100:(ci.discount||0);
      const net=ci.price-d;const qd=isW?ci.qty.toFixed(3):ci.qty;
      const m=ci.saleMode||'retail';
      const isLoan=m==='loan'||m==='ws_loan';
      const modeLabel=m==='ws_loan'?'💳 W.LOAN':m==='loan'?'💳 R.LOAN':m==='wholesale'?'📦 WS':'🏪 Retail';
      const modeBadge=`<span style="font-size:10px;color:${isLoan?'var(--red)':m==='wholesale'?'var(--purple)':'var(--cyan)'};font-weight:700;margin-left:4px;">${modeLabel}</span>`;
      const priceTxt=isLoan?`<span style="color:var(--red);font-size:11px;">💳 LOAN</span>`:`${cur} ${net.toFixed(2)}`;
      
      return `<div class="cart-item"${isLoan?' style="border-color:rgba(239,68,68,0.3);"':m==='wholesale'?' style="border-color:rgba(139,92,246,0.3);"':''}>
        <button class="cart-item-del" onclick="Cart.remove(${idx})">✕</button>
        <div class="cart-item-name">${esc(ci.name)}${ci.label?` <span style="color:var(--t3)">(${esc(ci.label)})</span>`:''} ${modeBadge}</div>
        <div class="cart-item-row">
          <div class="qty-ctrl">
            <button class="qty-btn" onclick="Cart.updateQty(${idx},${isW?-0.1:-1})">−</button>
            <span class="qty-val">${qd} ${ci.unit}</span>
            <button class="qty-btn" onclick="Cart.updateQty(${idx},${isW?0.1:1})">+</button>
          </div>
          <span class="cart-item-price">${priceTxt}</span>
        </div>
        ${ci.discount?`<div class="cart-disc-tag">Disc: ${ci.discount}${ci.discType==='percent'?'%':' '+cur}</div>`:''}
      </div>`;
    }).join('');
  }
};

// ── DISCOUNT ────────────────────────────
const Discount={
  openCart(){
    const cur=S.settings.currency;
    document.getElementById('discTitle').textContent='💰 Cart Discount';
    document.getElementById('discBody').innerHTML=`<div class="form-group"><label>Type</label><select id="dType"><option value="percent" ${S.cartDiscType==='percent'?'selected':''}>% Percentage</option><option value="fixed" ${S.cartDiscType==='fixed'?'selected':''}>Fixed (${cur})</option></select></div><div class="form-group"><label>Value</label><input type="number" id="dVal" value="${S.cartDisc}" min="0" step="0.5"></div><div class="modal-foot"><button class="btn btn-ghost" onclick="S.cartDisc=0;Cart.render();Modal.close('discModal')">Remove</button><button class="btn btn-primary" onclick="S.cartDiscType=document.getElementById('dType').value;S.cartDisc=parseFloat(document.getElementById('dVal').value)||0;Cart.render();Modal.close('discModal');toast('Discount applied','success')">Apply</button></div>`;
    Modal.open('discModal');
  },
  openItem(){
    if(!S.cart.length){toast('Cart empty','error');return;}
    const cur=S.settings.currency;
    document.getElementById('discTitle').textContent='🏷️ Item Discount';
    const opts=S.cart.map((c,i)=>`<option value="${i}">${esc(c.name)} [${c.saleMode}]</option>`).join('');
    document.getElementById('discBody').innerHTML=`<div class="form-group"><label>Item</label><select id="dItem">${opts}</select></div><div class="form-group"><label>Type</label><select id="dIT"><option value="percent">%</option><option value="fixed">Fixed</option></select></div><div class="form-group"><label>Value</label><input type="number" id="dIV" value="0" min="0" step="0.5"></div><div class="modal-foot"><button class="btn btn-ghost" onclick="Modal.close('discModal')">Cancel</button><button class="btn btn-primary" onclick="const i=parseInt(document.getElementById('dItem').value);S.cart[i].discount=parseFloat(document.getElementById('dIV').value)||0;S.cart[i].discType=document.getElementById('dIT').value;Cart.render();Modal.close('discModal');toast('Applied','success')">Apply</button></div>`;
    Modal.open('discModal');
  }
};

const Checkout={
  async runWithLoan(){
    const loanItems=S.cart.filter(ci=>ci.saleMode==='loan'||ci.saleMode==='ws_loan');
    const nonLoanItems=S.cart.filter(ci=>ci.saleMode!=='loan'&&ci.saleMode!=='ws_loan');
    if(!loanItems.length){toast('No loan items in cart','error');return;}

    // Process non-loan items as a normal sale if any
    if(nonLoanItems.length){
      const tempCart=[...S.cart];
      S.cart=nonLoanItems;
      await this.run(true);
      S.cart=loanItems;
    }

    // Build prefill data for Loans page
    const cur=S.settings.currency;
    const desc=loanItems.map(ci=>ci.name).join(', ');
    const itemDetails=loanItems.map(ci=>`${ci.name} x${ci.qty} (${cur} ${ci.price.toFixed(2)})`).join('\n');
    const total=loanItems.reduce((s,ci)=>s+ci.price,0);

    // Store prefill in session
    window._loanPrefill={desc,itemDetails,total};

    // Clear loan items from cart
    S.cart=S.cart.filter(ci=>ci.saleMode!=='loan'&&ci.saleMode!=='ws_loan');
    Cart.render();

    // Navigate to Loans tab
    Nav.go('loans');
    await LoanMgr.render();

    // Open new loan form with prefill
    setTimeout(()=>{
      LoanMgr.openNewLoan(null, window._loanPrefill);
    },400);

    toast('💳 Loan items moved to Loans page!','success');
  },
  async run(silent=false){
    if(!S.cart.length)return;
    const total=Cart.total();const sub=Cart.sub();let profit=0;const saleItems=[];
    // Generate human-readable bill ID: date + seq
    const billDate=new Date();
    const billId='BILL-'+billDate.getFullYear().toString().slice(-2)+String(billDate.getMonth()+1).padStart(2,'0')+String(billDate.getDate()).padStart(2,'0')+'-'+String(billDate.getHours()).padStart(2,'0')+String(billDate.getMinutes()).padStart(2,'0')+String(billDate.getSeconds()).padStart(2,'0');

    for(const ci of S.cart){
      const item=S.items.find(i=>i.id===ci.itemId);if(!item)continue;
      let remaining=ci.qty;const batches=[...(item.stockBatches||[])];
      let itemCostTotal=0;
      for(let i=0;i<batches.length&&remaining>0;i++){
        if((batches[i].quantity||0)<=0)continue;
        const used=Math.min(batches[i].quantity,remaining);
        // Fix: use batch costPrice, fall back to item costPrice, never 0 if item has cost
        const unitCost=batches[i].costPrice!=null&&batches[i].costPrice>0
          ? batches[i].costPrice
          : (item.costPrice||0);
        const cost=used*unitCost;
        const sale=used*ci.unitPrice;
        const disc=ci.discType==='percent'?sale*(ci.discount||0)/100:(ci.discount||0)*(used/ci.qty);
        profit+=(sale-disc)-cost;
        itemCostTotal+=cost;
        batches[i]={...batches[i],quantity:batches[i].quantity-used};
        remaining-=used;
      }
      try{await DB.patch('items/'+item.id,{stockBatches:batches});}catch(e){}
      const da=ci.discType==='percent'?ci.price*(ci.discount||0)/100:(ci.discount||0);
      saleItems.push({
        itemId:ci.itemId,name:ci.name,qty:ci.qty,unit:ci.unit,
        unitPrice:ci.unitPrice,costPrice:item.costPrice||0,
        discount:ci.discount||0,discType:ci.discType||'percent',
        price:ci.price-da,saleMode:ci.saleMode||'retail'
      });
    }
    const cda=S.cartDiscType==='percent'?sub*S.cartDisc/100:S.cartDisc;

    // Build receipt text for storage
    const cur=S.settings.currency;const shop=S.settings.shopName;
    const dateStr=billDate.toLocaleString();
    let receiptText=`${shop}\n${dateStr}\n${billId}\n${'─'.repeat(32)}\n`;
    saleItems.forEach(si=>{
      receiptText+=`${si.name}${si.saleMode==='wholesale'?' [WS]':''}\n  ${si.qty} × ${cur}${si.unitPrice.toFixed(2)} = ${cur}${(si.price||0).toFixed(2)}\n`;
    });
    receiptText+=`${'─'.repeat(32)}\nSubtotal: ${cur}${sub.toFixed(2)}\n`;
    if(cda>0)receiptText+=`Discount: -${cur}${cda.toFixed(2)}\n`;
    receiptText+=`TOTAL: ${cur}${total.toFixed(2)}\n`;

    const sale={
      billId,date:billDate.toISOString(),
      items:saleItems,subtotal:sub,cartDiscount:cda,
      total,profit:profit-cda,status:'completed',
      receiptText
    };
    try{
      const id=await DB.push('sales',sale);sale.id=id;
      window._lastSale=sale;
      if(!silent){Receipt.show(sale);}
      S.cart=[];S.cartDisc=0;Cart.render();await loadAll();
      if(!silent)toast('Sale completed! 🎉','success');
    }catch(e){toast('Sale error: '+e.message,'error');}
  }
};

// ── RECEIPT ─────────────────────────────
const Receipt={
  show(sale){
    const cur=S.settings.currency;const shop=S.settings.shopName;
    const date=new Date(sale.date).toLocaleString();
    const ih=(sale.items||[]).map(si=>`<div class="r-line"><span>${esc(si.name)} ${si.saleMode==='wholesale'?'[WS]':''} ×${(si.qty||0).toFixed(2)}</span><span>${cur} ${(si.price||0).toFixed(2)}</span></div>`).join('');
    document.getElementById('receiptBody').innerHTML=`<div class="receipt">
      <div class="receipt-h">
        <div class="receipt-title">${esc(shop)}</div>
        <div class="receipt-sub">${date}</div>
        <div style="font-size:10px;font-weight:700;color:#333;margin-top:4px;letter-spacing:0.5px;">🧾 ${esc(sale.billId||('Sale #'+(sale.id||'').slice(-8)))}</div>
      </div>
      <div>${ih}</div>
      <div class="r-total">
        <div class="r-line"><span>Subtotal</span><span>${cur} ${(sale.subtotal||0).toFixed(2)}</span></div>
        ${sale.cartDiscount>0?`<div class="r-line"><span>Discount</span><span>-${cur} ${sale.cartDiscount.toFixed(2)}</span></div>`:''}
        <div class="r-line" style="font-size:14px;font-weight:700"><span>TOTAL</span><span>${cur} ${(sale.total||0).toFixed(2)}</span></div>
      </div>
      <div class="r-footer">${esc(S.settings.receiptNote)}</div>
    </div>`;
    const btBtn=document.getElementById('btPrintBtn');
    if(btBtn)btBtn.style.display=BTPrinter.status==='connected'?'':'none';
    Modal.open('receiptModal');
  }
};

// ── INVENTORY TABLE ─────────────────────
const InvTable={
  render(){
    const tbody=document.getElementById('invBody');if(!tbody)return;
    const q=document.getElementById('invSearch')?.value.trim().toLowerCase();
    const cur=S.settings.currency;let items=[...S.items];
    if(q)items=items.filter(i=>i.name?.toLowerCase().includes(q)||i.barcode?.toLowerCase().includes(q)||S.categories.find(c=>c.id===i.categoryId)?.name?.toLowerCase().includes(q));
    if(!items.length){tbody.innerHTML=`<tr><td colspan="9" style="text-align:center;padding:40px;color:var(--t3)">No products found ➕</td></tr>`;return;}
    tbody.innerHTML=items.map(item=>{
      const stock=getStock(item);const thresh=item.lowStockThreshold||S.settings.lowStockAlert;
      const isLow=stock<=thresh;const isOut=stock<=0;
      const cat=S.categories.find(c=>c.id===item.categoryId)?.name||'—';
      const pct=Math.min(100,(stock/Math.max(thresh*3,1))*100);
      const bc=isOut?'var(--red)':isLow?'var(--orange)':'var(--green)';
      const tl={weighted:'⚖️ Weighted',liquid:'🫙 Liquid',unit:'📦 Unit'}[item.type]||'📦 Unit';
      const bat=(item.stockBatches||[]).filter(b=>b.quantity>0).length;
      const thumb=item.image?`<div class="item-thumb"><img src="${esc(item.image)}" alt=""></div>`:`<div class="item-thumb">${getCatEmoji(item.categoryId)}</div>`;
      return `<tr><td><div class="flex items-center gap-8">${thumb}<div><div style="font-weight:700">${esc(item.name)}</div>${item.barcode?`<div style="font-size:10px;color:var(--t3);font-family:'JetBrains Mono',monospace">${esc(item.barcode)}</div>`:''}</div></div></td><td><span class="badge badge-cyan">${esc(cat)}</span></td><td style="font-size:12px">${tl}</td><td><span class="mono text-cyan">${cur} ${(item.price||0).toFixed(2)}</span></td><td><span class="mono text-purple">${item.wholesalePrice?`${cur} ${parseFloat(item.wholesalePrice).toFixed(2)}`:'—'}</span></td><td>${S.adminUnlocked?`<span class="mono" style="color:var(--t2)">${cur} ${(item.costPrice||0).toFixed(2)}</span>`:'<span style="filter:blur(5px);font-size:11px;color:var(--t3)">🔐 Admin</span>'}</td><td><div style="font-weight:700">${stock.toFixed(2)} <span style="color:var(--t3);font-size:10px">${item.unit||''}</span></div><div class="stock-bar"><div class="stock-bar-inner" style="width:${pct}%;background:${bc}"></div></div><div style="font-size:10px;color:var(--t3);margin-top:2px">${bat} batch</div></td><td>${isOut?'<span class="badge badge-red">Out</span>':isLow?'<span class="badge badge-orange">Low</span>':'<span class="badge badge-green">In Stock</span>'}</td><td><div class="flex gap-6"><button class="btn btn-ghost btn-xs" onclick="ItemForm.open('${item.id}')">✏️</button><button class="btn btn-success btn-xs" onclick="StockAdd.open('${item.id}')">+📦</button><button class="btn btn-danger btn-xs" onclick="Items.del('${item.id}')">🗑️</button></div></td></tr>`;
    }).join('');
  }
};

const StockAdd = {
  _curId: null,
  open(id) {
    this._curId = id;
    const item = S.items.find(i=>i.id===id); if(!item)return;
    const cur = S.settings.currency;
    document.getElementById('discTitle').textContent = `📦 Add Stock — ${item.name}`;
    document.getElementById('discBody').innerHTML = `
      <div class="form-group">
        <label>Barcodes / IMEIs (Optional)</label>
        <div style="display:flex;gap:6px">
          <textarea id="saBc" rows="2" style="flex:1;font-family:'JetBrains Mono',monospace;font-size:12px;" placeholder="Scan barcodes..."></textarea>
          <button class="btn btn-secondary" onclick="StockAdd.startCam()" style="flex-shrink:0" title="Scan with Camera">📷</button>
        </div>
      </div>
      <div id="saCamWrap" style="display:none;margin-bottom:10px;border-radius:8px;overflow:hidden"></div>
      <div class="form-group">
        <label>Qty (${item.unit})</label>
        <input type="number" id="saQ" placeholder="10" min="0.001" step="0.001" autofocus>
      </div>
      <div class="form-group">
        <label>Cost Price (${cur}/${item.unit})</label>
        <input type="number" id="saC" value="${item.costPrice||''}" placeholder="0.00" min="0" step="0.5">
      </div>
      <div class="form-group">
        <label>Date Received</label>
        <input type="date" id="saD" value="${new Date().toISOString().split('T')[0]}">
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="StockAdd.stopCam();Modal.close('discModal')">Cancel</button>
        <button class="btn btn-primary" onclick="StockAdd.confirm('${id}')">➕ Add Stock</button>
      </div>
    `;
    Modal.open('discModal');
    setTimeout(() => {
      const bc = document.getElementById('saBc');
      const q = document.getElementById('saQ');
      if (bc && q) {
        bc.addEventListener('input', () => {
          const arr = bc.value.split(/[\n,]+/).map(b=>b.trim()).filter(b=>b.length>0);
          if(arr.length > 0) q.value = arr.length;
        });
      }
    }, 100);
  },
  _scanner: null,
  startCam() {
    const wrap = document.getElementById('saCamWrap');
    if(this._scanner) { this.stopCam(); return; } // toggle off
    wrap.style.display = 'block';
    wrap.innerHTML = '<div id="saCamInner"></div><div style="text-align:center;font-size:11px;color:var(--t2);padding:5px;">Scanning continuously...</div>';
    this._scanner = new Html5QrcodeScanner('saCamInner', {fps:10, qrbox:{width:250,height:150}, aspectRatio:1}, false);
    let lastScan = 0, lastCode = '';
    this._scanner.render((t) => {
      const now = Date.now();
      if(t === lastCode && now - lastScan < 2000) return;
      lastScan = now; lastCode = t;
      const bc = document.getElementById('saBc');
      const q = document.getElementById('saQ');
      const arr = bc.value.split(/[\n,]+/).map(b=>b.trim()).filter(b=>b.length>0);
      if(!arr.includes(t)) {
        bc.value = (bc.value ? bc.value + '\n' : '') + t;
        q.value = arr.length + 1;
        toast('Added: ' + t, 'success');
      } else {
        toast('Already scanned!', 'info');
      }
    }, (e)=>{});
  },
  stopCam() {
    try { if(this._scanner) this._scanner.clear(); } catch(e){}
    this._scanner = null;
    const wrap = document.getElementById('saCamWrap');
    if(wrap) { wrap.style.display = 'none'; wrap.innerHTML = ''; }
  },
  async confirm(id) {
    const qty = parseFloat(document.getElementById('saQ').value);
    if(!qty || qty <= 0) { toast('Enter qty', 'error'); return; }
    const cost = parseFloat(document.getElementById('saC').value) || 0;
    const date = document.getElementById('saD').value;
    const bcRaw = document.getElementById('saBc').value;
    
    this.stopCam();
    
    const item = S.items.find(i=>i.id===id);
    const batches = [...(item.stockBatches||[])];
    batches.push({batchId:'b_'+Date.now(), quantity:qty, costPrice:cost, dateAdded:date});
    
    let updates = { stockBatches: batches };
    if(bcRaw) {
      const existingBcs = (item.barcode||'').split(',').map(b=>b.trim()).filter(b=>b.length>0);
      const newBcs = bcRaw.split(/[\n,]+/).map(b=>b.trim()).filter(b=>b.length>0);
      updates.barcode = [...new Set([...existingBcs, ...newBcs])].join(', ');
    }
    
    await DB.patch('items/'+id, updates);
    await loadAll();
    Modal.close('discModal');
    toast('Stock added: ' + qty, 'success');
  }
};

const Items={async del(id){if(!confirm('Delete?'))return;await DB.del('items/'+id);await loadAll();toast('Deleted','info');}};

    

// ── ITEM FORM ───────────────────────────
const ItemForm={
  open(id){
    const item=id?S.items.find(i=>i.id===id):null;
    document.getElementById('itemModalTitle').textContent=item?'✏️ Edit Product':'➕ Add New Product';
    document.getElementById('itemFormBody').innerHTML=this.getHTML(item);
    if(item)this.toggleType();
    if(item?.liquidOptions?.length)this.renderLiqOpts(item.liquidOptions);
    Modal.open('itemModal');
  },
  getHTML(item){
    const cur=S.settings.currency;
    const catOpts=S.categories.map(c=>`<option value="${c.id}" ${item?.categoryId===c.id?'selected':''}>${esc(c.name)}</option>`).join('');
    return `
      <div class="grid-2">
        <div class="form-group"><label>Product Name *</label><input id="fName" type="text" value="${esc(item?.name||'')}" placeholder="e.g. Samsung Galaxy A55 Case" autofocus></div>
        <div class="form-group">
          <label>Barcode</label>
          <div style="position:relative;">
            <button type="button" style="position:absolute;left:0;top:0;bottom:0;width:34px;background:var(--glass2);border:none;border-right:1px solid var(--border2);border-radius:var(--r8) 0 0 var(--r8);cursor:pointer;color:var(--cyan);font-size:14px;display:flex;align-items:center;justify-content:center;z-index:1;" onclick="Scanner.openFormScan()" title="Scan barcode">📷</button>
            <input id="fBarcode" type="text" value="${esc(item?.barcode||'')}" placeholder="Scan or type barcode..." style="padding-left:38px;" oninput="ItemForm.previewBarcode()">
          </div>
          <div id="bcPreviewWrap" style="display:${item?.barcode?'block':'none'}">
            <div class="bc-preview" id="bcPreview"></div>
          </div>
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group"><label>Category</label><select id="fCat"><option value="">-- Select --</option>${catOpts}</select></div>
        <div class="form-group"><label>Item Type</label><select id="fType" onchange="ItemForm.toggleType()"><option value="unit" ${!item?.type||item?.type==='unit'?'selected':''}>📦 Unit (pcs)</option><option value="weighted" ${item?.type==='weighted'?'selected':''}>⚖️ Weighted (kg/g)</option><option value="liquid" ${item?.type==='liquid'?'selected':''}>🫙 Liquid (L/ml)</option></select></div>
      </div>
      <div class="grid-4">
        <div class="form-group"><label>Retail Price (${cur})</label><input id="fPrice" type="number" value="${item?.price||''}" placeholder="0.00" min="0" step="0.5"></div>
        <div class="form-group"><label>Wholesale Price (${cur})</label><input id="fWsP" type="number" value="${item?.wholesalePrice||''}" placeholder="0.00" min="0" step="0.5"></div>
        <div class="form-group">${S.adminUnlocked
          ? `<label>Cost Price (${cur}) 🔐</label><input id="fCost" type="number" value="${item?.costPrice||''}" placeholder="0.00" min="0" step="0.5">`
          : `<label style="color:var(--t3)">Cost Price</label><div style="padding:9px 12px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--r8);font-size:12px;color:var(--t3);display:flex;align-items:center;gap:5px;">🔐 Admin only<input type="hidden" id="fCost" value="${item?.costPrice||0}"></div>`
        }</div>
        <div class="form-group"><label>Unit</label><input id="fUnit" type="text" value="${esc(item?.unit||'pcs')}" placeholder="pcs / kg / L"></div>
      </div>
      <div class="grid-2">
        <div class="form-group"><label>Low Stock Alert</label><input id="fThresh" type="number" value="${item?.lowStockThreshold||S.settings.lowStockAlert}" min="0"></div>
        <div class="form-group"><label>Default Discount (%)</label><input id="fDisc" type="number" value="${item?.defaultDiscount||0}" min="0" max="100"></div>
      </div>
      <div id="fWeightHint" style="display:none"><div class="info-hint">⚖️ Weighted: In POS you can enter by amount (Rs.) or weight (kg/g). Stock tracked by weight.</div></div>
      <div id="fLiquidFields" style="display:none">
        <div style="background:var(--purple2);border:1px solid rgba(139,92,246,0.25);border-radius:var(--r8);padding:14px;margin-bottom:12px;">
          <div style="font-size:13px;font-weight:700;color:var(--purple);margin-bottom:10px">🫙 Liquid Options (e.g. Quarter / Half / Full)</div>
          <div id="liqOptsList"></div>
          <button class="btn btn-ghost btn-sm mt-8" onclick="ItemForm.addLiqRow()">+ Add Option</button>
        </div>
      </div>
      ${!item?`<div class="form-group"><label>Initial Stock Quantity</label><input id="fInitStock" type="number" value="0" min="0" step="0.001"></div>`:''}
      <div class="form-group"><label>Image URL (optional)</label><input id="fImg" type="text" value="${esc(item?.image||'')}" placeholder="https://..."></div>
      <div class="form-group"><label>Description</label><textarea id="fDesc" rows="2">${esc(item?.description||'')}</textarea></div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close('itemModal')">Cancel</button>
        <button class="btn btn-primary" onclick="ItemForm.save(${item?`'${item.id}'`:null})">💾 ${item?'Update':'Save'} Product</button>
      </div>`;
  },
  toggleType(){
    const t=document.getElementById('fType')?.value;
    const u=document.getElementById('fUnit');
    document.getElementById('fWeightHint').style.display=t==='weighted'?'':'none';
    document.getElementById('fLiquidFields').style.display=t==='liquid'?'':'none';
    if(u){if(t==='weighted'&&u.value==='pcs')u.value='kg';if(t==='liquid'&&u.value==='pcs')u.value='L';if(t==='unit'&&(u.value==='kg'||u.value==='L'))u.value='pcs';}
  },
  previewBarcode(){
    const code=document.getElementById('fBarcode')?.value.trim();
    const wrap=document.getElementById('bcPreviewWrap');
    const prev=document.getElementById('bcPreview');
    if(!code||!wrap||!prev){if(wrap)wrap.style.display='none';return;}
    wrap.style.display='block';
    try{
      prev.innerHTML='<svg id="bcSvg"></svg>';
      JsBarcode('#bcSvg',code,{format:'CODE128',width:1.8,height:50,displayValue:true,fontSize:11,margin:4,background:'#ffffff',lineColor:'#000000'});
    }catch(e){prev.innerHTML=`<div style="color:var(--red);font-size:11px;padding:5px">Invalid barcode: ${e.message}</div>`;}
  },
  addLiqRow(l='',a='',p=''){const list=document.getElementById('liqOptsList');const div=document.createElement('div');div.className='flex gap-8 mb-8 items-center';div.innerHTML=`<input class="lo-l" type="text" value="${esc(l)}" placeholder="Label" style="flex:1"><input class="lo-a" type="number" value="${a}" placeholder="Qty" style="width:70px" min="0.01" step="0.01"><input class="lo-p" type="number" value="${p}" placeholder="Price" style="width:80px" min="0" step="0.5"><button class="btn btn-danger btn-xs" onclick="this.closest('div').remove()">✕</button>`;list.appendChild(div);},
  renderLiqOpts(opts){opts.forEach(o=>this.addLiqRow(o.label,o.amount,o.price));},
  getLiqOpts(){return Array.from(document.querySelectorAll('#liqOptsList .flex')).map(r=>({label:r.querySelector('.lo-l').value,amount:parseFloat(r.querySelector('.lo-a').value)||0,price:parseFloat(r.querySelector('.lo-p').value)||0})).filter(o=>o.label&&o.amount>0);},
  async save(id){
    const name=document.getElementById('fName').value.trim();
    if(!name){toast('Name required','error');return;}
    const type=document.getElementById('fType').value;
    const data={name,type,barcode:document.getElementById('fBarcode').value.trim()||null,categoryId:document.getElementById('fCat').value||null,price:parseFloat(document.getElementById('fPrice').value)||0,wholesalePrice:parseFloat(document.getElementById('fWsP').value)||null,costPrice:parseFloat(document.getElementById('fCost').value)||0,unit:document.getElementById('fUnit').value||'pcs',lowStockThreshold:parseFloat(document.getElementById('fThresh').value)||5,defaultDiscount:parseFloat(document.getElementById('fDisc').value)||0,image:document.getElementById('fImg').value.trim()||null,description:document.getElementById('fDesc').value.trim()||null,liquidOptions:type==='liquid'?this.getLiqOpts():null,updatedAt:new Date().toISOString()};
    if(!id){const iq=parseFloat(document.getElementById('fInitStock')?.value)||0;data.createdAt=new Date().toISOString();data.stockBatches=iq>0?[{batchId:'b_'+Date.now(),quantity:iq,costPrice:data.costPrice,dateAdded:new Date().toISOString().split('T')[0]}]:[];await DB.push('items',data);toast('Product added ✅','success');}
    else{await DB.patch('items/'+id,data);toast('Updated ✅','success');}
    Modal.close('itemModal');await loadAll();
  }
};


// ── BARCODE TAB ──────────────────────────
const BarcodeTab={
  _codes:[], // currently generated barcode strings

  render(){
    // Just refresh the preview if we have codes
    this._renderList();
    this._renderPreview();
  },

  filter(){}, // no-op for this design

  generateBatch(){
    const prefix=(document.getElementById('bcPrefix')?.value.trim().toUpperCase()||'JM').slice(0,4);
    const count=Math.min(40,Math.max(1,parseInt(document.getElementById('bcGenCount')?.value)||40));
    const price=document.getElementById('bcManualPrice')?.value.trim();
    const existing=new Set(this._codes.map(c=>c.code||c));

    // Generate `count` unique barcodes
    const generated=[];
    let attempts=0;
    while(generated.length<count && attempts<2000){
      attempts++;
      // Format: PREFIX + 10 digits (timestamp slice + random) → always unique
      const ts=Date.now().toString().slice(-6);
      const rand=Math.floor(Math.random()*9999).toString().padStart(4,'0');
      const seq=String(generated.length+1).padStart(3,'0');
      const code=prefix+ts+rand+seq; // e.g. JM1234565678001
      if(!existing.has(code)){generated.push({code, price});existing.add(code);}
    }

    this._codes=generated;

    const el=document.getElementById('bcGenStatus');
    if(el)el.textContent=`✅ ${generated.length} barcodes generated — select and print!`;

    this._renderList();
    this._renderPreview();
    this._updateCount();
    toast(`🔲 ${generated.length} new barcodes generated!`,'success');
  },

  _renderList(){
    const list=document.getElementById('bcList');if(!list)return;
    if(!this._codes.length){
      list.innerHTML=`<div style="text-align:center;color:var(--t3);padding:40px 16px;font-size:12px;">👆 "Generate" click කරන්න<br>barcodes generate වෙනවා</div>`;
      return;
    }
    const cur=S.settings.currency;
    list.innerHTML=this._codes.map((item,i)=>`
      <div style="display:flex;align-items:center;gap:8px;padding:7px 12px;border-bottom:1px solid var(--border);">
        <input type="checkbox" class="bc-item-chk" data-code="${item.code||item}" data-price="${item.price||''}" checked
          style="width:14px;height:14px;cursor:pointer;accent-color:var(--cyan);flex-shrink:0;"
          onchange="BarcodeTab._updateCount()">
        <div style="font-family:'JetBrains Mono',monospace;font-size:11px;color:var(--cyan);flex:1;">
          ${item.code||item}
          ${item.price?`<div style="font-size:10px;color:var(--t3);font-family:'Exo 2',sans-serif;margin-top:2px;">${cur} ${parseFloat(item.price).toFixed(2)}</div>`:''}
        </div>
        <div style="font-size:10px;color:var(--t3);">#${i+1}</div>
      </div>`).join('');
    this._updateCount();
  },

  _updateCount(){
    const checked=document.querySelectorAll('.bc-item-chk:checked').length;
    const el=document.getElementById('bcSelCount');
    if(el){el.textContent=checked;el.className='cnt'+(checked>40?' over':'');}
    clearTimeout(this._debounce);
    this._debounce=setTimeout(()=>this._renderPreview(),100);
  },

  _getSelectedCodes(){
    const codes=[];
    document.querySelectorAll('.bc-item-chk:checked').forEach(chk=>{
      if(chk.dataset.code)codes.push({code: chk.dataset.code, price: chk.dataset.price});
    });
    return codes;
  },

  _renderPreview(){
    const preview=document.getElementById('bcA4Preview');if(!preview)return;
    const selected=this._getSelectedCodes();
    // Pad to 40
    const labels=[...selected];
    while(labels.length<40)labels.push(null);

    preview.innerHTML='';
    const cur=S.settings.currency;
    labels.forEach((item,idx)=>{
      const cell=document.createElement('div');
      cell.className='bc-cell'+(item?'':' empty');
      if(item){
        cell.innerHTML=`
          ${item.price?`<div class="bc-cell-price">${cur} ${parseFloat(item.price).toFixed(2)}</div>`:''}
          <svg id="bcpv${idx}"></svg>
          <div class="bc-cell-num">${item.code}</div>`;
      }
      preview.appendChild(cell);
    });
    labels.forEach((item,idx)=>{
      if(!item)return;
      requestAnimationFrame(()=>{
        try{JsBarcode(`#bcpv${idx}`,item.code,{format:'CODE128',width:0.95,height:26,displayValue:false,margin:0,background:'#ffffff',lineColor:'#000000'});}catch(e){}
      });
    });
  },

  selectAll(){document.querySelectorAll('.bc-item-chk').forEach(c=>c.checked=true);this._updateCount();},
  clearAll(){document.querySelectorAll('.bc-item-chk').forEach(c=>c.checked=false);this._updateCount();},

  async doPrintBT(){
    const codes=this._getSelectedCodes();
    if(!codes.length){toast('First generate barcodes, then print.','error');return;}
    if(BTPrinter.status!=='connected'||!BTPrinter.characteristic){toast('No Bluetooth printer connected.','error');return;}

    const ESC=0x1B;const GS=0x1D;const LF=0x0A;
    const alignCenter = new Uint8Array([ESC, 0x61, 1]);
    const bcHeight = new Uint8Array([GS, 0x68, 70]); // height
    const bcWidth = new Uint8Array([GS, 0x77, 2]);   // width
    const bcText = new Uint8Array([GS, 0x48, 2]);    // text below
    const enc=new TextEncoder();

    let chunks = [new Uint8Array([ESC,0x40]), alignCenter, bcHeight, bcWidth, bcText];
    
    codes.forEach(item=>{
        const bcData = enc.encode('{B' + item.code);
        const bcCmd = new Uint8Array([GS, 0x6B, 73, bcData.length]);
        chunks.push(bcCmd);
        chunks.push(bcData);
        chunks.push(new Uint8Array([LF, LF, LF, LF]));
    });
    
    chunks.push(new Uint8Array([GS,0x56,0x00])); // cut
    
    const totalLen = chunks.reduce((acc, c)=>acc+c.length, 0);
    const full = new Uint8Array(totalLen);
    let offset = 0;
    chunks.forEach(c=>{ full.set(c, offset); offset += c.length; });
    
    await BTPrinter.writeChunk(full);
    toast(`🖨️ Printing ${codes.length} barcodes to POS...`,'success');
  },

  doPrintRoll(){
    const codes=this._getSelectedCodes();
    if(!codes.length){toast('First generate barcodes, then print.','error');return;}
    
    const shop=esc(S.settings.shopName);
    const cur=S.settings.currency;

    let rows = '';
    for(let i=0; i<codes.length; i+=2) {
      const c1 = codes[i];
      const c2 = codes[i+1];
      
      const lbl1 = c1 ? `
        <div class="sticker">
          <div class="s-shop">${shop}</div>
          ${c1.price ? `<div class="s-price">${cur} ${parseFloat(c1.price).toFixed(2)}</div>` : `<div class="s-price">&nbsp;</div>`}
          <svg id="sr${i}"></svg>
          <div class="s-num">${c1.code}</div>
        </div>` : '<div class="sticker empty"></div>';
        
      const lbl2 = c2 ? `
        <div class="sticker">
          <div class="s-shop">${shop}</div>
          ${c2.price ? `<div class="s-price">${cur} ${parseFloat(c2.price).toFixed(2)}</div>` : `<div class="s-price">&nbsp;</div>`}
          <svg id="sr${i+1}"></svg>
          <div class="s-num">${c2.code}</div>
        </div>` : '<div class="sticker empty"></div>';

      rows += `<div class="sticker-row">${lbl1}${lbl2}</div>`;
    }

    const inits=codes.map((item,idx)=>{
      if(!item)return'';
      const safe=item.code.replace(/\\/g,'\\\\').replace(/'/g,"\\'");
      return`try{JsBarcode('#sr${idx}','${safe}',{format:'CODE128',width:1.5,height:34,displayValue:false,margin:0,background:'#fff',lineColor:'#000'});}catch(e){}`;
    }).join('');

    const pw=window.open('','_blank','width=600,height=800');
    if(!pw){toast('Pop-up blocked.','error');return;}
    pw.document.write(`<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<title>Barcodes Roll — ${shop}</title>
<script src="https://cdn.jsdelivr.net/npm/jsbarcode@3.11.5/dist/JsBarcode.all.min.js"><\/script>
<style>
  @page{size: 80mm auto; margin:0;}
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:Arial,sans-serif;background:#fff; width: 80mm;}
  .sticker-row {
    display: flex;
    width: 80mm;
    height: 30mm;
    overflow: hidden;
    page-break-inside: avoid;
  }
  .sticker {
    width: 40mm;
    height: 30mm;
    display:flex;flex-direction:column;
    align-items:center;justify-content:center;
    text-align:center;
    padding: 1.5mm;
  }
  .sticker.empty { visibility: hidden; }
  .s-shop { font-size: 7pt; font-weight: bold; margin-bottom: 0.5mm; color:#222; }
  .s-price { font-size: 9pt; font-weight: 900; margin-bottom: 0.5mm; color:#000; }
  svg { width: 100%; max-width: 36mm; height: auto; max-height: 14mm; display: block; }
  .s-num { font-family:'Courier New',monospace; font-size: 6.5pt; font-weight: bold; margin-top: 0.5mm; }
  @media print{*{-webkit-print-color-adjust:exact;print-color-adjust:exact;}}
</style>
</head>
<body>
${rows}
<script>
window.onload=function(){
  ${inits}
  setTimeout(function(){window.print();},1000);
};
<\/script>
</body></html>`);
    pw.document.close();
    toast(`🖨️ Printing ${codes.length} labels (2 Cols)...`,'success');
  },

  doPrint(){
    const codes=this._getSelectedCodes();
    if(!codes.length){toast('First generate barcodes, then print.','error');return;}
    if(codes.length>40){toast('Max 40 per A4 sheet.','error');return;}

    const labels=[...codes];
    while(labels.length<40)labels.push(null);

    const shop=esc(S.settings.shopName);
    const cur=S.settings.currency;

    const cells=labels.map((item,idx)=>{
      if(!item)return`<div class="label empty"></div>`;
      return`<div class="label">
        <div class="lshop">${shop}</div>
        ${item.price?`<div class="lprice">${cur} ${parseFloat(item.price).toFixed(2)}</div>`:`<div class="lprice">&nbsp;</div>`}
        <svg id="s${idx}"></svg>
        <div class="lnum">${item.code}</div>
      </div>`;
    }).join('');

    const inits=labels.map((item,idx)=>{
      if(!item)return'';
      const safe=item.code.replace(/\\/g,'\\\\').replace(/'/g,"\\'");
      return`try{JsBarcode('#s${idx}','${safe}',{format:'CODE128',width:1.3,height:32,displayValue:false,margin:0,background:'#fff',lineColor:'#000'});}catch(e){}`;
    }).join('');

    const pw=window.open('','_blank','width=1050,height=820');
    if(!pw){toast('Pop-up blocked. Allow pop-ups and retry.','error');return;}
    pw.document.write(`<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<title>Barcodes — ${shop}</title>
<script src="https://cdn.jsdelivr.net/npm/jsbarcode@3.11.5/dist/JsBarcode.all.min.js"><\/script>
<style>
  @page{size:A4 portrait;margin:5mm 6mm}
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:Arial,sans-serif;background:#fff;}
  .hdr{text-align:center;font-size:8pt;color:#555;margin-bottom:3mm;}
  .grid{
    display:grid;
    grid-template-columns:repeat(5,1fr);
    grid-template-rows:repeat(8,33.5mm);
    gap:1.5mm;width:100%;
  }
  .label{
    border:0.5px solid #bbb;
    padding:1.5mm;
    display:flex;flex-direction:column;
    align-items:center;justify-content:center;
    text-align:center;overflow:hidden;
  }
  .label.empty{border:0.5px dashed #ddd;background:#f9f9f9;}
  .lshop { font-size: 6pt; font-weight: bold; margin-bottom: 0.5mm; color:#222; }
  .lprice { font-size: 8pt; font-weight: 900; margin-bottom: 0.5mm; color:#000; }
  svg{width:100%;height:auto;display:block;max-height:22mm;}
  .lnum{font-family:'Courier New',monospace;font-size:5.5pt;color:#333;margin-top:1mm;letter-spacing:0.5px;}
  @media print{*{-webkit-print-color-adjust:exact;print-color-adjust:exact;}}
</style>
</head>
<body>
<div class="hdr">${shop} &nbsp;•&nbsp; ${codes.length} barcode stickers &nbsp;•&nbsp; ${new Date().toLocaleDateString()}</div>
<div class="grid">${cells}</div>
<script>
window.onload=function(){
  ${inits}
  setTimeout(function(){window.print();},1000);
};
<\/script>
</body></html>`);
    pw.document.close();
    toast(`🖨️ Printing ${codes.length} barcode stickers...`,'success');
  }
};

// ── LOAN MANAGEMENT ─────────────────────
const LoanMgr={
  _customers:[],
  _loans:[],
  _activeCustomerId:null,

  async loadData(){
    const [custs,loans]=await Promise.all([DB.get('loan_customers'),DB.get('loans')]);
    this._customers=DB.obj2arr(custs);
    this._loans=DB.obj2arr(loans);
  },

  async render(){
    await this.loadData();
    this._renderStats();
    this._renderCustList();
    this._checkDueNotifications();
    if(this._activeCustomerId)this._renderRight(this._activeCustomerId);
  },

  _renderStats(){
    const el=document.getElementById('loanStats');if(!el)return;
    const cur=S.settings.currency;
    const activeLoans=this._loans.filter(l=>l.status!=='cleared');
    const totalOwed=activeLoans.reduce((s,l)=>s+(l.totalAmount-l.paidAmount),0);
    const totalGiven=this._loans.reduce((s,l)=>s+(l.totalAmount||0),0);
    const totalRecovered=this._loans.reduce((s,l)=>s+(l.paidAmount||0),0);
    const overdueLoans=activeLoans.filter(l=>this._isOverdue(l)).length;
    el.innerHTML=`
      <div class="loan-stat"><div class="loan-stat-lbl">Total Outstanding</div><div class="loan-stat-val" style="color:var(--red)">${cur} ${totalOwed.toFixed(2)}</div></div>
      <div class="loan-stat"><div class="loan-stat-lbl">Total Given</div><div class="loan-stat-val" style="color:var(--cyan)">${cur} ${totalGiven.toFixed(2)}</div></div>
      <div class="loan-stat"><div class="loan-stat-lbl">Recovered</div><div class="loan-stat-val" style="color:var(--green)">${cur} ${totalRecovered.toFixed(2)}</div></div>
      <div class="loan-stat"><div class="loan-stat-lbl">Active Loans</div><div class="loan-stat-val">${activeLoans.length}</div></div>
      <div class="loan-stat"><div class="loan-stat-lbl">Overdue</div><div class="loan-stat-val" style="color:${overdueLoans>0?'var(--red)':'var(--green)'}">${overdueLoans}</div></div>
      <div class="loan-stat"><div class="loan-stat-lbl">Customers</div><div class="loan-stat-val">${this._customers.length}</div></div>`;
  },

  filter(){
    const q=(document.getElementById('loanSearch')?.value||'').toLowerCase();
    this._renderCustList(q);
  },

  _renderCustList(q=''){
    const el=document.getElementById('loanCustList');if(!el)return;
    const cur=S.settings.currency;
    let custs=[...this._customers];
    if(q)custs=custs.filter(c=>c.name?.toLowerCase().includes(q)||c.phone?.toLowerCase().includes(q));
    if(!custs.length){
      el.innerHTML=`<div style="text-align:center;color:var(--t3);padding:40px 12px;font-size:12px;">${this._customers.length?'No results.':'No customers yet.<br>Add a customer to start.'}</div>`;return;
    }
    el.innerHTML=custs.map(c=>{
      const loans=this._loans.filter(l=>l.customerId===c.id&&l.status!=='cleared');
      const owed=loans.reduce((s,l)=>s+(l.totalAmount-l.paidAmount),0);
      const hasOverdue=loans.some(l=>this._isOverdue(l));
      const initial=(c.name||'?')[0].toUpperCase();
      const active=this._activeCustomerId===c.id;
      return`<div class="loan-cust-row${active?' active':''}" onclick="LoanMgr._selectCustomer('${c.id}')">
        <div class="loan-cust-avatar">${initial}</div>
        <div class="loan-cust-info">
          <div class="loan-cust-name">${esc(c.name)}</div>
          <div class="loan-cust-phone">${esc(c.phone||'—')}</div>
        </div>
        <div class="loan-cust-badge">
          <div class="loan-cust-owe${owed<=0?' paid':''}">${owed>0?`${cur} ${owed.toFixed(2)}`:'✓ Clear'}</div>
          ${hasOverdue?`<div style="font-size:9px;color:var(--red);font-weight:700;">⚠ OVERDUE</div>`:''}
          <div style="font-size:9px;color:var(--t3);">${loans.length} loan${loans.length!==1?'s':''}</div>
        </div>
      </div>`;
    }).join('');
  },

  _selectCustomer(id){
    this._activeCustomerId=id;
    this._renderCustList(document.getElementById('loanSearch')?.value||'');
    this._renderRight(id);
  },

  _renderRight(customerId){
    const el=document.getElementById('loanRight');if(!el)return;
    const cur=S.settings.currency;
    const cust=this._customers.find(c=>c.id===customerId);
    if(!cust){el.innerHTML='';return;}
    const loans=this._loans.filter(l=>l.customerId===customerId).sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt));
    const totalOwed=loans.filter(l=>l.status!=='cleared').reduce((s,l)=>s+(l.totalAmount-l.paidAmount),0);
    el.innerHTML=`
      <!-- Customer header -->
      <div style="background:var(--glass2);border:1px solid var(--border2);border-radius:var(--r12);padding:14px;display:flex;align-items:flex-start;gap:12px;">
        <div style="flex-shrink:0;">
          ${cust.photo1||cust.photo2
            ? `<div style="display:flex;gap:6px;">
                ${cust.photo1?`<img src="${cust.photo1}" style="width:52px;height:52px;border-radius:var(--r8);object-fit:cover;border:1px solid var(--border2);" alt="ID">`:''}
                ${cust.photo2?`<img src="${cust.photo2}" style="width:52px;height:52px;border-radius:var(--r8);object-fit:cover;border:1px solid var(--border2);" alt="Photo">`:''}
               </div>`
            : `<div style="width:48px;height:48px;border-radius:50%;background:linear-gradient(135deg,var(--cyan),var(--purple));display:flex;align-items:center;justify-content:center;font-size:20px;font-weight:800;color:#000;">${(cust.name||'?')[0].toUpperCase()}</div>`
          }
        </div>
        <div style="flex:1">
          <div style="font-size:16px;font-weight:800;">${esc(cust.name)}</div>
          <div style="font-size:12px;color:var(--t2);">📞 ${esc(cust.phone||'—')} &nbsp;|&nbsp; 🏠 ${esc(cust.address||'—')}</div>
          ${cust.notes?`<div style="font-size:11px;color:var(--t3);margin-top:2px;">📝 ${esc(cust.notes)}</div>`:''}
        </div>
        <div style="text-align:right;flex-shrink:0;">
          <div style="font-size:11px;color:var(--t3);">Total Outstanding</div>
          <div style="font-family:'JetBrains Mono',monospace;font-size:18px;font-weight:900;color:${totalOwed>0?'var(--red)':'var(--green)'};">${totalOwed>0?`${cur} ${totalOwed.toFixed(2)}`:'✓ All Clear'}</div>
        </div>
        <div style="display:flex;flex-direction:column;gap:5px;">
          <button class="btn btn-primary btn-sm" onclick="LoanMgr.openNewLoan('${cust.id}')">➕ New Loan</button>
          <button class="btn btn-ghost btn-sm" onclick="LoanMgr.openCustomerForm('${cust.id}')">✏️ Edit</button>
          <button class="btn btn-danger btn-sm" onclick="LoanMgr.deleteCustomer('${cust.id}')">🗑️</button>
        </div>
      </div>
      <!-- Loans -->
      <div style="font-size:12px;font-weight:700;color:var(--t2);padding:4px 0 2px;">LOANS (${loans.length})</div>
      ${loans.length?loans.map(l=>this._loanCard(l,cur)).join('')
        :`<div style="text-align:center;color:var(--t3);padding:30px;font-size:12px;">No loans yet for this customer.</div>`}`;
  },

  _loanCard(loan,cur){
    const pct=loan.totalAmount>0?Math.min(100,(loan.paidAmount/loan.totalAmount)*100):0;
    const remaining=loan.totalAmount-loan.paidAmount;
    const isCleared=loan.status==='cleared'||remaining<=0;
    const overdue=this._isOverdue(loan);
    const borderColor=isCleared?'rgba(16,185,129,0.3)':overdue?'rgba(239,68,68,0.3)':'rgba(255,255,255,0.1)';
    const instHTML=loan.installments?.length?`
      <div style="margin-top:10px;border-top:1px solid var(--border);padding-top:10px;">
        <div style="font-size:11px;font-weight:700;color:var(--t2);margin-bottom:6px;">📅 Installment Schedule</div>
        ${loan.installments.map((inst,i)=>{
          const due=new Date(inst.dueDate);const now=new Date();
          const daysLeft=Math.ceil((due-now)/(1000*60*60*24));
          let cls='upcoming',dotCls='upcoming',info='';
          if(inst.paid){cls='paid-inst';dotCls='paid-inst';info=`✓ Paid ${inst.paidDate?new Date(inst.paidDate).toLocaleDateString():''}`;}
          else if(daysLeft<0){cls='overdue';dotCls='overdue';info=`⚠ ${Math.abs(daysLeft)}d overdue`;}
          else if(daysLeft<=7){cls='due';dotCls='due';info=`⏰ Due in ${daysLeft}d`;}
          else info=`${due.toLocaleDateString()}`;
          return`<div class="loan-installment-row ${cls}">
            <div class="inst-dot ${dotCls}"></div>
            <div style="flex:1;font-size:11px;">Installment ${i+1} — ${cur} ${(inst.amount||0).toFixed(2)}</div>
            <div style="font-size:10px;color:var(--t2);">${info}</div>
            ${!inst.paid?`<button class="btn btn-success btn-xs" onclick="LoanMgr.markInstallmentPaid('${loan.id}',${i})">✓</button>`:''}
          </div>`;
        }).join('')}
      </div>`:'';
    return`<div class="loan-card" style="border-color:${borderColor};">
      <div class="loan-card-head">
        <div>
          <div class="loan-card-title">${esc(loan.description||'Loan')} ${isCleared?'<span class="badge badge-green">Cleared ✓</span>':overdue?'<span class="badge badge-red">⚠ Overdue</span>':''}</div>
          <div class="loan-card-date">📅 ${new Date(loan.createdAt).toLocaleDateString()} &nbsp;|&nbsp; ${loan.installments?.length?`${loan.installments.length} installments`:'No installment plan'}</div>
          ${loan.items?`<div style="font-size:11px;color:var(--t2);margin-top:3px;">🛍 ${esc(loan.items)}</div>`:''}
        </div>
        <div style="display:flex;gap:5px;align-items:flex-start;flex-shrink:0;">
          ${!isCleared?`<button class="btn btn-primary btn-xs" onclick="LoanMgr.openPayment('${loan.id}')">💰 Pay</button>`:''}
          ${isCleared?`<button class="btn btn-danger btn-xs" onclick="LoanMgr.clearLoan('${loan.id}')">🗑 Remove</button>`:`<button class="btn btn-success btn-xs" onclick="LoanMgr.markCleared('${loan.id}')">✓ Clear</button>`}
        </div>
      </div>
      <div class="loan-amounts">
        <div class="loan-amt-block"><div class="loan-amt-lbl">Total</div><div class="loan-amt-val" style="color:var(--t1);">${cur} ${(loan.totalAmount||0).toFixed(2)}</div></div>
        <div class="loan-amt-block"><div class="loan-amt-lbl">Paid</div><div class="loan-amt-val" style="color:var(--green);">${cur} ${(loan.paidAmount||0).toFixed(2)}</div></div>
        <div class="loan-amt-block"><div class="loan-amt-lbl">Remaining</div><div class="loan-amt-val" style="color:${remaining>0?'var(--red)':'var(--green)'};">${cur} ${Math.max(0,remaining).toFixed(2)}</div></div>
      </div>
      <div class="loan-progress"><div class="loan-progress-fill" style="width:${pct.toFixed(1)}%"></div></div>
      <div style="font-size:10px;color:var(--t3);text-align:right;">${pct.toFixed(0)}% paid</div>
      ${loan.paymentHistory?.length?`
        <div style="margin-top:8px;border-top:1px solid var(--border);padding-top:8px;">
          <div style="font-size:11px;font-weight:700;color:var(--t2);margin-bottom:4px;">Payment History</div>
          ${loan.paymentHistory.slice(-5).reverse().map(p=>`
            <div style="display:flex;justify-content:space-between;font-size:11px;padding:3px 0;border-bottom:1px solid var(--border);">
              <span style="color:var(--t2);">${new Date(p.date).toLocaleDateString()} ${p.note?`— ${esc(p.note)}`:''}</span>
              <span style="font-family:'JetBrains Mono',monospace;color:var(--green);font-weight:700;">+${cur} ${(p.amount||0).toFixed(2)}</span>
            </div>`).join('')}
        </div>`:''}
      ${instHTML}
    </div>`;
  },

  _isOverdue(loan){
    if(loan.status==='cleared'||(loan.totalAmount-loan.paidAmount)<=0)return false;
    if(!loan.installments?.length){
      if(loan.dueDate&&new Date(loan.dueDate)<new Date())return true;
      return false;
    }
    return loan.installments.some(i=>!i.paid&&new Date(i.dueDate)<new Date());
  },

  _checkDueNotifications(){
    const cur=S.settings.currency;
    const tomorrow=new Date();tomorrow.setDate(tomorrow.getDate()+1);
    const soonLoans=[];
    this._loans.filter(l=>l.status!=='cleared').forEach(loan=>{
      const cust=this._customers.find(c=>c.id===loan.customerId);
      if(!cust)return;
      (loan.installments||[]).forEach((inst,i)=>{
        if(inst.paid)return;
        const due=new Date(inst.dueDate);
        const daysLeft=Math.ceil((due-new Date())/(1000*60*60*24));
        if(daysLeft>=0&&daysLeft<=3){
          soonLoans.push({name:cust.name,amount:inst.amount,daysLeft,loanDesc:loan.description||'Loan'});
        } else if(daysLeft<0){
          soonLoans.push({name:cust.name,amount:inst.amount,daysLeft,loanDesc:loan.description||'Loan',overdue:true});
        }
      });
      // Simple due date (no installments)
      if(!loan.installments?.length&&loan.dueDate){
        const daysLeft=Math.ceil((new Date(loan.dueDate)-new Date())/(1000*60*60*24));
        if(daysLeft>=0&&daysLeft<=3)soonLoans.push({name:cust.name,amount:loan.totalAmount-loan.paidAmount,daysLeft,loanDesc:loan.description||'Loan'});
        else if(daysLeft<0)soonLoans.push({name:cust.name,amount:loan.totalAmount-loan.paidAmount,daysLeft,loanDesc:loan.description||'Loan',overdue:true});
      }
    });
    soonLoans.forEach(n=>{
      if(n.overdue){toast(`⚠️ OVERDUE: ${n.name} — ${cur} ${(n.amount||0).toFixed(2)} (${n.loanDesc})`, 'error');}
      else{toast(`⏰ Due in ${n.daysLeft}d: ${n.name} — ${cur} ${(n.amount||0).toFixed(2)} (${n.loanDesc})`, 'info');}
    });
  },

  openCustomerForm(id=null){
    const cust=id?this._customers.find(c=>c.id===id):null;
    // Photos state (up to 2 base64 strings)
    window._custPhotos=[cust?.photo1||null, cust?.photo2||null];
    document.getElementById('loanCustTitle').textContent=cust?'✏️ Edit Customer':'👤 Add Customer';
    document.getElementById('loanCustBody').innerHTML=`
      <div class="cust-photo-zone">
        <div class="cust-photo-slot" id="cps0"
          ondragover="event.preventDefault();this.classList.add('drag-over')"
          ondragleave="this.classList.remove('drag-over')"
          ondrop="LoanMgr._dropPhoto(event,0)"
          onpaste="LoanMgr._pastePhoto(event,0)"
          onclick="document.getElementById('cpFile0').click()"
          tabindex="0">
          <input type="file" id="cpFile0" accept="image/*" style="display:none" onchange="LoanMgr._filePhoto(event,0)">
          <div id="cpi0">${window._custPhotos[0]
            ? `<img src="${window._custPhotos[0]}" alt="Photo 1"><button class="cust-photo-del" onclick="event.stopPropagation();LoanMgr._removePhoto(0)">✕</button>`
            : `<div class="photo-hint"><span>🪪</span>Photo 1<br><small>drag / paste / click</small></div>`
          }</div>
        </div>
        <div class="cust-photo-slot" id="cps1"
          ondragover="event.preventDefault();this.classList.add('drag-over')"
          ondragleave="this.classList.remove('drag-over')"
          ondrop="LoanMgr._dropPhoto(event,1)"
          onpaste="LoanMgr._pastePhoto(event,1)"
          onclick="document.getElementById('cpFile1').click()"
          tabindex="0">
          <input type="file" id="cpFile1" accept="image/*" style="display:none" onchange="LoanMgr._filePhoto(event,1)">
          <div id="cpi1">${window._custPhotos[1]
            ? `<img src="${window._custPhotos[1]}" alt="Photo 2"><button class="cust-photo-del" onclick="event.stopPropagation();LoanMgr._removePhoto(1)">✕</button>`
            : `<div class="photo-hint"><span>📷</span>Photo 2<br><small>drag / paste / click</small></div>`
          }</div>
        </div>
      </div>
      <div class="form-group"><label>Full Name *</label><input id="lcName" type="text" value="${esc(cust?.name||'')}" placeholder="Customer name" autofocus></div>
      <div class="form-group"><label>Phone</label><input id="lcPhone" type="tel" value="${esc(cust?.phone||'')}" placeholder="07X XXXXXXX"></div>
      <div class="form-group"><label>Address</label><input id="lcAddr" type="text" value="${esc(cust?.address||'')}" placeholder="Address"></div>
      <div class="form-group"><label>Notes</label><textarea id="lcNotes" rows="2">${esc(cust?.notes||'')}</textarea></div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close('loanCustomerModal')">Cancel</button>
        <button class="btn btn-primary" onclick="LoanMgr.saveCustomer(${id?`'${id}'`:null})">💾 Save</button>
      </div>`;
    // Enable paste on slots
    setTimeout(()=>{
      ['cps0','cps1'].forEach((sid,i)=>{
        const el=document.getElementById(sid);
        if(el)el.addEventListener('paste',e=>LoanMgr._pastePhoto(e,i));
      });
    },100);
    Modal.open('loanCustomerModal');
  },

  _photoSlotUpdate(idx){
    const el=document.getElementById('cpi'+idx);if(!el)return;
    if(window._custPhotos[idx]){
      el.innerHTML=`<img src="${window._custPhotos[idx]}" alt="Photo ${idx+1}"><button class="cust-photo-del" onclick="event.stopPropagation();LoanMgr._removePhoto(${idx})">✕</button>`;
    } else {
      el.innerHTML=`<div class="photo-hint"><span>${idx===0?'🪪':'📷'}</span>Photo ${idx+1}<br><small>drag / paste / click</small></div>`;
    }
    const slot=document.getElementById('cps'+idx);
    if(slot)slot.classList.remove('drag-over');
  },

  _removePhoto(idx){window._custPhotos[idx]=null;this._photoSlotUpdate(idx);},

  _readImgFile(file,idx){
    if(!file||!file.type.startsWith('image/'))return;
    const reader=new FileReader();
    reader.onload=e=>{
      // Compress to max 600px wide
      const img=new Image();
      img.onload=()=>{
        const max=600;const scale=Math.min(1,max/img.width);
        const c=document.createElement('canvas');
        c.width=img.width*scale;c.height=img.height*scale;
        c.getContext('2d').drawImage(img,0,0,c.width,c.height);
        window._custPhotos[idx]=c.toDataURL('image/jpeg',0.82);
        this._photoSlotUpdate(idx);
      };
      img.src=e.target.result;
    };
    reader.readAsDataURL(file);
  },

  _filePhoto(event,idx){
    const file=event.target.files[0];
    if(file)this._readImgFile(file,idx);
    event.target.value='';
  },

  _dropPhoto(event,idx){
    event.preventDefault();
    const file=event.dataTransfer.files[0];
    if(file)this._readImgFile(file,idx);
    document.getElementById('cps'+idx)?.classList.remove('drag-over');
  },

  _pastePhoto(event,idx){
    const items=event.clipboardData?.items;
    if(!items)return;
    for(const item of items){
      if(item.type.startsWith('image/')){
        event.preventDefault();
        this._readImgFile(item.getAsFile(),idx);
        return;
      }
    }
  },

  async saveCustomer(id){
    const name=document.getElementById('lcName').value.trim();
    if(!name){toast('Name required','error');return;}
    const data={
      name,
      phone:document.getElementById('lcPhone').value.trim(),
      address:document.getElementById('lcAddr').value.trim(),
      notes:document.getElementById('lcNotes').value.trim(),
      photo1:window._custPhotos?.[0]||null,
      photo2:window._custPhotos?.[1]||null,
      updatedAt:new Date().toISOString()
    };
    if(!id){data.createdAt=new Date().toISOString();await DB.push('loan_customers',data);}
    else await DB.patch('loan_customers/'+id,data);
    Modal.close('loanCustomerModal');
    toast('Customer saved ✅','success');
    await this.render();
  },

  async deleteCustomer(id){
    const loans=this._loans.filter(l=>l.customerId===id&&l.status!=='cleared');
    if(loans.length&&!confirm(`This customer has ${loans.length} active loan(s). Delete anyway?`))return;
    if(!confirm('Delete customer and all their loans?'))return;
    const allLoans=this._loans.filter(l=>l.customerId===id);
    for(const l of allLoans)await DB.del('loans/'+l.id);
    await DB.del('loan_customers/'+id);
    if(this._activeCustomerId===id)this._activeCustomerId=null;
    toast('Customer deleted','info');
    await this.render();
  },

  openNewLoan(preCustomerId=null, prefill=null){
    const custOpts=this._customers.map(c=>`<option value="${c.id}" ${c.id===preCustomerId?'selected':''}>${esc(c.name)} — ${esc(c.phone||'')}</option>`).join('');
    document.getElementById('loanNewBody').innerHTML=`
      <div class="form-group">
        <label>Customer *</label>
        <div style="display:flex;gap:6px;align-items:center;">
          <select id="lnCust" style="flex:1;" onchange="LoanMgr._onLnCustChange()">
            <option value="">— Select customer —</option>
            ${custOpts}
            <option value="__new__">➕ Add New Customer…</option>
          </select>
        </div>
        <!-- Inline quick-add customer panel (hidden by default) -->
        <div id="lnNewCustPanel" style="display:none;margin-top:10px;padding:12px;background:var(--bg3);border:1px solid var(--cyan);border-radius:var(--r8);">
          <div style="font-size:12px;font-weight:700;color:var(--cyan);margin-bottom:8px;">👤 New Customer</div>
          <div class="cust-photo-zone" style="margin-bottom:10px;">
            <div class="cust-photo-slot" id="lncps0"
              ondragover="event.preventDefault();this.classList.add('drag-over')"
              ondragleave="this.classList.remove('drag-over')"
              ondrop="LoanMgr._dropPhoto(event,0)"
              onclick="document.getElementById('lncpFile0').click()">
              <input type="file" id="lncpFile0" accept="image/*" style="display:none" onchange="LoanMgr._filePhoto(event,0)">
              <div id="cpi0"><div class="photo-hint"><span>🪪</span>ID Photo<br><small>drag / click / paste</small></div></div>
            </div>
            <div class="cust-photo-slot" id="lncps1"
              ondragover="event.preventDefault();this.classList.add('drag-over')"
              ondragleave="this.classList.remove('drag-over')"
              ondrop="LoanMgr._dropPhoto(event,1)"
              onclick="document.getElementById('lncpFile1').click()">
              <input type="file" id="lncpFile1" accept="image/*" style="display:none" onchange="LoanMgr._filePhoto(event,1)">
              <div id="cpi1"><div class="photo-hint"><span>📷</span>Photo 2<br><small>drag / click / paste</small></div></div>
            </div>
          </div>
          <div class="grid-2">
            <div class="form-group" style="margin-bottom:8px;"><label>Full Name *</label><input id="lnNewName" type="text" placeholder="Customer name"></div>
            <div class="form-group" style="margin-bottom:8px;"><label>Phone</label><input id="lnNewPhone" type="tel" placeholder="07X XXXXXXX"></div>
          </div>
          <div class="form-group" style="margin-bottom:8px;"><label>Address</label><input id="lnNewAddr" type="text" placeholder="Address"></div>
          <div class="form-group" style="margin-bottom:8px;"><label>Notes</label><textarea id="lnNewNotes" rows="1" style="min-height:36px;"></textarea></div>
          <button class="btn btn-primary btn-sm w-full" onclick="LoanMgr.saveInlineCustomer()">✅ Save Customer &amp; Continue</button>
        </div>
      </div>
      <div class="form-group"><label>Description / Item(s)</label><input id="lnDesc" type="text" value="${esc(prefill?.desc||'')}" placeholder="e.g. Samsung A55, charger..."></div>
      <div class="form-group"><label>Items borrowed (details)</label><textarea id="lnItems" rows="2">${esc(prefill?.itemDetails||'')}</textarea></div>
      <div class="grid-2">
        <div class="form-group"><label>Total Loan Amount (${S.settings.currency}) *</label><input id="lnTotal" type="number" min="0" step="0.5" value="${prefill?.total||''}" placeholder="0.00" oninput="LoanMgr._previewInstallments()"></div>
        <div class="form-group"><label>Initial Payment (${S.settings.currency})</label><input id="lnInit" type="number" min="0" step="0.5" value="0" placeholder="0.00"></div>
      </div>
      <div class="form-group"><label>Simple Due Date (optional)</label><input id="lnDue" type="date"></div>
      <hr style="border:none;border-top:1px solid var(--border);margin:12px 0;">
      <div style="font-size:13px;font-weight:700;color:var(--cyan);margin-bottom:10px;">📅 Installment Plan (optional)</div>
      <div class="grid-2">
        <div class="form-group"><label>Number of Installments</label><input id="lnInstCount" type="number" min="0" max="60" value="0" placeholder="0 = no plan" oninput="LoanMgr._previewInstallments()"></div>
        <div class="form-group"><label>Every (months)</label><input id="lnInstEvery" type="number" min="1" max="12" value="1" oninput="LoanMgr._previewInstallments()"></div>
      </div>
      <div class="form-group"><label>First Installment Date</label><input id="lnInstStart" type="date" value="${new Date(Date.now()+30*24*60*60*1000).toISOString().split('T')[0]}" oninput="LoanMgr._previewInstallments()"></div>
      <div id="lnInstPreview" style="font-size:11px;color:var(--t2);margin-bottom:8px;"></div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close('loanNewModal')">Cancel</button>
        <button class="btn btn-primary" onclick="LoanMgr.saveLoan()">💾 Create Loan</button>
      </div>`;
    // init photo state
    window._custPhotos=[null,null];
    // enable paste on photo slots
    setTimeout(()=>{
      ['lncps0','lncps1'].forEach((sid,i)=>{
        const el=document.getElementById(sid);
        if(el)el.addEventListener('paste',e=>LoanMgr._pastePhoto(e,i));
      });
      // pre-select if only one customer
      if(this._customers.length===1&&!preCustomerId){
        const sel=document.getElementById('lnCust');
        if(sel)sel.value=this._customers[0].id;
      }
    },80);
    Modal.open('loanNewModal');
  },

  _onLnCustChange(){
    const val=document.getElementById('lnCust')?.value;
    const panel=document.getElementById('lnNewCustPanel');
    if(panel)panel.style.display=val==='__new__'?'':'none';
    if(val==='__new__'){
      window._custPhotos=[null,null];
      setTimeout(()=>document.getElementById('lnNewName')?.focus(),80);
    }
  },

  async saveInlineCustomer(){
    const name=document.getElementById('lnNewName')?.value.trim();
    if(!name){toast('Name required','error');return;}
    const data={
      name,
      phone:document.getElementById('lnNewPhone')?.value.trim()||'',
      address:document.getElementById('lnNewAddr')?.value.trim()||'',
      notes:document.getElementById('lnNewNotes')?.value.trim()||'',
      photo1:window._custPhotos?.[0]||null,
      photo2:window._custPhotos?.[1]||null,
      createdAt:new Date().toISOString(),updatedAt:new Date().toISOString()
    };
    const newId=await DB.push('loan_customers',data);
    toast(`✅ Customer "${name}" added!`,'success');
    // reload customers list
    await this.loadData();
    // update the select dropdown
    const sel=document.getElementById('lnCust');
    if(sel){
      // remove __new__ option, add new customer, re-add __new__ at end
      const newOpt=document.createElement('option');
      newOpt.value=newId;newOpt.textContent=`${name} — ${data.phone||''}`;
      const newOpt2=document.createElement('option');
      newOpt2.value='__new__';newOpt2.textContent='➕ Add New Customer…';
      // replace select content
      sel.innerHTML=`<option value="">— Select customer —</option>`;
      this._customers.forEach(c=>{
        const o=document.createElement('option');
        o.value=c.id;o.textContent=`${c.name} — ${c.phone||''}`;
        sel.appendChild(o);
      });
      sel.appendChild(newOpt2);
      sel.value=newId;
    }
    // hide panel
    const panel=document.getElementById('lnNewCustPanel');
    if(panel)panel.style.display='none';
    // reset photos
    window._custPhotos=[null,null];
    // refresh left list in background
    this._renderCustList();
    this._renderStats();
  },

  _previewInstallments(){
    const count=parseInt(document.getElementById('lnInstCount')?.value)||0;
    const el=document.getElementById('lnInstPreview');if(!el)return;
    if(!count){el.textContent='';return;}
    const every=parseInt(document.getElementById('lnInstEvery')?.value)||1;
    const start=document.getElementById('lnInstStart')?.value;
    const total=parseFloat(document.getElementById('lnTotal')?.value)||0;
    const instAmt=total>0?(total/count).toFixed(2):0;
    el.innerHTML=`✅ ${count} installments of ${S.settings.currency} ${instAmt} each, every ${every} month(s), starting ${start||'—'}`;
  },

  async saveLoan(){
    const custId=document.getElementById('lnCust')?.value;
    if(!custId||custId==='__new__'){toast('Select or add a customer first','error');return;}
    const total=parseFloat(document.getElementById('lnTotal').value)||0;
    if(!total){toast('Enter loan amount','error');return;}
    const initPay=parseFloat(document.getElementById('lnInit').value)||0;
    const count=parseInt(document.getElementById('lnInstCount')?.value)||0;
    const every=parseInt(document.getElementById('lnInstEvery')?.value)||1;
    const startStr=document.getElementById('lnInstStart')?.value;

    // Build installments
    let installments=[];
    if(count>0&&startStr){
      const instAmt=(total-initPay)/count;
      for(let i=0;i<count;i++){
        const d=new Date(startStr);
        d.setMonth(d.getMonth()+i*every);
        installments.push({dueDate:d.toISOString().split('T')[0],amount:parseFloat(instAmt.toFixed(2)),paid:false});
      }
    }

    const loan={
      customerId:custId,
      description:document.getElementById('lnDesc').value.trim()||'Loan',
      items:document.getElementById('lnItems').value.trim()||null,
      totalAmount:total,
      paidAmount:initPay,
      dueDate:document.getElementById('lnDue').value||null,
      installments,
      paymentHistory:initPay>0?[{date:new Date().toISOString(),amount:initPay,note:'Initial payment'}]:[],
      status:'active',
      createdAt:new Date().toISOString()
    };
    await DB.push('loans',loan);
    Modal.close('loanNewModal');
    toast('Loan created ✅','success');
    this._activeCustomerId=custId;
    await this.render();
  },

  openPayment(loanId){
    const loan=this._loans.find(l=>l.id===loanId);if(!loan)return;
    const cur=S.settings.currency;
    const remaining=loan.totalAmount-loan.paidAmount;
    document.getElementById('loanPayBody').innerHTML=`
      <div style="margin-bottom:14px;padding:10px;background:var(--glass);border-radius:var(--r8);">
        <div style="font-size:13px;font-weight:700;">${esc(loan.description||'Loan')}</div>
        <div style="font-size:12px;color:var(--t2);margin-top:4px;">Remaining: <strong style="color:var(--red)">${cur} ${remaining.toFixed(2)}</strong></div>
      </div>
      <div class="form-group"><label>Payment Amount (${cur}) *</label><input id="lpAmt" type="number" min="0.01" step="0.5" max="${remaining}" placeholder="${remaining.toFixed(2)}" autofocus></div>
      <div class="form-group"><label>Note (optional)</label><input id="lpNote" type="text" placeholder="e.g. Cash, Bank transfer..."></div>
      <div class="form-group"><label>Date</label><input id="lpDate" type="date" value="${new Date().toISOString().split('T')[0]}"></div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close('loanPayModal')">Cancel</button>
        <button class="btn btn-success" onclick="LoanMgr.recordPayment('${loanId}')">✅ Record Payment</button>
      </div>`;
    Modal.open('loanPayModal');
  },

  async recordPayment(loanId){
    const loan=this._loans.find(l=>l.id===loanId);if(!loan)return;
    const amt=parseFloat(document.getElementById('lpAmt').value)||0;
    if(!amt||amt<=0){toast('Enter payment amount','error');return;}
    const note=document.getElementById('lpNote').value.trim();
    const date=document.getElementById('lpDate').value||new Date().toISOString().split('T')[0];
    const newPaid=loan.paidAmount+amt;
    const history=[...(loan.paymentHistory||[]),{date:new Date(date).toISOString(),amount:amt,note}];
    const cleared=newPaid>=loan.totalAmount;
    await DB.patch('loans/'+loanId,{paidAmount:newPaid,paymentHistory:history,status:cleared?'cleared':'active'});
    Modal.close('loanPayModal');
    toast(`✅ ${S.settings.currency} ${amt.toFixed(2)} recorded!${cleared?' Loan fully cleared! 🎉':''}`, 'success');
    await this.render();
  },

  async markInstallmentPaid(loanId,idx){
    const loan=this._loans.find(l=>l.id===loanId);if(!loan)return;
    const inst=loan.installments[idx];if(!inst||inst.paid)return;
    const insts=[...loan.installments];
    insts[idx]={...inst,paid:true,paidDate:new Date().toISOString().split('T')[0]};
    const newPaid=(loan.paidAmount||0)+inst.amount;
    const history=[...(loan.paymentHistory||[]),{date:new Date().toISOString(),amount:inst.amount,note:`Installment ${idx+1} paid`}];
    const cleared=newPaid>=loan.totalAmount;
    await DB.patch('loans/'+loanId,{installments:insts,paidAmount:newPaid,paymentHistory:history,status:cleared?'cleared':'active'});
    toast(`Installment ${idx+1} marked paid ✅`,'success');
    await this.render();
  },

  async markCleared(loanId){
    if(!confirm('Mark this loan as fully cleared?'))return;
    const loan=this._loans.find(l=>l.id===loanId);if(!loan)return;
    await DB.patch('loans/'+loanId,{status:'cleared',paidAmount:loan.totalAmount});
    toast('Loan cleared ✅','success');
    await this.render();
  },

  async clearLoan(loanId){
    if(!confirm('Remove this loan record permanently?'))return;
    await DB.del('loans/'+loanId);
    toast('Loan removed','info');
    await this.render();
  }
};

// ── BLUETOOTH PRINTER ───────────────────
const BTPrinter={
  device:null, server:null, characteristic:null, status:'disconnected',
  async connect(){
    if(!navigator.bluetooth){toast('Web Bluetooth not supported in this browser. Use Chrome/Edge.','error');return;}
    try{
      toast('Searching for Bluetooth devices...','info');
      this.device=await navigator.bluetooth.requestDevice({
        acceptAllDevices:true,
        optionalServices:['000018f0-0000-1000-8000-00805f9b34fb','49535343-fe7d-4ae5-8fa9-9fafd205e455','e7810a71-73ae-499d-8c15-faa9aef0c3f2','0000ffe0-0000-1000-8000-00805f9b34fb']
      });
      this.server=await this.device.gatt.connect();
      this.device.addEventListener('gattserverdisconnected',()=>this.onDisconnect());
      // Try common printer service UUIDs
      const uuids=['000018f0-0000-1000-8000-00805f9b34fb','49535343-fe7d-4ae5-8fa9-9fafd205e455','e7810a71-73ae-499d-8c15-faa9aef0c3f2','0000ffe0-0000-1000-8000-00805f9b34fb'];
      for(const uuid of uuids){
        try{
          const svc=await this.server.getPrimaryService(uuid);
          const chars=await svc.getCharacteristics();
          for(const ch of chars){
            if(ch.properties.write||ch.properties.writeWithoutResponse){this.characteristic=ch;break;}
          }
          if(this.characteristic)break;
        }catch(e){continue;}
      }
      if(!this.characteristic){
        // Try all services
        try{
          const services=await this.server.getPrimaryServices();
          for(const svc of services){
            const chars=await svc.getCharacteristics();
            for(const ch of chars){if(ch.properties.write||ch.properties.writeWithoutResponse){this.characteristic=ch;break;}}
            if(this.characteristic)break;
          }
        }catch(e){}
      }
      this.status='connected';
      this.updateUI();
      toast('🔵 Printer connected: '+this.device.name,'success');
    }catch(e){
      this.status='disconnected';this.updateUI();
      if(e.name!=='NotFoundError')toast('Bluetooth error: '+e.message,'error');
    }
  },
  onDisconnect(){this.status='disconnected';this.characteristic=null;this.updateUI();toast('Printer disconnected','info');},
  disconnect(){
    try{if(this.device?.gatt?.connected)this.device.gatt.disconnect();}catch(e){}
    this.device=null;this.characteristic=null;this.status='disconnected';this.updateUI();toast('Disconnected','info');
  },
  async writeChunk(data){
    if(!this.characteristic)return;
    const CHUNK=20;
    for(let i=0;i<data.length;i+=CHUNK){
      const chunk=data.slice(i,i+CHUNK);
      try{
        if(this.characteristic.properties.writeWithoutResponse)await this.characteristic.writeValueWithoutResponse(chunk);
        else await this.characteristic.writeValue(chunk);
        await new Promise(r=>setTimeout(r,20));
      }catch(e){console.error('BT write:',e);}
    }
  },
  async printReceipt(sale){
    if(this.status!=='connected'||!this.characteristic){toast('No printer connected. Connect in ⚙️ Settings.','error');return;}
    if(!sale){toast('No sale data','error');return;}
    const cur=S.settings.currency;const shop=S.settings.shopName;
    const date=new Date(sale.date).toLocaleString();
    const ESC=0x1B;const LF=0x0A;const GS=0x1D;
    let text=`${shop}\n${date}\nSale #${(sale.id||'').slice(-8)}\n${'─'.repeat(32)}\n`;
    (sale.items||[]).forEach(si=>{
      const m=si.saleMode==='wholesale'?'[WS]':'';
      text+=`${si.name} ${m}\n  ${si.qty} x ${si.unitPrice?.toFixed(2)} = ${cur} ${(si.price||0).toFixed(2)}\n`;
    });
    text+=`${'─'.repeat(32)}\n`;
    if(sale.cartDiscount>0)text+=`Discount: -${cur} ${sale.cartDiscount.toFixed(2)}\n`;
    text+=`TOTAL: ${cur} ${(sale.total||0).toFixed(2)}\n\n${S.settings.receiptNote}\n\n\n`;
    const enc=new TextEncoder();
    const bytes=enc.encode(text);
    
    const bcStr = sale.billId || (sale.id||'').slice(-8).toUpperCase();
    const alignCenter = new Uint8Array([ESC, 0x61, 1]);
    const alignLeft = new Uint8Array([ESC, 0x61, 0]);
    const bcHeight = new Uint8Array([GS, 0x68, 60]);
    const bcWidth = new Uint8Array([GS, 0x77, 2]);
    const bcText = new Uint8Array([GS, 0x48, 2]);
    const bcData = enc.encode('{B' + bcStr);
    const bcCmd = new Uint8Array([GS, 0x6B, 73, bcData.length]);
    const lf = new Uint8Array([LF, LF]);
    
    // Add ESC/POS init + cut
    const init=new Uint8Array([ESC,0x40]);
    const cut=new Uint8Array([GS,0x56,0x00]);
    
    const parts = [
      init,
      bytes,
      alignCenter,
      bcHeight,
      bcWidth,
      bcText,
      bcCmd,
      bcData,
      lf,
      alignLeft,
      cut
    ];
    
    const totalLen = parts.reduce((a, p) => a + p.length, 0);
    const full = new Uint8Array(totalLen);
    let offset = 0;
    parts.forEach(p => { full.set(p, offset); offset += p.length; });
    
    await this.writeChunk(full);
    toast('Printed via Bluetooth 🖨️','success');
  },
  updateUI(){
    const dot=document.getElementById('btDot');if(dot)dot.className='bt-dot'+(this.status==='connected'?' connected':'');
    // Update settings UI if open
    const statusEl=document.getElementById('btStatusText');if(statusEl)statusEl.textContent=this.status==='connected'?('Connected: '+(this.device?.name||'Unknown')):'Not connected';
    const btnEl=document.getElementById('btConnectBtn');if(btnEl)btnEl.textContent=this.status==='connected'?'🔴 Disconnect':'📡 Connect Printer';
    const btPrintBtn=document.getElementById('btPrintBtn');if(btPrintBtn)btPrintBtn.style.display=this.status==='connected'?'':'none';
  },
  toggle(){if(this.status==='connected')this.disconnect();else this.connect();}
};

// ── ADMIN ───────────────────────────────
const Admin={
  unlock(){const pin=document.getElementById('pinInput').value;if(pin===S.settings.adminPin){S.adminUnlocked=true;document.getElementById('adminLock').style.display='none';this.render();toast('Admin unlocked 🔓','success');}else{toast('Wrong PIN ❌','error');document.getElementById('pinInput').value='';}},
  lock(){S.adminUnlocked=false;document.getElementById('adminLock').style.display='flex';document.getElementById('pinInput').value='';},
  getSales(){const p=document.getElementById('adminPeriod')?.value||'month';const now=new Date();return S.sales.filter(s=>{const d=new Date(s.date);if(p==='today')return d.toDateString()===now.toDateString();if(p==='week'){const w=new Date(now);w.setDate(w.getDate()-7);return d>=w;}if(p==='month')return d.getMonth()===now.getMonth()&&d.getFullYear()===now.getFullYear();return true;});},
  render(){
    if(!S.adminUnlocked)return;
    const cur=S.settings.currency;const sales=this.getSales();
    const total=sales.reduce((a,s)=>a+(s.total||0),0);
    const profit=sales.reduce((a,s)=>a+(s.profit||0),0);
    const stv=S.items.reduce((a,i)=>a+getStock(i)*(i.costPrice||0),0);
    const lowC=S.items.filter(i=>getStock(i)<=(i.lowStockThreshold||S.settings.lowStockAlert)).length;
    document.getElementById('statsRow').innerHTML=`
      <div class="stat-card"><div class="stat-lbl">💰 Revenue</div><div class="stat-val text-cyan">${cur} ${total.toFixed(2)}</div><div class="stat-sub">${sales.length} sales</div></div>
      <div class="stat-card"><div class="stat-lbl">📈 Profit</div><div class="stat-val ${profit>=0?'text-green':'text-red'}">${cur} ${profit.toFixed(2)}</div><div class="stat-sub">${total>0?(profit/total*100).toFixed(1):0}% margin</div></div>
      <div class="stat-card"><div class="stat-lbl">🏪 Stock Value</div><div class="stat-val text-purple">${cur} ${stv.toFixed(2)}</div><div class="stat-sub">${S.items.length} products</div></div>
      <div class="stat-card"><div class="stat-lbl">⚠️ Low Stock</div><div class="stat-val text-orange">${lowC}</div><div class="stat-sub">Need restock</div></div>
      <div class="stat-card"><div class="stat-lbl">📦 Categories</div><div class="stat-val text-cyan">${S.categories.length}</div><div class="stat-sub">Product groups</div></div>
      <div class="stat-card"><div class="stat-lbl">🛒 Avg Sale</div><div class="stat-val">${cur} ${sales.length?(total/sales.length).toFixed(2):'0.00'}</div><div class="stat-sub">Per transaction</div></div>`;
    // Top items
    const ic={};sales.forEach(s=>s.items?.forEach(si=>{ic[si.itemId]=(ic[si.itemId]||0)+(si.qty||1);}));
    const top=Object.entries(ic).sort((a,b)=>b[1]-a[1]).slice(0,6);const mx=top[0]?.[1]||1;
    document.getElementById('topItems').innerHTML=top.length?top.map(([id,qty],i)=>{const nm=S.items.find(it=>it.id===id)?.name||id.slice(-6);return`<div class="top-row"><div class="top-rank">${i+1}</div><div class="top-name">${esc(nm)}</div><div class="top-bar"><div class="top-bar-fill" style="width:${(qty/mx*100).toFixed(0)}%"></div></div><div class="top-count">${qty.toFixed(1)}</div></div>`;}).join(''):`<div style="color:var(--t3);text-align:center;padding:14px">No data yet</div>`;
    // Low stock
    const li=S.items.filter(i=>getStock(i)<=(i.lowStockThreshold||S.settings.lowStockAlert));
    document.getElementById('lowStockDiv').innerHTML=li.length?li.map(item=>`<div class="low-item-row"><div><div style="font-size:12px;font-weight:700">${esc(item.name)}</div><div style="font-size:10px;color:var(--t3)">${getStock(item).toFixed(2)} ${item.unit||''}</div></div><span class="badge badge-${getStock(item)<=0?'red':'orange'}">${getStock(item)<=0?'Out':'Low'}</span></div>`).join(''):`<div style="color:var(--t3);text-align:center;padding:14px">All OK ✅</div>`;
    // Recent sales
    const rc=this.getSales().slice(0,15);
    document.getElementById('salesDiv').innerHTML=rc.length?rc.map(s=>{const d=new Date(s.date);const ts=d.toLocaleTimeString('en-US',{hour:'2-digit',minute:'2-digit',hour12:true});const ds=d.toLocaleDateString('en-US',{month:'short',day:'numeric'});const wsc=(s.items||[]).filter(i=>i.saleMode==='wholesale').length;return`<div class="sale-row" onclick="SaleDetail.show('${s.id}')"><div class="sale-row-info"><strong>${ds}, ${ts}</strong><div style="font-size:10px;font-family:'JetBrains Mono',monospace;color:var(--t3);">${s.billId||('#'+(s.id||'').slice(-8))}</div>${(s.items||[]).length} items${wsc?` (${wsc} wholesale)`:''} ${s.status==='returned'?'<span class="badge badge-red">Returned</span>':''}</div><div class="sale-row-amt"><div class="total">${cur} ${(s.total||0).toFixed(2)}</div><div class="profit">+${cur} ${(s.profit||0).toFixed(2)}</div></div></div>`;}).join(''):`<div style="color:var(--t3);text-align:center;padding:20px">No sales in this period</div>`;
  },

  searchBill(){
    const q=(document.getElementById('billSearchInput')?.value||'').trim().toUpperCase();
    const el=document.getElementById('billSearchResult');if(!el)return;
    if(!q){el.innerHTML='';return;}
    const matches=S.sales.filter(s=>(s.billId||'').toUpperCase().includes(q)||(s.id||'').toUpperCase().includes(q));
    if(!matches.length){el.innerHTML=`<div style="font-size:12px;color:var(--red);padding:8px 0;">No bill found for "${q}"</div>`;return;}
    const cur=S.settings.currency;
    el.innerHTML=`<div style="font-size:11px;color:var(--cyan);font-weight:700;margin-bottom:4px;">${matches.length} result(s):</div>`+
      matches.slice(0,5).map(s=>{
        const d=new Date(s.date).toLocaleString();
        return`<div class="sale-row" onclick="SaleDetail.show('${s.id}')" style="border-color:rgba(0,212,255,0.3);margin-bottom:5px;">
          <div class="sale-row-info"><strong style="font-family:'JetBrains Mono',monospace;">${s.billId||s.id}</strong><div style="font-size:11px;">${d}</div></div>
          <div class="sale-row-amt"><div class="total">${cur} ${(s.total||0).toFixed(2)}</div>${s.status==='returned'?'<div style="font-size:10px;color:var(--red)">RETURNED</div>':''}</div>
        </div>`;
      }).join('');
  }
};

const SaleDetail={
  show(id){
    const sale=S.sales.find(s=>s.id===id);if(!sale)return;
    const cur=S.settings.currency;
    const isReturned=sale.status==='returned';
    document.getElementById('saleBody').innerHTML=`
      <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:12px;">
        <div>
          <div style="font-family:'JetBrains Mono',monospace;font-size:13px;font-weight:800;color:var(--cyan);">${sale.billId||('Sale #'+(sale.id||'').slice(-8))}</div>
          <div style="color:var(--t2);font-size:12px;margin-top:2px;">🕐 ${new Date(sale.date).toLocaleString()}</div>
          ${isReturned?`<div class="badge badge-red" style="margin-top:4px;">↩ Returned ${sale.returnedAt?new Date(sale.returnedAt).toLocaleDateString():''}</div>`:''}
        </div>
        ${!isReturned?`<button class="btn btn-danger btn-sm" onclick="SaleDetail.openReturn('${id}')">↩ Return</button>`:''}
      </div>
      ${(sale.items||[]).map(si=>{
        const ws=si.saleMode==='wholesale';
        const profit=S.adminUnlocked?(si.price-(si.costPrice||0)*si.qty):null;
        return`<div style="display:flex;justify-content:space-between;padding:7px 0;border-bottom:1px solid var(--border)">
          <div>
            <div style="font-weight:700;font-size:13px">${esc(si.name)}${ws?' <span class="badge badge-purple">WS</span>':''}</div>
            <div style="font-size:11px;color:var(--t3)">${si.qty} ${si.unit} × ${cur} ${si.unitPrice?.toFixed(2)||0}</div>
            ${S.adminUnlocked&&si.costPrice?`<div style="font-size:10px;color:var(--t3)">Cost: ${cur} ${(si.costPrice*si.qty).toFixed(2)}</div>`:''}
          </div>
          <div style="text-align:right;">
            <div style="font-family:'JetBrains Mono',monospace;font-weight:700;color:${ws?'var(--purple)':'var(--cyan)'};">${cur} ${(si.price||0).toFixed(2)}</div>
            ${S.adminUnlocked&&profit!=null?`<div style="font-size:10px;color:${profit>=0?'var(--green)':'var(--red)'};">+${cur}${profit.toFixed(2)}</div>`:''}
          </div>
        </div>`;
      }).join('')}
      <div style="margin-top:12px">
        <div style="display:flex;justify-content:space-between;padding:4px 0;font-size:13px"><span>Subtotal</span><span>${cur} ${(sale.subtotal||0).toFixed(2)}</span></div>
        ${sale.cartDiscount>0?`<div style="display:flex;justify-content:space-between;color:var(--green);font-size:13px"><span>Discount</span><span>-${cur} ${sale.cartDiscount.toFixed(2)}</span></div>`:''}
        <div style="display:flex;justify-content:space-between;font-size:15px;font-weight:800;border-top:1px solid var(--border);margin-top:4px;padding-top:6px"><span>Total</span><span class="text-cyan">${cur} ${(sale.total||0).toFixed(2)}</span></div>
        ${S.adminUnlocked?`<div style="display:flex;justify-content:space-between;font-size:12px;color:var(--green)"><span>Profit</span><span>+${cur} ${(sale.profit||0).toFixed(2)}</span></div>`:''}
      </div>
      ${sale.receiptText?`<details style="margin-top:12px;"><summary style="font-size:11px;color:var(--t3);cursor:pointer;">📄 Receipt Text</summary><pre style="font-size:10px;color:var(--t2);margin-top:6px;background:var(--bg3);padding:8px;border-radius:var(--r8);overflow-x:auto;white-space:pre-wrap;">${esc(sale.receiptText)}</pre></details>`:''}`;
    Modal.open('saleModal');
  },

  openReturn(saleId){
    const sale=S.sales.find(s=>s.id===saleId);if(!sale)return;
    const cur=S.settings.currency;
    document.getElementById('discTitle').textContent='↩ Return / Refund';
    document.getElementById('discBody').innerHTML=`
      <div style="padding:10px;background:rgba(239,68,68,0.08);border:1px solid rgba(239,68,68,0.2);border-radius:var(--r8);margin-bottom:12px;">
        <div style="font-size:13px;font-weight:700;">${sale.billId||('Sale #'+(sale.id||'').slice(-8))}</div>
        <div style="font-size:12px;color:var(--t2);">${new Date(sale.date).toLocaleString()}</div>
        <div style="font-size:13px;font-weight:800;color:var(--red);margin-top:4px;">Total: ${cur} ${(sale.total||0).toFixed(2)}</div>
      </div>
      <div style="font-size:12px;font-weight:700;color:var(--t2);margin-bottom:8px;">Select items to return:</div>
      ${(sale.items||[]).map((si,i)=>`
        <div style="display:flex;align-items:center;gap:8px;padding:7px 0;border-bottom:1px solid var(--border);">
          <input type="checkbox" class="ret-chk" data-idx="${i}" checked style="width:auto;accent-color:var(--red);">
          <div style="flex:1;font-size:12px;"><strong>${esc(si.name)}</strong><br><span style="color:var(--t3);">${si.qty} ${si.unit} — ${cur} ${(si.price||0).toFixed(2)}</span></div>
          <input type="number" class="ret-qty" data-idx="${i}" value="${si.qty}" min="0.001" max="${si.qty}" step="0.001" style="width:60px;padding:4px 6px;font-size:11px;">
        </div>`).join('')}
      <div class="form-group" style="margin-top:12px;"><label>Reason</label><input id="retReason" type="text" placeholder="Defective / Wrong item / Customer changed mind..."></div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close('discModal')">Cancel</button>
        <button class="btn btn-danger" onclick="SaleDetail.processReturn('${saleId}')">↩ Process Return</button>
      </div>`;
    Modal.open('discModal');
  },

  async processReturn(saleId){
    const sale=S.sales.find(s=>s.id===saleId);if(!sale)return;
    const reason=document.getElementById('retReason')?.value.trim()||'No reason';
    const returnItems=[];
    let refundTotal=0;
    document.querySelectorAll('.ret-chk:checked').forEach(chk=>{
      const idx=parseInt(chk.dataset.idx);
      const si=sale.items[idx];if(!si)return;
      const retQty=parseFloat(document.querySelectorAll('.ret-qty')[idx]?.value)||si.qty;
      returnItems.push({...si,returnQty:retQty});
      refundTotal+=si.price*(retQty/si.qty);
    });
    if(!returnItems.length){toast('Select at least one item','error');return;}
    if(!confirm(`Return ${returnItems.length} item(s) for refund of ${S.settings.currency} ${refundTotal.toFixed(2)}?`))return;

    // Restore stock
    for(const ri of returnItems){
      const item=S.items.find(i=>i.id===ri.itemId);if(!item)continue;
      const batches=[...(item.stockBatches||[])];
      if(batches.length>0){batches[0]={...batches[0],quantity:(batches[0].quantity||0)+ri.returnQty};}
      else batches.push({batchId:'ret_'+Date.now(),quantity:ri.returnQty,costPrice:ri.costPrice||0,dateAdded:new Date().toISOString().split('T')[0]});
      await DB.patch('items/'+item.id,{stockBatches:batches});
    }
    // Mark sale as returned
    await DB.patch('sales/'+saleId,{status:'returned',returnedAt:new Date().toISOString(),returnReason:reason,returnItems,refundTotal});
    // Create a return record in sales
    const retSale={
      billId:'RET-'+(sale.billId||saleId.slice(-8)),
      date:new Date().toISOString(),
      items:returnItems.map(ri=>({...ri,qty:ri.returnQty,price:-(ri.price*(ri.returnQty/ri.qty))})),
      subtotal:-refundTotal,cartDiscount:0,total:-refundTotal,profit:refundTotal,
      status:'return',returnOf:saleId,reason,
      receiptText:`RETURN — ${sale.billId||saleId}\nReason: ${reason}\nRefund: ${S.settings.currency}${refundTotal.toFixed(2)}`
    };
    await DB.push('sales',retSale);
    Modal.close('discModal');Modal.close('saleModal');
    await loadAll();
    toast(`↩ Return processed — Refund: ${S.settings.currency} ${refundTotal.toFixed(2)} ✅`,'success');
    if(S.adminUnlocked)Admin.render();
  }
};

// ── BACKUP ──────────────────────────────
const Backup={
  export(){const data={categories:S.categories,items:S.items,sales:S.sales,settings:S.settings,exportDate:new Date().toISOString()};const b=new Blob([JSON.stringify(data,null,2)],{type:'application/json'});const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=`janith-mobile-backup-${new Date().toISOString().split('T')[0]}.json`;a.click();toast('Exported ⬇️','success');},
  async import(event){const file=event.target.files[0];if(!file)return;const text=await file.text();const data=JSON.parse(text);if(!confirm(`Import ${data.items?.length||0} products?`))return;if(data.categories)await DB.set('categories',Object.fromEntries(data.categories.map(c=>{const{id,...r}=c;return[id,r];})));if(data.items)await DB.set('items',Object.fromEntries(data.items.map(i=>{const{id,...r}=i;return[id,r];})));if(data.sales)await DB.set('sales',Object.fromEntries(data.sales.map(s=>{const{id,...r}=s;return[id,r];})));await loadAll();toast('Imported ✅','success');},
  async seed(){if(!confirm('Add sample phone shop data?'))return;const cats=['Phones','Cases & Covers','Chargers & Cables','Earphones','Screen Protectors','Power Banks','Memory Cards'];const cids={};for(const n of cats){cids[n]=await DB.push('categories',{name:n,createdAt:new Date().toISOString()});}const si=[{name:'Samsung Galaxy A55 Case',categoryId:cids['Cases & Covers'],price:350,wholesalePrice:280,costPrice:200,unit:'pcs',type:'unit',stockBatches:[{batchId:'b1',quantity:30,costPrice:200,dateAdded:'2025-01-01'}]},{name:'iPhone 15 Tempered Glass',categoryId:cids['Screen Protectors'],price:250,wholesalePrice:180,costPrice:100,unit:'pcs',type:'unit',stockBatches:[{batchId:'b1',quantity:50,costPrice:100,dateAdded:'2025-01-01'}]},{name:'USB-C Fast Charger 20W',categoryId:cids['Chargers & Cables'],price:900,wholesalePrice:750,costPrice:550,unit:'pcs',type:'unit',stockBatches:[{batchId:'b1',quantity:20,costPrice:550,dateAdded:'2025-01-01'}]},{name:'Type-C Cable 1m',categoryId:cids['Chargers & Cables'],price:200,wholesalePrice:150,costPrice:80,unit:'pcs',type:'unit',stockBatches:[{batchId:'b1',quantity:100,costPrice:80,dateAdded:'2025-01-01'}]},{name:'Wireless Earbuds Pro',categoryId:cids['Earphones'],price:2500,wholesalePrice:2000,costPrice:1500,unit:'pcs',type:'unit',stockBatches:[{batchId:'b1',quantity:15,costPrice:1500,dateAdded:'2025-01-01'}]},{name:'10000mAh Power Bank',categoryId:cids['Power Banks'],price:2200,wholesalePrice:1800,costPrice:1300,unit:'pcs',type:'unit',stockBatches:[{batchId:'b1',quantity:12,costPrice:1300,dateAdded:'2025-01-01'}]},{name:'128GB MicroSD Card',categoryId:cids['Memory Cards'],price:1200,wholesalePrice:950,costPrice:700,unit:'pcs',type:'unit',stockBatches:[{batchId:'b1',quantity:25,costPrice:700,dateAdded:'2025-01-01'}]},{name:'Coconut Oil 1L',categoryId:null,price:650,wholesalePrice:550,costPrice:430,unit:'L',type:'liquid',liquidOptions:[{label:'Quarter',amount:0.25,price:170},{label:'Half',amount:0.5,price:330},{label:'Full',amount:1,price:650}],stockBatches:[{batchId:'b1',quantity:10,costPrice:430,dateAdded:'2025-01-01'}]},{name:'Rice (Samba)',categoryId:null,price:200,wholesalePrice:180,costPrice:155,unit:'kg',type:'weighted',stockBatches:[{batchId:'b1',quantity:50,costPrice:155,dateAdded:'2025-01-01'}]}];for(const item of si){await DB.push('items',{...item,createdAt:new Date().toISOString(),lowStockThreshold:5,defaultDiscount:0});}await loadAll();toast('Sample data added ✅','success');},
  async clearSales(){if(!confirm('Delete ALL sales? Cannot be undone.'))return;await DB.del('sales');await loadAll();toast('Cleared','info');}
};

// ── AI ──────────────────────────────────
const AI={
  _pending:null,
  async send(){
    const input=document.getElementById('aiInput');const msg=input.value.trim();if(!msg)return;
    input.value='';this.addMsg('user',msg);this.showTyping();
    if(this._pending&&(msg.toLowerCase().includes('yes')||msg.toLowerCase()==='ok')){const item=this._pending;this._pending=null;this.hideTyping();POS.addFlow(item.id);this.addMsg('bot',`✅ **${item.name}** — select sale type in the popup.`);return;}
    this._pending=null;
    const key=S.settings.geminiKey||GEMINI_KEY_DEFAULT;
    if(!key){this.hideTyping();this.addMsg('bot','⚠️ Configure Gemini API Key in ⚙️ Settings');return;}
    const inv=S.items.map(i=>`- ID:${i.id}|${i.name}|Rs.${i.price||0} retail/Rs.${i.wholesalePrice||i.price||0} WS|Stock:${getStock(i).toFixed(1)} ${i.unit||''}`).join('\n');
    const low=S.items.filter(i=>getStock(i)<=(i.lowStockThreshold||S.settings.lowStockAlert)).map(i=>i.name).join(', ')||'None';
    const ts=new Date().toDateString();const tsd=S.sales.filter(s=>new Date(s.date).toDateString()===ts);const tst=tsd.reduce((a,s)=>a+(s.total||0),0);
    const sys=`You are Janith Mobile AI — assistant for phone shop "${S.settings.shopName}".\n\nINVENTORY (${S.items.length} items):\n${inv}\n\nLOW STOCK: ${low}\nTODAY: ${tsd.length} sales, Rs.${tst.toFixed(2)}\n\nRULES:\n- Reply [ADD_TO_CART:ITEM_ID:item_name] to add item\n- Be concise, prices in Rs.\n- Support Retail/Wholesale/Gift/Free sale modes`;
    try{
      const res=await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${key}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({contents:[{role:'user',parts:[{text:sys+'\n\nUser: '+msg}]}],generationConfig:{maxOutputTokens:600,temperature:0.7}})});
      const data=await res.json();if(data.error)throw new Error(data.error.message);
      let reply=data.candidates?.[0]?.content?.parts?.[0]?.text||'No response.';
      const cm=reply.match(/\[ADD_TO_CART:([^\s:]+):([^\]]+)\]/);
      this.hideTyping();
      if(cm){const id=cm[1];const nm=cm[2];const item=S.items.find(i=>i.id===id)||S.items.find(i=>i.name.toLowerCase().includes(nm.toLowerCase()));reply=reply.replace(cm[0],'').trim();if(item){this._pending=item;this.addMsg('bot',(reply||`Add **${item.name}** to cart?`)+'\n\n[Say "yes" to confirm]',item.id,item.name);}else{this.addMsg('bot',reply||'Product not found.');}}
      else this.addMsg('bot',reply);
    }catch(e){this.hideTyping();this.addMsg('bot','❌ AI Error: '+e.message);}
  },
  quick(m){document.getElementById('aiInput').value=m;this.send();},
  addMsg(role,text,itemId=null,itemName=null){const msgs=document.getElementById('aiMsgs');const div=document.createElement('div');div.className='ai-msg '+role;const av=role==='bot'?`<div class="msg-avatar">✨</div>`:`<div class="msg-avatar">👤</div>`;const fmt=text.replace(/\*\*(.+?)\*\*/g,'<strong>$1</strong>').replace(/\n/g,'<br>');let action='';if(itemId&&itemName){action=`<button class="ai-action" onclick="POS.addFlow('${itemId}');this.textContent='✅ Added!';this.style.background='var(--green2)'">➕ Add ${esc(itemName)}</button>`;}div.innerHTML=`${av}<div><div class="msg-bubble">${fmt}</div>${action}</div>`;msgs.appendChild(div);msgs.scrollTop=msgs.scrollHeight;},
  showTyping(){const msgs=document.getElementById('aiMsgs');const d=document.createElement('div');d.id='typingDot';d.className='ai-msg bot';d.innerHTML=`<div class="msg-avatar">✨</div><div class="typing"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>`;msgs.appendChild(d);msgs.scrollTop=msgs.scrollHeight;document.getElementById('aiStatus').textContent='Thinking...';},
  hideTyping(){document.getElementById('typingDot')?.remove();document.getElementById('aiStatus').textContent='Ready';},
  clear(){document.getElementById('aiMsgs').innerHTML=`<div class="ai-msg bot"><div class="msg-avatar">✨</div><div><div class="msg-bubble">Chat cleared! How can I help? 😊</div></div></div>`;}
};

// ── SCANNER ─────────────────────────────
const Scanner={
  _scanner:null,_mode:'pos',
  openPOS(){this._mode='pos';Modal.open('scanModal');this._start();},
  openFormScan(){this._mode='form';Modal.open('scanModal');this._start();},
  _start(){setTimeout(()=>{try{if(typeof Html5QrcodeScanner!=='undefined'){this._scanner=new Html5QrcodeScanner('scanBox',{fps:10,qrbox:250},false);this._scanner.render(code=>{this.close();if(this._mode==='form'){const el=document.getElementById('fBarcode');if(el){el.value=code;ItemForm.previewBarcode();}}else{POS.scanBarcode(code);}},()=>{});}else{document.getElementById('scanBox').innerHTML=`<div style="padding:30px;text-align:center;color:var(--t2)">Scanner library not loaded. Type barcode manually.</div>`;}}catch(e){document.getElementById('scanBox').innerHTML=`<div style="padding:30px;text-align:center;color:var(--t2)">Camera error. Try barcode input field.</div>`;}},300);},
  close(){try{this._scanner?.clear();}catch(e){}this._scanner=null;Modal.close('scanModal');document.getElementById('scanBox').innerHTML='';}
};

// ── SETTINGS ────────────────────────────
const Settings={
  open(){
    document.getElementById('settingsBody').innerHTML=`
      <div class="form-group"><label>Shop Name</label><input id="setName" type="text" value="${esc(S.settings.shopName)}"></div>
      <div class="grid-2">
        <div class="form-group"><label>Currency Symbol</label><input id="setCur" type="text" value="${esc(S.settings.currency)}"></div>
        <div class="form-group"><label>Low Stock Alert</label><input id="setLow" type="number" value="${S.settings.lowStockAlert||5}" min="0"></div>
      </div>
      <div class="form-group"><label>Receipt Footer Note</label><input id="setNote" type="text" value="${esc(S.settings.receiptNote||'')}"></div>
      <div class="form-group"><label>🤖 Gemini AI API Key</label><input id="setGemini" type="password" value="${esc(S.settings.geminiKey||'')}"><span style="font-size:11px;color:var(--t2);margin-top:3px;display:block">Get free key: <a href="https://aistudio.google.com/app/apikey" target="_blank" style="color:var(--cyan)">aistudio.google.com</a></span></div>
      <hr style="border:none;border-top:1px solid var(--border);margin:14px 0">
      <div style="font-size:13px;font-weight:700;color:var(--cyan);margin-bottom:10px">🔑 Change Admin PIN</div>
      <div class="grid-3">
        <div class="form-group"><label>Current PIN</label><input id="curPin" type="password" maxlength="6" placeholder="••••"></div>
        <div class="form-group"><label>New PIN</label><input id="newPin" type="password" maxlength="6" placeholder="New PIN"></div>
        <div class="form-group"><label>Confirm PIN</label><input id="confPin" type="password" maxlength="6" placeholder="Confirm"></div>
      </div>
      <button class="btn btn-orange btn-sm mb-12" onclick="Settings.changePin()">🔑 Change PIN</button>
      <hr style="border:none;border-top:1px solid var(--border);margin:14px 0">
      <div style="font-size:13px;font-weight:700;color:var(--cyan);margin-bottom:10px">📡 Bluetooth Printer / Label Machine</div>
      <div class="bt-status-bar">
        <div class="bt-status-indicator ${BTPrinter.status==='connected'?'connected':''}" id="btStatusDot"></div>
        <div style="flex:1"><span id="btStatusText" style="font-size:13px;font-weight:600">${BTPrinter.status==='connected'?'Connected: '+(BTPrinter.device?.name||'Unknown'):'Not connected'}</span><br><span style="font-size:11px;color:var(--t2)">Supports ESC/POS thermal printers via Web Bluetooth</span></div>
        <button class="btn btn-sm ${BTPrinter.status==='connected'?'btn-danger':'btn-purple'}" id="btConnectBtn" onclick="BTPrinter.toggle()">${BTPrinter.status==='connected'?'🔴 Disconnect':'📡 Connect Printer'}</button>
      </div>
      <div class="info-hint" style="font-size:11px;">
        ⚠️ Bluetooth printing requires <strong>Chrome or Edge</strong> browser.<br>
        Works with most ESC/POS BLE thermal printers. For Bluetooth barcode <em>scanners</em>, pair them via your device's OS Bluetooth settings — they work as a keyboard automatically.
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close('settingsModal')">Cancel</button>
        <button class="btn btn-primary" onclick="Settings.save()">💾 Save Settings</button>
      </div>`;
    Modal.open('settingsModal');
  },
  changePin(){
    const cur=document.getElementById('curPin').value;
    const n=document.getElementById('newPin').value;
    const c=document.getElementById('confPin').value;
    if(cur!==S.settings.adminPin){toast('Wrong current PIN ❌','error');return;}
    if(!n||n.length<4){toast('New PIN must be at least 4 digits','error');return;}
    if(n!==c){toast('PINs do not match','error');return;}
    S.settings.adminPin=n;
    document.getElementById('curPin').value='';document.getElementById('newPin').value='';document.getElementById('confPin').value='';
    toast('PIN changed ✅','success');
  },
  async save(){
    S.settings.shopName=document.getElementById('setName').value;
    S.settings.currency=document.getElementById('setCur').value;
    S.settings.geminiKey=document.getElementById('setGemini').value;
    S.settings.lowStockAlert=parseInt(document.getElementById('setLow').value)||5;
    S.settings.receiptNote=document.getElementById('setNote').value;
    await DB.set('settings/main',S.settings);
    Modal.close('settingsModal');toast('Settings saved ✅','success');
  }
};

// ── HELPERS ─────────────────────────────
function getStock(item){const b=item.stockBatches;if(!b)return 0;const a=Array.isArray(b)?b:Object.values(b);return a.reduce((s,b)=>s+(b?(b.quantity||0):0),0);}
function getCatEmoji(catId){const cat=S.categories.find(c=>c.id===catId);if(!cat)return'📱';const n=(cat.name||'').toLowerCase();if(n.includes('phone')||n.includes('mobile'))return'📱';if(n.includes('cover')||n.includes('case'))return'🛡️';if(n.includes('charger')||n.includes('cable'))return'🔌';if(n.includes('earphone')||n.includes('headphone')||n.includes('earbud'))return'🎧';if(n.includes('screen')||n.includes('glass')||n.includes('temper'))return'🪟';if(n.includes('battery')||n.includes('power bank'))return'🔋';if(n.includes('memory')||n.includes('sd card'))return'💾';if(n.includes('repair')||n.includes('spare'))return'🔧';if(n.includes('watch'))return'⌚';if(n.includes('oil')||n.includes('liquid'))return'🫙';if(n.includes('rice')||n.includes('grain')||n.includes('kirana'))return'🌾';if(n.includes('accessory'))return'🎒';return'📱';}
function esc(str){if(!str)return'';return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');}

// ── PWA ─────────────────────────────────
function setupPWA(){
  try{const c=document.createElement('canvas');c.width=c.height=192;const ctx=c.getContext('2d');ctx.fillStyle='#06080f';ctx.fillRect(0,0,192,192);const g=ctx.createLinearGradient(0,0,192,192);g.addColorStop(0,'#00d4ff');g.addColorStop(1,'#8b5cf6');ctx.fillStyle=g;ctx.beginPath();ctx.roundRect?ctx.roundRect(23,23,146,146,35):ctx.arc(96,96,80,0,Math.PI*2);ctx.fill();ctx.fillStyle='#000';ctx.font='bold 42px sans-serif';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText('JM',96,88);ctx.font='600 20px sans-serif';ctx.fillText('POS',96,118);const icon=c.toDataURL();const m={name:'Janith Mobile POS',short_name:'JM POS',start_url:'./',display:'standalone',background_color:'#06080f',theme_color:'#00d4ff',icons:[{src:icon,sizes:'192x192',type:'image/png',purpose:'any maskable'}]};const bl=new Blob([JSON.stringify(m)],{type:'application/manifest+json'});const lk=document.createElement('link');lk.rel='manifest';lk.href=URL.createObjectURL(bl);document.head.appendChild(lk);}catch(e){}
  if('serviceWorker' in navigator){
    const sw=`
const C='jm-mobile-v4';
const STATIC=[
  'https://fonts.googleapis.com/css2?family=Exo+2:wght@300;400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500;600;700&display=swap',
  'https://cdn.jsdelivr.net/npm/fuse.js@7.0.0/dist/fuse.min.js',
  'https://cdn.jsdelivr.net/npm/jsbarcode@3.11.5/dist/JsBarcode.all.min.js'
];
self.addEventListener('install',e=>{
  self.skipWaiting();
  e.waitUntil(caches.open(C).then(c=>c.addAll(STATIC).catch(()=>{})));
});
self.addEventListener('activate',e=>{
  e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==C).map(k=>caches.delete(k)))));
  clients.claim();
});
self.addEventListener('fetch',e=>{
  const url=e.request.url;
  // Never intercept Firebase or Gemini API calls
  if(url.includes('firebasedatabase')||url.includes('googleapis.com/v1beta')||url.includes('generativelanguage'))return;
  // Cache-first for static CDN assets; network-first for everything else
  if(url.includes('cdn.jsdelivr.net')||url.includes('fonts.googleapis')||url.includes('fonts.gstatic')||url.includes('unpkg.com')){
    e.respondWith(caches.match(e.request).then(c=>{
      if(c)return c;
      return fetch(e.request).then(r=>{
        if(r.ok){const cl=r.clone();caches.open(C).then(ca=>ca.put(e.request,cl));}
        return r;
      }).catch(()=>new Response('',{status:503}));
    }));
    return;
  }
  // For navigation requests (the app HTML) — cache a copy, serve offline
  if(e.request.mode==='navigate'){
    e.respondWith(fetch(e.request).then(r=>{
      if(r.ok){const cl=r.clone();caches.open(C).then(ca=>ca.put(e.request,cl));}
      return r;
    }).catch(()=>caches.match(e.request).then(c=>c||caches.match('/'))));
    return;
  }
});`;
    navigator.serviceWorker.register(URL.createObjectURL(new Blob([sw],{type:'application/javascript'}))).catch(()=>{});
  }
}

document.addEventListener('DOMContentLoaded',init);

// --- REPAIR MANAGER ---
const RepairMgr = {
  _repairs: [],
  async load() {
    this._repairs = DB.obj2arr(await DB.get('repairs')).sort((a,b)=>new Date(b.date)-new Date(a.date));
  },
  async render() {
    const q = document.getElementById('repairSearch')?.value.toLowerCase() || '';
    const list = document.getElementById('repairList');
    if(!list) return;
    let items = this._repairs;
    if(q) items = items.filter(r => (r.model||'').toLowerCase().includes(q) || (r.owner||'').toLowerCase().includes(q) || (r.phone||'').includes(q) || (r.repairId||'').toLowerCase().includes(q));
    if(!items.length) { list.innerHTML = '<div class="empty-state">No repairs found</div>'; return; }
    list.innerHTML = items.map(r => 
      `<div class="item-card" style="display:flex; justify-content:space-between; padding:12px; border:1px solid var(--border); border-radius:var(--r8); margin-bottom:8px; background:var(--glass);">
        <div>
          <div style="font-weight:bold; font-size:14px; color:var(--cyan);">${esc(r.model)}</div>
          <div style="font-size:12px; color:var(--t2);">ID: <span style="font-family:'JetBrains Mono';color:var(--t1);">${esc(r.repairId||r.id.slice(-6).toUpperCase())}</span></div>
          <div style="font-size:12px; color:var(--t2); margin-top:2px;">👤 ${esc(r.owner)} 📞 ${esc(r.phone)}</div>
          <div style="font-size:11px; color:var(--t3); margin-top:2px;">🏠 ${esc(r.address||'-')}</div>
          <div style="font-size:12px; color:var(--t2); margin-top:4px;">🔧 දෝශය (Issue): ${esc(r.issue)}</div>
          <div style="font-size:11px; color:var(--t3); margin-top:4px;">📅 Received: ${new Date(r.date).toLocaleDateString()}</div>
        </div>
        <div style="text-align:right; display:flex; flex-direction:column; gap:8px; align-items:flex-end;">
          <select style="font-size:12px; padding:4px; border-radius:4px; background:var(--bg3); color:${r.status==='returned'?'var(--green)':r.status==='repaired'?'var(--cyan)':'var(--orange)'}; border:1px solid var(--border);" onchange="RepairMgr.updateStatus('${r.id}', this.value)">
            <option value="pending" ${r.status==='pending'?'selected':''}>⏳ භාරගත්තා (Pending)</option>
            <option value="in_progress" ${r.status==='in_progress'?'selected':''}>🔨 හදමින් පවතී (In Progress)</option>
            <option value="repaired" ${r.status==='repaired'?'selected':''}>✅ හදලා ඉවරයි (Repaired)</option>
            <option value="returned" ${r.status==='returned'?'selected':''}>📦 කස්ටමර්ට දුන්නා (Returned)</option>
            <option value="cannot_fix" ${r.status==='cannot_fix'?'selected':''}>❌ හදන්න බෑ (Cannot Fix)</option>
          </select>
          <div style="display:flex;gap:4px;">
            <button class="btn btn-ghost btn-xs" style="color:var(--t2);border:1px solid var(--border);" onclick="RepairMgr.print('${r.id}')">🖨️ Print</button>
            <button class="btn btn-danger btn-xs" onclick="RepairMgr.del('${r.id}')">Delete</button>
          </div>
        </div>
      </div>`
    ).join('');
  },
  openNew() {
    document.getElementById('optContent').innerHTML = 
      `<div class="modal-head"><div class="modal-title">🔧 Phone Repair භාරගැනීම</div><button class="modal-x" onclick="Modal.close('optModal')">✕</button></div>
      <div class="form-group"><label>Phone Model</label><input id="repModel" type="text" placeholder="Samsung A51..." autofocus></div>
      <div class="form-group"><label>අයිතිකරුගේ නම (Owner)</label><input id="repOwner" type="text" placeholder="Nimal"></div>
      <div class="form-group"><label>ලිපිනය (Address)</label><input id="repAddr" type="text" placeholder="ලිපිනය"></div>
      <div class="form-group"><label>දුරකථන අංකය (Phone)</label><input id="repPhone" type="tel" placeholder="07X XXXXXXX"></div>
      <div class="form-group"><label>දෝශය (Issue/Fault)</label><textarea id="repIssue" rows="2" placeholder="Display broken, charging issue..."></textarea></div>
      <button class="btn btn-primary w-full mt-8" onclick="RepairMgr.save()">Save Repair & Print Receipt</button>`;
    Modal.open('optModal');
  },
  async save() {
    const model = document.getElementById('repModel').value.trim();
    const owner = document.getElementById('repOwner').value.trim();
    const address = document.getElementById('repAddr').value.trim();
    const phone = document.getElementById('repPhone').value.trim();
    const issue = document.getElementById('repIssue').value.trim();
    if(!model) return toast('Model required', 'error');
    
    // Generate a readable ID (e.g. REP-83021)
    const repairId = 'REP-' + Date.now().toString().slice(-4) + Math.floor(Math.random()*10);
    
    const id = await DB.push('repairs', { repairId, model, owner, address, phone, issue, status: 'pending', date: new Date().toISOString() });
    Modal.close('optModal');
    toast('Repair added ✅', 'success');
    await this.load();
    this.render();
    
    // Auto print receipt
    this.print(id);
  },
  async updateStatus(id, status) {
    await DB.patch('repairs/'+id, { status });
    toast('Status updated', 'success');
    await this.load();
    this.render();
  },
  async del(id) {
    if(!confirm('Delete repair record?')) return;
    await DB.del('repairs/'+id);
    toast('Deleted', 'info');
    await this.load();
    this.render();
  },
  print(id) {
    const r = this._repairs.find(x => x.id === id);
    if(!r) return;
    
    let printWin = window.open('', '_blank');
    let html = `
      <html>
      <head>
        <title>Repair Receipt</title>
        <style>
          @page { margin: 0; }
          body { font-family: monospace; width: 58mm; margin: 0; padding: 10px; font-size: 12px; color: #000; }
          .center { text-align: center; }
          .bold { font-weight: bold; }
          .line { border-top: 1px dashed #000; margin: 5px 0; }
          .item { display: flex; justify-content: space-between; margin-bottom: 3px; }
          h2 { margin: 0 0 5px 0; font-size: 16px; }
        </style>
      </head>
      <body>
        <div class="center">
          <h2>📱 JANITH MOBILE</h2>
          <div>REPAIR RECEIPT</div>
        </div>
        <div class="line"></div>
        <div class="item"><span>Date:</span><span>${new Date(r.date).toLocaleDateString()}</span></div>
        <div class="item"><span>Rep ID:</span><span class="bold">${r.repairId || r.id.slice(-6).toUpperCase()}</span></div>
        <div class="line"></div>
        <div style="margin-bottom:3px;"><span class="bold">Model:</span> ${esc(r.model)}</div>
        <div style="margin-bottom:3px;"><span class="bold">Owner:</span> ${esc(r.owner)}</div>
        <div style="margin-bottom:3px;"><span class="bold">Phone:</span> ${esc(r.phone)}</div>
        <div style="margin-bottom:3px;"><span class="bold">Issue:</span> ${esc(r.issue)}</div>
        <div class="line"></div>
        <div class="center" style="font-size:10px; margin-top:10px;">
          * Please bring this receipt when<br>collecting your device.
        </div>
        <div class="center" style="font-size:10px; margin-top:5px;">
          Thank You!
        </div>
        <script>
          window.onload = function() { window.print(); window.close(); }
        </script>
      </body>
      </html>
    `;
    printWin.document.write(html);
    printWin.document.close();
  }
};

// --- RETURN MANAGER ---
const ReturnMgr = {
  _supplierReturns: [],
  _lastSearchedBill: null,

  async load() {
    this._supplierReturns = DB.obj2arr(await DB.get('supplierReturns')).sort((a,b)=>new Date(b.date)-new Date(a.date));
  },

  // UI Tabs Switcher
  switchTab(tab) {
    document.getElementById('retTab1')?.classList.toggle('active', tab==='customer');
    document.getElementById('retTab2')?.classList.toggle('active', tab==='supplier');
    if(document.getElementById('retCustSection')) document.getElementById('retCustSection').style.display = tab==='customer' ? 'block' : 'none';
    if(document.getElementById('retSupSection')) document.getElementById('retSupSection').style.display = tab==='supplier' ? 'block' : 'none';
    if(tab === 'supplier') this.renderSupplier();
  },

  // 1. COMPANY (SUPPLIER) RETURNS
  async renderSupplier() {
    const q = document.getElementById('returnSearch')?.value.toLowerCase() || '';
    const list = document.getElementById('returnList');
    if(!list) return;
    let items = this._supplierReturns;
    if(q) items = items.filter(r => (r.itemName||'').toLowerCase().includes(q) || (r.supplier||'').toLowerCase().includes(q) || (r.barcode||'').includes(q));
    if(!items.length) { list.innerHTML = '<div class="empty-state">No supplier returns found</div>'; return; }
    list.innerHTML = items.map(r => 
      `<div class="item-card" style="display:flex; justify-content:space-between; padding:12px; border:1px solid var(--border); border-radius:var(--r8); margin-bottom:8px; background:var(--glass);">
        <div>
          <div style="font-weight:bold; font-size:14px; color:var(--red);">${esc(r.itemName)}</div>
          <div style="font-size:12px; color:var(--t2);">🏢 Supplier/Company: ${esc(r.supplier)}</div>
          <div style="font-size:12px; color:var(--t2); margin-top:2px;">🏷️ Barcode/IMEI: ${esc(r.barcode)}</div>
          <div style="font-size:12px; color:var(--orange); margin-top:4px;">⚠️ හේතුව (Reason): ${esc(r.reason)}</div>
          <div style="font-size:11px; color:var(--t3); margin-top:4px;">📅 දිනය (Date): ${new Date(r.date).toLocaleDateString()}</div>
        </div>
        <div style="text-align:right; display:flex; flex-direction:column; gap:8px; align-items:flex-end;">
          <select style="font-size:12px; padding:4px; border-radius:4px; background:var(--bg3); color:${['received','refunded'].includes(r.status)?'var(--green)':'var(--orange)'}; border:1px solid var(--border);" onchange="ReturnMgr.updateSupStatus('${r.id}', this.value)">
            <option value="shop" ${r.status==='shop'?'selected':''}>🏪 කඩේ තියෙනවා (At Shop)</option>
            <option value="sent" ${r.status==='sent'?'selected':''}>📤 කම්පැනියට යැව්වා (Sent)</option>
            <option value="received" ${r.status==='received'?'selected':''}>✅ අලුත් එකක් ලැබුණා (Replaced)</option>
            <option value="refunded" ${r.status==='refunded'?'selected':''}>💰 සල්ලි ලැබුණා (Refunded)</option>
            <option value="rejected" ${r.status==='rejected'?'selected':''}>❌ මාරුකරේ නෑ (Rejected)</option>
          </select>
          <button class="btn btn-danger btn-xs" onclick="ReturnMgr.delSup('${r.id}')">Delete</button>
        </div>
      </div>`
    ).join('');
  },
  openNewSupplierReturn() {
    document.getElementById('optContent').innerHTML = 
      `<div class="modal-head"><div class="modal-title">🔄 කම්පැනි රිටන් එකක් දාන්න</div><button class="modal-x" onclick="Modal.close(&apos;optModal&apos;)">✕</button></div>
      <div class="form-group"><label>අයිටම් එකේ නම (Item Name)</label><input id="retItem" type="text" placeholder="e.g. Samsung Battery..." autofocus></div>
      <div class="form-group"><label>Barcode / IMEI</label><input id="retBc" type="text" placeholder="Scan barcode..."></div>
      <div class="form-group"><label>ආයතනය / Supplier</label><input id="retSup" type="text" placeholder="e.g. Singhagiri..."></div>
      <div class="form-group"><label>දෝශය / හේතුව (Reason)</label><textarea id="retReason" rows="2" placeholder="වැඩ කරන්නේ නැත..."></textarea></div>
      <button class="btn btn-primary w-full mt-8" onclick="ReturnMgr.saveSup()">Save Supplier Return</button>`;
    Modal.open('optModal');
  },
  async saveSup() {
    const itemName = document.getElementById('retItem').value.trim();
    const barcode = document.getElementById('retBc').value.trim();
    const supplier = document.getElementById('retSup').value.trim();
    const reason = document.getElementById('retReason').value.trim();
    if(!itemName) return toast('Item required', 'error');
    await DB.push('supplierReturns', { itemName, barcode, supplier, reason, status: 'shop', date: new Date().toISOString() });
    Modal.close('optModal');
    toast('Supplier Return tracked ✅', 'success');
    await this.load();
    this.renderSupplier();
  },
  async updateSupStatus(id, status) {
    await DB.patch('supplierReturns/'+id, { status });
    toast('Status updated', 'success');
    await this.load();
    this.renderSupplier();
  },
  async delSup(id) {
    if(!confirm('Delete return record?')) return;
    await DB.del('supplierReturns/'+id);
    toast('Deleted', 'info');
    await this.load();
    this.renderSupplier();
  },

  // 2. CUSTOMER RETURNS
  async searchBill() {
    const billId = document.getElementById('retBillSearch').value.trim();
    if(!billId) return toast('Enter a Bill ID', 'error');
    const res = document.getElementById('retBillResult');
    res.innerHTML = '<div style="color:var(--t2);font-size:12px;">Searching...</div>';
    
    // fetch all sales from DB
    const allSales = DB.obj2arr(await DB.get('sales'));
    const sale = allSales.find(s => s.id === billId || s.billId === billId);
    
    if(!sale) {
      res.innerHTML = '<div style="color:var(--red);font-size:13px;padding:12px;background:var(--bg3);border-radius:6px;">❌ Bill not found. Check the ID.</div>';
      this._lastSearchedBill = null;
      return;
    }
    this._lastSearchedBill = sale;
    const cur = S.settings.currency;
    
    // Check if fully returned
    if(sale.status === 'returned') {
       res.innerHTML = `<div style="color:var(--orange);font-size:13px;padding:12px;background:var(--bg3);border-radius:6px;">⚠️ This bill has already been marked as fully returned.</div>`;
       return;
    }

    let itemsHtml = (sale.items||[]).map((it, idx) => `
      <div style="display:flex;justify-content:space-between;align-items:center;padding:8px;border-bottom:1px solid var(--border);">
        <div>
          <div style="font-size:13px;color:var(--t1);">${esc(it.name)}</div>
          <div style="font-size:11px;color:var(--t3);">${it.qty} ${it.unit} × ${cur} ${it.unitPrice.toFixed(2)}</div>
        </div>
        <div style="display:flex;align-items:center;gap:12px;">
          <div style="font-family:'JetBrains Mono';font-size:13px;">${cur} ${(it.price||0).toFixed(2)}</div>
          ${it.price > 0 ? `<button class="btn btn-ghost btn-sm" style="color:var(--red);border:1px solid var(--red);" onclick="ReturnMgr.processReturn('${sale.id}', ${idx})">↩ Return</button>` : '<span style="font-size:11px;color:var(--t3);">Returned</span>'}
        </div>
      </div>
    `).join('');

    res.innerHTML = `
      <div style="border:1px solid var(--border);border-radius:var(--r8);background:var(--bg1);">
        <div style="padding:12px;border-bottom:1px solid var(--border);background:var(--bg2);border-radius:8px 8px 0 0;">
          <div style="font-weight:bold;font-size:14px;color:var(--t1);">Bill: ${sale.billId || sale.id}</div>
          <div style="font-size:11px;color:var(--t3);">${new Date(sale.date).toLocaleString()}</div>
        </div>
        <div style="padding:8px;">${itemsHtml}</div>
      </div>
    `;
  },
  async processReturn(saleId, itemIdx) {
    const sale = this._lastSearchedBill;
    if(!sale || sale.id !== saleId) return;
    const itemToReturn = sale.items[itemIdx];
    
    document.getElementById('optContent').innerHTML = `
      <div class="modal-head"><div class="modal-title">↩ Process Return</div><button class="modal-x" onclick="Modal.close('optModal')">✕</button></div>
      <div style="margin-bottom:16px;font-size:13px;color:var(--t2);">
        Returning: <strong style="color:var(--t1)">${esc(itemToReturn.name)}</strong><br>
        Quantity: ${itemToReturn.qty}<br>
        Amount: <strong style="color:var(--red)">${S.settings.currency} ${(itemToReturn.price||0).toFixed(2)}</strong>
      </div>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <button class="btn btn-primary w-full" onclick="ReturnMgr.executeReturn('${saleId}', ${itemIdx}, 'exchange')">🔄 Exchange (Add to Cart as Credit)</button>
        <button class="btn btn-danger w-full" onclick="ReturnMgr.executeReturn('${saleId}', ${itemIdx}, 'refund')">💰 Cash Refund (Return Money)</button>
      </div>
      <div style="margin-top:16px;font-size:11px;color:var(--orange);">⚠️ Note: The returned item's stock will be added back automatically.</div>
    `;
    Modal.open('optModal');
  },
  async executeReturn(saleId, itemIdx, type) {
    const sale = this._lastSearchedBill;
    if(!sale) return;
    const item = sale.items[itemIdx];
    const amount = item.price || 0;
    
    // 1. Restore stock
    try {
      const dbItem = await DB.get('items/'+item.itemId);
      if(dbItem && dbItem.stockBatches && dbItem.stockBatches.length > 0) {
         dbItem.stockBatches[0].quantity += item.qty; // simple restore to first batch
         await DB.patch('items/'+item.itemId, { stockBatches: dbItem.stockBatches });
      }
    } catch(e) { console.error("Could not restore stock", e); }

    // 2. Mark item as returned
    sale.items[itemIdx].name = "[RETURNED] " + sale.items[itemIdx].name;
    sale.items[itemIdx].price = 0; // zero out the price
    sale.status = "partial_return";
    await DB.patch('sales/'+saleId, { items: sale.items, status: sale.status });

    // 3. Create negative sale record for accounting
    const refundRecord = {
       billId: 'RET-' + Date.now().toString().slice(-6),
       date: new Date().toISOString(),
       items: [{
         itemId: item.itemId, name: "Return: " + item.name, qty: item.qty, unit: item.unit,
         unitPrice: item.unitPrice, price: -Math.abs(amount), costPrice: item.costPrice || 0
       }],
       subtotal: -Math.abs(amount), total: -Math.abs(amount), profit: -Math.abs((amount) - (item.costPrice*item.qty||0)),
       isReturn: true
    };
    await DB.push('sales', refundRecord);

    Modal.close('optModal');

    // 4. Action based on type
    if(type === 'exchange') {
       S.cart.push({
         itemId: 'CREDIT', name: `Credit (Return: ${item.name})`, qty: 1, unit: 'pcs',
         retailP: -Math.abs(amount), wsP: -Math.abs(amount), unitPrice: -Math.abs(amount), price: -Math.abs(amount),
         discount: 0, discType: 'percent', saleMode: 'retail'
       });
       Cart.render();
       toast('Credit added to cart for exchange!', 'success');
       Nav.go('pos');
    } else {
       toast(`Cash Refund processed! Give ${S.settings.currency} ${amount.toFixed(2)} to customer.`, 'success');
       this.searchBill(); // refresh bill view
    }

    if(window.Admin) Admin.render();
  }
};


// HOOK LOAD ALL
const _origLoadAll = window.loadAll;
window.loadAll = async function() {
  if(_origLoadAll) await _origLoadAll();
  try { await RepairMgr.load(); await ReturnMgr.load(); } catch(e) {}
};




