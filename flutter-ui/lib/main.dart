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
      if (!mounted) return;
      setState(() => _finished = true);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
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
    if (progress < 0.55) return 'CONNECTING YOUR STUDIO';
    if (progress < 0.82) return 'TUNING THE EXPERIENCE';
    return 'READY TO PLAY';
  }
}

class ListenerHomeScreen extends StatelessWidget {
  const ListenerHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(22, 22, 22, 12),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    SvgPicture.asset(
                      'assets/high-tech-logo.svg',
                      width: 42,
                      height: 42,
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'HIGH-TECH',
                            style: TextStyle(
                              fontWeight: FontWeight.w800,
                              letterSpacing: 2.2,
                            ),
                          ),
                          Text(
                            'ENTERTAINMENT',
                            style: TextStyle(
                              color: _gold,
                              fontSize: 9,
                              letterSpacing: 2.4,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () {},
                      icon: const Icon(Icons.notifications_none_rounded),
                    ),
                    IconButton(
                      onPressed: () {},
                      icon: const Icon(Icons.account_circle_outlined),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(22, 24, 22, 16),
              sliver: SliverToBoxAdapter(
                child: Container(
                  padding: const EdgeInsets.all(22),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(26),
                    gradient: const LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [Color(0xFF49371B), Color(0xFF211A10), _surface],
                    ),
                    border: Border.all(color: _gold.withValues(alpha: 0.24)),
                  ),
                  child: const Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'SOUND WITHOUT LIMITS',
                        style: TextStyle(
                          color: _gold,
                          fontSize: 10,
                          letterSpacing: 2.1,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      SizedBox(height: 12),
                      Text(
                        'Your sound.\nYour world.',
                        style: TextStyle(
                          fontSize: 32,
                          height: 1.08,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      SizedBox(height: 12),
                      Text(
                        'Discover music, artists and original productions.',
                        style: TextStyle(color: Colors.white70, height: 1.4),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(22, 12, 22, 14),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    const Expanded(
                      child: Text(
                        'Explore',
                        style: TextStyle(
                          fontSize: 21,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    TextButton(
                      onPressed: () {},
                      child: const Text('SEE ALL'),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 22),
              sliver: SliverGrid(
                delegate: SliverChildListDelegate([
                  _FeatureTile(
                    icon: Icons.graphic_eq_rounded,
                    title: 'Music',
                    subtitle: 'Listen and discover',
                  ),
                  _FeatureTile(
                    icon: Icons.person_pin_circle_outlined,
                    title: 'Artists',
                    subtitle: 'Meet the talent',
                  ),
                  _FeatureTile(
                    icon: Icons.video_library_outlined,
                    title: 'Videos',
                    subtitle: 'Watch original content',
                  ),
                  _FeatureTile(
                    icon: Icons.favorite_border_rounded,
                    title: 'Your library',
                    subtitle: 'Your saved music',
                  ),
                ]),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  mainAxisSpacing: 12,
                  crossAxisSpacing: 12,
                  childAspectRatio: 1.28,
                ),
              ),
            ),
            const SliverToBoxAdapter(child: SizedBox(height: 32)),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        backgroundColor: _surface,
        indicatorColor: _gold.withValues(alpha: 0.18),
        selectedIndex: 0,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.search_rounded),
            label: 'Discover',
          ),
          NavigationDestination(
            icon: Icon(Icons.library_music_outlined),
            label: 'Library',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline_rounded),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}

class _FeatureTile extends StatelessWidget {
  const _FeatureTile({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withValues(alpha: 0.07)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Icon(icon, color: _gold, size: 27),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 4),
              Text(
                subtitle,
                style: const TextStyle(
                  color: Colors.white54,
                  fontSize: 11,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
