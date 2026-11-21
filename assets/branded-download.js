(()=>{'use strict';
const $=s=>document.querySelector(s),params=new URLSearchParams(location.search),trackId=params.get('track');
const config=window.HTE_SUPABASE_CONFIG||{},client=window.HTE_SUPABASE_CLIENT||(window.supabase&&config.url&&config.publishableKey?window.supabase.createClient(config.url,config.publishableKey):null);
const button=$('#create-download'),status=$('#download-status'),title=$('#track-title'),artist=$('#track-artist'),progress=$('#download-progress');
let track=null,sourceUrl='',jingleUrl='',alreadyBranded=false,busy=false;
const MAX_BYTES=100*1024*1024;
function message(text,type=''){status.textContent=text;status.className='status '+type}
function safeHteAudio(value){try{const u=new URL(String(value||'')),base=new URL(config.url||'');if(u.protocol!=='https:'||u.origin!==base.origin||u.username||u.password)return '';const path=decodeURIComponent(u.pathname),match=path.match(/^\/storage\/v1\/object\/(?:public|sign|authenticated)\/([^/]+)\/(.+)$/);return match&&['hte-audio','ht storage'].includes(decodeURIComponent(match[1]))?u.href:''}catch{return ''}}
function fileStem(value){return String(value||'HTE-track').normalize('NFKD').replace(/[^A-Za-z0-9_-]+/g,'-').replace(/^-+|-+$/g,'').slice(0,70)||'HTE-track'}
function extensionForMime(mime){const type=String(mime||'').split(';')[0].toLowerCase();return ({'audio/mpeg':'mp3','audio/mp3':'mp3','audio/mp4':'m4a','audio/x-m4a':'m4a','audio/aac':'aac','audio/wav':'wav','audio/x-wav':'wav','audio/ogg':'ogg','audio/webm':'webm'})[type]||'mp3'}
async function load(){
 if(!client){message('The secure catalogue connection is unavailable. Reload this page and try again.','error');return}
 if(!trackId){message('No track was selected. Return to the HTE catalogue and choose a song.','error');return}
 try{
  const result=await client.from('tracks').select('id,title,artist,audio_url,source_url,source_platform,rights_status,download_allowed,branded_audio_url,download_branding_status,is_published').eq('id',trackId).eq('is_published',true).single();
  if(result.error)throw result.error;track=result.data;
  title.textContent=track.title||'HTE track';artist.textContent=track.artist||'High-Tech Entertainment';
  if(track.source_platform!=='direct'||track.download_allowed!==true||track.rights_status!=='owned_or_licensed')throw Error('Download is available only for HTE-owned or licensed audio with confirmed permissions.');
  const sourceResult=await client.functions.invoke('hte-download-delivery',{body:{action:'source',trackId:track.id}});
  if(sourceResult.error||!sourceResult.data?.success||!sourceResult.data?.sourceUrl)throw new Error(sourceResult.data?.error||sourceResult.error?.message||'The original HTE audio could not be opened securely.');
  sourceUrl=sourceResult.data.sourceUrl;
  alreadyBranded=sourceResult.data.alreadyBranded===true;
  if(!alreadyBranded){
   const {data:settings}=await client.from('hte_download_branding_settings').select('jingle_url').eq('id',1).eq('is_active',true).maybeSingle();
   jingleUrl=safeHteAudio(settings?.jingle_url);
  }
  button.disabled=false;
  message(alreadyBranded?'The approved HTE-branded master is ready for secure delivery.':jingleUrl?'Ready. HTE will add its approved audio ident before temporary delivery.':'Ready. The original authorised audio will be staged in temporary Supabase storage before delivery.','success');
 }catch(error){message(error.message||'This download is not available.','error');button.disabled=true}
}
function waitForRecorderStop(recorder){return new Promise((resolve,reject)=>{recorder.addEventListener('stop',resolve,{once:true});recorder.addEventListener('error',()=>reject(new Error('The audio recorder stopped unexpectedly.')),{once:true})})}
function playSource(audio,url,label){return new Promise((resolve,reject)=>{audio.pause();audio.onended=null;audio.onerror=null;audio.src=url;audio.currentTime=0;audio.onerror=()=>reject(new Error('Could not read the '+label+'. Check the connection and try again.'));audio.onended=resolve;audio.load();const started=audio.play();if(started&&typeof started.catch==='function')started.catch(()=>reject(new Error('Audio processing was blocked. Tap the button again and allow playback.')));})}
async function makeBrandedBlob(){
 if(!window.AudioContext||!window.MediaRecorder)throw Error('This browser does not support audio branding. Open this page in the latest Chrome browser.');
 const mime=['audio/webm;codecs=opus','audio/ogg;codecs=opus','audio/mp4'].find(type=>MediaRecorder.isTypeSupported(type));
 if(!mime)throw Error('This browser cannot create a compatible branded audio file. Use the latest Chrome browser.');
 const context=new AudioContext();let audio=null,recorder=null,stopPromise=null,chunks=[];
 try{
  await context.resume();audio=new Audio();audio.crossOrigin='anonymous';audio.preload='auto';
  const source=context.createMediaElementSource(audio),destination=context.createMediaStreamDestination();source.connect(destination);
  recorder=new MediaRecorder(destination.stream,{mimeType:mime,audioBitsPerSecond:256000});
  recorder.ondataavailable=event=>{if(event.data&&event.data.size)chunks.push(event.data)};
  stopPromise=waitForRecorderStop(recorder);recorder.start(1000);
  message('Adding the approved HTE audio ident…');
  await playSource(audio,jingleUrl,'HTE audio ident');progress.style.width='12%';
  message('Recording the authorised track…');
  audio.ontimeupdate=()=>{if(audio.duration>0)progress.style.width=(12+Math.min(78,(audio.currentTime/audio.duration)*78))+'%'};
  await playSource(audio,sourceUrl,'track audio');audio.ontimeupdate=null;recorder.stop();await stopPromise;
  const blob=new Blob(chunks,{type:recorder.mimeType||mime});if(!blob.size)throw Error('The branded audio file was empty. Please retry.');
  return blob;
 }finally{if(recorder&&recorder.state!=='inactive')try{recorder.stop()}catch{}if(audio){audio.pause();audio.removeAttribute('src');audio.load()}await context.close().catch(()=>{})}
}
async function originalBlob(){
 const response=await fetch(sourceUrl,{mode:'cors',cache:'no-store'});
 if(!response.ok)throw Error('The original audio could not be retrieved. Check the connection and retry.');
 const blob=await response.blob();
 if(!blob.size||blob.size>MAX_BYTES)throw Error('The audio file is empty or exceeds the 100 MB temporary download limit.');
 return blob;
}
async function deliverTemporary(blob){
 if(blob.size>MAX_BYTES)throw Error('The prepared audio exceeds the 80 MB temporary download limit.');
 const mime=String(blob.type||'audio/mpeg').split(';')[0].toLowerCase(),ext=extensionForMime(mime),fileName=fileStem(track.title)+'-HTE.'+ext;
 message('Preparing a private temporary download in Supabase…');progress.style.width='84%';
 const prepared=await client.functions.invoke('hte-download-delivery',{body:{action:'prepare',trackId:track.id,fileName,contentType:mime,fileSize:blob.size}});
 if(prepared.error||!prepared.data?.success)throw new Error(prepared.data?.error||prepared.error?.message||'Secure temporary storage could not be prepared.');
 const job=prepared.data;
 const uploaded=await client.storage.from(job.bucket).uploadToSignedUrl(job.path,job.token,blob,{contentType:mime,upsert:false});
 if(uploaded.error)throw uploaded.error;
 message('Temporary audio stored. Preparing your secure download link…');progress.style.width='94%';
 const completed=await client.functions.invoke('hte-download-delivery',{body:{action:'complete',jobId:job.jobId}});
 if(completed.error||!completed.data?.success||!completed.data?.downloadUrl)throw new Error(completed.data?.error||completed.error?.message||'The temporary download could not be finalised.');
 const anchor=document.createElement('a');anchor.href=completed.data.downloadUrl;anchor.download=completed.data.fileName||fileName;anchor.rel='noopener';anchor.style.display='none';document.body.append(anchor);anchor.click();anchor.remove();
 progress.style.width='100%';message('Your download is being delivered securely. The temporary Supabase copy is automatically removed after its retention period.','success');
}
async function brandedDownload(){
 if(busy||!track)return;busy=true;button.disabled=true;progress.style.width='0%';
 try{
  let blob;
  if(jingleUrl&&!alreadyBranded)blob=await makeBrandedBlob();
  else{message(alreadyBranded?'Retrieving the approved HTE-branded master…':'Retrieving the authorised original audio…');blob=await originalBlob()}
  await deliverTemporary(blob);
 }catch(error){message(error.message||'The HTE download could not be prepared.','error');button.disabled=false}
 finally{busy=false;if(status.classList.contains('success'))button.disabled=false}
}
button.addEventListener('click',brandedDownload);
load();
})();