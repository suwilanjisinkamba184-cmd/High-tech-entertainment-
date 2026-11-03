import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'playback_engine.dart';
import 'video_player_page.dart';

class LocalFilesTab extends StatefulWidget {
  const LocalFilesTab({super.key});
  @override
  State<LocalFilesTab> createState() => _LocalFilesTabState();
}

class _LocalFilesTabState extends State<LocalFilesTab> {
  List<PlatformFile> _files = [];
  bool _picking = false;

  static const _audioExtensions = {'mp3','m4a','aac','wav','flac','ogg','opus','aiff'};
  static const _videoExtensions = {'mp4','m4v','mov','mkv','webm','3gp'};

  Future<void> _browse() async {
    setState(() => _picking = true);
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: [..._audioExtensions, ..._videoExtensions],
        allowMultiple: true,
        withData: false,
      );
      if (result != null && mounted) {
        setState(() => _files = result.files.where((file) => file.path != null).toList());
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not open local files: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _picking = false);
    }
  }

  bool _isAudio(PlatformFile file) => _audioExtensions.contains(file.extension?.toLowerCase());
  bool _isVideo(PlatformFile file) => _videoExtensions.contains(file.extension?.toLowerCase());

  Future<void> _playAudio(PlatformFile selected) async {
    final audio = _files.where(_isAudio).toList();
    final queue = audio.map((file) => <String, dynamic>{
      'id': file.path,
      'local_path': file.path,
      'title': file.name,
      'artist': 'On this device',
    }).toList();
    final index = queue.indexWhere((item) => item['local_path'] == selected.path);
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
      builder: (_) => HteVideoPlayerPage(url: '', filePath: file.path, title: file.name),
    ));
  }

  @override
  Widget build(BuildContext context) => Column(children: [
    Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Row(children: [
        const Expanded(child: Text('Files on this device',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold))),
        FilledButton.icon(
          onPressed: _picking ? null : _browse,
          icon: const Icon(Icons.folder_open),
          label: Text(_picking ? 'Opening…' : 'Browse'),
        ),
      ]),
    ),
    const Padding(
      padding: EdgeInsets.symmetric(horizontal: 18, vertical: 4),
      child: Text(
        'Choose music and videos stored on your phone. Selected files play without mobile data or Wi-Fi.',
        style: TextStyle(color: Colors.white60, fontSize: 12),
      ),
    ),
    Expanded(child: _files.isEmpty
      ? const Center(child: Text('No local files selected. Tap Browse to open your phone storage.',
          textAlign: TextAlign.center, style: TextStyle(color: Colors.white60)))
      : ListView.builder(
          itemCount: _files.length,
          itemBuilder: (context, index) {
            final file = _files[index];
            final audio = _isAudio(file);
            final video = _isVideo(file);
            return Card(child: ListTile(
              leading: Icon(audio ? Icons.music_note : Icons.video_file,
                color: const Color(0xFFD9B96E)),
              title: Text(file.name, maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text('${audio ? 'Audio' : video ? 'Video' : 'Media'} • ${(file.size / (1024 * 1024)).toStringAsFixed(1)} MB'),
              trailing: IconButton(
                tooltip: audio ? 'Play audio' : 'Play video',
                icon: const Icon(Icons.play_circle_fill, color: Color(0xFFD9B96E), size: 32),
                onPressed: audio ? () => _playAudio(file) : video ? () => _playVideo(file) : null,
              ),
            ));
          },
        )),
  ]);
}
