-- Align temporary audio staging with the existing HTE 100 MB upload limit.
UPDATE storage.buckets
SET file_size_limit = 104857600
WHERE id = 'hte-download-temp';
