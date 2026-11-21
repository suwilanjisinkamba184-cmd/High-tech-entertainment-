-- HTE accepts FLAC uploads in Founder Studio, so private temporary delivery must allow them.
UPDATE storage.buckets
SET allowed_mime_types = ARRAY[
  'audio/mpeg','audio/mp3','audio/mp4','audio/x-m4a','audio/aac',
  'audio/wav','audio/x-wav','audio/ogg','audio/webm','audio/flac','audio/x-flac'
]::text[]
WHERE id = 'hte-download-temp';
