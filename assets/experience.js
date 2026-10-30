(()=>{'use strict';
const $=s=>document.querySelector(s);
const progress=document.createElement('div');progress.className='hte-progress';document.body.append(progress);
const top=document.createElement('button');top.className='hte-backtop';top.type='button';top.setAttribute('aria-label','Back to top');top.textContent='↑';document.body.append(top);
const update=()=>{const max=document.documentElement.scrollHeight-innerHeight;progress.style.width=(max>0?scrollY/max*100:0)+'%';top.classList.toggle('visible',scrollY>500)};
addEventListener('scroll',update,{passive:true});update();top.addEventListener('click',()=>scrollTo({top:0,behavior:'smooth'}));
const heroArt=$('.hero-art');if(heroArt&&!heroArt.querySelector('.hte-orbit-label')){const label=document.createElement('span');label.className='hte-orbit-label';label.textContent='SOUND • CULTURE • INNOVATION';heroArt.append(label)}
const tracks=$('#public-tracks');
if(tracks){const tools=document.createElement('div');tools.className='catalog-tools';tools.innerHTML='<input type="search" id="hte-track-search" placeholder="Search tracks or artists…" aria-label="Search music catalogue"><select id="hte-genre-filter" aria-label="Filter by genre"><option value="">All genres</option></select><span class="catalog-count" id="hte-catalog-count" aria-live="polite">Explore the catalogue</span>';tracks.parentNode.insertBefore(tools,tracks);
const search=$('#hte-track-search'),genre=$('#hte-genre-filter'),count=$('#hte-catalog-count');
const apply=()=>{const q=search.value.trim().toLowerCase(),g=genre.value;let visible=0;tracks.querySelectorAll('.music-card').forEach(card=>{const text=card.textContent.toLowerCase(),pill=card.querySelector('.pill')?.textContent.trim()||'';const show=(!q||text.includes(q))&&(!g||pill.toLowerCase()===g);card.hidden=!show;if(show)visible++});count.textContent=visible+' track'+(visible===1?'':'s')+(q||g?' found':' available')};
const populate=()=>{const vals=[...new Set([...tracks.querySelectorAll('.music-card .pill')].map(x=>x.textContent.trim()).filter(Boolean))].sort();const current=genre.value;genre.innerHTML='<option value="">All genres</option>'+vals.map(v=>'<option value="'+v.replace(/&/g,'&amp;').replace(/"/g,'&quot;').toLowerCase()+'">'+v.replace(/</g,'&lt;')+'</option>').join('');genre.value=current;apply()};
search.addEventListener('input',apply);genre.addEventListener('change',apply);new MutationObserver(populate).observe(tracks,{childList:true,subtree:true});populate();
}
const sections=[...document.querySelectorAll('main .section')];if('IntersectionObserver'in window){document.body.classList.add('reveal-ready');const io=new IntersectionObserver(entries=>entries.forEach(e=>{if(e.isIntersecting){e.target.classList.add('is-visible');io.unobserve(e.target)}}),{threshold:.08});sections.forEach(s=>io.observe(s))}else sections.forEach(s=>s.classList.add('is-visible'));
})();