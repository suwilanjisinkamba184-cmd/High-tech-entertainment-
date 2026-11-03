import 'dart:io';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class HteVideoPlayerPage extends StatefulWidget {
  const HteVideoPlayerPage({super.key,required this.url,required this.title,this.filePath});
  final String url,title;
  final String? filePath;
  @override State<HteVideoPlayerPage> createState()=>_HteVideoPlayerPageState();
}
class _HteVideoPlayerPageState extends State<HteVideoPlayerPage> {
  late final VideoPlayerController controller;
  bool ready=false; String? error;
  @override void initState(){
    super.initState();
    controller = (widget.filePath != null
      ? VideoPlayerController.file(File(widget.filePath!))
      : VideoPlayerController.networkUrl(Uri.parse(widget.url)))
      ..initialize().then((_){if(mounted)setState(()=>ready=true);})
      .catchError((e){if(mounted)setState(()=>error=e.toString());});
  }
  @override void dispose(){controller.dispose();super.dispose();}
  @override Widget build(BuildContext context)=>Scaffold(
    appBar:AppBar(title:Text(widget.title)),backgroundColor:Colors.black,
    body:Center(child:error!=null?Padding(padding:const EdgeInsets.all(24),child:Text('Video playback failed: $error'))
      :!ready?const CircularProgressIndicator()
      :AspectRatio(aspectRatio:controller.value.aspectRatio,child:Stack(alignment:Alignment.bottomCenter,children:[
        VideoPlayer(controller),
        VideoProgressIndicator(controller,allowScrubbing:true,padding:const EdgeInsets.all(12)),
        Center(child:IconButton(iconSize:68,color:Colors.white,
          icon:Icon(controller.value.isPlaying?Icons.pause_circle:Icons.play_circle),
          onPressed:()=>setState(()=>controller.value.isPlaying?controller.pause():controller.play()))),
      ]))),
  );
}
