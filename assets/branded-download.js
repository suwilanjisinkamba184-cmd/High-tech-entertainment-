(()=>{'use strict';
const $=s=>document.querySelector(s),params=new URLSearchParams(location.search),trackId=params.get('track');
const config=window.HTE_SUPABASE_CONFIG||{},client=window.HTE_SUPABASE_CLIENT||(window.supabase&&config.url&&config.publishableKey?window.supabase.createClient(config.url,config.publishableKey):null);
const button=$('#create-download'),status=$('#download-status'),title=$('#track-title'),artist=$('#track-artist'),progress=$('#download-progress');
let track=null,jingleUrl='',busy=false;
function message(text,type=''){status.textContent=text;status.className='status '+type}
function safeUrl(value){try{const u=new URL(String(value||''));return u.protocol==='https:'&&!u.username&&!u.password?u.href:''}catch{return ''}}
function fileStem(value){return String(value||'HTE-track').normalize('NFKD').replace(/[^A-Za-z0-9_-]+/g,'-').replace(/^-+|-+$/g,'').slice(0,70)||'HTE-track'}
async function load(){
 if(!client){message('The secure catalogue connection is unavailable. Reload this page and try again.','error');return}
 if(!trackId){message('No track was selected. Return to the HTE catalogue and choose a song.','error');return}
 try{
  const result=await client.from('tracks').select('id,title,artist,audio_url,source_url,source_platform,rights_status,download_allowed,branded_audio_url,download_branding_status,is_published').eq('id',trackId).eq('is_published',true).single();
  if(result.error)throw result.error;track=result.data;button.dataset.trackDownload=track.id;
  title.textContent=track.title||'HTE track';artist.textContent=track.artist||'High-Tech Entertainment';
  const platform=String(track.source_platform||'direct').toLowerCase(),source=String(track.source_url||'').trim();
  const audioHost=(()=>{try{return new URL(String(track.audio_url||'')).hostname.toLowerCase()}catch{return ''}})();
  const externalHost=audioHost==='youtu.be'||audioHost.endsWith('youtube.com')||audioHost.endsWith('spotify.com')||audioHost==='spotify.link';
  if(platform!=='direct'||source||externalHost||!safeUrl(track.audio_url))throw Error('This is an external streaming link. Open it on YouTube or Spotify; HTE cannot create a download from that source.');
  if(track.download_allowed!==true||!['owned_or_licensed','legacy_existing'].includes(track.rights_status))throw Error('Download is disabled because HTE download rights have not been confirmed for this audio.');
  if(track.branded_audio_url&&track.download_branding_status==='ready'){
    button.textContent='Download HTE-branded audio';button.disabled=false;message('A branded master is ready. Tap to save it.','success');return;
  }
  const settings=await client.from('hte_download_branding_settings').select('jingle_url').eq('id',1).eq('is_active',true).single();
  if(settings.error)throw Error('The HTE download ident has not been configured yet. Founder Studio must upload the approved jingle before branded downloads can start.');
  jingleUrl=safeUrl(settings.data?.jingle_url);
  if(!jingleUrl)throw Error('The configured HTE download ident URL is invalid. Please contact HTE support.');
  button.disabled=false;message('Ready. The HTE audio ident will be placed before the song.','success');
 }catch(error){message(error.message||'This download is not available.','error');button.disabled=true}
}
function waitForRecorderStop(recorder){
 return new Promise((resolve,reject)=>{recorder.addEventListener('stop',resolve,{once:true});recorder.addEventListener('error',()=>reject(new Error('The audio recorder stopped unexpectedly.')),{once:true})});
}
function playSource(audio,url,label){
 return new Promise((resolve,reject)=>{
  audio.pause();audio.onended=null;audio.onerror=null;audio.src=url;audio.currentTime=0;
  audio.onerror=()=>reject(new Error('Could not read the '+label+'. Check the connection and try again.'));
  audio.onended=resolve;audio.load();
  const started=audio.play();if(started&&typeof started.catch==='function')started.catch(()=>reject(new Error('Audio processing was blocked. Tap the button again and allow playback.')));
 });
}
async function brandedDownload(){
 if(busy||!track)return;busy=true;button.disabled=true;progress.style.width='0%';
 if(track.branded_audio_url&&track.download_branding_status==='ready'){
  const a=document.createElement('a');a.href=track.branded_audio_url;a.download=fileStem(track.title)+'.mp3';a.rel='noopener';a.click();message('Your HTE-branded audio is ready.','success');busy=false;button.disabled=false;return;
 }
 let context=null,recorder=null,audio=null,stopPromise=null,chunks=[];
 try{
  if(!window.AudioContext||!window.MediaRecorder)throw Error('This browser does not support audio branding. Open this page in the latest Chrome browser.');
  const mime=['audio/webm;codecs=opus','audio/ogg;codecs=opus','audio/mp4'].find(type=>MediaRecorder.isTypeSupported(type));
  if(!mime)throw Error('This browser cannot create a compatible branded audio file. Use the latest Chrome browser.');
  context=new AudioContext();await context.resume();
  audio=new Audio();audio.crossOrigin='anonymous';audio.preload='auto';
  const source=context.createMediaElementSource(audio),destination=context.createMediaStreamDestination();source.connect(destination);
  recorder=new MediaRecorder(destination.stream,{mimeType:mime,audioBitsPerSecond:256000});
  recorder.ondataavailable=event=>{if(event.data&&event.data.size)chunks.push(event.data)};
  stopPromise=waitForRecorderStop(recorder);recorder.start(1000);
  message('Adding the High-Tech Entertainment audio ident…');
  await playSource(audio,jingleUrl,'HTE audio ident');progress.style.width='15%';
  message('Recording the authorised song after the HTE ident…');
  audio.ontimeupdate=()=>{if(audio.duration>0)progress.style.width=(15+Math.min(80,(audio.currentTime/audio.duration)*80))+'%'};
  await playSource(audio,safeUrl(track.audio_url),'track audio');audio.ontimeupdate=null;
  recorder.stop();await stopPromise;
  const blob=new Blob(chunks,{type:recorder.mimeType||mime});
  if(!blob.size)throw Error('The branded audio file was empty. Please retry.');
  const ext=mime.includes('mp4')?'m4a':mime.includes('ogg')?'ogg':'webm';
  const objectUrl=URL.createObjectURL(blob),a=document.createElement('a');
  a.href=objectUrl;a.download=fileStem(track.title)+'-HTE.'+ext;a.style.display='none';document.body.append(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(objectUrl),60000);
  progress.style.width='100%';message('Your HTE-branded download has been created. The ident is embedded at the beginning of the file.','success');
 }catch(error){
  if(recorder&&recorder.state!=='inactive')try{recorder.stop()}catch{}
  message(error.message||'The HTE-branded download could not be created.','error');
 }finally{
  if(audio){audio.pause();audio.removeAttribute('src');audio.load()}
  if(context)await context.close().catch(()=>{});
  busy=false;button.disabled=false;
 }
}
button.addEventListener('click',brandedDownload);
load();
})();