import React, { useMemo, useState } from "react";
const gold = "#d7b66f";
const menu = [{
  label: "Overview",
  icon: "◫"
}, {
  label: "Music uploads",
  icon: "♫"
}, {
  label: "Artists",
  icon: "♙"
}, {
  label: "Releases",
  icon: "▣"
}, {
  label: "Artwork",
  icon: "▧"
}, {
  label: "Users",
  icon: "◎"
}, {
  label: "Analytics",
  icon: "▥"
}, {
  label: "Activity",
  icon: "◷"
}];
const demoTracks = [{
  title: "Golden Hour",
  artist: "L Junior ZM",
  status: "Published",
  date: "Today"
}, {
  title: "Midnight Motion",
  artist: "DJ Maneuvera",
  status: "Draft",
  date: "Yesterday"
}, {
  title: "Beyond The Sound",
  artist: "OJ Junior",
  status: "Published",
  date: "28 Oct"
}, {
  title: "No Limits",
  artist: "L Junior ZM",
  status: "Review",
  date: "26 Oct"
}];
const goldText = "text-[#d7b66f]";
function Metric({
  label,
  value,
  detail,
  icon
}: {
  label: string;
  value: string;
  detail: string;
  icon: string;
}) {
  return <div className="rounded-2xl border border-white/[.08] bg-[#141517] p-4"><div className="flex items-center justify-between"><span className="text-xs text-zinc-400">{label}</span><span className={goldText}>{icon}</span></div><p className="mt-3 text-3xl font-semibold tracking-tight">{value}</p><p className="mt-1 text-[10px] text-zinc-500">{detail}</p></div>;
}
function Status({
  value
}: {
  value: string;
}) {
  const cls = value === "Published" ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300" : value === "Draft" ? "border-zinc-500/30 bg-zinc-500/10 text-zinc-300" : "border-amber-500/30 bg-amber-500/10 text-amber-300";
  return <span className={`rounded-full border px-2.5 py-1 text-[10px] ${cls}`}>{value}</span>;
}
export const HTAdminPrivateAndroidAppPrototype = () => {
  const [active, setActive] = useState("Overview");
  const [tracks, setTracks] = useState(demoTracks);
  const [search, setSearch] = useState("");
  const [notice, setNotice] = useState("");
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadArtist, setUploadArtist] = useState("L Junior ZM");
  const [uploadGenre, setUploadGenre] = useState("Afrobeat");
  const [uploadStatus, setUploadStatus] = useState("Draft");
  const [period, setPeriod] = useState("7 days");
  const filtered = useMemo(() => tracks.filter(t => (t.title + " " + t.artist + " " + t.status).toLowerCase().includes(search.toLowerCase())), [tracks, search]);
  const addTrack = () => {
    if (!uploadTitle.trim()) {
      setNotice("Enter a track title before saving.");
      return;
    }
    setTracks(old => [{
      title: uploadTitle.trim(),
      artist: uploadArtist,
      status: uploadStatus,
      date: "Just now"
    }, ...old]);
    setUploadTitle("");
    setNotice("Demo record added. Connect Supabase to save it permanently.");
    setActive("Music uploads");
  };
  const header = (sub: string, title: string, desc: string) => <div className="mb-6"><p className="text-[10px] tracking-[.24em] text-[#d7b66f]">{sub}</p><h1 className="mt-2 text-2xl font-semibold">{title}</h1><p className="mt-2 text-xs leading-relaxed text-zinc-400">{desc}</p></div>;
  const action = (label: string, fn: () => void) => <button onClick={fn} className="rounded-xl bg-[#d7b66f] px-4 py-2.5 text-xs font-bold text-[#17130b]">{label}</button>;
  const render = () => {
    if (active === "Overview") return <>
      {header("PRIVATE FOUNDER STUDIO", "Command centre", "Manage the High-Tech Entertainment catalogue, creators and platform operations.")}
      <div className="grid grid-cols-2 gap-3"><Metric label="Total tracks" value={String(tracks.length).padStart(2, "0")} detail="Catalogue entries" icon="♫" /><Metric label="Published" value={String(tracks.filter(t => t.status === "Published").length).padStart(2, "0")} detail="Live catalogue" icon="◉" /><Metric label="Artists" value="04" detail="Demo artist records" icon="♙" /><Metric label="Pending review" value={String(tracks.filter(t => t.status === "Review").length).padStart(2, "0")} detail="Awaiting approval" icon="◷" /></div>
      <div className="mt-6 rounded-2xl border border-[#d7b66f]/25 bg-gradient-to-br from-[#332714] to-[#141414] p-4"><p className="text-[10px] tracking-[.2em] text-[#e4c982]">FOUNDER ACCESS</p><h2 className="mt-2 text-xl font-semibold">Your studio. Your catalogue.</h2><p className="mt-2 text-xs leading-relaxed text-zinc-300">Upload music, prepare releases and control what becomes visible in HT Music.</p><div className="mt-4 flex gap-2">{action("＋ Upload music", () => setActive("Music uploads"))}<button onClick={() => setActive("Releases")} className="rounded-xl border border-white/15 px-4 py-2.5 text-xs">Manage releases</button></div></div>
      <div className="mt-6"><div className="mb-3 flex items-center justify-between"><h3 className="font-semibold">Recent catalogue activity</h3><button onClick={() => setActive("Activity")} className="text-xs text-[#d7b66f]">View all →</button></div>{tracks.slice(0, 3).map(t => <div key={t.title} className="mb-2 flex items-center gap-3 rounded-xl border border-white/[.07] bg-white/[.025] p-3"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-[#b38a42] to-[#21180a] font-black text-black">HT</div><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{t.title}</p><p className="text-[10px] text-zinc-500">{t.artist} · {t.date}</p></div><Status value={t.status} /></div>)}</div>
    </>;
    if (active === "Music uploads") return <>{header("CATALOGUE MANAGEMENT", "Music uploads", "Prepare a track, assign its artist and choose whether it is a draft or ready for publishing.")}<div className="rounded-2xl border border-white/10 bg-[#141517] p-4"><label className="text-xs text-zinc-400">Track title</label><input value={uploadTitle} onChange={e => setUploadTitle(e.target.value)} placeholder="Enter track title" className="mt-2 w-full rounded-xl border border-white/10 bg-black/30 px-3 py-3 text-sm outline-none focus:border-[#d7b66f]" /><label className="mt-4 block text-xs text-zinc-400">Artist</label><select value={uploadArtist} onChange={e => setUploadArtist(e.target.value)} className="mt-2 w-full rounded-xl border border-white/10 bg-[#0b0c0e] px-3 py-3 text-sm"><option>L Junior ZM</option><option>DJ Maneuvera</option><option>OJ Junior</option><option>The Golden Vocals</option></select><label className="mt-4 block text-xs text-zinc-400">Genre</label><select value={uploadGenre} onChange={e => setUploadGenre(e.target.value)} className="mt-2 w-full rounded-xl border border-white/10 bg-[#0b0c0e] px-3 py-3 text-sm">{["Afrobeat", "Dancehall", "Drill", "Gospel", "Other"].map(x => <option key={x}>{x}</option>)}</select><label className="mt-4 block text-xs text-zinc-400">Publishing status</label><select value={uploadStatus} onChange={e => setUploadStatus(e.target.value)} className="mt-2 w-full rounded-xl border border-white/10 bg-[#0b0c0e] px-3 py-3 text-sm"><option>Draft</option><option>Review</option><option>Published</option></select><div className="mt-5 rounded-xl border border-dashed border-white/20 p-5 text-center"><span className="text-2xl text-[#d7b66f]">⇧</span><p className="mt-2 text-sm font-semibold">Audio and artwork upload</p><p className="mt-1 text-[10px] text-zinc-500">Storage picker will connect to the private Supabase bucket.</p></div><button onClick={addTrack} className="mt-5 w-full rounded-xl bg-[#d7b66f] py-3 text-sm font-bold text-black">Save track record</button></div></>;
    if (active === "Artists") return <>{header("CREATOR DIRECTORY", "Artists", "Manage artist profiles, credits and catalogue relationships.")}{["L Junior ZM · Producer / CEO", "DJ Maneuvera · DJ", "OJ Junior · Artist", "The Golden Vocals · Choir"].map((a, i) => <div key={a} className="mb-2 flex items-center gap-3 rounded-xl border border-white/10 bg-[#141517] p-4"><div className="flex h-11 w-11 items-center justify-center rounded-full border border-[#d7b66f]/40 bg-[#302414] font-bold text-[#e3c47e]">{a.split(" ").slice(0, 2).map(x => x[0]).join("")}</div><span className="flex-1 text-sm font-medium">{a}</span><button onClick={() => setNotice("Artist editor will save profile changes through Supabase.")} className="text-xs text-[#d7b66f]">Edit</button></div>)}{action("＋ Add artist", () => setNotice("New artist form will be connected to Supabase."))}</>;
    if (active === "Releases") return <>{header("RELEASE MANAGEMENT", "Releases", "Create release campaigns, attach artwork and control publication dates.")}{tracks.map(t => <div key={t.title} className="mb-2 flex items-center gap-3 rounded-xl border border-white/10 bg-[#141517] p-3"><div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-[#b58b43] to-[#17120b] font-black text-black">HT</div><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{t.title}</p><p className="text-xs text-zinc-500">{t.artist}</p></div><Status value={t.status} /></div>)}{action("＋ Create release", () => setNotice("Release creation will be wired to the releases table."))}</>;
    if (active === "Artwork") return <>{header("VISUAL IDENTITY", "Artwork library", "Manage cover art, artist images and promotional graphics.")}<div className="grid grid-cols-2 gap-3">{["Release covers", "Artist portraits", "Promotional banners", "Video thumbnails"].map((x, i) => <button key={x} onClick={() => setNotice(x + " will connect to Supabase Storage.")} className="aspect-square rounded-2xl border border-white/10 bg-gradient-to-br from-[#4a3618] via-[#171719] to-black p-4 text-left"><span className="text-3xl text-[#d7b66f]">{["▧", "♙", "▤", "▷"][i]}</span><p className="mt-8 text-sm font-semibold">{x}</p><p className="mt-1 text-[10px] text-zinc-500">Manage assets →</p></button>)}</div></>;
    if (active === "Users") return <>{header("ACCOUNT OVERSIGHT", "Users", "Review account activity and access levels. Sensitive account actions require server-side authorization.")}<div className="rounded-2xl border border-white/10 bg-[#141517] p-4"><p className="text-sm font-semibold">User management</p><p className="mt-2 text-xs text-zinc-500">Connect the Supabase Auth admin endpoint to list users safely. Never expose the service-role key in this app.</p><button onClick={() => setNotice("User directory requires a protected server-side Edge Function.")} className="mt-4 rounded-xl border border-[#d7b66f]/40 px-4 py-2 text-xs text-[#d7b66f]">Open secure user manager</button></div></>;
    if (active === "Analytics") return <>{header("PLATFORM INSIGHTS", "Analytics", "Monitor listening activity, catalogue growth and audience engagement.")}<div className="mb-4 flex gap-2">{["7 days", "30 days", "90 days"].map(x => <button key={x} onClick={() => setPeriod(x)} className={`rounded-full border px-3 py-2 text-[10px] ${period === x ? "border-[#d7b66f] bg-[#d7b66f] text-black" : "border-white/10 text-zinc-400"}`}>{x}</button>)}</div><div className="grid grid-cols-2 gap-3"><Metric label="Track plays" value="—" detail="Awaiting event data" icon="♫" /><Metric label="Downloads" value="—" detail="Awaiting event data" icon="⇩" /><Metric label="Active listeners" value="—" detail={period} icon="◎" /><Metric label="Top release" value="—" detail="No analytics connected" icon="★" /></div><div className="mt-4 rounded-2xl border border-white/10 bg-[#141517] p-5"><p className="font-semibold">Listening trends</p><div className="mt-5 flex h-28 items-end gap-2">{[30, 52, 42, 75, 60, 88, 66, 95, 56, 78, 68, 100].map((v, i) => <div key={i} className="flex-1 rounded-t bg-gradient-to-t from-[#76551f] to-[#dfc17a]" style={{
            height: v + "%"
          }} />)}</div><p className="mt-3 text-[10px] text-zinc-500">Illustrative chart only — live metrics require event tracking.</p></div></>;
    if (active === "Activity") return <>{header("AUDIT TRAIL", "Platform activity", "Review recent publishing and management actions.")}{["Track catalogue updated", "Release publication pending", "Artist profile reviewed", "Artwork library opened"].map((x, i) => <div key={x} className="mb-2 flex gap-3 rounded-xl border border-white/10 bg-[#141517] p-4"><span className="text-[#d7b66f]">◷</span><div><p className="text-sm">{x}</p><p className="mt-1 text-[10px] text-zinc-500">{["Just now", "Today", "Yesterday", "28 Oct"][i]} · Demo activity</p></div></div>)}</>;
    return null;
  };
  return <div className="min-h-screen w-full bg-[#08090b] text-white flex justify-center font-sans"><div className="relative min-h-screen w-full max-w-[430px] overflow-hidden bg-[#0b0c0e] pb-24"><div className="pointer-events-none absolute inset-x-0 top-0 h-72 bg-[radial-gradient(ellipse_at_top,rgba(196,148,55,.16),transparent_68%)]" /><header className="relative flex items-center justify-between border-b border-white/[.07] px-5 py-5"><div className="flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-xl border border-[#d7b66f]/60 bg-gradient-to-br from-[#d9bd7a] to-[#39260c] font-black text-black">HT</div><div><p className="text-[9px] tracking-[.25em] text-[#d7b66f]">HIGH-TECH</p><p className="text-sm font-bold tracking-[.13em]">FOUNDER STUDIO</p></div></div><span className="rounded-full border border-[#d7b66f]/30 px-2.5 py-1 text-[9px] text-[#d7b66f]">PRIVATE</span></header><div className="relative flex"><aside className="hidden w-14 shrink-0 flex-col gap-2 border-r border-white/[.06] px-2 py-4 sm:flex">{menu.map(m => <button key={m.label} title={m.label} onClick={() => setActive(m.label)} className={`rounded-xl py-3 text-lg ${active === m.label ? "bg-[#d7b66f]/15 text-[#d7b66f]" : "text-zinc-500"}`}>{m.icon}</button>)}</aside><main className="min-w-0 flex-1 px-5 py-6">{render()}</main></div>{notice && <div className="fixed bottom-24 left-1/2 z-50 w-[90%] max-w-[390px] -translate-x-1/2 rounded-xl border border-[#d7b66f]/30 bg-[#201a0f] p-4 text-xs shadow-2xl"><div className="flex justify-between gap-3"><span>{notice}</span><button onClick={() => setNotice("")} className="text-[#d7b66f]">✕</button></div></div>}<nav className="fixed bottom-0 left-1/2 z-40 flex w-full max-w-[430px] -translate-x-1/2 gap-1 overflow-x-auto border-t border-white/[.08] bg-[#0d0e10]/95 px-2 pb-4 pt-2 backdrop-blur-xl sm:hidden">{menu.map(m => <button key={m.label} onClick={() => setActive(m.label)} className={`flex min-w-[66px] flex-col items-center gap-1 rounded-lg px-1 py-1 text-[8px] ${active === m.label ? "text-[#d7b66f]" : "text-zinc-500"}`}><span className="text-base">{m.icon}</span>{m.label}</button>)}</nav></div></div>;
};