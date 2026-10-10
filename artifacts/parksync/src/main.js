import { api, session } from './api.js';
import './styles.css';

const app = document.querySelector('#app');
let user = null;
let pageRequest = 0;
let currentLocation = null;
let slotsCache = [];
let selectedSlot = null;
let quoteCache = null;
let quoteParams = null;
let toastTimer;
const customerNav = [
  ['dashboard','Overview','⌂'],['search','Find parking','⌕'],['bookings','My bookings','▤'],
  ['vehicles','My vehicles','◇'],['payments','Payments','↗'],['notifications','Notifications','◉'],
  ['support','Support','?'],['profile','Profile','○']
];
const adminNav = [
  ['admin','Operations','⌂'],['admin-users','Users','◎'],['admin-locations','Facilities','⌖'],
  ['admin-bookings','Bookings','▤'],['admin-payments','Payments','↗'],['admin-reports','Reports','▥'],['admin-support','Support','?']
];
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money = value => `₹${Number(value || 0).toLocaleString('en-IN',{maximumFractionDigits:2})}`;
const date = value => value ? new Date(value).toLocaleString('en-IN',{dateStyle:'medium',timeStyle:'short'}) : '—';
const shortDate = value => value ? new Date(value).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'}) : '—';
const status = (s='') => `<span class="badge ${['ACTIVE','COMPLETED','AVAILABLE','PAID','DEMO_PAID','OPEN','RESOLVED'].includes(s)?'ok':['UPCOMING','RESERVED','PENDING','IN_PROGRESS'].includes(s)?'warn':['CANCELLED','DISABLED','INACTIVE','CLOSED'].includes(s)?'bad':''}" data-testid="status-${esc(s).toLowerCase()}">${esc(s.replaceAll('_',' '))}</span>`;
const input = (label,name,type='text',value='',placeholder='',required=true,autocomplete='',readOnly=false) => {
  const autocompleteHint=autocomplete||({name:'name',phone:'tel',email:'email',password:'current-password',currentPassword:'current-password',newPassword:'new-password',confirmPassword:'new-password'}[name]||'');
  return `<div class="field"><label for="${esc(name)}">${esc(label)}</label><input id="${esc(name)}" name="${esc(name)}" type="${type}" value="${esc(value)}" placeholder="${esc(placeholder)}" ${autocompleteHint?`autocomplete="${esc(autocompleteHint)}"`:''} ${required?'required':''} ${readOnly?'readonly aria-readonly="true"':''} data-testid="input-${esc(name)}"></div>`;
};
const select = (label,name,options,value='') => `<div class="field"><label for="${esc(name)}">${esc(label)}</label><select id="${esc(name)}" name="${esc(name)}" data-testid="select-${esc(name)}">${options.map(([v,l])=>`<option value="${esc(v)}" ${v===value?'selected':''}>${esc(l)}</option>`).join('')}</select></div>`;
const btn = (label,action,attrs='') => `<button class="btn btn-primary" data-action="${action}" ${attrs}>${label}</button>`;
function notify(message, error=false) {
  document.querySelector('.toast')?.remove();
  const el=document.createElement('div'); el.className=`toast${error?' error':''}`; el.setAttribute('role','status'); el.dataset.testid='status-toast'; el.textContent=message; document.body.append(el);
  clearTimeout(toastTimer); toastTimer=setTimeout(()=>el.remove(),3600);
}
function setRoute(route){ const next=`#/${route}`; if(location.hash===next){ loadPage(); return; } location.hash=next; }
function routeParts(){ return ((location.hash.replace(/^#\/?/,'')||'dashboard').split('?')[0]||'dashboard').split('/').filter(Boolean); }
function routeName(){ return routeParts()[0]||'dashboard'; }
function isAdmin(){ return user?.role==='ADMIN'; }
function loading(){return `<div class="grid"><div class="panel"><div class="loading-line"></div><div class="loading-line" style="margin-top:18px;width:80%"></div><div class="loading-line" style="margin-top:18px;width:64%"></div></div></div>`;}
function failed(error,retry){return `<div class="error" role="alert" data-testid="status-error">${esc(error.message||error)} <button class="text-button" data-action="${retry}">Try again</button></div>`;}
function empty(title,description,action=''){return `<div class="empty" data-testid="state-empty"><div class="empty-icon">—</div><strong>${esc(title)}</strong>${esc(description)}${action?`<div style="margin-top:16px">${action}</div>`:''}</div>`;}
function shell(route,content){
  const nav=isAdmin()?adminNav:customerNav, active=route.startsWith('admin')?route:route;
  return `<div class="shell"><aside class="sidebar" id="sidebar"><div class="brand">PARK<span>SYNC</span></div><div class="nav-label">${isAdmin()?'OPERATIONS':'YOUR PARKSYNC'}</div><nav class="nav-list">${nav.map(([id,label,glyph])=>`<a href="#/${id}" class="nav-link ${active===id?'active':''}" data-testid="link-${id}"><span class="glyph">${glyph}</span>${label}</a>`).join('')}</nav><div class="side-bottom"><div class="side-user" data-testid="text-session-user">${esc(user?.name)} · ${isAdmin()?'Operator':'Driver'}</div><button class="btn btn-quiet btn-sm" data-action="logout" data-testid="button-logout">Sign out</button></div></aside><main class="main"><header class="topbar"><button class="btn btn-secondary mobile-toggle" data-action="menu" aria-label="Open menu">Menu</button><div class="breadcrumb">MUMBAI · ${isAdmin()?'PARKING OPERATIONS':'PARKING COMPANION'}</div><div class="top-actions"><span class="badge">${isAdmin()?'ADMIN CONSOLE':'LIVE SESSION'}</span></div></header><div id="page">${content}</div></main></div>`;
}
function heading(title,subtitle,action=''){return `<div class="page-heading"><div><div class="eyebrow">PARKSYNC / MUMBAI</div><h1>${title}</h1><p>${subtitle}</p></div>${action}</div>`;}
function authPage(mode='login',error=''){
  const register=mode==='register';
  app.innerHTML=`<div class="auth-wrap"><section class="auth-art"><div class="brand">PARK<span>SYNC</span></div><div class="road"><div class="car"></div></div><div class="auth-copy"><div class="eyebrow">LESS CIRCLING. MORE ARRIVING.</div><h1>Your spot.<br>Already sorted.</h1><p>Browse Mumbai parking listings, check the displayed slot details, and plan your arrival with fewer unknowns.</p></div><div class="footer-note">A clearer route from “where do I park?” to “I’m here.”</div></section><section class="auth-form-side"><div class="auth-card"><div class="eyebrow">${register?'A BETTER ARRIVAL STARTS HERE':'WELCOME BACK'}</div><h2>${register?'Create your account':'Sign in'}</h2><p>${register?'One account for every smoother arrival.':'Your next parking plan is one sign-in away.'}</p>${error?`<div class="error" role="alert" data-testid="status-auth-error">${esc(error)}</div>`:''}<form data-form="${register?'register':'login'}">${register?input('Full name','name')+input('Mobile number','phone','tel'):''}${input('Email address','email','email','', 'you@example.com')}${input('Password','password','password','','',true,register?'new-password':'current-password')}<button class="btn btn-primary btn-block" type="submit" data-testid="button-auth-submit">${register?'Create account':'Sign in'} <span aria-hidden="true">→</span></button></form><div class="auth-switch">${register?'Already have an account?':'New to PARKSYNC?'} <button class="text-button" data-action="auth-switch" data-mode="${register?'login':'register'}" data-testid="button-auth-switch">${register?'Sign in':'Create an account'}</button></div><p class="footer-note">Your account is protected. PARKSYNC never stores payment card details.</p></div></section></div>`;
  if(register){
    const form=app.querySelector('form[data-form="register"]');
    const submit=form?.querySelector('button[type="submit"]');
    submit?.insertAdjacentHTML('beforebegin',`${input('Confirm password','confirmPassword','password','','',true)}<small class="footer-note">Use 10–72 characters with upper and lower case letters, a number, and a symbol.</small><details class="terms-copy"><summary>Read PARKSYNC terms</summary><p>PARKSYNC stores reservation and account records to operate this demonstration service. Listings marked DEMO DATA and their availability are examples, not live operator inventory. Confirm current rates and conditions with the parking operator. Payment choices marked DEMO simulate a record only and do not transfer or charge money. Keep booking details accurate and use your account only for lawful reservations.</p></details><label class="terms-check"><input type="checkbox" name="termsAccepted" required data-testid="check-terms"><span>I have read and accept the PARKSYNC terms.</span></label>`);
  }
}
async function loadPage(){
  const requestId=++pageRequest;
  const route=routeName();
  if(!session.token){if(requestId===pageRequest)authPage();return;}
  app.innerHTML=loading();
  try{
    if(!user) user=await api.me();
    if(requestId!==pageRequest)return;
    if(user?.role==='ADMIN'&&!route.startsWith('admin')){setRoute('admin');return;}
    if(user?.role!=='ADMIN'&&route.startsWith('admin')){setRoute('dashboard');return;}
    const html=await renderRoute(route,routeParts()[1]);
    if(requestId!==pageRequest||routeName()!==route)return;
    app.innerHTML=shell(route,html);
  }catch(error){
    if(requestId!==pageRequest)return;
    if(!session.token){authPage('login', error.message);return;}
    app.innerHTML=shell(route,failed(error,'retry'));
  }
}
const listOf = data => Array.isArray(data)?data:(data?.items||data?.content||[]);
async function renderRoute(route,id){
  switch(route){
    case 'dashboard': return isAdmin()?adminOverview():dashboard();
    case 'search': return searchLocations();
    case 'location': return locationDetails(id);
    case 'bookings': return customerBookings();
    case 'vehicles': return vehiclesPage();
    case 'profile': return profilePage();
    case 'notifications': return notificationsPage();
    case 'payments': return paymentsPage();
    case 'support': return supportPage();
    case 'admin': return adminOverview();
    case 'admin-users': return adminUsers();
    case 'admin-locations': return adminLocations();
    case 'admin-bookings': return adminBookings();
    case 'admin-payments': return adminPayments();
    case 'admin-reports': return adminReports();
    case 'admin-support': return adminSupport();
    default:return empty('Page not found','This PARKSYNC page is not available.',btn('Back to overview','go-dashboard'));
  }
}
async function dashboard(){
  const d=await api.dashboard(), bookings=listOf(d.recentBookings), notifications=listOf(d.notifications), vehicles=listOf(d.vehicles);
  return `${heading(`Good to see you, ${esc((user?.name||'').split(' ')[0])}.`,'A parking plan that’s ready when you are.',btn('Find a spot','go-search'))}<div class="stats"><div class="stat"><span>ACTIVE PLANS</span><strong data-testid="text-active-bookings">${d.activeBookingCount??0}</strong><small>Confirmed visits</small></div><div class="stat"><span>YOUR VEHICLES</span><strong>${vehicles.length}</strong><small>Ready to book</small></div><div class="stat"><span>UNREAD UPDATES</span><strong>${notifications.filter(n=>!n.read).length}</strong><small>Check what’s new</small></div><div class="stat"><span>IN MUMBAI</span><strong>24/7</strong><small>Plan ahead, arrive easy</small></div></div><div class="grid columns"><section class="panel"><div class="panel-title"><h2>Recent bookings</h2><a class="text-button" href="#/bookings">All bookings →</a></div>${bookings.length?bookings.slice(0,4).map(bookingCard).join(''):empty('No bookings yet','Your confirmed visits will show up here.',btn('Find parking','go-search'))}</section><section class="panel"><div class="panel-title"><h2>Next up</h2><span class="badge">YOUR PLAN</span></div>${bookings.find(b=>['UPCOMING','ACTIVE'].includes(b.status))?bookingCard(bookings.find(b=>['UPCOMING','ACTIVE'].includes(b.status))):empty('Nothing on the calendar','Book a real slot before you head out.',btn('Search locations','go-search'))}<div style="margin-top:18px" class="footer-note">${notifications[0]?`Latest: ${esc(notifications[0].title)}`:'Updates about your visits will appear in Notifications.'}</div></section></div>`;
}
function bookingCard(b){
  const canCancel=b.status==='UPCOMING'&&b.startAt&&new Date(b.startAt).getTime()>Date.now();
  const canPay=(b.status==='UPCOMING'||b.status==='ACTIVE')&&b.paymentStatus==='UNPAID';
  return `<article class="booking-card" data-testid="card-booking-${esc(b.id)}"><div><div class="mono" style="font-size:10px;color:var(--amber)">${esc(b.reference)}</div><h3>${esc(b.locationName||b.location?.name||'Parking location')}</h3><p>${esc(b.slotCode||'Slot pending')} · ${esc(b.vehicleRegistration||'Vehicle')}</p><p>${date(b.startAt)} → ${date(b.endAt)}</p><div style="display:flex;gap:8px;align-items:center;margin-top:10px">${status(b.status)}<strong>${money(b.finalFee??b.estimatedFee)}</strong></div></div><div class="booking-actions">${canCancel?`<button class="btn btn-danger btn-sm" data-action="cancel-booking" data-id="${esc(b.id)}" data-testid="button-cancel-${esc(b.id)}">Cancel</button>`:''}${canPay?`<button class="btn btn-primary btn-sm" data-action="pay-booking" data-id="${esc(b.id)}">Demo payment</button>`:''}</div></article>`;
}
function quotedAmount(){
  if(!quoteCache)return null;
  const hours=Number(quoteCache.billableHours||0);
  if(!selectedSlot)return {fee:Number(quoteCache.estimatedFee),hours};
  const rate=selectedSlot.hourlyRate!=null&&selectedSlot.hourlyRate!==''?Number(selectedSlot.hourlyRate):Number(currentLocation?.hourlyRate||0);
  return {fee:Math.round((rate*hours+Number.EPSILON)*100)/100,hours};
}
function safeMapUrl(url){
  const value=String(url??'').trim();
  return /^https?:\/\//i.test(value)?value:'';
}
function ticketThread(ticket){
  const messages=Array.isArray(ticket.messages)?ticket.messages:[];
  const thread=messages.filter(message=>!(message.authorRole==='CUSTOMER'&&message.message===ticket.description));
  if(!thread.length)return '';
  return thread.map(message=>`<div class="notice"><strong>${esc(message.authorName||'PARKSYNC')} · ${esc(String(message.authorRole||'').replaceAll('_',' '))}</strong><br>${esc(message.message)}<div class="footer-note">${date(message.createdAt)}</div></div>`).join('');
}
async function searchLocations(){
  const p=new URLSearchParams(location.hash.split('?')[1]||'');
  const query={q:p.get('q')||'',category:p.get('category')||'',vehicleType:p.get('vehicleType')||'',availableOnly:p.get('availableOnly')||'',maxHourlyRate:p.get('maxHourlyRate')||'',sort:p.get('sort')||''};
  const locations=listOf(await api.locations(query));
  return `${heading('Find your parking spot','Browse listed locations and review availability. Confirm current conditions with each operator.')}${locations.some(l=>l.demoData)?'<div class="alert" data-testid="notice-demo-locations"><strong>Demo facility data:</strong> Some initial locations are demonstration records. Check the label on each facility before reserving.</div>':''}<form class="toolbar" data-form="search"><input name="q" placeholder="Search area or facility" value="${esc(query.q)}" aria-label="Search locations" data-testid="input-location-query"><select name="category" data-testid="select-location-category"><option value="">All facility types</option>${[['MALL','Mall'],['PUBLIC','Public'],['STANDALONE','Standalone'],['OTHER','Other']].map(([v,l])=>`<option ${query.category===v?'selected':''} value="${v}">${l}</option>`).join('')}</select><select name="vehicleType" data-testid="select-vehicle-type"><option value="">Any vehicle</option>${vehicleOptions.slice(1).map(([v,l])=>`<option value="${v}" ${query.vehicleType===v?'selected':''}>${l}</option>`).join('')}</select><select name="sort" data-testid="select-location-sort"><option value="">Recommended order</option><option value="price" ${query.sort==='price'?'selected':''}>Price: low first</option><option value="availability" ${query.sort==='availability'?'selected':''}>Availability</option><option value="name" ${query.sort==='name'?'selected':''}>Name</option></select><label style="display:flex;gap:8px;align-items:center;color:var(--muted);font-size:12px"><input type="checkbox" name="availableOnly" ${query.availableOnly==='true'?'checked':''} data-testid="check-available-only"> Available only</label><button class="btn btn-primary" type="submit" data-testid="button-search">Search</button></form>${locations.length?`<div class="location-grid">${locations.map(locationCard).join('')}</div>`:empty('No matching parking yet','Try another area or loosen a filter.')}`;
}
function locationCard(l){return `<article class="location-card" data-testid="card-location-${esc(l.id)}"><div class="loc-art"><span>${esc(l.category||'PARKING')} · ${l.demoData?'DEMO FACILITY':'FACILITY'}</span></div><div class="loc-body"><h3>${esc(l.name)}</h3><p>${esc(l.address)}${l.area?` · ${esc(l.area)}`:''}</p><div class="loc-meta"><span>${l.availableSlots??'—'} of ${l.totalSlots??'—'} slots available</span><span class="loc-price">${money(l.hourlyRate)} / hr</span></div><div class="loc-footer">${l.demoData?'<span class="badge demo">DEMO DATA</span>':'<span class="badge ok">LISTED</span>'}<a class="btn btn-secondary btn-sm" href="#/location/${encodeURIComponent(l.id)}" data-testid="link-location-${esc(l.id)}">View slots →</a></div></div></article>`}
async function locationDetails(id){
  if(!id)return empty('Choose a facility','Return to search to browse parking.',btn('Search parking','go-search'));
  const [l,slots]=await Promise.all([api.location(id),api.slots(id)]);
  if(String(currentLocation?.id)!==String(l.id)){selectedSlot=null;quoteCache=null;quoteParams=null;}
  currentLocation=l;slotsCache=listOf(slots);
  let vehicles=listOf(await api.vehicles());
  const intervalChosen=Array.isArray(quoteCache?.slots);
  const openIds=new Set((quoteCache?.slots||[]).map(s=>String(s.id)));
  const bookable=s=>s.status!=='DISABLED'&&(!intervalChosen||openIds.has(String(s.id)));
  const availableCount=intervalChosen?quoteCache.slots.length:Number(l.availableSlots??0);
  return `${heading(esc(l.name),'Choose a compatible slot and your visit times.',`<a class="btn btn-secondary" href="#/search">← All facilities</a>`)}${l.demoData?'<div class="alert" data-testid="notice-location-demo">Demo facility data — facility details and availability are for demonstration. Booking requests are sent to the server.</div>':''}<div class="split"><section class="grid"><div class="panel"><div class="panel-title"><h2>Facility details</h2>${status(l.active?'ACTIVE':'INACTIVE')}</div><p class="muted">${esc(l.address)}${l.area?` · ${esc(l.area)}`:''}</p><p>${esc(l.description||'')}</p><div class="kv"><span>Operating hours</span><strong>${esc(l.operatingHours||'—')}</strong></div><div class="kv"><span>Rate</span><strong>${money(l.hourlyRate)} / hour</strong></div><div class="kv"><span>Supported vehicles</span><strong>${esc((l.supportedVehicleTypes||[]).join(', ')||'—')}</strong></div>${safeMapUrl(l.mapUrl)?`<a class="text-button" href="${esc(safeMapUrl(l.mapUrl))}" target="_blank" rel="noopener">Open facility map ↗</a>`:''}</div><div class="panel"><div class="panel-title"><h2>Choose a slot</h2><span class="badge ok">${availableCount} AVAILABLE</span></div>${slotsCache.length?`<div class="slot-grid">${slotsCache.map(s=>`<button class="slot ${String(selectedSlot?.id)===String(s.id)?'selected':''}" data-action="choose-slot" data-id="${esc(s.id)}" ${bookable(s)?'':'disabled'} aria-pressed="${String(selectedSlot?.id)===String(s.id)}" data-testid="slot-${esc(s.code)}">${esc(s.code)}<br>${esc(s.status)}</button>`).join('')}</div>`:empty('Slot data unavailable','No slot records were returned for this facility.')}</div></section><aside class="panel"><div class="panel-title"><h2>Plan your visit</h2><span class="badge">RESERVE</span></div>${vehicles.length?`<form data-form="quote">${select('Vehicle','vehicleId',vehicles.map(v=>[String(v.id),`${v.registration} · ${v.brand} ${v.model}`]),quoteParams?.vehicleId||'')}${input('Arrival','startAt','datetime-local',quoteParams?.startLocal||'', '',true)}${input('Departure','endAt','datetime-local',quoteParams?.endLocal||'', '',true)}<button class="btn btn-secondary btn-block" type="submit" data-testid="button-get-quote">Get server quote</button></form>`:`${empty('Add a vehicle first','Vehicle details are required to check compatible spaces.',btn('Add vehicle','go-vehicles'))}`}${quoteCache?`<div class="notice" data-testid="quote-summary"><strong>Estimate: ${money(quotedAmount().fee)}</strong><div>${quotedAmount().hours} billable hour${quotedAmount().hours===1?'':'s'} · ${esc(quoteCache.currency||'INR')}</div>${quoteCache.pricingRule?`<small>${esc(quoteCache.pricingRule)}</small>`:''}</div>`:''}${selectedSlot&&quoteCache?`<div class="kv"><span>Selected slot</span><strong>${esc(selectedSlot.code)}</strong></div><button class="btn btn-primary btn-block" data-action="confirm-booking" data-testid="button-confirm-booking">Confirm reservation</button>`:''}<p class="footer-note" style="margin-top:16px">Billable hours come from the PARKSYNC server. The estimate uses the selected slot rate when one is set, otherwise the facility rate. A slot is reserved only after the confirmation succeeds.</p></aside></div>`;
}
async function customerBookings(){
  const bookings=listOf(await api.bookings());
  const upcoming=bookings.filter(b=>['UPCOMING','ACTIVE'].includes(b.status)), history=bookings.filter(b=>!['UPCOMING','ACTIVE'].includes(b.status));
  return `${heading('My bookings','Your reservations, arrivals, and past visits.')}${upcoming.length?`<section><div class="panel-title"><h2>Current visits</h2><span class="badge">${upcoming.length}</span></div>${upcoming.map(bookingCard).join('')}</section>`:empty('No current visits','When you reserve a space, your plan will be here.',btn('Find parking','go-search'))}<section style="margin-top:28px"><div class="panel-title"><h2>Booking history</h2></div>${history.length?history.map(bookingCard).join(''):empty('No past bookings','Completed and cancelled bookings remain available here.')}</section>`;
}
const vehicleOptions=[['','Select type'],['CAR','Car'],['MOTORCYCLE','Motorcycle'],['SCOOTER','Scooter'],['OTHER','Other']];
async function vehiclesPage(){
 const vehicles=listOf(await api.vehicles());
 return `${heading('My vehicles','Manage the vehicles you bring to every booking.',btn('Add vehicle','vehicle-add','data-testid="button-add-vehicle"'))}${vehicles.length?`<div class="grid">${vehicles.map(v=>`<article class="panel booking-card" data-testid="card-vehicle-${esc(v.id)}"><div><span class="eyebrow">${esc(v.type)}</span><h3 style="margin:8px 0">${esc(v.registration)}</h3><p class="muted">${esc([v.color,v.brand,v.model].filter(Boolean).join(' · '))}</p></div><div class="row-actions"><button class="btn btn-secondary btn-sm" data-action="vehicle-edit" data-id="${esc(v.id)}">Edit</button><button class="btn btn-danger btn-sm" data-action="vehicle-delete" data-id="${esc(v.id)}">Delete</button></div></article>`).join('')}</div>`:empty('No vehicles added','Add a vehicle to see compatible slots and reserve parking.',btn('Add your first vehicle','vehicle-add'))}`;
}
async function profilePage(){return `${heading('Profile & security','Keep your account details current.') }<div class="grid columns"><form class="panel" data-form="profile"><div class="panel-title"><h2>Your details</h2></div>${input('Full name','name','text',user?.name||'')}${input('Email address','email','email',user?.email||'', '',false,'email',true)}<p class="footer-note">Email cannot be changed.</p>${input('Mobile number','phone','tel',user?.phone||'')}<button class="btn btn-primary" type="submit" data-testid="button-save-profile">Save changes</button></form><form class="panel" data-form="password"><div class="panel-title"><h2>Change password</h2></div>${input('Current password','currentPassword','password')}${input('New password','newPassword','password')}<small class="footer-note">Use 10–72 characters with upper and lower case letters, a number, and a symbol.</small>${input('Confirm new password','confirmPassword','password')}<button class="btn btn-secondary" type="submit" data-testid="button-change-password">Update password</button></form></div>`}
async function notificationsPage(){const items=listOf(await api.notifications());return `${heading('Notifications','Updates about reservations and account activity.')}${items.length?`<div class="grid">${items.map(n=>`<article class="panel" data-testid="card-notification-${esc(n.id)}" style="display:flex;justify-content:space-between;gap:18px"><div><div style="display:flex;gap:8px;align-items:center"><h3 style="margin:0;font-size:15px">${esc(n.title)}</h3>${n.read?'<span class="badge">READ</span>':'<span class="badge warn">NEW</span>'}</div><p class="muted">${esc(n.message)}</p><small class="muted">${date(n.createdAt)}</small></div>${!n.read?`<button class="btn btn-secondary btn-sm" data-action="read-notification" data-id="${esc(n.id)}">Mark read</button>`:''}</article>`).join('')}</div>`:empty('All quiet for now','New updates will appear here.')}`}
async function paymentsPage(){const items=listOf(await api.payments());return `${heading('Payment records','Review recorded payment activity.') }<div class="alert" data-testid="notice-demo-payments"><strong>Simulated payments only.</strong> PARKSYNC demo payment records are not real charges and do not process money.</div>${items.length?table(['Reference','Booking','Amount','Method','Status','Recorded'],items.map(p=>[esc(p.reference),esc(p.bookingReference),money(p.amount),esc(p.method),`${status(p.status)} ${p.demo?'<span class="badge demo">DEMO</span>':''}`,shortDate(p.createdAt)])):empty('No payment records','A record appears here after a simulated payment is requested.')}`}
async function supportPage(){const items=listOf(await api.support());return `${heading('Support','Send a request to the parking operations team.',btn('New support request','ticket-add','data-testid="button-new-ticket"'))}${items.length?`<div class="grid">${items.map(ticketCard).join('')}</div>`:empty('No support requests','Need help with a booking? Send us a note.',btn('Create a request','ticket-add'))}`}
function ticketCard(t){return `<article class="panel" data-testid="card-ticket-${esc(t.id)}"><div class="panel-title"><h3>${esc(t.subject)}</h3>${status(t.status)}</div><div class="eyebrow">${esc(t.category)} · ${shortDate(t.createdAt)}</div><p>${esc(t.description)}</p>${ticketThread(t)}</article>`}
function table(headers,rows){return `<div class="panel table-wrap"><table><thead><tr>${headers.map(x=>`<th>${x}</th>`).join('')}</tr></thead><tbody>${rows.length?rows.map(row=>`<tr>${row.map(cell=>`<td>${cell}</td>`).join('')}</tr>`).join(''):`<tr><td colspan="${headers.length}">${empty('No records found','There is nothing to show for these filters.')}</td></tr>`}</tbody></table></div>`}
function statsPanel(items){return `<div class="stats">${items.map(([label,value,caption])=>`<div class="stat"><span>${esc(label)}</span><strong>${esc(value??'—')}</strong><small>${esc(caption||'')}</small></div>`).join('')}</div>`}
async function adminOverview(){const d=await api.adminOverview();const c=d.counts||{};const recent=listOf(d.recentBookings);return `${heading('Operations overview','A live view of facilities, reservations, and service activity.')}${statsPanel(Object.entries(c).slice(0,4).map(([k,v])=>[k.replaceAll(/([A-Z])/g,' $1').toUpperCase(),v,'Current records']).concat([['DEMO PAYMENTS TOTAL',money(d.demoPaymentsTotal),'Simulated records only']]).slice(0,5))}<div class="alert" data-testid="notice-demo-payments">Demo payment total includes simulated payment records only. This is not real revenue.</div><div class="panel"><div class="panel-title"><h2>Recent bookings</h2><a href="#/admin-bookings" class="text-button">Booking search →</a></div>${recent.length?table(['Reference','Customer','Facility','Slot','Visit','Status','Estimate'],recent.map(b=>[esc(b.reference),esc(b.customerName||'—'),esc(b.locationName||'—'),esc(b.slotCode||'—'),shortDate(b.startAt),status(b.status),money(b.estimatedFee)])):empty('No recent bookings','New reservation records will appear here.')}</div>`}
async function adminUsers(){const q=new URLSearchParams(location.hash.split('?')[1]||'').get('q')||'';const users=listOf(await api.adminUsers({q}));return `${heading('User activation','Find customer accounts and control access.') }<form class="toolbar" data-form="admin-user-search"><input name="q" value="${esc(q)}" placeholder="Search name, email, or phone" data-testid="input-user-search"><button class="btn btn-primary" type="submit">Search users</button></form>${table(['Name','Email','Phone','Role','Account','Action'],users.map(u=>[esc(u.name),esc(u.email),esc(u.phone||'—'),esc(u.role),status(u.active?'ACTIVE':'INACTIVE'),`<button class="btn ${u.active?'btn-danger':'btn-secondary'} btn-sm" data-action="toggle-user" data-id="${esc(u.id)}" data-active="${!u.active}">${u.active?'Deactivate':'Activate'}</button>`]))}`}
async function adminLocations(){const locations=listOf(await api.adminLocations());return `${heading('Facilities & slots','Maintain public parking locations and real slot status.',btn('Add facility','location-add','data-testid="button-add-location"'))}<div class="alert">Initial facilities marked DEMO DATA are demonstration records. Do not treat them as confirmed live inventory.</div>${locations.length?`<div class="grid">${locations.map(l=>`<article class="panel" data-testid="card-admin-location-${esc(l.id)}"><div class="panel-title"><div><h3 style="margin:0 0 5px">${esc(l.name)}</h3><small class="muted">${esc(l.address)}</small></div><div class="row-actions">${l.demoData?'<span class="badge demo">DEMO DATA</span>':''}${status(l.active?'ACTIVE':'INACTIVE')}</div></div><div class="kv"><span>Area / type</span><strong>${esc(l.area||'—')} · ${esc(l.category)}</strong></div><div class="kv"><span>Slots available</span><strong>${l.availableSlots??'—'} / ${l.totalSlots??'—'}</strong></div><div class="kv"><span>Hourly rate</span><strong>${money(l.hourlyRate)}</strong></div><div class="row-actions" style="margin-top:15px"><button class="btn btn-secondary btn-sm" data-action="location-edit" data-id="${esc(l.id)}">Edit facility</button><button class="btn btn-secondary btn-sm" data-action="slots-manage" data-id="${esc(l.id)}">Manage slots</button><button class="btn ${l.active?'btn-danger':'btn-primary'} btn-sm" data-action="toggle-location" data-id="${esc(l.id)}" data-active="${!l.active}">${l.active?'Deactivate':'Reactivate'}</button></div></article>`).join('')}</div>`:empty('No facilities returned','No records are available for this operator account.',btn('Add facility','location-add'))}`}
async function adminBookings(){const params=new URLSearchParams(location.hash.split('?')[1]||'');const q=params.get('q')||'', st=params.get('status')||'', locationId=params.get('locationId')||'';const [items,locations]=await Promise.all([api.adminBookings({q,status:st,locationId}),api.adminLocations()]);return `${heading('Booking operations','Search reservations and record verified arrivals and departures.') }<form class="toolbar" data-form="admin-booking-search"><input name="q" value="${esc(q)}" placeholder="Reference, customer, registration" data-testid="input-booking-search"><select name="status" data-testid="select-booking-status"><option value="">All statuses</option>${['UPCOMING','ACTIVE','COMPLETED','CANCELLED'].map(s=>`<option value="${s}" ${st===s?'selected':''}>${s}</option>`).join('')}</select><select name="locationId" data-testid="select-booking-location"><option value="">All facilities</option>${listOf(locations).map(l=>`<option value="${esc(l.id)}" ${locationId===String(l.id)?'selected':''}>${esc(l.name)}${l.active?'':' (inactive)'}</option>`).join('')}</select><button class="btn btn-primary" type="submit">Search</button></form>${table(['Reference / customer','Facility / slot','Vehicle','Visit','Status','Entry / exit'],items.map(b=>[`${esc(b.reference)}<br><small>${esc(b.customerName||'—')}</small>`,`${esc(b.locationName||'—')}<br>${esc(b.slotCode||'—')}`,esc(b.vehicleRegistration||'—'),`${date(b.startAt)}<br>${date(b.endAt)}`,status(b.status),`<div class="row-actions">${b.status==='UPCOMING'?`<button class="btn btn-primary btn-sm" data-action="entry" data-id="${esc(b.id)}">Record entry</button>`:''}${b.status==='ACTIVE'?`<button class="btn btn-secondary btn-sm" data-action="exit" data-id="${esc(b.id)}">Record exit</button>`:''}</div>`]))}`}
async function adminPayments(){const st=new URLSearchParams(location.hash.split('?')[1]||'').get('status')||'';const items=listOf(await api.adminPayments({status:st}));return `${heading('Payment records','Inspect customer payment records; simulated activity stays labelled.') }<div class="alert"><strong>Demo payment disclaimer:</strong> simulated payment entries are not real charges or revenue.</div><form class="toolbar" data-form="admin-payment-filter"><select name="status"><option value="">All payment statuses</option><option value="DEMO_PAID" ${st==='DEMO_PAID'?'selected':''}>DEMO_PAID</option></select><button class="btn btn-primary" type="submit">Apply filter</button></form>${table(['Reference','Booking','Amount','Method','Status','Demo','Date'],items.map(p=>[esc(p.reference),esc(p.bookingReference),money(p.amount),esc(p.method),status(p.status),p.demo?'<span class="badge demo">DEMO</span>':'No',date(p.createdAt)]))}`}
function reportCaption(key){
  const selectedDates=new Set(['bookings','activeBookings','upcomingBookings','completedBookings','cancelledBookings']);
  return selectedDates.has(key)?'Selected dates':'All time';
}
function reportBreakdown(title,rows,label){
  return `<section class="panel"><div class="panel-title"><h2>${esc(title)}</h2><span class="badge">SELECTED DATES</span></div>${rows.length?`<div class="table-wrap"><table><thead><tr><th>${esc(label)}</th><th>Bookings</th></tr></thead><tbody>${rows.map(([name,count])=>`<tr><td>${esc(name)}</td><td>${esc(count)}</td></tr>`).join('')}</tbody></table></div>`:empty('No records found','There is nothing to show for these filters.')}</section>`;
}
async function adminReports(){const p=new URLSearchParams(location.hash.split('?')[1]||'');const from=p.get('from')||'',to=p.get('to')||'';const d=await api.reports({from,to});const counts=Object.entries(d.counts||{}).map(([k,v])=>[k.replaceAll(/([A-Z])/g,' $1').toUpperCase(),v,reportCaption(k)]);return `${heading('Reports','Booking activity for the selected dates, plus current account and facility totals.') }<form class="toolbar" data-form="report-filter"><label class="field" style="margin:0">From<input type="date" name="from" value="${esc(from)}"></label><label class="field" style="margin:0">To<input type="date" name="to" value="${esc(to)}"></label><button class="btn btn-primary" type="submit">Update report</button></form><div class="alert">Booking counts and the demo payment total follow the selected dates. Customer, vehicle, facility, and slot totals are current all-time figures. Demo payments are simulated records, not real revenue.</div>${statsPanel(counts.concat([['DEMO PAYMENTS TOTAL',money(d.demoPaymentsTotal),'Selected dates · simulated only']]))}<div class="grid">${reportBreakdown('Bookings by vehicle',Object.entries(d.vehicleCategoryCounts||{}),'Vehicle type')}${reportBreakdown('Bookings by facility',Object.entries(d.bookingsByFacility||{}),'Facility')}${reportBreakdown('Peak arrival hours',Object.entries(d.peakArrivalHours||{}),'Hour')}</div>`}
async function adminSupport(){const items=listOf(await api.adminSupport());return `${heading('Support queue','Reply to parking questions and update ticket status.')}${items.length?`<div class="grid">${items.map(t=>`<article class="panel" data-testid="card-admin-ticket-${esc(t.id)}"><div class="panel-title"><div><div class="eyebrow">${esc(t.customerName||'Customer')} · ${esc(t.category)}</div><h3 style="margin:7px 0 0">${esc(t.subject)}</h3></div>${status(t.status)}</div><p class="muted">${esc(t.description)}</p>${ticketThread(t)}<button class="btn btn-secondary btn-sm" data-action="ticket-reply" data-id="${esc(t.id)}" data-response="${esc(t.response||'')}" data-status="${esc(t.status||'OPEN')}">Reply / update status</button></article>`).join('')}</div>`:empty('Queue is clear','New customer support requests will appear here.')}`}

function modal(title,body){document.querySelector('.modal-backdrop')?.remove();const wrap=document.createElement('div');wrap.className='modal-backdrop';wrap.innerHTML=`<section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div class="modal-head"><h2 id="modal-title">${title}</h2><button class="close" data-action="close-modal" aria-label="Close">×</button></div>${body}</section>`;document.body.append(wrap);}
function closeModal(){document.querySelector('.modal-backdrop')?.remove();}
function vehicleForm(v={}){modal(v.id?'Edit vehicle':'Add a vehicle',`<form data-form="vehicle" data-id="${esc(v.id||'')}">${input('Registration plate','registration','text',v.registration||'','e.g. MH 01 AB 1234')}${select('Vehicle type','type',vehicleOptions.slice(1),v.type||'CAR')}<div class="two">${input('Make','brand','text',v.brand||'')}${input('Model','model','text',v.model||'')}</div>${input('Color (optional)','color','text',v.color||'', 'e.g. Midnight blue',false)}<div class="modal-actions"><button type="button" class="btn btn-quiet" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Save vehicle</button></div></form>`);}
function locationForm(l={}){modal(l.id?'Edit facility':'Add facility',`<form data-form="location" data-id="${esc(l.id||'')}">${input('Facility name','name','text',l.name||'')}${input('Street address','address','text',l.address||'')}${input('Area','area','text',l.area||'')}${select('Category','category',[['MALL','Mall'],['PUBLIC','Public'],['STANDALONE','Standalone'],['OTHER','Other']],l.category||'PUBLIC')}${input('Operating hours','operatingHours','text',l.operatingHours||'')}${input('Hourly rate (₹)','hourlyRate','number',l.hourlyRate||'','',true)}<div class="field"><label for="description">Description</label><textarea name="description" id="description" data-testid="input-description">${esc(l.description||'')}</textarea></div><div class="field"><label>Supported vehicle types</label><div class="row-actions">${vehicleOptions.slice(1).map(([v,label])=>`<label class="badge"><input type="checkbox" name="supportedVehicleTypes" value="${v}" ${(l.supportedVehicleTypes||[]).includes(v)?'checked':''}> ${label}</label>`).join('')}</div></div>${input('Map URL (optional)','mapUrl','url',l.mapUrl||'', 'https://…',false)}<div class="modal-actions"><button type="button" class="btn btn-quiet" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Save facility</button></div></form>`);}
async function slotsModal(locationId){
 try{
  const slots=listOf(await api.adminSlots(locationId));
  const selectMarkup=slot=>`<select data-slot-status="${esc(slot.id)}">${['AVAILABLE','OCCUPIED','RESERVED','DISABLED'].map(s=>`<option ${s===slot.status?'selected':''}>${s}</option>`).join('')}</select>`;
  modal('Manage facility slots',`${slots.length?`<div class="table-wrap"><table><thead><tr><th>Slot</th><th>Status</th><th>Vehicle types</th><th>Rate override</th><th></th></tr></thead><tbody>${slots.map(s=>`<tr><td>${esc(s.code)}</td><td>${selectMarkup(s)}</td><td>${esc((s.vehicleTypes||[]).join(', '))}</td><td>${s.hourlyRate?money(s.hourlyRate):'Facility rate'}</td><td><button class="btn btn-secondary btn-sm" data-action="save-slot" data-id="${esc(s.id)}" data-location="${esc(locationId)}">Save</button></td></tr>`).join('')}</tbody></table></div>`:empty('No slots listed','Create a facility slot to begin managing capacity.')}<form data-form="slot-add" data-id="${esc(locationId)}" style="margin-top:20px"><div class="two">${input('Slot code','code')}${select('Initial status','status',[['AVAILABLE','Available'],['OCCUPIED','Occupied'],['RESERVED','Reserved'],['DISABLED','Disabled']],'AVAILABLE')}</div><div class="field"><label>Vehicle types</label><div class="row-actions">${vehicleOptions.slice(1).map(([v,label])=>`<label class="badge"><input type="checkbox" name="vehicleTypes" value="${v}" checked> ${label}</label>`).join('')}</div></div>${input('Hourly rate override (optional)','hourlyRate','number','', '',false)}<div class="modal-actions"><button type="button" class="btn btn-quiet" data-action="close-modal">Done</button><button class="btn btn-primary" type="submit">Add slot</button></div></form>`);
 }catch(error){modal('Manage facility slots',failed(error,'retry'));}
}
function ticketForm(){modal('New support request',`<form data-form="ticket">${select('Request category','category',[['BOOKING','Booking'],['FACILITY','Facility'],['PAYMENT','Payment'],['ACCOUNT','Account'],['OTHER','Other']])}${input('Subject','subject') }<div class="field"><label for="description">Tell us what happened</label><textarea id="description" name="description" minlength="10" required data-testid="input-description"></textarea></div><div class="modal-actions"><button type="button" class="btn btn-quiet" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Send request</button></div></form>`);}
function replyForm(button){modal('Reply to support request',`<form data-form="reply" data-id="${esc(button.dataset.id)}"><div class="field"><label for="response">Your response</label><textarea id="response" name="response" required data-testid="input-response">${esc(button.dataset.response||'')}</textarea></div>${select('Ticket status','status',[['OPEN','Open'],['IN_PROGRESS','In progress'],['RESOLVED','Resolved'],['CLOSED','Closed']],button.dataset.status||'OPEN')}<div class="modal-actions"><button type="button" class="btn btn-quiet" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Send reply</button></div></form>`);}
async function refresh(){closeModal();await loadPage();}
function formData(form){return new FormData(form);}
function vals(form){return Object.fromEntries(formData(form).entries());}
async function submitForm(form){
 const kind=form.dataset.form, v=vals(form); const id=form.dataset.id;
 try{
  if(kind==='login'||kind==='register'){
   if(kind==='register'&&v.password!==v.confirmPassword)throw new Error('Your passwords do not match.');
   if(kind==='register'&&!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{10,72}$/.test(v.password))throw new Error('Use 10–72 characters with upper and lower case letters, a number, and a symbol.');
   const auth=kind==='login'?await api.login({email:v.email,password:v.password}):await api.register({name:v.name,email:v.email,phone:v.phone,password:v.password,termsAccepted:formData(form).get('termsAccepted')==='on'});
   session.set(auth.token);user=auth.user;notify(kind==='register'?'Your PARKSYNC account is ready.':'Signed in. Your parking plan is waiting.');setRoute(user.role==='ADMIN'?'admin':'dashboard');return;
  }
  if(kind==='search'){const q=new URLSearchParams();Object.entries(v).forEach(([k,x])=>x&&q.set(k,x));if(formData(form).has('availableOnly'))q.set('availableOnly','true');location.hash=`#/search${q.size?'?'+q.toString():''}`;return;}
  if(kind==='quote'){
   const startAt=new Date(v.startAt).toISOString(),endAt=new Date(v.endAt).toISOString();
   if(new Date(endAt)<=new Date(startAt))throw new Error('Departure must be after arrival.');
   quoteParams={vehicleId:v.vehicleId,startAt,endAt,startLocal:v.startAt,endLocal:v.endAt};
   quoteCache=await api.quote({locationId:Number(currentLocation.id),vehicleId:Number(v.vehicleId),startAt,endAt});
   if(selectedSlot&&!quoteCache.slots?.some(s=>String(s.id)===String(selectedSlot.id)))selectedSlot=null;
   await loadPage();return;
  }
  if(kind==='vehicle'){
   const body={registration:v.registration,type:v.type,brand:v.brand,model:v.model,color:v.color||null};
   if(id)await api.updateVehicle(id,body);else await api.createVehicle(body);
   notify(id?'Vehicle updated.':'Vehicle added.');await refresh();return;
  }
  if(kind==='profile'){const updated=await api.profile({name:v.name,phone:v.phone});user={...user,...updated};notify('Profile updated.');await refresh();return;}
  if(kind==='password'){if(v.newPassword!==v.confirmPassword)throw new Error('The new passwords do not match.');if(!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{10,72}$/.test(v.newPassword))throw new Error('Use 10–72 characters with upper and lower case letters, a number, and a symbol.');await api.password({currentPassword:v.currentPassword,newPassword:v.newPassword});session.clear();user=null;authPage('login','Password updated. Please sign in again.');return;}
  if(kind==='ticket'){await api.createTicket({category:v.category,subject:v.subject,description:v.description});notify('Support request sent.');await refresh();return;}
  if(kind==='location'){
   const checks=[...form.querySelectorAll('[name="supportedVehicleTypes"]:checked')].map(x=>x.value);
   const mapUrl=(v.mapUrl||'').trim();
   if(mapUrl&&!/^https?:\/\//i.test(mapUrl))throw new Error('Map URL must start with http:// or https://.');
   const body={name:v.name,address:v.address,area:v.area,category:v.category,description:v.description,operatingHours:v.operatingHours,supportedVehicleTypes:checks,hourlyRate:Number(v.hourlyRate),mapUrl:mapUrl||null};
   if(id)await api.updateLocation(id,body);else await api.createLocation(body);
   notify(id?'Facility updated.':'Facility created.');await refresh();return;
  }
  if(kind==='slot-add'){
   const types=[...form.querySelectorAll('[name="vehicleTypes"]:checked')].map(x=>x.value);
   await api.createSlot(id,{code:v.code,status:v.status,vehicleTypes:types,hourlyRate:v.hourlyRate?Number(v.hourlyRate):null});notify('Slot created.');await slotsModal(id);return;
  }
  if(kind==='reply'){await api.reply(id,{response:v.response,status:v.status});notify('Support reply sent.');await refresh();return;}
  if(kind==='admin-user-search'){location.hash=`#/admin-users${v.q?`?q=${encodeURIComponent(v.q)}`:''}`;return;}
  if(kind==='admin-booking-search'){const q=new URLSearchParams();if(v.q)q.set('q',v.q);if(v.status)q.set('status',v.status);if(v.locationId)q.set('locationId',v.locationId);location.hash=`#/admin-bookings${q.size?'?'+q.toString():''}`;return;}
  if(kind==='admin-payment-filter'){location.hash=`#/admin-payments${v.status?`?status=${encodeURIComponent(v.status)}`:''}`;return;}
  if(kind==='report-filter'){const q=new URLSearchParams();if(v.from)q.set('from',v.from);if(v.to)q.set('to',v.to);location.hash=`#/admin-reports${q.size?'?'+q.toString():''}`;return;}
 }catch(error){notify(error.message,true);}
}
function showSelectedSlot(){
  document.querySelectorAll('.slot[data-action="choose-slot"]').forEach(slot=>{
    const selected=selectedSlot&&String(slot.dataset.id)===String(selectedSlot.id);
    slot.classList.toggle('selected',selected);
    slot.setAttribute('aria-pressed',selected?'true':'false');
  });
  const amount=quotedAmount();
  const summary=document.querySelector('[data-testid="quote-summary"]');
  if(summary&&amount){
    summary.innerHTML=`<strong>Estimate: ${money(amount.fee)}</strong><div>${amount.hours} billable hour${amount.hours===1?'':'s'} · ${esc(quoteCache.currency||'INR')}</div>${quoteCache.pricingRule?`<small>${esc(quoteCache.pricingRule)}</small>`:''}`;
  }
  let confirmBtn=document.querySelector('[data-action="confirm-booking"]');
  if(selectedSlot&&quoteCache){
    if(!confirmBtn){
      const kv=document.createElement('div');
      kv.className='kv';
      kv.innerHTML=`<span>Selected slot</span><strong></strong>`;
      confirmBtn=document.createElement('button');
      confirmBtn.className='btn btn-primary btn-block';
      confirmBtn.dataset.action='confirm-booking';
      confirmBtn.setAttribute('data-testid','button-confirm-booking');
      confirmBtn.textContent='Confirm reservation';
      summary?.insertAdjacentElement('afterend',kv);
      kv.insertAdjacentElement('afterend',confirmBtn);
    }
    const label=confirmBtn.previousElementSibling?.querySelector('strong');
    if(label)label.textContent=selectedSlot.code;
  }else if(confirmBtn){
    if(confirmBtn.previousElementSibling?.classList.contains('kv'))confirmBtn.previousElementSibling.remove();
    confirmBtn.remove();
  }
}
async function action(button){
 const a=button.dataset.action,id=button.dataset.id;
 try{
  if(a==='auth-switch'){authPage(button.dataset.mode);return;}
  if(a==='go-search'){setRoute('search');return;} if(a==='go-dashboard'){setRoute('dashboard');return;} if(a==='go-vehicles'){setRoute('vehicles');return;}
  if(a==='logout'){if(confirm('Sign out of PARKSYNC?')){try{await api.logout();}catch{}session.clear();user=null;setRoute('login');authPage();}return;}
  if(a==='menu'){document.querySelector('#sidebar')?.classList.toggle('open');return;}
  if(a==='retry'){loadPage();return;} if(a==='close-modal'){closeModal();return;}
  if(a==='vehicle-add'){vehicleForm();return;}
  if(a==='vehicle-edit'){const vehicles=listOf(await api.vehicles());vehicleForm(vehicles.find(v=>String(v.id)===String(id))||{});return;}
  if(a==='vehicle-delete'){if(confirm('Delete this vehicle? You cannot undo this action.')){await api.deleteVehicle(id);notify('Vehicle deleted.');await refresh();}return;}
  if(a==='choose-slot'){
   const slot=slotsCache.find(s=>String(s.id)===String(id))||null;
   if(!slot||slot.status==='DISABLED')return;
   if(Array.isArray(quoteCache?.slots)&&!quoteCache.slots.some(s=>String(s.id)===String(slot.id)))return;
   selectedSlot=slot;showSelectedSlot();return;
  }
  if(a==='confirm-booking'){
   if(!selectedSlot||!quoteCache||!quoteCache.slots?.some(s=>String(s.id)===String(selectedSlot.id)))throw new Error('Choose an available slot for this visit before confirming.');
   const booking=await api.createBooking({locationId:Number(currentLocation.id),vehicleId:Number(quoteParams.vehicleId),startAt:quoteParams.startAt,endAt:quoteParams.endAt,slotId:Number(selectedSlot.id)});
   notify(`Reservation confirmed · ${booking.reference}`);setRoute('bookings');return;
  }
  if(a==='cancel-booking'){if(confirm('Cancel this reservation? This action cannot be undone.')){await api.cancelBooking(id);notify('Reservation cancelled.');await refresh();}return;}
  if(a==='pay-booking'){
   const booking=await api.booking(id);
   if(confirm(`Record a simulated payment for ${booking.reference}? This does not charge money.`)){const method=prompt('Choose DEMO_CASH, DEMO_CARD, or DEMO_UPI','DEMO_UPI');if(method&&['DEMO_CASH','DEMO_CARD','DEMO_UPI'].includes(method)){await api.demoPayment({bookingId:Number(id),method});notify('Demo payment recorded — no money was charged.');await refresh();}else if(method)notify('Choose DEMO_CASH, DEMO_CARD, or DEMO_UPI.',true);}return;
  }
  if(a==='read-notification'){await api.readNotification(id);notify('Notification marked as read.');await refresh();return;}
  if(a==='ticket-add'){ticketForm();return;}
  if(a==='location-add'){locationForm();return;}
  if(a==='location-edit'){const l=await api.location(id);locationForm(l);return;}
  if(a==='toggle-location'){
   const active=button.dataset.active==='true';
   if(!active&&!confirm('Deactivate this facility? Its booking history will remain.'))return;
   await api.setLocationActive(id,active);notify(active?'Facility reactivated.':'Facility deactivated.');await refresh();return;
  }
  if(a==='slots-manage'){await slotsModal(id);return;}
  if(a==='save-slot'){
   const tr=button.closest('tr'),statusValue=tr.querySelector(`[data-slot-status="${CSS.escape(id)}"]`).value;
    const list=listOf(await api.adminSlots(button.dataset.location)),slot=list.find(s=>String(s.id)===String(id));
   await api.updateSlot(id,{code:slot.code,status:statusValue,vehicleTypes:slot.vehicleTypes||[],hourlyRate:slot.hourlyRate??null});notify('Slot updated.');await slotsModal(button.dataset.location);return;
  }
  if(a==='toggle-user'){const active=button.dataset.active==='true';if(!active&&!confirm('Deactivate this account? The user will lose access.'))return;await api.setUserActive(id,active);notify(`Account ${active?'activated':'deactivated'}.`);await refresh();return;}
  if(a==='entry'||a==='exit'){if(confirm(`Record ${a==='entry'?'vehicle entry':'vehicle exit'} for this booking?`)){if(a==='entry')await api.entry(id);else await api.exit(id);notify(a==='entry'?'Entry recorded.':'Exit recorded.');await refresh();}return;}
  if(a==='ticket-reply'){replyForm(button);return;}
 }catch(error){notify(error.message,true);}
}
document.addEventListener('submit',event=>{const form=event.target.closest('form[data-form]');if(!form)return;event.preventDefault();submitForm(form);});
document.addEventListener('click',event=>{
 const button=event.target.closest('[data-action]');if(button){event.preventDefault();action(button);return;}
 if(event.target.classList.contains('modal-backdrop'))closeModal();
 if(event.target.closest('.nav-link')&&innerWidth<=700)document.querySelector('#sidebar')?.classList.remove('open');
});
window.addEventListener('hashchange',()=>{loadPage();});
window.addEventListener('parksync:unauthorized',()=>{user=null;authPage('login','Your session expired. Please sign in again.');});
if(!location.hash)location.hash='#/dashboard';
loadPage();