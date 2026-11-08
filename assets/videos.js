(()=>{'use strict';
const el=document.getElementById('public-videos');if(!el)return;
const cfg=window.HTE_SUPABASE_CONFIG||{};
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fail=message=>{el.innerHTML='<div class="empty" role="alert">'+esc(message)+' <button class="btn" type="button" data-video-retry>Retry videos</button></div>'};
const safeHttps=value=>{try{const url=new URL(String(value||''));return url.protocol==='https:'&&!url.username&&!url.password?url.href:''}catch{return ''}};
function videoEmbed(value){
  const safe=safeHttps(value);if(!safe)return '';
  try{
    const url=new URL(safe),host=url.hostname.replace(/^www\./,'').toLowerCase(),parts=url.pathname.split('/').filter(Boolean);
    if(host==='youtu.be'){
      const id=parts[0]||'';
      return /^[A-Za-z0-9_-]{11}$/.test(id)?'https://www.youtube-nocookie.com/embed/'+id+'?rel=0':'';
    }
    if(['youtube.com','m.youtube.com','music.youtube.com','youtube-nocookie.com'].includes(host)){
      const id=url.searchParams.get('v')||(['embed','shorts','live'].includes(parts[0])?parts[1]:'');
      return /^[A-Za-z0-9_-]{11}$/.test(id||'')?'https://www.youtube-nocookie.com/embed/'+id+'?rel=0':'';
    }
    if(host==='vimeo.com'||host==='player.vimeo.com'){
      const id=[...parts].reverse().find(part=>/^\d+$/.test(part))||'';
      return id?'https://player.vimeo.com/video/'+id:'';
    }
  }catch{return ''}
  return '';
}
let db=null;
if(window.supabase&&cfg.url&&cfg.publishableKey){
  db=window.supabase.createClient(cfg.url,cfg.publishableKey,{auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false}});
}
function videoCard(video){
  const src=safeHttps(video.video_url),poster=safeHttps(video.thumbnail_url),embed=videoEmbed(src),title=esc(video.title||'High-Tech Entertainment video');
  if(!src)return '';
  const path=new URL(src).pathname.toLowerCase();
  const direct=path.includes('/storage/v1/object/')||/\.(mp4|webm|mov|m4v)$/i.test(path);
  let player='';
  if(embed){
    player='<iframe class="video-embed" title="Watch '+title+'" src="'+esc(embed)+'" loading="lazy" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>';
  }else if(direct){
    player='<video controls preload="metadata"'+(poster?' poster="'+esc(poster)+'"':'')+' src="'+esc(src)+'"></video>';
  }else{
    player='<a class="video-external" href="'+esc(src)+'" target="_blank" rel="noopener noreferrer">Watch video ↗</a>';
  }
  return '<article class="video-card">'+player+'<h3>'+title+'</h3><p>'+(video.artist?esc(video.artist)+' · ':'')+esc(video.description||'Official video from High-Tech Entertainment')+'</p></article>';
}
async function load(){
  if(!db){fail('Video catalogue is temporarily unavailable. Check your connection and configuration.');return}
  el.innerHTML='<div class="empty" role="status">Loading official videos…</div>';
  try{
    const {data,error}=await db.from('videos').select('id,title,artist,description,video_url,thumbnail_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60);
    if(error)throw error;
    const cards=(data||[]).map(videoCard).filter(Boolean);
    el.innerHTML=cards.length?cards.join(''):'<div class="empty">Official videos will appear here soon.</div>';
  }catch(error){
    console.error('HTE public videos',error);
    fail('Videos could not be loaded. Please try again.');
  }
}
el.addEventListener('click',event=>{if(event.target.closest('[data-video-retry]')){if(db)void load();else location.reload()}});
void load();
})();