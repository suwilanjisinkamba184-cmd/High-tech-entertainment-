import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const _studioGold = Color(0xFFD7A83E);
const _studioBlack = Color(0xFF080808);
const _studioSurface = Color(0xFF171717);
const _studioChannel = MethodChannel('com.hightechentertainment.app/data');

class _StudioOffering {
  const _StudioOffering({
    required this.title,
    required this.description,
    required this.icon,
    required this.tagline,
  });

  final String title;
  final String description;
  final IconData icon;
  final String tagline;
}

const _studioOfferings = <_StudioOffering>[
  _StudioOffering(
    title: 'Music production',
    description: 'Turn your idea into a finished, release-ready record.',
    icon: Icons.graphic_eq_rounded,
    tagline: 'FROM IDEA TO RECORD',
  ),
  _StudioOffering(
    title: 'Beat licensing',
    description: 'Find the right sound and discuss a licence for your project.',
    icon: Icons.album_rounded,
    tagline: 'FIND YOUR SOUND',
  ),
  _StudioOffering(
    title: 'Mixing & mastering',
    description: 'Bring clarity, balance and impact to your final mix.',
    icon: Icons.tune_rounded,
    tagline: 'MAKE EVERY DETAIL COUNT',
  ),
  _StudioOffering(
    title: 'Music promotion',
    description: 'Plan a focused release campaign for your music.',
    icon: Icons.campaign_rounded,
    tagline: 'REACH MORE LISTENERS',
  ),
  _StudioOffering(
    title: 'Music video production',
    description: 'Build a visual direction for your next music release.',
    icon: Icons.movie_creation_outlined,
    tagline: 'MAKE IT VISUAL',
  ),
  _StudioOffering(
    title: 'Artist development',
    description: 'Shape your sound, identity and next career steps.',
    icon: Icons.mic_external_on_rounded,
    tagline: 'BUILD YOUR ARTISTRY',
  ),
];

class StudioServicesScreen extends StatelessWidget {
  const StudioServicesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: _studioGold.withValues(alpha: 0.42)),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF302515), Color(0xFF17130D), Color(0xFF101010)],
            ),
            boxShadow: [
              BoxShadow(
                color: _studioGold.withValues(alpha: 0.08),
                blurRadius: 28,
                spreadRadius: 1,
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.auto_awesome_rounded, color: _studioGold, size: 17),
                  const SizedBox(width: 8),
                  Text(
                    'HIGH-TECH STUDIO',
                    style: TextStyle(
                      color: _studioGold.withValues(alpha: 0.95),
                      fontSize: 10,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 2,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              const Text(
                'Make your next release happen.',
                style: TextStyle(
                  fontSize: 27,
                  height: 1.12,
                  fontWeight: FontWeight.w900,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                'From beats and production to mixing, mastering and promotion — bring your next music project to one creative studio.',
                style: TextStyle(color: Colors.white70, height: 1.5, fontSize: 13),
              ),
              const SizedBox(height: 20),
              const Row(
                children: [
                  _StudioProof(icon: Icons.headphones_rounded, label: 'MUSIC'),
                  SizedBox(width: 16),
                  _StudioProof(icon: Icons.graphic_eq_rounded, label: 'PRODUCTION'),
                  SizedBox(width: 16),
                  _StudioProof(icon: Icons.public_rounded, label: 'BEYOND'),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 26),
        const Text(
          'STUDIO SERVICES',
          style: TextStyle(
            color: _studioGold,
            fontSize: 11,
            fontWeight: FontWeight.w800,
            letterSpacing: 1.8,
          ),
        ),
        const SizedBox(height: 6),
        const Text(
          'Choose what you want to create.',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 14),
        LayoutBuilder(
          builder: (context, constraints) {
            final columns = constraints.maxWidth >= 560 ? 3 : 2;
            return GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _studioOfferings.length,
              gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: columns,
                crossAxisSpacing: 10,
                mainAxisSpacing: 10,
                childAspectRatio: columns == 3 ? 0.91 : 0.82,
              ),
              itemBuilder: (context, index) {
                final offering = _studioOfferings[index];
                return _OfferingCard(
                  offering: offering,
                  onTap: () => showModalBottomSheet<void>(
                    context: context,
                    isScrollControlled: true,
                    useSafeArea: true,
                    backgroundColor: _studioSurface,
                    builder: (_) => _QuoteRequestSheet(offering: offering),
                  ),
                );
              },
            );
          },
        ),
        const SizedBox(height: 22),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: _studioSurface,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
          ),
          child: const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'A CLEAR, PERSONAL PROCESS',
                style: TextStyle(
                  color: _studioGold,
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.5,
                ),
              ),
              SizedBox(height: 14),
              _StudioStep(number: '01', title: 'Choose a service', detail: 'Tell us what you want to make.'),
              SizedBox(height: 12),
              _StudioStep(number: '02', title: 'Share your brief', detail: 'Add your goals, references and preferred timing.'),
              SizedBox(height: 12),
              _StudioStep(number: '03', title: 'Receive a response', detail: 'The team reviews your request and follows up with next steps and a quote.'),
            ],
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'No prices are assumed in the app. Every request is reviewed before a quote is confirmed.',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.white54, fontSize: 11, height: 1.4),
        ),
      ],
    );
  }
}

class _StudioProof extends StatelessWidget {
  const _StudioProof({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, color: _studioGold, size: 13),
        const SizedBox(width: 5),
        Text(
          label,
          style: const TextStyle(
            color: Colors.white70,
            fontSize: 8,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.7,
          ),
        ),
      ],
    );
  }
}

class _OfferingCard extends StatelessWidget {
  const _OfferingCard({required this.offering, required this.onTap});

  final _StudioOffering offering;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: _studioSurface,
      borderRadius: BorderRadius.circular(17),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(17),
        child: Container(
          padding: const EdgeInsets.all(13),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(17),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: _studioGold.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(offering.icon, color: _studioGold, size: 20),
              ),
              const SizedBox(height: 11),
              Text(
                offering.tagline,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: _studioGold,
                  fontSize: 8,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.6,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                offering.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w800, height: 1.15),
              ),
              const SizedBox(height: 6),
              Expanded(
                child: Text(
                  offering.description,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Colors.white60, fontSize: 10, height: 1.35),
                ),
              ),
              const SizedBox(height: 4),
              const Row(
                children: [
                  Text(
                    'REQUEST A QUOTE',
                    style: TextStyle(
                      color: _studioGold,
                      fontSize: 9,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 0.4,
                    ),
                  ),
                  SizedBox(width: 4),
                  Icon(Icons.arrow_forward_rounded, color: _studioGold, size: 13),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StudioStep extends StatelessWidget {
  const _StudioStep({required this.number, required this.title, required this.detail});

  final String number;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(number, style: const TextStyle(color: _studioGold, fontWeight: FontWeight.w900, fontSize: 12)),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
              const SizedBox(height: 3),
              Text(detail, style: const TextStyle(color: Colors.white60, fontSize: 11, height: 1.4)),
            ],
          ),
        ),
      ],
    );
  }
}

class _QuoteRequestSheet extends StatefulWidget {
  const _QuoteRequestSheet({required this.offering});

  final _StudioOffering offering;

  @override
  State<_QuoteRequestSheet> createState() => _QuoteRequestSheetState();
}

class _QuoteRequestSheetState extends State<_QuoteRequestSheet> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _brief = TextEditingController();
  DateTime? _requestedDate;
  bool _busy = false;
  bool _submitted = false;
  String? _message;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _brief.dispose();
    super.dispose();
  }

  Future<void> _chooseDate() async {
    final today = DateTime.now();
    final selected = await showDatePicker(
      context: context,
      initialDate: _requestedDate ?? today,
      firstDate: today,
      lastDate: DateTime(today.year + 2),
      helpText: 'PREFERRED PROJECT DATE',
    );
    if (selected != null && mounted) setState(() => _requestedDate = selected);
  }

  Future<void> _submit() async {
    if (_busy || !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final result = await _studioChannel.invokeMapMethod<String, dynamic>(
        'submitBooking',
        {
          'name': _name.text.trim(),
          'email': _email.text.trim(),
          'bookingType': widget.offering.title,
          'requestedDate': _requestedDate == null
              ? null
              : _requestedDate!.toIso8601String().substring(0, 10),
          'message': _brief.text.trim(),
        },
      );
      if (!mounted) return;
      if (result?['success'] == true) {
        setState(() {
          _submitted = true;
          _message = 'Your request has been received. The High-Tech team will review your brief and follow up with a quote.';
        });
      } else {
        setState(() => _message = result?['message']?.toString() ?? 'Your request could not be submitted. Please try again.');
      }
    } on PlatformException catch (error) {
      if (mounted) {
        setState(() => _message = error.message ?? 'Your request could not be submitted. Please try again.');
      }
    } on MissingPluginException {
      if (mounted) setState(() => _message = 'Booking services are unavailable in this app build.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 16, 20, 20 + MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 38,
                  height: 4,
                  decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(4)),
                ),
              ),
              const SizedBox(height: 18),
              Text(
                _submitted ? 'REQUEST RECEIVED' : 'START YOUR PROJECT',
                style: const TextStyle(color: _studioGold, fontSize: 10, fontWeight: FontWeight.w900, letterSpacing: 1.7),
              ),
              const SizedBox(height: 7),
              Text(
                widget.offering.title,
                style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 8),
              if (_submitted) ...[
                const Icon(Icons.check_circle_outline_rounded, color: _studioGold, size: 48),
                const SizedBox(height: 12),
                Text(_message ?? 'Your request has been received.', style: const TextStyle(color: Colors.white70, height: 1.5)),
                const SizedBox(height: 18),
                FilledButton(
                  onPressed: () => Navigator.pop(context),
                  style: FilledButton.styleFrom(backgroundColor: _studioGold, foregroundColor: _studioBlack),
                  child: const Text('DONE'),
                ),
              ] else ...[
                const Text(
                  'Tell us a little about your project. We will review your request and respond with the next steps.',
                  style: TextStyle(color: Colors.white60, fontSize: 12, height: 1.45),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _name,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Your name', filled: true),
                  validator: (value) => (value ?? '').trim().isEmpty ? 'Enter your name.' : null,
                ),
                const SizedBox(height: 10),
                TextFormField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  decoration: const InputDecoration(labelText: 'Email address', filled: true),
                  validator: (value) {
                    final email = (value ?? '').trim();
                    return RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(email)
                        ? null
                        : 'Enter a valid email address.';
                  },
                ),
                const SizedBox(height: 10),
                TextFormField(
                  controller: _brief,
                  minLines: 3,
                  maxLines: 5,
                  maxLength: 3000,
                  decoration: const InputDecoration(
                    labelText: 'Project brief',
                    hintText: 'What are you creating? Include your goals and any useful details.',
                    filled: true,
                    alignLabelWithHint: true,
                  ),
                  validator: (value) => (value ?? '').trim().isEmpty ? 'Tell us about your project.' : null,
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: _busy ? null : _chooseDate,
                  icon: const Icon(Icons.calendar_month_rounded),
                  label: Text(_requestedDate == null
                      ? 'ADD PREFERRED DATE (OPTIONAL)'
                      : 'PREFERRED DATE: ' + _requestedDate!.toIso8601String().substring(0, 10)),
                ),
                if (_message != null) ...[
                  const SizedBox(height: 10),
                  Text(_message!, style: const TextStyle(color: Color(0xFFFFB4AB), fontSize: 12)),
                ],
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: _busy ? null : _submit,
                  icon: _busy
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: _studioBlack))
                      : const Icon(Icons.send_rounded),
                  label: Text(_busy ? 'SENDING REQUEST…' : 'SEND PROJECT REQUEST'),
                  style: FilledButton.styleFrom(
                    backgroundColor: _studioGold,
                    foregroundColor: _studioBlack,
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
