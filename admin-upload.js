(()=>{'use strict';
const withTimeout=(promise,ms,message)=>{let timer;return Promise.race([Promise.resolve(promise),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error(message)),ms)})]).finally(()=>clearTimeout(timer))};
const form=document.getElementById('track-form');
if(!form)return;
document.addEventListener('submit',async function(event){
  if(event.target!==form)return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const button=form.querySelector('button[type="submit"],button:not([type])');
  const status=document.getElementById('track-status');
  const say=(message,type='')=>{if(status){status.textContent=message;status.className='status '+type;}};
  if(button)button.disabled=true;
  let uploadedPath=null,recordSaved=false;
  try{
    const config=window.HTE_SUPABASE_CONFIG||{};
    if(!window.supabase||!config.url||!config.publishableKey)throw new Error('The upload service did not load. Refresh the page and try again.');
    const client=window.HTE_SUPABASE_CLIENT;if(!client)throw new Error('The shared High-Tech Entertainment session could not be restored. Reload Founder Studio and try again.');
    say('Checking founder access…');
    const {data:auth,error:authError}=await withTimeout(client.auth.getSession(),12000,'Founder sign-in check timed out. Reload and retry.');
    if(authError)throw authError;
    const user=auth.session?.user;
    if(!user)throw new Error('Your founder session is not active. Sign in again and retry.');
    const {data:founderAllowed,error:founderError}=await withTimeout(client.rpc('hte_is_admin'),12000,'Founder permission check timed out. Reload and retry.');
    if(founderError)throw founderError;
    if(founderAllowed!==true)throw new Error('This account is not authorised to upload music to Founder Studio.');
    const data=new FormData(form);
    const file=data.get('audio');
    const hasFile=file instanceof File&&file.size>0;
    const sourceUrl=String(data.get('source_url')||'').trim();
    if(hasFile&&sourceUrl)throw new Error('Choose either an external link or an audio file, not both.');
    if(!hasFile&&!sourceUrl)throw new Error('Add a YouTube or Spotify link, or select an audio file.');
    let audioUrl=null,sourcePlatform='direct',rightsStatus='stream_only',downloadAllowed=false,brandingStatus='not_applicable';
    if(sourceUrl){
      let parsed;
      try{parsed=new URL(sourceUrl)}catch{throw new Error('Enter a valid YouTube or Spotify URL.')}
      if(parsed.protocol!=='https:'||parsed.username||parsed.password)throw new Error('External music links must use a secure HTTPS URL.');
      const host=parsed.hostname.toLowerCase().replace(/^www\./,'');
      if(host==='youtu.be'||host==='youtube.com'||host==='m.youtube.com'||host==='music.youtube.com'||host==='youtube-nocookie.com'){
        const videoId=host==='youtu.be'?parsed.pathname.split('/').filter(Boolean)[0]:parsed.searchParams.get('v')||parsed.pathname.split('/').filter(Boolean).slice(-1)[0];
        if(!videoId||!/^[A-Za-z0-9_-]{11}$/.test(videoId))throw new Error('That YouTube URL does not appear to point to a valid video.');
        sourcePlatform='youtube';
      }else if(host==='open.spotify.com'||host==='spotify.com'||host==='spotify.link'){
        if(host!=='spotify.link'&&!/(?:^|\/)(track|album|playlist|episode|show)\//.test(parsed.pathname))throw new Error('Use a Spotify track, album or playlist link.');
        sourcePlatform='spotify';
      }else{
        throw new Error('For now, Founder Studio accepts YouTube and Spotify links, or an uploaded audio file.');
      }
      const {data:duplicates,error:duplicateError}=await withTimeout(client.from('tracks').select('id').eq('source_url',sourceUrl).limit(1),12000,'Duplicate-link check timed out. Please retry.');
      if(duplicateError)throw duplicateError;
      if(duplicates?.length)throw new Error('This exact music link is already in the HTE catalogue.');
      rightsStatus='external_link';
    }else{
      const ext=(file.name.split('.').pop()||'').toLowerCase();
      const allowed=['mp3','m4a','mp4','aac','wav','ogg','flac','webm'];
      if(!allowed.includes(ext))throw new Error('Unsupported audio format. Use MP3, M4A, AAC, WAV, OGG, FLAC or WebM.');
      if(file.size>100*1024*1024)throw new Error('This audio file is over the 100 MB bucket limit.');
      const mimeByExt={mp3:'audio/mpeg',m4a:'audio/mp4',mp4:'audio/mp4',aac:'audio/aac',wav:'audio/wav',ogg:'audio/ogg',flac:'audio/flac',webm:'audio/webm'};
      const contentType=(file.type&&file.type!=='application/octet-stream')?file.type:mimeByExt[ext];
      say('Uploading audio file… This may take a moment.');
      const safeName=file.name.replace(/[^a-zA-Z0-9._-]/g,'_');
      const {data:stored,error:storageError}=await withTimeout(client.storage.from('hte-audio').upload(user.id+'/'+Date.now()+'-'+safeName,file,{upsert:false,contentType}),90000,'Audio upload timed out. Check your connection before retrying.');
      if(storageError)throw storageError;
      uploadedPath=stored.path;
      audioUrl=client.storage.from('hte-audio').getPublicUrl(stored.path).data.publicUrl;
      const rightsConfirmed=data.get('rights_confirmed')==='on';
      rightsStatus=rightsConfirmed?'owned_or_licensed':'stream_only';
      downloadAllowed=rightsConfirmed;
      brandingStatus=rightsConfirmed?'pending':'not_applicable';
    }
    say('Saving track details to the shared HTE catalogue…');
    const {error:dbError}=await withTimeout(client.from('tracks').insert({
      title:String(data.get('title')||'').trim(),
      artist:String(data.get('artist')||'').trim(),
      uploader:String(data.get('uploader')||'High Tech Entertainment').trim(),
      genre:String(data.get('genre')||'').trim(),
      release_id:String(data.get('release_id')||'').trim()||null,
      cover_url:String(data.get('cover_url')||'').trim()||null,
      audio_url:audioUrl,
      source_url:sourceUrl||null,
      source_platform:sourcePlatform,
      rights_status:rightsStatus,
      download_allowed:downloadAllowed,
      download_branding_status:brandingStatus,
      is_published:data.get('is_published')==='on'
    }),15000,'Track details could not be saved before the request timed out.')
    if(dbError)throw dbError;
    recordSaved=true;
    form.reset();
    say('Success! Your track has been saved to Supabase. The website and app will sync the published entry automatically.','success');
    setTimeout(()=>location.reload(),1200);
  }catch(error){
    if(uploadedPath&&!recordSaved){try{await client.storage.from('hte-audio').remove([uploadedPath]);}catch(cleanupError){console.warn('HTE orphaned audio cleanup failed:',cleanupError)}}
    console.error('HTE track upload:',error);
    say(error?.message||'Upload failed. Please try again.','error');
  }finally{
    if(button)button.disabled=false;
  }
},true);
})();