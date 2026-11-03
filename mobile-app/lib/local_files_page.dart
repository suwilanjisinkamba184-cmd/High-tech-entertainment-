import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'playback_engine.dart';
import 'video_player_page.dart';


/// Download a direct media URL into the app's persistent offline library.
/// The streamed response is written to disk (not buffered into RAM), which is
/// important on low-memory Android Go devices.
Future<String> downloadRemoteMedia({
  required String url,
  required String title,
  required String kind,
}) async {
  final uri = Uri.tryParse(url.trim());
  if (uri == null ||
      (uri.scheme != 'https' && uri.scheme != 'http') ||
      uri.host.isEmpty) {
    throw const FormatException('This is not a direct downloadable media URL.');
  }

  final client = HttpClient();
  try {
    final request = await client.getUrl(uri);
    request.headers.set(HttpHeaders.acceptHeader, '*/*');
    final response = await request.close();
    if (response.statusCode < 200 || response.statusCode >= 300) {
      await response.drain<void>();
      throw HttpException('Download failed (HTTP ${response.statusCode}).', uri: uri);
    }

    final directory = await getApplicationSupportDirectory();
    final mediaDirectory = Directory('${directory.path}/hte_media');
    await mediaDirectory.create(recursive: true);

    var extension = uri.pathSegments.isNotEmpty
        ? uri.pathSegments.last.split('.').last.toLowerCase()
        : '';
    if (!RegExp(r'^[a-z0-9]{2,5}
  const LocalFilesTab({super.key});

  @override
  State<LocalFilesTab> createState() => _LocalFilesTabState();
}

class _LocalFilesTabState extends State<LocalFilesTab> {
  static const _savedFilesKey = 'hte_saved_local_media_v1';
  static const _audioExtensions = {
    'mp3', 'm4a', 'aac', 'wav', 'flac', 'ogg', 'opus', 'aiff'
  };
  static const _videoExtensions = {
    'mp4', 'm4v', 'mov', 'mkv', 'webm', '3gp'
  };

  List<PlatformFile> _files = [];
  bool _picking = false;

  @override
  void initState() {
    super.initState();
    _restoreSavedFiles();
  }

  Future<void> _restoreSavedFiles() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getStringList(_savedFilesKey) ?? [];
      final restored = <PlatformFile>[];

      for (final value in saved) {
        final item = jsonDecode(value) as Map<String, dynamic>;
        final path = item['path']?.toString() ?? '';
        final file = File(path);
        if (path.isNotEmpty && await file.exists()) {
          restored.add(PlatformFile(
            name: item['name']?.toString() ?? file.uri.pathSegments.last,
            size: await file.length(),
            path: path,
            extension: item['extension']?.toString(),
          ));
        }
      }

      if (mounted) setState(() => _files = restored);
      await _saveFileIndex(restored);
    } catch (error) {
      debugPrint('HTE local media restore failed: $error');
    }
  }

  Future<void> _saveFileIndex(List<PlatformFile> files) async {
    final prefs = await SharedPreferences.getInstance();
    final values = files
        .where((file) => file.path != null)
        .map((file) => jsonEncode({
              'path': file.path,
              'name': file.name,
              'extension': file.extension,
            }))
        .toList();
    await prefs.setStringList(_savedFilesKey, values);
  }

  Future<void> _browse() async {
    setState(() => _picking = true);
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: [..._audioExtensions, ..._videoExtensions],
        allowMultiple: true,
        withData: false,
      );

      if (result == null || !mounted) return;

      final directory = await getApplicationSupportDirectory();
      final mediaDirectory = Directory('${directory.path}/hte_media');
      await mediaDirectory.create(recursive: true);

      final persisted = <PlatformFile>[..._files];
      for (final selected in result.files) {
        final sourcePath = selected.path;
        if (sourcePath == null) continue;

        final extension = selected.extension?.toLowerCase();
        if (!_audioExtensions.contains(extension) &&
            !_videoExtensions.contains(extension)) {
          continue;
        }

        try {
          final source = File(sourcePath);
          if (!await source.exists()) continue;

          final safeName = selected.name.replaceAll(
            RegExp(r'[^A-Za-z0-9._-]'),
            '_',
          );
          final destination = File(
            '${mediaDirectory.path}/${DateTime.now().microsecondsSinceEpoch}_$safeName',
          );
          final copied = await source.copy(destination.path);

          persisted.removeWhere((file) => file.name == selected.name);
          persisted.add(PlatformFile(
            name: selected.name,
            size: await copied.length(),
            path: copied.path,
            extension: extension,
          ));
        } catch (error) {
          debugPrint('Could not save selected media ${selected.name}: $error');
        }
      }

      if (mounted) setState(() => _files = persisted);
      await _saveFileIndex(persisted);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not import local media: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _picking = false);
    }
  }

  bool _isAudio(PlatformFile file) =>
      _audioExtensions.contains(file.extension?.toLowerCase());

  bool _isVideo(PlatformFile file) =>
      _videoExtensions.contains(file.extension?.toLowerCase());

  Future<void> _playAudio(PlatformFile selected) async {
    final audio = _files.where(_isAudio).toList();
    final queue = audio
        .map((file) => <String, dynamic>{
              'id': file.path,
              'local_path': file.path,
              'title': file.name,
              'artist': 'On this device',
            })
        .toList();

    final index =
        queue.indexWhere((item) => item['local_path'] == selected.path);
    if (index < 0) return;

    try {
      await playback.playTrack(queue[index], queue);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Local playback failed: $error')),
        );
      }
    }
  }

  void _playVideo(PlatformFile file) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => HteVideoPlayerPage(
        url: '',
        filePath: file.path,
        title: file.name,
      ),
    ));
  }

  Future<void> _removeFile(PlatformFile file) async {
    final path = file.path;
    if (path != null) {
      try {
        final stored = File(path);
        if (await stored.exists()) await stored.delete();
      } catch (error) {
        debugPrint('Could not remove local media copy: $error');
      }
    }

    final updated = _files.where((item) => item.path != path).toList();
    if (mounted) setState(() => _files = updated);
    await _saveFileIndex(updated);
  }

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    'Offline media library',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                ),
                FilledButton.icon(
                  onPressed: _picking ? null : _browse,
                  icon: const Icon(Icons.folder_open),
                  label: Text(_picking ? 'Importing…' : 'Import files'),
                ),
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 18, vertical: 4),
            child: Text(
              'Imported music and videos are copied into High-Tech Entertainment storage and remain available offline, even after restarting the app.',
              style: TextStyle(color: Colors.white60, fontSize: 12),
            ),
          ),
          Expanded(
            child: _files.isEmpty
                ? const Center(
                    child: Text(
                      'Your offline library is empty. Tap Import files to add music or videos from your phone.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white60),
                    ),
                  )
                : ListView.builder(
                    itemCount: _files.length,
                    itemBuilder: (context, index) {
                      final file = _files[index];
                      final audio = _isAudio(file);
                      final video = _isVideo(file);
                      return Card(
                        child: ListTile(
                          leading: Icon(
                            audio ? Icons.music_note : Icons.video_file,
                            color: const Color(0xFFD9B96E),
                          ),
                          title: Text(
                            file.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            '${audio ? 'Audio' : video ? 'Video' : 'Media'} • ${(file.size / (1024 * 1024)).toStringAsFixed(1)} MB • Offline',
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: audio ? 'Play audio' : 'Play video',
                                icon: const Icon(
                                  Icons.play_circle_fill,
                                  color: Color(0xFFD9B96E),
                                  size: 30,
                                ),
                                onPressed: audio
                                    ? () => _playAudio(file)
                                    : video
                                        ? () => _playVideo(file)
                                        : null,
                              ),
                              IconButton(
                                tooltip: 'Remove from offline library',
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () => _removeFile(file),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      );
}
).hasMatch(extension)) {
      final mime = response.headers.contentType?.mimeType ?? '';
      extension = mime == 'video/mp4' ? 'mp4' : mime == 'audio/mpeg' ? 'mp3' : kind;
    }
    final safeTitle = title.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_');
    final file = File(
      '${mediaDirectory.path}/${DateTime.now().microsecondsSinceEpoch}_$safeTitle.$extension',
    );
    await response.pipe(file.openWrite());

    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getStringList('hte_saved_local_media_v1') ?? [];
    saved.add(jsonEncode({
      'path': file.path,
      'name': '$title.$extension',
      'extension': extension,
    }));
    await prefs.setStringList('hte_saved_local_media_v1', saved);
    return file.path;
  } finally {
    client.close(force: true);
  }
}

class LocalFilesTab extends StatefulWidget {
  const LocalFilesTab({super.key});

  @override
  State<LocalFilesTab> createState() => _LocalFilesTabState();
}

class _LocalFilesTabState extends State<LocalFilesTab> {
  static const _savedFilesKey = 'hte_saved_local_media_v1';
  static const _audioExtensions = {
    'mp3', 'm4a', 'aac', 'wav', 'flac', 'ogg', 'opus', 'aiff'
  };
  static const _videoExtensions = {
    'mp4', 'm4v', 'mov', 'mkv', 'webm', '3gp'
  };

  List<PlatformFile> _files = [];
  bool _picking = false;

  @override
  void initState() {
    super.initState();
    _restoreSavedFiles();
  }

  Future<void> _restoreSavedFiles() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getStringList(_savedFilesKey) ?? [];
      final restored = <PlatformFile>[];

      for (final value in saved) {
        final item = jsonDecode(value) as Map<String, dynamic>;
        final path = item['path']?.toString() ?? '';
        final file = File(path);
        if (path.isNotEmpty && await file.exists()) {
          restored.add(PlatformFile(
            name: item['name']?.toString() ?? file.uri.pathSegments.last,
            size: await file.length(),
            path: path,
            extension: item['extension']?.toString(),
          ));
        }
      }

      if (mounted) setState(() => _files = restored);
      await _saveFileIndex(restored);
    } catch (error) {
      debugPrint('HTE local media restore failed: $error');
    }
  }

  Future<void> _saveFileIndex(List<PlatformFile> files) async {
    final prefs = await SharedPreferences.getInstance();
    final values = files
        .where((file) => file.path != null)
        .map((file) => jsonEncode({
              'path': file.path,
              'name': file.name,
              'extension': file.extension,
            }))
        .toList();
    await prefs.setStringList(_savedFilesKey, values);
  }

  Future<void> _browse() async {
    setState(() => _picking = true);
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: [..._audioExtensions, ..._videoExtensions],
        allowMultiple: true,
        withData: false,
      );

      if (result == null || !mounted) return;

      final directory = await getApplicationSupportDirectory();
      final mediaDirectory = Directory('${directory.path}/hte_media');
      await mediaDirectory.create(recursive: true);

      final persisted = <PlatformFile>[..._files];
      for (final selected in result.files) {
        final sourcePath = selected.path;
        if (sourcePath == null) continue;

        final extension = selected.extension?.toLowerCase();
        if (!_audioExtensions.contains(extension) &&
            !_videoExtensions.contains(extension)) {
          continue;
        }

        try {
          final source = File(sourcePath);
          if (!await source.exists()) continue;

          final safeName = selected.name.replaceAll(
            RegExp(r'[^A-Za-z0-9._-]'),
            '_',
          );
          final destination = File(
            '${mediaDirectory.path}/${DateTime.now().microsecondsSinceEpoch}_$safeName',
          );
          final copied = await source.copy(destination.path);

          persisted.removeWhere((file) => file.name == selected.name);
          persisted.add(PlatformFile(
            name: selected.name,
            size: await copied.length(),
            path: copied.path,
            extension: extension,
          ));
        } catch (error) {
          debugPrint('Could not save selected media ${selected.name}: $error');
        }
      }

      if (mounted) setState(() => _files = persisted);
      await _saveFileIndex(persisted);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not import local media: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _picking = false);
    }
  }

  bool _isAudio(PlatformFile file) =>
      _audioExtensions.contains(file.extension?.toLowerCase());

  bool _isVideo(PlatformFile file) =>
      _videoExtensions.contains(file.extension?.toLowerCase());

  Future<void> _playAudio(PlatformFile selected) async {
    final audio = _files.where(_isAudio).toList();
    final queue = audio
        .map((file) => <String, dynamic>{
              'id': file.path,
              'local_path': file.path,
              'title': file.name,
              'artist': 'On this device',
            })
        .toList();

    final index =
        queue.indexWhere((item) => item['local_path'] == selected.path);
    if (index < 0) return;

    try {
      await playback.playTrack(queue[index], queue);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Local playback failed: $error')),
        );
      }
    }
  }

  void _playVideo(PlatformFile file) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => HteVideoPlayerPage(
        url: '',
        filePath: file.path,
        title: file.name,
      ),
    ));
  }

  Future<void> _removeFile(PlatformFile file) async {
    final path = file.path;
    if (path != null) {
      try {
        final stored = File(path);
        if (await stored.exists()) await stored.delete();
      } catch (error) {
        debugPrint('Could not remove local media copy: $error');
      }
    }

    final updated = _files.where((item) => item.path != path).toList();
    if (mounted) setState(() => _files = updated);
    await _saveFileIndex(updated);
  }

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    'Offline media library',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                ),
                FilledButton.icon(
                  onPressed: _picking ? null : _browse,
                  icon: const Icon(Icons.folder_open),
                  label: Text(_picking ? 'Importing…' : 'Import files'),
                ),
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 18, vertical: 4),
            child: Text(
              'Imported music and videos are copied into High-Tech Entertainment storage and remain available offline, even after restarting the app.',
              style: TextStyle(color: Colors.white60, fontSize: 12),
            ),
          ),
          Expanded(
            child: _files.isEmpty
                ? const Center(
                    child: Text(
                      'Your offline library is empty. Tap Import files to add music or videos from your phone.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white60),
                    ),
                  )
                : ListView.builder(
                    itemCount: _files.length,
                    itemBuilder: (context, index) {
                      final file = _files[index];
                      final audio = _isAudio(file);
                      final video = _isVideo(file);
                      return Card(
                        child: ListTile(
                          leading: Icon(
                            audio ? Icons.music_note : Icons.video_file,
                            color: const Color(0xFFD9B96E),
                          ),
                          title: Text(
                            file.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            '${audio ? 'Audio' : video ? 'Video' : 'Media'} • ${(file.size / (1024 * 1024)).toStringAsFixed(1)} MB • Offline',
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: audio ? 'Play audio' : 'Play video',
                                icon: const Icon(
                                  Icons.play_circle_fill,
                                  color: Color(0xFFD9B96E),
                                  size: 30,
                                ),
                                onPressed: audio
                                    ? () => _playAudio(file)
                                    : video
                                        ? () => _playVideo(file)
                                        : null,
                              ),
                              IconButton(
                                tooltip: 'Remove from offline library',
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () => _removeFile(file),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      );
}
