(()=>{'use strict';
const $=s=>document.querySelector(s);
const top=document.createElement('button');top.className='hte-backtop';top.type='button';top.setAttribute('aria-label','Back to top');top.textContent='↑';document.body.append(top);
const update=()=>{top.classList.toggle('visible',scrollY>500)};
addEventListener('scroll',update,{passive:true});addEventListener('resize',update);update();top.addEventListener('click',()=>scrollTo({top:0,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'}));
const heroArt=$('.hero-art');if(heroArt&&!heroArt.querySelector('.hte-orbit-label')){const label=document.createElement('span');label.className='hte-orbit-label';label.textContent='SOUND • CULTURE • INNOVATION';heroArt.append(label)}
const tracks=$('#public-tracks');
if(tracks){
 const tools=document.createElement('div');tools.className='catalog-tools';
 tools.innerHTML='<input type="search" id="hte-track-search" placeholder="Search tracks or artists…" aria-label="Search music catalogue"><select id="hte-genre-filter" aria-label="Filter by genre"><option value="">All genres</option></select><button class="hte-favourites-toggle" id="hte-favourites-toggle" type="button" aria-pressed="false">♡ Saved tracks</button><span class="catalog-count" id="hte-catalog-count" aria-live="polite">Explore the catalogue</span>';
 tracks.parentNode.insertBefore(tools,tracks);
 const search=$('#hte-track-search'),genre=$('#hte-genre-filter'),count=$('#hte-catalog-count'),favToggle=$('#hte-favourites-toggle');
 const storageKey='hte.savedTracks.guest.v1';let saved=new Set();
 try{saved=new Set(JSON.parse(localStorage.getItem(storageKey)||'[]').map(String))}catch{saved=new Set()}
 let savedOnly=false;
 const persist=()=>{try{localStorage.setItem(storageKey,JSON.stringify([...saved]))}catch{count.textContent='Saved tracks are unavailable in this browser'}}
 const addButtons=()=>tracks.querySelectorAll('.music-card').forEach(card=>{
  if(card.querySelector('[data-track-fav]'))return;
  const id=card.dataset.trackId;if(!id)return;
  const b=document.createElement('button');b.type='button';b.dataset.trackFav=id;b.setAttribute('aria-pressed',String(saved.has(id)));b.textContent=saved.has(id)?'♥ Saved':'♡ Save track';
  b.addEventListener('click',()=>{if(saved.has(id))saved.delete(id);else saved.add(id);persist();b.setAttribute('aria-pressed',String(saved.has(id)));b.textContent=saved.has(id)?'♥ Saved':'♡ Save track';apply()});
  card.append(b);
 });
 const apply=()=>{const q=search.value.trim().toLowerCase(),g=genre.value;let visible=0;tracks.querySelectorAll('.music-card').forEach(card=>{const text=[card.querySelector('h3')?.textContent,card.querySelector('.muted')?.textContent,card.querySelector('.pill')?.textContent].filter(Boolean).join(' ').toLowerCase(),pill=card.querySelector('.pill')?.textContent.trim().toLowerCase()||'',id=card.dataset.trackId;const show=(!q||text.includes(q))&&(!g||pill===g)&&(!savedOnly||saved.has(id));card.hidden=!show;if(show)visible++});count.textContent=visible+' track'+(visible===1?'':'s')+(savedOnly?' saved':'')+(q||g?' found':' available');favToggle.setAttribute('aria-pressed',String(savedOnly));favToggle.textContent=savedOnly?'♥ Showing saved tracks':'♡ Saved tracks'};
 const populate=()=>{const vals=[...new Set([...tracks.querySelectorAll('.music-card .pill')].map(x=>x.textContent.trim()).filter(Boolean))].sort();const current=genre.value;genre.replaceChildren();const all=document.createElement('option');all.value='';all.textContent='All genres';genre.append(all);vals.forEach(v=>{const option=document.createElement('option');option.value=v.toLowerCase();option.textContent=v;genre.append(option)});genre.value=current;addButtons();apply()};
 search.addEventListener('input',apply);genre.addEventListener('change',apply);favToggle.addEventListener('click',()=>{savedOnly=!savedOnly;apply()});
 new MutationObserver(populate).observe(tracks,{childList:true,subtree:true});populate();
}
const sections=[...document.querySelectorAll('main .section')];
if('IntersectionObserver'in window&&!matchMedia('(prefers-reduced-motion: reduce)').matches){document.body.classList.add('reveal-ready');const io=new IntersectionObserver(entries=>entries.forEach(e=>{if(e.isIntersecting){e.target.classList.add('is-visible');io.unobserve(e.target)}}),{threshold:.08});sections.forEach(s=>io.observe(s))}else sections.forEach(s=>s.classList.add('is-visible'));
})();