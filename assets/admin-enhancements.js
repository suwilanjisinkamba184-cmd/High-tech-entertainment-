(()=>{'use strict';
const db=window.HTE_SUPABASE_CLIENT;
if(!db)return;
const $=s=>document.querySelector(s),withTimeout=(promise,ms,message)=>{let timer;return Promise.race([Promise.resolve(promise),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error(message)),ms)})]).finally(()=>clearTimeout(timer))},esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let modal;
async function founder(){const {data,error}=await withTimeout(db.auth.getSession(),12000,'Founder sign-in check timed out.');if(error)throw error;const user=data.session?.user;if(!user)throw Error('Founder sign-in required.');const {data:isAdmin,error:roleError}=await withTimeout(db.rpc('hte_is_admin'),12000,'Founder permission check timed out.');if(roleError)throw roleError;if(isAdmin!==true)throw Error('Founder access required.');return user}
function ensureModal(){if(modal)return;modal=document.createElement('dialog');modal.className='hte-edit-dialog';modal.innerHTML='<form method="dialog" class="hte-edit-card" id="hte-track-edit-form"><div class="eyebrow">FOUNDER STUDIO / TRACK EDITOR</div><h2>Edit track details</h2><p class="muted small">Update catalogue metadata without re-uploading the audio file.</p><input type="hidden" name="id"><div class="field"><label>Track title</label><input name="title" required maxlength="140"></div><div class="field"><label>Artist</label><input name="artist" required maxlength="180"></div><div class="field"><label>Genre</label><input name="genre" maxlength="80"></div><div class="field"><label>Cover artwork URL</label><input name="cover_url" type="url" maxlength="1000"></div><label class="hte-edit-publish"><input name="is_published" type="checkbox"> Published on public website</label><div class="hte-edit-actions"><button type="button" class="btn" data-edit-cancel>Cancel</button><button type="submit" class="btn gold">Save changes</button></div><p class="status" data-edit-status role="status"></p></form>';document.body.append(modal);modal.addEventListener('click',e=>{if(e.target===modal)modal.close();if(e.target.closest('[data-edit-cancel]'))modal.close()});modal.querySelector('form').addEventListener('submit',saveTrack)}
function addEditButtons(){const body=$('#admin-tracks');if(!body)return;body.querySelectorAll('tr').forEach(row=>{const del=row.querySelector('[data-delete-track]');if(!del||row.querySelector('[data-edit-track]'))return;const b=document.createElement('button');b.type='button';b.className='btn';b.textContent='Edit';b.dataset.editTrack=del.dataset.deleteTrack;del.parentElement.prepend(b)})}
function addArtistArtworkButtons(){const body=$('#admin-artists');if(!body)return;body.querySelectorAll('tr').forEach(row=>{const toggle=row.querySelector('[data-toggle-artist]');if(!toggle||row.querySelector('[data-artist-artwork]'))return;const b=document.createElement('button');b.type='button';b.className='btn';b.textContent='Add / replace artwork';b.dataset.artistArtwork=toggle.dataset.toggleArtist;toggle.parentElement.insertBefore(b,toggle)})}
async function uploadArtistArtwork(id,button){try{const user=await founder(),input=document.createElement('input');input.type='file';input.accept='image/jpeg,image/png,image/webp';input.addEventListener('change',async()=>{const file=input.files?.[0];if(!file)return;if(!['image/jpeg','image/png','image/webp'].includes(file.type)){alert('Choose a JPG, PNG or WebP image.');return}if(file.size>20*1024*1024){alert('Artist artwork must be smaller than 20 MB.');return}let path='';button.disabled=true;const label=button.textContent;button.textContent='Uploading…';try{const safeName=file.name.replace(/[^a-zA-Z0-9._-]/g,'_'),objectPath='artists/'+user.id+'/'+Date.now()+'-'+safeName,{data:stored,error:uploadError}=await withTimeout(db.storage.from('hte-artwork').upload(objectPath,file,{upsert:false,contentType:file.type}),60000,'Artist artwork upload timed out.');if(uploadError)throw uploadError;path=stored.path;const imageUrl=db.storage.from('hte-artwork').getPublicUrl(path).data.publicUrl;const {error:updateError}=await withTimeout(db.from('artists').update({image_url:imageUrl}).eq('id',id),15000,'Artist artwork update timed out.');if(updateError)throw updateError;path='';button.textContent='Artwork saved';setTimeout(()=>location.reload(),500)}catch(error){if(path){const {error:cleanupError}=await db.storage.from('hte-artwork').remove([path]);if(cleanupError)console.warn('HTE artist artwork cleanup failed',cleanupError)}button.disabled=false;button.textContent=label;alert(error.message||'Artist artwork could not be saved.')}});input.click()}catch(error){alert(error.message||'Founder access required to upload artist artwork.')}}
async function editTrack(id){try{await founder();const {data,error}=await withTimeout(db.from('tracks').select('id,title,artist,genre,cover_url,is_published').eq('id',id).single(),12000,'Track details request timed out.');if(error)throw error;ensureModal();const f=$('#hte-track-edit-form');Object.entries(data).forEach(([k,v])=>{if(f.elements[k]){if(f.elements[k].type==='checkbox')f.elements[k].checked=!!v;else f.elements[k].value=v??''}});modal.querySelector('[data-edit-status]').textContent='';modal.showModal()}catch(e){alert(e.message||'Could not open track editor.')}}
async function saveTrack(e){e.preventDefault();const f=e.currentTarget,b=f.querySelector('[type=submit]'),status=f.querySelector('[data-edit-status]');b.disabled=true;status.textContent='Saving changes…';try{await founder();const d=new FormData(f),patch={title:String(d.get('title')||'').trim(),artist:String(d.get('artist')||'').trim(),genre:String(d.get('genre')||'').trim()||null,cover_url:String(d.get('cover_url')||'').trim()||null,is_published:d.get('is_published')==='on'};const {error}=await withTimeout(db.from('tracks').update(patch).eq('id',String(d.get('id'))),15000,'Track update timed out.');if(error)throw error;status.textContent='Track updated successfully.';status.className='status success';setTimeout(()=>{modal.close();$('#refresh-admin')?.click()},500)}catch(err){status.textContent=err.message||'Could not save track.';status.className='status error'}finally{b.disabled=false}}
async function uploadDownloadJingle(file,status){
 if(!file)throw Error('Choose an HTE download jingle audio file.');
 const allowed=['audio/mpeg','audio/mp3','audio/mp4','audio/aac','audio/wav','audio/x-wav','audio/ogg'];
 const ext=(file.name.split('.').pop()||'').toLowerCase();
 if(!allowed.includes(file.type)&&!['mp3','m4a','aac','wav','ogg'].includes(ext))throw Error('Use an MP3, M4A, AAC, WAV or OGG jingle file.');
 if(file.size>20*1024*1024)throw Error('The HTE jingle must be 20 MB or smaller.');
 const user=await founder(),safeName=file.name.replace(/[^a-zA-Z0-9._-]/g,'_');
 const path=user.id+'/branding-'+Date.now()+'-'+safeName;
 const contentType=file.type||({mp3:'audio/mpeg',m4a:'audio/mp4',aac:'audio/aac',wav:'audio/wav',ogg:'audio/ogg'}[ext]||'audio/mpeg');
 status.textContent='Uploading HTE download jingle…';
 const {data:stored,error:uploadError}=await withTimeout(db.storage.from('hte-audio').upload(path,file,{upsert:false,contentType}),60000,'Jingle upload timed out.');
 if(uploadError)throw uploadError;
 const jingleUrl=db.storage.from('hte-audio').getPublicUrl(stored.path).data.publicUrl;
 const {error:saveError}=await withTimeout(db.from('hte_download_branding_settings').upsert({id:1,jingle_url:jingleUrl,is_active:true,updated_at:new Date().toISOString()},{onConflict:'id'}),15000,'Jingle settings could not be saved.');
 if(saveError){const {error:cleanupError}=await db.storage.from('hte-audio').remove([stored.path]);if(cleanupError)console.warn('HTE jingle cleanup failed',cleanupError);throw saveError}
 status.textContent='HTE download jingle is active. New eligible downloads will include it before the song.';
 status.className='status success';
}
async function loadBrandingSettings(){
 const box=$('#hte-download-branding');if(!box)return;
 const status=box.querySelector('[data-branding-status]');
 try{
  await founder();
  const {data,error}=await withTimeout(db.from('hte_download_branding_settings').select('jingle_url,is_active,updated_at').eq('id',1).maybeSingle(),12000,'Download branding settings request timed out.');
  if(error)throw error;
  if(data?.is_active&&data.jingle_url){
   status.innerHTML='Active HTE download ident configured · <a href="'+esc(data.jingle_url)+'" target="_blank" rel="noopener noreferrer">Preview jingle ↗</a>';
   status.className='status success';
  }else{
   status.textContent='No download jingle is configured yet. Upload the approved HTE ident to activate branded downloads.';
   status.className='status';
  }
 }catch(error){status.textContent=error.message||'Download branding settings are unavailable.';status.className='status error'}
}
async function metrics(){
 const host=$('.admin-stat-grid');if(!host)return;
 if(!$('#hte-ops-metrics')){
  const box=document.createElement('div');box.id='hte-ops-metrics';box.className='hte-ops-metrics';
  box.innerHTML='<div class="hte-ops-heading"><div><span class="eyebrow">LIVE OPERATIONS</span><h3>Publishing & enquiries</h3></div><button class="btn" type="button" data-ops-refresh>↻ Update</button></div><div class="hte-ops-grid"><article><small>UNPUBLISHED TRACKS</small><strong data-metric="drafts">—</strong></article><article><small>VIDEO LIBRARY</small><strong data-metric="videos">—</strong></article><article><small>ENQUIRIES RECEIVED</small><strong data-metric="messages">—</strong></article></div><p class="muted small" data-metric-status>Counts are read from the protected Supabase workspace.</p>';
  host.insertAdjacentElement('afterend',box);
  box.addEventListener('click',e=>{if(e.target.closest('[data-ops-refresh]'))loadMetrics()});
 }
 if(!$('#hte-audience-metrics')){
  const audience=document.createElement('section');audience.id='hte-audience-metrics';audience.className='hte-ops-metrics';
  audience.innerHTML='<div class="hte-ops-heading"><div><span class="eyebrow">AUDIENCE INTELLIGENCE</span><h3>Viewers & subscribers</h3><p class="muted small">Website and app activity from Supabase. Subscribers are unique accounts following HTE artists.</p></div><button class="btn" type="button" data-audience-refresh>↻ Refresh</button></div><div class="hte-ops-grid hte-audience-grid" style="grid-template-columns:repeat(auto-fit,minmax(128px,1fr))"><article><small>PAGE VIEWS</small><strong data-audience="page_views">—</strong></article><article><small>UNIQUE VIEWERS</small><strong data-audience="unique_viewers">—</strong></article><article><small>REGISTERED MEMBERS</small><strong data-audience="registered_members">—</strong></article><article><small>ARTIST SUBSCRIBERS</small><strong data-audience="artist_subscribers">—</strong></article><article><small>TRACK PLAYS</small><strong data-audience="total_plays">—</strong></article><article><small>TRACK DOWNLOADS</small><strong data-audience="track_downloads">—</strong></article><article><small>VIEWS · 30 DAYS</small><strong data-audience="page_views_30d">—</strong></article></div><p class="muted small" data-audience-status>Audience totals are loading…</p>';
  const ops=$('#hte-ops-metrics');if(ops)ops.insertAdjacentElement('afterend',audience);else host.insertAdjacentElement('afterend',audience);
  audience.addEventListener('click',e=>{if(e.target.closest('[data-audience-refresh]'))loadAudienceMetrics()});
 }
 if(!$('#hte-download-branding')){
  const branding=document.createElement('section');branding.id='hte-download-branding';branding.className='hte-ops-metrics';
  branding.innerHTML='<div class="hte-ops-heading"><div><span class="eyebrow">DOWNLOAD EXPERIENCE</span><h3>HTE audio ident</h3><p class="muted small">Upload the approved HTE spoken jingle. Eligible downloads are composed with the ident before the song.</p></div><button class="btn gold" type="button" data-upload-jingle>Upload jingle</button></div><input type="file" accept="audio/mpeg,audio/mp4,audio/aac,audio/wav,audio/ogg,.mp3,.m4a,.aac,.wav,.ogg" data-jingle-file hidden><p class="status" data-branding-status>Checking download branding…</p>';
  const audience=$('#hte-audience-metrics');if(audience)audience.insertAdjacentElement('afterend',branding);else host.insertAdjacentElement('afterend',branding);
  const input=branding.querySelector('[data-jingle-file]'),button=branding.querySelector('[data-upload-jingle]'),status=branding.querySelector('[data-branding-status]');
  button.addEventListener('click',()=>input.click());
  input.addEventListener('change',async()=>{const file=input.files?.[0];if(!file)return;button.disabled=true;try{await uploadDownloadJingle(file,status);await loadBrandingSettings()}catch(error){status.textContent=error.message||'Jingle upload failed.';status.className='status error'}finally{button.disabled=false;input.value=''}});
 }
 loadMetrics();loadAudienceMetrics();loadBrandingSettings();
}
async function loadAudienceMetrics(){
 const box=$('#hte-audience-metrics');if(!box)return;
 const status=box.querySelector('[data-audience-status]');
 try{
  await founder();
  const {data,error}=await withTimeout(db.rpc('hte_admin_get_analytics'),15000,'Audience analytics request timed out.');
  if(error)throw error;
  const row=Array.isArray(data)?data[0]:data;
  if(!row)throw Error('Supabase did not return audience analytics.');
  ['page_views','unique_viewers','registered_members','artist_subscribers','total_plays','track_downloads','page_views_30d'].forEach(key=>{
   const node=box.querySelector('[data-audience="'+key+'"]');if(node)node.textContent=Number(row[key]||0).toLocaleString();
  });
  if(status)status.textContent='Updated '+new Date().toLocaleTimeString()+' · Viewers are privacy-safe unique browser/app identifiers.';
 }catch(error){
  if(status)status.textContent=error.message||'Audience analytics are temporarily unavailable.';
  console.warn('HTE audience analytics:',error);
 }
}
async function loadMetrics(){const box=$('#hte-ops-metrics');if(!box)return;try{await founder();const queries=[db.from('tracks').select('id',{count:'exact',head:true}).eq('is_published',false),db.from('videos').select('id',{count:'exact',head:true}),db.from('contact_messages').select('id',{count:'exact',head:true})];const results=await withTimeout(Promise.all(queries),15000,'Operations metrics request timed out.');const keys=['drafts','videos','messages'];results.forEach((r,i)=>{if(r.error)throw r.error;box.querySelector('[data-metric="'+keys[i]+'"]').textContent=String(r.count??0)});box.querySelector('[data-metric-status]').textContent='Updated '+new Date().toLocaleTimeString()}catch(e){box.querySelector('[data-metric-status]').textContent='Some metrics are unavailable. Check founder permissions and table access.';console.warn('HTE operations metrics:',e)}}
document.addEventListener('click',e=>{const artwork=e.target.closest('[data-artist-artwork]');if(artwork){uploadArtistArtwork(artwork.dataset.artistArtwork,artwork);return}const b=e.target.closest('[data-edit-track]');if(b)editTrack(b.dataset.editTrack)});
const body=$('#admin-tracks');if(body){new MutationObserver(addEditButtons).observe(body,{childList:true,subtree:true});addEditButtons()}const artistBody=$('#admin-artists');if(artistBody){new MutationObserver(addArtistArtworkButtons).observe(artistBody,{childList:true,subtree:true});addArtistArtworkButtons()}
const observer=new MutationObserver(()=>{if(!$('#hte-ops-metrics'))metrics()});const stats=$('.admin-stat-grid');if(stats)observer.observe(stats,{childList:true,subtree:true});
document.addEventListener('click',e=>{if(e.target.closest('#refresh-admin'))setTimeout(()=>{loadMetrics();loadAudienceMetrics()},700);if(e.target.closest('[data-tab="overview"]'))setTimeout(metrics,100)});
setTimeout(metrics,500);
})();