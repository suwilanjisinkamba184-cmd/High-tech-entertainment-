import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'admin_studio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:share_plus/share_plus.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'playback_engine.dart';
import 'video_player_page.dart';
import 'local_files_page.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

const supabaseUrl = 'https://wqrxacjbsldbnoekbkbf.supabase.co';
const supabaseKey = 'sb_publishable_hB_aRh-dnicpRQq3wiRtWg_JTdhmMda';
const authRedirect = 'com.hightechentertainment.app://login-callback';
final db = Supabase.instance.client;
bool _supabaseInitialized = false;
bool _audioBackgroundInitialized = false;
late final HtePlayback playback;
late final AudioPlayer player;
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
      scaffoldBackgroundColor: const Color(0xFF08090B),
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFFD9B96E),
        brightness: Brightness.dark,
      ).copyWith(
        primary: const Color(0xFFD9B96E),
        onPrimary: const Color(0xFF17130B),
        secondary: const Color(0xFFC7CCD2),
        surface: const Color(0xFF111316),
        outline: const Color(0xFF393A3B),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFF08090B),
        foregroundColor: Color(0xFFF6F3EC),
        elevation: 0,
        centerTitle: false,
        surfaceTintColor: Colors.transparent,
      ),
      cardTheme: CardThemeData(
        color: const Color(0xFF111214),
        elevation: 0,
        margin: const EdgeInsets.symmetric(horizontal: 2, vertical: 5),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
          side: const BorderSide(color: Color(0xFF2D2E30)),
        ),
      ),
      listTileTheme: const ListTileThemeData(
        iconColor: Color(0xFFB9B7B1),
        minVerticalPadding: 10,
        contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 5),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFF0E1012),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFF393A3B)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFF393A3B)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFFD9B96E), width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFFFFAAA4)),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: const Color(0xFFD9B96E),
          foregroundColor: const Color(0xFF17130B),
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 13),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)),
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: const Color(0xFFF6F3EC),
          minimumSize: const Size(48, 48),
          side: const BorderSide(color: Color(0xFF514936)),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)),
        ),
      ),
      dividerTheme: const DividerThemeData(color: Color(0xFF292B2E), thickness: 1),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: Color(0xFFD9B96E),
        linearTrackColor: Color(0xFF292B2E),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: const Color(0xFF1B1D20),
        contentTextStyle: const TextStyle(color: Color(0xFFF6F3EC)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        behavior: SnackBarBehavior.floating,
      ),
      visualDensity: VisualDensity.standard,
    ),
    home: const StartupGate(),
  );
}


class StartupGate extends StatefulWidget {
  const StartupGate({super.key});

  @override
  State<StartupGate> createState() => _StartupGateState();
}

class _StartupGateState extends State<StartupGate> {
  bool _ready = false;
  bool _starting = true;
  String? _error;

  @override
  void initState() {
    super.initState();
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
      if (!_supabaseInitialized) {
        await Supabase.initialize(
          url: supabaseUrl,
          publishableKey: supabaseKey,
        );
        _supabaseInitialized = true;
      }
      // The background audio handler MUST finish initialising before the
      // singleton AudioPlayer is constructed. Mark it ready only after init
      // succeeds: marking an attempt before awaiting it leaves just_audio's
      // late _audioHandler unset if init throws, and a retry then creates a
      // player against an uninitialised handler (the reported crash).
      if (defaultTargetPlatform == TargetPlatform.android &&
          !_audioBackgroundInitialized) {
        await JustAudioBackground.init(
          androidNotificationChannelId: 'com.hightechentertainment.app.audio',
          androidNotificationChannelName: 'High-Tech Music Playback',
          androidNotificationOngoing: true,
        );
        _audioBackgroundInitialized = true;
      }
      playback = HtePlayback();
      player = playback.player;
      // Keep the branded splash visible for the complete startup advert.
      // A short network timeout lets the app open normally when offline.
      await playback.playStartupAd();
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
                SvgPicture.asset(
                  'assets/high-tech-logo.svg',
                  width: 116,
                  height: 116,
                  semanticsLabel: 'High-Tech Entertainment',
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
  final pages = const [HomeTab(), DiscoverTab(), ReleasesTab(), LibraryTab(), AccountTab()];
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: IndexedStack(index: tab, children: pages)),
    bottomNavigationBar: Column(mainAxisSize: MainAxisSize.min, children: [const PlayerBar(), NavigationBar(
      selectedIndex: tab,
      onDestinationSelected: (value) => setState(() => tab = value),
      destinations: const [
        NavigationDestination(icon: Icon(Icons.home_outlined), label: 'Home'),
        NavigationDestination(icon: Icon(Icons.search), label: 'Discover'),
        NavigationDestination(icon: Icon(Icons.album_outlined), label: 'Releases'),
        NavigationDestination(icon: Icon(Icons.library_music_outlined), label: 'Library'),
        NavigationDestination(icon: Icon(Icons.person_outline), label: 'Profile'),
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
    padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
    child: Row(children: [
      SvgPicture.asset('assets/high-tech-logo.svg', width: 58, height: 58),
      const SizedBox(width: 14),
      const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('HIGH-TECH', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, letterSpacing: 2.4, height: 1.1)),
        SizedBox(height: 4),
        Text('ENTERTAINMENT', style: TextStyle(fontSize: 10, color: Color(0xFFD9B96E), letterSpacing: 3.2, fontWeight: FontWeight.w600)),
      ])),
      const Icon(Icons.graphic_eq_rounded, color: Color(0xFFD9B96E), size: 23),
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
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.only(bottom: 28),
    children: [
      const Brand(),
      Container(
        margin: const EdgeInsets.fromLTRB(18, 4, 18, 22),
        padding: const EdgeInsets.fromLTRB(22, 24, 22, 24),
        decoration: BoxDecoration(
          color: const Color(0xFF111214),
          border: Border.all(color: const Color(0xFF51452E)),
          borderRadius: BorderRadius.circular(6),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'INDEPENDENT MUSIC.\nBUILT TO TRAVEL.',
                    style: TextStyle(
                      color: Color(0xFFD9B96E),
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 2.0,
                      height: 1.7,
                    ),
                  ),
                ),
                SvgPicture.asset(
                  'assets/high-tech-logo.svg',
                  width: 74,
                  height: 74,
                  semanticsLabel: 'HT crest',
                ),
              ],
            ),
            const SizedBox(height: 22),
            const Text(
              'Quality sound.\nGlobal ambition.',
              style: TextStyle(
                fontSize: 34,
                height: 1.08,
                fontWeight: FontWeight.w700,
                letterSpacing: -1.2,
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Discover official releases, meet the artists and listen to the sounds of High-Tech Entertainment.',
              style: TextStyle(
                color: Color(0xFFB9B6AE),
                height: 1.65,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 22),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const MusicTab()),
                ),
                icon: const Icon(Icons.headphones_rounded, size: 18),
                label: const Text('Explore the music'),
              ),
            ),
          ],
        ),
      ),
      const Heading('Latest releases'),
      const ReleaseList(horizontal: true),
      const Heading('Featured artists'),
      const ArtistList(horizontal: true),
      Card(child: ListTile(
        leading: const Icon(Icons.people_outline, color: Color(0xFFD9B96E)),
        title: const Text('Explore all artists'),
        subtitle: const Text('Meet the creators behind the sound'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: const Text('Artists')), body: const ArtistsTab()))),
      )),
      const Heading('Watch & explore'),
      Card(child: ListTile(
        leading: const Icon(Icons.video_library_outlined, color: Color(0xFFD9B96E)),
        title: const Text('Music videos'),
        subtitle: const Text('Watch official High-Tech visual releases'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: const Text('Videos')), body: const VideosTab()))),
      )),
      const Heading('Released music'),
      const TrackList(compact: true),
      const Padding(
        padding: EdgeInsets.fromLTRB(20, 24, 20, 8),
        child: Text(
          'HIGH-TECH ENTERTAINMENT  ·  MUSIC. BEATS. PRODUCTION. BEYOND.',
          textAlign: TextAlign.center,
          style: TextStyle(
            color: Color(0xFF77736B),
            fontSize: 9,
            letterSpacing: 1.1,
            height: 1.8,
          ),
        ),
      ),
    ],
  );
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
          children: items.map((item) => InkWell(
            borderRadius: BorderRadius.circular(14),
            onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ArtistDetailPage(artist:item))),
            child: SizedBox(width: 112, child: Column(children: [
              Avatar(item: item),
              const SizedBox(height: 8),
              Text((item['name'] ?? 'Artist').toString(), maxLines: 1, overflow: TextOverflow.ellipsis),
            ])),
          )).toList(),
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
            onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ArtistDetailPage(artist:item))),
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

class ArtistDetailPage extends StatelessWidget {
  const ArtistDetailPage({super.key,required this.artist});
  final Map<String,dynamic> artist;
  @override
  Widget build(BuildContext context) {
    final name=(artist['name']??'Artist').toString();
    return Scaffold(
      appBar:AppBar(title:Text(name)),
      body:ListView(padding:const EdgeInsets.only(bottom:24),children:[
        const SizedBox(height:18),
        Center(child:Avatar(item:artist)),
        const SizedBox(height:12),
        Center(child:Text(name,style:const TextStyle(fontSize:24,fontWeight:FontWeight.bold))),
        Padding(padding:const EdgeInsets.all(20),child:Text((artist['bio']??'High-Tech Entertainment artist').toString(),textAlign:TextAlign.center,style:const TextStyle(color:Colors.white60))),
        const Heading('Artist releases'),
        FutureBuilder<List<Map<String,dynamic>>>(
          future:db.from('tracks').select().eq('is_published',true).eq('artist',name).order('created_at',ascending:false).then((data)=>List<Map<String,dynamic>>.from(data).where(_isPlayableTrack).toList()),
          builder:(context,snapshot)=>snapshot.hasError?const Notice('Unable to load this artist’s tracks.'):!snapshot.hasData?const Busy():snapshot.data!.isEmpty?const Notice('Published tracks by this artist will appear here.'):TrackList(items:snapshot.data!),
        ),
      ]),
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
        height: horizontal ? 260 : null,
        child: ListView.builder(
          scrollDirection: horizontal ? Axis.horizontal : Axis.vertical,
          shrinkWrap: !horizontal,
          physics: horizontal ? null : const NeverScrollableScrollPhysics(),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          itemCount: items.length,
          itemBuilder: (context, index) {
            final item = items[index];
            final spotifyUrl = (item['spotify_url'] ?? '').toString().trim();
            return SizedBox(
              width: horizontal ? 170 : null,
              child: Card(
                clipBehavior: Clip.antiAlias,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    MediaCover(url: item['cover_url']?.toString(), height: horizontal ? 112 : 190),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(12, 11, 12, 4),
                      child: Text(
                        (item['title'] ?? 'Release').toString(),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                    if ((item['release_date'] ?? '').toString().isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Text(
                          item['release_date'].toString(),
                          style: const TextStyle(color: Colors.white54, fontSize: 11),
                        ),
                      ),
                    if (spotifyUrl.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(10, 8, 10, 10),
                        child: SizedBox(
                          width: double.infinity,
                          child: OutlinedButton.icon(
                            icon: const Icon(Icons.play_arrow_rounded, size: 18),
                            label: const Text('Listen on Spotify'),
                            onPressed: () async {
                              final embed = spotifyEmbedUrl(spotifyUrl);
                              try {
                                if (embed != null && defaultTargetPlatform == TargetPlatform.android) {
                                  if (!context.mounted) return;
                                  await Navigator.of(context).push(MaterialPageRoute(
                                    builder: (_) => SpotifyEmbedPage(
                                      title: (item['title'] ?? 'Spotify release').toString(),
                                      artist: 'High-Tech Entertainment',
                                      embedUrl: embed,
                                    ),
                                  ));
                                } else {
                                  final uri = Uri.tryParse(spotifyUrl);
                                  if (uri != null && await canLaunchUrl(uri)) {
                                    await launchUrl(uri, mode: LaunchMode.externalApplication);
                                  }
                                }
                              } catch (error) {
                                if (context.mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Could not open Spotify: $error')),
                                  );
                                }
                              }
                            },
                          ),
                        ),
                      ),
                  ],
                ),
              ),
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
    future: items == null ? playableTracks() : Future.value(items!.where(_isPlayableTrack).toList()),
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
              IconButton(tooltip: 'Share track', onPressed: () => Share.share('${item['title']} — ${item['artist']}\nHigh-Tech Entertainment'), icon: const Icon(Icons.share_outlined, size: 20)),
              IconButton(
              icon: const Icon(Icons.play_circle_fill, color: Color(0xFFD4AF37), size: 34),
              onPressed: () async {
                final rawUrl = (item['audio_url'] ?? '').toString().trim();
                final embedUrl = spotifyEmbedUrl(rawUrl);
                if (isSpotifyUrl(rawUrl)) {
                  await player.stop();
                  playback.currentTrack.value = null;
                  playback.queueView.value = [];
                  if (embedUrl != null && defaultTargetPlatform == TargetPlatform.android) {
                    if (context.mounted) {
                      await Navigator.of(context).push(MaterialPageRoute(
                        builder: (_) => SpotifyEmbedPage(
                          title: (item['title'] ?? 'Spotify').toString(),
                          artist: (item['artist'] ?? 'High-Tech Entertainment').toString(),
                          embedUrl: embedUrl,
                        ),
                      ));
                    }
                  } else {
                    final uri = Uri.tryParse(rawUrl);
                    if (uri != null) await launchUrl(uri, mode: LaunchMode.externalApplication);
                  }
                  return;
                }
                try {
                  final audioQueue = items.where((track) => !isSpotifyUrl((track['audio_url'] ?? '').toString())).toList();
                  await playback.playTrack(item, audioQueue);
                } catch (error) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Playback failed: $error')),
                    );
                  }
                }
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
      if (playback.adPlaying.value) {
        return const Material(
          color: Color(0xFF1B1B1B),
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(children: [
              Icon(Icons.campaign, color: Color(0xFFD9B96E)),
              SizedBox(width: 10),
              Expanded(child: Text('High-Tech Entertainment • Advertisement')),
              Icon(Icons.graphic_eq, color: Color(0xFFD9B96E)),
            ]),
          ),
        );
      }
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
    appBar: AppBar(title: const Text('Now playing')),
    body: ValueListenableBuilder<bool>(
      valueListenable: playback.adPlaying,
      builder: (context, isAd, _) {
        if (isAd) {
          return const Center(child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.campaign, size: 64, color: Color(0xFFD9B96E)),
              SizedBox(height: 18),
              Text('Advertisement', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
              SizedBox(height: 8),
              Text('Please listen until the advert finishes.',
                style: TextStyle(color: Colors.white60)),
              SizedBox(height: 18),
              CircularProgressIndicator(color: Color(0xFFD9B96E)),
            ],
          ));
        }
        return ValueListenableBuilder<Map<String,dynamic>?>(
          valueListenable: playback.currentTrack,
          builder: (context, track, _) => track == null
            ? const Notice('Choose a track to start listening.')
            : Padding(
              padding: const EdgeInsets.all(24),
              child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(22),
                  child: SizedBox(width: 260, height: 260,
                    child: MediaCover(url: track['cover_url']?.toString(), height: 260)),
                ),
                const SizedBox(height: 24),
                Text((track['title'] ?? 'Now playing').toString(),
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                  textAlign: TextAlign.center),
                Text((track['artist'] ?? 'High-Tech Entertainment').toString(),
                  style: const TextStyle(color: Colors.white60)),
                ValueListenableBuilder<String?>(
                  valueListenable: playback.playbackError,
                  builder: (context, error, _) => error == null
                    ? const SizedBox(height: 12)
                    : Padding(padding: const EdgeInsets.only(top: 12),
                      child: Text(error, textAlign: TextAlign.center,
                        style: const TextStyle(color: Color(0xFFFFAAA4), fontSize: 12))),
                ),
                const SizedBox(height: 20),
                StreamBuilder<Duration>(
                  stream: player.positionStream,
                  initialData: Duration.zero,
                  builder: (context, pos) {
                    final duration = player.duration ?? Duration.zero;
                    final position = pos.data ?? Duration.zero;
                    final value = duration.inMilliseconds == 0 ? 0.0 :
                      position.inMilliseconds.clamp(0, duration.inMilliseconds).toDouble();
                    return Column(children: [
                      Slider(value: value,
                        max: duration.inMilliseconds == 0 ? 1.0 : duration.inMilliseconds.toDouble(),
                        onChanged: (v) => player.seek(Duration(milliseconds: v.round()))),
                      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [Text(_time(position)), Text(_time(duration))]),
                    ]);
                  },
                ),
                const SizedBox(height: 12),
                Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                  IconButton(onPressed: () => playback.toggleShuffle(),
                    icon: Icon(playback.shuffle ? Icons.shuffle_on : Icons.shuffle)),
                  IconButton(iconSize: 38, onPressed: () => playback.previous(),
                    icon: const Icon(Icons.skip_previous)),
                  StreamBuilder<PlayerState>(
                    stream: player.playerStateStream,
                    builder: (context, s) {
                      final playing = s.data?.playing ?? false;
                      return IconButton(iconSize: 64, color: const Color(0xFFD4AF37),
                        onPressed: () => playing ? player.pause() : player.play(),
                        icon: Icon(playing ? Icons.pause_circle_filled : Icons.play_circle_fill));
                    },
                  ),
                  IconButton(iconSize: 38, onPressed: () => playback.next(),
                    icon: const Icon(Icons.skip_next)),
                  IconButton(onPressed: () => playback.setRepeat(true),
                    icon: const Icon(Icons.repeat_one)),
                ]),
                const Text('Quality Sound, Global Vibes',
                  style: TextStyle(color: Colors.white54, fontSize: 12)),
              ]),
            ),
        );
      },
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
      results = await publishedTracks(term);
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

class ReleasesTab extends StatelessWidget {
  const ReleasesTab({super.key});
  @override
  Widget build(BuildContext context) => Column(children: [
    const Brand(tagline: 'OFFICIAL RELEASES'),
    const Align(alignment: Alignment.centerLeft, child: Padding(
      padding: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Text('Latest releases', style: TextStyle(fontSize: 25, fontWeight: FontWeight.bold)),
    )),
    const Expanded(child: ReleaseList()),
  ]);
}

class LibraryTab extends StatelessWidget {
  const LibraryTab({super.key});
  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 3,
    child: Column(children: [
      const Brand(tagline: 'YOUR MUSIC COLLECTION'),
      const TabBar(tabs:[
        Tab(text:'Favourites'),
        Tab(text:'Playlists'),
        Tab(text:'On device'),
      ],labelColor:Color(0xFFD9B96E)),
      const Expanded(child:TabBarView(children:[
        SavedTab(),
        PlaylistsTab(),
        LocalFilesTab(),
      ])),
    ]),
  );
}

class PlaylistsTab extends StatefulWidget {
  const PlaylistsTab({super.key});
  @override
  State<PlaylistsTab> createState() => _PlaylistsTabState();
}
class _PlaylistsTabState extends State<PlaylistsTab> {
  List<Map<String,dynamic>> items=[];
  bool loading=true;
  @override
  void initState(){super.initState();load();}
  Future<void> load() async {
    setState(()=>loading=true);
    try {
      final user=db.auth.currentUser;
      if(user==null){items=[];} else {
        final data=await db.from('playlists').select().eq('user_id',user.id).order('created_at',ascending:false);
        items=List<Map<String,dynamic>>.from(data);
      }
    } catch(e){debugPrint('Playlist load failed: $e');}
    if(mounted)setState(()=>loading=false);
  }
  Future<void> create() async {
    final controller=TextEditingController();
    final name=await showDialog<String>(context:context,builder:(ctx)=>AlertDialog(
      title:const Text('New playlist'),
      content:TextField(controller:controller,autofocus:true,maxLength:100,decoration:const InputDecoration(labelText:'Playlist name')),
      actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('Cancel')),
        FilledButton(onPressed:()=>Navigator.pop(ctx,controller.text.trim()),child:const Text('Create'))],
    ));
    if(!mounted){controller.dispose();return;}
    controller.dispose();
    if(name==null||name.isEmpty)return;
    final user=db.auth.currentUser;
    if(user==null){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Sign in to create playlists.')));return;}
    try {await db.from('playlists').insert({'user_id':user.id,'name':name});await load();}
    catch(e){if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('Could not create playlist: $e')));}
  }
  @override
  Widget build(BuildContext context)=>loading?const Busy():Column(children:[
    Padding(padding:const EdgeInsets.fromLTRB(16,12,16,6),child:Row(children:[
      const Expanded(child:Text('Your playlists',style:TextStyle(fontWeight:FontWeight.w700))),
      FilledButton.tonalIcon(onPressed:create,icon:const Icon(Icons.add),label:const Text('Create')),
    ])),
    Expanded(child:items.isEmpty?const Notice('Create a playlist to organise your favourite High-Tech tracks.'):
      ListView.builder(itemCount:items.length,itemBuilder:(ctx,i){
        final item=items[i];
        return Card(child:ListTile(
          leading:const CircleAvatar(backgroundColor:Color(0xFF30260E),child:Icon(Icons.queue_music,color:Color(0xFFD9B96E))),
          title:Text((item['name']??'Playlist').toString()),
          subtitle:Text((item['description']??'Your personal collection').toString()),
          trailing:const Icon(Icons.chevron_right),
          onTap:()=>Navigator.push(context,MaterialPageRoute(builder:(_)=>PlaylistDetailPage(playlist:item))),
        ));
      })),
  ]);
}

class PlaylistDetailPage extends StatefulWidget {
  const PlaylistDetailPage({super.key,required this.playlist});
  final Map<String,dynamic> playlist;
  @override
  State<PlaylistDetailPage> createState()=>_PlaylistDetailPageState();
}
class _PlaylistDetailPageState extends State<PlaylistDetailPage>{
  List<Map<String,dynamic>> tracks=[];
  bool loading=true;
  @override
  void initState(){super.initState();load();}
  Future<void> load() async {
    setState(()=>loading=true);
    try {
      final links=await db.from('playlist_tracks').select('track_id').eq('playlist_id',widget.playlist['id']).order('position');
      final ids=links.map((e)=>e['track_id'].toString()).toList();
      if(ids.isEmpty){tracks=[];} else {
        final data=await db.from('tracks').select().eq('is_published',true).inFilter('id',ids);
        final byId={for(final row in data) row['id'].toString():Map<String,dynamic>.from(row)};
        tracks=ids.where(byId.containsKey).map((id)=>byId[id]!).toList();
      }
    }catch(e){debugPrint('Playlist tracks load failed: $e');}
    if(mounted)setState(()=>loading=false);
  }
  Future<void> addTracks() async {
    try {
      final data=await publishedTracks();
      if(!mounted)return;
      final selected=await showDialog<List<Map<String,dynamic>>>(context:context,builder:(ctx){
        final chosen=<String>{};
        return StatefulBuilder(builder:(ctx,setDialog)=>AlertDialog(
          title:const Text('Add tracks'),
          content:SizedBox(width:360,height:360,child:ListView(children:data.map((track){
            final id=track['id'].toString();
            return CheckboxListTile(value:chosen.contains(id),title:Text((track['title']??'Track').toString()),subtitle:Text((track['artist']??'').toString()),
              onChanged:(v)=>setDialog(()=>v==true?chosen.add(id):chosen.remove(id)));
          }).toList())),
          actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('Cancel')),
            FilledButton(onPressed:()=>Navigator.pop(ctx,data.where((e)=>chosen.contains(e['id'].toString())).toList()),child:const Text('Add selected'))],
        ));
      });
      if(selected==null||selected.isEmpty)return;
      final current=await db.from('playlist_tracks').select('track_id').eq('playlist_id',widget.playlist['id']);
      final existing=current.map((e)=>e['track_id'].toString()).toSet();
      final fresh=selected.where((e)=>!existing.contains(e['id'].toString())).toList();
      for(var i=0;i<fresh.length;i++){
        await db.from('playlist_tracks').insert({'playlist_id':widget.playlist['id'],'track_id':fresh[i]['id'],'position':tracks.length+i});
      }
      await load();
    }catch(e){if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('Could not add tracks: $e')));}
  }
  @override
  Widget build(BuildContext context)=>Scaffold(
    appBar:AppBar(title:Text((widget.playlist['name']??'Playlist').toString())),
    body:loading?const Busy():Column(children:[
      Padding(padding:const EdgeInsets.all(16),child:Row(children:[
        Expanded(child:Text('${tracks.length} tracks',style:const TextStyle(color:Colors.white60))),
        FilledButton.tonalIcon(onPressed:addTracks,icon:const Icon(Icons.add),label:const Text('Add tracks')),
      ])),
      Expanded(child:tracks.isEmpty?const Notice('This playlist is empty. Add tracks from the High-Tech catalogue.'):TrackList(items:tracks)),
    ]),
  );
}

class NotificationsPage extends StatelessWidget {
  const NotificationsPage({super.key});
  @override
  Widget build(BuildContext context)=>Scaffold(
    appBar:AppBar(title:const Text('Notifications')),
    body:const Center(child:Padding(padding:EdgeInsets.all(28),child:Column(
      mainAxisSize:MainAxisSize.min,children:[
        Icon(Icons.notifications_none,color:Color(0xFFD9B96E),size:58),
        SizedBox(height:14),Text('You are all caught up',style:TextStyle(fontSize:19,fontWeight:FontWeight.bold)),
        SizedBox(height:8),Text('Release alerts and account notifications will appear here when notifications are enabled.',textAlign:TextAlign.center,style:TextStyle(color:Colors.white60)),
      ],
    ))),
  );
}

class ProfileEditPage extends StatefulWidget {
  const ProfileEditPage({super.key});
  @override
  State<ProfileEditPage> createState()=>_ProfileEditPageState();
}
class _ProfileEditPageState extends State<ProfileEditPage>{
  final _name=TextEditingController();
  final _bio=TextEditingController();
  final _phone=TextEditingController();
  String _avatarUrl='';
  bool _loading=true;
  bool _saving=false;
  @override
  void initState(){super.initState();_load();}
  @override
  void dispose(){_name.dispose();_bio.dispose();_phone.dispose();super.dispose();}
  Future<void> _load() async {
    final user=db.auth.currentUser;
    if(user==null){setState(()=>_loading=false);return;}
    try{
      final row=await db.from('profiles').select('display_name,bio,phone,avatar_url').eq('id',user.id).maybeSingle();
      if(row!=null){_name.text=(row['display_name']??'').toString();_bio.text=(row['bio']??'').toString();_phone.text=(row['phone']??'').toString();_avatarUrl=(row['avatar_url']??'').toString();}
    }catch(e){if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('Could not load profile: $e')));}
    if(mounted)setState(()=>_loading=false);
  }
  Future<void> _save() async {
    final user=db.auth.currentUser;
    if(user==null)return;
    setState(()=>_saving=true);
    try{
      await db.from('profiles').upsert({'id':user.id,'display_name':_name.text.trim(),'bio':_bio.text.trim(),'phone':_phone.text.trim(),'avatar_url':_avatarUrl,'updated_at':DateTime.now().toIso8601String()});
      if(mounted)ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Profile updated.')));
    }catch(e){if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('Profile update failed: $e')));}
    if(mounted)setState(()=>_saving=false);
  }
  @override
  Widget build(BuildContext context)=>Scaffold(
    appBar:AppBar(title:const Text('My profile')),
    body:_loading?const Busy():ListView(padding:const EdgeInsets.all(20),children:[
      const Brand(tagline:'YOUR HIGH-TECH ACCOUNT'),
      TextField(controller:_name,decoration:const InputDecoration(labelText:'Display name')),
      const SizedBox(height:12),TextField(controller:_bio,maxLines:4,decoration:const InputDecoration(labelText:'About you')),
      const SizedBox(height:12),TextField(controller:_phone,keyboardType:TextInputType.phone,decoration:const InputDecoration(labelText:'Phone number (optional)')),
      const SizedBox(height:18),FilledButton(onPressed:_saving?null:_save,child:_saving?const CircularProgressIndicator():const Text('Save profile')),
    ]),
  );
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
    setState(() => busy = true);
    try {
      if (signup) {
        await db.auth.signUp(email: email.text.trim(), password: password.text);
      } else {
        await db.auth.signInWithPassword(email: email.text.trim(), password: password.text);
      }
      if (mounted) setState(() {});
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Authentication request completed. Check your email if confirmation is enabled.')));
    } on AuthException catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> signInWithGoogle() async {
    setState(() => busy = true);
    try {
      await db.auth.signInWithOAuth(
        OAuthProvider.google,
        redirectTo: authRedirect,
        authScreenLaunchMode: LaunchMode.externalApplication,
      );
    } on AuthException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Google sign-in failed: ${error.message}')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not start Google sign-in: $error')),
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
          leading: const Icon(Icons.account_circle_outlined, color: Color(0xFFD4AF37)),
          title: const Text('My profile'),
          subtitle: const Text('Edit your display name and account details'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ProfileEditPage())),
        )),
        Card(child: ListTile(
          leading: const Icon(Icons.settings_outlined, color: Color(0xFFD4AF37)),
          title: const Text('App settings'),
          subtitle: const Text('Updates, app information and support'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettingsPage())),
        )),
        Card(child: ListTile(
          leading: const Icon(Icons.notifications_none, color: Color(0xFFD4AF37)),
          title: const Text('Notifications'),
          subtitle: const Text('Release and account updates'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NotificationsPage())),
        )),
      ] else ...[
        const SizedBox(height: 8),
      ],
      if (user != null && user.id == 'b268df9e-eff5-43b9-a38f-869b33add38b') ...[
        Card(child: ListTile(
          leading: const Icon(Icons.admin_panel_settings_outlined, color: Color(0xFFD9B96E), size: 28),
          title: const Text('HT Admin — Founder Studio', style: TextStyle(fontWeight: FontWeight.w700)),
          subtitle: const Text('Private catalogue, uploads, artists and releases'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const AdminStudioPage())),
        )),
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
        OutlinedButton.icon(
          onPressed: busy ? null : signInWithGoogle,
          icon: const Icon(Icons.g_mobiledata, size: 28),
          label: const Text('Continue with Google'),
          style: OutlinedButton.styleFrom(
            minimumSize: const Size.fromHeight(50),
            side: const BorderSide(color: Color(0xFFD4AF37)),
          ),
        ),
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 12),
          child: Row(children: [
            Expanded(child: Divider()),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 12),
              child: Text('OR USE EMAIL', style: TextStyle(color: Colors.white54, fontSize: 11, letterSpacing: 1.2)),
            ),
            Expanded(child: Divider()),
          ]),
        ),
        TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder())),
        const SizedBox(height: 14),
        FilledButton(onPressed: busy ? null : authenticate, child: Text(busy ? 'Please wait' : (signup ? 'Create account' : 'Sign in'))),
        TextButton(onPressed: () => setState(() => signup = !signup), child: Text(signup ? 'Already registered? Sign in' : 'Create a new account')),
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
  int adFrequency = 8;

  @override
  void initState() { super.initState(); _loadVersion(); _loadPlaybackSettings(); }

  Future<void> _loadPlaybackSettings() async {
    final prefs=await SharedPreferences.getInstance();
    autoplaySetting=prefs.getBool('hte_autoplay')??true;
    shuffleSetting=prefs.getBool('hte_shuffle')??false;
    adFrequency=HtePlayback.advertInterval;
    playback.autoplay=autoplaySetting; playback.shuffle=shuffleSetting; playback.audioAdsEnabled=true; playback.adEvery=HtePlayback.advertInterval;
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
        const ListTile(
          leading: Icon(Icons.campaign_outlined, color: Color(0xFFD4AF37)),
          title: Text('HT Audio Advertisements'),
          subtitle: Text('The startup advert plays before the app opens. The same advert plays after every 8 completed songs. Advert playback cannot be skipped.'),
        ),
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

bool isSpotifyUrl(String raw) {
  final value = raw.trim();
  if (value.startsWith('spotify:')) return true;
  final uri = Uri.tryParse(value);
  final host = (uri?.host ?? '').toLowerCase();
  return host == 'spotify.com' || host.endsWith('.spotify.com') || host == 'spotify.link';
}

String? spotifyEmbedUrl(String raw) {
  final value = raw.trim();
  final uri = Uri.tryParse(value);
  String? type;
  String? id;
  if (value.startsWith('spotify:')) {
    final parts = value.split(':');
    if (parts.length == 3) {
      type = parts[1];
      id = parts[2];
    }
  } else if (uri != null && (uri.host == 'open.spotify.com' || uri.host.endsWith('.spotify.com'))) {
    final parts = uri.pathSegments;
    final offset = parts.isNotEmpty && parts.first == 'embed' ? 1 : 0;
    if (parts.length > offset + 1) {
      type = parts[offset];
      id = parts[offset + 1];
    }
  }
  const supported = {'track', 'album', 'playlist', 'episode', 'show'};
  final validId = id != null && id.isNotEmpty && id.codeUnits.every((unit) =>
      (unit >= 48 && unit <= 57) ||
      (unit >= 65 && unit <= 90) ||
      (unit >= 97 && unit <= 122));
  if (type == null || !supported.contains(type) || !validId) return null;
  return 'https://open.spotify.com/embed/$type/$id?utm_source=generator&theme=0';
}

bool _isPlayableTrack(Map<String, dynamic> track) {
  final title = (track['title'] ?? '').toString().trim();
  final audioUrl = (track['audio_url'] ?? '').toString().trim();
  final uri = Uri.tryParse(audioUrl);
  return title.isNotEmpty &&
      (isSpotifyUrl(audioUrl) || (uri != null && uri.scheme == 'https' && uri.host.isNotEmpty));
}

Future<List<Map<String, dynamic>>> publishedTracks([String term = '']) async {
  var query = db.from('tracks')
      .select()
      .eq('is_published', true);
  if (term.trim().isNotEmpty) {
    query = query.ilike('title', '%${term.trim()}%');
  }
  final data = await query.order('created_at', ascending: false).limit(100);
  return List<Map<String, dynamic>>.from(data).where(_isPlayableTrack).toList();
}

Future<List<Map<String, dynamic>>> playableTracks() => publishedTracks();

class SpotifyEmbedPage extends StatefulWidget {
  const SpotifyEmbedPage({super.key, required this.title, required this.artist, required this.embedUrl});
  final String title;
  final String artist;
  final String embedUrl;
  @override
  State<SpotifyEmbedPage> createState() => _SpotifyEmbedPageState();
}

class _SpotifyEmbedPageState extends State<SpotifyEmbedPage> {
  late final WebViewController controller;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(const Color(0xFF08090B))
      ..setNavigationDelegate(NavigationDelegate(
        onPageFinished: (_) {
          if (mounted) setState(() => loading = false);
        },
      ))
      ..loadRequest(Uri.parse(widget.embedUrl));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.title, maxLines: 1, overflow: TextOverflow.ellipsis)),
    body: Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(widget.artist, style: const TextStyle(
            color: Color(0xFFD9B96E), letterSpacing: 1.2, fontSize: 11, fontWeight: FontWeight.w700)),
          const SizedBox(height: 5),
          const Text('Now playing on Spotify', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          const Text('Playback is provided by Spotify. Sign in to Spotify if prompted.',
            style: TextStyle(color: Colors.white60, fontSize: 12)),
        ]),
      ),
      Expanded(child: Stack(children: [
        WebViewWidget(controller: controller),
        if (loading) const Center(child: CircularProgressIndicator(color: Color(0xFFD9B96E))),
      ])),
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
  Widget build(BuildContext context) => const Center(
    child: CircularProgressIndicator(color: Color(0xFFD4AF37)),
  );
}

class Notice extends StatelessWidget {
  const Notice(this.message, {super.key});
  final String message;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(22),
    child: Text(message, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white60)),
  );
}