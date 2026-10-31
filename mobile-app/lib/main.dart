import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:share_plus/share_plus.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:just_audio/just_audio.dart';
import 'package:url_launcher/url_launcher.dart';

const supabaseUrl = 'https://wqrxacjbsldbnoekbkbf.supabase.co';
const supabaseKey = 'sb_publishable_hB_aRh-dnicpRQq3wiRtWg_JTdhmMda';
const authRedirect = 'com.hightechentertainment.app://login-callback';
final db = Supabase.instance.client;
final player = AudioPlayer();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Supabase.initialize(url: supabaseUrl, publishableKey: supabaseKey);
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
    home: const MainScreen(),
  );
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});
  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int tab = 0;
  final pages = const [HomeTab(), DiscoverTab(), ArtistsTab(), MusicTab(), SavedTab(), VideosTab(), AccountTab()];
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: IndexedStack(index: tab, children: pages)),
    bottomNavigationBar: NavigationBar(
      selectedIndex: tab,
      onDestinationSelected: (value) => setState(() => tab = value),
      destinations: const [
        NavigationDestination(icon: Icon(Icons.home_outlined), label: 'Home'),
        NavigationDestination(icon: Icon(Icons.search), label: 'Discover'),
        NavigationDestination(icon: Icon(Icons.people_outline), label: 'Artists'),
        NavigationDestination(icon: Icon(Icons.music_note_outlined), label: 'Music'),
        NavigationDestination(icon: Icon(Icons.favorite_border), label: 'Saved'),
        NavigationDestination(icon: Icon(Icons.video_library_outlined), label: 'Videos'),
        NavigationDestination(icon: Icon(Icons.person_outline), label: 'Account'),
      ],
    ),
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
      if (snapshot.hasError) return const Notice('Unable to load artists: ${snapshot.error}. Check connection, Data API access and RLS.');
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
      if (snapshot.hasError) return const Notice('Unable to load releases: ${snapshot.error}.');
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
    const PlayerBar(),
  ]);
}

class TrackList extends StatelessWidget {
  const TrackList({super.key, this.compact = false});
  final bool compact;
  @override
  Widget build(BuildContext context) => FutureBuilder<List<Map<String, dynamic>>>(
    future: rows('tracks', 'created_at'),
    builder: (context, snapshot) {
      if (snapshot.hasError) return const Notice('Unable to load tracks: ${snapshot.error}. Check connection, Data API access and RLS.');
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
                final audio = (item['audio_url'] ?? '').toString();
                if (audio.startsWith('http')) {
                  if (audio.contains('spotify.com')) {
                    final uri = Uri.tryParse(audio.replaceFirst('/embed/', '/'));
                    if (uri != null) await launchUrl(uri, mode: LaunchMode.externalApplication);
                  } else {
                    try { await player.setUrl(audio); await player.play(); } catch (error) { if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Playback failed: $error'))); }
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
  Widget build(BuildContext context) => StreamBuilder<PlayerState>(
    stream: player.playerStateStream,
    builder: (context, snapshot) {
      final active = snapshot.data?.playing ?? false;
      return Container(
        color: const Color(0xFF171717),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Row(children: [
          const Icon(Icons.graphic_eq, color: Color(0xFFD4AF37)),
          const Expanded(child: Text('High-Tech Player')),
          IconButton(onPressed: () => active ? player.pause() : player.play(), icon: Icon(active ? Icons.pause : Icons.play_arrow, color: const Color(0xFFD4AF37))),
          IconButton(onPressed: () => player.stop(), icon: const Icon(Icons.stop)),
        ]),
      );
    },
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
                final link = Uri.tryParse((item['video_url'] ?? '').toString());
                if (link != null && link.hasScheme) await launchUrl(link, mode: LaunchMode.externalApplication);
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
  final prefs = await SharedPreferences.getInstance();
  final ids = prefs.getStringList('hte_favourites') ?? <String>[];
  if (ids.contains(id)) { ids.remove(id); } else { ids.add(id); }
  await prefs.setStringList('hte_favourites', ids);
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
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    setState(() => loading = true);
    final prefs = await SharedPreferences.getInstance();
    final ids = prefs.getStringList('hte_favourites') ?? <String>[];
    try {
      if (ids.isEmpty) { saved = []; }
      else { saved = List<Map<String,dynamic>>.from(await db.from('tracks').select().inFilter('id', ids)); }
    } catch (_) { saved = []; }
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
  const AccountTab({super.key});
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


  Future<void> socialSignIn(OAuthProvider provider) async {
    try {
      final opened = await db.auth.signInWithOAuth(provider, redirectTo: authRedirect);
      if (!opened && mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Unable to open the sign-in page.')));
    } on AuthException catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Social sign-in is not configured: $error')));
    }
  }

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

  @override
  Widget build(BuildContext context) {
    final user = db.auth.currentUser;
    return ListView(padding: const EdgeInsets.all(20), children: [
      const Brand(),
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
        FilledButton(onPressed: busy ? null : authenticate, child: Text(busy ? 'Please wait' : (signup ? 'Create account' : 'Sign in'))),
        TextButton(onPressed: () => setState(() => signup = !signup), child: Text(signup ? 'Already registered? Sign in' : 'Create a new account')),
        const Padding(padding: EdgeInsets.symmetric(vertical: 10), child: Text('OR CONTINUE WITH', textAlign: TextAlign.center, style: TextStyle(color: Colors.white54, letterSpacing: 1.5, fontSize: 11))),
        OutlinedButton.icon(onPressed: busy ? null : () => socialSignIn(OAuthProvider.google), icon: const Icon(Icons.g_mobiledata), label: const Text('Continue with Google')),
        OutlinedButton.icon(onPressed: busy ? null : () => socialSignIn(OAuthProvider.facebook), icon: const Icon(Icons.facebook), label: const Text('Continue with Facebook')),
        OutlinedButton.icon(onPressed: busy ? null : () => socialSignIn(OAuthProvider.twitter), icon: const Icon(Icons.alternate_email), label: const Text('Continue with X / Twitter')),
        const Text('Instagram is not a Supabase sign-in provider. The app can link to the official Instagram profile, but Instagram login cannot be enabled as a Supabase provider.', textAlign: TextAlign.center, style: TextStyle(color: Colors.white54, fontSize: 11)),
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
    ]);
  }
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
