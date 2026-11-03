import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import 'playback_engine.dart';

/// Shared video screen for streamed and on-device media.
///
/// The music service remains the only just_audio AudioPlayer in the app.
/// Music is paused while a video is playing so the two audio streams never
/// compete; previously playing music resumes only after video disposal ends.
class HteVideoPlayerPage extends StatefulWidget {
  const HteVideoPlayerPage({
    super.key,
    required this.url,
    required this.title,
    this.filePath,
  });

  final String url;
  final String title;
  final String? filePath;

  @override
  State<HteVideoPlayerPage> createState() => _HteVideoPlayerPageState();
}

class _HteVideoPlayerPageState extends State<HteVideoPlayerPage> {
  VideoPlayerController? _controller;
  bool _ready = false;
  bool _resumeMusic = false;
  bool _lastPlaying = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    final music = HtePlayback.instance;

    // An advertisement is non-interruptible. Do not let opening a video pause
    // or replace the advert that is currently playing.
    if (music.adPlaying.value) {
      if (mounted) {
        setState(() => _error =
            'Please wait until the advertisement has finished before playing a video.');
      }
      return;
    }

    _resumeMusic = music.player.playing;
    if (_resumeMusic) {
      await music.player.pause();
    }
    // The route may have been closed while the asynchronous pause was pending.
    if (!mounted) return;

    try {
      final String? path = widget.filePath;
      late final VideoPlayerController video;

      if (path != null && path.trim().isNotEmpty) {
        final file = File(path);
        if (!await file.exists()) {
          throw const FileSystemException('The selected video file no longer exists.');
        }
        if (!mounted) return;
        video = VideoPlayerController.file(file);
      } else {
        final uri = Uri.tryParse(widget.url.trim());
        if (uri == null ||
            (uri.scheme != 'https' && uri.scheme != 'http') ||
            uri.host.isEmpty) {
          throw const FormatException('This video link is not a valid HTTP or HTTPS URL.');
        }
        video = VideoPlayerController.networkUrl(uri);
      }

      _controller = video;
      await video.initialize();
      // dispose() owns controller cleanup if the route was closed meanwhile.
      if (!mounted) return;

      _lastPlaying = video.value.isPlaying;
      video.addListener(_onVideoChanged);
      setState(() => _ready = true);
      await video.play();
    } catch (error) {
      if (mounted) {
        setState(() => _error = _friendlyVideoError(error));
      }
    }
  }

  String _friendlyVideoError(Object error) {
    final value = error.toString();
    if (value.contains('SocketException') ||
        value.contains('Failed host lookup')) {
      return 'The video could not load because there is no internet connection. Download it first or choose a local video.';
    }
    if (value.contains('404')) {
      return 'This video link is no longer available. Ask the administrator to upload it again.';
    }
    if (value.contains('403') || value.contains('401')) {
      return 'This video is private or its access has expired.';
    }
    return 'Video playback failed. The file may use an unsupported format or damaged codec. Details: $value';
  }

  void _onVideoChanged() {
    final video = _controller;
    if (!mounted || video == null) return;
    final playing = video.value.isPlaying;
    if (playing != _lastPlaying || video.value.hasError) {
      _lastPlaying = playing;
      setState(() {
        if (video.value.hasError) {
          _error = video.value.errorDescription ?? 'The video could not be decoded.';
        }
      });
    }
  }

  Future<void> _togglePlayback() async {
    final video = _controller;
    if (video == null || !video.value.isInitialized) return;
    if (video.value.isPlaying) {
      await video.pause();
    } else {
      await video.play();
    }
  }

  Future<void> _disposeVideoThenResume(VideoPlayerController video) async {
    try {
      await video.dispose();
    } catch (error) {
      debugPrint('Could not dispose video player cleanly: $error');
    }
    await _resumeMusicAfterVideo();
  }

  Future<void> _resumeMusicAfterVideo() async {
    if (!_resumeMusic) return;
    final music = HtePlayback.instance;
    // Never restart music over a mandatory advertisement.
    if (music.adPlaying.value) return;
    try {
      await music.player.play();
    } catch (error) {
      debugPrint('Could not resume music after video: $error');
    }
  }

  @override
  void dispose() {
    final video = _controller;
    if (video != null) {
      video.removeListener(_onVideoChanged);
      // Wait for the video audio session to be released before resuming music.
      unawaited(_disposeVideoThenResume(video));
    } else {
      unawaited(_resumeMusicAfterVideo());
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final video = _controller;
    final ratio = video?.value.aspectRatio ?? 16 / 9;

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text(widget.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        backgroundColor: Colors.black,
      ),
      body: Center(
        child: _error != null
            ? Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error_outline, color: Colors.amber, size: 44),
                    const SizedBox(height: 14),
                    Text(_error!, textAlign: TextAlign.center),
                    const SizedBox(height: 18),
                    TextButton(
                      onPressed: () => Navigator.of(context).maybePop(),
                      child: const Text('Go back'),
                    ),
                  ],
                ),
              )
            : !_ready || video == null
                ? const Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(color: Color(0xFFD4AF37)),
                      SizedBox(height: 14),
                      Text('Loading video…', style: TextStyle(color: Colors.white70)),
                    ],
                  )
                : AspectRatio(
                    aspectRatio: ratio.isFinite && ratio > 0 ? ratio : 16 / 9,
                    child: Stack(
                      alignment: Alignment.bottomCenter,
                      children: [
                        VideoPlayer(video),
                        GestureDetector(
                          behavior: HitTestBehavior.opaque,
                          onTap: _togglePlayback,
                          child: Center(
                            child: DecoratedBox(
                              decoration: BoxDecoration(
                                color: Colors.black54,
                                borderRadius: BorderRadius.circular(48),
                              ),
                              child: Padding(
                                padding: const EdgeInsets.all(10),
                                child: Icon(
                                  video.value.isPlaying
                                      ? Icons.pause_circle_filled
                                      : Icons.play_circle_fill,
                                  size: 62,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        ),
                        VideoProgressIndicator(
                          video,
                          allowScrubbing: true,
                          padding: const EdgeInsets.fromLTRB(12, 0, 12, 14),
                          colors: const VideoProgressColors(
                            playedColor: Color(0xFFD4AF37),
                            bufferedColor: Colors.white38,
                            backgroundColor: Colors.white24,
                          ),
                        ),
                      ],
                    ),
                  ),
      ),
    );
  }
}
