(()=>{'use strict';
const start=()=>{
 const catalogue=document.getElementById('music')||document.getElementById('video-catalogue'),videos=document.getElementById('public-videos');if(!catalogue||!videos)return;
 if(document.documentElement.dataset.hteCatalogueReady==='true')return;
 const wrap=catalogue.querySelector('.wrap');if(!wrap)return;
 document.documentElement.dataset.hteCatalogueReady='true';
 if(!document.getElementById('hte-catalogue-search')){
  const panel=document.createElement('div');panel.id='hte-catalogue-search';panel.className='hte-catalogue-search';
  panel.innerHTML='<div class="hte-search-copy"><span class="hte-search-kicker">HTE UNIVERSE SEARCH</span><strong>Find your next sound.</strong><span>Search songs, artists, releases and videos.</span></div><form id="hte-search-form" role="search"><label class="sr-only" for="hte-search-input">Search music, artists and videos</label><span class="hte-search-icon" aria-hidden="true">⌕</span><input id="hte-search-input" type="search" autocomplete="off" placeholder="Search songs, artists, genres or videos…" maxlength="120"><button type="button" id="hte-search-clear" aria-label="Clear search" hidden>×</button><button type="submit" class="hte-search-submit">Search</button></form><p id="hte-search-status" class="hte-search-status" aria-live="polite">Explore the full HTE catalogue.</p>';
  const head=wrap.querySelector('.section-head');wrap.insertBefore(panel,head||wrap.firstChild);
 }
 const videoSection=document.getElementById('videos');
 if(videoSection&&!document.getElementById('hte-video-years')){
  const filters=document.createElement('div');filters.id='hte-video-years';filters.className='hte-video-years';filters.setAttribute('aria-label','Filter videos by release year');
  filters.innerHTML='<span>ARCHIVE</span><button type="button" class="is-active" data-video-filter="all" aria-pressed="true">All years</button><button type="button" data-video-filter="2026" aria-pressed="false">2026</button><button type="button" data-video-filter="2025" aria-pressed="false">2025</button><button type="button" data-video-filter="2024" aria-pressed="false">2024</button><button type="button" data-video-filter="2023" aria-pressed="false">2023</button><button type="button" data-video-filter="older" aria-pressed="false">Earlier</button>';
  const list=videoSection.querySelector('#public-videos');if(list)list.parentNode.insertBefore(filters,list);
 }
 const input=document.getElementById('hte-search-input'),clear=document.getElementById('hte-search-clear'),status=document.getElementById('hte-search-status'),form=document.getElementById('hte-search-form');
 if(!input||!clear||!status||!form)return;
 input.setAttribute('aria-describedby',status.id);
 input.setAttribute('aria-controls','public-tracks public-releases public-artists public-videos');
 let activeYear='all';
 const norm=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/\s+/g,' ').trim();
 function syncQuery(value){
  try{
   const url=new URL(window.location.href),query=String(value||'').trim().slice(0,120);
   if(query)url.searchParams.set('q',query);else url.searchParams.delete('q');
   window.history.replaceState(window.history.state,'',url.pathname+url.search+url.hash);
  }catch(error){/* Search remains usable if history updates are unavailable. */}
 }
 function apply(){
  const raw=input.value.trim(),words=norm(raw).split(' ').filter(Boolean),cards=document.querySelectorAll('#public-tracks .music-card,#public-releases .release-card,#public-artists .artist-card,#public-videos .video-card');
  let shown=0,shownVideos=0;
  cards.forEach(card=>{
   const year=card.dataset.videoYear||'',isVideo=card.classList.contains('video-card');
   const yearMatch=!isVideo||activeYear==='all'||(activeYear==='older'?!!year&&Number(year)<2023:year===activeYear);
   const matches=words.every(word=>norm(card.innerText||'').includes(word));
   card.hidden=!(yearMatch&&matches);if(!card.hidden){shown++;if(isVideo)shownVideos++;}
  });
  clear.hidden=!raw;
  if(words.length&&!shown){
   status.dataset.state='empty';
   status.textContent='No matches for “'+raw+'”'+(activeYear==='all'?'':' in '+(activeYear==='older'?'earlier years':activeYear))+'. Try an artist name or clear your search.';
  }else if(words.length){
   status.dataset.state='results';
   status.textContent=shown+' matching catalogue item'+(shown===1?'':'s')+' across music, artists, releases and videos.';
  }else if(activeYear!=='all'){
   status.dataset.state='results';
   status.textContent='Showing '+shownVideos+' video'+(shownVideos===1?'':'s')+' from '+(activeYear==='older'?'earlier years':activeYear)+'. Music, artist and release cards are unchanged.
  }else{
   status.dataset.state='ready';
   status.textContent='Search across the HTE catalogue or choose a video year below.';
  }
 }
 input.addEventListener('input',()=>{apply();syncQuery(input.value);});
 input.addEventListener('search',()=>{apply();syncQuery(input.value);});
 form.addEventListener('submit',event=>{event.preventDefault();apply();syncQuery(input.value);if(input.value.trim())catalogue.scrollIntoView({behavior:'smooth',block:'start'});});
 clear.addEventListener('click',()=>{input.value='';apply();syncQuery('');input.focus();});
 window.addEventListener('popstate',()=>{input.value=new URLSearchParams(window.location.search).get('q')||'';input.value=input.value.slice(0,120);apply();});
 const filters=document.getElementById('hte-video-years');
 if(filters)filters.addEventListener('click',event=>{
  const button=event.target.closest('[data-video-filter]');if(!button)return;
  activeYear=button.dataset.videoFilter;
  filters.querySelectorAll('button').forEach(item=>{const active=item===button;item.classList.toggle('is-active',active);item.setAttribute('aria-pressed',String(active));});
  apply();
 });
 let tagging=false;
 async function tagYears(){
  const db=window.HTE_SUPABASE_CLIENT;if(!db||tagging)return;tagging=true;
  try{
   const {data,error}=await db.from('videos').select('title,published_at,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(500);
   if(error||!data)return;
   const byTitle=new Map(data.map(video=>[norm(video.title),String(video.published_at||video.created_at||'').slice(0,4)]));
   videos.querySelectorAll('.video-card').forEach(card=>{const title=card.querySelector('h3')?.textContent||'',year=byTitle.get(norm(title));if(year)card.dataset.videoYear=year;});
   apply();
  }finally{tagging=false;}
 }
 const observer=new MutationObserver(()=>{apply();if(!tagging)tagYears();});
 const tracks=document.getElementById('public-tracks'),releases=document.getElementById('public-releases'),artists=document.getElementById('public-artists');
 [tracks,releases,artists,videos].filter(Boolean).forEach(list=>observer.observe(list,{childList:true,subtree:true}));
 const initial=new URLSearchParams(window.location.search).get('q');if(initial)input.value=initial.slice(0,120);
 tagYears();apply();
};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
})();