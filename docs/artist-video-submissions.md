# Artist video submissions

The `submit-public-video` Supabase Edge Function lets any signed-in HTE user submit a public YouTube video, including a video hosted on a channel that is not owned by High-Tech Entertainment.

## Request

`POST /functions/v1/submit-public-video`

Headers:
- `Authorization: Bearer <signed-in-user-access-token>`
- `Content-Type: application/json`

Body:
```json
{
  "url": "https://www.youtube.com/watch?v=VIDEO_ID",
  "rights_confirmed": true
}
```

The submitter must confirm that they own the content or are authorized to submit and share it. The function verifies the user's Supabase session, validates the YouTube URL, retrieves public metadata through the YouTube Data API, rejects private/upcoming videos, and writes metadata into the shared `public.videos` table.

Submitted videos are immediately published in the shared catalogue. The original audiovisual file is never copied from YouTube, and `download_url` remains null. An HTE download link must only be added separately for an original file HTE is authorized to distribute.

Duplicate submissions return HTTP 409. The endpoint is JWT-protected; anonymous requests are rejected.

## Client integration

The website and Android client should call this endpoint with the current user's Supabase access token. After a successful response, refresh the shared `videos` query. Do not expose the service-role key or YouTube API key in either client.
