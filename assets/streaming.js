(()=>{'use strict';
const root=document.getElementById('hte-streaming');if(!root)return;
const cfg=window.HTE_SUPABASE_CONFIG||{},db=window.supabase&&cfg.url&&cfg.publishableKey?window.supabase.createClient(cfg.url,cfg.publishableKey,{auth:{persistSession:true,autoRefreshToken:true,autoRefreshToken:true,detectSessionInUrl:true}}):null;
const $=s=>root.querySelector(s),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const read=(k)=>{try{let x=JSON.parse(localStorage.getItem(k)||'[]');return Array.isArray(x)?x:[]}catch(_){return[]}};
let catalogue=[],filtered=[],current=-1,queue=[],audio=new Audio(),user=null,cloudPlaylistId=null;
let favorites=new Set(read('hte-favorites')),playlist=read('hte-playlist'),view='all',shuffle=false;
const spotify=x=>/open\.spotify\.com\/(embed\/)?(track|album|playlist)/i.test(x||'');
const art=x=>x.cover_url?'<img src="'+esc(x.cover_url)+'" alt="" loading="lazy"><span class="hte-art-fallback" hidden>HT</span>':'<span>HT</span>';
const duration=n=>{n=Number(n)||0;return n?Math.floor(n/60)+':'+String(Math.floor(n%60)).padStart(2,'0'):'—'};
function status(s){let e=$('[data-stream-status]');if(e)e.textContent=s}
function saveLocal(){try{localStorage.setItem('hte-favorites',JSON.stringify([...favorites]));localStorage.setItem('hte-playlist',JSON.stringify(playlist))}catch(_){status('Browser storage is full or unavailable. Your account-synced collection remains available when signed in.')}}
function ensureControls(){
 const genre=$('[data-stream-genre]');if(genre&&!genre.querySelector('[value="__favorites"]'))genre.insertAdjacentHTML('beforeend','<option value="__favorites">♥ My favourites</option>');
 const bar=$('.hte-stream-toolbar');if(bar&&!$('[data-cloud-state]'))bar.insertAdjacentHTML('beforeend','<span class="hte-cloud-state" data-cloud-state>Guest collection · this device</span>');
 const head=$('.hte-playlist-heading');if(head&&!$('[data-playlist-shuffle]'))head.insertAdjacentHTML('beforeend','<div class="hte-playlist-tools"><button class="btn" type="button" data-playlist-shuffle>⇄ Shuffle</button><button class="btn" type="button" data-playlist-save>↥ Sync playlist</button></div>');
}
async function syncAccount(){
 if(!db)return;
 try{
  const {data}=await db.auth.getSession();user=data?.session?.user||null;
  const badge=$('[data-cloud-state]');
  if(!user){cloudPlaylistId=null;if(badge)badge.textContent='Guest collection · this device';return}
  if(badge)badge.textContent='Syncing your HTE account…';
  const {data:favs,error:fe}=await db.from('user_favorites').select('track_id').eq('user_id',user.id);
  if(fe)throw fe;
  if(favs?.length){favorites=new Set(favs.map(x=>x.track_id));saveLocal()}
  else if(favorites.size){const rows=[...favorites].map(track_id=>({user_id:user.id,track_id}));const {error}=await db.from('user_favorites').upsert(rows,{onConflict:'user_id,track_id'});if(error)throw error}
  const {data:pls,error:pe}=await db.from('playlists').select('id').eq('user_id',user.id).eq('name','My HTE Playlist').limit(1);
  if(pe)throw pe;
  if(pls?.length)cloudPlaylistId=pls[0].id;
  else{const {data:p,error}=await db.from('playlists').insert({user_id:user.id,name:'My HTE Playlist',description:'Your personal High Tech Entertainment playlist',is_public:false}).select('id').single();if(error)throw error;cloudPlaylistId=p.id}
  const {data:items,error:ie}=await db.from('playlist_tracks').select('track_id,position').eq('playlist_id',cloudPlaylistId).order('position');
  if(ie)throw ie;
  if(items?.length){playlist=items.map(x=>x.track_id);saveLocal()}
  else if(playlist.length){const rows=playlist.map((track_id,position)=>({playlist_id:cloudPlaylistId,track_id,position}));const {error}=await db.from('playlist_tracks').upsert(rows,{onConflict:'playlist_id,track_id'});if(error)throw error}
  if(badge)badge.textContent='✓ Synced to your account';
 }catch(e){console.warn('HTE account library sync:',e);const badge=$('[data-cloud-state]');if(badge)badge.textContent='Account sync unavailable · local backup active'}
}
async function syncFavorite(id,on){if(!user||!db)return;try{if(on){const {error}=await db.from('user_favorites').upsert({user_id:user.id,track_id:id},{onConflict:'user_id,track_id'});if(error)throw error}else{const {error}=await db.from('user_favorites').delete().eq('user_id',user.id).eq('track_id',id);if(error)throw error}}catch(e){console.warn(e);status('Favourite saved on this device, but account sync failed. Please try again later.')}}
async function syncPlaylist(){if(!user||!cloudPlaylistId){status('Sign in through My account to sync your playlist across devices.');return}try{const {error:delErr}=await db.from('playlist_tracks').delete().eq('playlist_id',cloudPlaylistId);if(delErr)throw delErr;if(playlist.length){const rows=playlist.map((track_id,position)=>({playlist_id:cloudPlaylistId,track_id,position}));const {error}=await db.from('playlist_tracks').insert(rows);if(error)throw error}status('Playlist synced securely to your HTE account.')}catch(e){console.error(e);status('Playlist sync failed: '+(e.message||'check account permissions'))}}
async function recordPlay(track){if(!user||!db)return;try{const {error}=await db.from('listening_history').insert({user_id:user.id,track_id:track.id,completed:false});if(error)throw error}catch(e){console.warn('Listening history not saved:',e)}}
function drawPlaylist(){let el=$('[data-stream-playlist]');if(!el)return;let items=playlist.map(id=>catalogue.find(x=>x.id===id)).filter(Boolean);el.innerHTML=items.length?items.map((x,i)=>'<div class="hte-playlist-row"><span><b>'+String(i+1).padStart(2,'0')+'</b> '+esc(x.title)+' <small>— '+esc(x.artist||'HTE')+'</small></span><div><button class="btn hte-icon-btn" data-queue-play="'+esc(x.id)+'" aria-label="Play '+esc(x.title)+'">▶</button><button class="btn hte-icon-btn" data-remove="'+esc(x.id)+'" aria-label="Remove from playlist">×</button></div></div>').join(''):'<p class="muted small">Your playlist is saved on this device. Sign in to sync it across devices.</p>'}
function draw(){let host=$('[data-stream-grid]');if(!host)return;host.innerHTML=filtered.length?filtered.map(x=>{let i=catalogue.indexOf(x),sp=spotify(x.audio_url);return '<article class="hte-stream-card"><div class="hte-stream-art">'+art(x)+'<span class="hte-stream-tag">'+esc(x.genre||'Music')+'</span></div><div class="hte-stream-meta"><h3>'+esc(x.title)+'</h3><p>'+esc(x.artist||'High Tech Entertainment')+'</p><div class="hte-stream-actions">'+(sp?'<a class="btn gold" target="_blank" rel="noopener noreferrer" href="'+esc(String(x.audio_url).replace('/embed/','/'))+'">Listen on Spotify ↗</a>':'<button class="btn gold" data-play="'+i+'">▶ Play</button>')+'<button class="btn hte-icon-btn" data-fav="'+i+'" aria-label="Toggle favourite" aria-pressed="'+favorites.has(x.id)+'">'+(favorites.has(x.id)?'♥':'♡')+'</button><button class="btn hte-icon-btn" data-add="'+i+'" aria-label="Add to playlist">＋</button></div></div></article>'}).join(''):'<div class="empty">No matching tracks found. Try another search or genre.</div>';
 let count=$('[data-stream-count]');if(count)count.textContent=filtered.length+' tracks';
 let genres=[...new Set(catalogue.map(x=>x.genre).filter(Boolean))].sort(),sel=$('[data-stream-genre]'),old=sel?.value;
 if(sel){sel.innerHTML='<option value="">All genres</option><option value="__favorites">♥ My favourites</option>'+genres.map(g=>'<option value="'+esc(g)+'">'+esc(g)+'</option>').join('');sel.value=old==='__favorites'||genres.includes(old)?old:''}
 drawPlaylist();
}
function filter(){let term=($('[data-stream-search]')?.value||'').trim().toLowerCase(),genre=$('[data-stream-genre]')?.value||'';filtered=catalogue.filter(x=>(!genre||genre==='__favorites'?(!genre||favorites.has(x.id)):x.genre===genre)&&[x.title,x.artist,x.genre,x.uploader].some(v=>String(v||'').toLowerCase().includes(term)));draw()}
function player(x){let bar=$('[data-stream-player]');bar.hidden=false;$('[data-now-title]').textContent=x.title;$('[data-now-artist]').textContent=x.artist||'High Tech Entertainment';$('[data-now-cover]').innerHTML=art(x);let link=$('[data-now-download]');link.href=x.audio_url;link.download=(x.title||'hte-track').replace(/[^a-z0-9 _-]/gi,'')+'.mp3'}
function play(i){let x=catalogue[i];if(!x||spotify(x.audio_url))return;current=i;audio.src=x.audio_url;player(x);recordPlay(x);audio.play().then(()=>{$('[data-player-toggle]').textContent='Ⅱ'}).catch(e=>{console.warn(e);status('This track could not be played. Check the audio URL or connection.')})}
function next(){if(!queue.length)return;play(queue.shift())}
function updateTime(){let d=audio.duration||0,t=audio.currentTime||0;$('[data-player-progress]').max=d||100;$('[data-player-progress]').value=t;$('[data-player-time]').textContent=duration(t)+' / '+duration(d)}
async function load(){if(!db){status('Music service is unavailable. Please refresh and try again.');return}status('Loading the HTE catalogue…');try{let {data,error}=await db.from('tracks').select('id,title,artist,uploader,audio_url,cover_url,genre,duration_seconds,created_at,is_published').eq('is_published',true).order('created_at',{ascending:false}).limit(200);if(error)throw error;catalogue=data||[];ensureControls();await syncAccount();filtered=[...catalogue];draw();status(catalogue.length?'Free listening • '+catalogue.length+' published tracks':'The streaming library is ready. New music will appear here when published.')}catch(e){console.error(e);status('Music library error: '+(e.message||'Unable to retrieve published tracks'))}}
root.addEventListener('input',e=>{if(e.target.matches('[data-stream-search]'))filter()});
root.addEventListener('change',e=>{if(e.target.matches('[data-stream-genre]'))filter()});
root.addEventListener('click',async e=>{let b=e.target.closest('button');if(!b)return;
 if(b.dataset.play!==undefined){play(Number(b.dataset.play));return}
 if(b.dataset.fav!==undefined){let x=catalogue[Number(b.dataset.fav)];if(!x)return;let on=!favorites.has(x.id);on?favorites.add(x.id):favorites.delete(x.id);saveLocal();draw();syncFavorite(x.id,on);return}
 if(b.dataset.add!==undefined){let x=catalogue[Number(b.dataset.add)];if(x&&!playlist.includes(x.id)){playlist.push(x.id);saveLocal();drawPlaylist();status('Added to your playlist.');}return}
 if(b.dataset.remove){playlist=playlist.filter(id=>id!==b.dataset.remove);saveLocal();drawPlaylist();if(user&&cloudPlaylistId){const {error}=await db.from('playlist_tracks').delete().eq('playlist_id',cloudPlaylistId).eq('track_id',b.dataset.remove);if(error)status('Removed locally; cloud sync will retry next time.')}return}
 if(b.dataset.queuePlay){let i=catalogue.findIndex(x=>x.id===b.dataset.queuePlay);if(i>=0)play(i);return}
 if(b.hasAttribute('data-playlist-shuffle')){let ids=playlist.map(id=>catalogue.findIndex(x=>x.id===id)).filter(i=>i>=0);for(let i=ids.length-1;i>0;i--){let j=Math.floor(Math.random()*(i+1));[ids[i],ids[j]]=[ids[j],ids[i]]}queue=ids;next();return}
 if(b.hasAttribute('data-playlist-save')){await syncPlaylist();return}
});
$('[data-player-toggle]')?.addEventListener('click',()=>{if(!audio.src)return;if(audio.paused)audio.play().catch(()=>status('Playback could not resume.'));else audio.pause()});
$('[data-player-progress]')?.addEventListener('input',e=>audio.currentTime=Number(e.target.value));
$('[data-player-volume]')?.addEventListener('input',e=>audio.volume=Number(e.target.value));
$('[data-player-next]')?.addEventListener('click',()=>{let ids=playlist.map(id=>catalogue.findIndex(x=>x.id===id)).filter(i=>i>=0);let at=ids.indexOf(current);queue=at>=0?ids.slice(at+1):ids.filter(i=>i!==current);if(shuffle)queue.sort(()=>Math.random()-.5);next()});
$('[data-stream-clear]')?.addEventListener('click',async()=>{playlist=[];saveLocal();drawPlaylist();if(user&&cloudPlaylistId){const {error}=await db.from('playlist_tracks').delete().eq('playlist_id',cloudPlaylistId);if(error)status('Playlist cleared locally; cloud update failed.')}});
audio.addEventListener('timeupdate',updateTime);audio.addEventListener('loadedmetadata',updateTime);audio.addEventListener('play',()=>{$('[data-player-toggle]').textContent='Ⅱ'});audio.addEventListener('pause',()=>{$('[data-player-toggle]').textContent='▶'});audio.addEventListener('ended',next);
if(db)db.auth.onAuthStateChange(()=>{setTimeout(async()=>{await syncAccount();filter()},0)});
load();
})();