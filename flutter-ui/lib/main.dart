import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';

const _gold = Color(0xFFD7A83E);
const _black = Color(0xFF080808);
const _surface = Color(0xFF151515);
const _startupChannel = MethodChannel('com.hightechentertainment.app/startup');

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const HighTechEntertainmentApp());
}

class HighTechEntertainmentApp extends StatelessWidget {
  const HighTechEntertainmentApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'High-Tech Entertainment',
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: _black,
        colorScheme: const ColorScheme.dark(
          primary: _gold,
          secondary: _gold,
          surface: _surface,
        ),
        useMaterial3: true,
        fontFamily: 'Roboto',
      ),
      home: const StartupExperience(),
    );
  }
}

class StartupExperience extends StatefulWidget {
  const StartupExperience({super.key});

  @override
  State<StartupExperience> createState() => _StartupExperienceState();
}

class _StartupExperienceState extends State<StartupExperience>
    with TickerProviderStateMixin {
  static const _duration = Duration(seconds: 15);
  late final AnimationController _motion = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2600),
  )..repeat(reverse: true);
  late final AnimationController _progress = AnimationController(
    vsync: this,
    duration: _duration,
  );
  Timer? _timer;
  bool _finished = false;

  @override
  void initState() {
    super.initState();
    _startExperience();
  }

  Future<void> _startExperience() async {
    // Native Kotlin owns the sound. The visual timer begins on the same
    // launch frame; a missing bridge must never block the app from opening.
    try {
      await _startupChannel.invokeMethod<void>('playStartup');
    } on PlatformException {
      // Native audio is optional during module-only previews and CI tests.
    } on MissingPluginException {
      // The host bridge is wired in the native integration phase.
    }

    if (!mounted) return;
    _progress.forward();
    _timer = Timer(_duration, () {
      _startupChannel.invokeMethod<void>('stopStartup');
      if (!mounted) return;
      setState(() => _finished = true);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _startupChannel.invokeMethod<void>('stopStartup');
    _motion.dispose();
    _progress.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_finished) return const ListenerHomeScreen();

    return Scaffold(
      body: AnimatedBuilder(
        animation: Listenable.merge([_motion, _progress]),
        builder: (context, _) {
          final pulse = 0.96 + (_motion.value * 0.055);
          return Stack(
            fit: StackFit.expand,
            children: [
              const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    center: Alignment(0, -0.18),
                    radius: 0.95,
                    colors: [Color(0xFF292012), _black, _black],
                    stops: [0, 0.58, 1],
                  ),
                ),
              ),
              Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Transform.scale(
                      scale: pulse,
                      child: Container(
                        width: 184,
                        height: 184,
                        padding: const EdgeInsets.all(23),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: _gold.withValues(alpha: 0.62),
                            width: 1.2,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: _gold.withValues(
                                alpha: 0.08 + (_motion.value * 0.12),
                              ),
                              blurRadius: 38,
                              spreadRadius: 8,
                            ),
                          ],
                        ),
                        child: SvgPicture.asset(
                          'assets/high-tech-logo.svg',
                          fit: BoxFit.contain,
                        ),
                      ),
                    ),
                    const SizedBox(height: 34),
                    const Text(
                      'HIGH-TECH',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 5.2,
                      ),
                    ),
                    const SizedBox(height: 7),
                    const Text(
                      'ENTERTAINMENT',
                      style: TextStyle(
                        color: _gold,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 4.3,
                      ),
                    ),
                    const SizedBox(height: 42),
                    SizedBox(
                      width: 190,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(20),
                        child: LinearProgressIndicator(
                          value: _progress.value,
                          minHeight: 3,
                          backgroundColor: Colors.white.withValues(alpha: 0.12),
                          valueColor:
                              const AlwaysStoppedAnimation<Color>(_gold),
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(
                      _startupStatus(_progress.value),
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.58),
                        fontSize: 10,
                        letterSpacing: 1.6,
                      ),
                    ),
                  ],
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 36,
                child: Text(
                  'MUSIC  •  BEATS  •  PRODUCTION  •  BEYOND',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.38),
                    fontSize: 9,
                    letterSpacing: 1.4,
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  String _startupStatus(double progress) {
    if (progress < 0.25) return 'PREPARING YOUR EXPERIENCE';
    if (progress < 0.55) return 'LOADING THE STUDIO';
    if (progress < 0.82) return 'TUNING THE EXPERIENCE';
    return 'ALMOST READY';
  }
}

const _appChannel = MethodChannel('com.hightechentertainment.app/data');

class AppRouterScreen extends StatefulWidget {
  const AppRouterScreen({super.key});

  @override
  State<AppRouterScreen> createState() => _AppRouterScreenState();
}

class _AppRouterScreenState extends State<AppRouterScreen> {
  bool _loading = true;
  bool _authenticated = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadSession();
  }

  Future<void> _loadSession() async {
    try {
      final session = await _appChannel.invokeMapMethod<String, dynamic>('getSession');
      if (!mounted) return;
      setState(() {
        _authenticated = session?['authenticated'] == true;
        _loading = false;
        _error = null;
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = error.message ?? 'Could not connect to account services.';
      });
    } on MissingPluginException {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'The native application services are not available.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator(color: _gold)),
      );
    }
    if (!_authenticated) {
      return AuthScreen(onAuthenticated: _loadSession, error: _error);
    }
    return const ListenerHomeScreen();
  }
}

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key, required this.onAuthenticated, this.error});

  final Future<void> Function() onAuthenticated;
  final String? error;

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _createAccount = false;
  bool _busy = false;
  String? _message;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>(
        _createAccount ? 'signUp' : 'signIn',
        {'email': _email.text.trim(), 'password': _password.text},
      );
      if (!mounted) return;
      if (response?['success'] == true) {
        final session = response?['session'];
        if (session is Map && session['authenticated'] == true) {
          await widget.onAuthenticated();
        } else {
          setState(() => _message =
              response?['message']?.toString() ??
              'Account created. Check your email, then sign in.');
        }
      } else {
        setState(() => _message =
            response?['message']?.toString() ?? 'Sign-in was unsuccessful.');
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Account request failed.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openNativeSignIn() async {
    try {
      await _startupChannel.invokeMethod<void>('openNativeApp');
    } on PlatformException {
      if (mounted) setState(() => _message = 'Native sign-in could not be opened.');
    } on MissingPluginException {
      if (mounted) setState(() => _message = 'Native sign-in is unavailable.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(26),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Center(
                    child: SvgPicture.asset(
                      'assets/high-tech-logo.svg',
                      width: 104,
                      height: 104,
                    ),
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    'WELCOME TO HIGH-TECH',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.6,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Sign in to discover music, artists and original productions.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white60, height: 1.5),
                  ),
                  const SizedBox(height: 30),
                  TextField(
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.email],
                    decoration: const InputDecoration(
                      labelText: 'Email address',
                      prefixIcon: Icon(Icons.email_outlined),
                      filled: true,
                      fillColor: _surface,
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _password,
                    obscureText: true,
                    autofillHints: const [AutofillHints.password],
                    decoration: const InputDecoration(
                      labelText: 'Password',
                      prefixIcon: Icon(Icons.lock_outline),
                      filled: true,
                      fillColor: _surface,
                      border: OutlineInputBorder(),
                    ),
                    onSubmitted: (_) => _submit(),
                  ),
                  if (_message != null || widget.error != null) ...[
                    const SizedBox(height: 12),
                    Text(
                      _message ?? widget.error!,
                      style: const TextStyle(color: Color(0xFFFFB4AB)),
                    ),
                  ],
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    style: FilledButton.styleFrom(
                      backgroundColor: _gold,
                      foregroundColor: _black,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: _busy
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Text(_createAccount ? 'CREATE ACCOUNT' : 'SIGN IN'),
                  ),
                  TextButton(
                    onPressed: _busy
                        ? null
                        : () => setState(() {
                              _createAccount = !_createAccount;
                              _message = null;
                            }),
                    child: Text(_createAccount
                        ? 'Already have an account? Sign in'
                        : 'New here? Create an account'),
                  ),
                  const Divider(height: 30, color: Colors.white24),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _openNativeSignIn,
                    icon: const Icon(Icons.login),
                    label: const Text('Continue with Google'),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'Google sign-in opens the existing secure native authentication flow.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white38, fontSize: 11),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class ListenerHomeScreen extends StatefulWidget {
  const ListenerHomeScreen({super.key});

  @override
  State<ListenerHomeScreen> createState() => _ListenerHomeScreenState();
}

class _ListenerHomeScreenState extends State<ListenerHomeScreen> {
  int _tab = 0;
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _tracks = [];
  List<Map<String, dynamic>> _artists = [];
  List<Map<String, dynamic>> _videos = [];
  Set<String> _favourites = {};

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<List<Map<String, dynamic>>> _loadList(String method) async {
    final rows = await _appChannel.invokeListMethod<dynamic>(method);
    return (rows ?? [])
        .whereType<Map>()
        .map((row) => Map<String, dynamic>.from(row))
        .toList();
  }

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final tracksResponse =
          await _appChannel.invokeMapMethod<String, dynamic>('getTracks');
      final tracks = ((tracksResponse?['items'] as List?) ?? [])
          .whereType<Map>()
          .map((row) => Map<String, dynamic>.from(row))
          .toList();
      final artists = await _loadList('getArtists');
      final videos = await _loadList('getVideos');
      final favourites =
          await _appChannel.invokeListMethod<String>('getFavourites') ?? [];
      if (!mounted) return;
      setState(() {
        _tracks = tracks;
        _artists = artists;
        _videos = videos;
        _favourites = favourites.toSet();
        _error = tracksResponse?['error']?.toString();
        _loading = false;
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.message ?? 'Could not load your High-Tech library.';
        _loading = false;
      });
    } on MissingPluginException {
      if (!mounted) return;
      setState(() {
        _error = 'The app data bridge is not available in this build.';
        _loading = false;
      });
    }
  }

  Future<void> _toggleFavourite(String trackId) async {
    final save = !_favourites.contains(trackId);
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>(
        'setFavourite',
        {'trackId': trackId, 'saved': save},
      );
      if (response?['success'] == true && mounted) {
        setState(() {
          if (save) {
            _favourites.add(trackId);
          } else {
            _favourites.remove(trackId);
          }
        });
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(response?['message']?.toString() ?? 'Could not update saved music.')),
        );
      }
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message ?? 'Could not update saved music.')),
        );
      }
    }
  }

  Future<void> _signOut() async {
    try {
      await _appChannel.invokeMethod<void>('signOut');
      if (mounted) {
        setState(() {
          _tracks = [];
          _artists = [];
          _videos = [];
          _favourites = {};
          _tab = 0;
        });
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const AuthScreen(onAuthenticated: _reloadAfterLogin)),
          (_) => false,
        );
      }
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message ?? 'Sign-out failed.')),
        );
      }
    }
  }

  Future<void> _reloadAfterLogin() async {
    if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final titles = ['Music', 'Artists', 'Videos', 'Your Library', 'Profile'];
    return Scaffold(
      appBar: AppBar(
        title: Text(titles[_tab], style: const TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: _refresh,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: _gold))
          : _error != null && _tracks.isEmpty && _artists.isEmpty && _videos.isEmpty
              ? _ErrorState(message: _error!, onRetry: _refresh)
              : IndexedStack(
                  index: _tab,
                  children: [
                    _MusicList(
                      tracks: _tracks,
                      favourites: _favourites,
                      onFavourite: _toggleFavourite,
                      onRefresh: _refresh,
                    ),
                    _ArtistList(artists: _artists),
                    _VideoList(videos: _videos),
                    _MusicList(
                      tracks: _tracks.where((t) => _favourites.contains(t['id'])).toList(),
                      favourites: _favourites,
                      onFavourite: _toggleFavourite,
                      onRefresh: _refresh,
                      emptyMessage: 'Your saved tracks will appear here.',
                    ),
                    _ProfilePanel(onSignOut: _signOut),
                  ],
                ),
      bottomNavigationBar: NavigationBar(
        backgroundColor: _surface,
        indicatorColor: _gold.withValues(alpha: 0.18),
        selectedIndex: _tab,
        onDestinationSelected: (index) => setState(() => _tab = index),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.music_note_outlined), selectedIcon: Icon(Icons.music_note), label: 'Music'),
          NavigationDestination(icon: Icon(Icons.people_outline), selectedIcon: Icon(Icons.people), label: 'Artists'),
          NavigationDestination(icon: Icon(Icons.video_library_outlined), selectedIcon: Icon(Icons.video_library), label: 'Videos'),
          NavigationDestination(icon: Icon(Icons.favorite_border), selectedIcon: Icon(Icons.favorite), label: 'Library'),
          NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: 'Profile'),
        ],
      ),
    );
  }
}

class _MusicList extends StatelessWidget {
  const _MusicList({
    required this.tracks,
    required this.favourites,
    required this.onFavourite,
    required this.onRefresh,
    this.emptyMessage = 'No published tracks are available yet.',
  });

  final List<Map<String, dynamic>> tracks;
  final Set<String> favourites;
  final Future<void> Function(String) onFavourite;
  final Future<void> Function() onRefresh;
  final String emptyMessage;

  @override
  Widget build(BuildContext context) {
    if (tracks.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 110),
            Icon(Icons.library_music_outlined, size: 56, color: _gold.withValues(alpha: 0.7)),
            const SizedBox(height: 16),
            Center(child: Text(emptyMessage, style: const TextStyle(color: Colors.white60))),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: tracks.length,
        separatorBuilder: (_, __) => const Divider(color: Colors.white12),
        itemBuilder: (context, index) {
          final track = tracks[index];
          final id = track['id']?.toString() ?? '';
          final cover = track['coverUrl']?.toString();
          return ListTile(
            contentPadding: const EdgeInsets.symmetric(vertical: 5),
            leading: ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: cover != null && cover.startsWith('https://')
                  ? Image.network(cover, width: 54, height: 54, fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => _coverPlaceholder())
                  : _coverPlaceholder(),
            ),
            title: Text(track['title']?.toString() ?? 'Untitled', maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: Text([
              track['artist']?.toString() ?? 'High-Tech Entertainment',
              if ((track['genre']?.toString() ?? '').isNotEmpty) track['genre'].toString(),
            ].join(' • '), maxLines: 1, overflow: TextOverflow.ellipsis),
            trailing: IconButton(
              tooltip: favourites.contains(id) ? 'Remove from favourites' : 'Add to favourites',
              onPressed: id.isEmpty ? null : () => onFavourite(id),
              icon: Icon(
                favourites.contains(id) ? Icons.favorite : Icons.favorite_border,
                color: favourites.contains(id) ? _gold : Colors.white70,
              ),
            ),
            onTap: () => ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Playback controls are being connected to the native Media3 player.')),
            ),
          );
        },
      ),
    );
  }

  Widget _coverPlaceholder() => Container(
        width: 54,
        height: 54,
        color: _surface,
        child: const Icon(Icons.music_note, color: _gold),
      );
}

class _ArtistList extends StatelessWidget {
  const _ArtistList({required this.artists});
  final List<Map<String, dynamic>> artists;

  @override
  Widget build(BuildContext context) {
    if (artists.isEmpty) {
      return const Center(child: Text('No published artists yet.', style: TextStyle(color: Colors.white60)));
    }
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: artists.length,
      separatorBuilder: (_, __) => const Divider(color: Colors.white12),
      itemBuilder: (context, index) {
        final artist = artists[index];
        final image = artist['imageUrl']?.toString();
        return ListTile(
          leading: CircleAvatar(
            backgroundColor: _surface,
            backgroundImage: image != null && image.startsWith('https://') ? NetworkImage(image) : null,
            child: image == null || !image.startsWith('https://')
                ? const Icon(Icons.person, color: _gold)
                : null,
          ),
          title: Text(artist['name']?.toString() ?? 'Artist'),
          subtitle: Text(artist['bio']?.toString() ?? '', maxLines: 2, overflow: TextOverflow.ellipsis),
        );
      },
    );
  }
}

class _VideoList extends StatelessWidget {
  const _VideoList({required this.videos});
  final List<Map<String, dynamic>> videos;

  @override
  Widget build(BuildContext context) {
    if (videos.isEmpty) {
      return const Center(child: Text('No published videos yet.', style: TextStyle(color: Colors.white60)));
    }
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: videos.length,
      separatorBuilder: (_, __) => const Divider(color: Colors.white12),
      itemBuilder: (context, index) {
        final video = videos[index];
        final thumb = video['thumbnailUrl']?.toString();
        return ListTile(
          leading: ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: thumb != null && thumb.startsWith('https://')
                ? Image.network(thumb, width: 76, height: 54, fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => _videoPlaceholder())
                : _videoPlaceholder(),
          ),
          title: Text(video['title']?.toString() ?? 'Video'),
          subtitle: Text(video['artist']?.toString() ?? 'High-Tech Entertainment'),
          trailing: const Icon(Icons.play_circle_outline, color: _gold),
          onTap: () => ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Video playback integration is the next native-service connection.')),
          ),
        );
      },
    );
  }

  Widget _videoPlaceholder() => Container(
        width: 76,
        height: 54,
        color: _surface,
        child: const Icon(Icons.play_circle_outline, color: _gold),
      );
}

class _ProfilePanel extends StatelessWidget {
  const _ProfilePanel({required this.onSignOut});
  final Future<void> Function() onSignOut;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(22),
      children: [
        const CircleAvatar(
          radius: 38,
          backgroundColor: _surface,
          child: Icon(Icons.person, color: _gold, size: 38),
        ),
        const SizedBox(height: 18),
        const Center(child: Text('Your High-Tech account', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700))),
        const SizedBox(height: 8),
        const Center(child: Text('Profile editing and bookings are being migrated.', style: TextStyle(color: Colors.white54))),
        const SizedBox(height: 28),
        OutlinedButton.icon(
          onPressed: onSignOut,
          icon: const Icon(Icons.logout),
          label: const Text('Sign out'),
        ),
      ],
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});
  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off, color: _gold, size: 42),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white70)),
            const SizedBox(height: 18),
            FilledButton(onPressed: onRetry, child: const Text('TRY AGAIN')),
          ],
        ),
      ),
    );
  }
}
