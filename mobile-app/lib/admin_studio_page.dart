import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

const _founderEmail = 'suwilanjisinkamba184@gmail.com';
const _gold = Color(0xFFD9B96E);
final _supabase = Supabase.instance.client;

/// Private founder dashboard. RLS policies remain the actual security boundary;
/// this email check only controls which interface is shown in the client.
class AdminStudioPage extends StatefulWidget {
  const AdminStudioPage({super.key});

  @override
  State<AdminStudioPage> createState() => _AdminStudioPageState();
}

class _AdminStudioPageState extends State<AdminStudioPage> {
  int _section = 0;
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _tracks = [];
  List<Map<String, dynamic>> _artists = [];
  List<Map<String, dynamic>> _releases = [];

  bool get _isFounder =>
      (_supabase.auth.currentUser?.email ?? '').toLowerCase() == _founderEmail;

  @override
  void initState() {
    super.initState();
    _loadDashboard();
  }

  Future<void> _loadDashboard() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      if (!_isFounder) {
        throw const AuthException('Founder access is required.');
      }
      final results = await Future.wait([
        _supabase.from('tracks').select().order('created_at', ascending: false).limit(100),
        _supabase.from('artists').select().limit(100),
        _supabase.from('releases').select().order('created_at', ascending: false).limit(100),
      ]);
      if (!mounted) return;
      setState(() {
        _tracks = List<Map<String, dynamic>>.from(results[0]);
        _artists = List<Map<String, dynamic>>.from(results[1]);
        _releases = List<Map<String, dynamic>>.from(results[2]);
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  Future<void> _togglePublished(Map<String, dynamic> track) async {
    final id = track['id'];
    if (id == null) {
      _message('This track has no record ID and cannot be updated.');
      return;
    }
    final next = track['is_published'] != true;
    try {
      await _supabase.from('tracks').update({'is_published': next}).eq('id', id);
      _message(next ? 'Track published.' : 'Track moved back to drafts.');
      await _loadDashboard();
    } catch (error) {
      _message('Could not update this track: $error');
    }
  }

  void _message(String value) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));
  }

  @override
  Widget build(BuildContext context) {
    if (!_isFounder) {
      return Scaffold(
        appBar: AppBar(title: const Text('Founder Studio')),
        body: const Center(
          child: Padding(
            padding: EdgeInsets.all(28),
            child: Text('This private area is available only to the authorised High-Tech Entertainment founder account.',
                textAlign: TextAlign.center),
          ),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('FOUNDER STUDIO', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, letterSpacing: 1.4)),
          Text('PRIVATE ADMINISTRATION', style: TextStyle(fontSize: 9, color: _gold, letterSpacing: 1.8)),
        ]),
        actions: [
          IconButton(tooltip: 'Refresh', onPressed: _loadDashboard, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: _gold))
          : _error != null
              ? _errorView()
              : RefreshIndicator(
                  color: _gold,
                  onRefresh: _loadDashboard,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
                    children: [
                      _welcomeCard(),
                      const SizedBox(height: 18),
                      _metrics(),
                      const SizedBox(height: 22),
                      _sectionTabs(),
                      const SizedBox(height: 14),
                      if (_section == 0) _overview()
                      else if (_section == 1) _tracksView()
                      else if (_section == 2) _artistsView()
                      else _releasesView(),
                    ],
                  ),
                ),
    );
  }

  Widget _errorView() => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.cloud_off, color: _gold, size: 42),
            const SizedBox(height: 12),
            const Text('Studio data could not be loaded', style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white60, fontSize: 12)),
            const SizedBox(height: 16),
            FilledButton(onPressed: _loadDashboard, child: const Text('Try again')),
          ]),
        ),
      );

  Widget _welcomeCard() => Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: _gold.withValues(alpha: .35)),
          gradient: const LinearGradient(
            colors: [Color(0xFF382A15), Color(0xFF171719), Color(0xFF101113)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('HIGH-TECH ENTERTAINMENT', style: TextStyle(color: _gold, fontSize: 10, letterSpacing: 2)),
          SizedBox(height: 9),
          Text('Your studio. Your catalogue.', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
          SizedBox(height: 6),
          Text('Manage published music, artists and releases from one private workspace.',
              style: TextStyle(color: Colors.white70, fontSize: 12, height: 1.45)),
        ]),
      );

  Widget _metrics() => Row(children: [
        Expanded(child: _metric('TRACKS', _tracks.length.toString(), Icons.music_note)),
        const SizedBox(width: 9),
        Expanded(child: _metric('PUBLISHED', _tracks.where((e) => e['is_published'] == true).length.toString(), Icons.public)),
        const SizedBox(width: 9),
        Expanded(child: _metric('ARTISTS', _artists.length.toString(), Icons.people_outline)),
      ]);

  Widget _metric(String label, String value, IconData icon) => Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFF141619),
          borderRadius: BorderRadius.circular(15),
          border: Border.all(color: Colors.white.withValues(alpha: .07)),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, color: _gold, size: 18),
          const SizedBox(height: 9),
          Text(value, style: const TextStyle(fontSize: 23, fontWeight: FontWeight.bold)),
          Text(label, style: const TextStyle(color: Colors.white54, fontSize: 9, letterSpacing: 1)),
        ]),
      );

  Widget _sectionTabs() => SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(children: [
          _tab('Overview', 0),
          _tab('Music uploads', 1),
          _tab('Artists', 2),
          _tab('Releases', 3),
        ]),
      );

  Widget _tab(String title, int index) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ChoiceChip(
          label: Text(title),
          selected: _section == index,
          onSelected: (_) => setState(() => _section = index),
          selectedColor: _gold,
          labelStyle: TextStyle(
            color: _section == index ? const Color(0xFF17130B) : Colors.white70,
            fontWeight: FontWeight.w700,
            fontSize: 11,
          ),
          side: BorderSide(color: _section == index ? _gold : Colors.white24),
          backgroundColor: const Color(0xFF151619),
        ),
      );

  Widget _overview() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Recent catalogue', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        if (_tracks.isEmpty) const _EmptyState('No tracks found in the catalogue yet.')
        else ..._tracks.take(6).map(_trackTile),
        const SizedBox(height: 14),
        const Text('Quick actions', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        _actionTile(Icons.library_music_outlined, 'Manage music', 'Review drafts and published tracks', () => setState(() => _section = 1)),
        _actionTile(Icons.people_outline, 'Manage artists', 'View artist records', () => setState(() => _section = 2)),
        _actionTile(Icons.album_outlined, 'Manage releases', 'Review release records', () => setState(() => _section = 3)),
      ]);

  Widget _tracksView() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Music catalogue', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
        const SizedBox(height: 5),
        const Text('Publish or unpublish existing track records. Audio uploads will be added with the approved Storage picker workflow.',
            style: TextStyle(color: Colors.white54, fontSize: 11)),
        const SizedBox(height: 10),
        if (_tracks.isEmpty) const _EmptyState('No track records found.')
        else ..._tracks.map(_trackTile),
      ]);

  Widget _trackTile(Map<String, dynamic> track) {
    final published = track['is_published'] == true;
    final title = (track['title'] ?? 'Untitled track').toString();
    final artist = (track['artist'] ?? track['artist_name'] ?? 'Unknown artist').toString();
    return Card(
      child: ListTile(
        leading: Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            gradient: const LinearGradient(colors: [Color(0xFFB48A43), Color(0xFF21190C)]),
          ),
          child: const Icon(Icons.graphic_eq, color: Color(0xFF17130B)),
        ),
        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(artist, maxLines: 1, overflow: TextOverflow.ellipsis),
        trailing: SizedBox(
          width: 94,
          child: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text(published ? 'PUBLISHED' : 'DRAFT', style: TextStyle(fontSize: 9, color: published ? Colors.greenAccent : Colors.white54, letterSpacing: .8)),
            const SizedBox(height: 3),
            TextButton(
              onPressed: () => _togglePublished(track),
              style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(60, 26)),
              child: Text(published ? 'Unpublish' : 'Publish', style: const TextStyle(color: _gold, fontSize: 10)),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _artistsView() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Artist directory', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        if (_artists.isEmpty) const _EmptyState('No artist records found.')
        else ..._artists.map((artist) {
          final name = (artist['name'] ?? artist['artist_name'] ?? 'Unnamed artist').toString();
          return _actionTile(Icons.person_outline, name, 'Artist profile record', () => _message('Artist profile editing will be connected to the approved artist schema.'));
        }),
      ]);

  Widget _releasesView() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Release catalogue', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        if (_releases.isEmpty) const _EmptyState('No release records found.')
        else ..._releases.map((release) {
          final title = (release['title'] ?? release['name'] ?? 'Untitled release').toString();
          final artist = (release['artist'] ?? release['artist_name'] ?? 'High-Tech Entertainment').toString();
          return _actionTile(Icons.album_outlined, title, artist, () => _message('Release editing will be connected to the approved release schema.'));
        }),
      ]);

  Widget _actionTile(IconData icon, String title, String subtitle, VoidCallback onTap) => Card(
        child: ListTile(
          leading: Icon(icon, color: _gold),
          title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
          trailing: const Icon(Icons.chevron_right),
          onTap: onTap,
        ),
      );
}

class _EmptyState extends StatelessWidget {
  const _EmptyState(this.message);
  final String message;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(22),
        child: Text(message, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white54)),
      );
}
