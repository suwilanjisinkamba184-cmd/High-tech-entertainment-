import React, { useMemo, useState } from "react";
type Track = {
  title: string;
  artist: string;
  album: string;
  duration: string;
  genre: string;
  tone: string;
  tag: string;
};
const tracks: Track[] = [{
  title: "Golden Hour",
  artist: "L Junior ZM",
  album: "Golden Hour",
  duration: "3:42",
  genre: "Afrobeat",
  tone: "from-amber-500/40 via-yellow-950 to-zinc-950",
  tag: "NEW RELEASE"
}, {
  title: "Midnight Motion",
  artist: "DJ Maneuvera",
  album: "Midnight Motion",
  duration: "4:08",
  genre: "Dancehall",
  tone: "from-violet-500/40 via-indigo-950 to-zinc-950",
  tag: "TRENDING"
}, {
  title: "Beyond The Sound",
  artist: "OJ Junior",
  album: "Beyond The Sound",
  duration: "3:56",
  genre: "Afrobeat",
  tone: "from-cyan-500/30 via-slate-900 to-black",
  tag: "HT EXCLUSIVE"
}, {
  title: "No Limits",
  artist: "L Junior ZM",
  album: "No Limits",
  duration: "3:18",
  genre: "Drill",
  tone: "from-rose-500/30 via-red-950 to-black",
  tag: "FEATURED"
}, {
  title: "Golden Vocals",
  artist: "The Golden Vocals",
  album: "Choir Sessions",
  duration: "5:12",
  genre: "Gospel",
  tone: "from-yellow-200/25 via-stone-800 to-black",
  tag: "STUDIO SESSION"
}];
const nav = [{
  label: "Home",
  icon: "⌂"
}, {
  label: "Discover",
  icon: "◈"
}, {
  label: "Releases",
  icon: "▤"
}, {
  label: "Search",
  icon: "⌕"
}, {
  label: "Library",
  icon: "♡"
}];
const gold = "#d4b46d";
const artists = ["L Junior ZM", "DJ Maneuvera", "OJ Junior", "The Golden Vocals"];
const genres = ["All", "Afrobeat", "Dancehall", "Drill", "Gospel"];
function Cover({
  track,
  size = "md"
}: {
  track: Track;
  size?: "sm" | "md" | "lg";
}) {
  const dimensions = size === "lg" ? "h-64 w-64 rounded-[28px]" : size === "sm" ? "h-12 w-12 rounded-xl" : "aspect-square w-full rounded-2xl";
  return <div className={`relative ${dimensions} shrink-0 overflow-hidden border border-white/10 bg-gradient-to-br ${track.tone}`}>
    <div className="absolute inset-0 bg-[radial-gradient(circle_at_70%_20%,rgba(255,255,255,.35),transparent_38%)]" />
    <div className="absolute inset-0 flex items-center justify-center"><span className={`font-black tracking-tighter text-white/85 ${size === "lg" ? "text-7xl" : size === "sm" ? "text-sm" : "text-4xl"}`}>HT<span style={{
          color: gold
        }}>.</span></span></div>
    {size !== "sm" && <span className="absolute bottom-2 left-2 rounded bg-black/55 px-2 py-1 text-[8px] tracking-widest text-white">{track.tag}</span>}
  </div>;
}
function SectionTitle({
  eyebrow,
  title,
  action,
  onAction
}: {
  eyebrow: string;
  title: string;
  action?: string;
  onAction?: () => void;
}) {
  return <div className="mb-3 flex items-end justify-between"><div><p className="text-[10px] font-semibold tracking-[.22em] text-[#c9a45c]">{eyebrow}</p><h2 className="mt-1 text-xl font-semibold">{title}</h2></div>{action && <button onClick={onAction} className="text-xs text-zinc-400">{action} <span style={{
        color: gold
      }}>→</span></button>}</div>;
}
export const HTMusicAndroidAppPrototype = () => {
  const [active, setActive] = useState("Home");
  const [playing, setPlaying] = useState(false);
  const [liked, setLiked] = useState<number[]>([0]);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const [genre, setGenre] = useState("All");
  const [showPlayer, setShowPlayer] = useState(false);
  const [tab, setTab] = useState("Playlists");
  const [notice, setNotice] = useState("");
  const current = tracks[selected];
  const filtered = useMemo(() => tracks.filter(t => (genre === "All" || t.genre === genre) && (query.trim() === "" || (t.title + " " + t.artist + " " + t.genre).toLowerCase().includes(query.toLowerCase()))), [genre, query]);
  const choose = (index: number) => {
    setSelected(index);
    setPlaying(true);
    setShowPlayer(true);
  };
  const toggleLike = (index: number) => setLiked(old => old.includes(index) ? old.filter(x => x !== index) : [...old, index]);
  const playButton = (index: number) => <button aria-label="Play track" onClick={() => choose(index)} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-[#c9a45c]/35 text-[#d4b46d] hover:bg-[#d4b46d]/10">▶</button>;
  const trackRow = (t: Track, i: number) => <div key={t.title} className="flex items-center gap-3 rounded-2xl border border-white/[.06] bg-white/[.025] p-2.5">
    <button onClick={() => choose(i)}><Cover track={t} size="sm" /></button>
    <button onClick={() => choose(i)} className="min-w-0 flex-1 text-left"><p className="truncate text-sm font-semibold">{t.title}</p><p className="truncate text-xs text-zinc-500">{t.artist} · {t.genre}</p></button>
    <span className="text-[10px] text-zinc-500">{t.duration}</span>
    <button aria-label="Add to favourites" onClick={() => toggleLike(i)} className={liked.includes(i) ? "px-1 text-[#d8b96f]" : "px-1 text-zinc-600"}>♥</button>
    {playButton(i)}
  </div>;
  const pageHeader = (eyebrow: string, title: string, desc: string) => <div className="mb-6"><p className="text-[10px] tracking-[.25em] text-[#c9a45c]">{eyebrow}</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">{title}</h1><p className="mt-2 text-sm leading-relaxed text-zinc-400">{desc}</p></div>;
  const renderPage = () => {
    if (active === "Home") return <>
      <div className="mb-4 flex items-end justify-between"><div><p className="text-xs uppercase tracking-widest text-zinc-500">Your sound. Your world.</p><h1 className="mt-1 text-[27px] font-semibold tracking-tight">Feel the frequency<span style={{
              color: gold
            }}>.</span></h1></div><span className="rounded-full border border-[#b18a45]/30 px-3 py-1.5 text-[10px] tracking-widest text-[#d2b36f]">HT MUSIC</span></div>
      <div className="relative min-h-[218px] overflow-hidden rounded-[26px] border border-[#c5a15d]/30 bg-gradient-to-br from-[#3c2b14] via-[#17130f] to-[#101114] p-5 shadow-[0_18px_50px_rgba(0,0,0,.35)]">
        <div className="absolute -right-12 -top-16 h-64 w-64 rounded-full border border-[#d9b76d]/20" /><div className="absolute -right-2 -top-6 h-44 w-44 rounded-full border border-[#d9b76d]/15" />
        <div className="relative z-10 max-w-[230px]"><span className="text-[9px] font-bold tracking-[.25em] text-[#e4c982]">THE HIGH-TECH SOUND</span><h2 className="mt-3 text-[30px] font-semibold leading-[1.05]">Discover<br />what moves you.</h2><p className="mt-3 text-xs leading-relaxed text-zinc-300">Fresh releases, exclusive sounds and the artists shaping tomorrow.</p><button onClick={() => setActive("Discover")} className="mt-5 rounded-full bg-[#d2b36f] px-5 py-2.5 text-[11px] font-bold text-[#17130b]">EXPLORE MUSIC ↗</button></div>
        <div className="absolute bottom-7 right-5 flex items-end gap-1 opacity-70">{[18, 31, 23, 42, 28, 54, 35, 20, 44, 27, 38, 17].map((h, i) => <span key={i} className="w-1 rounded-full bg-[#d6b66f]" style={{
            height: h
          }} />)}</div>
      </div>
      <section className="mt-7"><SectionTitle eyebrow="MADE FOR THE MOMENT" title="Recently played" action="See all" onAction={() => setActive("Library")} /><div className="flex gap-3 overflow-x-auto pb-2">{tracks.slice(0, 4).map((t, i) => <button key={t.title} onClick={() => choose(i)} className="w-[132px] shrink-0 text-left"><Cover track={t} /><p className="mt-2 truncate text-sm font-semibold">{t.title}</p><p className="truncate text-xs text-zinc-500">{t.artist}</p></button>)}</div></section>
      <section className="mt-7"><SectionTitle eyebrow="CURATED FOR YOU" title="Latest releases" action="View all" onAction={() => setActive("Releases")} /><div className="space-y-2">{tracks.slice(0, 3).map((t, i) => trackRow(t, i))}</div></section>
      <section className="mt-7 rounded-2xl border border-white/[.07] bg-gradient-to-r from-white/[.05] to-transparent p-4"><div className="flex items-center justify-between"><div><p className="text-[10px] tracking-[.2em] text-[#c9a45c]">ARTIST SPOTLIGHT</p><h3 className="mt-1 text-lg font-semibold">Meet the creators.</h3><p className="mt-1 text-xs text-zinc-500">The voices behind the sound.</p></div><div className="flex h-14 w-14 items-center justify-center rounded-full border border-[#c9a45c]/40 bg-gradient-to-br from-[#b58a42] to-[#17120b] font-black text-black">LJ</div></div><button onClick={() => setActive("Artists")} className="mt-3 text-xs text-[#d4b46d]">Explore artists →</button></section>
    </>;
    if (active === "Discover") return <>{pageHeader("EXPLORE THE SOUND", "Discover", "Find your next favourite track, explore genres and meet the artists behind the music.")}<div className="mb-5 flex gap-2 overflow-x-auto pb-1">{genres.map(g => <button key={g} onClick={() => setGenre(g)} className={`shrink-0 rounded-full border px-4 py-2 text-xs ${genre === g ? "border-[#d4b46d] bg-[#d4b46d] text-black" : "border-white/10 text-zinc-400"}`}>{g}</button>)}</div><SectionTitle eyebrow="PICKED FOR YOU" title="Recommended tracks" /><div className="space-y-2">{filtered.map(t => trackRow(t, tracks.indexOf(t)))}</div><section className="mt-7"><SectionTitle eyebrow="EXPLORE MORE" title="Artists" action="See all" onAction={() => setActive("Artists")} /><div className="grid grid-cols-2 gap-3">{artists.map((a, i) => <button key={a} onClick={() => setActive("Artists")} className="rounded-2xl border border-white/10 bg-white/[.03] p-4 text-left"><div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full border border-[#c9a45c]/40 bg-gradient-to-br from-[#b58a42] to-[#17120b] font-bold text-black">{a.split(" ").map(x => x[0]).join("").slice(0, 2)}</div><p className="text-sm font-semibold">{a}</p><p className="mt-1 text-[10px] text-zinc-500">High-Tech artist</p></button>)}</div></section></>;
    if (active === "Releases") return <>{pageHeader("THE LATEST FROM HT", "Releases", "Singles, projects and studio exclusives from the High-Tech Entertainment catalogue.")}<div className="mb-5 grid grid-cols-2 gap-3">{tracks.slice(0, 4).map((t, i) => <button key={t.title} onClick={() => choose(i)} className="text-left"><Cover track={t} /><p className="mt-2 text-sm font-semibold">{t.album}</p><p className="text-xs text-zinc-500">{t.artist}</p><p className="mt-1 text-[9px] tracking-widest text-[#c9a45c]">SINGLE · HT MUSIC</p></button>)}</div><SectionTitle eyebrow="ALL TRACKS" title="Track listing" /><div className="space-y-2">{tracks.map((t, i) => trackRow(t, i))}</div></>;
    if (active === "Search") return <>{pageHeader("FIND YOUR SOUND", "Search", "Search tracks, artists and genres across High-Tech Entertainment.")}<div className="mb-5 flex items-center gap-3 rounded-2xl border border-[#c9a45c]/30 bg-white/[.04] px-4 py-3"><span className="text-xl text-[#d4b46d]">⌕</span><input autoFocus value={query} onChange={e => setQuery(e.target.value)} placeholder="Tracks, artists, genres…" className="w-full bg-transparent text-sm outline-none placeholder:text-zinc-600" /></div><SectionTitle eyebrow={query ? "SEARCH RESULTS" : "POPULAR RIGHT NOW"} title={query ? `Results for “${query}”` : "Popular tracks"} /><div className="space-y-2">{filtered.map(t => trackRow(t, tracks.indexOf(t)))}{filtered.length === 0 && <p className="rounded-2xl border border-white/10 p-5 text-center text-sm text-zinc-500">No matches found. Try another track or artist.</p>}</div></>;
    if (active === "Library") return <>{pageHeader("YOUR PERSONAL COLLECTION", "Your Library", "Keep your favourite music, playlists and listening history together.")}<div className="mb-5 flex gap-2">{["Playlists", "Favourites", "History"].map(x => <button key={x} onClick={() => setTab(x)} className={`rounded-full border px-4 py-2 text-xs ${tab === x ? "border-[#d4b46d] bg-[#d4b46d] text-black" : "border-white/10 text-zinc-400"}`}>{x}</button>)}</div>{tab === "Favourites" ? <div className="space-y-2">{tracks.filter((t, i) => liked.includes(i)).map(t => trackRow(t, tracks.indexOf(t)))}</div> : tab === "History" ? <div className="space-y-2">{tracks.slice(0, 3).map(t => trackRow(t, tracks.indexOf(t)))}</div> : <div className="space-y-3"><button onClick={() => setTab("Favourites")} className="flex w-full items-center gap-4 rounded-2xl border border-white/10 bg-gradient-to-r from-[#473719] to-[#17130e] p-4 text-left"><span className="text-3xl text-[#e1c47e]">♥</span><span><b className="block text-sm">Liked Songs</b><small className="text-xs text-zinc-400">{liked.length} saved tracks</small></span><span className="ml-auto">→</span></button><button onClick={() => setTab("History")} className="flex w-full items-center gap-4 rounded-2xl border border-white/10 bg-white/[.03] p-4 text-left"><span className="text-3xl text-[#d4b46d]">◷</span><span><b className="block text-sm">Recently Played</b><small className="text-xs text-zinc-400">Your listening history</small></span><span className="ml-auto">→</span></button><div className="rounded-2xl border border-dashed border-white/15 p-5 text-center"><p className="text-sm font-semibold">Your playlists</p><p className="mt-1 text-xs text-zinc-500">Playlist creation will be connected to your account backend.</p><button onClick={() => setNotice("Playlist creation will be enabled with account sync.")} className="mt-3 rounded-full border border-[#c9a45c]/40 px-4 py-2 text-xs text-[#d4b46d]">＋ Create playlist</button></div></div>}</>;
    if (active === "Artists") return <>{pageHeader("THE PEOPLE BEHIND THE SOUND", "Artists", "Discover the producers, performers and creative voices of High-Tech Entertainment.")}<div className="space-y-3">{artists.map((a, i) => <button key={a} onClick={() => {
          setQuery(a);
          setActive("Search");
        }} className="flex w-full items-center gap-4 rounded-2xl border border-white/10 bg-white/[.03] p-4 text-left"><div className="flex h-14 w-14 items-center justify-center rounded-full border border-[#c9a45c]/40 bg-gradient-to-br from-[#b58a42] to-[#17120b] font-bold text-black">{a.split(" ").map(x => x[0]).join("").slice(0, 2)}</div><span className="min-w-0 flex-1"><b className="block text-sm">{a}</b><small className="text-xs text-zinc-500">{i === 0 ? "Producer · CEO" : i === 1 ? "DJ · Artist" : i === 2 ? "Artist" : "Choir · Vocal ensemble"}</small></span><span className="text-[#d4b46d]">→</span></button>)}</div></>;
    if (active === "Profile") return <>{pageHeader("YOUR ACCOUNT", "Profile", "Manage your HT Music account and listening experience.")}<div className="mb-5 flex items-center gap-4 rounded-2xl border border-[#c9a45c]/25 bg-gradient-to-r from-[#342713] to-[#111214] p-4"><div className="flex h-14 w-14 items-center justify-center rounded-full bg-[#d4b46d] text-xl font-black text-black">HT</div><div><p className="font-semibold">Welcome to HT Music</p><p className="text-xs text-zinc-400">Sign in to sync your music and downloads</p></div></div>{["Sign in / Create account", "Listening preferences", "Downloads", "Notifications", "Privacy & account", "Help and support"].map(x => <button key={x} onClick={() => setNotice(x + " will be connected to the secure account service.")} className="mb-2 flex w-full items-center justify-between rounded-xl border border-white/[.07] bg-white/[.025] p-4 text-left text-sm">{x}<span className="text-[#d4b46d]">›</span></button>)}</>;
    if (active === "Notifications") return <>{pageHeader("STAY IN THE LOOP", "Notifications", "Updates about new releases, artists and High-Tech announcements.")}<div className="rounded-2xl border border-white/10 bg-white/[.03] p-5"><p className="font-semibold">You're all caught up</p><p className="mt-2 text-sm text-zinc-500">New release alerts and account notifications will appear here.</p></div></>;
    return <>{pageHeader("HT MUSIC", active, "Explore the High-Tech Entertainment music experience.")}</>;
  };
  return <div className="min-h-screen w-full bg-[#08090b] font-sans text-white flex justify-center">
    <div className="relative min-h-screen w-full max-w-[430px] overflow-hidden bg-[#0b0c0e] pb-40 shadow-2xl">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-80 bg-[radial-gradient(ellipse_at_top,rgba(196,148,55,0.16),transparent_68%)]" />
      <header className="relative flex items-center justify-between px-5 pb-5 pt-7">
        <button onClick={() => setActive("Home")} className="flex items-center gap-3 text-left"><div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#c9a45c]/60 bg-gradient-to-br from-[#d9bd7a] via-[#8e6a2c] to-[#17130b] shadow-[0_0_24px_rgba(202,164,88,.16)]"><span className="text-lg font-black tracking-tighter text-black">HT</span></div><div><p className="text-[10px] font-semibold tracking-[.28em] text-[#c9a45c]">HIGH-TECH</p><p className="text-sm font-bold tracking-[.16em]">ENTERTAINMENT</p></div></button>
        <button aria-label="Notifications" onClick={() => setActive("Notifications")} className="relative h-10 w-10 rounded-full border border-white/10 bg-white/[.04] text-lg text-zinc-300">♧<i className="absolute right-2 top-2 h-1.5 w-1.5 rounded-full bg-[#d6b56d]" /></button>
      </header>
      <main className="relative px-5">{renderPage()}</main>
      {notice && <div className="fixed bottom-28 left-1/2 z-[70] w-[90%] max-w-[390px] -translate-x-1/2 rounded-xl border border-[#c9a45c]/40 bg-[#211b10] p-4 text-sm shadow-xl"><div className="flex items-start justify-between gap-3"><p>{notice}</p><button onClick={() => setNotice("")} className="text-[#d4b46d]">✕</button></div></div>}
      <div className="fixed bottom-[66px] left-1/2 z-30 w-full max-w-[430px] -translate-x-1/2 px-3"><button onClick={() => setShowPlayer(true)} className="flex w-full items-center gap-3 rounded-2xl border border-[#c9a45c]/25 bg-[#171717]/95 p-2.5 text-left shadow-[0_8px_35px_rgba(0,0,0,.55)] backdrop-blur-xl"><Cover track={current} size="sm" /><span className="min-w-0 flex-1"><b className="block truncate text-xs">{current.title}</b><small className="block truncate text-[10px] text-zinc-500">{current.artist}</small></span><span onClick={e => {
            e.stopPropagation();
            toggleLike(selected);
          }} className={liked.includes(selected) ? "text-[#d8b96f]" : "text-zinc-500"}>♥</span><span onClick={e => {
            e.stopPropagation();
            setPlaying(!playing);
          }} className="flex h-9 w-9 items-center justify-center rounded-full bg-[#d2b36f] font-bold text-black">{playing ? "Ⅱ" : "▶"}</span></button></div>
      <nav className="fixed bottom-0 left-1/2 z-40 flex w-full max-w-[430px] -translate-x-1/2 justify-around border-t border-white/[.07] bg-[#0d0e10]/95 px-1 pb-5 pt-3 backdrop-blur-xl">{nav.map(n => <button key={n.label} onClick={() => setActive(n.label)} className={`flex min-w-[58px] flex-col items-center gap-1 text-[9px] ${active === n.label ? "text-[#d5b46e]" : "text-zinc-500"}`}><span className="text-xl leading-5">{n.icon}</span><span>{n.label}</span></button>)}<button onClick={() => setActive("Profile")} className={`flex min-w-[58px] flex-col items-center gap-1 text-[9px] ${active === "Profile" ? "text-[#d5b46e]" : "text-zinc-500"}`}><span className="text-xl leading-5">◉</span><span>Profile</span></button></nav>
      {showPlayer && <div className="fixed inset-0 z-[60] flex flex-col bg-[#08090b] px-6 pb-10 pt-8"><div className="flex items-center justify-between"><button onClick={() => setShowPlayer(false)} className="text-sm text-[#d4b46d]">⌄ Minimise</button><span className="text-[10px] tracking-[.25em] text-zinc-500">NOW PLAYING</span><button onClick={() => setNotice("More player options will be available in the full app.")} className="text-xl text-zinc-400">•••</button></div><div className="flex flex-1 flex-col items-center justify-center"><Cover track={current} size="lg" /><div className="mt-8 w-full max-w-[320px]"><div className="flex items-center justify-between gap-3"><div className="min-w-0"><h2 className="truncate text-2xl font-semibold">{current.title}</h2><p className="mt-1 text-sm text-zinc-400">{current.artist}</p></div><button onClick={() => toggleLike(selected)} className={liked.includes(selected) ? "text-2xl text-[#d8b96f]" : "text-2xl text-zinc-500"}>♥</button></div><div className="mt-8"><div className="h-1 overflow-hidden rounded-full bg-white/10"><div className="h-full w-[38%] rounded-full bg-[#d4b46d]" /></div><div className="mt-2 flex justify-between text-[10px] text-zinc-500"><span>1:24</span><span>{current.duration}</span></div></div><div className="mt-7 flex items-center justify-between"><button onClick={() => setNotice("Shuffle mode is ready for playback integration.")} className="text-zinc-500">⤨</button><button onClick={() => {
                setSelected((selected + tracks.length - 1) % tracks.length);
                setPlaying(true);
              }} className="text-3xl text-zinc-200">|◀</button><button onClick={() => setPlaying(!playing)} className="flex h-16 w-16 items-center justify-center rounded-full bg-[#d4b46d] text-2xl text-black">{playing ? "Ⅱ" : "▶"}</button><button onClick={() => {
                setSelected((selected + 1) % tracks.length);
                setPlaying(true);
              }} className="text-3xl text-zinc-200">▶|</button><button onClick={() => setNotice("Repeat mode is ready for playback integration.")} className="text-zinc-500">↻</button></div><div className="mt-8 flex justify-between text-xs text-zinc-500"><button onClick={() => setNotice("Queue management will be connected to the playback engine.")}>☷ Queue</button><button onClick={() => setNotice("Device selection will be available in the native player.")}>▣ Connect device</button><button onClick={() => setNotice("Share action will be connected in the native app.")}>↗ Share</button></div></div></div><p className="text-center text-[9px] tracking-[.3em] text-zinc-700">HIGH-TECH ENTERTAINMENT · MUSIC. BEATS. BEYOND.</p></div>}
    </div>
  </div>;
};