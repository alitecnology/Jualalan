/**
 * KELASLIVE ADMIN + BILLING API (starter)
 * Dipasang sebagai FILE BARU di proyek Apps Script yang sama dengan GuruGate.gs.
 * Jangan membuat doGet/doPost baru. Ikuti instruksi integrasi di README.
 * Payment provider: Midtrans Snap (sandbox sebagai default).
 *
 * KEAMANAN: Server Key Midtrans dan kredensial owner hanya disimpan di Script Properties.
 * Jangan pernah menaruh Server Key di APK Android atau file publik.
 */
var KLA = {
  SHEET_PRICES: 'KLA_Prices',
  SHEET_ORDERS: 'KLA_Orders',
  SHEET_DEVICES: 'KLA_Devices',
  SESSION_SECONDS: 21600,
  DEFAULTS: [
    ['1d', '1 hari', 1000, 1], ['2d', '2 hari', 2000, 2],
    ['3d', '3 hari', 3000, 3], ['4d', '4 hari', 4000, 4],
    ['5d', '5 hari', 5000, 5], ['6d', '6 hari', 6000, 6],
    ['7d', '1 minggu', 6000, 7], ['30d', '1 bulan', 25000, 30]
  ]
};

/** Jalankan sekali dari editor Apps Script untuk membuat sheet database. */
function klaInitDatabase() {
  var ss = ss_();
  klaEnsureSheet_(ss, KLA.SHEET_PRICES, ['package_id','label','price_idr','duration_days','enabled']);
  klaEnsureSheet_(ss, KLA.SHEET_ORDERS, ['order_id','device_id','package_id','amount_idr','status','snap_token','payment_url','created_at','paid_at','midtrans_transaction_id']);
  klaEnsureSheet_(ss, KLA.SHEET_DEVICES, ['device_id','status','package_id','activated_at','expires_at','last_order_id','updated_at']);
  var sh = ss.getSheetByName(KLA.SHEET_PRICES);
  if (sh.getLastRow() < 2) {
    KLA.DEFAULTS.forEach(function (p) { sh.appendRow([p[0],p[1],p[2],p[3],true]); });
  }
  return 'Database KelasLive Admin siap.';
}

/** Jalankan manual sekali untuk membuat akun owner. Gunakan sandi panjang dan unik. */
function klaConfigureOwner(email, password) {
  email = String(email || '').trim().toLowerCase();
  password = String(password || '');
  if (!/^\S+@\S+\.\S+$/.test(email) || password.length < 14) {
    throw new Error('Email tidak valid atau sandi kurang dari 14 karakter.');
  }
  var salt = Utilities.getUuid() + Utilities.getUuid();
  var hash = klaPasswordHash_(salt, password);
  var p = PropertiesService.getScriptProperties();
  p.setProperties({KLA_OWNER_EMAIL: email, KLA_OWNER_SALT: salt, KLA_OWNER_HASH: hash});
  return 'Akun owner tersimpan. Sandi tidak disimpan dalam bentuk teks.';
}


/** Setup praktis: isi sementara KLA_SETUP_OWNER_EMAIL dan KLA_SETUP_OWNER_PASSWORD
 * di Project Settings > Script Properties, jalankan fungsi ini, lalu nilai sementara
 * akan dihapus otomatis. Jangan menaruh sandi di source code. */
function klaConfigureOwnerFromSetup() {
  var p=PropertiesService.getScriptProperties();
  var email=p.getProperty('KLA_SETUP_OWNER_EMAIL')||'', password=p.getProperty('KLA_SETUP_OWNER_PASSWORD')||'';
  if(!email||!password) throw new Error('Isi KLA_SETUP_OWNER_EMAIL dan KLA_SETUP_OWNER_PASSWORD di Script Properties terlebih dahulu.');
  var result=klaConfigureOwner(email,password);
  p.deleteProperty('KLA_SETUP_OWNER_EMAIL'); p.deleteProperty('KLA_SETUP_OWNER_PASSWORD');
  return result;
}

/** Jalankan manual setelah memiliki Midtrans Server Key. Default tetap sandbox. */
function klaConfigureMidtrans(serverKey, environment) {
  serverKey = String(serverKey || '').trim();
  environment = String(environment || 'sandbox').toLowerCase();
  if (!serverKey || ['sandbox','production'].indexOf(environment) < 0) throw new Error('Server Key atau environment tidak valid.');
  PropertiesService.getScriptProperties().setProperties({KLA_MIDTRANS_SERVER_KEY:serverKey, KLA_MIDTRANS_ENV:environment});
  return 'Konfigurasi Midtrans tersimpan. Jangan bagikan Server Key.';
}


/** Setup Midtrans tanpa menaruh secret di source code. Nilai setup dihapus setelah dipakai. */
function klaConfigureMidtransFromSetup() {
  var p=PropertiesService.getScriptProperties();
  var key=p.getProperty('KLA_SETUP_MIDTRANS_SERVER_KEY')||'';
  var env=p.getProperty('KLA_SETUP_MIDTRANS_ENV')||'sandbox';
  if(!key) throw new Error('Isi KLA_SETUP_MIDTRANS_SERVER_KEY di Script Properties.');
  var result=klaConfigureMidtrans(key,env);
  p.deleteProperty('KLA_SETUP_MIDTRANS_SERVER_KEY'); p.deleteProperty('KLA_SETUP_MIDTRANS_ENV');
  return result;
}

function klaEnsureSheet_(ss, name, headers) {
  var sh = ss.getSheetByName(name);
  if (!sh) sh = ss.insertSheet(name);
  if (sh.getLastRow() === 0) { sh.appendRow(headers); sh.setFrozenRows(1); }
  return sh;
}

/** Panggil dari doPost(e) untuk aksi kla_* dan webhook Midtrans. */
function klaDispatch_(e, body) {
  body = body || {};
  var route = String((e && e.parameter && e.parameter.a) || body.a || '');
  if (route === 'midtrans_notification') return klaMidtransNotification_(body);
  if (route === 'kla_checkout') return klaCreateCheckout_(body);
  if (route.indexOf('kla_') !== 0) return null;
  if (route === 'kla_owner_login') return klaOwnerLogin_(body);
  var owner = klaRequireSession_(body.session);
  if (!owner) return {ok:false,error:'Sesi admin tidak valid atau sudah berakhir.'};
  if (route === 'kla_dashboard') return klaDashboard_();
  if (route === 'kla_prices') return {ok:true,prices:klaGetPrices_()};
  if (route === 'kla_set_price') return klaSetPrice_(body);
  if (route === 'kla_orders') return {ok:true,orders:klaRecentRows_(KLA.SHEET_ORDERS, 100)};
  if (route === 'kla_devices') return {ok:true,devices:klaRecentRows_(KLA.SHEET_DEVICES, 200)};
  if (route === 'kla_device_status') return klaSetDeviceStatus_(body);
  if (route === 'kla_logout') { CacheService.getScriptCache().remove('KLA_SESS_'+klaTokenHash_(body.session)); return {ok:true}; }
  return {ok:false,error:'Aksi admin tidak dikenal.'};
}

function klaOwnerLogin_(b) {
  var cache=CacheService.getScriptCache(), attemptKey='KLA_LOGIN_FAIL_'+klaTokenHash_(String(b.email||'').toLowerCase());
  var attempts=Number(cache.get(attemptKey)||0);
  if(attempts>=10) return {ok:false,error:'Terlalu banyak percobaan. Coba lagi sekitar 15 menit.'};
  var p = PropertiesService.getScriptProperties();
  var email = String(b.email || '').trim().toLowerCase();
  var savedEmail = p.getProperty('KLA_OWNER_EMAIL') || '';
  var salt = p.getProperty('KLA_OWNER_SALT') || '';
  var expected = p.getProperty('KLA_OWNER_HASH') || '';
  if (!savedEmail || !salt || !expected) return {ok:false,error:'Akun owner belum dikonfigurasi di server.'};
  var actual = klaPasswordHash_(salt, String(b.password || ''));
  if (email !== savedEmail || !klaConstantEqual_(actual, expected)) { cache.put(attemptKey,String(attempts+1),900); return {ok:false,error:'Email atau sandi salah.'}; }
  cache.remove(attemptKey);
  var token = Utilities.getUuid().replace(/-/g,'') + Utilities.getUuid().replace(/-/g,'');
  CacheService.getScriptCache().put('KLA_SESS_'+klaTokenHash_(token), savedEmail, KLA.SESSION_SECONDS);
  return {ok:true,session:token,expires_in:KLA.SESSION_SECONDS,owner_email:savedEmail};
}

function klaPasswordHash_(salt, password) {
  var value = salt + '|' + password;
  for (var i=0; i<3000; i++) {
    var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, value, Utilities.Charset.UTF_8);
    value = bytes.map(function (b) { return ('0'+(b&255).toString(16)).slice(-2); }).join('');
  }
  return value;
}
function klaTokenHash_(token) {
  var b = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(token||''), Utilities.Charset.UTF_8);
  return b.map(function(x){return ('0'+(x&255).toString(16)).slice(-2);}).join('');
}
function klaRequireSession_(token) {
  if (!token || String(token).length < 40) return false;
  return CacheService.getScriptCache().get('KLA_SESS_'+klaTokenHash_(token)) || false;
}
function klaConstantEqual_(a,b) {
  if (a.length !== b.length) return false;
  var diff=0; for(var i=0;i<a.length;i++) diff |= a.charCodeAt(i)^b.charCodeAt(i);
  return diff===0;
}

function klaGetPrices_() {
  var sh = ss_().getSheetByName(KLA.SHEET_PRICES);
  if (!sh || sh.getLastRow()<2) return [];
  return sh.getRange(2,1,sh.getLastRow()-1,5).getValues().filter(function(r){return r[0];}).map(function(r){
    return {package_id:String(r[0]),label:String(r[1]),price_idr:Number(r[2]),duration_days:Number(r[3]),enabled:r[4]===true || String(r[4])==='TRUE'};
  });
}
function klaSetPrice_(b) {
  var id=String(b.package_id||''); var price=Number(b.price_idr);
  if (!id || !isFinite(price) || price<100 || price>100000000 || Math.floor(price)!==price) return {ok:false,error:'Paket atau harga tidak valid.'};
  var sh=ss_().getSheetByName(KLA.SHEET_PRICES), rows=sh.getDataRange().getValues();
  for(var i=1;i<rows.length;i++) if(String(rows[i][0])===id){ sh.getRange(i+1,3).setValue(price); return {ok:true,package_id:id,price_idr:price}; }
  return {ok:false,error:'Paket tidak ditemukan.'};
}
function klaDashboard_() {
  var devices=klaRecentRows_(KLA.SHEET_DEVICES,1000), orders=klaRecentRows_(KLA.SHEET_ORDERS,1000);
  var now=Date.now(), active=devices.filter(function(d){return d.status==='active' && new Date(d.expires_at).getTime()>now;}).length;
  var revenue=orders.filter(function(o){return o.status==='paid';}).reduce(function(s,o){return s+Number(o.amount_idr||0);},0);
  return {ok:true,active_customers:active,total_devices:devices.length,paid_orders:orders.filter(function(o){return o.status==='paid';}).length,revenue_idr:revenue};
}
function klaRecentRows_(sheetName, limit) {
  var sh=ss_().getSheetByName(sheetName); if(!sh||sh.getLastRow()<2)return [];
  var n=Math.min(limit,sh.getLastRow()-1), start=sh.getLastRow()-n+1, vals=sh.getRange(start,1,n,sh.getLastColumn()).getValues();
  var heads=sh.getRange(1,1,1,sh.getLastColumn()).getValues()[0];
  return vals.reverse().map(function(row){var o={};heads.forEach(function(h,i){o[h]=row[i] instanceof Date?row[i].toISOString():row[i];});return o;});
}
function klaSetDeviceStatus_(b) {
  var id=String(b.device_id||''), status=String(b.status||'');
  if(!id || ['active','blocked'].indexOf(status)<0)return {ok:false,error:'ID perangkat atau status tidak valid.'};
  var sh=ss_().getSheetByName(KLA.SHEET_DEVICES); if(!sh)return {ok:false,error:'Database belum disiapkan.'};
  var vals=sh.getDataRange().getValues();
  for(var i=1;i<vals.length;i++) if(String(vals[i][0])===id){sh.getRange(i+1,2).setValue(status);sh.getRange(i+1,7).setValue(new Date().toISOString());return {ok:true};}
  return {ok:false,error:'Perangkat tidak ditemukan.'};
}

/** Checkout publik: harga dihitung server-side, bukan dipercaya dari aplikasi. */
function klaCreateCheckout_(b) {
  var device=String(b.device_id||'').trim(), pkg=String(b.package_id||'');
  if(!/^[A-Za-z0-9_-]{8,100}$/.test(device)) return {ok:false,error:'ID perangkat tidak valid.'};
  var price=klaGetPrices_().filter(function(p){return p.package_id===pkg && p.enabled;})[0];
  if(!price)return {ok:false,error:'Paket tidak tersedia.'};
  var key=PropertiesService.getScriptProperties().getProperty('KLA_MIDTRANS_SERVER_KEY');
  if(!key)return {ok:false,error:'Payment gateway belum dikonfigurasi.'};
  var orderId='KL-'+Utilities.getUuid().replace(/-/g,'').slice(0,20).toUpperCase();
  var env=PropertiesService.getScriptProperties().getProperty('KLA_MIDTRANS_ENV')||'sandbox';
  var endpoint=env==='production'?'https://app.midtrans.com/snap/v1/transactions':'https://app.sandbox.midtrans.com/snap/v1/transactions';
  var payload={transaction_details:{order_id:orderId,gross_amount:price.price_idr},item_details:[{id:pkg,price:price.price_idr,quantity:1,name:'KelasLive '+price.label}]};
  var auth=Utilities.base64Encode(key+':');
  var response=UrlFetchApp.fetch(endpoint,{method:'post',contentType:'application/json',headers:{Authorization:'Basic '+auth,Accept:'application/json'},payload:JSON.stringify(payload),muteHttpExceptions:true});
  var code=response.getResponseCode(), data={}; try{data=JSON.parse(response.getContentText());}catch(e){}
  if(code<200||code>=300||!data.token||!data.redirect_url)return {ok:false,error:'Pembuatan pembayaran gagal.',gateway_status:code};
  var sh=ss_().getSheetByName(KLA.SHEET_ORDERS);
  sh.appendRow([orderId,device,pkg,price.price_idr,'pending',data.token,data.redirect_url,new Date().toISOString(),'','']);
  return {ok:true,order_id:orderId,amount_idr:price.price_idr,payment_url:data.redirect_url,snap_token:data.token,environment:env};
}

/** Webhook: URL Midtrans harus diatur ke WEB_APP_URL/exec?a=midtrans_notification. */
function klaMidtransNotification_(b) {
  var lock=LockService.getScriptLock(); lock.waitLock(20000);
  try { return klaMidtransNotificationLocked_(b); } finally { lock.releaseLock(); }
}
function klaMidtransNotificationLocked_(b) {
  var props=PropertiesService.getScriptProperties(), key=props.getProperty('KLA_MIDTRANS_SERVER_KEY')||'';
  if(!key)return {ok:false,error:'Server Key belum disetel.'};
  var expected=klaSha512_(String(b.order_id||'')+String(b.status_code||'')+String(b.gross_amount||'')+key);
  if(!klaConstantEqual_(expected,String(b.signature_key||'')))return {ok:false,error:'Signature tidak valid.'};
  var status=String(b.transaction_status||''), fraud=String(b.fraud_status||'');
  var success=(status==='settlement'||status==='capture') && (!fraud||fraud==='accept');
  var sh=ss_().getSheetByName(KLA.SHEET_ORDERS), rows=sh.getDataRange().getValues(), rowIndex=-1, order=null;
  for(var i=1;i<rows.length;i++) if(String(rows[i][0])===String(b.order_id)){rowIndex=i+1;order=rows[i];break;}
  if(!order)return {ok:false,error:'Order tidak ditemukan.'};
  if(Number(String(order[3]))!==Math.round(Number(b.gross_amount)))return {ok:false,error:'Nominal transaksi tidak cocok.'};
  if(String(order[4])==='paid')return {ok:true,duplicate:true};
  if(success){
    var device=String(order[1]), pkg=String(order[2]), price=klaGetPrices_().filter(function(p){return p.package_id===pkg;})[0];
    if(!price)return {ok:false,error:'Paket transaksi tidak ditemukan.'};
    klaActivateDevice_(device,pkg,price.duration_days,String(b.order_id));
    sh.getRange(rowIndex,5).setValue('paid'); sh.getRange(rowIndex,9).setValue(new Date().toISOString()); sh.getRange(rowIndex,10).setValue(String(b.transaction_id||''));
  } else if(['expire','cancel','deny','failure'].indexOf(status)>=0){sh.getRange(rowIndex,5).setValue(status);}
  return {ok:true,processed:success,status:status};
}
function klaActivateDevice_(device,pkg,days,orderId) {
  var sh=ss_().getSheetByName(KLA.SHEET_DEVICES), vals=sh.getDataRange().getValues(), idx=-1, oldExpiry=0;
  for(var i=1;i<vals.length;i++)if(String(vals[i][0])===device){idx=i+1;oldExpiry=new Date(vals[i][4]).getTime()||0;break;}
  var start=Math.max(Date.now(),oldExpiry), expires=new Date(start+days*86400000).toISOString(), now=new Date().toISOString();
  var row=[device,'active',pkg,now,expires,orderId,now];
  if(idx>0)sh.getRange(idx,1,1,row.length).setValues([row]);else sh.appendRow(row);
}
function klaSha512_(value) {
  var bytes=Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_512,value,Utilities.Charset.UTF_8);
  return bytes.map(function(b){return ('0'+(b&255).toString(16)).slice(-2);}).join('');
}

/** Endpoint pemeriksaan masa aktif untuk aplikasi User; tetap baca dari server. */
function klaCheckDevice_(deviceId) {
  var sh=ss_().getSheetByName(KLA.SHEET_DEVICES); if(!sh)return {ok:true,active:false};
  var vals=sh.getDataRange().getValues();
  for(var i=1;i<vals.length;i++)if(String(vals[i][0])===String(deviceId)){
    var expiry=new Date(vals[i][4]).getTime();
    return {ok:true,active:vals[i][1]==='active'&&expiry>Date.now(),expires_at:vals[i][4],package_id:vals[i][2]};
  }
  return {ok:true,active:false};
}
