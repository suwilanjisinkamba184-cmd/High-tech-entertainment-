import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;

import 'playback_engine.dart';

/// First native Voice Companion increment: local speech-to-text and local TTS.
/// Android must have an offline recognition language pack and an installed
/// TTS voice whose network_required flag is false.
class VoiceCompanionPage extends StatefulWidget {
  const VoiceCompanionPage({super.key});

  @override
  State<VoiceCompanionPage> createState() => _VoiceCompanionPageState();
}

class _VoiceCompanionPageState extends State<VoiceCompanionPage> {
  final stt.SpeechToText _speech = stt.SpeechToText();
  final FlutterTts _tts = FlutterTts();
  List<stt.LocaleName> _locales = [];
  List<Map<String, dynamic>> _offlineVoices = [];
  String? _selectedLocale;
  String? _selectedVoiceKey;
  String _transcript = '';
  String _status = 'Preparing offline voice services…';
  bool _ready = false;
  bool _listening = false;
  bool _speaking = false;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  String _voiceKey(Map voice) =>
      '${voice['name'] ?? ''}|${voice['locale'] ?? ''}';

  Future<void> _initialize() async {
    try {
      await _tts.awaitSpeakCompletion(true);
      await _tts.setSpeechRate(0.48);
      await _tts.setVolume(1.0);
      final voices = await _tts.getVoices;
      final offline = <Map<String, dynamic>>[];
      for (final raw in voices) {
        if (raw is! Map) continue;
        final voice = Map<String, dynamic>.from(raw);
        final networkRequired = voice['network_required'];
        if (networkRequired == false || networkRequired == 0 ||
            networkRequired.toString().toLowerCase() == 'false') {
          offline.add(voice);
        }
      }
      final initialized = await _speech.initialize(
        onStatus: (status) {
          if (!mounted) return;
          setState(() => _listening = _speech.isListening);
        },
        onError: (error) {
          if (!mounted) return;
          setState(() {
            _listening = false;
            _status = 'Offline recognition error: ${error.errorMsg}';
          });
        },
      );
      final locales = initialized ? await _speech.locales() : <stt.LocaleName>[];
      final prefsLocale = locales.firstWhere(
        (locale) => locale.localeId.toLowerCase().startsWith('en'),
        orElse: () => locales.isNotEmpty
            ? locales.first
            : stt.LocaleName('en_US', 'English (United States)'),
      );
      if (!mounted) return;
      setState(() {
        _offlineVoices = offline;
        _locales = locales;
        _selectedLocale = prefsLocale.localeId;
        _ready = initialized;
        _status = initialized
            ? (offline.isEmpty
                ? 'Recognition ready. No confirmed offline speech voice is installed.'
                : 'Offline voice services ready.')
            : 'Speech recognition is unavailable. Check microphone permission and Android speech services.';
      });
      final savedVoice = _selectedVoiceKey;
      if (offline.isNotEmpty) {
        final voice = offline.firstWhere(
          (item) => _voiceKey(item) == savedVoice,
          orElse: () => offline.first,
        );
        await _applyVoice(voice, persist: false);
      }
    } catch (error) {
      if (!mounted) return;
      setState(() => _status = 'Voice setup failed: $error');
    }
  }

  Future<void> _applyVoice(Map<String, dynamic> voice, {bool persist = true}) async {
    final name = voice['name']?.toString() ?? '';
    final locale = voice['locale']?.toString() ?? '';
    if (name.isEmpty || locale.isEmpty) return;
    await _tts.setVoice({'name': name, 'locale': locale});
    if (!mounted) return;
    setState(() => _selectedVoiceKey = _voiceKey(voice));
  }

  Future<void> _speak(String text) async {
    try {
      if (_offlineVoices.isNotEmpty && _selectedVoiceKey != null) {
        final voice = _offlineVoices.firstWhere(
          (item) => _voiceKey(item) == _selectedVoiceKey,
          orElse: () => _offlineVoices.first,
        );
        await _tts.setVoice({
          'name': voice['name'],
          'locale': voice['locale'],
        });
      }
      if (mounted) setState(() => _speaking = true);
      await _tts.speak(text);
    } catch (error) {
      if (mounted) setState(() => _status = 'Speech output failed: $error');
    } finally {
      if (mounted) setState(() => _speaking = false);
    }
  }

  Future<void> _listen() async {
    if (!_ready || _listening) return;
    try {
      await _tts.stop();
      setState(() {
        _transcript = '';
        _status = 'Listening on this device…';
        _listening = true;
      });
      await _speech.listen(
        onResult: (result) {
          if (!mounted) return;
          setState(() => _transcript = result.recognizedWords);
          if (result.finalResult) {
            unawaited(_handleCommand(result.recognizedWords));
          }
        },
        listenOptions: stt.SpeechListenOptions(
          onDevice: true,
          partialResults: false,
          cancelOnError: true,
          listenFor: const Duration(seconds: 8),
          pauseFor: const Duration(seconds: 2),
          localeId: _selectedLocale,
        ),
      );
    } catch (error) {
      if (mounted) {
        setState(() {
          _listening = false;
          _status = 'Offline listening could not start: $error';
        });
      }
    }
  }

  Future<void> _handleCommand(String raw) async {
    final command = raw.toLowerCase().trim();
    if (command.isEmpty) return;
    await _speech.stop();
    if (mounted) setState(() => _listening = false);

    final playback = HtePlayback.instance;
    String response;
    try {
      if (RegExp(r'\b(pause|hold)\b').hasMatch(command)) {
        await playback.player.pause();
        response = 'Music paused.';
      } else if (RegExp(r'\b(resume|continue)\b').hasMatch(command)) {
        await playback.player.play();
        response = 'Resuming music.';
      } else if (RegExp(r'\b(next|skip)\b').hasMatch(command)) {
        await playback.next();
        response = 'Skipping to the next track.';
      } else if (RegExp(r'\b(previous|back)\b').hasMatch(command)) {
        await playback.previous();
        response = 'Going back to the previous track.';
      } else if (RegExp(r'\b(stop music|stop playback)\b').hasMatch(command)) {
        await playback.player.stop();
        response = 'Music stopped.';
      } else if (command == 'stop speaking' || command == 'be quiet') {
        await _tts.stop();
        response = 'Okay.';
      } else {
        response = 'I heard $raw. That command is not available offline yet.';
      }
      if (mounted) setState(() => _status = response);
      await _speak(response);
    } catch (error) {
      response = 'I could not complete that command. $error';
      if (mounted) setState(() => _status = response);
      await _speak(response);
    }
  }

  @override
  void dispose() {
    unawaited(_speech.cancel());
    unawaited(_tts.stop());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final selectedVoice = _offlineVoices.where(
      (voice) => _voiceKey(voice) == _selectedVoiceKey,
    ).firstOrNull;
    return Scaffold(
      appBar: AppBar(title: const Text('High-Tech Voice Companion')),
      body: ListView(
        padding: const EdgeInsets.all(18),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(children: [
                    Icon(Icons.offline_bolt, color: Color(0xFFD4AF37)),
                    SizedBox(width: 8),
                    Text('ON-DEVICE MODE', style: TextStyle(fontWeight: FontWeight.w900)),
                  ]),
                  const SizedBox(height: 8),
                  Text(_status),
                  const SizedBox(height: 14),
                  Center(
                    child: FilledButton.icon(
                      onPressed: !_ready || _listening ? null : _listen,
                      icon: Icon(_listening ? Icons.mic : Icons.mic_none),
                      label: Text(_listening ? 'Listening…' : 'Press to speak'),
                    ),
                  ),
                  if (_transcript.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Text('You said: $_transcript'),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          const Text('Offline speaking voice', style: TextStyle(fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          if (_offlineVoices.isEmpty)
            const Card(child: Padding(
              padding: EdgeInsets.all(14),
              child: Text('No offline TTS voices were found. Install an offline voice in Android Settings → Text-to-speech output, then reopen this page.'),
            ))
          else
            Card(
              child: Column(children: [
                ListTile(
                  leading: const Icon(Icons.record_voice_over),
                  title: Text(selectedVoice?['name']?.toString() ?? 'System voice'),
                  subtitle: Text(selectedVoice?['locale']?.toString() ?? 'Offline voice'),
                  trailing: const Icon(Icons.verified, color: Colors.green),
                ),
                const Divider(height: 1),
                for (final voice in _offlineVoices)
                  RadioListTile<String>(
                    value: _voiceKey(voice),
                    groupValue: _selectedVoiceKey,
                    title: Text(voice['name']?.toString() ?? 'Voice'),
                    subtitle: Text(voice['locale']?.toString() ?? ''),
                    onChanged: (value) {
                      if (value != null) {
                        unawaited(_applyVoice(voice));
                      }
                    },
                  ),
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: OutlinedButton.icon(
                    onPressed: _speaking ? null : () => _speak('Hello. This is your High-Tech Entertainment offline voice companion.'),
                    icon: const Icon(Icons.play_arrow),
                    label: const Text('Test selected voice'),
                  ),
                ),
              ]),
            ),
          const SizedBox(height: 12),
          const Text('Offline commands', style: TextStyle(fontWeight: FontWeight.w800)),
          const Card(child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('“Pause music”\n“Resume music”\n“Next track”\n“Previous track”\n“Stop music”'),
          )),
          const SizedBox(height: 8),
          Text(
            'Offline recognition depends on an installed Android offline language pack. Voice choices shown here are filtered to voices that report no network requirement. Online news, recommendations and account actions will be added through the secure platform service.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}
