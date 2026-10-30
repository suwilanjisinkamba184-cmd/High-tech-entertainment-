(()=>{'use strict';
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
  try{
    const config=window.HTE_SUPABASE_CONFIG||{};
    if(!window.supabase||!config.url||!config.publishableKey)throw new Error('The upload service did not load. Refresh the page and try again.');
    const client=window.supabase.createClient(config.url,config.publishableKey,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true,flowType:'pkce'}});
    say('Checking founder access…');
    const {data:auth,error:authError}=await client.auth.getSession();
    if(authError)throw authError;
    const user=auth.session?.user;
    if(!user||user.id!=='b268df9e-eff5-43b9-a38f-869b33add38b'||String(user.email||'').toLowerCase()!=='suwilanjisinkamba184@gmail.com')throw new Error('Founder session not found. Sign out, sign in again, and retry.');
    const data=new FormData(form);
    const file=data.get('audio');
    if(!(file instanceof File)||!file.size)throw new Error('Please select an audio file.');
    const ext=(file.name.split('.').pop()||'').toLowerCase();
    const allowed=['mp3','m4a','mp4','aac','wav','ogg','flac','webm'];
    if(!allowed.includes(ext))throw new Error('Unsupported audio format. Use MP3, M4A, AAC, WAV, OGG, FLAC or WebM.');
    if(file.size>100*1024*1024)throw new Error('This audio file is over the 100 MB bucket limit.');
    const mimeByExt={mp3:'audio/mpeg',m4a:'audio/mp4',mp4:'audio/mp4',aac:'audio/aac',wav:'audio/wav',ogg:'audio/ogg',flac:'audio/flac',webm:'audio/webm'};
    const contentType=(file.type&&file.type!=='application/octet-stream')?file.type:mimeByExt[ext];
    say('Uploading audio file… This may take a moment.');
    const safeName=file.name.replace(/[^a-zA-Z0-9._-]/g,'_');
    const {data:stored,error:storageError}=await client.storage.from('hte-audio').upload(user.id+'/'+Date.now()+'-'+safeName,file,{upsert:false,contentType});
    if(storageError)throw storageError;
    const audioUrl=client.storage.from('hte-audio').getPublicUrl(stored.path).data.publicUrl;
    say('Saving track details…');
    const {error:dbError}=await client.from('tracks').insert({
      title:String(data.get('title')||'').trim(),
      artist:String(data.get('artist')||'').trim(),
      uploader:String(data.get('uploader')||'High Tech Entertainment').trim(),
      genre:String(data.get('genre')||'').trim(),
      cover_url:String(data.get('cover_url')||'').trim()||null,
      audio_url:audioUrl,
      is_published:data.get('is_published')==='on'
    });
    if(dbError)throw dbError;
    form.reset();
    say('Success! Your track has been uploaded and saved. Refreshing the music list…','success');
    setTimeout(()=>location.reload(),1200);
  }catch(error){
    console.error('HTE track upload:',error);
    say(error?.message||'Upload failed. Please try again.','error');
  }finally{
    if(button)button.disabled=false;
  }
},true);
})();