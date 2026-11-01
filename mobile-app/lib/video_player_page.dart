import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class HteVideoPlayerPage extends StatefulWidget {
  const HteVideoPlayerPage({super.key, required this.url, required this.title});
  final String url;
  final String title;

  @override
  State<HteVideoPlayerPage> createState() => _HteVideoPlayerPageState();
}

class _HteVideoPlayerPageState extends State<HteVideoPlayerPage> {
  VideoPlayerController? controller;
  bool ready = false;
  String? error;

  @override
  void initState() {
    super.initState();
    final uri = Uri.tryParse(widget.url.trim());
    if (uri == null || !['http', 'https'].contains(uri.scheme) || uri.host.isEmpty) {
      error = 'This video does not have a valid web address.';
      return;
    }
    final video = VideoPlayerController.networkUrl(uri);
    controller = video;
    video.initialize().then((_) {
      if (mounted) setState(() => ready = true);
    }).catchError((Object e) {
      if (mounted) setState(() => error = 'The video could not be loaded. Check your connection or try again.');
    });
  }

  @override
  void dispose() {
    controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final video = controller;
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      backgroundColor: Colors.black,
      body: Center(
        child: error != null
            ? Padding(
                padding: const EdgeInsets.all(24),
                child: Text(error!, textAlign: TextAlign.center),
              )
            : !ready || video == null
                ? const CircularProgressIndicator()
                : AspectRatio(
                    aspectRatio: video.value.aspectRatio > 0 ? video.value.aspectRatio : 16 / 9,
                    child: Stack(
                      alignment: Alignment.bottomCenter,
                      children: [
                        VideoPlayer(video),
                        VideoProgressIndicator(video, allowScrubbing: true, padding: const EdgeInsets.all(12)),
                        Center(
                          child: IconButton(
                            iconSize: 68,
                            color: Colors.white,
                            icon: Icon(video.value.isPlaying ? Icons.pause_circle : Icons.play_circle),
                            onPressed: () async {
                              try {
                                if (video.value.isPlaying) {
                                  await video.pause();
                                } else {
                                  await video.play();
                                }
                                if (mounted) setState(() {});
                              } catch (_) {
                                if (mounted) setState(() => error = 'Video playback was interrupted. Please try again.');
                              }
                            },
                          ),
                        ),
                      ],
                    ),
                  ),
      ),
    );
  }
}
