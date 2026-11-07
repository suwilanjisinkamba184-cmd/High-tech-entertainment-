import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:file_picker/file_picker.dart';

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
    if (_finished) return const AppRouterScreen();

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

class _AppRouterScreenState extends State<AppRouterScreen>
    with WidgetsBindingObserver {
  bool _loading = true;
  bool _authenticated = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadSession();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Google OAuth is completed by the existing native Activity. Refresh the
    // shared Supabase session when Flutter becomes foreground again.
    if (state == AppLifecycleState.resumed) {
      _loadSession();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
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

  Future<void> _requestPasswordReset() async {
    if (_email.text.trim().isEmpty) {
      setState(() => _message = 'Enter your email address first.');
      return;
    }
    setState(() { _busy = true; _message = null; });
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>(
        'requestPasswordReset', {'email': _email.text.trim()});
      if (mounted) {
        setState(() => _message = response?['message']?.toString()
            ?? (response?['success'] == true ? 'Check your email for reset instructions.' : 'Password reset could not be requested.'));
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Password reset could not be requested.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _startGoogleSignIn() async {
    setState(() => _busy = true);
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>('startGoogleOAuth');
      if (response?['success'] != true && mounted) {
        setState(() => _message = response?['message']?.toString() ?? 'Google sign-in could not be started.');
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Google sign-in could not be started.');
    } on MissingPluginException {
      if (mounted) setState(() => _message = 'Google sign-in is unavailable in this build.');
    } finally {
      if (mounted) setState(() => _busy = false);
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
                  if (!_createAccount)
                    TextButton(
                      onPressed: _busy ? null : _requestPasswordReset,
                      child: const Text('Forgot password?'),
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
                    onPressed: _busy ? null : _startGoogleSignIn,
                    icon: const Icon(Icons.login),
                    label: const Text('Continue with Google'),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'Google sign-in opens securely and returns to this Flutter app.',
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
  Map<String, dynamic>? _nowPlayingTrack;
  bool _isPlaying = false;

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

  Future<void> _openVoiceCompanion() async {
    try {
      await _appChannel.invokeMethod<void>('openVoiceCompanion');
    } on PlatformException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? 'Voice Companion could not be opened.')),
      );
    } on MissingPluginException {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Voice Companion is unavailable in this build.')),
      );
    }
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

  Future<void> _playTrack(Map<String, dynamic> track) async {
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>(
        'playTrack',
        {'trackId': track['id']?.toString() ?? ''},
      );
      if (response?['success'] == true && mounted) {
        setState(() {
          _nowPlayingTrack = track;
          _isPlaying = true;
        });
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(response?['message']?.toString() ?? 'Playback could not start.')),
        );
      }
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message ?? 'Playback could not start.')),
        );
      }
    }
  }

  Future<void> _togglePlayback() async {
    final method = _isPlaying ? 'pausePlayback' : 'resumePlayback';
    try {
      await _appChannel.invokeMapMethod<String, dynamic>(method);
      if (mounted) setState(() => _isPlaying = !_isPlaying);
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message ?? 'Player control failed.')),
        );
      }
    }
  }

  Future<void> _playVideo(Map<String, dynamic> video) async {
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>(
        'playVideo',
        {
          'videoId': video['id']?.toString() ?? '',
          'videoUrl': video['videoUrl']?.toString() ?? '',
          'title': video['title']?.toString() ?? '',
        },
      );
      if (response?['success'] != true && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(response?['message']?.toString() ?? 'Video could not be opened.')),
        );
      }
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message ?? 'Video could not be opened.')),
        );
      }
    }
  }

  Future<void> _downloadTrack(Map<String, dynamic> track) async {
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>(
        'downloadTrack',
        {'trackId': track['id']?.toString() ?? ''},
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(response?['success'] == true
              ? 'Download added to Android download manager.'
              : 'This track is not available for download.')),
        );
      }
    } on PlatformException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message ?? 'Download could not start.')),
        );
      }
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
          MaterialPageRoute(builder: (_) => AuthScreen(onAuthenticated: () async {
            if (!mounted) return;
            Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const ListenerHomeScreen()),
            );
          })),
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

  @override
  Widget build(BuildContext context) {
    final titles = ['Music', 'Artists', 'Videos', 'Your Library', 'Profile'];
    return Scaffold(
      appBar: AppBar(
        title: Text(titles[_tab], style: const TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(
            tooltip: 'Open AI Voice Companion',
            onPressed: _openVoiceCompanion,
            icon: const Icon(Icons.graphic_eq_rounded, color: _gold),
          ),
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
                      onPlay: _playTrack,
                      onDownload: _downloadTrack,
                      onRefresh: _refresh,
                    ),
                    _ArtistList(artists: _artists),
                    _VideoList(videos: _videos, onPlay: _playVideo),
                    _LibraryPanel(
                      tracks: _tracks,
                      favourites: _favourites,
                      onFavourite: _toggleFavourite,
                      onPlay: _playTrack,
                      onDownload: _downloadTrack,
                      onRefresh: _refresh,
                    ),
                    _ProfilePanel(onSignOut: _signOut),
                  ],
                ),
      bottomNavigationBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_nowPlayingTrack != null)
            _NowPlayingBar(
              track: _nowPlayingTrack!,
              playing: _isPlaying,
              onToggle: _togglePlayback,
              onNext: () async {
                await _appChannel.invokeMethod<void>('nextTrack');
              },
            ),
          NavigationBar(
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
        ],
      ),
    );
  }
}

class _LibraryPanel extends StatefulWidget {
  const _LibraryPanel({
    required this.tracks,
    required this.favourites,
    required this.onFavourite,
    required this.onPlay,
    required this.onDownload,
    required this.onRefresh,
  });

  final List<Map<String, dynamic>> tracks;
  final Set<String> favourites;
  final Future<void> Function(String) onFavourite;
  final Future<void> Function(Map<String, dynamic>) onPlay;
  final Future<void> Function(Map<String, dynamic>) onDownload;
  final Future<void> Function() onRefresh;

  @override
  State<_LibraryPanel> createState() => _LibraryPanelState();
}

class _LibraryPanelState extends State<_LibraryPanel> {
  List<Map<String, dynamic>> _playlists = [];
  bool _loadingPlaylists = true;
  String? _playlistError;

  @override
  void initState() {
    super.initState();
    _loadPlaylists();
  }

  Future<void> _loadPlaylists() async {
    setState(() {
      _loadingPlaylists = true;
      _playlistError = null;
    });
    try {
      final rows = await _appChannel.invokeListMethod<dynamic>('getPlaylists');
      if (!mounted) return;
      setState(() {
        _playlists = (rows ?? []).whereType<Map>()
            .map((row) => Map<String, dynamic>.from(row)).toList();
        _loadingPlaylists = false;
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        _playlistError = error.message ?? 'Could not load playlists.';
        _loadingPlaylists = false;
      });
    } on MissingPluginException {
      if (!mounted) return;
      setState(() {
        _playlistError = 'Playlist services are unavailable in this build.';
        _loadingPlaylists = false;
      });
    }
  }

  Future<void> _createPlaylist() async {
    final name = TextEditingController();
    final description = TextEditingController();
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create playlist'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: name, autofocus: true, maxLength: 80,
              decoration: const InputDecoration(labelText: 'Playlist name')),
            TextField(controller: description, maxLength: 500,
              decoration: const InputDecoration(labelText: 'Description')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Create')),
        ],
      ),
    );
    if (accepted == true && name.text.trim().isNotEmpty) {
      try {
        final result = await _appChannel.invokeMapMethod<String, dynamic>('createPlaylist', {
          'name': name.text.trim(),
          'description': description.text.trim(),
        });
        if (result?['success'] == true) {
          await _loadPlaylists();
        } else {
          _showMessage(result?['message']?.toString() ?? 'Playlist could not be created.');
        }
      } on PlatformException catch (error) {
        _showMessage(error.message ?? 'Playlist could not be created.');
      }
    }
    name.dispose();
    description.dispose();
  }

  Future<void> _addTrack(String playlistId, Map<String, dynamic> track) async {
    try {
      final result = await _appChannel.invokeMapMethod<String, dynamic>('addTrackToPlaylist', {
        'playlistId': playlistId,
        'trackId': track['id']?.toString() ?? '',
      });
      _showMessage(result?['success'] == true
          ? 'Track added to playlist.'
          : result?['message']?.toString() ?? 'Could not add track.');
    } on PlatformException catch (error) {
      _showMessage(error.message ?? 'Could not add track.');
    }
  }

  Future<List<dynamic>> _loadPlaylistTrackIds(String playlistId) async {
    return await _appChannel.invokeListMethod<dynamic>(
      'getPlaylistTracks', {'playlistId': playlistId}) ?? <dynamic>[];
  }

  Future<void> _deletePlaylist(String id) async {
    try {
      final result = await _appChannel.invokeMapMethod<String, dynamic>('deletePlaylist', {'playlistId': id});
      if (result?['success'] == true) {
        await _loadPlaylists();
      } else {
        _showMessage(result?['message']?.toString() ?? 'Could not delete playlist.');
      }
    } on PlatformException catch (error) {
      _showMessage(error.message ?? 'Could not delete playlist.');
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final favourites = widget.tracks.where((track) => widget.favourites.contains(track['id'])).toList();
    return RefreshIndicator(
      onRefresh: () async {
        await widget.onRefresh();
        await _loadPlaylists();
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              const Expanded(child: Text('YOUR PLAYLISTS', style: TextStyle(
                color: _gold, fontWeight: FontWeight.w800, letterSpacing: 1.2))),
              FilledButton.icon(onPressed: _createPlaylist, icon: const Icon(Icons.add), label: const Text('Create')),
            ],
          ),
          const SizedBox(height: 10),
          if (_loadingPlaylists)
            const Center(child: Padding(
              padding: EdgeInsets.all(18),
              child: CircularProgressIndicator(color: _gold),
            ))
          else if (_playlistError != null)
            ListTile(
              leading: const Icon(Icons.error_outline, color: _gold),
              title: Text(_playlistError!),
              trailing: IconButton(icon: const Icon(Icons.refresh), onPressed: _loadPlaylists),
            )
          else if (_playlists.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 18),
              child: Text('Create a playlist to organise your favourite music.',
                style: TextStyle(color: Colors.white60)),
            )
          else
            ..._playlists.map((playlist) => Card(
              color: _surface,
              child: ExpansionTile(
                leading: const Icon(Icons.queue_music_rounded, color: _gold),
                title: Text(playlist['name']?.toString() ?? 'Playlist'),
                subtitle: Text(playlist['description']?.toString() ?? ''),
                trailing: PopupMenuButton<String>(
                  onSelected: (action) {
                    if (action == 'add') {
                      showModalBottomSheet<void>(
                        context: context,
                        builder: (sheetContext) => SafeArea(
                          child: ListView(
                            shrinkWrap: true,
                            children: [
                              const ListTile(title: Text('Add a track')),
                              ...widget.tracks.map((track) => ListTile(
                                leading: const Icon(Icons.music_note, color: _gold),
                                title: Text(track['title']?.toString() ?? 'Untitled'),
                                subtitle: Text(track['artist']?.toString() ?? ''),
                                onTap: () {
                                  Navigator.pop(sheetContext);
                                  _addTrack(playlist['id'].toString(), track);
                                },
                              )),
                            ],
                          ),
                        ),
                      );
                    } else if (action == 'delete') {
                      _deletePlaylist(playlist['id'].toString());
                    }
                  },
                  itemBuilder: (_) => const [
                    PopupMenuItem(value: 'add', child: Text('Add tracks')),
                    PopupMenuItem(value: 'delete', child: Text('Delete playlist')),
                  ],
                ),
                children: [
                  FutureBuilder<List<dynamic>>(
                    future: _loadPlaylistTrackIds(playlist['id'].toString()),
                    builder: (context, snapshot) {
                      if (snapshot.connectionState == ConnectionState.waiting) {
                        return const Padding(
                          padding: EdgeInsets.all(16),
                          child: CircularProgressIndicator(color: _gold),
                        );
                      }
                      final ids = (snapshot.data ?? []).map((id) => id.toString()).toSet();
                      final rows = widget.tracks.where((track) => ids.contains(track['id']?.toString())).toList();
                      if (rows.isEmpty) {
                        return const ListTile(title: Text('No tracks in this playlist yet.',
                          style: TextStyle(color: Colors.white54)));
                      }
                      return Column(
                        children: rows.map((track) => ListTile(
                          title: Text(track['title']?.toString() ?? 'Untitled'),
                          subtitle: Text(track['artist']?.toString() ?? ''),
                          trailing: IconButton(
                            tooltip: 'Play track',
                            icon: const Icon(Icons.play_arrow, color: _gold),
                            onPressed: () => widget.onPlay(track),
                          ),
                        )).toList(),
                      );
                    },
                  ),
                ],
              ),
            )),
          const Divider(height: 34, color: Colors.white12),
          const Text('SAVED TRACKS', style: TextStyle(
            color: _gold, fontWeight: FontWeight.w800, letterSpacing: 1.2)),
          const SizedBox(height: 8),
          if (favourites.isEmpty)
            const Text('Your saved tracks will appear here.', style: TextStyle(color: Colors.white60))
          else
            ...favourites.map((track) => ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.favorite, color: _gold),
              title: Text(track['title']?.toString() ?? 'Untitled'),
              subtitle: Text(track['artist']?.toString() ?? ''),
              onTap: () => widget.onPlay(track),
              trailing: PopupMenuButton<String>(
                onSelected: (action) {
                  if (action == 'remove') widget.onFavourite(track['id'].toString());
                  if (action == 'download') widget.onDownload(track);
                },
                itemBuilder: (_) => const [
                  PopupMenuItem(value: 'download', child: Text('Download')),
                  PopupMenuItem(value: 'remove', child: Text('Remove from favourites')),
                ],
              ),
            )),
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
    required this.onPlay,
    required this.onDownload,
    required this.onRefresh,
    this.emptyMessage = 'No published tracks are available yet.',
  });

  final List<Map<String, dynamic>> tracks;
  final Set<String> favourites;
  final Future<void> Function(String) onFavourite;
  final Future<void> Function(Map<String, dynamic>) onPlay;
  final Future<void> Function(Map<String, dynamic>) onDownload;
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
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  tooltip: 'Download track',
                  onPressed: id.isEmpty ? null : () => onDownload(track),
                  icon: const Icon(Icons.download_outlined, color: Colors.white70),
                ),
                IconButton(
                  tooltip: favourites.contains(id) ? 'Remove from favourites' : 'Add to favourites',
                  onPressed: id.isEmpty ? null : () => onFavourite(id),
                  icon: Icon(
                    favourites.contains(id) ? Icons.favorite : Icons.favorite_border,
                    color: favourites.contains(id) ? _gold : Colors.white70,
                  ),
                ),
              ],
            ),
            onTap: () => onPlay(track),
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
  const _VideoList({required this.videos, required this.onPlay});
  final List<Map<String, dynamic>> videos;
  final Future<void> Function(Map<String, dynamic>) onPlay;

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
          onTap: () => onPlay(video),
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

class _NowPlayingBar extends StatelessWidget {
  const _NowPlayingBar({
    required this.track,
    required this.playing,
    required this.onToggle,
    required this.onNext,
  });

  final Map<String, dynamic> track;
  final bool playing;
  final Future<void> Function() onToggle;
  final Future<void> Function() onNext;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(0xFF211A10),
      padding: const EdgeInsets.fromLTRB(14, 8, 10, 8),
      child: Row(
        children: [
          const Icon(Icons.graphic_eq, color: _gold),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(track['title']?.toString() ?? 'Now playing',
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w700)),
                Text(track['artist']?.toString() ?? 'High-Tech Entertainment',
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white60, fontSize: 11)),
              ],
            ),
          ),
          IconButton(
            tooltip: playing ? 'Pause' : 'Resume',
            onPressed: onToggle,
            icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded, color: _gold),
          ),
          IconButton(
            tooltip: 'Next track',
            onPressed: onNext,
            icon: const Icon(Icons.skip_next_rounded, color: _gold),
          ),
        ],
      ),
    );
  }
}

class _ProfilePanel extends StatefulWidget {
  const _ProfilePanel({required this.onSignOut});
  final Future<void> Function() onSignOut;

  @override
  State<_ProfilePanel> createState() => _ProfilePanelState();
}

class _ProfilePanelState extends State<_ProfilePanel> {
  final _displayName = TextEditingController();
  final _bio = TextEditingController();
  final _phone = TextEditingController();
  final _bookingName = TextEditingController();
  final _bookingEmail = TextEditingController();
  final _bookingType = TextEditingController(text: 'Music production');
  final _bookingDate = TextEditingController();
  final _bookingMessage = TextEditingController();
  bool _loading = true;
  bool _saving = false;
  String? _message;
  String _avatarUrl = '';
  List<Map<String, dynamic>> _bookings = [];
  Map<String, dynamic>? _founder;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _displayName.dispose();
    _bio.dispose();
    _phone.dispose();
    _bookingName.dispose();
    _bookingEmail.dispose();
    _bookingType.dispose();
    _bookingDate.dispose();
    _bookingMessage.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final profile = await _appChannel.invokeMapMethod<String, dynamic>('getProfile');
      final bookings = await _appChannel.invokeListMethod<dynamic>('getMyBookings') ?? [];
      final founder = await _appChannel.invokeMapMethod<String, dynamic>('getFounderData');
      if (!mounted) return;
      _displayName.text = profile?['displayName']?.toString() ?? '';
      _bio.text = profile?['bio']?.toString() ?? '';
      _phone.text = profile?['phone']?.toString() ?? '';
      _avatarUrl = profile?['avatarUrl']?.toString() ?? '';
      setState(() {
        _bookings = bookings.whereType<Map>().map((row) => Map<String, dynamic>.from(row)).toList();
        _founder = founder?['authorized'] == true ? founder : null;
        _loading = false;
      });
    } on PlatformException catch (error) {
      if (mounted) setState(() {
        _message = error.message ?? 'Could not load account information.';
        _loading = false;
      });
    }
  }

  Future<void> _saveProfile() async {
    setState(() { _saving = true; _message = null; });
    try {
      final result = await _appChannel.invokeMapMethod<String, dynamic>('saveProfile', {
        'displayName': _displayName.text,
        'bio': _bio.text,
        'phone': _phone.text,
        'avatarUrl': _avatarUrl,
      });
      if (mounted) setState(() => _message = result?['success'] == true
          ? 'Profile saved.'
          : result?['message']?.toString() ?? 'Profile could not be saved.');
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Profile could not be saved.');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _submitBooking() async {
    setState(() { _saving = true; _message = null; });
    try {
      final result = await _appChannel.invokeMapMethod<String, dynamic>('submitBooking', {
        'name': _bookingName.text,
        'email': _bookingEmail.text,
        'bookingType': _bookingType.text,
        'requestedDate': _bookingDate.text.trim().isEmpty ? null : _bookingDate.text.trim(),
        'message': _bookingMessage.text,
      });
      if (result?['success'] == true) {
        _bookingName.clear();
        _bookingEmail.clear();
        _bookingDate.clear();
        _bookingMessage.clear();
        await _load();
      }
      if (mounted) setState(() => _message = result?['success'] == true
          ? 'Your booking request has been submitted.'
          : result?['message']?.toString() ?? 'Booking could not be submitted.');
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Booking could not be submitted.');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _setDraftPublished(Map<String, dynamic> draft) async {
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>('setDraftPublished', {
        'table': draft['table'], 'id': draft['id'], 'published': true,
      });
      if (response?['success'] == true) {
        await _load();
      } else if (mounted) {
        setState(() => _message = response?['message']?.toString() ?? 'Publishing was rejected.');
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Publishing was rejected.');
    }
  }

  Future<void> _createCampaign() async {
    final title = TextEditingController();
    final audioUrl = TextEditingController();
    final startsAt = TextEditingController();
    final endsAt = TextEditingController();
    final priority = TextEditingController(text: '0');
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: _surface,
        title: const Text('Create sponsored audio campaign'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: title, decoration: const InputDecoration(labelText: 'Campaign title')),
              TextField(controller: audioUrl, decoration: const InputDecoration(labelText: 'Secure audio URL')),
              TextField(controller: startsAt, decoration: const InputDecoration(labelText: 'Start time (ISO 8601, optional)')),
              TextField(controller: endsAt, decoration: const InputDecoration(labelText: 'End time (ISO 8601, optional)')),
              TextField(controller: priority, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Priority (0–1000)')),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('CANCEL')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('SAVE CAMPAIGN')),
        ],
      ),
    );
    if (confirmed != true) {
      title.dispose(); audioUrl.dispose(); startsAt.dispose(); endsAt.dispose(); priority.dispose();
      return;
    }
    try {
      final result = await _appChannel.invokeMapMethod<String, dynamic>('createCampaign', {
        'title': title.text.trim(),
        'audioUrl': audioUrl.text.trim(),
        'startsAt': startsAt.text.trim().isEmpty ? null : startsAt.text.trim(),
        'endsAt': endsAt.text.trim().isEmpty ? null : endsAt.text.trim(),
        'priority': int.tryParse(priority.text.trim()) ?? 0,
      });
      if (result?['success'] == true) {
        if (mounted) setState(() => _message = 'Campaign saved as unpublished.');
        await _load();
      } else if (mounted) {
        setState(() => _message = result?['message']?.toString() ?? 'Campaign could not be saved.');
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Campaign could not be saved.');
    } finally {
      title.dispose(); audioUrl.dispose(); startsAt.dispose(); endsAt.dispose(); priority.dispose();
    }
  }

  Future<void> _setCampaignPublished(Map<String, dynamic> campaign, bool published) async {
    try {
      final result = await _appChannel.invokeMapMethod<String, dynamic>('setCampaignPublished', {
        'id': campaign['id'],
        'published': published,
      });
      if (result?['success'] == true) {
        if (mounted) setState(() => _message = published ? 'Campaign published.' : 'Campaign unpublished.');
        await _load();
      } else if (mounted) {
        setState(() => _message = result?['message']?.toString() ?? 'Campaign update failed.');
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Campaign update failed.');
    }
  }

  Future<void> _updateBooking(Map<String, dynamic> booking, String status) async {
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>('updateBookingStatus', {
        'id': booking['id'], 'status': status,
      });
      if (response?['success'] == true) {
        await _load();
      } else if (mounted) {
        setState(() => _message = response?['message']?.toString() ?? 'Booking update failed.');
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Booking update failed.');
    }
  }

  Future<String?> _pickAndUploadFounderMedia(String kind) async {
    final picked = await FilePicker.platform.pickFiles(
      type: kind == 'Track' ? FileType.audio : FileType.video,
      withData: kind == 'Track',
    );
    if (picked == null || picked.files.isEmpty) return null;
    final file = picked.files.single;
    final extension = (file.extension ?? '').toLowerCase();
    final contentType = switch (extension) {
      'mp3' => 'audio/mpeg',
      'm4a' => 'audio/mp4',
      'aac' => 'audio/aac',
      'wav' => 'audio/wav',
      'ogg' => 'audio/ogg',
      'mp4' => 'video/mp4',
      'webm' => 'video/webm',
      'mov' => 'video/quicktime',
      'm4v' => 'video/x-m4v',
      _ => '',
    };
    if (contentType.isEmpty) {
      _showFounderMessage('That file format is not supported.');
      return null;
    }
    try {
      final response = kind == 'Track'
          ? await _appChannel.invokeMapMethod<String, dynamic>('uploadAudio', {
              'bytes': file.bytes,
              'contentType': contentType,
              'extension': extension,
            })
          : await _appChannel.invokeMapMethod<String, dynamic>('uploadVideo', {
              'uri': file.path == null ? '' : Uri.file(file.path!).toString(),
              'contentType': contentType,
              'extension': extension,
            });
      if (response?['success'] == true) {
        _showFounderMessage('Media uploaded securely.');
        return response?['url']?.toString();
      }
      _showFounderMessage(response?['message']?.toString() ?? 'Upload failed.');
    } on PlatformException catch (error) {
      _showFounderMessage(error.message ?? 'Upload failed.');
    } on MissingPluginException {
      _showFounderMessage('Media upload is unavailable in this build.');
    }
    return null;
  }

  void _showFounderMessage(String message) {
    if (!mounted) return;
    setState(() => _message = message);
  }

  Future<void> _pickAndUploadAvatar() async {
    final picked = await FilePicker.platform.pickFiles(type: FileType.image, withData: true);
    if (picked == null || picked.files.isEmpty) return;
    final file = picked.files.single;
    final extension = (file.extension ?? '').toLowerCase();
    final contentType = switch (extension) {
      'png' => 'image/png',
      'webp' => 'image/webp',
      'jpg' || 'jpeg' => 'image/jpeg',
      _ => '',
    };
    if (contentType.isEmpty || file.bytes == null) {
      _showFounderMessage('Choose a JPG, PNG or WebP image.');
      return;
    }
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>('uploadAvatar', {
        'bytes': file.bytes,
        'contentType': contentType,
      });
      if (response?['success'] == true) {
        _avatarUrl = response?['url']?.toString() ?? '';
        _showFounderMessage('Profile picture uploaded. Save your profile to apply it.');
      } else {
        _showFounderMessage(response?['message']?.toString() ?? 'Profile picture upload failed.');
      }
    } on PlatformException catch (error) {
      _showFounderMessage(error.message ?? 'Profile picture upload failed.');
    }
  }

  Future<void> _createContent(String kind) async {
    final title = TextEditingController();
    final secondary = TextEditingController();
    final details = TextEditingController();
    final genre = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: _surface,
        title: Text('Create ' + kind.toLowerCase()),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: title, decoration: InputDecoration(
                labelText: kind == 'Artist' ? 'Artist name' : 'Title')),
              if (kind == 'Track' || kind == 'Video')
                TextField(controller: secondary, decoration: const InputDecoration(labelText: 'Artist')),
              if (kind == 'Track' || kind == 'Video') ...[
                TextField(controller: details, readOnly: true, decoration: InputDecoration(
                  labelText: kind == 'Track' ? 'Uploaded audio URL' : 'Uploaded video URL')),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    onPressed: () async {
                      final url = await _pickAndUploadFounderMedia(kind);
                      if (url != null) details.text = url;
                    },
                    icon: const Icon(Icons.upload_file),
                    label: Text(kind == 'Track' ? 'CHOOSE AND UPLOAD AUDIO' : 'CHOOSE AND UPLOAD VIDEO'),
                  ),
                ),
              ],
              if (kind == 'Track')
                TextField(controller: genre, decoration: const InputDecoration(labelText: 'Genre')),
              if (kind == 'Artist' || kind == 'Release')
                TextField(controller: details, decoration: InputDecoration(
                  labelText: kind == 'Artist' ? 'Biography' : 'Description')),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('CANCEL')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('SAVE DRAFT')),
        ],
      ),
    );
    if (confirmed != true) {
      title.dispose(); secondary.dispose(); details.dispose(); genre.dispose();
      return;
    }
    final method = switch (kind) {
      'Track' => 'createTrack',
      'Artist' => 'createArtist',
      'Release' => 'createRelease',
      _ => 'createVideo',
    };
    final Map<String, dynamic> args = switch (kind) {
      'Track' => {'title': title.text, 'artist': secondary.text, 'audioUrl': details.text, 'genre': genre.text},
      'Artist' => {'name': title.text, 'bio': details.text},
      'Release' => {'title': title.text, 'description': details.text},
      _ => {'title': title.text, 'artist': secondary.text, 'videoUrl': details.text, 'description': ''},
    };
    try {
      final response = await _appChannel.invokeMapMethod<String, dynamic>(method, args);
      if (response?['success'] == true) {
        setState(() => _message = kind + ' draft saved.');
        await _load();
      } else if (mounted) {
        setState(() => _message = response?['message']?.toString() ?? 'Could not save draft.');
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Could not save draft.');
    } finally {
      title.dispose(); secondary.dispose(); details.dispose(); genre.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator(color: _gold));
    final overview = _founder?['overview'] is Map
        ? Map<String, dynamic>.from(_founder!['overview'] as Map)
        : <String, dynamic>{};
    final drafts = ((_founder?['drafts'] as List?) ?? [])
        .whereType<Map>().map((row) => Map<String, dynamic>.from(row)).toList();
    final founderBookings = ((_founder?['bookings'] as List?) ?? [])
        .whereType<Map>().map((row) => Map<String, dynamic>.from(row)).toList();
    final campaigns = ((_founder?['campaigns'] as List?) ?? [])
        .whereType<Map>().map((row) => Map<String, dynamic>.from(row)).toList();
    return ListView(
      padding: const EdgeInsets.all(18),
      children: [
        const Text('YOUR ACCOUNT', style: TextStyle(color: _gold, fontSize: 11, letterSpacing: 1.8, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        _PanelCard(
          title: 'Profile details',
          child: Column(
            children: [
              TextField(controller: _displayName, decoration: const InputDecoration(labelText: 'Display name')),
              TextField(controller: _bio, decoration: const InputDecoration(labelText: 'Bio'), maxLines: 3),
              TextField(controller: _phone, decoration: const InputDecoration(labelText: 'Phone number'), keyboardType: TextInputType.phone),
              const SizedBox(height: 10),
              Align(
                alignment: Alignment.centerLeft,
                child: OutlinedButton.icon(
                  onPressed: _pickAndUploadAvatar,
                  icon: const Icon(Icons.add_a_photo_outlined),
                  label: const Text('UPLOAD PROFILE PHOTO'),
                ),
              ),
              const SizedBox(height: 14),
              FilledButton(onPressed: _saving ? null : _saveProfile,
                style: FilledButton.styleFrom(backgroundColor: _gold, foregroundColor: _black),
                child: const Text('SAVE PROFILE')),
            ],
          ),
        ),
        const SizedBox(height: 14),
        _PanelCard(
          title: 'Request a booking',
          child: Column(
            children: [
              TextField(controller: _bookingName, decoration: const InputDecoration(labelText: 'Your name')),
              TextField(controller: _bookingEmail, decoration: const InputDecoration(labelText: 'Email'), keyboardType: TextInputType.emailAddress),
              TextField(controller: _bookingType, decoration: const InputDecoration(labelText: 'Booking type')),
              TextField(controller: _bookingDate, decoration: const InputDecoration(labelText: 'Requested date (YYYY-MM-DD)')),
              TextField(controller: _bookingMessage, decoration: const InputDecoration(labelText: 'Tell us about your project'), maxLines: 3),
              const SizedBox(height: 14),
              FilledButton(onPressed: _saving ? null : _submitBooking,
                style: FilledButton.styleFrom(backgroundColor: _gold, foregroundColor: _black),
                child: const Text('SUBMIT BOOKING')),
            ],
          ),
        ),
        const SizedBox(height: 14),
        _PanelCard(
          title: 'My bookings',
          child: _bookings.isEmpty
              ? const Text('Your booking requests will appear here.', style: TextStyle(color: Colors.white60))
              : Column(
                  children: _bookings.map((booking) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(booking['type']?.toString() ?? 'Booking'),
                    subtitle: Text((booking['requestedDate'] ?? 'Date not specified').toString() + ' • ' + (booking['status'] ?? 'pending').toString()),
                  )).toList(),
                ),
        ),
        if (_message != null) ...[
          const SizedBox(height: 12),
          Text(_message!, style: const TextStyle(color: _gold)),
        ],
        if (_founder != null) ...[
          const SizedBox(height: 28),
          const Text('FOUNDER STUDIO', style: TextStyle(color: _gold, fontSize: 11, letterSpacing: 1.8, fontWeight: FontWeight.w800)),
          const SizedBox(height: 12),
          _PanelCard(
            title: 'Studio overview',
            child: Wrap(
              spacing: 10,
              runSpacing: 10,
              children: overview.entries.map((entry) => Chip(
                label: Text(entry.key.toUpperCase() + ': ' + entry.value.toString()),
                backgroundColor: const Color(0xFF302515),
                labelStyle: const TextStyle(color: _gold),
              )).toList(),
            ),
          ),
          const SizedBox(height: 12),
          _PanelCard(
            title: 'Create content drafts',
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: ['Track', 'Artist', 'Release', 'Video'].map((kind) => OutlinedButton(
                onPressed: () => _createContent(kind),
                child: Text('NEW ' + kind.toUpperCase()),
              )).toList(),
            ),
          ),
          const SizedBox(height: 12),
          _PanelCard(
            title: 'Unpublished content',
            child: drafts.isEmpty
                ? const Text('No drafts awaiting publication.', style: TextStyle(color: Colors.white60))
                : Column(
                    children: drafts.map((draft) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(draft['title']?.toString() ?? 'Untitled'),
                      subtitle: Text(draft['table']?.toString() ?? ''),
                      trailing: TextButton(onPressed: () => _setDraftPublished(draft), child: const Text('PUBLISH')),
                    )).toList(),
                  ),
          ),
          const SizedBox(height: 12),
          _PanelCard(
            title: 'Sponsored audio campaigns',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: _createCampaign,
                    icon: const Icon(Icons.campaign_outlined),
                    label: const Text('NEW CAMPAIGN'),
                  ),
                ),
                const SizedBox(height: 8),
                if (campaigns.isEmpty)
                  const Text('No campaigns found.', style: TextStyle(color: Colors.white60))
                else
                  ...campaigns.map((campaign) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(campaign['title']?.toString() ?? 'Untitled campaign'),
                    subtitle: Text('Priority: ${campaign['priority'] ?? 0} • ${campaign['isPublished'] == true ? 'Published' : 'Draft'}'),
                    trailing: PopupMenuButton<bool>(
                      initialValue: campaign['isPublished'] == true,
                      onSelected: (publish) => _setCampaignPublished(campaign, publish),
                      itemBuilder: (_) => const [
                        PopupMenuItem(value: true, child: Text('Publish')),
                        PopupMenuItem(value: false, child: Text('Unpublish')),
                      ],
                      child: Icon(campaign['isPublished'] == true ? Icons.public : Icons.public_off, color: _gold),
                    ),
                  )),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _PanelCard(
            title: 'Booking management',
            child: founderBookings.isEmpty
                ? const Text('No booking requests.', style: TextStyle(color: Colors.white60))
                : Column(
                    children: founderBookings.map((booking) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(booking['name']?.toString() ?? 'Client'),
                      subtitle: Text((booking['type'] ?? 'Booking').toString() + ' • ' + (booking['status'] ?? 'pending').toString()),
                      trailing: PopupMenuButton<String>(
                        onSelected: (status) => _updateBooking(booking, status),
                        itemBuilder: (_) => const [
                          PopupMenuItem(value: 'confirmed', child: Text('Confirm')),
                          PopupMenuItem(value: 'declined', child: Text('Decline')),
                          PopupMenuItem(value: 'completed', child: Text('Complete')),
                        ],
                      ),
                    )).toList(),
                  ),
          ),
        ],
        const SizedBox(height: 20),
        OutlinedButton.icon(onPressed: widget.onSignOut, icon: const Icon(Icons.logout), label: const Text('SIGN OUT')),
        const SizedBox(height: 20),
      ],
    );
  }
}

class _PanelCard extends StatelessWidget {
  const _PanelCard({required this.title, required this.child});
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(17),
      decoration: BoxDecoration(
        color: _surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
          const SizedBox(height: 12),
          child,
        ],
      ),
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
