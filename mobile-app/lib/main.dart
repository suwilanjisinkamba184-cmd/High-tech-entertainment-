import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'admin_studio.dart';
import 'diagnostics_service.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:share_plus/share_plus.dart';
import 'package:shorebird_code_push/shorebird_code_push.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:audio_session/audio_session.dart';
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
const googleWebClientId = String.fromEnvironment('HTE_GOOGLE_WEB_CLIENT_ID');
bool _googleSignInInitialized = false;
final db = Supabase.instance.client;
bool _supabaseInitialized = false;
bool _audioBackgroundInitialized = false;
// Both references resolve to the single process-wide playback service.
// Top-level finals are lazy, so the service is first created after the Android
// audio background handler has been initialized in StartupGate.
final HtePlayback playback = HtePlayback.instance;
final AudioPlayer player = HtePlayback.instance.player;
// Separate from the music/ad player and retained across StartupGate -> AuthGate.
final AudioPlayer _startupSoundPlayer = AudioPlayer();

/// Plays the bundled launch ident once and waits for actual playback completion.
/// This player is separate from the music/ad player. A sound failure is logged
/// and returned as false; it must never trap the user on the splash screen.
Future<bool> playStartupJingle() async {
  // Configure the dedicated startup player before requesting audio focus.
  // This is deliberately independent of Supabase and the music/ad player.
  for (var attempt = 1; attempt <= 2; attempt++) {
    try {
      final session = await AudioSession.instance;
      await session.configure(AudioSessionConfiguration.music());
      await session.setActive(true);

      await _startupSoundPlayer.stop();
      await _startupSoundPlayer.setVolume(1.0);
      await _startupSoundPlayer.setLoopMode(LoopMode.off);
      final duration = await _startupSoundPlayer.setAudioSource(
        AudioSource.asset('assets/high-tech-startup.mp3'),
      );
      final maxWait = duration != null && duration > Duration.zero
          ? duration + const Duration(seconds: 8)
          : const Duration(seconds: 30);

      // Subscribe before play() so a short ident cannot complete before the
      // completion listener is attached. Do not await play() itself.
      final completed = _startupSoundPlayer.processingStateStream.firstWhere(
        (state) => state == ProcessingState.completed,
      );
      unawaited(_startupSoundPlayer.play());
      await completed.timeout(maxWait);

      debugPrint('HTE startup sound completed successfully on attempt $attempt.');
      await session.setActive(false);
      return true;
    } catch (error, stackTrace) {
      debugPrint('HTE startup sound attempt $attempt failed: $error');
      debugPrintStack(stackTrace: stackTrace);
      try {
        await _startupSoundPlayer.stop();
        await AudioSession.instance.then((session) => session.setActive(false));
      } catch (resetError) {
        debugPrint('HTE startup audio reset failed: $resetError');
      }
      if (attempt < 2) {
        await Future<void>.delayed(const Duration(milliseconds: 500));
      }
    }
  }

  debugPrint('HTE startup sound failed after two attempts; opening the app.');
  return false;
}

/// Centralised Shorebird OTA checker. All checks target the public Stable track.
class HteOtaUpdateService {
  static final ShorebirdUpdater _updater = ShorebirdUpdater();
  static Future<String>? _activeCheck;

  static Future<String> checkAndInstall() {
    final active = _activeCheck;
    if (active != null) return active;
    final operation = _checkAndInstall();
    _activeCheck = operation;
    return operation.whenComplete(() {
      if (identical(_activeCheck, operation)) _activeCheck = null;
    });
  }

  static Future<String> _checkAndInstall() async {
    if (!Platform.isAndroid) {
      return 'OTA patch updates are currently enabled for Android only.';
    }
    if (!_updater.isAvailable) {
      return 'Shorebird OTA is unavailable in this installation. This app must be installed from a Shorebird release build.';
    }
    try {
      final status = await _updater.checkForUpdate(track: UpdateTrack.stable);
      switch (status) {
        case UpdateStatus.outdated:
          await _updater.update(track: UpdateTrack.stable);
          final nextPatch = await _updater.readNextPatch();
          final patchLabel = nextPatch == null ? 'latest patch' : 'Patch #${nextPatch.number}';
          return '$patchLabel downloaded successfully. Fully close and reopen the app to activate it.';
        case UpdateStatus.restartRequired:
          final nextPatch = await _updater.readNextPatch();
          final patchLabel = nextPatch == null ? 'The downloaded patch' : 'Patch #${nextPatch.number}';
          return '$patchLabel is ready. Fully close and reopen the app to activate it.';
        case UpdateStatus.upToDate:
          final currentPatch = await _updater.readCurrentPatch();
          return currentPatch == null
              ? 'No new OTA patch is available. This installation is running the base release.'
              : 'No new OTA patch is available. Installed patch: #${currentPatch.number}.';
        case UpdateStatus.unavailable:
          return 'Shorebird OTA is unavailable in this app installation.';
      }
    } catch (error, stackTrace) {
      debugPrint('HTE OTA update check failed: $error');
      debugPrintStack(stackTrace: stackTrace);
      return 'OTA check failed. Check your internet connection and try again.';
    }
  }
}

final ValueNotifier<int> favouritesRevision = ValueNotifier<int>(0);

/// Lightweight client-side validation; Supabase remains the authority.
bool isValidEmailAddress(String value) =>
    RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(value.trim());

bool isValidPasswordPair(String password, String confirmation) =>
    password.length >= 8 && password == confirmation;

/// Convert low-level exceptions into clear, user-safe messages.
String friendlyErrorMessage(Object error) {
  final details = error.toString().toLowerCase();
  final offline = error is SocketException ||
      error is TimeoutException ||
      details.contains('socketexception') ||
      details.contains('failed host lookup') ||
      details.contains('network is unreachable') ||
      details.contains('no address associated') ||
      details.contains('connection timed out') ||
      details.contains('connection reset') ||
      details.contains('clientexception') ||
      details.contains('failed to fetch') ||
      details.contains('internet connection');
  if (offline) {
    return 'No internet connection. Connect to the internet and try again.';
  }
  if (error is AuthException) {
    if (details.contains('invalid login credentials')) {
      return 'The email or password is incorrect. Check your details and try again.';
    }
    if (details.contains('email not confirmed')) {
      return 'Please confirm your email address before signing in.';
    }
    if (details.contains('provider is not enabled') ||
        details.contains('unsupported provider')) {
      return 'This sign-in provider is not enabled yet. Please use another sign-in method.';
    }
    if (details.contains('too many requests') ||
        details.contains('rate limit')) {
      return 'Too many attempts. Please wait a little while and try again.';
    }
    if (details.contains('password')) {
      return 'The password could not be accepted. Check the requirements and try again.';
    }
    return 'We could not complete sign-in. Check your account details and try again.';
  }
  if (error is HttpException) {
    if (details.contains('404')) {
      return 'This media file is no longer available. Please ask the publisher to upload it again.';
    }
    if (details.contains('401') || details.contains('403')) {
      return 'You do not have permission to access this media file.';
    }
    return 'The media server could not complete the request. Please retry.';
  }
  if (error is FormatException) {
    if (details.contains('smaller than 5 mb')) {
      return 'Choose a profile image smaller than 5 MB.';
    }
    if (details.contains('does not match the requested media type')) {
      return 'Choose a file that matches the selected audio or video type.';
    }
    return 'The returned data or media format is not supported. Please try another item.';
  }
  if (error is FileSystemException) {
    return 'Your device could not access this file. Check available storage and permissions.';
  }
  if (details.contains('row level security') ||
      details.contains('row-level security') ||
      details.contains('permission denied') ||
      details.contains('not authorized')) {
    return 'This action is not permitted for the current account.';
  }
  if (details.contains('bucket not found')) {
    return 'Media storage is not configured correctly. Please contact support.';
  }
  if (details.contains('payload too large') ||
      details.contains('file size exceeds') ||
      details.contains('maximum allowed size')) {
    return 'This file is too large. Choose a smaller file and try again.';
  }
  if (details.contains('duplicate key') ||
      details.contains('already exists')) {
    return 'This item already exists. Refresh the list and try a different name.';
  }
  if (details.contains('pgrst116') ||
      details.contains('no rows found') ||
      details.contains('object not found')) {
    return 'This item is no longer available. Refresh and try again.';
  }
  if (details.contains('storageexception') ||
      details.contains('storage api')) {
    return 'Media storage could not complete the request. Check your connection and access, then retry.';
  }
  if (details.contains('relation') && details.contains('does not exist')) {
    return 'This feature is temporarily unavailable. Please try again later.';
  }
  return 'Something went wrong. Please try again.';
}


class AppThemeController {
  static const _preferenceKey = 'hte_theme_mode_v1';
  static final ValueNotifier<ThemeMode> mode = ValueNotifier(ThemeMode.dark);

  static Future<void> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString(_preferenceKey) ?? 'dark';
      mode.value = switch (saved) {
        'light' => ThemeMode.light,
        'system' => ThemeMode.system,
        _ => ThemeMode.dark,
      };
    } catch (error) {
      debugPrint('HTE theme preference could not be loaded: $error');
    }
  }

  static Future<void> setMode(ThemeMode value) async {
    mode.value = value;
    final prefs = await SharedPreferences.getInstance();
    final saved = switch (value) {
      ThemeMode.light => 'light',
      ThemeMode.system => 'system',
      ThemeMode.dark => 'dark',
    };
    await prefs.setString(_preferenceKey, saved);
  }
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  FlutterError.onError = (details) {
    FlutterError.presentError(details);
    unawaited(HteDiagnostics.record(
      details.exception,
      stackTrace: details.stack,
      source: 'flutter-framework',
    ));
  };
  PlatformDispatcher.instance.onError = (error, stackTrace) {
    unawaited(HteDiagnostics.record(
      error,
      stackTrace: stackTrace,
      source: 'platform-dispatcher',
    ));
    return false;
  };
  runApp(const HteApp());
  // Load the saved appearance without delaying the startup animation or sound.
  unawaited(AppThemeController.load());
}

class HteApp extends StatelessWidget {
  const HteApp({super.key});
  @override
  Widget build(BuildContext context) => ValueListenableBuilder<ThemeMode>(
    valueListenable: AppThemeController.mode,
    builder: (context, mode, child) => MaterialApp(
    title: 'High-Tech Entertainment',
    debugShowCheckedModeBanner: false,
    themeMode: mode,
    theme: ThemeData.from(
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFFD9B96E),
        brightness: Brightness.light,
      ),
    ).copyWith(
      scaffoldBackgroundColor: const Color(0xFFF7F6F2),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFFF7F6F2),
        foregroundColor: Color(0xFF171717),
        elevation: 0,
        surfaceTintColor: Colors.transparent,
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        margin: const EdgeInsets.symmetric(horizontal: 2, vertical: 5),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: Color(0xFFE5E0D5)),
        ),
      ),
    ),
    darkTheme: ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xFF07090D),
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFFE3B85F),
        brightness: Brightness.dark,
      ).copyWith(
        primary: const Color(0xFFE3B85F),
        onPrimary: const Color(0xFF17120A),
        secondary: const Color(0xFFB8D3E6),
        onSecondary: const Color(0xFF09121A),
        surface: const Color(0xFF10151D),
        surfaceContainer: const Color(0xFF131A23),
        surfaceContainerHigh: const Color(0xFF19222D),
        outline: const Color(0xFF34404D),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFF07090D),
        foregroundColor: Color(0xFFF5F2EA),
        elevation: 0,
        centerTitle: false,
        surfaceTintColor: Colors.transparent,
      ),
      textTheme: ThemeData.dark().textTheme.apply(
        bodyColor: const Color(0xFFF2F1ED),
        displayColor: const Color(0xFFF8F5EC),
      ),
      cardTheme: CardThemeData(
        color: const Color(0xFF111720),
        elevation: 0,
        margin: const EdgeInsets.symmetric(horizontal: 2, vertical: 6),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
          side: const BorderSide(color: Color(0xFF263442)),
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
    ),
  );
}


class StartupGate extends StatefulWidget {
  const StartupGate({super.key});

  @override
  State<StartupGate> createState() => _StartupGateState();
}

class _StartupGateState extends State<StartupGate> with SingleTickerProviderStateMixin {
  bool _ready = false;
  bool _starting = true;
  bool _startupSoundHandled = false;
  String? _error;
  Future<void>? _initialization;
  Future<bool>? _startupSoundTask;
  final DateTime _launchStartedAt = DateTime.now();
  double _startupProgress = 0.06;
  late final AnimationController _startupAnimation;
  late final AnimationController _ringAnimation;

  @override
  void initState() {
    super.initState();
    _startupAnimation = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat(reverse: true);
    _ringAnimation = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 7),
    )..repeat();

    // Start the launch ident immediately on the first frame. It must not wait
    // for Supabase, background playback setup, or authentication.
    _startupSoundTask = _playStartupSound();
    _initialize();
  }

  Future<bool> _playStartupSound() async {
    if (_startupSoundHandled) return true;
    final played = await playStartupJingle();
    _startupSoundHandled = played;
    return played;
  }

  void _setStartupProgress(double value) {
    if (!mounted) return;
    setState(() => _startupProgress = value.clamp(0.0, 1.0).toDouble());
  }

  Future<void> _enforceMinimumSplashDuration() async {
    const minimumSplash = Duration(seconds: 15);
    final elapsed = DateTime.now().difference(_launchStartedAt);
    if (elapsed < minimumSplash) {
      await Future<void>.delayed(minimumSplash - elapsed);
    }
  }

  /// Coalesce overlapping startup initialization requests.
  Future<void> _initialize() {
    final running = _initialization;
    if (running != null) return running;

    final attempt = _initializeServices();
    _initialization = attempt;
    return attempt.whenComplete(() {
      if (identical(_initialization, attempt)) _initialization = null;
    });
  }

  Future<void> _initializeServices() async {
    if (mounted) {
      setState(() {
        _starting = true;
        _error = null;
        _startupProgress = 0.08;
      });
    }
    try {
      // Normal background music setup is isolated from the startup ident.
      if (defaultTargetPlatform == TargetPlatform.android &&
          !_audioBackgroundInitialized) {
        try {
          await JustAudioBackground.init(
            androidNotificationChannelId: 'com.hightechentertainment.app.audio',
            androidNotificationChannelName: 'High-Tech Music Playback',
            androidNotificationOngoing: true,
          );
          _audioBackgroundInitialized = true;
        } catch (audioError, stackTrace) {
          debugPrint('HTE background audio initialisation failed: $audioError');
          debugPrintStack(stackTrace: stackTrace);
          unawaited(HteDiagnostics.record(
            audioError,
            stackTrace: stackTrace,
            source: 'background-audio-init',
          ));
        }
      }
      _setStartupProgress(0.28);

      try {
        final audioSession = await AudioSession.instance;
        await audioSession.configure(AudioSessionConfiguration.music());
      } catch (sessionError, stackTrace) {
        debugPrint('HTE media audio session configuration failed: $sessionError');
        debugPrintStack(stackTrace: stackTrace);
      }
      _setStartupProgress(0.42);

      // Register the native audio handler first to avoid competing audio-session
      // initialization. Start the launch ident before any Supabase work.
      final soundTask = _startupSoundTask ??= _playStartupSound();

      // Core app initialization runs while the separate ident plays.
      await _initializeCoreServices();
      _setStartupProgress(0.76);

      final soundPlayed = await soundTask;
      if (!soundPlayed) {
        debugPrint('HTE startup audio failed; continuing without blocking sign-in.');
      }
      _setStartupProgress(0.90);

      // Preserve the agreed minimum 15-second branded startup experience.
      await _enforceMinimumSplashDuration();

      if (mounted) {
        setState(() {
          _startupProgress = 1.0;
          _ready = true;
          _starting = false;
        });
        unawaited(HteOtaUpdateService.checkAndInstall().then(
          (message) => debugPrint('HTE startup OTA check: $message'),
        ));
      }
    } catch (error, stackTrace) {
      unawaited(HteDiagnostics.record(
        error,
        stackTrace: stackTrace,
        source: 'startup-initialization',
      ));
      // Even a recoverable initialization error must not shorten the
      // minimum branded startup interval.
      await _enforceMinimumSplashDuration();
      if (mounted) {
        setState(() {
          _error = friendlyErrorMessage(error);
          _starting = false;
        });
      }
    }
  }

  Future<void> _initializeCoreServices() async {
    // Restore the sponsored-audio counter; adverts never play during startup.
    await playback.restoreAdCounter();
    if (!_supabaseInitialized) {
      await Supabase.initialize(
        url: supabaseUrl,
        publishableKey: supabaseKey,
      );
      _supabaseInitialized = true;
    }
  }

  @override
  void dispose() {
    _startupAnimation.dispose();
    _ringAnimation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_ready) return const AuthGate();
    return Scaffold(
      backgroundColor: const Color(0xFF080808),
      bottomNavigationBar: SizedBox(
        height: 3,
        child: LinearProgressIndicator(
          value: _startupProgress,
          minHeight: 3,
          backgroundColor: const Color(0xFF29231A),
          valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFFD9B96E)),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                AnimatedBuilder(
                  animation: Listenable.merge([_startupAnimation, _ringAnimation]),
                  builder: (context, child) {
                    final pulse = _startupAnimation.value;
                    return Transform.scale(
                      scale: 0.96 + (pulse * 0.045),
                      child: Container(
                        width: 224,
                        height: 224,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: const Color(0xFFD9A52B).withValues(alpha: 0.12 + (pulse * 0.16)),
                              blurRadius: 28 + (pulse * 24),
                              spreadRadius: 2 + (pulse * 5),
                            ),
                          ],
                        ),
                        child: Stack(
                          alignment: Alignment.center,
                          children: [
                            Transform.rotate(
                              angle: _ringAnimation.value * 6.283185307179586,
                              child: Container(
                                width: 218,
                                height: 218,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  border: Border.all(color: const Color(0xFFD9B96E).withValues(alpha: 0.72), width: 1.6),
                                  gradient: const SweepGradient(colors: [Color(0x00D9B96E), Color(0xFFD9B96E), Color(0x00D9B96E)]),
                                  boxShadow: [BoxShadow(color: const Color(0xFFD9A52B).withValues(alpha: 0.18 + (pulse * 0.12)), blurRadius: 16, spreadRadius: 1)],
                                ),
                              ),
                            ),
                            Transform.rotate(
                              angle: -_ringAnimation.value * 6.283185307179586,
                              child: SvgPicture.asset(
                                'assets/high-tech-logo.svg',
                                width: 184,
                                height: 184,
                                semanticsLabel: 'High-Tech Entertainment app logo',
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 26),
                AnimatedBuilder(
                  animation: _startupAnimation,
                  builder: (context, child) {
                    final pulse = _startupAnimation.value;
                    return Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: List.generate(5, (index) {
                        final phase = (pulse + (index * 0.17)) % 1.0;
                        return Container(
                          width: 4,
                          height: 9 + (phase * 19),
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          decoration: BoxDecoration(
                            color: Color.lerp(const Color(0xFF8A6420), const Color(0xFFFFD978), phase),
                            borderRadius: BorderRadius.circular(5),
                            boxShadow: [
                              BoxShadow(
                                color: const Color(0xFFD9A52B).withValues(alpha: 0.25),
                                blurRadius: 7,
                              ),
                            ],
                          ),
                        );
                      }),
                    );
                  },
                ),
                const SizedBox(height: 24),
                const Text(
                  'HIGH-TECH',
                  style: TextStyle(fontSize: 25, fontWeight: FontWeight.w900, letterSpacing: 4),
                ),
                const SizedBox(height: 5),
                const Text(
                  'ENTERTAINMENT',
                  style: TextStyle(color: Color(0xFFD4AF37), fontSize: 12, letterSpacing: 5),
                ),
                const SizedBox(height: 26),
                if (_starting) ...[
                  const Text(
                    'Preparing your experience…',
                    style: TextStyle(color: Colors.white60),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    '${(_startupProgress * 100).round()}%',
                    style: const TextStyle(
                      color: Color(0xFFD9B96E),
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
                if (_error != null) ...[
                  Text(
                    _error!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Close and reopen the app after checking your connection.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white54, fontSize: 12),
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
        final authState = snapshot.data;
        final session = snapshot.hasData
            ? authState!.session
            : db.auth.currentSession;
        if (session != null &&
            authState?.event == AuthChangeEvent.passwordRecovery) {
          return const PasswordRecoveryPage();
        }
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
      body: Stack(children: [
        const Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFF17130D), Color(0xFF090E14), Color(0xFF06080C)],
              ),
            ),
          ),
        ),
        Positioned(
          top: -170, right: -125,
          child: IgnorePointer(child: Container(
            width: 390, height: 390,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: const Color(0x2DE1BD72), width: 1.2),
            ),
          )),
        ),
        Positioned(
          top: -125, right: -82,
          child: IgnorePointer(child: Container(
            width: 290, height: 290,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: const Color(0x223F7894), width: 1.1),
            ),
          )),
        ),
        SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: ListView(
                padding: const EdgeInsets.fromLTRB(24, 26, 24, 30),
                children: [
                  const SizedBox(height: 10),
                  Center(child: Container(
                    width: 142, height: 142,
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: const SweepGradient(colors: [
                        Color(0xFF78572A), Color(0xFFE8C979),
                        Color(0xFF5B8095), Color(0xFF78572A),
                      ]),
                      boxShadow: const [
                        BoxShadow(color: Color(0x447E622F), blurRadius: 35, spreadRadius: 3),
                      ],
                    ),
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: const BoxDecoration(shape: BoxShape.circle, color: Color(0xFF090D12)),
                      child: SvgPicture.asset(
                        'assets/high-tech-logo.svg',
                        semanticsLabel: 'High-Tech Entertainment',
                      ),
                    ),
                  )),
                  const SizedBox(height: 22),
                  const Text('HIGH-TECH ENTERTAINMENT',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900,
                      letterSpacing: 2.2, color: Color(0xFFE4C477))),
                  const SizedBox(height: 8),
                  const Text('MUSIC. BEATS. PRODUCTION. BEYOND.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Color(0xFF9EADB8), fontSize: 9,
                      fontWeight: FontWeight.w800, letterSpacing: 1.4)),
                  const SizedBox(height: 28),
                  Container(
                    padding: const EdgeInsets.fromLTRB(18, 21, 18, 19),
                    decoration: BoxDecoration(
                      color: const Color(0xD9101720),
                      borderRadius: BorderRadius.circular(25),
                      border: Border.all(color: const Color(0xFF344453)),
                      boxShadow: const [
                        BoxShadow(color: Color(0x66000000), blurRadius: 30, offset: Offset(0, 14)),
                      ],
                    ),
                    child: const Column(children: [
                      Text('YOUR SOUND. YOUR SPACE.',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900,
                          letterSpacing: -.5, color: Color(0xFFF6F2E9))),
                      SizedBox(height: 7),
                      Text('Sign in to enter the High-Tech experience.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Color(0xFFAAB6C0), fontSize: 11)),
                      SizedBox(height: 20),
                      AccountTab(embedded: true),
                    ]),
                  ),
                  const SizedBox(height: 20),
                  const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                    Icon(Icons.shield_outlined, size: 13, color: Color(0xFF8796A2)),
                    SizedBox(width: 7),
                    Text('SECURE ACCOUNT ACCESS',
                      style: TextStyle(color: Color(0xFF8796A2), fontSize: 8,
                        fontWeight: FontWeight.w800, letterSpacing: 1.3)),
                  ]),
                ],
              ),
            ),
          ),
        ),
      ]),
    );
  }
}

class PasswordRecoveryPage extends StatefulWidget {
  const PasswordRecoveryPage({super.key});

  @override
  State<PasswordRecoveryPage> createState() => _PasswordRecoveryPageState();
}

class _PasswordRecoveryPageState extends State<PasswordRecoveryPage> {
  final _password = TextEditingController();
  final _confirmPassword = TextEditingController();
  bool _busy = false;
  bool _showPassword = false;

  @override
  void dispose() {
    _password.dispose();
    _confirmPassword.dispose();
    super.dispose();
  }

  Future<void> _savePassword() async {
    final password = _password.text;
    if (password.length < 8) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Choose a password with at least 8 characters.')),
      );
      return;
    }
    if (!isValidPasswordPair(password, _confirmPassword.text)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('The passwords do not match. Please check and try again.')),
      );
      return;
    }
    setState(() => _busy = true);
    try {
      await db.auth.updateUser(UserAttributes(password: password));
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Your password has been updated successfully.')),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Stack(children: [
      const Positioned.fill(child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(0xFF17130D), Color(0xFF090E14), Color(0xFF06080C)],
          ),
        ),
      )),
      SafeArea(child: Center(child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              color: const Color(0xFF111923),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFF465565)),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const Icon(Icons.lock_reset_rounded, color: Color(0xFFE4C477), size: 48),
              const SizedBox(height: 14),
              const Text('RESET YOUR PASSWORD',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900,
                  letterSpacing: 1.1)),
              const SizedBox(height: 8),
              const Text('Choose a new password for your High-Tech account.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Color(0xFFAAB6C0), fontSize: 12)),
              const SizedBox(height: 22),
              TextField(
                controller: _password,
                obscureText: !_showPassword,
                autofillHints: const [AutofillHints.newPassword],
                decoration: const InputDecoration(labelText: 'New password', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _confirmPassword,
                obscureText: !_showPassword,
                autofillHints: const [AutofillHints.newPassword],
                decoration: const InputDecoration(labelText: 'Confirm new password', border: OutlineInputBorder()),
              ),
              Align(alignment: Alignment.centerRight, child: TextButton(
                onPressed: () => setState(() => _showPassword = !_showPassword),
                child: Text(_showPassword ? 'Hide passwords' : 'Show passwords'),
              )),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: _busy ? null : _savePassword,
                child: Text(_busy ? 'Updating…' : 'Update password'),
              ),
            ]),
          ),
        ),
      ))),
    ]),
  );
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});
  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int tab = 0;
  final pages = const [HomeTab(), DiscoverTab(), ReleasesTab(), LibraryTab(), AccountTab(), MoreTab()];
  static const _items = [
    (Icons.space_dashboard_rounded, 'Home'),
    (Icons.explore_rounded, 'Discover'),
    (Icons.album_rounded, 'Releases'),
    (Icons.library_music_rounded, 'Library'),
    (Icons.person_rounded, 'Profile'),
    (Icons.grid_view_rounded, 'More'),
  ];

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Stack(children: [
      const Positioned.fill(
        child: IgnorePointer(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: Alignment(-0.85, -0.95),
                radius: 1.25,
                colors: [Color(0x1A9C762E), Color(0x0007090D)],
              ),
            ),
          ),
        ),
      ),
      SafeArea(child: IndexedStack(index: tab, children: pages)),
    ]),
    bottomNavigationBar: Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xF2141B24), Color(0xFF080B10)],
        ),
        border: Border(top: BorderSide(color: Color(0xFF34404D), width: .7)),
        boxShadow: [BoxShadow(color: Color(0x66000000), blurRadius: 24, offset: Offset(0, -8))],
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const PlayerBar(),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 8, 8, 7),
            child: Row(
              children: List.generate(_items.length, (index) {
                final selected = tab == index;
                final item = _items[index];
                return Expanded(
                  child: Semantics(
                    button: true,
                    selected: selected,
                    label: item.$2,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(15),
                      onTap: () => setState(() => tab = index),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 240),
                        curve: Curves.easeOutCubic,
                        margin: const EdgeInsets.symmetric(horizontal: 3),
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        decoration: BoxDecoration(
                          gradient: selected ? const LinearGradient(
                            begin: Alignment.topLeft, end: Alignment.bottomRight,
                            colors: [Color(0xFF4A3920), Color(0xFF211D17)],
                          ) : null,
                          color: selected ? null : Colors.transparent,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(
                            color: selected ? const Color(0xFFB68B43) : Colors.transparent,
                          ),
                        ),
                        child: Column(mainAxisSize: MainAxisSize.min, children: [
                          Icon(item.$1, size: 20,
                            color: selected ? const Color(0xFFF0CC7B) : const Color(0xFF82909E)),
                          const SizedBox(height: 4),
                          Text(item.$2, maxLines: 1,
                            style: TextStyle(fontSize: 9, letterSpacing: .2,
                              fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                              color: selected ? const Color(0xFFF0CC7B) : const Color(0xFF9BA7B2))),
                        ]),
                      ),
                    ),
                  ),
                );
              }),
            ),
          ),
        ),
      ]),
    ),
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
        leading: const Icon(Icons.tune_rounded, color: Color(0xFFD4AF37)),
        title: const Text('App settings'),
        subtitle: const Text('Appearance, playback, downloads, updates and privacy'),
        trailing: const Icon(Icons.chevron_right),
        // SettingsPage owns its Scaffold and AppBar. Do not wrap it in a
        // second Scaffold, which produced duplicate Settings/back bars.
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => const SettingsPage()),
        ),
      )),
      Card(child: ListTile(
        leading: const Icon(Icons.download_for_offline_outlined, color: Color(0xFFD4AF37)),
        title: const Text('Offline media library'),
        subtitle: const Text('Manage music and videos saved on this device'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => _open(context, 'Offline media', const LocalFilesTab()),
      )),
      Card(child: ListTile(
        leading: const Icon(Icons.notifications_none, color: Color(0xFFD4AF37)),
        title: const Text('Notifications'),
        subtitle: const Text('Release news and account announcements'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NotificationsPage())),
      )),
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
        subtitle: const Text('Profile, sign out and account details'),
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
    padding: const EdgeInsets.fromLTRB(18, 15, 18, 15),
    child: Row(children: [
      Container(
        width: 58, height: 58,
        padding: const EdgeInsets.all(5),
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: const SweepGradient(colors: [Color(0xFF7E5A26), Color(0xFFE8C878), Color(0xFF607D91), Color(0xFF7E5A26)]),
          boxShadow: const [BoxShadow(color: Color(0x337E5A26), blurRadius: 18, spreadRadius: 1)],
        ),
        child: Container(
          padding: const EdgeInsets.all(4),
          decoration: const BoxDecoration(shape: BoxShape.circle, color: Color(0xFF090D12)),
          child: SvgPicture.asset('assets/high-tech-logo.svg', semanticsLabel: 'High-Tech Entertainment'),
        ),
      ),
      const SizedBox(width: 13),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('HIGH-TECH', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900, letterSpacing: 2.1, height: 1.05)),
        const SizedBox(height: 4),
        const Text('ENTERTAINMENT', style: TextStyle(fontSize: 9, color: Color(0xFFE2BC69), letterSpacing: 3.0, fontWeight: FontWeight.w800)),
        const SizedBox(height: 5),
        Text(tagline, maxLines: 1, overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 8, color: Color(0xFF8795A3), letterSpacing: 1.0, fontWeight: FontWeight.w600)),
      ])),
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 7),
        decoration: BoxDecoration(
          color: const Color(0xFF13202A),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: const Color(0xFF344B5A)),
        ),
        child: const Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(Icons.graphic_eq_rounded, color: Color(0xFFE2BC69), size: 13),
          SizedBox(width: 5),
          Text('HT MUSIC', style: TextStyle(color: Color(0xFFC7D5DF), fontSize: 8, fontWeight: FontWeight.w800, letterSpacing: .8)),
        ]),
      ),
    ]),
  );
}

class Heading extends StatelessWidget {
  const Heading(this.text, {super.key});
  final String text;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(19, 20, 19, 10),
    child: Row(children: [
      Container(width: 3, height: 17,
        decoration: BoxDecoration(color: const Color(0xFFE3BD70), borderRadius: BorderRadius.circular(4))),
      const SizedBox(width: 9),
      Expanded(child: Text(text.toUpperCase(),
        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w900,
          letterSpacing: 1.15, color: Color(0xFFE7E3D9)))),
    ]),
  );
}

class AppUpdateBanner extends StatefulWidget {
  const AppUpdateBanner({super.key});

  @override
  State<AppUpdateBanner> createState() => _AppUpdateBannerState();
}

class _AppUpdateBannerState extends State<AppUpdateBanner> {
  String? _availableVersion;

  int _compareVersions(String left, String right) {
    final a = left.split('.').map((part) => int.tryParse(part) ?? 0).toList();
    final b = right.split('.').map((part) => int.tryParse(part) ?? 0).toList();
    for (var index = 0; index < (a.length > b.length ? a.length : b.length); index++) {
      final first = index < a.length ? a[index] : 0;
      final second = index < b.length ? b[index] : 0;
      if (first != second) return first.compareTo(second);
    }
    return 0;
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _checkForUpdate());
  }

  Future<void> _checkForUpdate() async {
    if (defaultTargetPlatform != TargetPlatform.android) return;
    try {
      final info = await PackageInfo.fromPlatform();
      final response = await http.get(
        Uri.https('api.github.com', '/repos/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases/latest'),
        headers: {
          'Accept': 'application/vnd.github+json',
          'User-Agent': 'High-Tech-Entertainment-App',
          'Cache-Control': 'no-cache',
          'X-GitHub-Api-Version': '2022-11-28',
        },
      ).timeout(const Duration(seconds: 12));
      if (!mounted || response.statusCode != 200) return;
      final release = jsonDecode(response.body) as Map<String, dynamic>;
      if (release['draft'] == true || release['prerelease'] == true) return;

      final tag = (release['tag_name'] ?? '').toString();
      final isSuRelease = tag.toUpperCase().endsWith('-SU');
      final latest = tag
          .replaceFirst(RegExp(r'^[vV]'), '')
          .replaceFirst(RegExp(r'-SU$', caseSensitive: false), '');
      final installedSu = info.version.toUpperCase().contains(' SU');
      final installed = info.version
          .replaceFirst(RegExp(r'\s+SU$', caseSensitive: false), '')
          .trim();
      final latestBuild = int.tryParse(
        RegExp(r'Android build:\s*(\d+)', caseSensitive: false)
            .firstMatch((release['body'] ?? '').toString())
            ?.group(1) ?? '',
      ) ?? 0;
      final installedBuild = int.tryParse(info.buildNumber) ?? 0;
      final comparison = _compareVersions(latest, installed);
      final updateAvailable = latest.isNotEmpty &&
          (comparison > 0 ||
              (comparison == 0 && latestBuild > installedBuild) ||
              (isSuRelease && !installedSu));
      if (!updateAvailable) return;

      final assets = release['assets'] as List<dynamic>? ?? [];
      final hasApk = assets.any((raw) {
        if (raw is! Map<String, dynamic>) return false;
        final name = (raw['name'] ?? '').toString().toLowerCase();
        final url = (raw['browser_download_url'] ?? '').toString();
        final size = (raw['size'] as num?)?.toInt() ?? 0;
        return name.endsWith('.apk') && url.startsWith('https://') && size > 0;
      });
      if (!hasApk || !mounted) return;
      setState(() => _availableVersion = latest);
    } catch (error) {
      // Automatic update discovery is best-effort. Manual checking remains
      // available in Settings, and an offline start must never block the app.
      debugPrint('HTE automatic update check skipped: $error');
    }
  }

  @override
  Widget build(BuildContext context) {
    final version = _availableVersion;
    if (version == null) return const SizedBox.shrink();
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 15),
      padding: const EdgeInsets.all(15),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF3A2D1B), Color(0xFF192631)],
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE0B96E)),
      ),
      child: Row(children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: const Color(0xFF111820),
            borderRadius: BorderRadius.circular(13),
            border: Border.all(color: const Color(0xFF6B5838)),
          ),
          child: const Icon(Icons.system_update_alt_rounded, color: Color(0xFFE3BD70)),
        ),
        const SizedBox(width: 12),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('UPDATE AVAILABLE · v$version',
              style: const TextStyle(color: Color(0xFFF0D18B), fontSize: 9,
                fontWeight: FontWeight.w900, letterSpacing: .8)),
            const SizedBox(height: 4),
            const Text('A new High-Tech experience is ready.',
              style: TextStyle(color: Color(0xFFD3DDE4), fontSize: 10)),
          ],
        )),
        const SizedBox(width: 8),
        IconButton(
          tooltip: 'Review app update',
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const SettingsPage(autoCheckForUpdates: true)),
          ),
          icon: const Icon(Icons.arrow_forward_rounded, color: Color(0xFFE3BD70)),
        ),
      ]),
    );
  }
}

class HomeTab extends StatelessWidget {
  const HomeTab({super.key});

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.only(bottom: 28),
    children: [
      const Brand(),
      const AppUpdateBanner(),
      Container(
        margin: const EdgeInsets.fromLTRB(16, 2, 16, 18),
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: const Color(0xFF8B6B35).withValues(alpha: .8)),
          gradient: const LinearGradient(
            begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(0xFF292319), Color(0xFF111923), Color(0xFF090D13)],
            stops: [0, .56, 1],
          ),
          boxShadow: const [
            BoxShadow(color: Color(0x553A2A10), blurRadius: 30, offset: Offset(0, 14)),
            BoxShadow(color: Color(0x222D536A), blurRadius: 28, offset: Offset(10, -8)),
          ],
        ),
        child: Stack(children: [
          Positioned(right: -92, top: -76,
            child: Container(width: 290, height: 290,
              decoration: BoxDecoration(shape: BoxShape.circle,
                border: Border.all(color: const Color(0x44D7B66B), width: 1.1)))),
          Positioned(right: -60, top: -43,
            child: Container(width: 225, height: 225,
              decoration: BoxDecoration(shape: BoxShape.circle,
                border: Border.all(color: const Color(0x223D7894), width: 1.1)))),
          Positioned(right: 8, top: 54,
            child: Opacity(opacity: .12,
              child: SvgPicture.asset('assets/high-tech-logo.svg', width: 150, height: 150))),
          Positioned(right: 24, bottom: 34,
            child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              for (final h in [12.0, 22.0, 16.0, 34.0, 23.0, 43.0, 29.0, 18.0, 36.0, 24.0, 15.0])
                Container(width: 3, height: h, margin: const EdgeInsets.symmetric(horizontal: 2),
                  decoration: BoxDecoration(color: const Color(0xFFDDBB70).withValues(alpha: .58),
                    borderRadius: BorderRadius.circular(4))),
            ])),
          Padding(
            padding: const EdgeInsets.fromLTRB(22, 22, 22, 23),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Container(width: 7, height: 7,
                  decoration: const BoxDecoration(color: Color(0xFF8ED2B2), shape: BoxShape.circle,
                    boxShadow: [BoxShadow(color: Color(0x668ED2B2), blurRadius: 8)])),
                const SizedBox(width: 8),
                const Text('THE OFFICIAL HIGH-TECH EXPERIENCE',
                  style: TextStyle(color: Color(0xFFC6D3DC), fontSize: 8,
                    fontWeight: FontWeight.w800, letterSpacing: 1.35)),
                const Spacer(),
                const Icon(Icons.auto_awesome, color: Color(0xFFE3C47D), size: 17),
              ]),
              const SizedBox(height: 29),
              const Text('SOUND WITHOUT\nLIMITS.',
                style: TextStyle(fontSize: 39, height: 1.0, fontWeight: FontWeight.w900,
                  letterSpacing: -1.7, color: Color(0xFFF8F4E9))),
              const SizedBox(height: 13),
              const SizedBox(width: 270,
                child: Text('Discover the artists, original releases and sound shaping the next wave.',
                  style: TextStyle(color: Color(0xFFB9C4CC), height: 1.55, fontSize: 12))),
              const SizedBox(height: 23),
              Wrap(spacing: 8, runSpacing: 8, children: [
                FilledButton.icon(
                  onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const MusicTab())),
                  icon: const Icon(Icons.play_arrow_rounded, size: 19),
                  label: const Text('Explore music'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size(0, 46),
                    padding: const EdgeInsets.symmetric(horizontal: 17),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => Scaffold(
                    appBar: AppBar(title: const Text('Videos')), body: const VideosTab()))),
                  icon: const Icon(Icons.play_circle_outline_rounded, size: 18),
                  label: const Text('Watch visuals'),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size(0, 46),
                    foregroundColor: const Color(0xFFD5E0E7),
                    side: const BorderSide(color: Color(0xFF526575)),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
              ]),
              const SizedBox(height: 24),
              Row(children: [
                const Icon(Icons.graphic_eq_rounded, color: Color(0xFFE3C47D), size: 15),
                const SizedBox(width: 7),
                const Text('MUSIC  /  BEATS  /  PRODUCTION',
                  style: TextStyle(color: Color(0xFF8799A7), fontSize: 8,
                    fontWeight: FontWeight.w800, letterSpacing: 1.2)),
              ]),
            ]),
          ),
        ]),
      ),
      Padding(
        padding: const EdgeInsets.fromLTRB(19, 4, 19, 10),
        child: Row(children: [
          const Expanded(child: Text('ENTER THE SOUND',
            style: TextStyle(color: Color(0xFFE4C57E), fontSize: 9,
              letterSpacing: 1.8, fontWeight: FontWeight.w900))),
          Container(width: 38, height: 1, color: const Color(0xFF6F5A35)),
        ]),
      ),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Row(children: [
          Expanded(child: _HomeQuickAction(icon: Icons.album_rounded, title: 'New releases',
            subtitle: 'Fresh from HTE', onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: const Text('Releases')), body: const ReleasesTab()))))),
          const SizedBox(width: 10),
          Expanded(child: _HomeQuickAction(icon: Icons.people_alt_rounded, title: 'Artists',
            subtitle: 'Meet the creators', onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: const Text('Artists')), body: const ArtistsTab()))))),
        ]),
      ),
      const ContinueListeningCard(),
      const Heading('Latest releases'),
      const ReleaseList(horizontal: true),
      const Heading('Featured artists'),
      const ArtistList(horizontal: true),
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: const LinearGradient(colors: [Color(0xFF15212A), Color(0xFF10151C)]),
            borderRadius: BorderRadius.circular(19),
            border: Border.all(color: const Color(0xFF334B5B)),
          ),
          child: Row(children: [
            Container(width: 42, height: 42,
              decoration: BoxDecoration(color: const Color(0xFF23313A), borderRadius: BorderRadius.circular(13)),
              child: const Icon(Icons.video_library_rounded, color: Color(0xFFE3C47D), size: 22)),
            const SizedBox(width: 13),
            const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Beyond the audio', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
              SizedBox(height: 4),
              Text('Official videos and visual releases.',
                style: TextStyle(color: Color(0xFF9EADB8), fontSize: 10)),
            ])),
            IconButton(
              tooltip: 'Explore videos',
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => Scaffold(appBar: AppBar(title: const Text('Videos')), body: const VideosTab()))),
              icon: const Icon(Icons.arrow_forward_rounded, color: Color(0xFFE3C47D)),
            ),
          ]),
        ),
      ),
      const Heading('Watch the latest videos'),
      const VideoPreviewList(),
      const Heading('Released music'),
      const TrackList(compact: true),
      const Padding(
        padding: EdgeInsets.fromLTRB(20, 24, 20, 8),
        child: Text('HIGH-TECH ENTERTAINMENT  ·  QUALITY SOUND, GLOBAL VIBES',
          textAlign: TextAlign.center,
          style: TextStyle(color: Color(0xFF72808D), fontSize: 8,
            letterSpacing: 1.2, height: 1.8)),
      ),
    ],
  );
}

class ContinueListeningCard extends StatelessWidget {
  const ContinueListeningCard({super.key});

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<Map<String, dynamic>?>(
    valueListenable: playback.currentTrack,
    builder: (context, track, _) {
      if (track == null) return const SizedBox.shrink();
      return ValueListenableBuilder<bool>(
        valueListenable: playback.adPlaying,
        builder: (context, isAd, _) {
          if (isAd) return const SizedBox.shrink();
          return Padding(
            padding: const EdgeInsets.fromLTRB(18, 0, 18, 8),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Heading('Continue listening'),
              Card(
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const FullPlayerPage())),
                  child: Padding(
                    padding: const EdgeInsets.all(10),
                    child: Row(children: [
                      SizedBox(width: 58, height: 58,
                        child: MediaCover(url: track['cover_url']?.toString(), height: 58)),
                      const SizedBox(width: 12),
                      Expanded(child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('PICK UP WHERE YOU LEFT OFF',
                            style: TextStyle(color: Color(0xFFD9B96E), fontSize: 9,
                              letterSpacing: 1.1, fontWeight: FontWeight.w700)),
                          const SizedBox(height: 5),
                          Text((track['title'] ?? 'Now playing').toString(),
                            maxLines: 1, overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontWeight: FontWeight.w700)),
                          Text((track['artist'] ?? 'High-Tech Entertainment').toString(),
                            maxLines: 1, overflow: TextOverflow.ellipsis,
                            style: const TextStyle(color: Colors.white54, fontSize: 12)),
                        ],
                      )),
                      const Icon(Icons.play_circle_fill,
                        color: Color(0xFFD9B96E), size: 34),
                    ]),
                  ),
                ),
              ),
            ]),
          );
        },
      );
    },
  );
}

class VideoPreviewList extends StatelessWidget {
  const VideoPreviewList({super.key});

  Future<void> _openVideo(BuildContext context, Map<String, dynamic> item) async {
    final raw = (item['video_url'] ?? '').toString().trim();
    final uri = Uri.tryParse(raw);
    if (uri == null || (uri.scheme != 'https' && uri.scheme != 'http') || uri.host.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('This video is not available yet.')));
      return;
    }
    final host = uri.host.toLowerCase();
    if (host.contains('youtube.com') || host == 'youtu.be' || host.contains('vimeo.com')) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
      return;
    }
    if (!context.mounted) return;
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => HteVideoPlayerPage(
        url: raw,
        title: (item['title'] ?? 'High-Tech video').toString(),
      ),
    ));
  }

  @override
  Widget build(BuildContext context) => RetryFutureBuilder<List<Map<String, dynamic>>>(
    future: () => rows('videos', 'created_at'),
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return Notice(friendlyErrorMessage(snapshot.error!));
      }
      if (!snapshot.hasData) return const Busy();
      final videos = snapshot.data!;
      if (videos.isEmpty) {
        return const Notice('Official High-Tech visual releases will appear here.');
      }
      return SizedBox(
        height: 184,
        child: ListView.separated(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          scrollDirection: Axis.horizontal,
          itemCount: videos.take(8).length,
          separatorBuilder: (_, __) => const SizedBox(width: 12),
          itemBuilder: (context, index) {
            final item = videos[index];
            return SizedBox(
              width: 228,
              child: InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: () => _openVideo(context, item),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Expanded(child: Stack(fit: StackFit.expand, children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(14),
                      child: MediaCover(
                        url: item['thumbnail_url']?.toString(),
                        height: 126),
                    ),
                    const Center(child: Icon(Icons.play_circle_fill,
                      color: Colors.white, size: 48)),
                    Positioned(
                      left: 8, bottom: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: .72),
                          borderRadius: BorderRadius.circular(6)),
                        child: const Text('HT VIDEO',
                          style: TextStyle(fontSize: 8, letterSpacing: 1.1,
                            fontWeight: FontWeight.w800, color: Color(0xFFE8CB8B))),
                      ),
                    ),
                  ])),
                  const SizedBox(height: 7),
                  Text((item['title'] ?? 'Music video').toString(),
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12)),
                  Text((item['artist'] ?? 'High-Tech Entertainment').toString(),
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white54, fontSize: 10)),
                ]),
              ),
            );
          },
        ),
      );
    },
  );
}


class _HomeQuickAction extends StatelessWidget {
  const _HomeQuickAction({required this.icon, required this.title, required this.subtitle, required this.onTap});
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(16),
    child: Container(
      padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(
        color: const Color(0xFF141517),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFF34302A)),
      ),
      child: Row(children: [
        Icon(icon, color: const Color(0xFFD9B96E), size: 22),
        const SizedBox(width: 9),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
          const SizedBox(height: 3),
          Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 9, color: Color(0xFF9B978F))),
        ])),
      ]),
    ),
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
  Widget build(BuildContext context) => RetryFutureBuilder<List<Map<String, dynamic>>>(
    future: () => rows('artists', 'name'),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Notice(friendlyErrorMessage(snapshot.error!));
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
        RetryFutureBuilder<List<Map<String,dynamic>>>(
          future: () => db.from('tracks').select().eq('is_published',true).eq('artist',name).order('created_at',ascending:false).then((data)=>List<Map<String,dynamic>>.from(data).where(_isPlayableTrack).toList()),
          builder:(context,snapshot)=>snapshot.hasError?Notice(friendlyErrorMessage(snapshot.error!)):!snapshot.hasData?const Busy():snapshot.data!.isEmpty?const Notice('Published tracks by this artist will appear here.'):TrackList(items:snapshot.data!),
        ),
      ]),
    );
  }
}

class ReleaseList extends StatelessWidget {
  const ReleaseList({super.key, this.horizontal = false});
  final bool horizontal;
  @override
  Widget build(BuildContext context) => RetryFutureBuilder<List<Map<String, dynamic>>>(
    future: () => rows('releases', 'created_at'),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Notice(friendlyErrorMessage(snapshot.error!));
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
                                    SnackBar(content: Text(friendlyErrorMessage(error))),
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
  Widget build(BuildContext context) => RetryFutureBuilder<List<Map<String, dynamic>>>(
    future: () => items == null ? playableTracks() : Future.value(items!.where(_isPlayableTrack).toList()),
    builder: (context, snapshot) {
      if (snapshot.hasError) return Notice(friendlyErrorMessage(snapshot.error!));
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
                tooltip: 'Download for offline listening',
                icon: const Icon(Icons.download_for_offline_outlined, size: 21),
                onPressed: () async {
                  try {
                    final url = (item['audio_url'] ?? '').toString().trim();
                    if (isSpotifyUrl(url)) {
                      throw StateError('Spotify streams cannot be downloaded here.');
                    }
                    await downloadRemoteMedia(
                      url: url,
                      title: (item['title'] ?? 'High-Tech track').toString(),
                      kind: 'mp3',
                    );
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Track saved to your Offline media library.')),
                      );
                    }
                  } catch (error) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text(friendlyErrorMessage(error))),
                      );
                    }
                  }
                },
              ),
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
                      SnackBar(content: Text(friendlyErrorMessage(error))),
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
        return Container(
          margin: const EdgeInsets.fromLTRB(10, 8, 10, 2),
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
          decoration: BoxDecoration(
            gradient: const LinearGradient(colors: [Color(0xFF2B2418), Color(0xFF171B20)]),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: const Color(0xFF6B5735)),
          ),
          child: const Row(children: [
            Icon(Icons.campaign_rounded, color: Color(0xFFE3BD70), size: 19),
            SizedBox(width: 10),
            Expanded(child: Text('HIGH-TECH PROMOTION  ·  SPONSORED AUDIO',
              style: TextStyle(color: Color(0xFFE8D9B8), fontSize: 9, fontWeight: FontWeight.w800, letterSpacing: .7))),
            Icon(Icons.graphic_eq_rounded, color: Color(0xFFE3BD70), size: 18),
          ]),
        );
      }
      return Container(
        margin: const EdgeInsets.fromLTRB(10, 7, 10, 2),
        decoration: BoxDecoration(
          gradient: const LinearGradient(begin: Alignment.centerLeft, end: Alignment.centerRight,
            colors: [Color(0xFF1C2832), Color(0xFF121923)]),
          borderRadius: BorderRadius.circular(15),
          border: Border.all(color: const Color(0xFF405464)),
          boxShadow: const [BoxShadow(color: Color(0x33000000), blurRadius: 12, offset: Offset(0, 4))],
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            borderRadius: BorderRadius.circular(15),
            onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const FullPlayerPage())),
            child: Padding(padding: const EdgeInsets.symmetric(horizontal:9,vertical:5),child:Row(children:[
              ClipRRect(borderRadius: BorderRadius.circular(9),
                child: SizedBox(width:40,height:40,child:MediaCover(url:track['cover_url']?.toString(),height:40))),
              const SizedBox(width:10),
              Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,mainAxisSize:MainAxisSize.min,children:[
                Text((track['title']??'High-Tech Player').toString(),maxLines:1,overflow:TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800)),
                Text((track['artist']??'').toString(),style:const TextStyle(color:Color(0xFF9EADB8),fontSize:10),maxLines:1),
              ])),
              StreamBuilder<PlayerState>(stream:player.playerStateStream,builder:(context,s){
                final playing=s.data?.playing??false;
                return IconButton(
                  visualDensity: VisualDensity.compact,
                  tooltip: playing ? 'Pause' : 'Play',
                  onPressed:()=>playing?player.pause():player.play(),
                  icon:Icon(playing?Icons.pause_rounded:Icons.play_arrow_rounded,color:const Color(0xFFE3BD70),size:24));
              }),
              IconButton(
                visualDensity: VisualDensity.compact,
                tooltip: 'Next track',
                onPressed:()=>playback.next(),
                icon:const Icon(Icons.skip_next_rounded,color:Color(0xFFB9C7D1))),
            ])),
          ),
        ),
      );
    },
  );
}

class FullPlayerPage extends StatelessWidget {
  const FullPlayerPage({super.key});

  String _time(Duration value) {
    final hours = value.inHours;
    final minutes = value.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = value.inSeconds.remainder(60).toString().padLeft(2, '0');
    return hours > 0 ? '$hours:$minutes:$seconds' : '$minutes:$seconds';
  }

  Future<void> _seekBy(Duration delta) async {
    final duration = player.duration ?? Duration.zero;
    final target = player.position + delta;
    final clamped = target < Duration.zero
        ? Duration.zero
        : target > duration
            ? duration
            : target;
    await player.seek(clamped);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('NOW PLAYING', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w900, letterSpacing: 1.5)),
      actions: [
        IconButton(
          tooltip: 'Playback queue',
          onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const QueuePage())),
          icon: const Icon(Icons.queue_music_rounded, color: Color(0xFFE3BD70)),
        ),
      ],
    ),
    body: Stack(children: [
      const Positioned.fill(child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(0xFF17130D), Color(0xFF0B1118), Color(0xFF07090D)],
          ),
        ),
      )),
      ValueListenableBuilder<bool>(
      valueListenable: playback.adPlaying,
      builder: (context, isAd, _) {
        if (isAd) {
          return Center(child: Container(
            margin: const EdgeInsets.all(24),
            padding: const EdgeInsets.all(26),
            decoration: BoxDecoration(
              gradient: const LinearGradient(colors: [Color(0xFF302719), Color(0xFF111923)]),
              borderRadius: BorderRadius.circular(25),
              border: Border.all(color: const Color(0xFF80653A)),
            ),
            child: const Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.campaign_rounded, size: 58, color: Color(0xFFE3BD70)),
                SizedBox(height: 18),
                Text('HIGH-TECH PROMOTION',
                  style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900, letterSpacing: 1.0)),
                SizedBox(height: 8),
                Text('Please listen until the sponsored audio finishes.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Color(0xFFB6C1CA), fontSize: 12)),
                SizedBox(height: 20),
                CircularProgressIndicator(color: Color(0xFFE3BD70)),
              ],
            ),
          ));
        }
        return ValueListenableBuilder<Map<String,dynamic>?>(
          valueListenable: playback.currentTrack,
          builder: (context, track, _) => track == null
            ? const Notice('Choose a track to start listening.')
            : LayoutBuilder(
              builder: (context, constraints) {
                final artworkSize = (constraints.maxWidth - 56).clamp(150.0, 280.0).toDouble();
                return SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(22, 18, 22, 28),
                  child: ConstrainedBox(
                    constraints: BoxConstraints(minHeight: constraints.maxHeight - 46),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Text('HIGH-TECH AUDIO EXPERIENCE',
                          style: TextStyle(color: Color(0xFFE3BD70), fontSize: 8,
                            fontWeight: FontWeight.w900, letterSpacing: 1.8)),
                        const SizedBox(height: 13),
                        Container(
                          width: artworkSize + 8,
                          height: artworkSize + 8,
                          padding: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            gradient: const SweepGradient(colors: [
                              Color(0xFF77572C), Color(0xFFE5C477),
                              Color(0xFF587D91), Color(0xFF77572C),
                            ]),
                            borderRadius: BorderRadius.circular(27),
                            boxShadow: const [
                              BoxShadow(color: Color(0x553D2C08), blurRadius: 30, spreadRadius: 2),
                            ],
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(23),
                            child: MediaCover(url: track['cover_url']?.toString(), height: artworkSize),
                          ),
                        ),
                        const SizedBox(height: 23),
                        Text((track['title'] ?? 'Now playing').toString(),
                          style: const TextStyle(fontSize: 24, height: 1.15,
                            fontWeight: FontWeight.w900, letterSpacing: -.6),
                          textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis),
                        const SizedBox(height: 7),
                        Text((track['artist'] ?? 'High-Tech Entertainment').toString(),
                          style: const TextStyle(color: Color(0xFFAFBBC5), fontSize: 13),
                          textAlign: TextAlign.center),
                        ValueListenableBuilder<String?>(
                          valueListenable: playback.playbackError,
                          builder: (context, error, _) => error == null
                            ? const SizedBox(height: 10)
                            : Padding(padding: const EdgeInsets.only(top: 10),
                              child: Text(error, textAlign: TextAlign.center,
                                style: const TextStyle(color: Color(0xFFFFAAA4), fontSize: 12))),
                        ),
                        StreamBuilder<Duration>(
                          stream: player.positionStream,
                          initialData: Duration.zero,
                          builder: (context, positionSnapshot) {
                            final duration = player.duration ?? Duration.zero;
                            final position = positionSnapshot.data ?? Duration.zero;
                            final maxMs = duration.inMilliseconds;
                            final currentMs = maxMs <= 0
                                ? 0.0
                                : position.inMilliseconds.clamp(0, maxMs).toDouble();
                            return Column(children: [
                              Slider(
                                value: currentMs,
                                max: maxMs <= 0 ? 1.0 : maxMs.toDouble(),
                                onChanged: maxMs <= 0
                                    ? null
                                    : (value) => player.seek(Duration(milliseconds: value.round())),
                              ),
                              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(_time(position), style: const TextStyle(color: Colors.white60, fontSize: 12)),
                                  Text(_time(duration), style: const TextStyle(color: Colors.white60, fontSize: 12)),
                                ]),
                            ]);
                          },
                        ),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            IconButton(
                              tooltip: 'Shuffle',
                              onPressed: () => playback.toggleShuffle(),
                              icon: Icon(playback.shuffle ? Icons.shuffle_on : Icons.shuffle,
                                color: playback.shuffle ? const Color(0xFFD4AF37) : null),
                            ),
                            IconButton(
                              tooltip: 'Back 10 seconds',
                              onPressed: () => _seekBy(const Duration(seconds: -10)),
                              icon: const Icon(Icons.replay_10),
                            ),
                            IconButton(
                              tooltip: 'Previous track',
                              iconSize: 38,
                              onPressed: () => playback.previous(),
                              icon: const Icon(Icons.skip_previous),
                            ),
                            StreamBuilder<PlayerState>(
                              stream: player.playerStateStream,
                              builder: (context, snapshot) {
                                final playing = snapshot.data?.playing ?? false;
                                return IconButton(
                                  tooltip: playing ? 'Pause' : 'Play',
                                  iconSize: 66,
                                  color: const Color(0xFFD4AF37),
                                  onPressed: () => playing ? player.pause() : player.play(),
                                  icon: Icon(playing ? Icons.pause_circle_filled : Icons.play_circle_fill),
                                );
                              },
                            ),
                            IconButton(
                              tooltip: 'Next track',
                              iconSize: 38,
                              onPressed: () => playback.next(),
                              icon: const Icon(Icons.skip_next),
                            ),
                            IconButton(
                              tooltip: 'Forward 10 seconds',
                              onPressed: () => _seekBy(const Duration(seconds: 10)),
                              icon: const Icon(Icons.forward_10),
                            ),
                            StreamBuilder<LoopMode>(
                              stream: player.loopModeStream,
                              initialData: LoopMode.off,
                              builder: (context, snapshot) {
                                final repeating = snapshot.data == LoopMode.one;
                                return IconButton(
                                  tooltip: repeating ? 'Turn repeat off' : 'Repeat this track',
                                  onPressed: () => playback.setRepeat(!repeating),
                                  icon: Icon(Icons.repeat_one,
                                    color: repeating ? const Color(0xFFD4AF37) : null),
                                );
                              },
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.speed, size: 18, color: Color(0xFFD9B96E)),
                            const SizedBox(width: 8),
                            const Text('Playback speed'),
                            const SizedBox(width: 10),
                            PopupMenuButton<double>(
                              tooltip: 'Choose audio playback speed',
                              onSelected: (speed) => player.setSpeed(speed),
                              itemBuilder: (_) => [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]
                                  .map((speed) => PopupMenuItem<double>(
                                    value: speed,
                                    child: Text(speed == 1.0 ? 'Normal (1×)' : '$speed×'),
                                  )).toList(),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 8),
                                decoration: BoxDecoration(
                                  border: Border.all(color: const Color(0xFFD9B96E)),
                                  borderRadius: BorderRadius.circular(20),
                                ),
                                child: const Text('Change', style: TextStyle(color: Color(0xFFD9B96E))),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        const Text('Quality Sound, Global Vibes',
                          style: TextStyle(color: Colors.white54, fontSize: 12)),
                      ],
                    ),
                  ),
                );
              },
            ),
        );
      },
      ),
    ]),
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
    Expanded(child: RetryFutureBuilder<List<Map<String, dynamic>>>(
      future: () => rows('videos', 'created_at'),
      builder: (context, snapshot) {
        if (snapshot.hasError) return Notice(friendlyErrorMessage(snapshot.error!));
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
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    tooltip: 'Download video for offline viewing',
                    icon: const Icon(Icons.download_for_offline_outlined, color: Color(0xFFD4AF37)),
                    onPressed: () async {
                      try {
                        final raw = (item['video_url'] ?? '').toString().trim();
                        final uri = Uri.tryParse(raw);
                        final isDirect = uri != null &&
                            (uri.scheme == 'https' || uri.scheme == 'http') &&
                            uri.host.isNotEmpty &&
                            !raw.contains('youtube.com') &&
                            !raw.contains('youtu.be') &&
                            !raw.contains('vimeo.com');
                        if (!isDirect) {
                          throw StateError('Only direct video-file links can be downloaded. YouTube and Vimeo must be watched online.');
                        }
                        await downloadRemoteMedia(
                          url: raw,
                          title: (item['title'] ?? 'High-Tech video').toString(),
                          kind: 'mp4',
                        );
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Video saved to your Offline media library.')),
                          );
                        }
                      } catch (error) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(friendlyErrorMessage(error))),
                          );
                        }
                      }
                    },
                  ),
                  const Icon(Icons.open_in_new, color: Color(0xFFD4AF37)),
                ],
              ),
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
  Timer? _searchDebounce;
  List<Map<String, dynamic>> results = [];
  List<String> availableGenres = [];
  String selectedGenre = 'All';
  bool loading = true;
  String? error;
  int _requestNumber = 0;

  @override
  void initState() {
    super.initState();
    search('');
  }

  Future<void> search(String term, {String? genre}) async {
    if (!mounted) return;
    final request = ++_requestNumber;
    setState(() {
      loading = true;
      error = null;
      if (genre != null) selectedGenre = genre;
    });
    try {
      final tracks = await publishedTracks(term);
      if (!mounted || request != _requestNumber) return;
      final genres = tracks
          .map((track) => (track['genre'] ?? '').toString().trim())
          .where((value) => value.isNotEmpty && value.toLowerCase() != 'spotify')
          .toSet()
          .toList()
        ..sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
      availableGenres = genres;
      if (selectedGenre != 'All' && !availableGenres.contains(selectedGenre)) {
        selectedGenre = 'All';
      }
      results = selectedGenre == 'All'
          ? tracks
          : tracks.where((track) =>
              (track['genre'] ?? '').toString().trim() == selectedGenre).toList();
    } catch (e) {
      if (!mounted || request != _requestNumber) return;
      error = friendlyErrorMessage(e);
    }
    if (mounted && request == _requestNumber) setState(() => loading = false);
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _requestNumber++;
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final genres = ['All', ...availableGenres];
    return Column(children: [
      const Brand(),
      Padding(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('FIND YOUR NEXT SOUND',
            style: TextStyle(color: Color(0xFFD9B96E), fontSize: 10,
              letterSpacing: 2, fontWeight: FontWeight.w800)),
          const SizedBox(height: 5),
          const Text('Discover music',
            style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
          const SizedBox(height: 5),
          const Text('Explore the sounds of High-Tech Entertainment.',
            style: TextStyle(color: Colors.white60, fontSize: 12)),
        ]),
      ),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: TextField(
          controller: controller,
          onChanged: (value) {
            _searchDebounce?.cancel();
            _searchDebounce = Timer(
              const Duration(milliseconds: 300),
              () => search(value),
            );
          },
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            prefixIcon: const Icon(Icons.search_rounded),
            hintText: 'Search tracks by title',
            filled: true,
            fillColor: const Color(0xFF111923),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(16)),
          ),
        ),
      ),
      const SizedBox(height: 12),
      SizedBox(
        height: 42,
        child: ListView.separated(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          scrollDirection: Axis.horizontal,
          itemCount: genres.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (context, index) {
            final genre = genres[index];
            final selected = genre == selectedGenre;
            return ChoiceChip(
              label: Text(genre),
              selected: selected,
              onSelected: (_) => search(controller.text, genre: genre),
              selectedColor: const Color(0xFFD9B96E),
              backgroundColor: const Color(0xFF151619),
              side: BorderSide(
                color: selected ? const Color(0xFFD9B96E) : const Color(0xFF393A3B)),
              labelStyle: TextStyle(
                color: selected ? const Color(0xFF17130B) : Colors.white70,
                fontWeight: FontWeight.w700,
                fontSize: 11),
            );
          },
        ),
      ),
      Expanded(
        child: loading
            ? const Busy()
            : error != null
                ? Notice(error!, onRetry: () => search(controller.text))
                : results.isEmpty
                    ? const Notice('No tracks match this search yet. Try another genre or search term.')
                    : Column(children: [
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
                          child: Row(children: [
                            const Expanded(child: Text('AVAILABLE TO PLAY',
                              style: TextStyle(color: Color(0xFFD9B96E),
                                fontSize: 9, letterSpacing: 1.4,
                                fontWeight: FontWeight.w800))),
                            Text('${results.length} tracks',
                              style: const TextStyle(color: Colors.white54, fontSize: 11)),
                          ]),
                        ),
                        Expanded(child: TrackList(items: results)),
                      ]),
      ),
    ]);
  }
}

class SavedTab extends StatefulWidget {
  const SavedTab({super.key});
  @override
  State<SavedTab> createState() => _SavedTabState();
}
class _SavedTabState extends State<SavedTab> {
  List<Map<String,dynamic>> saved = [];
  bool loading = true;
  String? loadError;
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
    if (mounted) setState(() { loading = true; loadError = null; });
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
      loadError = friendlyErrorMessage(error);
      debugPrint('Could not load synced favourites: $error');
    }
    if (mounted) setState(() => loading = false);
  }
  @override
  Widget build(BuildContext context) => Column(children: [
    const Brand(),
    const Align(alignment: Alignment.centerLeft, child: Padding(padding: EdgeInsets.symmetric(horizontal: 20), child: Text('Your favourites', style: TextStyle(fontSize: 25, fontWeight: FontWeight.bold)))),
    Expanded(child: loading
      ? const Busy()
      : loadError != null
          ? Notice(loadError!, onRetry: load)
          : saved.isEmpty
              ? const Notice('No saved tracks yet. Tap the heart beside a track to add it here.')
              : TrackList(items: saved)),
  ]);
}

class ReleasesTab extends StatelessWidget {
  const ReleasesTab({super.key});
  @override
  Widget build(BuildContext context) => Column(children: [
    const Brand(tagline: 'OFFICIAL RELEASES'),
    Padding(
      padding: const EdgeInsets.fromLTRB(19, 3, 19, 13),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(17),
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [Color(0xFF33291B), Color(0xFF151C24)]),
          borderRadius: BorderRadius.circular(19),
          border: Border.all(color: const Color(0xFF6C5736)),
        ),
        child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('THE RELEASE VAULT', style: TextStyle(color: Color(0xFFE5C47C), fontSize: 9, fontWeight: FontWeight.w900, letterSpacing: 1.7)),
          SizedBox(height: 7),
          Text('Every drop.\nOne destination.', style: TextStyle(fontSize: 22, height: 1.08, fontWeight: FontWeight.w900)),
          SizedBox(height: 6),
          Text('Official music and projects from the High-Tech family.', style: TextStyle(color: Color(0xFFB0BAC2), fontSize: 10)),
        ]),
      ),
    ),
    const Expanded(child: ReleaseList()),
  ]);
}

class LibraryTab extends StatelessWidget {
  const LibraryTab({super.key});
  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 3,
    child: Column(children: [
      const Brand(tagline: 'YOUR PERSONAL SOUNDSPACE'),
      const Padding(
        padding: EdgeInsets.fromLTRB(19, 1, 19, 12),
        child: Align(alignment: Alignment.centerLeft,
          child: Text('Your collection', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, letterSpacing: -.6))),
      ),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14),
        child: Container(
          decoration: BoxDecoration(color: const Color(0xFF111923), borderRadius: BorderRadius.circular(15),
            border: Border.all(color: const Color(0xFF34404D))),
          child: const TabBar(
            dividerColor: Colors.transparent,
            indicatorSize: TabBarIndicatorSize.tab,
            indicator: BoxDecoration(
              gradient: LinearGradient(colors: [Color(0xFF5B4728), Color(0xFF30281C)]),
              borderRadius: BorderRadius.all(Radius.circular(12)),
              border: Border.fromBorderSide(BorderSide(color: Color(0xFFB18B4C))),
            ),
            labelColor: Color(0xFFF0D18B),
            unselectedLabelColor: Color(0xFF8C9AA6),
            labelStyle: TextStyle(fontSize: 10, fontWeight: FontWeight.w800),
            tabs:[Tab(text:'Saved'), Tab(text:'Playlists'), Tab(text:'On device')],
          ),
        ),
      ),
      const SizedBox(height: 8),
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
  String? loadError;
  @override
  void initState(){super.initState();load();}
  Future<void> load() async {
    if (mounted) setState(() { loading = true; loadError = null; });
    try {
      final user=db.auth.currentUser;
      if(user==null){items=[];} else {
        final data=await db.from('playlists').select().eq('user_id',user.id).order('created_at',ascending:false);
        items=List<Map<String,dynamic>>.from(data);
      }
    } catch(e){
      loadError=friendlyErrorMessage(e);
      debugPrint('Playlist load failed: $e');
    }
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
    catch(e){if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(friendlyErrorMessage(e))));}
  }
  @override
  Widget build(BuildContext context)=>loading
    ? const Busy()
    : loadError != null
        ? Notice(loadError!, onRetry: load)
        : Column(children:[
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
  String? loadError;
  @override
  void initState(){super.initState();load();}
  Future<void> load() async {
    if (mounted) setState(() { loading = true; loadError = null; });
    try {
      final links=await db.from('playlist_tracks').select('track_id').eq('playlist_id',widget.playlist['id']).order('position');
      final ids=links.map((e)=>e['track_id'].toString()).toList();
      if(ids.isEmpty){tracks=[];} else {
        final data=await db.from('tracks').select().eq('is_published',true).inFilter('id',ids);
        final byId={for(final row in data) row['id'].toString():Map<String,dynamic>.from(row)};
        tracks=ids.where(byId.containsKey).map((id)=>byId[id]!).toList();
      }
    }catch(e){
      loadError=friendlyErrorMessage(e);
      debugPrint('Playlist tracks load failed: $e');
    }
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
    }catch(e){if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(friendlyErrorMessage(e))));}
  }
  @override
  Widget build(BuildContext context)=>Scaffold(
    appBar:AppBar(title:Text((widget.playlist['name']??'Playlist').toString())),
    body:loading
      ? const Busy()
      : loadError != null
          ? Notice(loadError!, onRetry: load)
          : Column(children:[
      Padding(padding:const EdgeInsets.all(16),child:Row(children:[
        Expanded(child:Text('${tracks.length} tracks',style:const TextStyle(color:Colors.white60))),
        FilledButton.tonalIcon(onPressed:addTracks,icon:const Icon(Icons.add),label:const Text('Add tracks')),
      ])),
      Expanded(child:tracks.isEmpty?const Notice('This playlist is empty. Add tracks from the High-Tech catalogue.'):TrackList(items:tracks)),
    ]),
  );
}

class NotificationsPage extends StatefulWidget {
  const NotificationsPage({super.key});
  @override
  State<NotificationsPage> createState() => _NotificationsPageState();
}
class _NotificationsPageState extends State<NotificationsPage> {
  late Future<List<Map<String,dynamic>>> _notifications;
  @override
  void initState() { super.initState(); _notifications = _load(); }
  Future<List<Map<String,dynamic>>> _load() async {
    final user = db.auth.currentUser;
    if (user == null) return [];
    final rows = await db.from('app_notifications')
      .select('id,title,body,category,created_at')
      .or('user_id.eq.${user.id},user_id.is.null')
      .order('created_at', ascending: false)
      .limit(100);
    return List<Map<String,dynamic>>.from(rows);
  }
  void _retry() => setState(() => _notifications = _load());
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Notifications'), actions: [
      IconButton(tooltip: 'Refresh', onPressed: _retry, icon: const Icon(Icons.refresh)),
    ]),
    body: FutureBuilder<List<Map<String,dynamic>>>(
      future: _notifications,
      builder: (context, snapshot) {
        if (snapshot.hasError) return Notice(friendlyErrorMessage(snapshot.error!), onRetry: _retry);
        if (!snapshot.hasData) return const Busy();
        final items = snapshot.data!;
        if (items.isEmpty) return const Notice('You are all caught up. New release and account announcements will appear here.');
        return ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: items.length,
          itemBuilder: (context, index) {
            final item = items[index];
            final category = (item['category'] ?? 'announcement').toString();
            final date = DateTime.tryParse((item['created_at'] ?? '').toString());
            return Card(child: ListTile(
              leading: Icon(category == 'release' ? Icons.album_outlined : Icons.notifications_active_outlined, color: const Color(0xFFD9B96E)),
              title: Text((item['title'] ?? 'High-Tech Entertainment').toString()),
              subtitle: Padding(padding: const EdgeInsets.only(top: 6), child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text((item['body'] ?? '').toString()),
                  if (date != null) ...[
                    const SizedBox(height: 6),
                    Text(date.toLocal().toString().split('.').first, style: const TextStyle(color: Colors.white54, fontSize: 11)),
                  ],
                ],
              )),
              isThreeLine: true,
            ));
          },
        );
      },
    ),
  );
}

class ProfileEditPage extends StatefulWidget {
  const ProfileEditPage({super.key});
  @override
  State<ProfileEditPage> createState() => _ProfileEditPageState();
}

class _ProfileEditPageState extends State<ProfileEditPage> {
  final _name = TextEditingController();
  final _bio = TextEditingController();
  final _phone = TextEditingController();
  String _avatarUrl = '';
  bool _loading = true;
  bool _saving = false;
  bool _uploadingAvatar = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _name.dispose();
    _bio.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final user = db.auth.currentUser;
    if (user == null) {
      if (mounted) setState(() => _loading = false);
      return;
    }
    try {
      final row = await db.from('profiles')
          .select('display_name,bio,phone,avatar_url')
          .eq('id', user.id)
          .maybeSingle();
      if (row != null) {
        _name.text = (row['display_name'] ?? '').toString();
        _bio.text = (row['bio'] ?? '').toString();
        _phone.text = (row['phone'] ?? '').toString();
        _avatarUrl = (row['avatar_url'] ?? '').toString();
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _persistProfile() async {
    final user = db.auth.currentUser;
    if (user == null) throw const AuthException('Please sign in again to update your profile.');
    await db.from('profiles').upsert({
      'id': user.id,
      'display_name': _name.text.trim(),
      'bio': _bio.text.trim(),
      'phone': _phone.text.trim(),
      'avatar_url': _avatarUrl,
      'updated_at': DateTime.now().toIso8601String(),
    });
  }

  String? _storedAvatarPath(String value, String userId) {
    final uri = Uri.tryParse(value);
    if (uri == null) return null;
    final segments = uri.pathSegments;
    final bucketIndex = segments.indexOf('hte-artwork');
    if (bucketIndex < 0 || bucketIndex + 2 >= segments.length) return null;
    final path = segments.sublist(bucketIndex + 1).join('/');
    if (!path.startsWith('avatars/$userId/')) return null;
    return path;
  }

  Future<void> _uploadAvatar() async {
    if (_saving || _uploadingAvatar) return;
    final user = db.auth.currentUser;
    if (user == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Sign in to upload a profile picture.')),
      );
      return;
    }

    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const ['jpg', 'jpeg', 'png', 'webp', 'avif'],
        withData: false,
      );
      if (!mounted || result == null || result.files.isEmpty) return;
      final selected = result.files.single;
      if (selected.size <= 0 || selected.size > 5 * 1024 * 1024) {
        throw const FormatException('Choose a profile image smaller than 5 MB.');
      }
      final localPath = selected.path;
      if (localPath == null || localPath.isEmpty) {
        throw const FormatException('The selected image could not be opened. Please choose it again.');
      }
      final localFile = File(localPath);
      if (!await localFile.exists()) {
        throw const FormatException('The selected image is no longer available on this device.');
      }

      final extension = (selected.extension ?? '').toLowerCase();
      const mimeTypes = <String, String>{
        'jpg': 'image/jpeg',
        'jpeg': 'image/jpeg',
        'png': 'image/png',
        'webp': 'image/webp',
        'avif': 'image/avif',
      };
      final mimeType = mimeTypes[extension];
      if (mimeType == null) {
        throw const FormatException('This image format is not supported.');
      }

      setState(() => _uploadingAvatar = true);
      final oldPath = _storedAvatarPath(_avatarUrl, user.id);
      final storagePath = 'avatars/${user.id}/${DateTime.now().microsecondsSinceEpoch}.$extension';
      await db.storage.from('hte-artwork').upload(
        storagePath,
        localFile,
        fileOptions: FileOptions(
          cacheControl: '3600',
          contentType: mimeType,
          upsert: false,
        ),
      );
      if (!mounted) {
        try {
          await db.storage.from('hte-artwork').remove([storagePath]);
        } catch (cleanupError) {
          debugPrint('Unclaimed HTE profile image cleanup skipped: $cleanupError');
        }
        return;
      }
      final publicUrl = db.storage.from('hte-artwork').getPublicUrl(storagePath);
      setState(() => _avatarUrl = publicUrl);
      await _persistProfile();

      if (oldPath != null && oldPath != storagePath) {
        try {
          await db.storage.from('hte-artwork').remove([oldPath]);
        } catch (cleanupError) {
          debugPrint('Previous HTE profile image cleanup skipped: $cleanupError');
        }
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Your public profile picture has been updated.')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    } finally {
      if (mounted) setState(() => _uploadingAvatar = false);
    }
  }

  Future<void> _save() async {
    if (_saving || _uploadingAvatar) return;
    setState(() => _saving = true);
    try {
      await _persistProfile();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Profile updated successfully.')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('My profile')),
    body: _loading
        ? const Busy()
        : ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 30),
            children: [
              const Brand(tagline: 'YOUR HIGH-TECH ACCOUNT'),
              Container(
                margin: const EdgeInsets.only(bottom: 20),
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [Color(0xFF33291B), Color(0xFF18232D), Color(0xFF111720)],
                  ),
                  borderRadius: BorderRadius.circular(23),
                  border: Border.all(color: const Color(0xFF80663B)),
                ),
                child: Column(children: [
                  Container(
                    width: 116,
                    height: 116,
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: SweepGradient(colors: [
                        Color(0xFF77572C), Color(0xFFE5C477),
                        Color(0xFF587D91), Color(0xFF77572C),
                      ]),
                    ),
                    child: ClipOval(
                      child: _avatarUrl.isEmpty
                          ? Container(
                              color: const Color(0xFF101720),
                              child: const Icon(Icons.person_rounded, size: 58, color: Color(0xFFE3BD70)),
                            )
                          : Image.network(
                              _avatarUrl,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(
                                color: const Color(0xFF101720),
                                child: const Icon(Icons.person_rounded, size: 58, color: Color(0xFFE3BD70)),
                              ),
                            ),
                    ),
                  ),
                  const SizedBox(height: 13),
                  Text(
                    _name.text.trim().isEmpty ? 'Your High-Tech profile' : _name.text.trim(),
                    style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 5),
                  const Text(
                    'PUBLIC PROFILE PHOTO · PRIVATE ACCOUNT DETAILS',
                    style: TextStyle(color: Color(0xFFB4C0C9), fontSize: 8,
                      fontWeight: FontWeight.w800, letterSpacing: 1.1),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 15),
                  OutlinedButton.icon(
                    onPressed: (_saving || _uploadingAvatar) ? null : _uploadAvatar,
                    icon: _uploadingAvatar
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.add_a_photo_outlined),
                    label: Text(_uploadingAvatar ? 'Uploading picture…' : 'Change profile picture'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFFE8CB87),
                      side: const BorderSide(color: Color(0xFF80663B)),
                    ),
                  ),
                ]),
              ),
              const Heading('Profile information'),
              TextField(
                controller: _name,
                textCapitalization: TextCapitalization.words,
                maxLength: 80,
                decoration: const InputDecoration(labelText: 'Display name'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _bio,
                maxLines: 4,
                maxLength: 500,
                decoration: const InputDecoration(labelText: 'About you'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _phone,
                keyboardType: TextInputType.phone,
                maxLength: 30,
                decoration: const InputDecoration(labelText: 'Phone number (optional)'),
              ),
              const SizedBox(height: 18),
              FilledButton(
                onPressed: (_saving || _uploadingAvatar) ? null : _save,
                child: _saving
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Save profile'),
              ),
            ],
          ),
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
    if (busy) return;
    final address = email.text.trim();
    if (address.isEmpty || !isValidEmailAddress(address)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter a valid email address to continue.')),
      );
      return;
    }
    if (password.text.isEmpty || (signup && password.text.length < 8)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(signup
          ? 'Choose a password with at least 8 characters.'
          : 'Enter your password to sign in.')),
      );
      return;
    }
    setState(() => busy = true);
    try {
      if (signup) {
        await db.auth.signUp(email: address, password: password.text);
      } else {
        await db.auth.signInWithPassword(email: address, password: password.text);
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Authentication request completed. Check your email if confirmation is enabled.'),
        ));
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> requestPasswordReset() async {
    final address = email.text.trim();
    if (address.isEmpty || !isValidEmailAddress(address)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter your account email address first.')),
      );
      return;
    }
    if (busy) return;
    setState(() => busy = true);
    try {
      await db.auth.resetPasswordForEmail(address, redirectTo: authRedirect);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('If this email belongs to an account, a password reset link has been sent. Open it on this device.'),
          duration: Duration(seconds: 6),
        ));
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> signInWithGoogle() async {
    setState(() => busy = true);
    try {
      if (googleWebClientId.trim().isNotEmpty) {
        final google = GoogleSignIn.instance;
        if (!_googleSignInInitialized) {
          await google.initialize(serverClientId: googleWebClientId.trim());
          _googleSignInInitialized = true;
        }
        final account = await google.authenticate();
        final authorization = await account.authorizationClient
                .authorizationForScopes(const ['email', 'profile']) ??
            await account.authorizationClient
                .authorizeScopes(const ['email', 'profile']);
        final idToken = account.authentication.idToken;
        if (idToken == null) {
          throw const AuthException('Google did not return an identity token. Check the Web OAuth client ID.');
        }
        await db.auth.signInWithIdToken(
          provider: OAuthProvider.google,
          idToken: idToken,
          accessToken: authorization.accessToken,
        );
      } else {
        // Keep Google sign-in available when the native Web client ID has not
        // yet been supplied. Supabase completes OAuth and returns to this app
        // through the configured login-callback deep link.
        await db.auth.signInWithOAuth(
          OAuthProvider.google,
          redirectTo: authRedirect,
          authScreenLaunchMode: LaunchMode.externalApplication,
        );
      }
    } on AuthException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(friendlyErrorMessage(error))),
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> signInWithProvider(OAuthProvider provider, String label) async {
    if (busy) return;
    setState(() => busy = true);
    try {
      await db.auth.signInWithOAuth(
        provider,
        redirectTo: authRedirect,
        authScreenLaunchMode: LaunchMode.externalApplication,
      );
    } on AuthException catch (error) {
      final message = error.message.toLowerCase();
      final friendly = message.contains('provider is not enabled') || message.contains('unsupported provider')
          ? '$label sign-in is not enabled yet. The provider must first be configured in Supabase Auth.'
          : friendlyErrorMessage(error);
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(friendly)));
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(friendlyErrorMessage(error))));
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
        const SizedBox(height: 10),
        Row(children: [
          Expanded(child: OutlinedButton.icon(
            onPressed: busy ? null : () => signInWithProvider(OAuthProvider.facebook, 'Facebook'),
            icon: const Icon(Icons.facebook),
            label: const Text('Facebook'),
          )),
          const SizedBox(width: 10),
          Expanded(child: OutlinedButton.icon(
            onPressed: busy ? null : () => signInWithProvider(OAuthProvider.twitter, 'X'),
            icon: const Icon(Icons.alternate_email),
            label: const Text('Continue with X'),
          )),
        ]),
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
        TextField(
          controller: password,
          obscureText: true,
          autofillHints: [signup ? AutofillHints.newPassword : AutofillHints.password],
          decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder()),
          onSubmitted: (_) => busy ? null : authenticate(),
        ),
        if (!signup) Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: busy ? null : requestPasswordReset,
            child: const Text('Forgot password?'),
          ),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: busy ? null : authenticate,
          child: busy
            ? const SizedBox(width: 19, height: 19, child: CircularProgressIndicator(strokeWidth: 2))
            : Text(signup ? 'Create account' : 'Sign in'),
        ),
        TextButton(
          onPressed: busy ? null : () => setState(() => signup = !signup),
          child: Text(signup ? 'Already registered? Sign in' : 'Create a new account'),
        ),
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
  const SettingsPage({super.key, this.autoCheckForUpdates = false});
  final bool autoCheckForUpdates;
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String version = 'Loading…';
  bool checking = false;
  bool downloading = false;
  double downloadProgress = 0;
  String updateStatus = '';
  bool autoplaySetting = true;
  bool shuffleSetting = false;
  double videoSpeedSetting = 1.0;

  @override
  void initState() {
    super.initState();
    _loadVersion();
    _loadPlaybackSettings();
    if (widget.autoCheckForUpdates) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _checkForUpdates();
      });
    }
  }

  Future<void> _loadPlaybackSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      autoplaySetting = prefs.getBool('hte_autoplay') ?? true;
      shuffleSetting = prefs.getBool('hte_shuffle') ?? false;
      videoSpeedSetting = prefs.getDouble('hte_video_speed') ?? 1.0;
      playback.autoplay = autoplaySetting;
      playback.shuffle = shuffleSetting;
      // Sponsored audio is controlled by High-Tech Entertainment, not by listeners.
      // Ignore legacy local preferences so older installs cannot disable adverts.
      playback.audioAdsEnabled = true;
      playback.adEvery = HtePlayback.advertInterval;
      await player.setShuffleModeEnabled(shuffleSetting);
    } catch (error) {
      debugPrint('HTE settings could not be loaded: $error');
    }
    if (mounted) setState(() {});
  }

  Future<void> _savePlaybackSetting(String key, Object value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (value is bool) await prefs.setBool(key, value);
      if (value is int) await prefs.setInt(key, value);
      if (value is double) await prefs.setDouble(key, value);
    } catch (error) {
      debugPrint('HTE setting $key could not be saved: $error');
      if (mounted) _message('This setting could not be saved. Please try again.');
    }
  }

  Future<void> _loadVersion() async {
    try {
      final info = await PackageInfo.fromPlatform();
      if (mounted) setState(() => version = info.version);
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

  Future<void> _checkForOtaUpdates() async {
    if (checking || downloading) return;
    setState(() {
      checking = true;
      updateStatus = 'Checking Stable OTA channel…';
    });
    try {
      final result = await HteOtaUpdateService.checkAndInstall();
      if (!mounted) return;
      setState(() => updateStatus = result);
      _message(result);
    } finally {
      if (mounted) setState(() => checking = false);
    }
  }

  Future<void> _checkForUpdates() async {
    if (defaultTargetPlatform != TargetPlatform.android) {
      _message('In-app APK updates are available on Android devices.');
      return;
    }
    setState(() { checking = true; updateStatus = 'Checking for updates…'; });
    try {
      final info = await PackageInfo.fromPlatform();
      final response = await http.get(
        Uri.https('api.github.com', '/repos/suwilanjisinkamba184-cmd/High-tech-entertainment-/releases/latest'),
        headers: {
          'Accept': 'application/vnd.github+json',
          'User-Agent': 'High-Tech-Entertainment-App',
          'Cache-Control': 'no-cache',
          'X-GitHub-Api-Version': '2022-11-28',
        },
      ).timeout(const Duration(seconds: 20));
      if (!mounted) return;
      if (response.statusCode == 404) {
        _message('No official app update has been published yet.');
        return;
      }
      if (response.statusCode != 200) {
        final detail = response.statusCode == 403 || response.statusCode == 429
            ? 'GitHub is temporarily limiting update checks. Please try again in a few minutes.'
            : 'Update service returned HTTP ${response.statusCode}. Please retry.';
        _message(detail);
        return;
      }
      final release = jsonDecode(response.body) as Map<String, dynamic>;
      if (release['draft'] == true || release['prerelease'] == true) {
        _message('No stable update is available yet.');
        return;
      }
      final releaseTag = (release['tag_name'] ?? '').toString();
      final isSuRelease = releaseTag.toUpperCase().endsWith('-SU');
      final latest = releaseTag
          .replaceFirst(RegExp(r'^[vV]'), '')
          .replaceFirst(RegExp(r'-SU$', caseSensitive: false), '');
      // Compare semantic version, Android build number and SU flavour separately.
      // This catches same-version hotfixes and SU flavour upgrades reliably.
      final installedSu = info.version.toUpperCase().contains(' SU');
      final installedSemanticVersion = info.version.replaceFirst(
        RegExp(r'\s+SU$', caseSensitive: false), '').trim();
      final installedBuild = int.tryParse(info.buildNumber) ?? 0;
      final releaseBody = (release['body'] ?? '').toString();
      final releaseBuildMatch = RegExp(
        r'Android build:\s*(\d+)', caseSensitive: false).firstMatch(releaseBody);
      final latestBuild = int.tryParse(releaseBuildMatch?.group(1) ?? '') ?? 0;
      final versionComparison = _compareVersions(latest, installedSemanticVersion);
      final buildUpgradeAvailable = latestBuild > 0 && latestBuild > installedBuild;
      final flavourUpgradeAvailable = isSuRelease && !installedSu;
      final updateAvailable = versionComparison > 0 ||
          (versionComparison == 0 && buildUpgradeAvailable) ||
          flavourUpgradeAvailable;
      if (latest.isEmpty || !updateAvailable) {
        _message('You are using the latest published version ($version).');
        return;
      }
      final assets = release['assets'] as List<dynamic>? ?? [];
      Map<String, dynamic>? apk;
      for (final raw in assets) {
        final item = raw as Map<String, dynamic>;
        final name = (item['name'] ?? '').toString().toLowerCase();
        if (name.endsWith('.apk') && (name.contains('high-tech') || apk == null)) {
          apk = item;
          if (name == 'high-tech-entertainment.apk') break;
        }
      }
      if (apk == null) {
        _message('Version $latest is published, but its Android APK is not attached yet.');
        return;
      }
      final apkUrl = (apk['browser_download_url'] ?? '').toString();
      final expectedSize = (apk['size'] as num?)?.toInt() ?? 0;
      final digest = (apk['digest'] ?? '').toString();
      if (!apkUrl.startsWith('https://') || expectedSize <= 0) {
        _message('The published APK details are incomplete. Please try again later.');
        return;
      }
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('Update available'),
          content: Text('High-Tech Entertainment $latest is available. You have $version installed. Download the APK now? Your account and app data will be kept.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Later')),
            FilledButton(
              onPressed: () {
                Navigator.pop(dialogContext);
                _downloadAndInstall(apk!, latest, expectedSize, digest);
              },
              child: const Text('Download update'),
            ),
          ],
        ),
      );
    } catch (_) {
      if (mounted) _message('Could not check for updates. Check your internet connection and try again.');
    } finally {
      if (mounted) setState(() { checking = false; updateStatus = ''; });
    }
  }

  Future<void> _downloadAndInstall(
    Map<String, dynamic> asset,
    String latest,
    int expectedSize,
    String expectedDigest,
  ) async {
    if (downloading) return;
    setState(() { downloading = true; downloadProgress = 0; updateStatus = 'Preparing update…'; });
    File? apkFile;
    try {
      final url = (asset['browser_download_url'] ?? '').toString();
      final client = http.Client();
      try {
        final request = http.Request('GET', Uri.parse(url));
        request.headers['User-Agent'] = 'High-Tech-Entertainment-App';
        final streamed = await client.send(request).timeout(const Duration(seconds: 45));
        if (streamed.statusCode != 200) {
          throw HttpException('APK download failed (HTTP ${streamed.statusCode}).');
        }
        final directory = await getTemporaryDirectory();
        apkFile = File('${directory.path}/hte-update-$latest.apk');
        if (await apkFile.exists()) await apkFile.delete();
        if (!mounted) return;
        final sink = apkFile.openWrite();
        var received = 0;
        setState(() => updateStatus = 'Downloading update…');
        try {
          await for (final chunk in streamed.stream) {
            sink.add(chunk);
            received += chunk.length;
            if (mounted) setState(() => downloadProgress = (received / expectedSize).clamp(0.0, 1.0).toDouble());
          }
          await sink.flush();
        } finally {
          await sink.close();
        }
        if (received != expectedSize) {
          throw const FormatException('The downloaded APK is incomplete. Please retry.');
        }
      } finally {
        client.close();
      }

      final downloadedApk = apkFile;
      final firstBytes = await downloadedApk.openRead(0, 4).first;
      if (firstBytes.length < 4 || firstBytes[0] != 0x50 || firstBytes[1] != 0x4b) {
        throw const FormatException('The downloaded file is not a valid APK archive.');
      }
      if (expectedDigest.startsWith('sha256:')) {
        final actualDigest = await sha256.bind(downloadedApk.openRead()).first;
        if (actualDigest.toString().toLowerCase() != expectedDigest.substring(7).toLowerCase()) {
          throw const FormatException('APK integrity verification failed. The update was not opened.');
        }
      }
      if (!mounted) return;
      setState(() => updateStatus = 'Download complete. Opening Android installer…');
      final result = await OpenFilex.open(
        downloadedApk.path,
        type: 'application/vnd.android.package-archive',
      );
      if (!mounted) return;
      if (result.type != ResultType.done) {
        _message('APK downloaded, but Android could not open the installer (${result.message}). Allow High-Tech Entertainment to install unknown apps in Android Settings, then try again.');
      } else {
        _message('Update downloaded. Follow the Android installation prompt to finish updating.');
      }
    } catch (error) {
      if (mounted) _message(friendlyErrorMessage(error));
      if (apkFile != null && await apkFile.exists()) {
        try { await apkFile.delete(); } catch (_) {}
      }
    } finally {
      if (mounted) setState(() { downloading = false; downloadProgress = 0; updateStatus = ''; });
    }
  }

  Future<void> _openOfflineLibrary() async {
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        appBar: AppBar(title: const Text('Offline media library')),
        body: const SafeArea(child: LocalFilesTab()),
      ),
    ));
  }

  Future<void> _openNotifications() async {
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => const NotificationsPage(),
    ));
  }

  Future<void> _chooseVideoSpeed() async {
    const speeds = <double>[0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0];
    final selected = await showModalBottomSheet<double>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 8, 20, 12),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text('Default video playback speed',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
              ),
            ),
            ...speeds.map((speed) => ListTile(
              leading: Icon(
                videoSpeedSetting == speed ? Icons.radio_button_checked : Icons.radio_button_off,
                color: videoSpeedSetting == speed ? const Color(0xFFD4AF37) : null,
              ),
              title: Text(speed == 1.0 ? 'Normal (1×)' : '$speed×'),
              onTap: () => Navigator.pop(sheetContext, speed),
            )),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (selected == null) return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble('hte_video_speed', selected);
    if (mounted) setState(() => videoSpeedSetting = selected);
  }

  Future<void> _clearTemporaryCache() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Clear temporary cache?'),
        content: const Text(
          'This removes temporary app files only. Your downloaded offline music, videos, account and settings will be kept.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Clear cache'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      final directory = await getTemporaryDirectory();
      if (await directory.exists()) {
        await for (final entity in directory.list()) {
          try {
            await entity.delete(recursive: true);
          } catch (error) {
            debugPrint('Could not clear temporary item: $error');
          }
        }
      }
      if (mounted) _message('Temporary cache cleared. Offline downloads were preserved.');
    } catch (error) {
      if (mounted) _message('Could not clear temporary cache. Please try again.');
      debugPrint('HTE cache clear failed: $error');
    }
  }

  void _openWebsite(String path) {
    launchUrl(
      Uri.parse('https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/$path'),
      mode: LaunchMode.externalApplication,
    );
  }

  void _message(String value) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));

  Widget _themeChoiceTile(
    BuildContext sheetContext,
    ThemeMode value,
    String title,
    String subtitle,
  ) {
    final selected = AppThemeController.mode.value == value;
    return ListTile(
      leading: Icon(
        selected ? Icons.radio_button_checked : Icons.radio_button_off,
        color: selected ? const Color(0xFFD4AF37) : null,
      ),
      title: Text(title),
      subtitle: Text(subtitle),
      onTap: () => Navigator.pop(sheetContext, value),
    );
  }

  Future<void> _chooseTheme() async {
    final selected = await showModalBottomSheet<ThemeMode>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Choose your appearance',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700)),
              const SizedBox(height: 12),
              _themeChoiceTile(
                sheetContext,
                ThemeMode.dark,
                'High-Tech dark',
                'Signature black with gold accents',
              ),
              _themeChoiceTile(
                sheetContext,
                ThemeMode.light,
                'Light',
                'Bright, clean interface',
              ),
              _themeChoiceTile(
                sheetContext,
                ThemeMode.system,
                'System default',
                'Follow Android appearance settings',
              ),
            ],
          ),
        ),
      ),
    );
    if (selected != null) {
      await AppThemeController.setMode(selected);
      if (mounted) setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Settings')),
    body: ListView(padding: const EdgeInsets.all(18), children: [
      const Brand(tagline: 'APP PREFERENCES & INFORMATION'),
      Container(
        margin: const EdgeInsets.fromLTRB(2, 2, 2, 14),
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(0xFF33291B), Color(0xFF18232D), Color(0xFF111720)],
          ),
          borderRadius: BorderRadius.circular(22),
          border: Border.all(color: const Color(0xFF80663B)),
        ),
        child: Row(children: [
          Container(width: 44, height: 44,
            decoration: BoxDecoration(color: const Color(0xFF242A2D),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: const Color(0xFF56616A))),
            child: const Icon(Icons.tune_rounded, color: Color(0xFFE3BD70), size: 23)),
          const SizedBox(width: 13),
          const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('YOUR APP. YOUR SETTINGS.',
              style: TextStyle(color: Color(0xFFE3BD70), fontSize: 8,
                fontWeight: FontWeight.w900, letterSpacing: 1.4)),
            SizedBox(height: 5),
            Text('Control your experience',
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900)),
            SizedBox(height: 4),
            Text('Personalise playback, appearance and updates.',
              style: TextStyle(color: Color(0xFFB4C0C9), fontSize: 10)),
          ])),
        ]),
      ),
      Card(child: Column(children: [
        ListTile(
          leading: const Icon(Icons.palette_outlined, color: Color(0xFFD4AF37)),
          title: const Text('Appearance & themes'),
          subtitle: Text(switch (AppThemeController.mode.value) {
            ThemeMode.light => 'Light theme',
            ThemeMode.system => 'Follow device theme',
            ThemeMode.dark => 'High-Tech dark theme',
          }),
          trailing: const Icon(Icons.chevron_right),
          onTap: _chooseTheme,
        ),
      ])),
      Card(child: Column(children: [
        ListTile(
          leading: const Icon(Icons.system_update_alt_rounded, color: Color(0xFFD4AF37)),
          title: const Text('OTA Update Checker'),
          subtitle: Text(checking && updateStatus.startsWith('Checking Stable')
              ? updateStatus
              : 'Check and download Stable-channel app patches'),
          trailing: checking && updateStatus.startsWith('Checking Stable')
              ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.chevron_right),
          onTap: (checking || downloading) ? null : _checkForOtaUpdates,
        ),
        const Divider(height: 1),
        ListTile(
          leading: const Icon(Icons.system_update_alt, color: Color(0xFFD4AF37)),
          title: const Text('App updates'),
          subtitle: Text(downloading ? updateStatus : 'Check, download and install the latest official Android version'),
          trailing: (checking || downloading) ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.chevron_right),
          onTap: (checking || downloading) ? null : _checkForUpdates,
        ),
        const Divider(height: 1),
        ListTile(leading: const Icon(Icons.info_outline), title: const Text('App version'), subtitle: Text(version)),
      ])),
      if (downloading) ...[
        Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(updateStatus, style: const TextStyle(fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),
          LinearProgressIndicator(value: downloadProgress),
          const SizedBox(height: 6),
          Text('${(downloadProgress * 100).toStringAsFixed(0)}% downloaded', style: const TextStyle(color: Colors.white60)),
        ]))),
      ],
      const SizedBox(height: 12),
      const Heading('Playback preferences'),
      Card(child: Column(children: [
        SwitchListTile(
          title: const Text('Smart Autoplay'),
          subtitle: const Text('Continue with recommended music when the queue ends'),
          value: autoplaySetting,
          onChanged: (value) {
            setState(() => autoplaySetting = value);
            playback.autoplay = value;
            unawaited(_savePlaybackSetting('hte_autoplay', value));
          },
        ),
        SwitchListTile(
          title: const Text('Shuffle'),
          subtitle: const Text('Play queued tracks in random order'),
          value: shuffleSetting,
          onChanged: (value) {
            setState(() => shuffleSetting = value);
            playback.shuffle = value;
            unawaited(player.setShuffleModeEnabled(value));
            unawaited(_savePlaybackSetting('hte_shuffle', value));
          },
        ),
        ListTile(
          leading: const Icon(Icons.slow_motion_video_outlined, color: Color(0xFFD4AF37)),
          title: const Text('Default video playback speed'),
          subtitle: Text(videoSpeedSetting == 1.0 ? 'Normal speed (1×)' : '$videoSpeedSetting× speed'),
          trailing: const Icon(Icons.chevron_right),
          onTap: _chooseVideoSpeed,
        ),
        const Divider(height: 1),
        ListTile(
          leading: const Icon(Icons.campaign_rounded, color: Color(0xFFD4AF37)),
          title: const Text('Sponsored audio adverts'),
          subtitle: const Text('Managed by High-Tech Entertainment. A promotion may play after 8 completed songs, never on app launch, pause or resume.'),
          trailing: const Icon(Icons.shield_outlined, color: Color(0xFFD4AF37)),
        ),
      ])),
      const SizedBox(height: 12),
      const Heading('Downloads & offline listening'),
      Card(child: Column(children: [
        ListTile(
          leading: const Icon(Icons.download_for_offline_outlined, color: Color(0xFFD4AF37)),
          title: const Text('Offline media library'),
          subtitle: const Text('Import, play and remove music or videos stored on this device'),
          trailing: const Icon(Icons.chevron_right),
          onTap: _openOfflineLibrary,
        ),
        const Divider(height: 1),
        ListTile(
          leading: const Icon(Icons.folder_open_outlined),
          title: const Text('Manage saved media'),
          subtitle: const Text('Your offline downloads are stored separately from temporary cache'),
          trailing: const Icon(Icons.chevron_right),
          onTap: _openOfflineLibrary,
        ),
      ])),
      const SizedBox(height: 12),
      const Heading('Notifications & communication'),
      Card(child: ListTile(
        leading: const Icon(Icons.notifications_active_outlined, color: Color(0xFFD4AF37)),
        title: const Text('Notification inbox'),
        subtitle: const Text('View release announcements and account updates'),
        trailing: const Icon(Icons.chevron_right),
        onTap: _openNotifications,
      )),
      const SizedBox(height: 12),
      const Heading('Storage & data'),
      Card(child: ListTile(
        leading: const Icon(Icons.cleaning_services_outlined, color: Color(0xFFD4AF37)),
        title: const Text('Clear temporary cache'),
        subtitle: const Text('Free temporary storage without deleting offline downloads'),
        trailing: const Icon(Icons.chevron_right),
        onTap: _clearTemporaryCache,
      )),
      const SizedBox(height: 12),
      const Heading('Website, privacy & support'),
      Card(child: Column(children: [
        ListTile(leading: const Icon(Icons.language), title: const Text('High-Tech Entertainment website'), trailing: const Icon(Icons.open_in_new),
          onTap: () => _openWebsite('')),
        ListTile(leading: const Icon(Icons.privacy_tip_outlined), title: const Text('Privacy policy'), trailing: const Icon(Icons.open_in_new),
          onTap: () => _openWebsite('privacy-policy.html')),
        ListTile(leading: const Icon(Icons.description_outlined), title: const Text('Terms of use'), trailing: const Icon(Icons.open_in_new),
          onTap: () => _openWebsite('terms-of-use.html')),
        ListTile(leading: const Icon(Icons.delete_outline), title: const Text('Request account deletion'), trailing: const Icon(Icons.open_in_new),
          onTap: () => _openWebsite('account-deletion.html')),
        ListTile(leading: const Icon(Icons.support_agent_outlined), title: const Text('Support & contact'), subtitle: const Text('Visit the official High-Tech Entertainment website'), trailing: const Icon(Icons.open_in_new),
          onTap: () => _openWebsite('')),
      ])),
      const Heading('About the developer'),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: const [
              Text('HIGH TECHNOLOGIES', style: TextStyle(color: Color(0xFFD9B96E), fontWeight: FontWeight.w800, letterSpacing: 1.5, fontSize: 16)),
              SizedBox(height: 12),
              _DeveloperDetail(label: 'Product', value: 'High-Tech Entertainment'),
              _DeveloperDetail(label: 'Founder / Developer', value: 'Simon Suwilanji Sinkamba'),
              _DeveloperDetail(label: 'Developer email', value: 'suwilanjisinkamba184@gmail.com'),
              _DeveloperDetail(label: 'Location', value: 'Kitwe, Zambia'),
              SizedBox(height: 10),
              Text('Combining innovative technology, music, entertainment and digital experiences in one platform.', style: TextStyle(color: Colors.white70, height: 1.5)),
              SizedBox(height: 12),
              Text('Copyright 2026 High Technologies.', style: TextStyle(color: Colors.white54, fontSize: 12)),
            ],
          ),
        ),
      ),
      const Padding(padding: EdgeInsets.all(20), child: Text('Quality Sound, Global Vibes.\\nMusic. Beats. Production. Beyond.',
        textAlign: TextAlign.center, style: TextStyle(color: Colors.white54))),
    ]),
  );
}

class _DeveloperDetail extends StatelessWidget {
  const _DeveloperDetail({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(width: 82, child: Text(label, style: const TextStyle(color: Colors.white54))),
        Expanded(child: Text(value, style: const TextStyle(fontWeight: FontWeight.w600))),
      ],
    ),
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

/// A FutureBuilder with a real reload action for failed network requests.
/// The loader is called again only when the user taps Retry.
class RetryFutureBuilder<T> extends StatefulWidget {
  const RetryFutureBuilder({super.key, required this.future, required this.builder});
  final Future<T> Function() future;
  final Widget Function(BuildContext, AsyncSnapshot<T>) builder;

  @override
  State<RetryFutureBuilder<T>> createState() => _RetryFutureBuilderState<T>();
}

class _RetryFutureBuilderState<T> extends State<RetryFutureBuilder<T>> {
  late Future<T> _pending;

  @override
  void initState() {
    super.initState();
    _pending = widget.future();
  }

  void _retry() {
    setState(() => _pending = widget.future());
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<T>(
    future: _pending,
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return Notice(friendlyErrorMessage(snapshot.error!), onRetry: _retry);
      }
      return widget.builder(context, snapshot);
    },
  );
}

class Busy extends StatelessWidget {
  const Busy({super.key});
  @override
  Widget build(BuildContext context) => const Center(
    child: CircularProgressIndicator(color: Color(0xFFD4AF37)),
  );
}

class Notice extends StatelessWidget {
  const Notice(this.message, {super.key, this.onRetry});
  final String message;
  final VoidCallback? onRetry;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(22),
    child: Column(mainAxisSize: MainAxisSize.min, children: [
      Text(message, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white60)),
      if (onRetry != null) ...[
        const SizedBox(height: 14),
        OutlinedButton.icon(
          onPressed: onRetry,
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Retry'),
        ),
      ],
    ]),
  );

}