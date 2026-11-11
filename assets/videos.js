(()=>{'use strict';
const el=document.getElementById('public-videos');if(!el)return;
const cfg=window.HTE_SUPABASE_CONFIG||{};
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const safeHttps=value=>{try{const url=new URL(String(value||''));return url.protocol==='https:'&&!url.username&&!url.password?url.href:''}catch{return ''}};
function videoEmbed(value){
 const safe=safeHttps(value);if(!safe)return '';
 try{const url=new URL(safe),host=url.hostname.replace(/^www\./,'').toLowerCase(),parts=url.pathname.split('/').filter(Boolean);
  if(host==='youtu.be'){const id=parts[0]||'';return /^[A-Za-z0-9_-]{11}$/.test(id)?'https://www.youtube-nocookie.com/embed/'+id+'?rel=0':''}
  if(['youtube.com','m.youtube.com','music.youtube.com','youtube-nocookie.com'].includes(host)){const id=url.searchParams.get('v')||(['embed','shorts','live'].includes(parts[0])?parts[1]:'');return /^[A-Za-z0-9_-]{11}$/.test(id||'')?'https://www.youtube-nocookie.com/embed/'+id+'?rel=0':''}
  if(host==='vimeo.com'||host==='player.vimeo.com'){const id=[...parts].reverse().find(part=>/^\d+$/.test(part))||'';return id?'https://player.vimeo.com/video/'+id:''}
 }catch{return ''}return '';
}
const featured=[
 {id:'zm-bitter-truth-2026',title:'Bitter Truth',artist:'Yo Maps',description:'Official music video • released 6 November 2026',video_url:'https://www.youtube.com/watch?v=DINvroKkqn0',thumbnail_url:'https://i.ytimg.com/vi/DINvroKkqn0/hqdefault.jpg',featured:true},
 {id:'zm-nkankala-free-2026',title:'Nkankala Free',artist:'Way Dee ft. Xaven',description:'New Zambian release • 6 November 2026',video_url:'https://www.youtube.com/watch?v=bvbTTtQKCsI',featured:true},
 {id:'zm-international-baby-2026',title:'International Baby',artist:'76 Drums ft. Blood Kid YVOK',description:'New Zambian release • 3 November 2026',video_url:'https://www.youtube.com/watch?v=V0wBwSPHYvE',featured:true},
 {id:'zm-dear-mutima-2026',title:'Dear Mutima',artist:'Frank Ro ft. Kell Kay',description:'New Zambian–Malawian collaboration • November 2026',video_url:'https://www.youtube.com/watch?v=8HAJA43fta8',featured:true},
 {id:'zm-pa-duty-2026',title:'Pa Duty',artist:'Ace Trap ft. Natasha Chansa',description:'New Zambian release • November 2026',search_url:'https://www.youtube.com/results?search_query=Ace+Trap+Natasha+Chansa+Pa+Duty+official+video',featured:true},
 {id:'zm-final-destination-2026',title:'Final Destination',artist:'Shenky',description:'New Zambian release • November 2026',search_url:'https://www.youtube.com/results?search_query=Shenky+Final+Destination+official+video',featured:true}
];
let db=null;if(window.supabase&&cfg.url&&cfg.publishableKey){db=window.supabase.createClient(cfg.url,cfg.publishableKey,{auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false}})}
function videoCard(video){
 const src=safeHttps(video.video_url),poster=safeHttps(video.thumbnail_url),embed=videoEmbed(src),title=esc(video.title||'High-Tech Entertainment video'),artist=video.artist?esc(video.artist)+' · ':'',description=esc(video.description||'Official video from High-Tech Entertainment');
 if(video.search_url){const href=safeHttps(video.search_url);if(!href)return '';return '<article class="video-card video-discovery-card"><div class="video-discovery-art" aria-hidden="true"><span>🇿🇲</span><small>NEW ZAMBIAN MUSIC</small></div><div class="video-card-copy"><span class="video-card-kicker">Fresh release</span><h3>'+title+'</h3><p>'+artist+description+'</p><a class="btn ghost" href="'+esc(href)+'" target="_blank" rel="noopener noreferrer">Find official upload ↗</a></div></article>'}
 if(!src)return '';
 const path=new URL(src).pathname.toLowerCase(),direct=path.includes('/storage/v1/object/')||/\.(mp4|webm|mov|m4v)$/i.test(path);let player='';
 if(embed){player='<iframe class="video-embed" title="Watch '+title+'" src="'+esc(embed)+'" loading="lazy" allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe><a class="video-open-youtube" href="'+esc(src)+'" target="_blank" rel="noopener noreferrer">Open on YouTube ↗</a>'}
 else if(direct){player='<video controls preload="metadata"'+(poster?' poster="'+esc(poster)+'"':'')+' src="'+esc(src)+'"></video>'}
 else{player='<a class="video-external" href="'+esc(src)+'" target="_blank" rel="noopener noreferrer">Watch video ↗</a>'}
 return '<article class="video-card'+(video.featured?' is-featured':'')+'">'+(video.featured?'<span class="video-card-kicker">Featured Zambian release</span>':'')+player+'<h3>'+title+'</h3><p>'+artist+description+'</p></article>';
}
async function load(){
 el.innerHTML='<div class="empty" role="status">Loading official videos…</div>';
 let own=[],sourceError=false;
 if(db){try{const {data,error}=await db.from('videos').select('id,title,artist,description,video_url,thumbnail_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60);if(error)throw error;own=data||[]}catch(error){sourceError=true;console.warn('HTE public video catalogue unavailable; showing curated YouTube releases.',error)}}
 const ids=new Set(own.map(v=>{const u=safeHttps(v.video_url);return u?videoEmbed(u).split('/embed/')[1]?.split('?')[0]:''}).filter(Boolean));
 const curated=featured.filter(v=>{const id=videoEmbed(v.video_url).split('/embed/')[1]?.split('?')[0];return !id||!ids.has(id)});
 const cards=[...own.map(v=>videoCard(v)).filter(Boolean),...curated.map(v=>videoCard(v)).filter(Boolean)];
 el.innerHTML=cards.length?cards.join(''):'<div class="empty" role="status">New videos will appear here soon.</div>';
 if(sourceError)el.insertAdjacentHTML('beforeend','<p class="video-source-note">Showing selected YouTube releases while the HTE video catalogue reconnects.</p>');
}
el.addEventListener('click',event=>{if(event.target.closest('[data-video-retry]'))void load()});void load();
})();
