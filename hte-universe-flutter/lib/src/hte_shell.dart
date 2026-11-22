import 'package:flutter_svg/flutter_svg.dart';
import 'package:material_ui/material_ui.dart';

import 'hte_theme.dart';

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

  void _selectDestination(int index) {
    if (index < 0 || index >= HteDestination.values.length) return;
    setState(() => _selectedIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      _HomePage(onDiscover: () => _selectDestination(1)),
      const _DestinationPage(
        title: 'Discover music',
        description:
            'Explore published tracks, artists and releases from High-Tech Entertainment.',
        icon: Icons.explore_outlined,
      ),
      const _DestinationPage(
        title: 'Official videos',
        description:
            'Official video artwork and playback will appear here when the catalogue service is connected.',
        icon: Icons.ondemand_video_outlined,
      ),
      const _DestinationPage(
        title: 'Your library',
        description:
            'Your favourites, playlists, listening history and offline downloads belong here.',
        icon: Icons.library_music_outlined,
      ),
      const _DestinationPage(
        title: 'Your account',
        description:
            'Sign-in, profile, bookings and member settings are part of the account experience.',
        icon: Icons.person_outline_rounded,
      ),
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
                ),
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
  const _HomePage({required this.onDiscover});

  final VoidCallback onDiscover;

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(24, 28, 24, 48),
          sliver: SliverList.list(
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
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton.icon(
                  onPressed: onDiscover,
                  icon: const Icon(Icons.explore_outlined),
                  label: const Text('Explore music'),
                ),
              ),
              const SizedBox(height: 56),
              const Divider(),
              const SizedBox(height: 24),
              Text(
                'The HTE catalogue',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 10),
              Text(
                'Published music and official videos will load from the existing HTE catalogue. Draft and unpublished material stays private.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 32),
              const _EditorialRow(
                icon: Icons.graphic_eq_rounded,
                title: 'Music and releases',
                detail: 'Official tracks, albums and artist profiles',
              ),
              const _EditorialRow(
                icon: Icons.play_circle_outline_rounded,
                title: 'Official videos',
                detail: 'Video artwork, playback and fullscreen viewing',
              ),
              const _EditorialRow(
                icon: Icons.headphones_rounded,
                title: 'Your listening space',
                detail: 'Favourites, playlists and downloads',
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _DestinationPage extends StatelessWidget {
  const _DestinationPage({
    required this.title,
    required this.description,
    required this.icon,
  });

  final String title;
  final String description;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(32),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 42, color: HteColors.gold),
              const SizedBox(height: 20),
              Text(
                title,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 12),
              Text(
                description,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: HteColors.secondaryText,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
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
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: HteColors.gold, size: 24),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 4),
                Text(detail, style: Theme.of(context).textTheme.bodyMedium),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
