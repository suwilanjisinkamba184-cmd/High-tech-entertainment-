(()=>{'use strict';
const selectors=[{form:'#track-form',field:'audio',kind:'audio',label:'Audio preview'},{form:'#video-form',field:'video',kind:'video',label:'Video preview'}];
const formatSize=n=>n<1024*1024?(n/1024).toFixed(0)+' KB':(n/1024/1024).toFixed(1)+' MB';
function attach(config){
 const form=document.querySelector(config.form),input=form?.querySelector('input[name="'+config.field+'"]');if(!input||input.dataset.htePreview==='ready')return;
 input.dataset.htePreview='ready';
 const panel=document.createElement('section');panel.className='hte-upload-preview';panel.hidden=true;panel.setAttribute('aria-live','polite');
 const meta=document.createElement('div');meta.className='hte-upload-preview-meta';
 const title=document.createElement('strong');title.textContent=config.label;
 const detail=document.createElement('span');detail.textContent='Select a file to preview it before upload.';
 meta.append(title,detail);panel.append(meta);
 const media=document.createElement(config.kind);media.controls=true;media.preload='metadata';media.className='hte-upload-preview-media';panel.append(media);
 const clear=document.createElement('button');clear.type='button';clear.className='hte-upload-preview-clear';clear.textContent='Clear preview';panel.append(clear);
 input.insertAdjacentElement('afterend',panel);
 let objectUrl='';
 const reset=()=>{if(objectUrl){URL.revokeObjectURL(objectUrl);objectUrl=''}media.removeAttribute('src');media.load();panel.hidden=true;detail.textContent='Select a file to preview it before upload.'};
 input.addEventListener('change',()=>{
  reset();const file=input.files?.[0];if(!file)return;
  const expected=config.kind==='audio'?'audio/':'video/';
  const ext=(file.name.split('.').pop()||'').toLowerCase();
  const supported=config.kind==='audio'?['mp3','m4a','mp4','aac','wav','ogg','flac','webm']:['mp4','m4v','webm','mov'];
  const genericMime=!file.type||file.type==='application/octet-stream';
  const containerAudioMime=config.kind==='audio'&&['mp4','webm'].includes(ext)&&['video/mp4','video/webm'].includes(file.type);
  if(!supported.includes(ext)||(!genericMime&&!file.type.startsWith(expected)&&!containerAudioMime)){detail.textContent='Choose a supported '+config.kind+' file ('+supported.join(', ').toUpperCase()+').';panel.hidden=false;media.hidden=true;return}
  if(file.size>100*1024*1024){detail.textContent='File exceeds the 100 MB upload limit.';panel.hidden=false;media.hidden=true;return}
  objectUrl=URL.createObjectURL(file);media.hidden=false;media.src=objectUrl;detail.textContent='Preview ready.';panel.hidden=false;
 });
 clear.addEventListener('click',()=>{reset();input.value=''});
 form.addEventListener('reset',reset);
 window.addEventListener('pagehide',()=>{if(objectUrl)URL.revokeObjectURL(objectUrl)},{once:true});
}
function start(){selectors.forEach(attach)}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
})();