(()=>{'use strict';
const $=s=>document.querySelector(s),params=new URLSearchParams(location.search),trackId=params.get('track');
const config=window.HTE_SUPABASE_CONFIG||{},client=window.HTE_SUPABASE_CLIENT||(window.supabase&&config.url&&config.publishableKey?window.supabase.createClient(config.url,config.publishableKey):null);
const button=$('#create-download'),status=$('#download-status'),title=$('#track-title'),artist=$('#track-artist'),progress=$('#download-progress');
let track=null,busy=false;
function message(text,type=''){status.textContent=text;status.className='status '+type}
async function load(){
 if(!client){message('The secure catalogue connection is unavailable. Reload this page and try again.','error');return}
 if(!trackId){message('No track was selected. Return to the HTE catalogue and choose a song.','error');return}
 try{
  const result=await client.from('tracks').select('id,title,artist,audio_url,source_platform,rights_status,download_allowed,is_published').eq('id',trackId).eq('is_published',true).single();
  if(result.error)throw result.error;track=result.data;
  title.textContent=track.title||'HTE track';artist.textContent=track.artist||'High-Tech Entertainment';
  if(track.source_platform!=='direct'||track.download_allowed!==true||track.rights_status!=='owned_or_licensed')throw Error('Download is available only for HTE-owned or licensed audio with confirmed permissions.');
  if(!track.audio_url)throw Error('The original HTE audio file is not available.');
  button.disabled=false;
  message('Ready. Supabase will copy the authorised audio into private temporary storage and prepare your download.','success');
 }catch(error){message(error.message||'This download is not available.','error');button.disabled=true}
}
async function startDownload(){
 if(busy||!track)return;busy=true;button.disabled=true;progress.style.width='8%';
 try{
  message('Checking rights and preparing a private temporary Supabase copy…');
  const result=await client.functions.invoke('hte-download-delivery',{body:{action:'prepare',trackId:track.id}});
  if(result.error||!result.data?.success||!result.data?.downloadUrl)throw new Error(result.data?.error||result.error?.message||'The secure download could not be prepared.');
  progress.style.width='100%';
  const anchor=document.createElement('a');anchor.href=result.data.downloadUrl;anchor.download=result.data.fileName||'HTE-audio';anchor.rel='noopener';anchor.style.display='none';document.body.append(anchor);anchor.click();anchor.remove();
  message('Your download is ready. The private Supabase copy is automatically removed after 30 minutes; this download link expires in 3 minutes.','success');
 }catch(error){message(error.message||'The HTE download could not be prepared. Please retry.','error')}
 finally{busy=false;button.disabled=false}
}
button.addEventListener('click',startDownload);
load();
})();