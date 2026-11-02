import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

const _founderId = 'b268df9e-eff5-43b9-a38f-869b33add38b';
const _gold = Color(0xFFD9B96E);
final _supabase = Supabase.instance.client;

class AdminStudioPage extends StatefulWidget {
  const AdminStudioPage({super.key});
  @override
  State<AdminStudioPage> createState() => _AdminStudioPageState();
}

class _AdminStudioPageState extends State<AdminStudioPage> {
  int _section = 0;
  bool _busy = false;
  String? _error;
  final _title = TextEditingController();
  final _artist = TextEditingController();
  final _genre = TextEditingController(text: 'Afrobeat');
  final _artistName = TextEditingController();
  final _artistBio = TextEditingController();
  final _releaseTitle = TextEditingController();
  final _spotifyUrl = TextEditingController();
  final _releaseCoverUrl = TextEditingController();
  final _coverUrl = TextEditingController();
  PlatformFile? _audio;
  PlatformFile? _artwork;
  List<Map<String,dynamic>> _tracks = [];
  List<Map<String,dynamic>> _artists = [];
  List<Map<String,dynamic>> _releases = [];
  Map<String,int> _metrics = {};
  static const _sections = ['Overview','Music uploads','Tracks','Artists','Releases','Artwork','Users','Analytics','Activity'];

  @override
  void initState() { super.initState(); _loadAll(); }
  @override
  void dispose() {
    _title.dispose(); _artist.dispose(); _genre.dispose();
    _artistName.dispose(); _artistBio.dispose(); _releaseTitle.dispose(); _spotifyUrl.dispose(); _releaseCoverUrl.dispose(); _coverUrl.dispose();
    super.dispose();
  }

  bool get _isFounder => _supabase.auth.currentUser?.id == _founderId;
  void _toast(String value) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));
  }
  Future<void> _loadAll() async {
    if (!_isFounder) return;
    setState(() { _busy = true; _error = null; });
    try {
      final values = await Future.wait([
        _supabase.from('tracks').select().order('created_at', ascending: false).limit(100),
        _supabase.from('artists').select().order('created_at', ascending: false).limit(100),
        _supabase.from('releases').select().order('created_at', ascending: false).limit(100),
      ]);
      _tracks = List<Map<String,dynamic>>.from(values[0]);
      _artists = List<Map<String,dynamic>>.from(values[1]);
      _releases = List<Map<String,dynamic>>.from(values[2]);
      _metrics = {
        'Tracks': _tracks.length, 'Published': _tracks.where((e)=>e['is_published']==true).length,
        'Artists': _artists.length, 'Releases': _releases.length,
      };
    } catch (e) { _error = e.toString(); }
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _pickArtwork() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.image, withData: true);
    if (result != null && result.files.isNotEmpty) setState(() => _artwork = result.files.first);
  }
  Future<void> _uploadArtwork() async {
    if (_artwork == null) { _toast('Choose an image first.'); return; }
    setState(() => _busy = true);
    try {
      final url = await _uploadBytes(_artwork!, 'releases', 'hte-artwork');
      _coverUrl.text = url;
      setState(() => _artwork = null);
      _toast('Artwork uploaded. Its public URL is ready to attach to a track.');
    } catch(e) { _toast('Artwork upload failed: $e'); }
    finally { if(mounted)setState(()=>_busy=false); }
  }
  Future<void> _pickAudio() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom,
      allowedExtensions: ['mp3','m4a','aac','wav','ogg','flac'], withData: true);
    if (result != null && result.files.isNotEmpty) setState(() => _audio = result.files.first);
  }
  String _extension(PlatformFile file) => (file.extension ?? 'bin').toLowerCase();
  Future<String> _uploadBytes(PlatformFile file, String folder, String bucket) async {
    final Uint8List? bytes = file.bytes;
    if (bytes == null || bytes.isEmpty) throw Exception('Could not read selected file. Please select it again.');
    final path = '$folder/${DateTime.now().millisecondsSinceEpoch}_${file.name.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_')}';
    final ext = _extension(file);
    final mime = <String,String>{
      'mp3':'audio/mpeg','m4a':'audio/x-m4a','aac':'audio/aac',
      'wav':'audio/wav','ogg':'audio/ogg','flac':'audio/flac',
      'jpg':'image/jpeg','jpeg':'image/jpeg','png':'image/png','webp':'image/webp','avif':'image/avif',
    }[ext];
    await _supabase.storage.from(bucket).uploadBinary(path, bytes,
      fileOptions: FileOptions(upsert: false, contentType: mime));
    return _supabase.storage.from(bucket).getPublicUrl(path);
  }
  Future<void> _saveTrack() async {
    if (_title.text.trim().isEmpty || _artist.text.trim().isEmpty || _audio == null) {
      _toast('Enter a title and artist, then choose an audio file.'); return;
    }
    setState(() => _busy = true);
    try {
      final audioUrl = await _uploadBytes(_audio!, 'tracks', 'hte-audio');
      final coverUrl = _coverUrl.text.trim();
      await _supabase.from('tracks').insert({
        'title': _title.text.trim(), 'artist': _artist.text.trim(),
        'genre': _genre.text.trim(), 'audio_url': audioUrl,
        if (coverUrl.isNotEmpty) 'cover_url': coverUrl,
        'uploader': 'High-Tech Entertainment', 'is_published': false,
      });
      _title.clear(); _artist.clear(); _genre.text = 'Afrobeat';
      setState(() { _audio = null; _coverUrl.clear(); _section = 2; });
      await _loadAll(); _toast('Track uploaded and saved as a draft.');
    } catch (e) { _toast('Upload failed: $e'); if (mounted) setState(() => _busy = false); }
  }
  Future<void> _publishTrack(Map<String,dynamic> item, bool published) async {
    try {
      await _supabase.from('tracks').update({'is_published':published}).eq('id', item['id']);
      await _loadAll(); _toast(published ? 'Track published in HT Music.' : 'Track moved to drafts.');
    } catch(e) { _toast('Could not update track: $e'); }
  }
  Future<void> _deleteTrack(Map<String,dynamic> item) async {
    final yes = await showDialog<bool>(context: context, builder: (ctx)=>AlertDialog(
      title: const Text('Delete track?'), content: Text('Delete "${item['title']}" from the catalogue?'),
      actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('Cancel')),
        FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('Delete'))]));
    if (yes != true) return;
    try { await _supabase.from('tracks').delete().eq('id',item['id']); await _loadAll(); }
    catch(e) { _toast('Could not delete track: $e'); }
  }
  Future<void> _saveArtist() async {
    if (_artistName.text.trim().isEmpty) { _toast('Enter an artist name.'); return; }
    try {
      await _supabase.from('artists').insert({'name':_artistName.text.trim(),'bio':_artistBio.text.trim(),'is_published':false});
      _artistName.clear(); _artistBio.clear(); await _loadAll(); _toast('Artist saved as a draft.');
    } catch(e) { _toast('Could not save artist: $e'); }
  }
  Future<void> _publishArtist(Map<String,dynamic> item, bool published) async {
    try { await _supabase.from('artists').update({'is_published':published}).eq('id',item['id']); await _loadAll(); }
    catch(e) { _toast('Could not update artist: $e'); }
  }
  Future<void> _publishRelease(Map<String,dynamic> item, bool published) async {
    try { await _supabase.from('releases').update({'is_published':published}).eq('id',item['id']); await _loadAll(); }
    catch(e) { _toast('Could not update release: $e'); }
  }
  Future<void> _saveRelease() async {
    if (_releaseTitle.text.trim().isEmpty) { _toast('Enter a release title.'); return; }
    try {
      await _supabase.from('releases').insert({'title':_releaseTitle.text.trim(),'spotify_url':_spotifyUrl.text.trim().isEmpty?null:_spotifyUrl.text.trim(),'cover_url':_releaseCoverUrl.text.trim().isEmpty?null:_releaseCoverUrl.text.trim(),'is_published':false});
      _releaseTitle.clear(); _spotifyUrl.clear(); _releaseCoverUrl.clear(); await _loadAll(); _toast('Release saved as a draft.');
    } catch(e) { _toast('Could not save release: $e'); }
  }

  Widget _header(String eyebrow, String title, String subtitle) => Padding(
    padding: const EdgeInsets.only(bottom: 18),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children:[
      Text(eyebrow,style:const TextStyle(color:_gold,fontSize:10,letterSpacing:2.2,fontWeight:FontWeight.w700)),
      const SizedBox(height:7), Text(title,style:const TextStyle(fontSize:26,fontWeight:FontWeight.w800)),
      const SizedBox(height:6), Text(subtitle,style:const TextStyle(color:Colors.white60,height:1.45)),
    ]));
  Widget _metric(String label, int value, IconData icon) => Card(child:Padding(
    padding:const EdgeInsets.all(15),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      Icon(icon,color:_gold),const SizedBox(height:13),
      Text('$value',style:const TextStyle(fontSize:26,fontWeight:FontWeight.w800)),
      Text(label,style:const TextStyle(color:Colors.white60,fontSize:12)),
    ])));
  Widget _trackRows() => _tracks.isEmpty ? const _AdminEmpty('No tracks found. Upload your first release.') :
    ListView.builder(shrinkWrap:true,physics:const NeverScrollableScrollPhysics(),itemCount:_tracks.length,itemBuilder:(ctx,i){
      final item=_tracks[i]; final published=item['is_published']==true;
      return Card(child:ListTile(
        leading:const CircleAvatar(backgroundColor:Color(0xFF30260E),child:Icon(Icons.music_note,color:_gold)),
        title:Text((item['title']??'Untitled').toString(),maxLines:1,overflow:TextOverflow.ellipsis),
        subtitle:Text('${item['artist']??'Unknown'} · ${item['genre']??'Genre'} · ${published?'Published':'Draft'}',maxLines:2),
        trailing:PopupMenuButton<String>(onSelected:(v){if(v=='toggle')_publishTrack(item,!published);if(v=='delete')_deleteTrack(item);},
          itemBuilder:(_)=>[PopupMenuItem(value:'toggle',child:Text(published?'Unpublish':'Publish')),
            const PopupMenuItem(value:'delete',child:Text('Delete'))]),
        onTap:()=>_toast('Audio URL: ${item['audio_url']??'Not available'}'),
      ));
    });
  Widget _artistRows() => Column(children:_artists.map((item)=>Card(child:ListTile(
    leading:const CircleAvatar(backgroundColor:Color(0xFF30260E),child:Icon(Icons.person,color:_gold)),
    title:Text((item['name']??'Artist').toString()),
    subtitle:Text((item['bio']??'').toString(),maxLines:2,overflow:TextOverflow.ellipsis),
    trailing:PopupMenuButton<String>(onSelected:(v)=>_publishArtist(item,v=='publish'),
      itemBuilder:(_)=>[PopupMenuItem(value:'publish',child:Text(item['is_published']==true?'Keep published':'Publish')),
        const PopupMenuItem(value:'draft',child:Text('Move to drafts'))]),
  ))).toList());
  Widget _releaseRows() => Column(children:_releases.map((item)=>Card(child:ListTile(
    leading:const CircleAvatar(backgroundColor:Color(0xFF30260E),child:Icon(Icons.album,color:_gold)),
    title:Text((item['title']??'Release').toString()),
    subtitle:Text((item['release_date']??'No release date').toString()),
    trailing:PopupMenuButton<String>(onSelected:(v)=>_publishRelease(item,v=='publish'),
      itemBuilder:(_)=>[PopupMenuItem(value:'publish',child:Text(item['is_published']==true?'Keep published':'Publish')),
        const PopupMenuItem(value:'draft',child:Text('Move to drafts'))]),
  ))).toList());

  Widget _page() {
    switch(_section) {
      case 0: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('PRIVATE FOUNDER STUDIO','Command centre','Manage the High-Tech Entertainment catalogue and publishing operations.'),
        GridView.count(crossAxisCount:2,shrinkWrap:true,physics:const NeverScrollableScrollPhysics(),childAspectRatio:1.45,crossAxisSpacing:8,mainAxisSpacing:8,children:[
          _metric('Total tracks',_metrics['Tracks']??0,Icons.music_note),
          _metric('Published',_metrics['Published']??0,Icons.public),
          _metric('Artists',_metrics['Artists']??0,Icons.people),
          _metric('Releases',_metrics['Releases']??0,Icons.album),
        ]),
        const SizedBox(height:18),FilledButton.icon(onPressed:()=>setState(()=>_section=1),icon:const Icon(Icons.upload),label:const Text('Upload new music')),
        const SizedBox(height:20),const Text('Recent catalogue',style:TextStyle(fontWeight:FontWeight.w700)),_trackRows(),
      ]);
      case 1: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('CATALOGUE MANAGEMENT','Music uploads','Upload audio to the existing public audio bucket. Add a public artwork URL from the Artwork screen. New tracks remain drafts until you publish them.'),
        TextField(controller:_title,decoration:const InputDecoration(labelText:'Track title')),
        const SizedBox(height:10),TextField(controller:_artist,decoration:const InputDecoration(labelText:'Artist')),
        const SizedBox(height:10),TextField(controller:_genre,decoration:const InputDecoration(labelText:'Genre')),
        const SizedBox(height:12),
        OutlinedButton.icon(onPressed:_busy?null:_pickAudio,icon:const Icon(Icons.audio_file),label:Text(_audio?.name??'Choose audio file')),
        TextField(controller:_coverUrl,decoration:const InputDecoration(labelText:'Cover artwork URL (optional)',hintText:'https://…')),
        const SizedBox(height:16),SizedBox(width:double.infinity,child:FilledButton(onPressed:_busy?null:_saveTrack,child:_busy?const CircularProgressIndicator():const Text('Upload and save draft'))),
      ]);
      case 2: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('CATALOGUE','Tracks','Review uploaded tracks, publish them to HT Music or remove unwanted entries.'),_trackRows()]);
      case 3: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('CREATOR DIRECTORY','Artists','Create artist records. New records remain unpublished until your public catalogue is ready.'),
        TextField(controller:_artistName,decoration:const InputDecoration(labelText:'Artist name')),
        const SizedBox(height:10),TextField(controller:_artistBio,maxLines:3,decoration:const InputDecoration(labelText:'Biography')),
        const SizedBox(height:10),FilledButton(onPressed:_busy?null:_saveArtist,child:const Text('Save artist draft')),
        const SizedBox(height:18),_artistRows()]);
      case 4: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('RELEASE MANAGEMENT','Releases','Create release records and manage their publication status.'),
        TextField(controller:_releaseTitle,decoration:const InputDecoration(labelText:'Release title')),
        const SizedBox(height:10),TextField(controller:_spotifyUrl,decoration:const InputDecoration(labelText:'Spotify release URL (optional)')),
        const SizedBox(height:10),TextField(controller:_releaseCoverUrl,decoration:const InputDecoration(labelText:'Release artwork URL (optional)')),
        const SizedBox(height:10),FilledButton(onPressed:_busy?null:_saveRelease,child:const Text('Save release draft')),
        const SizedBox(height:18),_releaseRows()]);
      case 5: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('VISUAL LIBRARY','Artwork','Upload public release artwork to the dedicated HTE artwork bucket, then attach its URL to a track or release.'),
        OutlinedButton.icon(onPressed:_busy?null:_pickArtwork,icon:const Icon(Icons.image_outlined),label:Text(_artwork?.name??'Choose artwork image')),
        const SizedBox(height:10),
        SizedBox(width:double.infinity,child:FilledButton(onPressed:_busy?null:_uploadArtwork,child:_busy?const CircularProgressIndicator():const Text('Upload artwork'))),
        const SizedBox(height:16),
        TextField(controller:_coverUrl,decoration:const InputDecoration(labelText:'Artwork URL',hintText:'Upload an image or paste an existing public URL')),
        const SizedBox(height:8),
        const _AdminEmpty('Copy this URL into the Cover artwork URL field on the Music uploads screen. Public downloads are enabled; upload and delete permissions remain restricted to HTE administrators.'),
      ]);
      case 6: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('ACCOUNT OVERSIGHT','Users','User directory and account actions must use a protected server-side function. This mobile client intentionally does not expose the Supabase service-role key or enumerate private Auth users.'),
        const _AdminEmpty('Connect a founder-authorized Edge Function before enabling account-level actions.'),
      ]);
      case 7: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('PLATFORM INSIGHTS','Analytics','Audience-wide metrics require a founder-authorized aggregate endpoint. These figures show the live catalogue counts only.'),
        _metric('Catalogue tracks',_metrics['Tracks']??0,Icons.music_note),
        _metric('Published tracks',_metrics['Published']??0,Icons.public),
      ]);
      default: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('AUDIT TRAIL','Platform activity','Recent catalogue records from Supabase. Full audit history requires a dedicated audit-log table.'),
        _trackRows(),
      ]);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_isFounder) return Scaffold(appBar:AppBar(title:const Text('HT Admin')),body:const _AdminEmpty('Founder authorization required.'));
    return Scaffold(
      appBar:AppBar(title:const Text('HT FOUNDER STUDIO'),actions:[
        IconButton(tooltip:'Refresh',onPressed:_busy?null:_loadAll,icon:const Icon(Icons.refresh)),
      ]),
      body:_busy && _tracks.isEmpty ? const Center(child:CircularProgressIndicator(color:_gold)) :
        _error!=null ? Center(child:Padding(padding:const EdgeInsets.all(20),child:Text('Could not load admin data: $_error'))) :
        RefreshIndicator(onRefresh:_loadAll,child:SingleChildScrollView(physics:const AlwaysScrollableScrollPhysics(),padding:const EdgeInsets.fromLTRB(16,16,16,30),child:_page())),
      bottomNavigationBar:SafeArea(top:false,child:Container(
        height:68,color:const Color(0xFF101113),
        child:SingleChildScrollView(scrollDirection:Axis.horizontal,padding:const EdgeInsets.symmetric(horizontal:8),
          child:Row(children:List.generate(_sections.length,(i)=>Padding(
            padding:const EdgeInsets.symmetric(horizontal:3,vertical:8),
            child:ChoiceChip(label:Text(_sections[i]),selected:_section==i,
              selectedColor:_gold,labelStyle:TextStyle(color:_section==i?Colors.black:Colors.white70,fontSize:11),
              onSelected:(_)=>setState(()=>_section=i)),
          ))),
        ),
      )),
    );
  }
}
class _AdminEmpty extends StatelessWidget {
  const _AdminEmpty(this.message);
  final String message;
  @override
  Widget build(BuildContext context)=>Center(child:Padding(padding:const EdgeInsets.all(24),child:Text(message,textAlign:TextAlign.center,style:const TextStyle(color:Colors.white60,height:1.5))));
}
