#!/usr/bin/env python3
"""Pull new Zambian music videos from YouTube into the HTE Supabase catalogue.

The public site reads the `videos` table from Supabase and renders any YouTube
URL as an embedded player (see website/src/components/PublicCatalogue.tsx and
assets/videos.js). Inserting a row here is therefore all that is needed for a
new post to appear on the site -- no rebuild or redeploy required.

Environment variables:
  YOUTUBE_API_KEY            required  YouTube Data API v3 key
  SUPABASE_SERVICE_ROLE_KEY  required  bypasses RLS so the bot may insert
  SUPABASE_URL               optional  defaults to the site's project URL

Usage:
  python scripts/youtube_zambian_sync.py --dry-run
  python scripts/youtube_zambian_sync.py --limit 8
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "config" / "zambian-youtube.json"

YT_API = "https://www.googleapis.com/youtube/v3"
DEFAULT_SUPABASE_URL = "https://wqrxacjbsldbnoekbkbf.supabase.co"

ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
DURATION_RE = re.compile(r"^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$")

# Titles that are almost never a music release worth auto-posting.
TITLE_BLOCKLIST = (
    "interview",
    "podcast",
    "behind the scenes",
    "reaction",
    "tutorial",
    "vlog",
    "trailer",
    "teaser",
    "prank",
    "challenge",
)

NOISE_PATTERNS = (
    r"\s*[\(\[]\s*official\s*(music\s*)?(video|audio|visuali[sz]er|lyric\s*video)\s*[\)\]]",
    r"\s*[\(\[]\s*(official|hd|4k|explicit|clean)\s*[\)\]]",
    r"\s*[\(\[]\s*lyrics?\s*[\)\]]",
    r"\s*[\(\[]\s*audio\s*[\)\]]",
    r"\s*[\(\[]\s*visuali[sz]er\s*[\)\]]",
    r"\s*[\|\-]\s*official\s*(music\s*)?(video|audio)\s*$",
)

ARTIST_NOISE = (
    " - topic",
    "official",
    "vevo",
    "music",
    "entertainment",
    "records",
    "tv",
    "channel",
)


# --------------------------------------------------------------------------- #
# HTTP helpers
# --------------------------------------------------------------------------- #


def http_json(url: str, params: dict | None = None, headers: dict | None = None,
              retries: int = 3, timeout: int = 30) -> dict:
    """GET a JSON document with a small retry/backoff loop."""
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    last_error: Exception | None = None
    for attempt in range(retries):
        request = urllib.request.Request(url, headers=headers or {})
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", "replace")
            # 4xx other than rate limiting will not fix themselves.
            if error.code < 500 and error.code != 429:
                raise RuntimeError(f"HTTP {error.code} for {url.split('?')[0]}: {body[:400]}") from error
            last_error = RuntimeError(f"HTTP {error.code}: {body[:200]}")
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
        if attempt < retries - 1:
            time.sleep(2 ** attempt)
    raise RuntimeError(f"Request failed after {retries} attempts: {last_error}")


def yt_get(path: str, params: dict, api_key: str) -> dict:
    payload = dict(params)
    payload["key"] = api_key
    try:
        return http_json(f"{YT_API}/{path}", payload)
    except RuntimeError as error:
        message = str(error)
        if "quotaExceeded" in message:
            raise SystemExit(
                "YouTube API daily quota exhausted. The free tier allows 10,000 units/day; "
                "reduce `queries` in config/zambian-youtube.json or wait for the reset (midnight PT)."
            ) from error
        if "keyInvalid" in message or "API key not valid" in message:
            raise SystemExit(
                "YouTube rejected the API key. Check that YOUTUBE_API_KEY is correct and that "
                "'YouTube Data API v3' is enabled for that project in Google Cloud Console."
            ) from error
        raise


# --------------------------------------------------------------------------- #
# YouTube helpers
# --------------------------------------------------------------------------- #


def youtube_id(value: str | None) -> str:
    """Extract an 11-character YouTube video id from any common URL form."""
    if not value:
        return ""
    text = str(value).strip()
    if ID_RE.match(text):
        return text
    try:
        parsed = urllib.parse.urlparse(text)
    except ValueError:
        return ""
    host = (parsed.hostname or "").lower()
    if host.startswith("www."):
        host = host[4:]
    parts = [part for part in parsed.path.split("/") if part]
    candidate = ""
    if host == "youtu.be":
        candidate = parts[0] if parts else ""
    elif host in {"youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com"}:
        candidate = urllib.parse.parse_qs(parsed.query).get("v", [""])[0]
        if not candidate and parts and parts[0] in {"embed", "shorts", "live", "v"}:
            candidate = parts[1] if len(parts) > 1 else ""
    return candidate if ID_RE.match(candidate or "") else ""


def parse_duration(value: str | None) -> int:
    """Convert an ISO-8601 duration (PT3M42S) into seconds."""
    if not value:
        return 0
    match = DURATION_RE.match(value)
    if not match:
        return 0
    days, hours, minutes, seconds = (int(part or 0) for part in match.groups())
    return days * 86400 + hours * 3600 + minutes * 60 + seconds


def resolve_channel(handle_or_id: str, api_key: str) -> dict | None:
    """Resolve a channel handle (@name) or raw UC id to its uploads playlist."""
    value = handle_or_id.strip()
    if not value:
        return None
    params = {"part": "snippet,contentDetails"}
    if value.startswith("@"):
        params["forHandle"] = value
    elif value.startswith("UC") and len(value) == 24:
        params["id"] = value
    else:
        params["forHandle"] = value if value.startswith("@") else f"@{value}"
    try:
        payload = yt_get("channels", params, api_key)
    except RuntimeError as error:
        print(f"  ! could not resolve channel {value}: {error}", file=sys.stderr)
        return None
    items = payload.get("items") or []
    if not items:
        print(f"  ! channel not found: {value}", file=sys.stderr)
        return None
    item = items[0]
    uploads = (item.get("contentDetails", {}).get("relatedPlaylists", {}) or {}).get("uploads")
    if not uploads:
        print(f"  ! channel has no uploads playlist: {value}", file=sys.stderr)
        return None
    return {
        "id": item.get("id"),
        "title": item.get("snippet", {}).get("title", value),
        "uploads": uploads,
    }


def playlist_candidates(playlist_id: str, api_key: str, max_results: int) -> list[dict]:
    """Newest uploads from a channel's uploads playlist (1 quota unit per call)."""
    payload = yt_get(
        "playlistItems",
        {"part": "snippet,contentDetails", "playlistId": playlist_id, "maxResults": max_results},
        api_key,
    )
    results = []
    for item in payload.get("items") or []:
        snippet = item.get("snippet") or {}
        video_id = (item.get("contentDetails") or {}).get("videoId") or (snippet.get("resourceId") or {}).get("videoId")
        if not video_id:
            continue
        results.append(
            {
                "id": video_id,
                "title": snippet.get("title", ""),
                "channel": snippet.get("videoOwnerChannelTitle") or snippet.get("channelTitle") or "",
                "published": snippet.get("publishedAt") or snippet.get("videoPublishedAt") or "",
                "description": snippet.get("description", ""),
                "origin": "channel",
            }
        )
    return results


def search_candidates(query: str, api_key: str, published_after: str,
                      max_results: int, region: str) -> list[dict]:
    """Keyword discovery (100 quota units per call -- keep the query list short)."""
    payload = yt_get(
        "search",
        {
            "part": "snippet",
            "q": query,
            "type": "video",
            "order": "date",
            "maxResults": max_results,
            "videoCategoryId": "10",  # Music
            "regionCode": region,
            "relevanceLanguage": "en",
            "publishedAfter": published_after,
        },
        api_key,
    )
    results = []
    for item in payload.get("items") or []:
        video_id = (item.get("id") or {}).get("videoId")
        if not video_id:
            continue
        snippet = item.get("snippet") or {}
        results.append(
            {
                "id": video_id,
                "title": snippet.get("title", ""),
                "channel": snippet.get("channelTitle", ""),
                "published": snippet.get("publishedAt", ""),
                "description": snippet.get("description", ""),
                "origin": "search",
            }
        )
    return results


def video_durations(video_ids: list[str], api_key: str) -> dict[str, int]:
    """Batch-fetch durations so Shorts and hour-long mixes can be filtered out."""
    durations: dict[str, int] = {}
    for start in range(0, len(video_ids), 50):
        chunk = video_ids[start:start + 50]
        payload = yt_get(
            "videos",
            {"part": "contentDetails", "id": ",".join(chunk), "maxResults": 50},
            api_key,
        )
        for item in payload.get("items") or []:
            durations[item.get("id", "")] = parse_duration(
                (item.get("contentDetails") or {}).get("duration")
            )
    return durations


# --------------------------------------------------------------------------- #
# Text cleanup
# --------------------------------------------------------------------------- #


def clean_title(raw: str) -> str:
    title = re.sub(r"\s+", " ", str(raw or "")).strip()
    for pattern in NOISE_PATTERNS:
        title = re.sub(pattern, "", title, flags=re.IGNORECASE)
    title = title.strip(" -|\u2013\u2014")
    return title[:180] or str(raw or "").strip()[:180]


def clean_artist(raw: str) -> str:
    artist = re.sub(r"\s+", " ", str(raw or "")).strip()
    lowered = artist.lower()
    for noise in ARTIST_NOISE:
        if lowered.endswith(noise):
            artist = artist[: -len(noise)].strip(" -|\u2013\u2014")
            lowered = artist.lower()
    return artist[:120]


def build_description(candidate: dict, published: datetime | None) -> str:
    parts = ["New Zambian release"]
    if published:
        parts.append(f"released {published.strftime('%d %B %Y')}")
    channel = clean_artist(candidate.get("channel", ""))
    if channel:
        parts.append(f"via {channel}")
    return " \u2022 ".join(parts)


# --------------------------------------------------------------------------- #
# Supabase helpers
# --------------------------------------------------------------------------- #


def supabase_headers(service_key: str) -> dict:
    return {
        "apikey": service_key,
        "Authorization": f"Bearer {service_key}",
        "Content-Type": "application/json",
    }


def existing_video_ids(base_url: str, service_key: str) -> set[str]:
    """Read every catalogue row and normalise its URL to a YouTube id."""
    ids: set[str] = set()
    offset = 0
    page = 1000
    while True:
        url = f"{base_url}/rest/v1/videos?select=id,video_url&limit={page}&offset={offset}"
        rows = http_json(url, headers=supabase_headers(service_key))
        if not isinstance(rows, list):
            raise RuntimeError(f"Unexpected Supabase response: {str(rows)[:300]}")
        for row in rows:
            found = youtube_id(row.get("video_url"))
            if found:
                ids.add(found)
        if len(rows) < page:
            break
        offset += page
    return ids


def curated_video_ids() -> set[str]:
    """YouTube ids hardcoded in the site's curated featured list.

    assets/videos.js ships a hand-picked `featured` array that is merged with the
    Supabase rows at render time. Seeding the de-dupe set from it stops the bot
    from inserting a second row for a video a human already curated.
    """
    path = ROOT / "assets" / "videos.js"
    if not path.exists():
        return set()
    text = path.read_text(encoding="utf-8", errors="replace")
    ids: set[str] = set()
    for match in re.finditer(r"youtube\.com/watch\?v=([A-Za-z0-9_-]{11})", text):
        ids.add(match.group(1))
    for match in re.finditer(r"youtu\.be/([A-Za-z0-9_-]{11})", text):
        ids.add(match.group(1))
    return ids


def supabase_insert(base_url: str, service_key: str, rows: list[dict]) -> list[dict]:
    if not rows:
        return []
    request = urllib.request.Request(
        f"{base_url}/rest/v1/videos",
        data=json.dumps(rows).encode("utf-8"),
        headers={**supabase_headers(service_key), "Prefer": "return=representation"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.loads(response.read().decode("utf-8") or "[]")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")
        if error.code in (401, 403):
            raise SystemExit(
                "Supabase rejected the write. SUPABASE_SERVICE_ROLE_KEY must be the service_role "
                "key (not the publishable/anon key) because the videos table is protected by RLS. "
                f"Server said: {body[:300]}"
            ) from error
        raise RuntimeError(f"Supabase insert failed (HTTP {error.code}): {body[:400]}") from error


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #


def load_config() -> dict:
    if not CONFIG_PATH.exists():
        raise SystemExit(f"Missing config file: {CONFIG_PATH}")
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync new Zambian music videos into HTE.")
    parser.add_argument("--dry-run", action="store_true", help="Report what would be posted without writing.")
    parser.add_argument("--limit", type=int, default=None, help="Override max posts for this run.")
    parser.add_argument("--draft", action="store_true", help="Insert as unpublished drafts for review.")
    args = parser.parse_args()

    config = load_config()
    api_key = os.environ.get("YOUTUBE_API_KEY", "").strip()
    service_key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
    base_url = (os.environ.get("SUPABASE_URL") or config.get("supabase_url") or DEFAULT_SUPABASE_URL).rstrip("/")

    if not api_key:
        raise SystemExit("YOUTUBE_API_KEY is not set. See docs/YOUTUBE-AUTOMATION.md for how to create one.")
    if not service_key and not args.dry_run:
        raise SystemExit("SUPABASE_SERVICE_ROLE_KEY is not set. See docs/YOUTUBE-AUTOMATION.md.")

    max_posts = args.limit if args.limit is not None else int(config.get("max_posts_per_run", 8))
    publish = bool(config.get("publish_immediately", True)) and not args.draft
    min_seconds = int(config.get("min_duration_seconds", 60))
    max_seconds = int(config.get("max_duration_seconds", 900))
    lookback_days = int(config.get("lookback_days", 21))
    region = config.get("region_code", "ZM")

    now = datetime.now(timezone.utc)
    published_after = (now - timedelta(days=lookback_days)).strftime("%Y-%m-%dT%H:%M:%SZ")

    print(f"HTE YouTube sync \u2014 {now.strftime('%Y-%m-%d %H:%M UTC')}")
    print(f"  lookback: {lookback_days}d   max posts: {max_posts}   publish: {publish}   dry-run: {args.dry_run}")

    # 1. What is already on the site?
    known: set[str] = curated_video_ids()
    if known:
        print(f"  {len(known)} video(s) already curated in assets/videos.js")
    if service_key:
        known |= existing_video_ids(base_url, service_key)
        print(f"  catalogue already holds {len(known)} YouTube video(s) in total")
    else:
        print("  (dry run without Supabase key \u2014 cannot check catalogue duplicates)")

    # 2. Gather candidates.
    candidates: list[dict] = []
    seen: set[str] = set()

    def add(items: list[dict]) -> None:
        for item in items:
            if item["id"] in seen:
                continue
            seen.add(item["id"])
            candidates.append(item)

    for handle in config.get("channels", []):
        channel = resolve_channel(handle, api_key)
        if not channel:
            continue
        print(f"  channel {channel['title']} ({handle})")
        add(playlist_candidates(channel["uploads"], api_key, int(config.get("per_channel_results", 10))))

    for query in config.get("queries", []):
        print(f"  search  {query!r}")
        add(search_candidates(query, api_key, published_after, int(config.get("per_query_results", 10)), region))

    print(f"  {len(candidates)} unique candidate video(s) found")

    # 3. Filter.
    fresh = [c for c in candidates if c["id"] not in known]
    print(f"  {len(fresh)} not yet on the site")

    durations = video_durations([c["id"] for c in fresh], api_key) if fresh else {}

    accepted: list[dict] = []
    for candidate in fresh:
        title_lower = candidate["title"].lower()
        if any(word in title_lower for word in TITLE_BLOCKLIST):
            print(f"    skip (not a release): {candidate['title'][:70]}")
            continue
        seconds = durations.get(candidate["id"], 0)
        if seconds and seconds < min_seconds:
            print(f"    skip (too short, {seconds}s): {candidate['title'][:70]}")
            continue
        if seconds and seconds > max_seconds:
            print(f"    skip (too long, {seconds}s): {candidate['title'][:70]}")
            continue
        accepted.append(candidate)

    # Newest first, then cap.
    accepted.sort(key=lambda c: c.get("published") or "", reverse=True)
    accepted = accepted[:max_posts]

    if not accepted:
        print("Nothing new to post. Done.")
        return 0

    # 4. Build rows.
    rows = []
    for candidate in accepted:
        published = None
        if candidate.get("published"):
            try:
                published = datetime.fromisoformat(candidate["published"].replace("Z", "+00:00"))
            except ValueError:
                published = None
        video_id = candidate["id"]
        rows.append(
            {
                "title": clean_title(candidate["title"]),
                "artist": clean_artist(candidate.get("channel", "")),
                "description": build_description(candidate, published),
                "video_url": f"https://www.youtube.com/watch?v={video_id}",
                "thumbnail_url": f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg",
                "is_published": publish,
            }
        )

    print(f"\nReady to post {len(rows)} video(s):")
    for row in rows:
        print(f"  \u2022 {row['title']} \u2014 {row['artist']}  ({row['video_url']})")

    if args.dry_run:
        print("\nDry run: nothing written.")
        return 0

    inserted = supabase_insert(base_url, service_key, rows)
    print(f"\nInserted {len(inserted)} row(s) into Supabase. The site will show them on next load.")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as handle:
            handle.write(f"## HTE YouTube sync \u2014 {now.strftime('%Y-%m-%d %H:%M UTC')}\n\n")
            handle.write(f"Posted **{len(inserted)}** new video(s).\n\n")
            for row in rows:
                handle.write(f"- [{row['title']}]({row['video_url']}) \u2014 {row['artist']}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
