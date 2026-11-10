const {chromium}=require('playwright');const fs=require('fs');
/* Prueba de punta a punta en navegador real contra el servidor real.
   Uso: SERVER_LOG=ruta/al/log.txt node scripts/e2e-navegador.js   (ver docs/CONEXION-API.md, sección 6) */
const SD=process.env.OUT||'.',LOG=process.env.SERVER_LOG,API=process.env.API||'http://127.0.0.1:8787',WEB=process.env.WEB||'http://127.0.0.1:8081/';
const PASS='correcto caballo bateria grapa 2026';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const results=[];const ok=(n,c,x='')=>{results.push([c?'OK ':'FALLA',n,x]);console.log((c?'OK   ':'FALLA'),n,x)};
async function tokenFor(email,kind){for(let i=0;i<30;i++){const L=fs.readFileSync(LOG,'utf8').split('\n').filter(l=>l.includes('mail_console')&&l.includes(kind==='verify'?'Confirma':'Restablece'));
  for(const l of L.reverse()){const j=JSON.parse(l);const m=j.text.match(/#\/(verify|reset)\/([\w-]{47})/);if(m&&(m[1]===kind))return m[2]}await sleep(200)}return null}
async function apiCall(m,p,b,t){const r=await fetch(API+'/v1'+p,{method:m,headers:{'content-type':'application/json',...(t?{authorization:'Bearer '+t}:{})},body:b?JSON.stringify(b):undefined});let j=null;try{j=await r.json()}catch(e){}return{s:r.status,j}}
(async()=>{
  // --- datos de otra persona (bob) creados directo por API
  await apiCall('POST','/auth/register',{email:'bob@example.com',handle:'bob_cazador',password:PASS,lang:'es'});
  const bt=await tokenFor('bob','verify');await apiCall('POST','/auth/verify-email',{token:bt});
  const bl=(await apiCall('POST','/auth/login',{email:'bob@example.com',password:PASS})).j;
  await apiCall('PATCH','/me',{mode:'cazador'},bl.access_token);
  const o1=await apiCall('POST','/offers',{category_id:'super',title:'Leche en polvo entera 800 g',store:'Súper Familiar',price_old_cents:720,price_now_cents:545,ean:'7412345000110',link_url:'https://www.superfamiliar.example/leche'},bl.access_token);
  ok('bob publica oferta vía API',o1.s===201,JSON.stringify(o1.j).slice(0,120));
  await apiCall('POST','/offers',{category_id:'tech',title:'Audífonos Bluetooth',store:'TecnoPlaza',price_old_cents:5990,price_now_cents:3990,link_url:'https://www.tecnoplaza.example/aud'},bl.access_token);

  const xs=await apiCall('POST','/offers',{category_id:'super',title:'<img src=x onerror=window.__xss=1>Oferta',store:'<b>Tienda</b>',price_old_cents:1000,price_now_cents:500,link_url:'https://www.tienda.example/x'},bl.access_token);ok('bob publica oferta con HTML malicioso',xs.s===201,String(xs.s));
  const br=await chromium.launch();
  const ctx=await br.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
  await ctx.addInitScript(a=>{window.TONALLI_API=a},API);
  const pg=await ctx.newPage();const errs=[];pg.on('pageerror',e=>errs.push(e.message));pg.on('console',m=>{if(m.type()==='error')errs.push('console:'+m.text())});
  const shot=n=>pg.screenshot({path:SD+'/s-'+n+'.png'});
  await pg.goto(WEB);await pg.waitForSelector('h1.brand');
  ok('muestra pantalla de acceso',await pg.locator('form[data-s=login]').count()===1);await shot('1-login');
  // registro con datos inválidos
  await pg.click('[data-a=authMode][data-m=register]');
  await pg.fill('#au-e','ana@example.com');await pg.fill('#au-h','ana_test');await pg.fill('#au-p','corta');await pg.click('form button.primary');
  ok('rechaza contraseña corta en el cliente',(await pg.textContent('.err')).includes('15'));
  await pg.fill('#au-p',PASS);await pg.click('form button.primary');
  await pg.waitForSelector('form[data-s=login]');ok('registro ok, vuelve al login con aviso',(await pg.textContent('main')).includes('correo para confirmarla'));await shot('2-registrada');
  // login sin verificar
  await pg.fill('#au-e','ana@example.com');await pg.fill('#au-p','mala contraseña que no es');await pg.click('form button.primary');
  await pg.waitForFunction(()=>document.querySelector('.err')&&document.querySelector('.err').textContent.length>0);
  ok('login con clave incorrecta muestra error',(await pg.textContent('.err')).includes('incorrectos'));
  // enlace de verificación (como si lo abriera desde el correo)
  const vt=await tokenFor('ana','verify');ok('llegó el correo de verificación',!!vt);
  await pg.goto(WEB+'#/verify/'+vt);await pg.reload();
  await pg.waitForSelector('form[data-s=login]');
  ok('enlace de verificación confirma el correo',(await pg.textContent('main')).includes('Correo confirmado'));
  ok('token quitado de la URL',!(await pg.url()).includes(vt));
  await pg.fill('#au-e','ana@example.com');await pg.fill('#au-p',PASS);await pg.click('form button.primary');
  await pg.waitForSelector('nav.bar');ok('login ok, aparece la barra de pestañas',true);
  await pg.waitForSelector('.row');await shot('3-home');
  await sleep(300);ok('HTML del servidor se escapa (sin XSS)',(await pg.evaluate(()=>window.__xss))===undefined&&(await pg.textContent('main')).includes('<img src=x'));
  const rows=await pg.locator('.row').count();ok('home lista ofertas del servidor (categoría super)',rows>=1,'filas='+rows);
  // abrir detalle
  await pg.click('.row:has-text("Leche")');await pg.waitForSelector('.dt');await sleep(600);await shot('4-detalle');
  ok('detalle muestra precio en dólares',(await pg.textContent('.bigp')).includes('$5.45'),(await pg.textContent('.bigp')).replace(/\s+/g,' '));
  // votar
  await pg.click('.vb.pos');await sleep(600);
  ok('voto positivo registrado (temperatura 1°)',(await pg.textContent('.gnum')).includes('1°'));
  ok('botón votar queda presionado',await pg.getAttribute('.vb.pos','aria-pressed')==='true');
  await pg.click('.vb.pos');await sleep(500);ok('quitar voto vuelve a 0°',(await pg.textContent('.gnum')).includes('0°'));
  // comentar: rastreador no puede
  await pg.fill('#cmt','Confirmo, la vi hoy.');await pg.click('form[data-s=comment] button');await sleep(600);
  const t1=await pg.textContent('#toast');ok('comentar siendo Rastreador pide modo Cazador',t1.includes('Cazador'),t1);
  // modo cazador
  await pg.click('.tbtn[data-t=me]');await pg.click('[data-a=role][data-r=cazador]');await sleep(500);
  ok('cambia a modo Cazador en el servidor',await pg.getAttribute('[data-a=role][data-r=cazador]','aria-pressed')==='true');await shot('5-perfil');
  await pg.click('.tbtn[data-t=home]');await pg.waitForSelector('.row');await pg.click('.row:has-text("Leche")');await pg.waitForSelector('#cmt');await sleep(500);
  await pg.fill('#cmt','Confirmo, la vi hoy.');await pg.click('form[data-s=comment] button');await sleep(700);
  ok('comentario publicado',(await pg.textContent('main')).includes('Confirmo, la vi hoy.'));
  // reportar
  await pg.click('[data-a=report]');await pg.waitForSelector('form[data-s=report]');await pg.selectOption('#ro',{index:1});await pg.click('form[data-s=report] button.primary');await sleep(600);
  ok('reporte enviado',(await pg.textContent('#toast')).length>0,await pg.textContent('#toast'));
  // alertas
  await pg.click('.tbtn[data-t=alerts]');await pg.fill('#ak','leche');await pg.fill('#am','6');await pg.click('form[data-s=alert] button.primary');await sleep(700);
  ok('alerta creada en el servidor',(await pg.textContent('main')).includes('leche'));
  await pg.click('[data-a=testAlerts]');await sleep(700);await shot('6-alertas');
  const al=await pg.evaluate(async()=>{await loadNotifs();return S.notifs.length});ok('la alerta generó notificación',al>=1,'notifs='+al);
  // publicar
  await pg.click('.tbtn[data-t=post]');await pg.waitForSelector('form[data-s=pub]');
  await pg.fill('#f-title','Arroz blanco 5 lb');await pg.fill('#f-store','Súper Familiar');await pg.fill('#f-old','3.10');await pg.fill('#f-now','2.45');await pg.fill('#f-url','https://www.superfamiliar.example/arroz');
  await pg.click('form[data-s=pub] button.primary');await pg.waitForSelector('.dt',{timeout:5000});await sleep(600);await shot('7-publicada');
  ok('oferta publicada y abierta en detalle',(await pg.textContent('.dt')).includes('Arroz'));
  const karma=await pg.evaluate(()=>S.user.karma);ok('karma subió por publicar y comentar (servidor)',karma>=15,'karma='+karma);
  // escáner por código
  await pg.click('.tbtn[data-t=scan]');await pg.fill('#q','7412345000110');await pg.click('form[data-s=scan] button.primary');await sleep(700);
  ok('escáner busca el código en el servidor',(await pg.locator('.row').count())>=1);await shot('8-escaner');
  // recargar: sesión se recupera con el token de renovación
  await pg.reload();await pg.waitForSelector('nav.bar',{timeout:8000});ok('al recargar la sesión se recupera sola',true);
  // cerrar sesión
  await pg.click('.tbtn[data-t=me]');await pg.click('[data-a=goNotif]');await pg.click('[data-a=logout]');await pg.waitForSelector('form[data-s=login]');
  ok('cerrar sesión vuelve al acceso',true);
  const rt=await pg.evaluate(()=>localStorage.getItem('tonalli.rt'));ok('token de renovación borrado',rt===null);
  // olvidé contraseña
  await pg.click('[data-a=authMode][data-m=forgot]');await pg.fill('#au-e','ana@example.com');await pg.click('form button.primary');await pg.waitForSelector('form[data-s=login]');
  const rtk=await tokenFor('ana','reset');ok('llegó correo de restablecimiento',!!rtk);
  await pg.goto(WEB+'#/reset/'+rtk);await pg.reload();await pg.waitForSelector('form[data-s=reset]');
  await pg.fill('#au-p','otra frase larga y distinta 2027!');await pg.click('form button.primary');await pg.waitForSelector('form[data-s=login]');
  ok('restablecer contraseña funciona',(await pg.textContent('main')).includes('Contraseña cambiada'));
  await pg.fill('#au-e','ana@example.com');await pg.fill('#au-p','otra frase larga y distinta 2027!');await pg.click('form button.primary');await pg.waitForSelector('nav.bar');ok('login con la contraseña nueva',true);
  // sesión vencida: invalidar token de acceso y renovar
  const r=await pg.evaluate(async()=>{A.at='bat_invalido';const me=await api('GET','/me');return me.handle});ok('renovación automática de token vencido',r==='ana_test',r);
  const real=errs.filter(x=>!/ERR_TUNNEL|401|Failed to load resource/.test(x));ok('sin errores de JavaScript',real.length===0,real.join(' | ').slice(0,300));
  await br.close();
  console.log('\nRESUMEN',results.filter(r=>r[0]==='OK ').length+'/'+results.length);
  process.exit(results.some(r=>r[0]!=='OK ')?1:0);
})().catch(e=>{console.error('ERROR',e);process.exit(2)});
