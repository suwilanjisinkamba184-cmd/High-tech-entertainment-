import 'dart:convert';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:share_plus/share_plus.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'playback_engine.dart';
import 'video_player_page.dart';
import 'package:url_launcher/url_launcher.dart';

const supabaseUrl = 'https://wqrxacjbsldbnoekbkbf.supabase.co';
const supabaseKey = 'sb_publishable_hB_aRh-dnicpRQq3wiRtWg_JTdhmMda';
final db = Supabase.instance.client;
final playback = HtePlayback();
final player = playback.player;
final ValueNotifier<int> favouritesRevision = ValueNotifier<int>(0);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const HteApp());
}

class HteApp extends StatelessWidget {
  const HteApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'High-Tech Entertainment',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xFF090909),
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFD4AF37), brightness: Brightness.dark),
    ),
    home: const StartupGate(),
  );
}


class StartupGate extends StatefulWidget {
  const StartupGate({super.key});

  @override
  State<StartupGate> createState() => _StartupGateState();
}

class _StartupGateState extends State<StartupGate>
    with SingleTickerProviderStateMixin {
  late final AnimationController _spin;
  bool _ready = false;
  bool _starting = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _spin = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();
    _initialize();
  }

  Future<void> _initialize() async {
    if (mounted) {
      setState(() {
        _starting = true;
        _error = null;
      });
    }
    try {
      await Supabase.initialize(
        url: supabaseUrl,
        publishableKey: supabaseKey,
      );
      // Audio notifications are optional: a notification initialisation
      // failure must not prevent the rest of the app from opening.
      try {
        await JustAudioBackground.init(
          androidNotificationChannelId: 'com.hightechentertainment.app.audio',
          androidNotificationChannelName: 'High-Tech Music Playback',
          androidNotificationOngoing: true,
        );
      } catch (_) {
        // Playback remains available in-app even if background controls fail.
      }
      await Future<void>.delayed(const Duration(milliseconds: 700));
      if (mounted) {
        setState(() {
          _ready = true;
          _starting = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = 'We could not initialise the app services. Check your connection and try again.\n$error';
          _starting = false;
        });
      }
    }
  }

  @override
  void dispose() {
    _spin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_ready) return const AuthGate();
    return Scaffold(
      backgroundColor: const Color(0xFF080808),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                AnimatedBuilder(
                  animation: _spin,
                  builder: (context, child) => Transform.rotate(
                    angle: _spin.value * 2 * math.pi,
                    child: child,
                  ),
                  child: SvgPicture.asset(
                    'assets/high-tech-logo.svg',
                    width: 116,
                    height: 116,
                  ),
                ),
                const SizedBox(height: 30),
                const Text(
                  'HIGH-TECH',
                  style: TextStyle(
                    fontSize: 25,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 4,
                  ),
                ),
                const SizedBox(height: 5),
                const Text(
                  'ENTERTAINMENT',
                  style: TextStyle(
                    color: Color(0xFFD4AF37),
                    fontSize: 12,
                    letterSpacing: 5,
                  ),
                ),
                const SizedBox(height: 26),
                if (_starting)
                  const CircularProgressIndicator(
                    color: Color(0xFFD4AF37),
                    strokeWidth: 2.5,
                  ),
                if (_starting) ...[
                  const SizedBox(height: 14),
                  const Text(
                    'Preparing your experience…',
                    style: TextStyle(color: Colors.white60),
                  ),
                ],
                if (_error != null) ...[
                  Text(
                    _error!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 18),
                  FilledButton(
                    onPressed: _starting ? null : _initialize,
                    child: const Text('Try again'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}


/// Keeps the native app behind Supabase authentication. A saved session
/// opens the app; signing out returns the user to the sign-in screen.
class AuthGate extends StatelessWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<AuthState>(
      stream: db.auth.onAuthStateChange,
      builder: (context, snapshot) {
        final session = snapshot.hasData
            ? snapshot.data!.session
            : db.auth.currentSession;
        return session == null ? const LoginPage() : const MainScreen();
      },
    );
  }
}

class LoginPage extends StatelessWidget {
  const LoginPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF080808),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(26, 36, 26, 28),
              shrinkWrap: true,
              children: [
                const SizedBox(height: 18),
                Center(child: SvgPicture.asset(
                  'assets/high-tech-logo.svg',
                  width: 116,
                  height: 116,
                )),
                const SizedBox(height: 22),
                const Text('HIGH-TECH ENTERTAINMENT',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900,
                    letterSpacing: 2, color: Color(0xFFD4AF37))),
                const SizedBox(height: 8),
                const Text('Music. Beats. Production. Beyond.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white70)),
                const SizedBox(height: 30),
                const Text('Welcome back',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 27, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                const Text('Sign in to continue to High-Tech Entertainment.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white60)),
                const SizedBox(height: 22),
                const AccountTab(embedded: true),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});
  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int tab = 0;
  final pages = const [HomeTab(), DiscoverTab(), MusicTab(), SavedTab(), MoreTab()];
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: IndexedStack(index: tab, children: pages)),
    bottomNavigationBar: Column(mainAxisSize: MainAxisSize.min, children: [const PlayerBar(), NavigationBar(
      selectedIndex: tab,
      onDestinationSelected: (value) => setState(() => tab = value),
      destinations: const [
        NavigationDestination(icon: Icon(Icons.home_outlined), label: 'Home'),
        NavigationDestination(icon: Icon(Icons.search), label: 'Discover'),
        NavigationDestination(icon: Icon(Icons.music_note_outlined), label: 'Music'),
        NavigationDestination(icon: Icon(Icons.favorite_border), label: 'Saved'),
        NavigationDestination(icon: Icon(Icons.grid_view_rounded), label: 'More'),
      ],
    )]),
  );
}

class MoreTab extends StatelessWidget {
  const MoreTab({super.key});

  void _open(BuildContext context, String title, Widget page) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        appBar: AppBar(title: Text(title)),
        body: SafeArea(child: page),
      ),
    ));
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.only(bottom: 24),
    children: [
      const Brand(),
      const Padding(
        padding: EdgeInsets.fromLTRB(20, 12, 20, 8),
        child: Text('Explore more', style: TextStyle(fontSize: 25, fontWeight: FontWeight.bold)),
      ),
      Card(child: ListTile(
        leading: const Icon(Icons.people_outline, color: Color(0xFFD4AF37)),
        title: const Text('Artists'),
        subtitle: const Text('Discover High-Tech artists and their profiles'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => _open(context, 'Artists', const ArtistsTab()),
      )),
      Card(child: ListTile(
        leading: const Icon(Icons.video_library_outlined, color: Color(0xFFD4AF37)),
        title: const Text('Videos'),
        subtitle: const Text('Watch music videos and visual content'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => _open(context, 'Videos', const VideosTab()),
      )),
      Card(child: ListTile(
        leading: const Icon(Icons.person_outline, color: Color(0xFFD4AF37)),
        title: const Text('Account'),
        subtitle: const Text('Profile, sign out, privacy and settings'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => _open(context, 'Account', const AccountTab()),
      )),
    ],
  );
}

class Brand extends StatelessWidget {
  const Brand({super.key, this.tagline = 'MUSIC. BEATS. PRODUCTION. BEYOND.'});
  final String tagline;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(20),
    child: Row(children: [
      SvgPicture.asset('assets/high-tech-logo.svg', width: 52, height: 52),
      const SizedBox(width: 12),
      const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('HIGH-TECH', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900, letterSpacing: 2)),
        Text('ENTERTAINMENT', style: TextStyle(fontSize: 11, color: Color(0xFFD4AF37), letterSpacing: 3)),
      ])),
      const Icon(Icons.equalizer, color: Color(0xFFD4AF37)),
    ]),
  );
}

class Heading extends StatelessWidget {
  const Heading(this.text, {super.key});
  final String text;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 18, 20, 10),
    child: Text(text, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
  );
}

class HomeTab extends StatelessWidget {
  const HomeTab({super.key});
  @override
  Widget build(BuildContext context) => ListView(children: [
    const Brand(),
    Container(
      margin: const EdgeInsets.symmetric(horizontal: 18),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: const Color(0xFF806B2B)),
        gradient: const LinearGradient(colors: [Color(0xFF44350E), Color(0xFF15120A), Color(0xFF181818)]),
      ),
      child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('QUALITY SOUND,', style: TextStyle(color: Color(0xFFD4AF37), fontWeight: FontWeight.w900, letterSpacing: 2)),
        Text('GLOBAL VIBES.', style: TextStyle(fontSize: 29, fontWeight: FontWeight.w900)),
        SizedBox(height: 8),
        Text('Discover artists, music and new releases from High-Tech Entertainment.'),
      ]),
    ),
    const Heading('Latest releases'),
    const ReleaseList(horizontal: true),
    const Heading('Featured artists'),
    const ArtistList(horizontal: true),
    const Heading('Recently added music'),
    const TrackList(compact: true),
  ]);
}

class ArtistsTab extends StatelessWidget {
  const ArtistsTab({super.key});
  @override
  Widget build(BuildContext context) => const Column(children: [
    Brand(),
    Align(alignment: Alignment.centerLeft, child: Padding(padding: EdgeInsets.symmetric(horizontal: 20), child: Text('Artists', style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold)))),
    Expanded(child: ArtistList()),
  ]);
}

class ArtistList extends StatelessWidget {
  const ArtistList({super.key, this.horizontal = false});
  final bool horizontal;
  @override
  Widget build(BuildContext context) => FutureBuilder<List<Map<String, dynamic>>>(
    future: rows('artists', 'name'),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Notice('Unable to load artists: ${snapshot.error}. Check connection, Data API access and RLS.');
      if (!snapshot.hasData) return const Busy();
      final items = snapshot.data!;
      if (items.isEmpty) return const Notice('Published artist profiles will appear here.');
      if (horizontal) {
        return SizedBox(height: 145, child: ListView(
          scrollDirection: Axis.horizontal,
          children: items.map((item) => SizedBox(width: 112, child: Column(children: [
            Avatar(item: item),
            const SizedBox(height: 8),
            Text((item['name'] ?? 'Artist').toString(), maxLines: 1, overflow: TextOverflow.ellipsis),
          ]))).toList(),
        ));
      }
      return ListView.builder(
        padding: const EdgeInsets.all(16), itemCount: items.length,
        itemBuilder: (context, index) {
          final item = items[index];
          return Card(child: ListTile(
            leading: Avatar(item: item),
            title: Text((item['name'] ?? 'Artist').toString()),
            subtitle: Text((item['bio'] ?? 'High-Tech Entertainment artist').toString(), maxLines: 2, overflow: TextOverflow.ellipsis),
          ));
        },
      );
    },
  );
}

class Avatar extends StatelessWidget {
  const Avatar({super.key, required this.item});
  final Map<String, dynamic> item;
  @override
  Widget build(BuildContext context) {
    final url = (item['image_url'] ?? '').toString();
    return CircleAvatar(
      radius: 40,
      backgroundColor: const Color(0xFF30260E),
      backgroundImage: url.startsWith('http') ? NetworkImage(url) : null,
      child: url.startsWith('http') ? null : const Icon(Icons.person, color: Color(0xFFD4AF37), size: 34),
    );
  }
}

class ReleaseList extends StatelessWidget {
  const ReleaseList({super.key, this.horizontal = false});
  final bool horizontal;
  @override
  Widget build(BuildContext context) => FutureBuilder<List<Map<String, dynamic>>>(
    future: rows('releases', 'created_at'),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Notice('Unable to load releases: ${snapshot.error}.');
      if (!snapshot.hasData) return const Busy();
      final items = snapshot.data!;
      if (items.isEmpty) return const Notice('Published releases will appear here.');
      return SizedBox(
        height: horizontal ? 180 : null,
        child: ListView.builder(
          scrollDirection: horizontal ? Axis.horizontal : Axis.vertical,
          shrinkWrap: !horizontal,
          physics: horizontal ? null : const NeverScrollableScrollPhysics(),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          itemCount: items.length,
          itemBuilder: (context, index) {
            final item = items[index];
            return SizedBox(
              width: horizontal ? 145 : null,
              child: Card(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                MediaCover(url: item['cover_url']?.toString(), height: horizontal ? 112 : 170),
                Padding(padding: const EdgeInsets.all(10), child: Text((item['title'] ?? 'Release').toString(), maxLines: 2, overflow: TextOverflow.ellipsis)),
              ])),
            );
          },
        ),
      );
    },
  );
}

class MusicTab extends StatelessWidget {
  const MusicTab({super.key});
  @override
  Widget build(BuildContext context) => Column(children: [
    const Brand(),
    const Align(alignment: Alignment.centerLeft, child: Padding(padding: EdgeInsets.symmetric(horizontal: 20), child: Text('Music library', style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold)))),
    const Expanded(child: TrackList()),
  ]);
}

class TrackList extends StatelessWidget {
  const TrackList({super.key, this.compact = false, this.items});
  final bool compact;
  final List<Map<String, dynamic>>? items;
  @override
  Widget build(BuildContext context) => FutureBuilder<List<Map<String, dynamic>>>(
    future: items == null ? rows('tracks', 'created_at') : Future.value(items),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Notice('Unable to load tracks: ${snapshot.error}. Check connection, Data API access and RLS.');
      if (!snapshot.hasData) return const Busy();
      final items = snapshot.data!;
      if (items.isEmpty) return const Notice('Published tracks will appear here.');
      return ListView.builder(
        shrinkWrap: compact,
        physics: compact ? const NeverScrollableScrollPhysics() : null,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        itemCount: items.length,
        itemBuilder: (context, index) {
          final item = items[index];
          return Card(child: ListTile(
            leading: SizedBox(width: 48, height: 48, child: MediaCover(url: item['cover_url']?.toString(), height: 48)),
            title: Text((item['title'] ?? 'Track').toString(), maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: Text((item['artist'] ?? 'High-Tech Entertainment').toString()),
            trailing: Row(mainAxisSize: MainAxisSize.min, children: [
              IconButton(tooltip: 'Save favourite', onPressed: () async { await toggleFavourite((item['id'] ?? '').toString()); if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Your favourites have been updated.'))); }, icon: const Icon(Icons.favorite_border, size: 20, color: Colors.pinkAccent)),
              IconButton(tooltip: 'Share track', onPressed: () => Share.share('${item['title']} — ${item['artist']}\nHigh-Tech Entertainment'), icon: const Icon(Icons.share_outlined, size: 20)),
              IconButton(
              icon: const Icon(Icons.play_circle_fill, color: Color(0xFFD4AF37), size: 34),
              onPressed: () async {
                try { await playback.playTrack(item, items); }
                catch (error) { if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Playback failed: $error'))); }
              },
            )]),
          ));
        },
      );
    },
  );
}

class PlayerBar extends StatelessWidget {
  const PlayerBar({super.key});
  @override
  Widget build(BuildContext context) => ValueListenableBuilder<Map<String,dynamic>?>(
    valueListenable: playback.currentTrack,
    builder: (context, track, _) {
      if (track == null) return const SizedBox.shrink();
      return Material(
        color: const Color(0xFF1B1B1B),
        child: InkWell(
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const FullPlayerPage())),
          child: Padding(padding: const EdgeInsets.symmetric(horizontal:12,vertical:6),child:Row(children:[
            SizedBox(width:42,height:42,child:MediaCover(url:track['cover_url']?.toString(),height:42)),
            const SizedBox(width:10),
            Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,mainAxisSize:MainAxisSize.min,children:[
              Text((track['title']??'High-Tech Player').toString(),maxLines:1,overflow:TextOverflow.ellipsis),
              Text((track['artist']??'').toString(),style:const TextStyle(color:Colors.white54,fontSize:12),maxLines:1),
            ])),
            StreamBuilder<PlayerState>(stream:player.playerStateStream,builder:(context,s){
              final playing=s.data?.playing??false;
              return IconButton(onPressed:()=>playing?player.pause():player.play(),icon:Icon(playing?Icons.pause:Icons.play_arrow,color:const Color(0xFFD4AF37)));
            }),
            IconButton(onPressed:()=>playback.next(),icon:const Icon(Icons.skip_next)),
          ])),
        ),
      );
    },
  );
}

class FullPlayerPage extends StatelessWidget {
  const FullPlayerPage({super.key});
  String _time(Duration d) => '${d.inMinutes.toString().padLeft(2,'0')}:${(d.inSeconds%60).toString().padLeft(2,'0')}';
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar:AppBar(title:const Text('Now playing'),actions:[IconButton(tooltip:'Queue',onPressed:()=>Navigator.push(context,MaterialPageRoute(builder:(_)=>const QueuePage())),icon:const Icon(Icons.queue_music))]),
    body:ValueListenableBuilder<Map<String,dynamic>?>(
      valueListenable:playback.currentTrack,
      builder:(context,track,_)=>track==null?const Notice('Choose a track to start listening.'):Padding(
        padding:const EdgeInsets.all(24),child:Column(mainAxisAlignment:MainAxisAlignment.center,children:[
          ClipRRect(borderRadius:BorderRadius.circular(22),child:SizedBox(width:260,height:260,child:MediaCover(url:track['cover_url']?.toString(),height:260))),
          const SizedBox(height:24),
          Text((track['title']??'Now playing').toString(),style:const TextStyle(fontSize:22,fontWeight:FontWeight.bold),textAlign:TextAlign.center),
          Text((track['artist']??'High-Tech Entertainment').toString(),style:const TextStyle(color:Colors.white60)),
          const SizedBox(height:20),
          StreamBuilder<Duration>(stream:player.positionStream,initialData:Duration.zero,builder:(context,pos){
            final duration=player.duration??Duration.zero;
            final position=pos.data??Duration.zero;
            final value=duration.inMilliseconds==0?0.0:position.inMilliseconds.clamp(0,duration.inMilliseconds).toDouble();
            return Column(children:[
              Slider(value:value,max:duration.inMilliseconds==0?1.0:duration.inMilliseconds.toDouble(),onChanged:(v)=>player.seek(Duration(milliseconds:v.round()))),
              Row(mainAxisAlignment:MainAxisAlignment.spaceBetween,children:[Text(_time(position)),Text(_time(duration))]),
            ]);
          }),
          const SizedBox(height:12),
          Row(mainAxisAlignment:MainAxisAlignment.spaceEvenly,children:[
            IconButton(onPressed:()=>playback.toggleShuffle(),icon:Icon(playback.shuffle?Icons.shuffle_on:Icons.shuffle)),
            IconButton(iconSize:38,onPressed:()=>playback.previous(),icon:const Icon(Icons.skip_previous)),
            StreamBuilder<PlayerState>(stream:player.playerStateStream,builder:(context,s){
              final playing=s.data?.playing??false;
              return IconButton(iconSize:64,color:const Color(0xFFD4AF37),onPressed:()=>playing?player.pause():player.play(),icon:Icon(playing?Icons.pause_circle_filled:Icons.play_circle_fill));
            }),
            IconButton(iconSize:38,onPressed:()=>playback.next(),icon:const Icon(Icons.skip_next)),
            IconButton(onPressed:()=>playback.setRepeat(true),icon:const Icon(Icons.repeat_one)),
          ]),
          ValueListenableBuilder<bool>(valueListenable:playback.adPlaying,builder:(context,isAd,_)=>Text(isAd?'Sponsored audio • High-Tech Entertainment':'Quality Sound, Global Vibes',style:const TextStyle(color:Colors.white54,fontSize:12))),
        ]),
      ),
    ),
  );
}

class QueuePage extends StatelessWidget {
  const QueuePage({super.key});
  @override
  Widget build(BuildContext context)=>Scaffold(
    appBar:AppBar(title:const Text('Up next')),
    body:ValueListenableBuilder<List<Map<String,dynamic>>>(valueListenable:playback.queueView,builder:(context,items,_)=>items.isEmpty?const Notice('Your queue is empty.'):ListView.builder(itemCount:items.length,itemBuilder:(context,i){
      final item=items[i];
      return ListTile(leading:SizedBox(width:48,height:48,child:MediaCover(url:item['cover_url']?.toString(),height:48)),title:Text((item['title']??'Track').toString()),subtitle:Text((item['artist']??'').toString()),onTap:()=>playback.playTrack(item,items));
    })),
  );
}

class VideosTab extends StatelessWidget {
  const VideosTab({super.key});
  @override
  Widget build(BuildContext context) => Column(children: [
    const Brand(),
    const Align(alignment: Alignment.centerLeft, child: Padding(padding: EdgeInsets.symmetric(horizontal: 20), child: Text('Videos', style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold)))),
    Expanded(child: FutureBuilder<List<Map<String, dynamic>>>(
      future: rows('videos', 'created_at'),
      builder: (context, snapshot) {
        if (snapshot.hasError) return const Notice('Unable to load videos.');
        if (!snapshot.hasData) return const Busy();
        final items = snapshot.data!;
        if (items.isEmpty) return const Notice('Published videos will appear here.');
        return ListView.builder(
          itemCount: items.length,
          itemBuilder: (context, index) {
            final item = items[index];
            return Card(child: ListTile(
              leading: SizedBox(width: 62, height: 55, child: MediaCover(url: item['thumbnail_url']?.toString(), height: 55)),
              title: Text((item['title'] ?? 'Video').toString()),
              subtitle: Text((item['artist'] ?? 'High-Tech Entertainment').toString()),
              trailing: const Icon(Icons.open_in_new, color: Color(0xFFD4AF37)),
              onTap: () async {
                final raw=(item['video_url']??'').toString();
                final link=Uri.tryParse(raw);
                if(link==null||!link.hasScheme)return;
                final isWebVideo=raw.contains('youtube.com')||raw.contains('youtu.be')||raw.contains('vimeo.com');
                if(isWebVideo){await launchUrl(link,mode:LaunchMode.externalApplication);return;}
                if(context.mounted) Navigator.push(context,MaterialPageRoute(builder:(_)=>HteVideoPlayerPage(url:raw,title:(item['title']??'Video').toString())));
              },
            ));
          },
        );
      },
    )),
  ]);
}

class MediaCover extends StatelessWidget {
  const MediaCover({super.key, this.url, this.height = 100});
  final String? url;
  final double height;
  @override
  Widget build(BuildContext context) {
    if (url == null || !url!.startsWith('http')) {
      return Container(height: height, color: const Color(0xFF2B230F), child: const Center(child: Icon(Icons.album, color: Color(0xFFD4AF37), size: 35)));
    }
    return Image.network(url!, height: height, width: double.infinity, fit: BoxFit.cover, errorBuilder: (_, __, ___) =>
      const Center(child: Icon(Icons.album, color: Color(0xFFD4AF37))));
  }
}


Future<void> toggleFavourite(String id) async {
  final user = db.auth.currentUser;
  if (user == null) throw StateError('Sign in to save favourites.');
  if (id.isEmpty) throw ArgumentError('This track has no valid ID.');
  final existing = await db.from('user_favorites')
    .select('track_id')
    .eq('user_id', user.id)
    .eq('track_id', id)
    .maybeSingle();
  if (existing == null) {
    await db.from('user_favorites').insert({
      'user_id': user.id,
      'track_id': id,
    });
  } else {
    await db.from('user_favorites')
      .delete()
      .eq('user_id', user.id)
      .eq('track_id', id);
  }
  favouritesRevision.value++;
}

class DiscoverTab extends StatefulWidget {
  const DiscoverTab({super.key});
  @override
  State<DiscoverTab> createState() => _DiscoverTabState();
}
class _DiscoverTabState extends State<DiscoverTab> {
  final controller = TextEditingController();
  List<Map<String,dynamic>> results = [];
  bool loading = true;
  String? error;
  @override
  void initState() { super.initState(); search(''); }
  Future<void> search(String term) async {
    setState(() { loading = true; error = null; });
    try {
      final q = db.from('tracks').select().eq('is_published', true);
      final data = term.trim().isEmpty
        ? await q.order('created_at', ascending: false).limit(100)
        : await q.ilike('title', '%${term.trim()}%').order('created_at', ascending: false).limit(100);
      results = List<Map<String,dynamic>>.from(data);
    } catch (e) { error = e.toString(); }
    if (mounted) setState(() => loading = false);
  }
  @override
  void dispose() { controller.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) => Column(children: [
    const Brand(),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: TextField(controller: controller, onChanged: search, decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search tracks by title'))),
    Expanded(child: loading ? const Busy() : error != null ? Notice('Search failed: $error') : TrackList(items: results)),
  ]);
}

class SavedTab extends StatefulWidget {
  const SavedTab({super.key});
  @override
  State<SavedTab> createState() => _SavedTabState();
}
class _SavedTabState extends State<SavedTab> {
  List<Map<String,dynamic>> saved = [];
  bool loading = true;
  @override
  void initState() {
    super.initState();
    favouritesRevision.addListener(load);
    load();
  }
  @override
  void dispose() {
    favouritesRevision.removeListener(load);
    super.dispose();
  }
  Future<void> load() async {
    if (mounted) setState(() => loading = true);
    try {
      final user = db.auth.currentUser;
      if (user == null) {
        saved = [];
      } else {
        final favouriteRows = await db.from('user_favorites')
          .select('track_id')
          .eq('user_id', user.id)
          .order('created_at', ascending: false);
        final ids = favouriteRows
          .map((row) => row['track_id'].toString())
          .toList();
        if (ids.isEmpty) {
          saved = [];
        } else {
          final tracks = await db.from('tracks')
            .select()
            .eq('is_published', true)
            .inFilter('id', ids);
          final byId = {
            for (final track in tracks) track['id'].toString(): Map<String, dynamic>.from(track),
          };
          saved = ids.where(byId.containsKey).map((id) => byId[id]!).toList();
        }
      }
    } catch (error) {
      saved = [];
      debugPrint('Could not load synced favourites: $error');
    }
    if (mounted) setState(() => loading = false);
  }
  @override
  Widget build(BuildContext context) => Column(children: [
    const Brand(),
    const Align(alignment: Alignment.centerLeft, child: Padding(padding: EdgeInsets.symmetric(horizontal: 20), child: Text('Your favourites', style: TextStyle(fontSize: 25, fontWeight: FontWeight.bold)))),
    Expanded(child: loading ? const Busy() : saved.isEmpty ? const Notice('No saved tracks yet. Tap the heart beside a track to add it here.') : TrackList(items: saved)),
  ]);
}

class AccountTab extends StatefulWidget {
  const AccountTab({super.key, this.embedded = false});
  final bool embedded;
  @override
  State<AccountTab> createState() => _AccountTabState();
}

class _AccountTabState extends State<AccountTab> {
  final email = TextEditingController();
  final password = TextEditingController();
  bool signup = false;
  bool busy = false;

  @override
  void dispose() { email.dispose(); password.dispose(); super.dispose(); } 
  Future<void> authenticate() async {
    if (email.text.trim().isEmpty || password.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter your email address and password.')),
      );
      return;
    }
    setState(() => busy = true);
    try {
      if (signup) {
        await db.auth.signUp(email: email.text.trim(), password: password.text);
      } else {
        await db.auth.signInWithPassword(email: email.text.trim(), password: password.text);
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(signup
              ? 'Account request sent. Check your email if confirmation is enabled.'
              : 'Signed in successfully.')),
        );
      }
    } on AuthException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Unable to sign in. Check your connection and try again.')),
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }


  @override
  Widget build(BuildContext context) {
    final user = db.auth.currentUser;
    final content = <Widget>[
      if (user != null) ...[
        const Brand(),
        Card(child: ListTile(
          leading: const Icon(Icons.settings_outlined, color: Color(0xFFD4AF37)),
          title: const Text('App settings'),
          subtitle: const Text('Updates, app information and support'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettingsPage())),
        )),
      ] else ...[
        const SizedBox(height: 8),
      ],
      if (user != null) ...[
        const Icon(Icons.account_circle, color: Color(0xFFD4AF37), size: 80),
        Center(child: Text(user.email ?? 'Signed in')),
        const SizedBox(height: 18),
        FilledButton(onPressed: () async { await db.auth.signOut(); if (mounted) setState(() {}); }, child: const Text('Sign out')),
        TextButton.icon(
          onPressed: () => launchUrl(Uri.parse('https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account-deletion.html'), mode: LaunchMode.externalApplication),
          icon: const Icon(Icons.privacy_tip_outlined),
          label: const Text('Privacy policy & account deletion'),
        ),
      ] else ...[
        const Text('Your account', style: TextStyle(fontSize: 25, fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder())),
        const SizedBox(height: 14),
        FilledButton(
          onPressed: busy ? null : authenticate,
          child: Text(busy ? 'Please wait' : (signup ? 'Create account' : 'Sign in')),
        ),
        TextButton(onPressed: busy ? null : () => setState(() => signup = !signup), child: Text(signup ? 'Already registered? Sign in' : 'Create a new account')),
        TextButton.icon(
          onPressed: () => launchUrl(Uri.parse('https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/privacy-policy.html'), mode: LaunchMode.externalApplication),
          icon: const Icon(Icons.privacy_tip_outlined),
          label: const Text('Privacy policy'),
        ),
        TextButton(
          onPressed: () => launchUrl(Uri.parse('https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account-deletion.html'), mode: LaunchMode.externalApplication),
          child: const Text('Request account deletion'),
        ),
      ],
    ];
    if (widget.embedded) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: content,
      );
    }
    return ListView(padding: const EdgeInsets.all(20), children: content);
  }
}


class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String version = 'Loading…';
  bool checking = false;
  bool autoplaySetting = true;
  bool shuffleSetting = false;
  bool audioAdsSetting = true;
  int adFrequency = 3;

  @override
  void initState() { super.initState(); _loadVersion(); _loadPlaybackSettings(); }

  Future<void> _loadPlaybackSettings() async {
    final prefs=await SharedPreferences.getInstance();
    autoplaySetting=prefs.getBool('hte_autoplay')??true;
    shuffleSetting=prefs.getBool('hte_shuffle')??false;
    audioAdsSetting=prefs.getBool('hte_audio_ads')??true;
    adFrequency=prefs.getInt('hte_ad_frequency')??3;
    playback.autoplay=autoplaySetting; playback.shuffle=shuffleSetting; playback.audioAdsEnabled=audioAdsSetting; playback.adEvery=adFrequency;
    if(mounted)setState((){});
  }
  Future<void> _savePlaybackSetting(String key,Object value) async {
    final prefs=await SharedPreferences.getInstance();
    if(value is bool) await prefs.setBool(key,value);
    if(value is int) await prefs.setInt(key,value);
  }

  Future<void> _loadVersion() async {
    try {
      final info = await PackageInfo.fromPlatform();
      if (mounted) setState(() => version = '${info.version} (${info.buildNumber})');
    } catch (_) {
      if (mounted) setState(() => version = 'Version unavailable');
    }
  }

  List<int> _versionParts(String value) => value.replaceFirst(RegExp(r'^[vV]'), '')
      .split('+').first.split('.')
      .map((part) => int.tryParse(part.replaceAll(RegExp(r'[^0-9].*'), '')) ?? 0).toList();

  int _compareVersions(String a, String b) {
    final left = _versionParts(a), right = _versionParts(b);
    for (var i = 0; i < (left.length > right.length ? left.length : right.length); i++) {
      final x = i < left.length ? left[i] : 0, y = i < right.length ? right[i] : 0;
      if (x != y) return x.compareTo(y);
    }
    return 0;
  }

  Future<void> _checkForUpdates() async {
    setState(() => checking = true);
    try {
      final info = await PackageInfo.fromPlatform();
      final response = await http.get(
        Uri.https('api.github.com', '/repos/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases/latest'),
        headers: {'Accept': 'application/vnd.github+json', 'User-Agent': 'High-Tech-Entertainment-App'},
      ).timeout(const Duration(seconds: 15));
      if (!mounted) return;
      if (response.statusCode == 404) {
        _message('No official app update has been published yet. Please check again later.');
        return;
      }
      if (response.statusCode != 200) {
        _message('Update check could not be completed (HTTP ${response.statusCode}). Please try again.');
        return;
      }
      final release = jsonDecode(response.body) as Map<String, dynamic>;
      final latest = (release['tag_name'] ?? '').toString().replaceFirst(RegExp(r'^[vV]'), '');
      if (latest.isEmpty || _compareVersions(latest, info.version) <= 0) {
        _message('You are using the latest published version ($version).');
        return;
      }
      final assets = release['assets'] as List<dynamic>? ?? [];
      String? apkUrl;
      for (final item in assets) {
        final asset = item as Map<String, dynamic>;
        if ((asset['name'] ?? '').toString().toLowerCase().endsWith('.apk')) {
          apkUrl = (asset['browser_download_url'] ?? '').toString();
          break;
        }
      }
      final releaseUrl = (release['html_url'] ?? 'https://github.com/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases/latest').toString();
      final hasApk = apkUrl != null && apkUrl.startsWith('https://');
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('Update available'),
          content: Text('Version $latest is available. You have $version installed. ${hasApk ? 'Download the new APK to update the app.' : 'Open the release page for update details.'}'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Later')),
            FilledButton(
              onPressed: () async {
                Navigator.pop(dialogContext);
                final uri = Uri.tryParse(hasApk ? apkUrl! : releaseUrl);
                if (uri != null) await launchUrl(uri, mode: LaunchMode.externalApplication);
              },
              child: Text(hasApk ? 'Download APK' : 'View release'),
            ),
          ],
        ),
      );
    } catch (_) {
      if (mounted) _message('Could not check for updates. Check your internet connection and try again.');
    } finally {
      if (mounted) setState(() => checking = false);
    }
  }

  void _message(String value) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Settings')),
    body: ListView(padding: const EdgeInsets.all(18), children: [
      const Brand(tagline: 'APP PREFERENCES & INFORMATION'),
      Card(child: Column(children: [
        ListTile(
          leading: const Icon(Icons.system_update_alt, color: Color(0xFFD4AF37)),
          title: const Text('App updates'),
          subtitle: const Text('Check for the latest official Android version'),
          trailing: checking ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.chevron_right),
          onTap: checking ? null : _checkForUpdates,
        ),
        const Divider(height: 1),
        ListTile(leading: const Icon(Icons.info_outline), title: const Text('App version'), subtitle: Text(version)),
      ])),
      const SizedBox(height: 12),
      const Heading('Playback preferences'),
      Card(child: Column(children: [
        SwitchListTile(title:const Text('Smart Autoplay'),subtitle:const Text('Continue with recommended music when the queue ends'),value:autoplaySetting,onChanged:(v){setState(()=>autoplaySetting=v);playback.autoplay=v;_savePlaybackSetting('hte_autoplay',v);}),
        SwitchListTile(title:const Text('Shuffle'),subtitle:const Text('Play queued tracks in random order'),value:shuffleSetting,onChanged:(v){setState(()=>shuffleSetting=v);playback.shuffle=v;player.setShuffleModeEnabled(v);_savePlaybackSetting('hte_shuffle',v);}),
        SwitchListTile(title:const Text('HT Audio Ads'),subtitle:const Text('Play promotional audio between tracks'),value:audioAdsSetting,onChanged:(v){setState(()=>audioAdsSetting=v);playback.audioAdsEnabled=v;_savePlaybackSetting('hte_audio_ads',v);}),
        ListTile(title:const Text('Ad interval'),subtitle:const Text('Play a promotional clip after this many songs'),trailing:DropdownButton<int>(value:adFrequency,items:const [DropdownMenuItem(value:3,child:Text('3 songs')),DropdownMenuItem(value:4,child:Text('4 songs'))],onChanged:(v){if(v!=null){setState(()=>adFrequency=v);playback.adEvery=v;_savePlaybackSetting('hte_ad_frequency',v);}})),
      ])),
      const SizedBox(height: 12),
      Card(child: Column(children: [
        ListTile(leading: const Icon(Icons.language), title: const Text('High-Tech Entertainment website'), trailing: const Icon(Icons.open_in_new),
          onTap: () => launchUrl(Uri.parse('https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/'), mode: LaunchMode.externalApplication)),
        ListTile(leading: const Icon(Icons.privacy_tip_outlined), title: const Text('Privacy policy'), trailing: const Icon(Icons.open_in_new),
          onTap: () => launchUrl(Uri.parse('https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/privacy-policy.html'), mode: LaunchMode.externalApplication)),
        ListTile(leading: const Icon(Icons.delete_outline), title: const Text('Request account deletion'), trailing: const Icon(Icons.open_in_new),
          onTap: () => launchUrl(Uri.parse('https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/account-deletion.html'), mode: LaunchMode.externalApplication)),
      ])),
      const Padding(padding: EdgeInsets.all(20), child: Text('Quality Sound, Global Vibes.\nMusic. Beats. Production. Beyond.',
        textAlign: TextAlign.center, style: TextStyle(color: Colors.white54))),
    ]),
  );
}

Future<List<Map<String, dynamic>>> rows(String table, String orderBy) async {
  final result = await db.from(table).select().eq('is_published', true).order(orderBy, ascending: false);
  return List<Map<String, dynamic>>.from(result);
}

class Busy extends StatelessWidget {
  const Busy({super.key});
  @override
  Widget build(BuildContext context) => const Center(child: CircularProgressIndicator(color: Color(0xFFD4AF37)));
}

class Notice extends StatelessWidget {
  const Notice(this.message, {super.key});
  final String message;
  @override
  Widget build(BuildContext context) => Padding(padding: const EdgeInsets.all(22), child: Text(message, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white60)));
}
