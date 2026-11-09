(()=>{'use strict';const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)],C=window.HTE_SUPABASE_CONFIG||{};
document.querySelectorAll('form').forEach(form=>{if(!form.dataset.startedAt)form.dataset.startedAt=String(Date.now())});const db=window.supabase&&C.url&&C.publishableKey?window.supabase.createClient(C.url,C.publishableKey,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true,flowType:'pkce'}}):null;if(db)window.HTE_SUPABASE_CLIENT=db;let session=null,profile=null,avatarDisplay='',tracks=[],artists=[],releases=[],founderAccess=false,authRevision=0;const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),admin=u=>!!u&&!!session&&u.id===session.user.id&&founderAccess,siteBasePath=()=>location.pathname.startsWith('/High-tech-entertainment-/')?'/High-tech-entertainment-/':'/',notice=(el,s,type='')=>{if(el){el.textContent=s;el.className='status '+type;const isError=type==='error';el.setAttribute('role',isError?'alert':'status');el.setAttribute('aria-live',isError?'assertive':'polite');el.setAttribute('aria-atomic','true')}},toast=s=>{let e=$('#toast');if(e){e.textContent=s;e.classList.add('show');setTimeout(()=>e.classList.remove('show'),2500)}},initials=s=>String(s||'HT').trim().split(/\s+/).slice(0,2).map(x=>x[0]||'').join('').toUpperCase()||'HT',image=(u,n)=>u?'<img src="'+esc(u)+'" alt="">':esc(initials(n)),cover=(u,n)=>{const safe=safeHttpsUrl(u),fallback=siteBasePath()+'images/brand/high-tech-hero.svg',src=safe||fallback,alt=safe?String(n||'Track')+' cover':'High-Tech Entertainment promotional artwork used as the default cover';return '<img src="'+esc(src)+'" alt="'+esc(alt)+'" loading="lazy" onerror="this.onerror=null;this.src=\''+esc(fallback)+'\'">'};function safeHttpsUrl(value){try{const url=new URL(String(value||''));return url.protocol==='https:'&&!url.username&&!url.password?url.href:''}catch{return ''}}
async function rows(t,cols='*',pub=true){let q=db.from(t).select(cols);if(pub)q=q.eq('is_published',true);let {data,error}=await q.order('created_at',{ascending:false}).limit(100);if(error)throw error;return data||[]}function spotifyEmbed(raw){
  const value=String(raw||'').trim();
  let type='',id='';
  if(value.startsWith('spotify:')){
    const parts=value.split(':');if(parts.length===3){type=parts[1];id=parts[2]}
  }else{
    try{
      const u=new URL(value);const host=u.hostname.toLowerCase();
      if(host!=='open.spotify.com'&&!host.endsWith('.spotify.com'))return null;
      const parts=u.pathname.split('/').filter(Boolean),offset=parts[0]==='embed'?1:0;
      type=parts[offset]||'';id=parts[offset+1]||'';
    }catch{return null}
  }
  if(!['track','album','playlist','episode','show'].includes(type)||! /^[A-Za-z0-9]+$/.test(id))return null;
  return 'https://open.spotify.com/embed/'+type+'/'+id+'?utm_source=generator&theme=0';
}
function spotifyLink(raw){
  const embed=spotifyEmbed(raw);
  if(embed)return embed.replace('/embed/','/').split('?')[0];
  const value=String(raw||'').trim();
  return /^https:\/\//i.test(value)?value:'#';
}
function trackPlayer(x){
  const raw=String(x.audio_url||'').trim(),embed=spotifyEmbed(raw),safe=safeHttpsUrl(raw);
  if(embed)return '<iframe title="Listen to '+esc(x.title)+' on Spotify" src="'+esc(embed)+'" width="100%" height="152" style="border:0" loading="lazy" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"></iframe><a class="btn gold" style="width:100%;margin-top:8px" target="_blank" rel="noopener noreferrer" href="'+esc(spotifyLink(raw))+'">Open in Spotify ↗</a>';
  if(/spotify\.link|spotify\.com/i.test(raw))return '<a class="btn gold" style="width:100%;margin-top:8px" target="_blank" rel="noopener noreferrer" href="'+esc(spotifyLink(raw))+'">Listen on Spotify ↗</a>';
  if(safe){
    const url=new URL(safe),path=url.pathname.toLowerCase(),storage=path.includes('/storage/v1/object/'),direct=storage||/\.(mp3|mp4|m4a|wav|aac|ogg|flac|opus|webm)$/i.test(path);
    if(direct){
      const download=new URL(safe);
      if(storage)download.searchParams.set('download',(x.title||'high-tech-track').replace(/[^a-zA-Z0-9 _-]/g,''));
      return '<audio controls preload="none" src="'+esc(safe)+'" aria-label="Play '+esc(x.title)+'"></audio><a class="btn gold" style="width:100%;margin-top:8px" href="'+esc(download.href)+'" download>↓ Download track</a>';
    }
    return '<a class="btn gold" style="width:100%;margin-top:8px" target="_blank" rel="noopener noreferrer" href="'+esc(safe)+'">Open audio ↗</a>';
  }
  return '<p class="muted small">Audio is being prepared.</p>';
}
function releasePlayer(x){
  const raw=String(x.spotify_url||'').trim();if(!raw)return '';
  const embed=spotifyEmbed(raw);
  if(!embed)return '<a class="btn gold" target="_blank" rel="noopener noreferrer" href="'+esc(spotifyLink(raw))+'">Listen on Spotify ↗</a>';
  const type=embed.includes('/album/')||embed.includes('/playlist/')?352:152;
  return '<iframe title="Listen to '+esc(x.title)+' on Spotify" src="'+esc(embed)+'" width="100%" height="'+type+'" style="border:0" loading="lazy" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"></iframe><a class="btn gold" target="_blank" rel="noopener noreferrer" href="'+esc(spotifyLink(raw))+'">Open in Spotify ↗</a>';
}
function renderPublic(){
  const t=$('#public-tracks');
  if(t)t.innerHTML=tracks.length?tracks.map(x=>'<article class="music-card" data-track-id="'+esc(x.id)+'"><div class="cover">'+cover(x.cover_url,x.title)+'</div><span class="pill">'+esc(x.genre||'Music')+'</span><h3>'+esc(x.title)+'</h3><div class="muted small">'+esc(x.artist||'High Tech Entertainment')+'</div>'+trackPlayer(x)+'</article>').join(''):'<div class="empty">New music is on the way. Check back soon.</div>';
  const a=$('#public-artists');
  if(a)a.innerHTML=artists.length?artists.map(x=>'<article class="artist-card" style="padding:14px"><div class="cover">'+cover(x.image_url,x.name)+'</div><h3>'+esc(x.name)+'</h3><p class="muted small">'+esc(x.bio||'High Tech Entertainment artist')+'</p></article>').join(''):'<div class="empty">Artist profiles will appear here soon.</div>';
  const r=$('#public-releases');
  if(r)r.innerHTML=releases.length?releases.map(x=>'<article class="release-card" style="padding:14px"><div class="cover">'+cover(x.cover_url,x.title)+'</div><h3>'+esc(x.title)+'</h3><p class="muted small">'+esc(x.release_date||'Official release')+'</p>'+releasePlayer(x)+'</article>').join(''):'<div class="empty">Official releases will appear here soon.</div>';
}function showCatalogError(message){['#public-tracks','#public-artists','#public-releases'].forEach(selector=>{const el=$(selector);if(el)el.innerHTML='<div class="empty" role="alert">'+esc(message)+' <button class="btn" type="button" data-retry-catalog>Retry catalogue</button></div>'})}async function loadCatalog(){if(!db){showCatalogError('The music catalogue is temporarily unavailable. Check your connection and try again.');return}const targets=[['tracks','tracks','id,title,artist,uploader,audio_url,cover_url,genre,duration_seconds,created_at'],['artists','artists','id,name,slug,bio,image_url,created_at'],['releases','releases','id,title,slug,cover_url,release_date,spotify_url,created_at']];const results=await Promise.allSettled(targets.map(([table])=>rows(table,targets.find(x=>x[0]===table)[2])));const values={tracks,artists,releases};let failed=[];results.forEach((result,index)=>{const [table]=targets[index];if(result.status==='fulfilled')values[table]=result.value;else{failed.push(table);console.error('HTE public '+table+' query failed',result.reason)}});tracks=values.tracks;artists=values.artists;releases=values.releases;renderPublic();failed.forEach(table=>{const el=$('#public-'+table);if(el)el.innerHTML='<div class="empty" role="alert">'+(table==='releases'?'Official releases could not be loaded.':'This section could not be loaded.')+' <button class="btn" type="button" data-retry-catalog>Retry</button></div>'})}async function loadAccountDownloads(){const el=$('#account-downloads');if(!el||!db)return;el.innerHTML='<div class="empty">Loading available downloads…</div>';try{const {data,error}=await db.from('tracks').select('id,title,artist,genre,audio_url,cover_url').eq('is_published',true).order('created_at',{ascending:false}).limit(100);if(error)throw error;const items=data||[];el.innerHTML=items.length?'<div class="cards">'+items.map(x=>{const raw=String(x.audio_url||'').trim(),spotify=spotifyEmbed(raw),direct=/^https:\/\//i.test(raw)&&!spotify&&!/spotify\.link|spotify\.com/i.test(raw);const href=direct?raw+(raw.includes('?')?'&':'?')+'download=1':spotify?spotifyLink(raw):'#';const action=direct?'<a class="btn gold" href="'+esc(href)+'" download>Download audio ↓</a>':spotify?'<a class="btn gold" href="'+esc(href)+'" target="_blank" rel="noopener noreferrer">Listen on Spotify ↗</a>':'<span class="muted small">Audio not available yet</span>';return '<article class="music-card"><div class="cover">'+cover(x.cover_url,x.title)+'</div><h3>'+esc(x.title)+'</h3><p class="muted small">'+esc(x.artist||'High Tech Entertainment')+' · '+esc(x.genre||'Music')+'</p>'+action+'</article>'}).join('')+'</div>':'<div class="empty">No published music is available yet. Check back soon.</div>'}catch(error){console.error('HTE account downloads',error);el.innerHTML='<div class="empty" role="alert">Downloads could not be loaded. Please refresh and try again.</div>'}}async function profileLoad(){const u=session?.user;if(!u)throw Error('No authenticated user is available.');const read=()=>db.from('profiles').select('*').eq('id',u.id).maybeSingle();let result=await read();if(result.error)throw result.error;if(result.data){profile=result.data;return profile}const seed={id:u.id,display_name:String(u.user_metadata?.full_name||u.user_metadata?.name||u.user_metadata?.preferred_username||'').trim(),bio:'',phone:'',avatar_url:''};const created=await db.from('profiles').insert(seed).select('*').single();if(!created.error&&created.data){profile=created.data;return profile}if(created.error?.code==='23505'){result=await read();if(result.error)throw result.error;if(result.data){profile=result.data;return profile}}const reason=created.error?.message||'Supabase did not return the newly created profile row.';throw Error('Your sign-in succeeded, but your member profile is missing. Profile creation was rejected: '+reason+'. Check the authenticated own-profile INSERT policy and Data API permissions.');}async function resolveAvatar(){avatarDisplay='';if(profile?.avatar_url){if(/^https:\/\//i.test(profile.avatar_url))avatarDisplay=profile.avatar_url;else{let r=await db.storage.from('hte-avatars').createSignedUrl(profile.avatar_url,3600);if(!r.error)avatarDisplay=r.data.signedUrl}}}function updateUserUI(){let name=profile?.display_name||session?.user?.user_metadata?.full_name||session?.user?.email||'My account';$$('[data-user-name]').forEach(e=>e.textContent=name);$$('[data-user-avatar]').forEach(e=>e.innerHTML=image(avatarDisplay,name));$$('[data-user-email]').forEach(e=>e.textContent=session?.user?.email||'')}async function refreshAuth(nextSession,fromAuthEvent=false){
 const revision=++authRevision;
 if(!db){
  founderAccess=false;
  if($('#account-login'))$('#account-login').hidden=false;
  if($('#account-content'))$('#account-content').hidden=true;
  notice($('[data-auth-status]'),'Supabase could not initialise in this browser. Check your connection, then reload the page.','error');
  return;
 }
 if(fromAuthEvent){
  session=nextSession||null;
 }else{
  const {data,error}=await db.auth.getSession();
  if(revision!==authRevision)return;
  if(error)console.error('HTE session restore failed',error);
  session=data?.session||null;
 }
 const u=session?.user;
 founderAccess=false;
 let founderCheckFailed=false;
 if(u){
  try{
   const {data:roleAllowed,error:roleError}=await db.rpc('hte_is_admin');
   if(roleError)throw roleError;
   founderAccess=roleAllowed===true;
  }catch(roleError){
   founderCheckFailed=true;
   console.error('HTE founder role verification failed',roleError);
  }
 }else{
  profile=null;
  avatarDisplay='';
 }
 if(revision!==authRevision)return;
 const isAdmin=admin(u);
 $$('[data-signed-in]').forEach(e=>e.hidden=!u);
 $$('[data-signed-out]').forEach(e=>e.hidden=!!u);
 $$('[data-admin-only]').forEach(e=>e.hidden=!isAdmin);
 if(u){
  try{
   await profileLoad();
   if(revision!==authRevision)return;
   await resolveAvatar();
   if(revision!==authRevision)return;
   updateUserUI();
   if(location.pathname.includes('/account/'))await loadAccountDownloads();
   if(revision!==authRevision)return;
  }catch(e){
   if(revision!==authRevision)return;
   console.error('HTE profile load failed',e);
   profile={id:u.id,display_name:u.user_metadata?.full_name||u.user_metadata?.name||'',bio:'',phone:'',avatar_url:''};
   updateUserUI();
   const message=e.message||'Supabase profile access failed';
   notice($('#profile-status')||$('[data-auth-status]'),'You are signed in, but your profile could not be loaded yet. '+message,'error');
  }
 }
 if(u&&!location.pathname.includes('/admin/')&&!location.pathname.includes('/account/')){
  const next=new URLSearchParams(location.search).get('next');
  if(next==='admin'||next==='account'){
   const base=siteBasePath();
   location.replace(location.origin+base+next+'/');
   return;
  }
 }
 if(location.pathname.includes('/admin/')){
  if(!isAdmin){
   $('#admin-login').hidden=false;
   $('#admin-app').hidden=true;
   if(u)notice($('#login-status'),founderCheckFailed?'Your session is active, but Founder Studio permissions could not be verified. Reload and try again.':'This account is not authorised for founder administration.','error');
  }else{
   $('#admin-login').hidden=true;
   $('#admin-app').hidden=false;
   await adminRefresh();
  }
 }
 if(location.pathname.includes('/account/')){
  if($('#account-login'))$('#account-login').hidden=!!u;
  if($('#account-content'))$('#account-content').hidden=!u;
  if(u){
   $('#profile-email')&&($('#profile-email').textContent=u.email||'');
   const bookingForm=$('#booking-form');
   if(bookingForm){
    bookingForm.elements.name.value=profile?.display_name||u.user_metadata?.full_name||u.user_metadata?.name||'';
    bookingForm.elements.email.value=u.email||'';
   }
   let f=$('#profile-form');
   if(f&&profile){
    ['display_name','bio','phone'].forEach(k=>{if(f.elements[k])f.elements[k].value=profile[k]||''});
    $('#profile-avatar')&&($('#profile-avatar').innerHTML=image(avatarDisplay,profile.display_name));
   }
  }
  if(founderCheckFailed)notice($('[data-auth-status]'),'Your account is signed in, but Founder Studio permissions could not be verified. Please reload and try again.','error');
 }
}function tab(t){$$('[data-tab]').forEach(e=>e.classList.toggle('active',e.dataset.tab===t));$$('[data-view]').forEach(e=>e.hidden=e.dataset.view!==t);let h=$('#view-title');if(h)h.textContent=({overview:'Overview',videos:'Video manager',tracks:'Music manager',artists:'Artists',releases:'Releases',inbox:'Contact inbox',marketing:'AI Marketing Agent',settings:'Settings'})[t]||'Dashboard'}async function adminRefresh(){if(!admin(session?.user))return;try{[tracks,artists,releases]=await Promise.all([rows('tracks','id,title,artist,audio_url,is_published,created_at',false),rows('artists','id,name,bio,is_published,created_at',false),rows('releases','id,title,release_date,is_published,created_at',false)]);populateTrackReleaseOptions();$('#stat-tracks')&&($('#stat-tracks').textContent=tracks.length);$('#stat-artists')&&($('#stat-artists').textContent=artists.length);$('#stat-releases')&&($('#stat-releases').textContent=releases.length);tableRender();await inboxLoad()}catch(e){console.error(e);notice($('#admin-status'),'Could not load admin data. Verify tables and founder policies.','error')}}function populateTrackReleaseOptions(){
  const select=$('#track-form select[name="release_id"]');
  if(!select)return;
  const current=select.value;
  select.innerHTML='<option value="">Choose a release</option>'+releases.map(r=>'<option value="'+esc(r.id)+'">'+esc(r.title)+(r.is_published?' · Published':' · Draft')+'</option>').join('');
  if([...select.options].some(option=>option.value===current))select.value=current;
}
function tableRender(){let t=$('#admin-tracks');if(t)t.innerHTML=tracks.length?tracks.map(x=>'<tr><td>'+esc(x.title)+'</td><td>'+esc(x.artist||'—')+'</td><td>'+(x.is_published?'Published':'Draft')+'</td><td><div class="table-actions"><button class="btn" data-toggle-track="'+esc(x.id)+'" data-published="'+(!x.is_published)+'">'+(x.is_published?'Unpublish':'Publish')+'</button><button class="btn danger" data-delete-track="'+esc(x.id)+'">Delete</button></div></td></tr>').join(''):'<tr><td colspan="4">No tracks yet.</td></tr>';let a=$('#admin-artists');if(a)a.innerHTML=artists.length?artists.map(x=>'<tr><td>'+esc(x.name)+'</td><td>'+(x.is_published?'Published':'Draft')+'</td><td><div class="table-actions"><button class="btn" data-toggle-artist="'+esc(x.id)+'" data-published="'+(!x.is_published)+'">'+(x.is_published?'Unpublish':'Publish')+'</button><button class="btn danger" data-delete-artist="'+esc(x.id)+'">Delete</button></div></td></tr>').join(''):'<tr><td colspan="3">No artists added yet.</td></tr>';let r=$('#admin-releases');if(r)r.innerHTML=releases.length?releases.map(x=>'<tr><td>'+esc(x.title)+'</td><td>'+esc(x.release_date||'—')+'</td><td>'+(x.is_published?'Published':'Draft')+'</td><td><div class="table-actions"><button class="btn" data-toggle-release="'+esc(x.id)+'" data-published="'+(!x.is_published)+'">'+(x.is_published?'Unpublish':'Publish')+'</button><button class="btn danger" data-delete-release="'+esc(x.id)+'">Delete</button></div></td></tr>').join(''):'<tr><td colspan="4">No releases added yet.</td></tr>'}async function inboxLoad(){let el=$('#admin-inbox');if(!el)return;let {data,error}=await db.from('contact_messages').select('id,name,email,subject,message,created_at').order('created_at',{ascending:false}).limit(100);if(error){el.innerHTML='<div class="empty">Inbox unavailable. Check founder-only read policy.</div>';return}el.innerHTML=data?.length?data.map(x=>'<article class="panel" style="margin-bottom:12px"><div class="eyebrow">'+esc(new Date(x.created_at).toLocaleString())+'</div><h3>'+esc(x.subject||'Website enquiry')+'</h3><p class="muted small">'+esc(x.name)+' · '+esc(x.email)+'</p><p style="white-space:pre-wrap">'+esc(x.message)+'</p><a class="btn" href="mailto:'+encodeURIComponent(x.email)+'?subject='+encodeURIComponent('Re: '+(x.subject||'Website enquiry'))+'">Reply by email</a></article>').join(''):'<div class="empty">No enquiries yet.</div>'}async function upload(file,bucket,uid){if(!file)throw Error('Choose a file first.');const ext=String(file.name||'').split('.').pop().toLowerCase();const inferred={mp3:'audio/mpeg',m4a:'audio/mp4',mp4:'audio/mp4',aac:'audio/aac',wav:'audio/wav',ogg:'audio/ogg',flac:'audio/flac',webm:'audio/webm',jpg:'image/jpeg',jpeg:'image/jpeg',png:'image/png',webp:'image/webp'}[ext];const contentType=(file.type&&file.type!=='application/octet-stream')?file.type:(inferred||file.type||'application/octet-stream');let {data,error}=await db.storage.from(bucket).upload(uid+'/'+Date.now()+'-'+file.name.replace(/[^a-zA-Z0-9._-]/g,'_'),file,{upsert:false,contentType});if(error)throw error;return data.path}async function signout(){if(!db){location.href='../';return}const {error}=await db.auth.signOut({scope:'local'});if(error)throw error;session=null;profile=null;avatarDisplay='';location.href='../'}async function switchAccount(){if(!db)throw Error('Authentication service is unavailable.');const {error}=await db.auth.signOut({scope:'local'});if(error)throw error;session=null;profile=null;avatarDisplay='';updateUserUI();await refreshAuth();notice($('#login-status'),'You are signed out. Enter the other account email and password to switch accounts.','success')}function verifyPublicForm(form,f){if(String(f.get('website')||'').trim())throw Error('Your message could not be submitted.');const started=Number(form.dataset.startedAt||0);if(started&&Date.now()-started<1200)throw Error('Please wait a moment before submitting.');}async function contact(form){let f=new FormData(form);verifyPublicForm(form,f);let {error}=await db.from('contact_messages').insert({name:String(f.get('name')).trim(),email:String(f.get('email')).trim(),subject:String(f.get('subject')||'Website enquiry').trim(),message:[String(f.get('message')).trim(),f.get('rating')?'Rating: '+String(f.get('rating'))+'/5':''].filter(Boolean).join('\n\n')});if(error)throw error;form.reset()}async function booking(form){
  let f=new FormData(form);verifyPublicForm(form,f);
  let name=String(f.get('name')||'').trim(),email=String(f.get('email')||'').trim(),service=String(f.get('service')||'').trim(),phone=String(f.get('phone')||'').trim(),date=String(f.get('date')||'').trim(),time=String(f.get('time')||'').trim(),details=String(f.get('details')||'').trim();
  if(!service||!details)throw Error('Choose a service and add project details.');
  if(form.dataset.publicBooking==='true'){
    const {data,error}=await db.functions.invoke('public-booking-request',{body:{name,email,phone,service,date,time,details,website:String(f.get('website')||''),started_at:Number(form.dataset.startedAt||0)}});
    if(error){
      let message=error.message||'Your booking request could not be submitted.';
      const context=error.context;
      if(context&&typeof context.clone==='function'){
        const body=await context.clone().json().catch(()=>null);
        if(typeof body?.error==='string'&&body.error.trim())message=body.error;
      }
      throw Error(message);
    }
    if(data?.saved!==true)throw Error(data?.error||'Your booking request could not be saved.');
    form.reset();
    return {saved:true,notified:data.email_sent===true,error:data.error||''};
  }
  let message='Service: '+service+'\nPreferred date: '+(date||'Flexible')+'\nPreferred time: '+(time||'Flexible')+'\nPhone / WhatsApp: '+(phone||'Not provided')+'\n\nProject details:\n'+details;
  let {data,error}=await db.rpc('submit_booking_request',{p_name:name,p_email:email,p_subject:'Booking request — '+service,p_message:message});
  if(error)throw error;
  form.reset();
  let notification;
  try{notification=await db.functions.invoke('booking-notification',{body:{message_id:data}})}catch(error){notification={error}}
  let notificationError=notification.error?.message||notification.data?.error||'';
  const notificationContext=notification.error?.context;
  if(notificationContext&&typeof notificationContext.clone==='function'){
    const body=await notificationContext.clone().json().catch(()=>null);
    if(typeof body?.error==='string'&&body.error.trim())notificationError=body.error;
  }
  const notified=!notificationError&&(notification.data?.email_sent===true||notification.data?.already_sent===true);
  return {saved:true,notified,error:notificationError};
}function playBookingConfirmation(statusElement){
  if(!statusElement)return;
  const audio=new Audio(new URL(siteBasePath()+'assets/booking-confirmation.aac',location.origin).href);
  audio.preload='auto';
  const showRetry=()=>{
    if(statusElement.querySelector('[data-play-booking-sound]'))return;
    const button=document.createElement('button');
    button.type='button';button.className='btn';button.dataset.playBookingSound='';
    button.textContent='Play confirmation sound';
    button.style.marginTop='8px';
    button.addEventListener('click',()=>{
      audio.currentTime=0;
      audio.play().then(()=>button.remove()).catch(()=>{button.textContent='Audio unavailable in this browser';button.disabled=true;});
    });
    statusElement.append(document.createElement('br'),button);
  };
  try{const playback=audio.play();if(playback&&typeof playback.catch==='function')playback.catch(showRetry)}catch{showRetry()}
}
async function saveProfile(f){let d=new FormData(f),row={id:session.user.id,display_name:String(d.get('display_name')||'').trim(),bio:String(d.get('bio')||'').trim(),phone:String(d.get('phone')||'').trim(),avatar_url:profile?.avatar_url||''},photo=d.get('avatar');if(photo?.size){if(!['image/jpeg','image/png','image/webp'].includes(photo.type))throw Error('Use JPG, PNG or WebP.');if(photo.size>5*1024*1024)throw Error('Photo must be 5 MB or less.');row.avatar_url=await upload(photo,'hte-avatars',session.user.id)}let {error}=await db.from('profiles').upsert(row);if(error)throw error;profile=row;await resolveAvatar();updateUserUI();$('#profile-avatar')&&($('#profile-avatar').innerHTML=image(avatarDisplay,row.display_name));toast('Profile saved')}async function mutate(table,id,patch,del=false){if(!admin(session?.user))throw Error('Founder access required.');let q=db.from(table);q=del?q.delete().eq('id',id):q.update(patch).eq('id',id);let {error}=await q;if(error)throw error;await adminRefresh();toast(del?'Item deleted':'Updated')}document.addEventListener('click',e=>{if(e.target.closest('[data-retry-catalog]'))loadCatalog()});document.addEventListener('click',async e=>{let b=e.target.closest('[data-tab]');if(b){tab(b.dataset.tab);return}if(e.target.closest('[data-logout]')){await signout();return}if(e.target.closest('[data-switch-account]')){try{await switchAccount()}catch(err){notice($('#auth-status'),err.message||'Could not switch accounts.','error')}return}let x=e.target.closest('[data-toggle-track],[data-delete-track],[data-toggle-artist],[data-delete-artist],[data-toggle-release],[data-delete-release]');if(x){try{let del=x.hasAttribute('data-delete-track')||x.hasAttribute('data-delete-artist')||x.hasAttribute('data-delete-release');if(!confirm(del?'Delete this item permanently?':'Change this item’s publication status?'))return;if(x.dataset.toggleTrack)await mutate('tracks',x.dataset.toggleTrack,{is_published:x.dataset.published==='true'});if(x.dataset.deleteTrack)await mutate('tracks',x.dataset.deleteTrack,{},true);if(x.dataset.toggleArtist)await mutate('artists',x.dataset.toggleArtist,{is_published:x.dataset.published==='true'});if(x.dataset.deleteArtist)await mutate('artists',x.dataset.deleteArtist,{},true);if(x.dataset.toggleRelease)await mutate('releases',x.dataset.toggleRelease,{is_published:x.dataset.published==='true'});if(x.dataset.deleteRelease)await mutate('releases',x.dataset.deleteRelease,{},true)}catch(err){toast(err.message||'Action failed')}}});document.addEventListener('submit',async e=>{let f=e.target;if(f.matches('#login-form')){e.preventDefault();try{if(!db)throw Error('Authentication service is not configured. Please try again later.');const mode=f.dataset.mode||'signin',email=f.elements.email.value.trim(),password=f.elements.password.value;let result;if(mode==='signup'){if(password.length<8)throw Error('Use a password with at least 8 characters.');if(password!==f.elements.confirm_password.value)throw Error('The passwords do not match.');notice($('#login-status'),'Creating your account…');result=await db.auth.signUp({email,password,options:{data:{full_name:String(f.elements.display_name.value||'').trim()},emailRedirectTo:location.origin+siteBasePath()+'?next=account'}});if(result.error)throw result.error;if(result.data.session){notice($('#login-status'),'Your account is ready. Signing you in…','success');await refreshAuth()}else{notice($('#login-status'),'Account created. Check your email for the confirmation link, then return here to sign in.','success')}}else{notice($('#login-status'),'Signing in…');result=await db.auth.signInWithPassword({email,password});if(result.error)throw result.error;await refreshAuth()}}catch(x){notice($('#login-status'),x.message||'Authentication failed.','error')}}else if(f.matches('#password-update-form')){e.preventDefault();try{if(!db||!session?.user)throw Error('Open the password recovery link again before changing your password.');const password=f.elements.password.value;if(password.length<8)throw Error('Use a password with at least 8 characters.');const {error}=await db.auth.updateUser({password});if(error)throw error;f.reset();notice($('#password-update-status'),'Your password has been updated successfully.','success')}catch(x){notice($('#password-update-status'),x.message||'Password could not be updated.','error')}}else if(f.matches('#contact-form,#feedback-form')){e.preventDefault();const feedback=f.matches('#feedback-form'),status=$(feedback?'#feedback-status':'#contact-status');let b=f.querySelector('[type=submit]');b.disabled=true;notice(status,feedback?'Submitting feedback…':'Sending…');try{await contact(f);notice(status,feedback?'Thank you. Your feedback has been sent to the High Tech Entertainment team.':'Thank you. Your message has been sent.','success')}catch(x){notice(status,x.message||'Could not submit this message.','error')}finally{b.disabled=false}}else if(f.matches('#booking-form')){e.preventDefault();let b=f.querySelector('[type=submit]');b.disabled=true;notice($('#booking-status'),'Sending booking request…');try{let result=await booking(f);if(result.notified){notice($('#booking-status'),'Booking request received. An email alert has been sent to the High Tech Entertainment team. Your date and time still need confirmation.','success')}else{notice($('#booking-status'),'Your booking request was saved successfully, but the email alert could not be sent. The team can still see it in Founder Studio → Inbox. Please contact us if it is urgent.','error');console.error('Booking notification error:',result.error)}playBookingConfirmation($('#booking-status'))}catch(x){notice($('#booking-status'),x.message||'Could not send booking request. Please try the contact form.','error')}finally{b.disabled=false}}else if(f.matches('#profile-form')){e.preventDefault();try{notice($('#profile-status'),'Saving…');await saveProfile(f);notice($('#profile-status'),'Profile saved successfully.','success')}catch(x){notice($('#profile-status'),x.message||'Could not save profile. Check your profile permissions and avatar storage policy.','error')}}else if(f.matches('#track-form')){
  e.preventDefault();
  let b=f.querySelector('[type=submit]');b.disabled=true;notice($('#track-status'),'Saving track…');
  try{
    let d=new FormData(f),file=d.get('audio'),spotifyUrl=String(d.get('spotify_url')||'').trim(),audioUrl='';
    if(spotifyUrl){
      if(!spotifyEmbed(spotifyUrl))throw Error('Enter a valid Spotify track URL (open.spotify.com/track/…).');
      audioUrl=spotifyUrl;
    }else{
      const ext=String(file?.name||'').split('.').pop().toLowerCase();
      if(!file||!file.size)throw Error('Provide a Spotify track URL or choose an audio file.');
      if(file.size>100*1024*1024||!['mp3','m4a','mp4','aac','wav','ogg','flac','webm'].includes(ext)||(file.type&&!file.type.startsWith('audio/')&&file.type!=='application/octet-stream'))throw Error('Choose an MP3, M4A, AAC, WAV, OGG, FLAC or WebM audio file under 100 MB.');
      const path=await upload(file,'hte-audio',session.user.id);
      audioUrl=db.storage.from('hte-audio').getPublicUrl(path).data.publicUrl;
    }
    const {error}=await db.from('tracks').insert({
      title:String(d.get('title')).trim(),
      artist:String(d.get('artist')).trim(),
      release_id:String(d.get('release_id')||'').trim()||null,
      uploader:String(d.get('uploader')||'High Tech Entertainment').trim(),
      genre:String(d.get('genre')||'').trim(),
      cover_url:String(d.get('cover_url')||'').trim()||null,
      audio_url:audioUrl,
      is_published:!!d.get('is_published')
    });
    if(error)throw error;
    f.reset();await adminRefresh();notice($('#track-status'),'Track saved successfully.','success');
  }catch(x){notice($('#track-status'),x.message||'Could not save track.','error')}
  finally{b.disabled=false}
}else if(f.matches('#artist-form')){e.preventDefault();try{let d=new FormData(f),name=String(d.get('name')).trim(),{error}=await db.from('artists').insert({name,slug:name.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''),bio:String(d.get('bio')||'').trim(),image_url:String(d.get('image_url')||'').trim(),is_published:!!d.get('is_published')});if(error)throw error;f.reset();await adminRefresh();notice($('#artist-status'),'Artist added.','success')}catch(x){notice($('#artist-status'),x.message||'Could not add artist.','error')}}else if(f.matches('#release-form')){e.preventDefault();try{let d=new FormData(f),title=String(d.get('title')).trim(),{error}=await db.from('releases').insert({title,slug:title.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''),cover_url:String(d.get('cover_url')||'').trim(),release_date:String(d.get('release_date')||'')||null,spotify_url:String(d.get('spotify_url')||'').trim()||null,is_published:!!d.get('is_published')});if(error)throw error;f.reset();await adminRefresh();notice($('#release-status'),'Release added.','success')}catch(x){notice($('#release-status'),x.message||'Could not add release.','error')}}});$('#auth-mode-toggle')?.addEventListener('click',()=>{const form=$('#login-form');if(!form)return;const signup=form.dataset.mode!=='signup';form.dataset.mode=signup?'signup':'signin';$('#auth-mode-copy').textContent=signup?'Create your free HTE member account to save your profile and manage bookings.':'Sign in with Google or your registered email and password to access your account.';$('#auth-mode-title').textContent=signup?'Create your account.':'Welcome back.';$('#login-submit').textContent=signup?'Create account':'Sign in securely';$('#auth-mode-toggle').textContent=signup?'Already a member? Sign in':'New to HTE? Create an account';$('#forgot-password').hidden=signup;$('#account-display-name-field').hidden=!signup;$('#account-confirm-password-field').hidden=!signup;form.elements.confirm_password.required=signup;form.elements.password.autocomplete=signup?'new-password':'current-password';notice($('#login-status'),'')});$('#forgot-password')?.addEventListener('click',async()=>{try{if(!db)throw Error('Authentication service is not configured.');const email=$('#account-email')?.value.trim();if(!email)throw Error('Enter your email address first.');notice($('#login-status'),'Sending a password recovery link…');const {error}=await db.auth.resetPasswordForEmail(email,{redirectTo:location.origin+siteBasePath()+'?next=account'});if(error)throw error;notice($('#login-status'),'If an account exists for this email, a password recovery link has been sent. Check your inbox.','success')}catch(x){notice($('#login-status'),x.message||'Password recovery could not be started.','error')}});$('#google-login')?.addEventListener('click',async()=>{try{if(!db)throw Error('Authentication service is not configured. Please try again later.');let base=siteBasePath();let next=location.pathname.includes('/account/')?'account':'admin';let url=location.origin+base+'?next='+next;let {error}=await db.auth.signInWithOAuth({provider:'google',options:{redirectTo:url}});if(error)throw error}catch(x){notice($('#login-status'),x.message||'Google sign-in failed.','error')}});$('#refresh-admin')?.addEventListener('click',adminRefresh);$('#profile-form')?.addEventListener('change',e=>{if(e.target.name==='avatar'&&e.target.files?.[0]){let u=URL.createObjectURL(e.target.files[0]);$('#profile-avatar').innerHTML='<img src="'+u+'" alt="Profile photo preview">'}});const authStateSubscription=db?.auth.onAuthStateChange((event,nextSession)=>{Promise.resolve().then(()=>refreshAuth(nextSession,true)).catch(error=>{console.error('HTE auth-state update failed',error);notice($('[data-auth-status]'),'Your sign-in state could not be refreshed. Please reload the page.','error')});});if($('#year'))$('#year').textContent=new Date().getFullYear();$('[data-start-form],#contact-form,#booking-form,#feedback-form').forEach(f=>f.dataset.startedAt=String(Date.now()));refreshAuth().catch(error=>{console.error('HTE authentication initialisation failed',error);if($('#account-login'))$('#account-login').hidden=false;if($('#account-content'))$('#account-content').hidden=true;notice($('[data-auth-status]'),'We could not check your sign-in session. Please reload the page and try again.','error')});if($('#public-tracks'))loadCatalog()})();