import 'package:flutter_svg/flutter_svg.dart';
import 'package:material_ui/material_ui.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'backend/auth_repository.dart';
import 'backend/catalogue_repository.dart';
import 'hte_theme.dart';
import 'media/media_url_policy.dart';

enum HteDestination {
  home('Home', Icons.home_outlined, Icons.home_rounded),
  discover('Discover', Icons.explore_outlined, Icons.explore_rounded),
  videos('Videos', Icons.ondemand_video_outlined, Icons.ondemand_video_rounded),
  library('Library', Icons.library_music_outlined, Icons.library_music_rounded),
  account('Account', Icons.person_outline_rounded, Icons.person_rounded);

  const HteDestination(this.label, this.icon, this.selectedIcon);

  final String label;
  final IconData icon;
  final IconData selectedIcon;
}

class HteShell extends StatefulWidget {
  const HteShell({required this.backendConfigured, super.key});

  final bool backendConfigured;

  @override
  State<HteShell> createState() => _HteShellState();
}

class _HteShellState extends State<HteShell> {
  int _selectedIndex = 0;
  late Future<HteCatalogue> _catalogueFuture;

  @override
  void initState() {
    super.initState();
    _catalogueFuture = _loadCatalogue();
  }

  Future<HteCatalogue> _loadCatalogue() {
    if (!widget.backendConfigured) return Future.value(HteCatalogue.empty);
    return HteCatalogueRepository().load();
  }

  void _refreshCatalogue() {
    setState(() => _catalogueFuture = _loadCatalogue());
  }

  void _selectDestination(int index) {
    if (index < 0 || index >= HteDestination.values.length) return;
    setState(() => _selectedIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      _HomePage(
        catalogueFuture: _catalogueFuture,
        backendConfigured: widget.backendConfigured,
        onDiscover: () => _selectDestination(1),
        onVideos: () => _selectDestination(2),
        onRetry: _refreshCatalogue,
      ),
      _CataloguePage(
        title: 'Discover music',
        isVideo: false,
        catalogueFuture: _catalogueFuture,
        backendConfigured: widget.backendConfigured,
        onRetry: _refreshCatalogue,
      ),
      _CataloguePage(
        title: 'Official videos',
        isVideo: true,
        catalogueFuture: _catalogueFuture,
        backendConfigured: widget.backendConfigured,
        onRetry: _refreshCatalogue,
      ),
      _LibraryPage(
        backendConfigured: widget.backendConfigured,
        onAccount: () => _selectDestination(4),
      ),
      _AccountPage(backendConfigured: widget.backendConfigured),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxWidth < 720;
        final page = IndexedStack(index: _selectedIndex, children: pages);

        if (compact) {
          return Scaffold(
            body: SafeArea(child: page),
            bottomNavigationBar: NavigationBar(
              selectedIndex: _selectedIndex,
              onDestinationSelected: _selectDestination,
              destinations: [
                for (final destination in HteDestination.values)
                  NavigationDestination(
                    icon: Icon(destination.icon),
                    selectedIcon: Icon(destination.selectedIcon),
                    label: destination.label,
                  ),
              ],
            ),
          );
        }

        return Scaffold(
          body: SafeArea(
            child: Row(
              children: [
                NavigationRail(
                  minWidth: 88,
                  selectedIndex: _selectedIndex,
                  onDestinationSelected: _selectDestination,
                  labelType: NavigationRailLabelType.all,
                  leading: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 16, 12, 32),
                    child: Column(
                      children: [
                        SvgPicture.asset(
                          'assets/brand/high-tech-logo.svg',
                          width: 54,
                          height: 54,
                          semanticsLabel: 'High-Tech Entertainment logo',
                        ),
                        const SizedBox(height: 10),
                        const Text(
                          'HTE',
                          style: TextStyle(
                            color: HteColors.gold,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 2,
                          ),
                        ),
                      ],
                    ),
                  ),
                  destinations: [
                    for (final destination in HteDestination.values)
                      NavigationRailDestination(
                        icon: Icon(destination.icon),
                        selectedIcon: Icon(destination.selectedIcon),
                        label: Text(destination.label),
                      ),
                ],
                const VerticalDivider(width: 1, thickness: 1),
                Expanded(
                  child: Align(
                    alignment: Alignment.topCenter,
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 1240),
                      child: page,
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _HomePage extends StatelessWidget {
  const _HomePage({
    required this.catalogueFuture,
    required this.backendConfigured,
    required this.onDiscover,
    required this.onVideos,
    required this.onRetry,
  });

  final Future<HteCatalogue> catalogueFuture;
  final bool backendConfigured;
  final VoidCallback onDiscover;
  final VoidCallback onVideos;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 28, 24, 48),
      children: [
        Row(
          children: [
            SvgPicture.asset(
              'assets/brand/high-tech-logo.svg',
              width: 54,
              height: 54,
              semanticsLabel: 'High-Tech Entertainment logo',
            ),
            const SizedBox(width: 14),
            const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'HTE UNIVERSE',
                  style: TextStyle(
                    color: HteColors.primaryText,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 2.2,
                  ),
                ),
                SizedBox(height: 3),
                Text(
                  'HIGH-TECH ENTERTAINMENT',
                  style: TextStyle(
                    color: HteColors.secondaryText,
                    fontSize: 10,
                    letterSpacing: 1.5,
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 48),
        Text(
          'Music. Beats.\nProduction. Beyond.',
          style: Theme.of(context).textTheme.headlineLarge,
        ),
        const SizedBox(height: 14),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 580),
          child: Text(
            'Discover official releases, artists and videos from High-Tech Entertainment.',
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                  color: HteColors.secondaryText,
                ),
          ),
        ),
        const SizedBox(height: 26),
        Wrap(
          spacing: 12,
          runSpacing: 10,
          children: [
            FilledButton.icon(
              onPressed: onDiscover,
              icon: const Icon(Icons.explore_outlined),
              label: const Text('Explore music'),
            ),
            OutlinedButton.icon(
              onPressed: onVideos,
              icon: const Icon(Icons.play_circle_outline),
              label: const Text('Watch videos'),
            ),
          ],
        ),
        const SizedBox(height: 48),
        const Divider(),
        const SizedBox(height: 22),
        Text('The HTE catalogue',
            style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 12),
        if (!backendConfigured)
          const _StatusPanel(
            icon: Icons.cloud_off_outlined,
            title: 'Catalogue connection unavailable',
            detail: 'The app shell is available, but Supabase configuration could not be loaded.',
          )
        else
          FutureBuilder<HteCatalogue>(
            future: catalogueFuture,
            builder: (context, snapshot) {
              if (snapshot.hasError) {
                return _StatusPanel(
                  icon: Icons.cloud_off_outlined,
                  title: 'Could not load the catalogue',
                  detail: 'Check your connection and retry. Published data remains protected by Supabase policies.',
                  action: onRetry,
                );
              }
              if (!snapshot.hasData) {
                return const _StatusPanel(
                  icon: Icons.sync,
                  title: 'Loading published catalogue',
                  detail: 'Connecting to the existing High-Tech Entertainment catalogue.',
                  loading: true,
                );
              }
              final catalogue = snapshot.data!;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      _CountPill(label: 'Tracks', count: catalogue.tracks.length),
                      _CountPill(label: 'Videos', count: catalogue.videos.length),
                      _CountPill(label: 'Releases', count: catalogue.releases.length),
                      _CountPill(label: 'Artists', count: catalogue.artists.length),
                    ],
                  ),
                  const SizedBox(height: 18),
                  Text(
                    'Published music, releases and videos are read from Supabase. Artist visibility follows the existing row-level security policy.',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                  if (catalogue.releases.isNotEmpty) ...[
                    const SizedBox(height: 24),
                    Text('Latest releases', style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 8),
                    for (final release in catalogue.releases.take(3))
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: _TrackArtwork(url: release.coverUrl),
                        title: Text(release.title),
                        subtitle: Text(release.releaseDate ?? 'High-Tech Entertainment'),
                      ),
                  ],
                  if (catalogue.artists.isNotEmpty) ...[
                    const SizedBox(height: 20),
                    Text('HTE artists', style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final artist in catalogue.artists.take(8))
                          Chip(label: Text(artist.name)),
                      ],
                    ),
                  ],
                ],
              );
            },
          ),
        const SizedBox(height: 30),
        const _EditorialRow(
          icon: Icons.graphic_eq_rounded,
          title: 'Music and releases',
          detail: 'Published tracks and release information',
        ),
        const _EditorialRow(
          icon: Icons.play_circle_outline_rounded,
          title: 'Official videos',
          detail: 'Published video catalogue and poster artwork',
        ),
        const _EditorialRow(
          icon: Icons.headphones_rounded,
          title: 'Your listening space',
          detail: 'Member account and library access',
        ),
        const SizedBox(height: 20),
        Text(
          'Flutter migration milestone: live catalogue and member authentication are connected. Playback, offline downloads and Founder Studio remain in their dedicated migration/test stages.',
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: HteColors.secondaryText,
              ),
        ),
      ],
    );
  }
}

class _CataloguePage extends StatefulWidget {
  const _CataloguePage({
    required this.title,
    required this.isVideo,
    required this.catalogueFuture,
    required this.backendConfigured,
    required this.onRetry,
  });

  final String title;
  final bool isVideo;
  final Future<HteCatalogue> catalogueFuture;
  final bool backendConfigured;
  final VoidCallback onRetry;

  @override
  State<_CataloguePage> createState() => _CataloguePageState();
}

class _CataloguePageState extends State<_CataloguePage> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(22, 24, 22, 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(widget.title, style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 14),
              TextField(
                onChanged: (value) => setState(() => _query = value.trim().toLowerCase()),
                decoration: InputDecoration(
                  hintText: widget.isVideo ? 'Search official videos' : 'Search tracks, artists or genres',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _query.isEmpty
                      ? null
                      : IconButton(
                          tooltip: 'Clear search',
                          onPressed: () => setState(() => _query = ''),
                          icon: const Icon(Icons.close),
                        ),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: !widget.backendConfigured
              ? const _StatusPanel(
                  icon: Icons.cloud_off_outlined,
                  title: 'Catalogue unavailable',
                  detail: 'Supabase configuration is missing or failed to initialize.',
                )
              : FutureBuilder<HteCatalogue>(
                  future: widget.catalogueFuture,
                  builder: (context, snapshot) {
                    if (snapshot.hasError) {
                      return _StatusPanel(
                        icon: Icons.cloud_off_outlined,
                        title: 'Could not load catalogue',
                        detail: 'Check your internet connection and retry.',
                        action: widget.onRetry,
                      );
                    }
                    if (!snapshot.hasData) {
                      return const _StatusPanel(
                        icon: Icons.sync,
                        title: 'Loading catalogue',
                        detail: 'Fetching published HTE content.',
                        loading: true,
                      );
                    }
                    final catalogue = snapshot.data!;
                    if (widget.isVideo) {
                      final videos = catalogue.videos.where((video) {
                        final text = '${video.title} ${video.artist ?? ''} ${video.description ?? ''}'.toLowerCase();
                        return text.contains(_query);
                      }).toList(growable: false);
                      if (videos.isEmpty) {
                        return _EmptyPanel(
                          title: _query.isEmpty ? 'No published videos yet' : 'No videos match this search',
                          detail: _query.isEmpty ? 'Official video entries will appear here when published.' : 'Try another title or artist.',
                        );
                      }
                      return ListView.separated(
                        padding: const EdgeInsets.fromLTRB(22, 4, 22, 28),
                        itemCount: videos.length,
                        separatorBuilder: (_, __) => const Divider(height: 20),
                        itemBuilder: (context, index) {
                          final video = videos[index];
                          return ListTile(
                            contentPadding: EdgeInsets.zero,
                            leading: _HtePoster(
                              thumbnailUrl: video.thumbnailUrl,
                              videoUrl: video.videoUrl,
                            ),
                            title: Text(video.title, maxLines: 2, overflow: TextOverflow.ellipsis),
                            subtitle: Text(video.artist ?? 'High-Tech Entertainment'),
                            trailing: const Icon(Icons.ondemand_video_outlined, color: HteColors.gold),
                          );
                        },
                      );
                    }

                    final tracks = catalogue.tracks.where((track) {
                      final text = '${track.title} ${track.artist} ${track.genre ?? ''}'.toLowerCase();
                      return text.contains(_query);
                    }).toList(growable: false);
                    if (tracks.isEmpty) {
                      return _EmptyPanel(
                        title: _query.isEmpty ? 'No published tracks yet' : 'No tracks match this search',
                        detail: _query.isEmpty ? 'Published HTE tracks will appear here.' : 'Try another song, artist or genre.',
                      );
                    }
                    return ListView.separated(
                      padding: const EdgeInsets.fromLTRB(22, 4, 22, 28),
                      itemCount: tracks.length,
                      separatorBuilder: (_, __) => const Divider(height: 20),
                      itemBuilder: (context, index) {
                        final track = tracks[index];
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: _TrackArtwork(url: track.coverUrl),
                          title: Text(track.title, maxLines: 2, overflow: TextOverflow.ellipsis),
                          subtitle: Text(track.genre == null || track.genre!.isEmpty
                              ? track.artist
                              : '${track.artist} · ${track.genre}'),
                          trailing: const Icon(Icons.graphic_eq_rounded, color: HteColors.gold),
                        );
                      },
                    );
                  },
                ),
        ),
      ],
    );
  }
}

class _LibraryPage extends StatelessWidget {
  const _LibraryPage({required this.backendConfigured, required this.onAccount});

  final bool backendConfigured;
  final VoidCallback onAccount;

  @override
  Widget build(BuildContext context) {
    final user = backendConfigured ? HteAuthRepository.currentUser : null;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(28),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.library_music_outlined, size: 46, color: HteColors.gold),
              const SizedBox(height: 18),
              Text('Your library', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 12),
              Text(
                user == null
                    ? 'Sign in to connect your HTE account. Favourites, saved playlists and offline downloads will be added in the media migration stage.'
                    : 'Signed in as ${user.email ?? 'HTE member'}. Your personal library and offline download integration are part of the next media migration stage.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 22),
              FilledButton.icon(
                onPressed: onAccount,
                icon: const Icon(Icons.person_outline),
                label: Text(user == null ? 'Open account' : 'Account settings'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AccountPage extends StatefulWidget {
  const _AccountPage({required this.backendConfigured});

  final bool backendConfigured;

  @override
  State<_AccountPage> createState() => _AccountPageState();
}

class _AccountPageState extends State<_AccountPage> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;
  String? _message;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action, {String? successMessage}) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
      _message = null;
    });
    try {
      await action();
      if (mounted && successMessage != null) setState(() => _message = successMessage);
    } catch (error) {
      if (mounted) setState(() => _error = _safeAuthError(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _safeAuthError(Object error) {
    if (error is AuthException) return error.message;
    return 'The account request failed. Check your connection and try again.';
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.backendConfigured) {
      return const _StatusPanel(
        icon: Icons.cloud_off_outlined,
        title: 'Account service unavailable',
        detail: 'Supabase could not be initialized. Retry the app after checking configuration and internet access.',
      );
    }

    return StreamBuilder<AuthState>(
      stream: HteAuthRepository.authChanges,
      builder: (context, snapshot) {
        final user = snapshot.data?.session?.user ?? HteAuthRepository.currentUser;
        return Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(26),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Icon(Icons.account_circle_outlined, size: 48, color: HteColors.gold),
                  const SizedBox(height: 16),
                  Text(
                    user == null ? 'HTE member account' : 'Your HTE account',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 10),
                  if (user != null) ...[
                    Text(user.email ?? 'Signed-in HTE member', textAlign: TextAlign.center),
                    const SizedBox(height: 20),
                    const _StatusPanel(
                      icon: Icons.verified_user_outlined,
                      title: 'Signed in',
                      detail: 'Founder Studio access is still enforced by Supabase roles and row-level security.',
                    ),
                    const SizedBox(height: 16),
                    OutlinedButton.icon(
                      onPressed: _busy ? null : () => _run(HteAuthRepository.signOut),
                      icon: const Icon(Icons.logout),
                      label: const Text('Sign out'),
                    ),
                  ] else ...[
                    TextField(
                      controller: _email,
                      keyboardType: TextInputType.emailAddress,
                      autofillHints: const [AutofillHints.email],
                      decoration: const InputDecoration(labelText: 'Email address'),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _password,
                      obscureText: true,
                      autofillHints: const [AutofillHints.password],
                      decoration: const InputDecoration(labelText: 'Password'),
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: _busy
                          ? null
                          : () => _run(
                                () => HteAuthRepository.signInWithPassword(
                                  email: _email.text,
                                  password: _password.text,
                                ),
                              ),
                      child: Text(_busy ? 'Please wait…' : 'Sign in'),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton(
                      onPressed: _busy
                          ? null
                          : () => _run(
                                () => HteAuthRepository.signUp(
                                  email: _email.text,
                                  password: _password.text,
                                ),
                                successMessage: 'Account request submitted. If email confirmation is enabled, check your inbox.',
                              ),
                      child: const Text('Create account'),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: _busy ? null : () => _run(HteAuthRepository.signInWithGoogle),
                      icon: const Icon(Icons.login),
                      label: const Text('Continue with Google'),
                    ),
                  ],
                  if (_busy) ...[
                    const SizedBox(height: 16),
                    const LinearProgressIndicator(),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: 14),
                    Text(_error!, style: const TextStyle(color: HteColors.danger), textAlign: TextAlign.center),
                  ],
                  if (_message != null) ...[
                    const SizedBox(height: 14),
                    Text(_message!, style: const TextStyle(color: HteColors.gold), textAlign: TextAlign.center),
                  ],
                  const SizedBox(height: 18),
                  Text(
                    'Use your existing High-Tech Entertainment account. Authentication and permissions remain managed by Supabase.',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: HteColors.secondaryText),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _HtePoster extends StatefulWidget {
  const _HtePoster({required this.thumbnailUrl, required this.videoUrl});

  final String? thumbnailUrl;
  final String videoUrl;

  @override
  State<_HtePoster> createState() => _HtePosterState();
}

class _HtePosterState extends State<_HtePoster> {
  int _index = 0;
  bool _advancing = false;

  List<String> get _candidates {
    final candidates = <String>[];
    final seenQualities = <String>{};
    final supplied = HteMediaUrlPolicy.parseSecureUri(widget.thumbnailUrl);
    if (supplied != null &&
        RegExp(r'\.(jpg|jpeg|png|webp)$', caseSensitive: false).hasMatch(supplied.path)) {
      candidates.add(supplied.toString());
      if (supplied.pathSegments.isNotEmpty) {
        seenQualities.add(supplied.pathSegments.last);
      }
    }
    for (final uri in HteMediaUrlPolicy.youtubeThumbnailCandidates(widget.videoUrl)) {
      final quality = uri.pathSegments.isEmpty ? uri.toString() : uri.pathSegments.last;
      if (seenQualities.add(quality)) candidates.add(uri.toString());
    }
    return candidates.toList(growable: false);
  }

  @override
  Widget build(BuildContext context) {
    final candidates = _candidates;
    if (_index >= candidates.length) {
      return const SizedBox(
        width: 72,
        height: 52,
        child: Icon(Icons.ondemand_video_outlined, color: HteColors.gold),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: SizedBox(
        width: 72,
        height: 52,
        child: Image.network(
          candidates[_index],
          fit: BoxFit.cover,
          errorBuilder: (context, error, stackTrace) {
            if (!_advancing && _index < candidates.length - 1) {
              _advancing = true;
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (mounted) setState(() => _index += 1);
                _advancing = false;
              });
            } else if (_index == candidates.length - 1 && !_advancing) {
              _advancing = true;
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (mounted) setState(() => _index = candidates.length);
                _advancing = false;
              });
            }
            return const Icon(Icons.ondemand_video_outlined, color: HteColors.gold);
          },
        ),
      ),
    );
  }
}

class _TrackArtwork extends StatelessWidget {
  const _TrackArtwork({required this.url});

  final String? url;

  @override
  Widget build(BuildContext context) {
    final uri = HteMediaUrlPolicy.parseSecureUri(url);
    if (uri == null) {
      return const CircleAvatar(
        backgroundColor: HteColors.surfaceRaised,
        child: Icon(Icons.music_note, color: HteColors.gold),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: SizedBox(
        width: 52,
        height: 52,
        child: Image.network(
          uri.toString(),
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => const Icon(Icons.music_note, color: HteColors.gold),
        ),
      ),
    );
  }
}

class _StatusPanel extends StatelessWidget {
  const _StatusPanel({
    required this.icon,
    required this.title,
    required this.detail,
    this.action,
    this.loading = false,
  });

  final IconData icon;
  final String title;
  final String detail;
  final VoidCallback? action;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: HteColors.surface,
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, color: HteColors.gold),
                const SizedBox(width: 10),
                Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
              ],
            ),
            const SizedBox(height: 8),
            Text(detail, style: Theme.of(context).textTheme.bodyMedium),
            if (loading) ...[
              const SizedBox(height: 14),
              const LinearProgressIndicator(),
            ],
            if (action != null) ...[
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: action,
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _EmptyPanel extends StatelessWidget {
  const _EmptyPanel({required this.title, required this.detail});

  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.search_off, size: 36, color: HteColors.secondaryText),
              const SizedBox(height: 12),
              Text(title, style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
              const SizedBox(height: 8),
              Text(detail, style: Theme.of(context).textTheme.bodyMedium, textAlign: TextAlign.center),
            ],
          ),
        ),
      );
}

class _CountPill extends StatelessWidget {
  const _CountPill({required this.label, required this.count});

  final String label;
  final int count;

  @override
  Widget build(BuildContext context) => Chip(
        label: Text('$label · $count'),
        avatar: const Icon(Icons.circle, size: 8, color: HteColors.gold),
        side: const BorderSide(color: HteColors.divider),
        backgroundColor: HteColors.surfaceRaised,
      );
}

class _EditorialRow extends StatelessWidget {
  const _EditorialRow({
    required this.icon,
    required this.title,
    required this.detail,
  });

  final IconData icon;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 18),
      child: Row(
        children: [
          Icon(icon, color: HteColors.gold, size: 26),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 3),
                Text(detail, style: Theme.of(context).textTheme.bodyMedium),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
