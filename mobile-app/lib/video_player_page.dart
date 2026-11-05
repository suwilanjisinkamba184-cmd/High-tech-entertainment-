import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'playback_engine.dart';

/// Shared streamed and on-device video player with a real landscape mode.
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
  bool _isFullscreen = false;
  bool _retrying = false;
  double _playbackSpeed = 1.0;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    final music = HtePlayback.instance;
    if (music.adPlaying.value) {
      if (mounted) {
        setState(() => _error =
            'Please wait until the advertisement has finished before playing a video.');
      }
      return;
    }

    try {
      _resumeMusic = music.player.playing;
      if (_resumeMusic) await music.player.pause();
      if (!mounted) return;

      final path = widget.filePath;
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
      await video.initialize().timeout(const Duration(seconds: 45));
      if (!mounted) return;
      // Apply the user's saved default speed before playback begins.
      try {
        final preferences = await SharedPreferences.getInstance();
        final savedSpeed = preferences.getDouble('hte_video_speed') ?? 1.0;
        await video.setPlaybackSpeed(savedSpeed);
        _playbackSpeed = savedSpeed;
      } catch (error) {
        debugPrint('HTE saved video speed could not be applied: $error');
      }
      if (!mounted) return;
      _lastPlaying = video.value.isPlaying;
      video.addListener(_onVideoChanged);
      setState(() => _ready = true);
      await video.play();
    } catch (error) {
      if (mounted) setState(() => _error = _friendlyVideoError(error));
    }
  }

  String _friendlyVideoError(Object error) {
    final value = error.toString();
    if (value.contains('SocketException') || value.contains('Failed host lookup') || value.contains('Connection timed out') || value.contains('Network is unreachable') || value.contains('Connection reset')) {
      return 'The video could not load because there is no internet connection. Download it first or choose a local video.';
    }
    if (value.contains('404')) return 'This video link is no longer available. Ask the administrator to upload it again.';
    if (value.contains('403') || value.contains('401')) return 'This video is private or its access has expired.';
    return 'This video could not be played. Check your connection or try another video file.';
  }

  void _onVideoChanged() {
    final video = _controller;
    if (!mounted || video == null) return;
    final playing = video.value.isPlaying;
    if (playing != _lastPlaying || video.value.hasError) {
      _lastPlaying = playing;
      setState(() {
        if (video.value.hasError) {
          _error = 'Video playback stopped unexpectedly. Please try again or choose another video.';
        }
      });
    }
  }

  Future<void> _retryVideo() async {
    if (_retrying || !mounted) return;
    final shouldResumeMusic = _resumeMusic;
    setState(() {
      _retrying = true;
      _ready = false;
      _error = null;
    });
    final previous = _controller;
    _controller = null;
    if (previous != null) {
      previous.removeListener(_onVideoChanged);
      try {
        await previous.dispose();
      } catch (error) {
        debugPrint('Could not dispose failed video before retry: $error');
      }
    }
    if (!mounted) return;
    try {
      await _initialize();
      if (shouldResumeMusic) _resumeMusic = true;
    } catch (error) {
      if (mounted) setState(() => _error = _friendlyVideoError(error));
    } finally {
      if (mounted) setState(() => _retrying = false);
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

  Future<void> _setFullscreen(bool value) async {
    if (!mounted || _isFullscreen == value) return;
    setState(() => _isFullscreen = value);
    if (value) {
      await SystemChrome.setPreferredOrientations(const [
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    } else {
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
      await SystemChrome.setPreferredOrientations(const [
        DeviceOrientation.portraitUp,
        DeviceOrientation.portraitDown,
      ]);
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
    if (music.adPlaying.value) return;
    try {
      await music.player.play();
    } catch (error) {
      debugPrint('Could not resume music after video: $error');
    }
  }

  @override
  void dispose() {
    unawaited(SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge));
    unawaited(SystemChrome.setPreferredOrientations(const [
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]));
    final video = _controller;
    if (video != null) {
      video.removeListener(_onVideoChanged);
      unawaited(_disposeVideoThenResume(video));
    } else {
      unawaited(_resumeMusicAfterVideo());
    }
    super.dispose();
  }

  String _time(Duration value) {
    final minutes = value.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = value.inSeconds.remainder(60).toString().padLeft(2, '0');
    final hours = value.inHours;
    return hours > 0 ? '$hours:$minutes:$seconds' : '$minutes:$seconds';
  }

  Widget _videoSurface(VideoPlayerController video) {
    final ratio = video.value.aspectRatio;
    return Center(
      child: AspectRatio(
        aspectRatio: ratio.isFinite && ratio > 0 ? ratio : 16 / 9,
        child: Stack(
          fit: StackFit.expand,
          children: [
            VideoPlayer(video),
            GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: _togglePlayback,
              child: Center(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(50),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(10),
                    child: Icon(
                      video.value.isPlaying ? Icons.pause_circle_filled : Icons.play_circle_fill,
                      size: 62,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
            ),
            Positioned(
              left: 10,
              right: 10,
              bottom: 4,
              child: Material(
                color: Colors.black54,
                borderRadius: BorderRadius.circular(14),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  child: Row(
                    children: [
                      IconButton(
                        tooltip: video.value.isPlaying ? 'Pause video' : 'Play video',
                        onPressed: _togglePlayback,
                        icon: Icon(video.value.isPlaying ? Icons.pause : Icons.play_arrow, color: Colors.white),
                      ),
                      Text(_time(video.value.position), style: const TextStyle(color: Colors.white, fontSize: 11)),
                      Expanded(
                        child: VideoProgressIndicator(
                          video,
                          allowScrubbing: true,
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
                          colors: const VideoProgressColors(
                            playedColor: Color(0xFFD4AF37),
                            bufferedColor: Colors.white38,
                            backgroundColor: Colors.white24,
                          ),
                        ),
                      ),
                      Text(_time(video.value.duration), style: const TextStyle(color: Colors.white, fontSize: 11)),
                      IconButton(
                        tooltip: _isFullscreen ? 'Exit full screen' : 'Full screen',
                        onPressed: () => _setFullscreen(!_isFullscreen),
                        icon: Icon(_isFullscreen ? Icons.fullscreen_exit : Icons.fullscreen, color: const Color(0xFFD4AF37)),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final video = _controller;
    return PopScope(
      canPop: !_isFullscreen,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && _isFullscreen) {
          unawaited(_setFullscreen(false));
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        appBar: _isFullscreen
            ? null
            : AppBar(
                title: Text(widget.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                backgroundColor: Colors.black,
                actions: [
                  PopupMenuButton<double>(
                    tooltip: 'Playback speed',
                    enabled: _ready,
                    initialValue: _playbackSpeed,
                    onSelected: (speed) async {
                      final current = _controller;
                      if (current == null) return;
                      await current.setPlaybackSpeed(speed);
                      if (mounted) setState(() => _playbackSpeed = speed);
                    },
                    itemBuilder: (_) => [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]
                        .map((speed) => PopupMenuItem<double>(
                          value: speed,
                          child: Text(speed == 1.0 ? 'Normal (1×)' : '$speed×'),
                        )).toList(),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      child: Center(child: Text('$_playbackSpeed×',
                        style: const TextStyle(color: Color(0xFFD4AF37), fontWeight: FontWeight.w700))),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Full screen',
                    onPressed: _ready ? () => _setFullscreen(true) : null,
                    icon: const Icon(Icons.fullscreen, color: Color(0xFFD4AF37)),
                  ),
                ],
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
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton.icon(
                          onPressed: _retrying ? null : _retryVideo,
                          icon: _retrying
                            ? const SizedBox(width: 17, height: 17, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Icon(Icons.refresh_rounded),
                          label: Text(_retrying ? 'Retrying…' : 'Retry video'),
                        ),
                      ),
                      TextButton(onPressed: () => Navigator.of(context).maybePop(), child: const Text('Go back')),
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
                  : _videoSurface(video),
        ),
      ),
    );
  }
}
