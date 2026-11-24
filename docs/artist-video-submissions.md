# Artist video submissions

## Endpoint
The repository's `submit-public-video` Supabase Edge Function is the source of truth for the request contract. Before using or changing it, inspect `supabase/functions/submit-public-video/index.ts` and the corresponding deployment workflow.

The documented request shape is:

```http
POST /functions/v1/submit-public-video
Authorization: Bearer <signed-in-user-access-token>
Content-Type: application/json
```

```json
{
  "url": "https://www.youtube.com/watch?v=VIDEO_ID",
  "rights_confirmed": true
}
```

The client must use the current signed-in user's access token and the public publishable key. Never expose the service-role key or YouTube API key in a browser or Android client.

## Rights and catalogue handling
A submitter must confirm that they own the content or are authorized to submit and share it. The Edge Function is responsible for session validation, URL validation, metadata retrieval and database writes. Confirm the current implementation's moderation/publication behavior before describing a submission as immediately public or approved.

YouTube's watch URL is for official YouTube playback. Do not copy, scrape, rehost or offer a third-party YouTube video as an HTE downloadable file. An HTE download action is appropriate only for an original file HTE is authorized to distribute.

## Client behavior
On success, show a clear confirmation and refresh the shared video catalogue. Handle duplicate, invalid URL, private/unavailable video, authorization and provider errors with actionable user-facing messages. Never display raw stack traces or secret values.

## Verification
Test anonymous rejection, a valid signed-in submission, duplicate submission, an invalid URL, a private/unavailable video and the resulting catalogue visibility. Confirm the actual deployed Edge Function before reporting that submissions are live.
