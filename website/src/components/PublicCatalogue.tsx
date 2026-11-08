import { useEffect, useMemo, useRef, useState } from 'react';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';

type Track = { id:string; title:string; artist?:string|null; genre?:string|null; audio_url?:string|null; cover_url?:string|null };
type Artist = { id:string; name:string; bio?:string|null; image_url?:string|null };
type Release = { id:string; title:string; release_date?:string|null; cover_url?:string|null; spotify_url?:string|null };
type Video = { id:string; title:string; artist?:string|null; description?:string|null; video_url:string; thumbnail_url?:string|null };
type Data = { tracks:Track[]; artists:Artist[]; releases:Release[]; videos:Video[] };

const empty:Data = { tracks:[], artists:[], releases:[], videos:[] };
const GUEST_SAVED_KEY = 'hte.savedTracks.guest.v1';
const USER_SAVED_PREFIX = 'hte.savedTracks.user.';
let client:SupabaseClient|null = null;

function supabase() {
  if (client) return client;
  const cfg = (window as Window & { HTE_SUPABASE_CONFIG?:{url:string;publishableKey:string} }).HTE_SUPABASE_CONFIG;
  if (!cfg?.url || !cfg?.publishableKey) throw new Error('The music service is not configured.');
  client = createClient(cfg.url, cfg.publishableKey, { auth:{ persistSession:true, autoRefreshToken:true, detectSessionInUrl:true, flowType:'pkce' } });
  return client;
}

function safeHttps(value?:string|null) {
  try {
    const url = new URL(String(value || ''));
    if (url.protocol !== 'https:' || url.username || url.password) return '';
    return url.href;
  } catch { return ''; }
}

function spotifyEmbed(value?:string|null) {
  const raw = String(value || '').trim();
  const uriMatch = raw.match(/^spotify:(track|album|playlist):([A-Za-z0-9]+)$/);
  if (uriMatch) return 'https://open.spotify.com/embed/' + uriMatch[1] + '/' + uriMatch[2] + '?theme=0';
  const safe = safeHttps(raw);
  if (!safe) return '';
  try {
    const url = new URL(safe);
    if (url.hostname !== 'open.spotify.com') return '';
    const parts = url.pathname.split('/').filter(Boolean);
    const offset = parts[0] === 'embed' ? 1 : 0;
    const type = parts[offset], id = parts[offset + 1];
    if (!['track','album','playlist'].includes(type) || !/^[A-Za-z0-9]+$/.test(id || '')) return '';
    return 'https://open.spotify.com/embed/' + type + '/' + id + '?theme=0';
  } catch { return ''; }
}

function Cover({src,alt}:{src?:string|null;alt:string}) {
  const safe = safeHttps(src);
  return <div className="cover-art">{safe ? <img src={safe} alt={alt} loading="lazy" decoding="async" /> : <span aria-hidden="true">HT</span>}</div>;
}

function Spotify({url,title}:{url:string;title:string}) {
  const embed = spotifyEmbed(url);
  const safe = safeHttps(url);
  if (!embed) return safe ? <a className="text-link" href={safe} target="_blank" rel="noopener noreferrer">Open on Spotify ↗</a> : <p className="muted small">Spotify link unavailable.</p>;
  const height = embed.includes('/track/') ? 152 : 352;
  return <iframe title={'Listen to ' + title + ' on Spotify'} src={embed} width="100%" height={height} loading="lazy" allow="encrypted-media; clipboard-write; fullscreen; picture-in-picture" />;
}

function videoEmbed(value?:string|null) {
  const safe = safeHttps(value);
  if (!safe) return '';
  try {
    const url = new URL(safe);
    const host = url.hostname.replace(/^www\\./,'').toLowerCase();
    if (host === 'youtu.be') {
      const id = url.pathname.split('/').filter(Boolean)[0] || '';
      return /^[A-Za-z0-9_-]{11}$/.test(id) ? 'https://www.youtube-nocookie.com/embed/' + id + '?rel=0' : '';
    }
    if (['youtube.com','m.youtube.com','music.youtube.com','youtube-nocookie.com'].includes(host)) {
      const parts = url.pathname.split('/').filter(Boolean);
      const id = url.searchParams.get('v') || (['embed','shorts','live'].includes(parts[0]) ? parts[1] : '');
      return /^[A-Za-z0-9_-]{11}$/.test(id || '') ? 'https://www.youtube-nocookie.com/embed/' + id + '?rel=0' : '';
    }
    if (host === 'vimeo.com' || host === 'player.vimeo.com') {
      const parts = url.pathname.split('/').filter(Boolean);
      const id = [...parts].reverse().find(part=>/^\\d+$/.test(part)) || '';
      return id ? 'https://player.vimeo.com/video/' + id : '';
    }
  } catch { return ''; }
  return '';
}

function readSavedIds(key = GUEST_SAVED_KEY) {
  try {
    const parsed:unknown = JSON.parse(localStorage.getItem(key) || '[]');
    return Array.isArray(parsed) ? [...new Set(parsed.filter((id):id is string => typeof id === 'string'))] : [];
  } catch { return []; }
}

export default function PublicCatalogue() {
  const [data,setData] = useState<Data>(empty);
  const [loading,setLoading] = useState(true);
  const [errors,setErrors] = useState<string[]>([]);
  const [retry,setRetry] = useState(0);
  const [search,setSearch] = useState('');
  const [genre,setGenre] = useState('');
  const [savedOnly,setSavedOnly] = useState(false);
  const [savedIds,setSavedIds] = useState<string[]>([]);
  const [savedReady,setSavedReady] = useState(false);
  const [userId,setUserId] = useState<string|null>(null);
  const [syncNotice,setSyncNotice] = useState('');
  const recordedPlays = useRef(new Set<string>());
  const playHistoryIds = useRef(new Map<string,string>());
  const completedPending = useRef(new Set<string>());

  useEffect(() => {
    setSavedIds(readSavedIds());
    setSavedReady(true);
  },[]);

  useEffect(() => {
    if (savedReady) {
      try { localStorage.setItem(userId ? USER_SAVED_PREFIX + userId + '.v1' : GUEST_SAVED_KEY,JSON.stringify(savedIds)); }
      catch { setSyncNotice('This browser could not save favourites locally.'); }
    }
  },[savedIds,savedReady,userId]);

  useEffect(() => {
    let active = true;
    let db:SupabaseClient;
    try { db = supabase(); }
    catch (e) { setErrors(['music service']); setLoading(false); return; }

    const load = async () => {
      setLoading(true);
      setErrors([]);
      const requests = await Promise.allSettled([
        db.from('tracks').select('id,title,artist,genre,audio_url,cover_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60),
        db.from('artists').select('id,name,bio,image_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60),
        db.from('releases').select('id,title,release_date,cover_url,spotify_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60),
        db.from('videos').select('id,title,artist,description,video_url,thumbnail_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(40)
      ]);
      if (!active) return;
      const names = ['tracks','artists','releases','videos'];
      const next:Data = {...empty};
      const failed:string[] = [];
      requests.forEach((result,index) => {
        if (result.status === 'fulfilled' && !result.value.error) {
          (next as any)[names[index]] = result.value.data || [];
        } else {
          failed.push(names[index]);
          console.error('HTE catalogue ' + names[index] + ' query failed', result.status === 'rejected' ? result.reason : result.value.error);
        }
      });
      setData(next);
      setErrors(failed);
      setLoading(false);
    };
    void load();
    return () => { active = false; };
  },[retry]);

  useEffect(() => {
    let active = true;
    let db:SupabaseClient;
    try { db = supabase(); } catch { return; }

    const syncFavorites = async (id:string) => {
      try {
        const userKey = USER_SAVED_PREFIX + id + '.v1';
        const local = [...new Set([...readSavedIds(userKey),...readSavedIds(GUEST_SAVED_KEY)])];
        if (active) setSavedIds(local);
        const {data:cloud,error} = await db.from('user_favorites').select('track_id');
        if (error) throw error;
        const merged = [...new Set([...(cloud || []).map((row:{track_id:string})=>row.track_id),...local])];
        if (active) setSavedIds(merged);
        if (local.length) {
          const {error:saveError} = await db.from('user_favorites').upsert(
            local.map(track_id=>({user_id:id,track_id})),
            {onConflict:'user_id,track_id',ignoreDuplicates:true}
          );
          if (saveError) throw saveError;
        }
        try {
          localStorage.setItem(userKey,JSON.stringify(merged));
          localStorage.removeItem(GUEST_SAVED_KEY);
        } catch { /* Cloud favourites remain available even if local storage is disabled. */ }
        if (active) setSyncNotice('');
      } catch (e) {
        console.error('HTE favourite sync failed',e);
        if (active) setSyncNotice('Your saved tracks remain on this device. Cloud sync is temporarily unavailable.');
      }
    };

    const {data:{subscription}} = db.auth.onAuthStateChange((_event,session) => {
      const id = session?.user?.id || null;
      if (active) setUserId(id);
      if (id) void syncFavorites(id);
      else if (active) { setSavedIds(readSavedIds(GUEST_SAVED_KEY)); setSyncNotice(''); }
    });
    void db.auth.getSession().then(({data}) => {
      const id = data.session?.user?.id || null;
      if (active) setUserId(id);
      if (id) void syncFavorites(id);
      else if (active) setSavedIds(readSavedIds(GUEST_SAVED_KEY));
    }).catch(e => console.error('HTE auth state unavailable',e));

    return () => { active = false; subscription.unsubscribe(); };
  },[]);

  const genres = useMemo(
    () => [...new Set(data.tracks.map(track=>String(track.genre || 'Music').trim()).filter(Boolean))].sort((a,b)=>a.localeCompare(b)),
    [data.tracks]
  );

  const filteredTracks = useMemo(() => {
    const query = search.trim().toLowerCase();
    return data.tracks.filter(track => {
      const matchesText = !query || [track.title,track.artist,track.genre].some(value=>String(value || '').toLowerCase().includes(query));
      const matchesGenre = !genre || String(track.genre || 'Music').trim() === genre;
      const matchesSaved = !savedOnly || savedIds.includes(track.id);
      return matchesText && matchesGenre && matchesSaved;
    });
  },[data.tracks,search,genre,savedOnly,savedIds]);

  const toggleSaved = (trackId:string) => {
    const alreadySaved = savedIds.includes(trackId);
    setSavedIds(current => alreadySaved ? current.filter(id=>id!==trackId) : [...current,trackId]);
    if (userId) {
      const db = supabase();
      const request = alreadySaved
        ? db.from('user_favorites').delete().eq('user_id',userId).eq('track_id',trackId)
        : db.from('user_favorites').upsert({user_id:userId,track_id:trackId},{onConflict:'user_id,track_id'});
      void request.then(({error}) => {
        if (error) {
          console.error('HTE favourite update failed',error);
          setSyncNotice('A favourite could not sync. It is still saved on this device.');
        } else setSyncNotice('');
      });
    }
  };

  const recordPlay = async (trackId:string) => {
    if (!userId) return;
    const playKey = userId + ':' + trackId;
    if (recordedPlays.current.has(playKey)) return;
    recordedPlays.current.add(playKey);
    try {
      const db = supabase();
      const {data,error} = await db.from('listening_history').insert({user_id:userId,track_id:trackId}).select('id').single();
      if (error) throw error;
      if (data?.id) {
        playHistoryIds.current.set(playKey,data.id);
        if (completedPending.current.delete(playKey)) {
          const {error:updateError} = await db.from('listening_history').update({completed:true}).eq('id',data.id).eq('user_id',userId);
          if (updateError) throw updateError;
        }
      }
    } catch (e) {
      recordedPlays.current.delete(playKey);
      console.error('HTE listening history could not be recorded',e);
    }
  };

  const markCompleted = async (trackId:string) => {
    if (!userId) return;
    const playKey = userId + ':' + trackId;
    const historyId = playHistoryIds.current.get(playKey);
    if (!historyId) { completedPending.current.add(playKey); return; }
    try {
      const db = supabase();
      const {error} = await db.from('listening_history').update({completed:true}).eq('id',historyId).eq('user_id',userId);
      if (error) throw error;
    } catch (e) { console.error('HTE listening completion could not be recorded',e); }
  };

  const queryError = errors.length > 0;

  return <div className="catalogue">
    {queryError && <div className="catalogue-alert" role="alert"><span>Some catalogue sections could not load: {errors.join(', ')}.</span><button type="button" className="text-link" onClick={()=>setRetry(value=>value+1)}>Retry</button></div>}
    {loading && <p className="muted" role="status">Loading the HTE catalogue…</p>}
    <section className="catalogue-section" id="music">
      <div className="section-heading"><div><span className="eyebrow">THE SOUND</span><h2>Listen to the latest.</h2></div><span className="section-count">{filteredTracks.length} of {data.tracks.length} tracks</span></div>
      <div className="catalogue-tools" role="search" aria-label="Search and filter music">
        <label className="catalogue-search"><span className="sr-only">Search tracks and artists</span><input type="search" value={search} onChange={event=>setSearch(event.target.value)} placeholder="Search tracks or artists…" /></label>
        <label className="catalogue-genre"><span className="sr-only">Filter by genre</span><select value={genre} onChange={event=>setGenre(event.target.value)}><option value="">All genres</option>{genres.map(value=><option value={value} key={value}>{value}</option>)}</select></label>
        <button type="button" className="catalogue-saved-toggle" aria-pressed={savedOnly} onClick={()=>setSavedOnly(value=>!value)}>{savedOnly ? '♥ Showing saved' : '♡ Saved tracks'}</button>
        {(search || genre || savedOnly) && <button type="button" className="catalogue-clear" onClick={()=>{setSearch('');setGenre('');setSavedOnly(false)}}>Clear filters</button>}
      </div>
      {syncNotice && <p className="catalogue-sync-note" role="status">{syncNotice}</p>}
      {filteredTracks.length ? <div className="card-grid">{filteredTracks.map(track=>{
        const source = safeHttps(track.audio_url);
        const spotify = spotifyEmbed(track.audio_url);
        const isStorageAudio = !!source && new URL(source).pathname.includes('/storage/v1/object/');
        const isAudioFile = !!source && (isStorageAudio || /\.(mp3|m4a|wav|aac|ogg|flac|opus)$/i.test(new URL(source).pathname));
        const downloadUrl = source && isStorageAudio ? (()=>{const url=new URL(source);url.searchParams.set('download','1');return url.href})() : source;
        const saved = savedIds.includes(track.id);
        return <article className="media-card" key={track.id}>
          <Cover src={track.cover_url} alt={track.title + ' artwork'} />
          <div className="card-copy">
            <span className="tag">{track.genre || 'Music'}</span><h3>{track.title}</h3><p className="muted">{track.artist || 'High-Tech Entertainment'}</p>
            {spotify ? <Spotify url={track.audio_url || ''} title={track.title} /> : isAudioFile ? <><audio controls preload="none" src={source} aria-label={'Play ' + track.title} onPlay={()=>void recordPlay(track.id)} onEnded={()=>void markCompleted(track.id)} /><a className="text-link" href={downloadUrl || source} download>Download audio ↓</a></> : source ? <a className="text-link" href={source} target="_blank" rel="noopener noreferrer">Open audio ↗</a> : <p className="muted small">Audio coming soon.</p>}
            <button type="button" className="catalogue-save-button" aria-pressed={saved} onClick={()=>toggleSaved(track.id)}>{saved ? '♥ Saved' : '♡ Save track'}</button>
          </div>
        </article>;
      })}</div> : !loading && <p className="empty">{data.tracks.length ? 'No tracks match these filters.' : 'New music is on the way. Check back soon.'}</p>}
    </section>
    <section className="catalogue-section" id="artists"><div className="section-heading"><div><span className="eyebrow">THE ROSTER</span><h2>Meet the artists.</h2></div><span className="section-count">{data.artists.length} artists</span></div>
      {data.artists.length ? <div className="card-grid artist-grid">{data.artists.map(artist=><article className="media-card artist-card" key={artist.id}><Cover src={artist.image_url} alt={artist.name + ' portrait'} /><div className="card-copy"><h3>{artist.name}</h3><p className="muted">{artist.bio||'Artist · High-Tech Entertainment'}</p></div></article>)}</div>:!loading && <p className="empty">{errors.includes('artists') ? 'Artist profiles could not be loaded. Retry above.' : 'Artist profiles will appear here soon.'}</p>}
    </section>
    <section className="catalogue-section" id="releases"><div className="section-heading"><div><span className="eyebrow">OFFICIAL DROPS</span><h2>Projects & releases.</h2></div><span className="section-count">{data.releases.length} releases</span></div>
      {data.releases.length ? <div className="card-grid release-grid">{data.releases.map(release=><article className="media-card" key={release.id}><Cover src={release.cover_url} alt={release.title + ' cover'} /><div className="card-copy"><h3>{release.title}</h3><p className="muted">{release.release_date||'Official release'}</p>{release.spotify_url&&<Spotify url={release.spotify_url} title={release.title}/>}</div></article>)}</div>:!loading && <p className="empty">{errors.includes('releases') ? 'Official releases could not be loaded. Retry above.' : 'Official releases will appear here soon.'}</p>}
    </section>
    <section className="catalogue-section" id="videos"><div className="section-heading"><div><span className="eyebrow">WATCH & EXPERIENCE</span><h2>Visual releases.</h2></div><span className="section-count">{data.videos.length} videos</span></div>
      {data.videos.length ? <div className="card-grid video-grid">{data.videos.map(video=>{const videoUrl=safeHttps(video.video_url);const embed=videoEmbed(video.video_url);const poster=safeHttps(video.thumbnail_url);const direct=!!videoUrl && (new URL(videoUrl).pathname.includes('/storage/v1/object/') || /\\.(mp4|webm|mov|m4v)$/i.test(new URL(videoUrl).pathname));return <article className="media-card" key={video.id}>{embed ? <iframe className="video-embed" title={'Watch ' + video.title} src={embed} loading="lazy" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowFullScreen /> : direct ? <video controls preload="metadata" poster={poster || undefined} src={videoUrl} /> : videoUrl ? <a className="text-link" href={videoUrl} target="_blank" rel="noopener noreferrer">Watch video ↗</a> : <div className="empty">Video link unavailable.</div>}<div className="card-copy"><h3>{video.title}</h3><p className="muted">{[video.artist,video.description].filter(Boolean).join(' · ')}</p></div></article>})}</div>:!loading && <p className="empty">{errors.includes('videos') ? 'Official videos could not be loaded. Retry above.' : 'Official videos will appear here soon.'}</p>}
    </section>
  </div>;
}
