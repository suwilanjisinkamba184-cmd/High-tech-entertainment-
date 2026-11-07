import { useEffect, useState } from 'react';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';

type Track = { id:string; title:string; artist?:string|null; genre?:string|null; audio_url?:string|null; cover_url?:string|null };
type Artist = { id:string; name:string; bio?:string|null; image_url?:string|null };
type Release = { id:string; title:string; release_date?:string|null; cover_url?:string|null; spotify_url?:string|null };
type Video = { id:string; title:string; artist?:string|null; description?:string|null; video_url:string; thumbnail_url?:string|null };
type Data = { tracks:Track[]; artists:Artist[]; releases:Release[]; videos:Video[] };

const empty:Data = { tracks:[], artists:[], releases:[], videos:[] };
let client:SupabaseClient|null = null;
function supabase() {
  if (client) return client;
  const cfg = (window as Window & { HTE_SUPABASE_CONFIG?:{url:string;publishableKey:string} }).HTE_SUPABASE_CONFIG;
  if (!cfg?.url || !cfg?.publishableKey) throw new Error('The music service is not configured.');
  client = createClient(cfg.url, cfg.publishableKey, { auth:{ persistSession:true, autoRefreshToken:true, detectSessionInUrl:true, flowType:'pkce' } });
  return client;
}
function Cover({src,alt}:{src?:string|null;alt:string}) {
  return <div className="cover-art">{src ? <img src={src} alt={alt} loading="lazy" /> : <span aria-hidden="true">HT</span>}</div>;
}
function Spotify({url,title}:{url:string;title:string}) {
  const match = url.match(/open\.spotify\.com\/(track|album|playlist)\/([A-Za-z0-9]+)/);
  if (!match) return <a className="text-link" href={url} target="_blank" rel="noreferrer">Listen on Spotify ↗</a>;
  const height = match[1] === 'track' ? 152 : 352;
  return <iframe title={`Listen to ${title} on Spotify`} src={`https://open.spotify.com/embed/${match[1]}/${match[2]}?theme=0`} width="100%" height={height} loading="lazy" allow="encrypted-media; clipboard-write; fullscreen; picture-in-picture" />;
}
export default function PublicCatalogue() {
  const [data,setData] = useState<Data>(empty);
  const [loading,setLoading] = useState(true);
  const [error,setError] = useState('');
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const db = supabase();
        const [tracks,artists,releases,videos] = await Promise.all([
          db.from('tracks').select('id,title,artist,genre,audio_url,cover_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60),
          db.from('artists').select('id,name,bio,image_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60),
          db.from('releases').select('id,title,release_date,cover_url,spotify_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(60),
          db.from('videos').select('id,title,artist,description,video_url,thumbnail_url,created_at').eq('is_published',true).order('created_at',{ascending:false}).limit(40)
        ]);
        const failure = [tracks,artists,releases,videos].find(x=>x.error);
        if (failure?.error) throw failure.error;
        if (active) setData({ tracks:tracks.data||[], artists:artists.data||[], releases:releases.data||[], videos:videos.data||[] });
      } catch (e) {
        if (active) setError(e instanceof Error ? e.message : 'Catalogue unavailable.');
      } finally { if (active) setLoading(false); }
    })();
    return () => { active=false; };
  },[]);
  return <div className="catalogue">
    {loading && <p className="muted" role="status">Loading the HTE catalogue…</p>}
    {error && <p className="empty" role="alert">Some catalogue content could not load: {error}. Check your connection and refresh.</p>}
    <section className="catalogue-section" id="music"><div className="section-heading"><div><span className="eyebrow">THE SOUND</span><h2>Listen to the latest.</h2></div><span className="section-count">{data.tracks.length} tracks</span></div>
      {data.tracks.length ? <div className="card-grid">{data.tracks.map(t=><article className="media-card" key={t.id}><Cover src={t.cover_url} alt={`${t.title} artwork`} /><div className="card-copy"><span className="tag">{t.genre||'Music'}</span><h3>{t.title}</h3><p className="muted">{t.artist||'High-Tech Entertainment'}</p>{t.audio_url?.includes('open.spotify.com')?<Spotify url={t.audio_url} title={t.title}/>:t.audio_url?<audio controls preload="none" src={t.audio_url} aria-label={`Play ${t.title}`} />:<p className="muted small">Audio coming soon.</p>}</div></article>)}</div>:!loading && <p className="empty">New music is on the way. Check back soon.</p>}
    </section>
    <section className="catalogue-section" id="artists"><div className="section-heading"><div><span className="eyebrow">THE ROSTER</span><h2>Meet the artists.</h2></div><span className="section-count">{data.artists.length} artists</span></div>
      {data.artists.length ? <div className="card-grid artist-grid">{data.artists.map(a=><article className="media-card artist-card" key={a.id}><Cover src={a.image_url} alt={`${a.name} portrait`} /><div className="card-copy"><h3>{a.name}</h3><p className="muted">{a.bio||'Artist · High-Tech Entertainment'}</p></div></article>)}</div>:!loading && <p className="empty">Artist profiles will appear here soon.</p>}
    </section>
    <section className="catalogue-section" id="releases"><div className="section-heading"><div><span className="eyebrow">OFFICIAL DROPS</span><h2>Projects & releases.</h2></div><span className="section-count">{data.releases.length} releases</span></div>
      {data.releases.length ? <div className="card-grid release-grid">{data.releases.map(r=><article className="media-card" key={r.id}><Cover src={r.cover_url} alt={`${r.title} cover`} /><div className="card-copy"><h3>{r.title}</h3><p className="muted">{r.release_date||'Official release'}</p>{r.spotify_url&&<Spotify url={r.spotify_url} title={r.title}/>}</div></article>)}</div>:!loading && <p className="empty">Official releases will appear here soon.</p>}
    </section>
    <section className="catalogue-section" id="videos"><div className="section-heading"><div><span className="eyebrow">WATCH & EXPERIENCE</span><h2>Visual releases.</h2></div><span className="section-count">{data.videos.length} videos</span></div>
      {data.videos.length ? <div className="card-grid video-grid">{data.videos.map(v=><article className="media-card" key={v.id}><video controls preload="metadata" poster={v.thumbnail_url||undefined} src={v.video_url} /><div className="card-copy"><h3>{v.title}</h3><p className="muted">{[v.artist,v.description].filter(Boolean).join(' · ')}</p></div></article>)}</div>:!loading && <p className="empty">Official videos will appear here soon.</p>}
    </section>
  </div>;
}
