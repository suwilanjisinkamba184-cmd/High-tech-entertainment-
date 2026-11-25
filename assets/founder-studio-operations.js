(()=>{'use strict';
const configs=[
 {id:'admin-tracks',label:'Music catalogue',file:'hte-music-catalogue.csv'},
 {id:'admin-videos',label:'Video library',file:'hte-video-library.csv'},
 {id:'admin-artists',label:'Artist directory',file:'hte-artists.csv'},
 {id:'admin-releases',label:'Release catalogue',file:'hte-releases.csv'}
];
const escapeCsv=value=>'"'+String(value??'').replace(/"/g,'""').replace(/\r?\n/g,' ')+'"';
function init(tableBody){
 const table=tableBody.closest('table');if(!table||table.dataset.hteTools==='ready')return;
 table.dataset.hteTools='ready';
 const wrap=table.closest('.admin-table-wrap')||table.parentElement;
 const toolbar=document.createElement('div');toolbar.className='hte-table-tools';
 const search=document.createElement('input');search.type='search';search.className='hte-table-search';search.placeholder='Search '+tableBody.dataset.hteLabel.toLowerCase()+'…';search.setAttribute('aria-label','Search '+tableBody.dataset.hteLabel);
 const count=document.createElement('span');count.className='hte-table-count';count.setAttribute('aria-live','polite');
 const exportButton=document.createElement('button');exportButton.type='button';exportButton.className='btn hte-table-export';exportButton.textContent='Export CSV';
 toolbar.append(search,count,exportButton);wrap.parentElement.insertBefore(toolbar,wrap);
 const rows=()=>[...tableBody.querySelectorAll('tr')].filter(row=>row.querySelector('td')&&!row.querySelector('td[colspan]'));
 const headers=[...table.querySelectorAll('thead th')].map(th=>th.innerText.trim());const dataCols=headers.length&&/actions/i.test(headers[headers.length-1])?headers.length-1:headers.length;
 const rowText=row=>[...row.querySelectorAll('td')].slice(0,dataCols).map(cell=>cell.innerText).join(' ').toLocaleLowerCase();
 const refresh=()=>{const term=search.value.trim().toLocaleLowerCase();const all=rows();let visible=0;all.forEach(row=>{const match=!term||rowText(row).includes(term);row.hidden=!match;if(match)visible++});count.textContent=visible+' of '+all.length+' records';};
 search.addEventListener('input',refresh);
 exportButton.addEventListener('click',()=>{const head=[...table.querySelectorAll('thead th')].map(th=>th.innerText.trim());const columns=head.length&&/actions/i.test(head[head.length-1])?head.length-1:head.length;const data=[head.slice(0,columns),...rows().filter(row=>!row.hidden).map(row=>[...row.querySelectorAll('td')].slice(0,columns).map(cell=>cell.innerText.trim()))];if(data.length<2){count.textContent='No visible records to export';return;}const csv='\ufeff'+data.map(line=>line.map(escapeCsv).join(',')).join('\r\n');const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const link=document.createElement('a');link.href=url;link.download=tableBody.dataset.hteFile;document.body.append(link);link.click();link.remove();URL.revokeObjectURL(url);});
 const observer=new MutationObserver(refresh);observer.observe(tableBody,{childList:true,subtree:true,characterData:true});refresh();
}
function start(){configs.forEach(config=>{const body=document.getElementById(config.id);if(body){body.dataset.hteLabel=config.label;body.dataset.hteFile=config.file;init(body)}})}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
const app=document.getElementById('admin-app');if(app)new MutationObserver(start).observe(app,{childList:true,subtree:true});
})();