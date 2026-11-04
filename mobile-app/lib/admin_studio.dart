import 'dart:io';
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
  final _videoTitle = TextEditingController();
  final _videoArtist = TextEditingController();
  final _videoDescription = TextEditingController();
  final _notificationTitle = TextEditingController();
  final _notificationBody = TextEditingController();
  PlatformFile? _audio;
  PlatformFile? _artwork;
  PlatformFile? _video;
  List<Map<String,dynamic>> _videos = [];
  List<Map<String,dynamic>> _tracks = [];
  List<Map<String,dynamic>> _artists = [];
  List<Map<String,dynamic>> _releases = [];
  List<Map<String,dynamic>> _artworkFiles = [];
  List<Map<String,dynamic>> _users = [];
  Map<String,dynamic> _analytics = {};
  Map<String,int> _metrics = {};
  static const _sections = ['Overview','Music uploads','Tracks','Artists','Releases','Artwork','Users','Analytics','Activity','Videos','Notifications'];

  @override
  void initState() { super.initState(); _loadAll(); }
  @override
  void dispose() {
    _title.dispose(); _artist.dispose(); _genre.dispose();
    _artistName.dispose(); _artistBio.dispose(); _releaseTitle.dispose(); _spotifyUrl.dispose(); _releaseCoverUrl.dispose(); _coverUrl.dispose(); _videoTitle.dispose(); _videoArtist.dispose(); _videoDescription.dispose(); _notificationTitle.dispose(); _notificationBody.dispose();
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
      final videoRows = await _supabase.from('videos').select().order('created_at', ascending: false).limit(100);
      _videos = List<Map<String,dynamic>>.from(videoRows);
      try {
        final files = await _supabase.storage.from('hte-artwork').list(path: 'releases');
        _artworkFiles = files.map((file) => {'name':file.name}).toList();
      } catch (e) { debugPrint('Artwork library listing unavailable: $e'); }
      try {
        final users = await _supabase.rpc('hte_admin_list_users');
        _users = List<Map<String,dynamic>>.from(users);
      } catch (e) { debugPrint('Secure user directory unavailable: $e'); }
      try {
        final analytics = await _supabase.rpc('hte_admin_get_analytics');
        if (analytics is List && analytics.isNotEmpty) {
          _analytics = Map<String,dynamic>.from(analytics.first);
        }
      } catch (e) { debugPrint('Secure platform analytics unavailable: $e'); }
      _metrics = {
        'Tracks': _tracks.length, 'Published': _tracks.where((e)=>e['is_published']==true).length,
        'Artists': _artists.length, 'Releases': _releases.length, 'Users': _users.length,
      };
    } catch (e) { debugPrint('Founder Studio load failed: $e'); _error = 'Could not load Founder Studio data. Check your connection and retry.'; }
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _pickArtwork() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: ['jpg','jpeg','png','webp','avif'], withData: false);
    if (mounted && result != null && result.files.isNotEmpty) setState(() => _artwork = result.files.first);
  }
  Future<void> _uploadArtwork() async {
    if (_artwork == null) { _toast('Choose an image first.'); return; }
    setState(() => _busy = true);
    try {
      final url = await _uploadFile(_artwork!, 'releases', 'hte-artwork');
      if (!mounted) return;
      _coverUrl.text = url;
      setState(() => _artwork = null);
      _toast('Artwork uploaded. Its public URL is ready to attach to a track.');
    } catch(e) { _toast('Artwork upload failed: $e'); }
    finally { if(mounted)setState(()=>_busy=false); }
  }
  Future<void> _pickAudio() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom,
      allowedExtensions: ['mp3','m4a','aac','wav','ogg','flac'], withData: false);
    if (mounted && result != null && result.files.isNotEmpty) setState(() => _audio = result.files.first);
  }
  String _extension(PlatformFile file) => (file.extension ?? 'bin').toLowerCase();
  Future<String> _uploadFile(PlatformFile file, String folder, String bucket) async {
    final localPath = file.path;
    if (localPath == null || localPath.isEmpty) throw Exception('The selected file path is unavailable. Please select it again.');
    final localFile = File(localPath);
    if (!await localFile.exists()) throw Exception('The selected file is no longer available on this device.');
    final path = '$folder/${DateTime.now().millisecondsSinceEpoch}_${file.name.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_')}';
    final ext = _extension(file);
    final mime = <String,String>{
      'mp3':'audio/mpeg','m4a':'audio/x-m4a','aac':'audio/aac',
      'wav':'audio/wav','ogg':'audio/ogg','flac':'audio/flac',
      'jpg':'image/jpeg','jpeg':'image/jpeg','png':'image/png','webp':'image/webp','avif':'image/avif',
      'mp4':'video/mp4','m4v':'video/x-m4v','mov':'video/quicktime','webm':'video/webm',
    }[ext];
    await _supabase.storage.from(bucket).upload(path, localFile,
      fileOptions: FileOptions(upsert: false, contentType: mime));
    return _supabase.storage.from(bucket).getPublicUrl(path);
  }
  Future<void> _saveTrack() async {
    if (_title.text.trim().isEmpty || _artist.text.trim().isEmpty || _audio == null) {
      _toast('Enter a title and artist, then choose an audio file.'); return;
    }
    setState(() => _busy = true);
    try {
      final audioUrl = await _uploadFile(_audio!, 'tracks', 'hte-audio');
      final coverUrl = _coverUrl.text.trim();
      await _supabase.from('tracks').insert({
        'title': _title.text.trim(), 'artist': _artist.text.trim(),
        'genre': _genre.text.trim(), 'audio_url': audioUrl,
        if (coverUrl.isNotEmpty) 'cover_url': coverUrl,
        'uploader': 'High-Tech Entertainment', 'is_published': false,
      });
      if (!mounted) return;
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
      if (!mounted) return;
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
  Future<void> _pickVideo() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['mp4','m4v','mov','webm'],
      withData: false,
    );
    if (mounted && result != null && result.files.isNotEmpty) {
      setState(() => _video = result.files.first);
    }
  }

  Future<void> _saveVideo() async {
    if (_videoTitle.text.trim().isEmpty || _video == null) {
      _toast('Enter a video title and choose a video file.');
      return;
    }
    setState(() => _busy = true);
    try {
      final url = await _uploadFile(_video!, 'videos', 'hte-videos');
      await _supabase.from('videos').insert({
        'title': _videoTitle.text.trim(),
        'artist': _videoArtist.text.trim().isEmpty ? 'High-Tech Entertainment' : _videoArtist.text.trim(),
        'description': _videoDescription.text.trim(),
        'video_url': url,
        'is_published': false,
      });
      _videoTitle.clear(); _videoArtist.clear(); _videoDescription.clear();
      setState(() => _video = null);
      await _loadAll();
      _toast('Video uploaded as a draft. Publish it when ready.');
    } catch (error) {
      debugPrint('Founder video upload failed: $error');
      _toast('Video upload failed. Check your connection, file size and permissions.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _publishVideo(Map<String,dynamic> item, bool published) async {
    try {
      await _supabase.from('videos').update({'is_published': published}).eq('id', item['id']);
      await _loadAll();
      _toast(published ? 'Video published in HT Music.' : 'Video moved to drafts.');
    } catch (error) {
      debugPrint('Video publication update failed: $error');
      _toast('Could not update this video. Please retry.');
    }
  }

  Future<void> _deleteVideo(Map<String,dynamic> item) async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete video?'),
        content: Text('Delete "${item['title']}" from the video catalogue?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (yes != true) return;
    try {
      await _supabase.from('videos').delete().eq('id', item['id']);
      await _loadAll();
      _toast('Video removed from the catalogue.');
    } catch (error) {
      debugPrint('Video deletion failed: $error');
      _toast('Could not delete this video. Please retry.');
    }
  }

  Future<void> _sendNotification() async {
    final title = _notificationTitle.text.trim();
    final body = _notificationBody.text.trim();
    if (title.isEmpty || body.isEmpty) {
      _toast('Enter both a notification title and message.');
      return;
    }
    setState(() => _busy = true);
    try {
      await _supabase.from('app_notifications').insert({
        'user_id': null,
        'title': title,
        'body': body,
        'category': 'announcement',
      });
      _notificationTitle.clear(); _notificationBody.clear();
      _toast('Announcement sent to the in-app notification inbox.');
    } catch (error) {
      debugPrint('Announcement send failed: $error');
      _toast('Announcement could not be sent. Check founder permissions and retry.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _videoRows() => _videos.isEmpty
      ? const _AdminEmpty('No videos have been uploaded yet.')
      : Column(children: _videos.map((item) {
          final published = item['is_published'] == true;
          return Card(child: ListTile(
            leading: const Icon(Icons.video_library_outlined, color: _gold),
            title: Text((item['title'] ?? 'Video').toString(), maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: Text('${item['artist'] ?? 'High-Tech Entertainment'} · ${published ? 'Published' : 'Draft'}'),
            trailing: PopupMenuButton<String>(
              onSelected: (value) {
                if (value == 'toggle') _publishVideo(item, !published);
                if (value == 'delete') _deleteVideo(item);
              },
              itemBuilder: (_) => [
                PopupMenuItem(value: 'toggle', child: Text(published ? 'Move to drafts' : 'Publish')),
                const PopupMenuItem(value: 'delete', child: Text('Delete')),
              ],
            ),
          ));
        }).toList());

  Future<void> _saveRelease() async {
    if (_releaseTitle.text.trim().isEmpty) { _toast('Enter a release title.'); return; }
    try {
      await _supabase.from('releases').insert({'title':_releaseTitle.text.trim(),'spotify_url':_spotifyUrl.text.trim().isEmpty?null:_spotifyUrl.text.trim(),'cover_url':_releaseCoverUrl.text.trim().isEmpty?null:_releaseCoverUrl.text.trim(),'is_published':false});
      if (!mounted) return;
      _releaseTitle.clear(); _spotifyUrl.clear(); _releaseCoverUrl.clear(); await _loadAll(); _toast('Release saved as a draft.');
    } catch(e) { _toast('Could not save release: $e'); }
  }

  Widget _header(String eyebrow, String title, String subtitle) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children:[
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: const Color(0xFF2C2416),
          borderRadius: BorderRadius.circular(30),
          border: Border.all(color: const Color(0xFF66512E)),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.lock_rounded, size: 11, color: _gold),
          const SizedBox(width: 6),
          Text(eyebrow,style:const TextStyle(color:_gold,fontSize:9,letterSpacing:1.6,fontWeight:FontWeight.w800)),
        ]),
      ),
      const SizedBox(height:12),
      Text(title,style:const TextStyle(fontSize:28,fontWeight:FontWeight.w800,letterSpacing:-.5)),
      const SizedBox(height:7),
      Text(subtitle,style:const TextStyle(color:Colors.white60,height:1.5,fontSize:12)),
    ]));
  Widget _metric(String label, int value, IconData icon) => Container(
    padding: const EdgeInsets.all(15),
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(18),
      gradient: const LinearGradient(begin: Alignment.topLeft,end: Alignment.bottomRight,
        colors:[Color(0xFF1C1B18),Color(0xFF111214)]),
      border: Border.all(color: const Color(0xFF403625)),
    ),
    child: Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      Row(children:[
        Container(width:32,height:32,decoration:BoxDecoration(color:const Color(0xFF342A19),borderRadius:BorderRadius.circular(10)),
          child:Icon(icon,color:_gold,size:18)),
        const Spacer(),
        const Icon(Icons.north_east_rounded,color:Color(0xFF777064),size:14),
      ]),
      const Spacer(),
      Text('$value',style:const TextStyle(fontSize:27,fontWeight:FontWeight.w800,letterSpacing:-.6)),
      Text(label,style:const TextStyle(color:Colors.white60,fontSize:11)),
    ]));
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
          _metric('Users',_metrics['Users']??0,Icons.people_alt),
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
        const _AdminEmpty('Tap an uploaded image below to use it as track artwork. Public downloads are enabled; upload and delete permissions remain restricted to HTE administrators.'),
        const SizedBox(height:14),
        const Text('Uploaded artwork',style:TextStyle(fontWeight:FontWeight.w700)),
        if(_artworkFiles.isEmpty) const _AdminEmpty('No artwork uploaded yet.'),
        ..._artworkFiles.map((file)=>Card(child:ListTile(
          leading:const Icon(Icons.image_outlined,color:_gold),
          title:Text((file['name']??'Artwork').toString(),maxLines:1,overflow:TextOverflow.ellipsis),
          trailing:const Icon(Icons.copy_outlined),
          onTap:(){
            _coverUrl.text=_supabase.storage.from('hte-artwork').getPublicUrl('releases/${file['name']}');
            _toast('Artwork URL selected. Use it on the Music uploads screen.');
          },
        ))),
      ]);
      case 6: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('ACCOUNT OVERSIGHT','Users','Secure directory of registered accounts. The server checks the signed-in owner/admin role before returning any user records.'),
        if(_users.isEmpty) const _AdminEmpty('No users found, or the secure user directory could not be loaded. Pull to refresh.'),
        ..._users.map((user)=>Card(child:ListTile(
          leading:const CircleAvatar(backgroundColor:Color(0xFF30260E),child:Icon(Icons.person_outline,color:_gold)),
          title:Text((user['email']??'No email').toString(),maxLines:1,overflow:TextOverflow.ellipsis),
          subtitle:Text('Role: ${user['role']??'user'} · Joined: ${user['created_at']??'Unknown'}',maxLines:2,overflow:TextOverflow.ellipsis),
          trailing:const Icon(Icons.verified_user_outlined,color:_gold),
        ))),
        const _AdminEmpty('Account suspension, deletion and role changes remain protected server-side operations and are not exposed in this directory.'),
      ]);
      case 7: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('PLATFORM INSIGHTS','Analytics','Live aggregate metrics are returned by a role-checked Supabase function. Download totals are not available because download events are not currently recorded.'),
        GridView.count(crossAxisCount:2,shrinkWrap:true,physics:const NeverScrollableScrollPhysics(),childAspectRatio:1.45,crossAxisSpacing:8,mainAxisSpacing:8,children:[
          _metric('Total plays',int.tryParse('${_analytics['total_plays']??0}')??0,Icons.play_arrow),
          _metric('Unique listeners',int.tryParse('${_analytics['unique_listeners']??0}')??0,Icons.headphones),
          _metric('Ad impressions',int.tryParse('${_analytics['ad_impressions']??0}')??0,Icons.campaign),
          _metric('Published tracks',int.tryParse('${_analytics['published_tracks']??0}')??(_metrics['Published']??0),Icons.public),
        ]),
        const SizedBox(height:18),
        Card(child:ListTile(
          leading:const Icon(Icons.trending_up,color:_gold),
          title:Text((_analytics['top_track_title']??'No listening data yet').toString()),
          subtitle:Text('Top track · ${_analytics['top_track_plays']??0} plays'),
        )),
        const SizedBox(height:8),Text('Total catalogue tracks: ${_analytics['total_tracks']??_metrics['Tracks']??0}',style:const TextStyle(color:Colors.white60)),
      ]);
      case 9: return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _header('VIDEO LIBRARY', 'Videos', 'Upload MP4, M4V, MOV or WebM files. New uploads stay unpublished until ready.'),
        TextField(controller: _videoTitle, decoration: const InputDecoration(labelText: 'Video title')),
        const SizedBox(height: 10),
        TextField(controller: _videoArtist, decoration: const InputDecoration(labelText: 'Artist / creator')),
        const SizedBox(height: 10),
        TextField(controller: _videoDescription, maxLines: 3, decoration: const InputDecoration(labelText: 'Description')),
        const SizedBox(height: 12),
        OutlinedButton.icon(onPressed: _busy ? null : _pickVideo, icon: const Icon(Icons.video_file_outlined), label: Text(_video?.name ?? 'Choose video file')),
        const SizedBox(height: 12),
        SizedBox(width: double.infinity, child: FilledButton(onPressed: _busy ? null : _saveVideo, child: _busy ? const CircularProgressIndicator() : const Text('Upload video draft'))),
        const SizedBox(height: 20),
        const Text('Uploaded videos', style: TextStyle(fontWeight: FontWeight.w700)),
        _videoRows(),
      ]);
      case 10: return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _header('APP COMMUNICATION', 'Notifications', 'Send an in-app announcement to all signed-in High-Tech Entertainment users.'),
        TextField(controller: _notificationTitle, maxLength: 120, decoration: const InputDecoration(labelText: 'Notification title')),
        const SizedBox(height: 10),
        TextField(controller: _notificationBody, maxLines: 4, maxLength: 1000, decoration: const InputDecoration(labelText: 'Message')),
        const SizedBox(height: 12),
        SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: _busy ? null : _sendNotification, icon: const Icon(Icons.send), label: const Text('Send announcement'))),
      ]);
      default: return Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        _header('AUDIT TRAIL','Platform activity','Recent tracks, artist profiles and releases from the live catalogue. A full actor-by-actor audit trail requires a dedicated audit-log table.'),
        const Text('Recent tracks',style:TextStyle(fontWeight:FontWeight.w700)),_trackRows(),
        const SizedBox(height:14),const Text('Artist activity',style:TextStyle(fontWeight:FontWeight.w700)),_artistRows(),
        const SizedBox(height:14),const Text('Release activity',style:TextStyle(fontWeight:FontWeight.w700)),_releaseRows(),
      ]);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_isFounder) return Scaffold(appBar:AppBar(title:const Text('HT Admin')),body:const _AdminEmpty('Founder authorization required.'));
    return Scaffold(
      appBar:AppBar(
        title:Row(children:[
          Container(width:32,height:32,decoration:BoxDecoration(
            color:const Color(0xFF2C2416),borderRadius:BorderRadius.circular(10),
            border:Border.all(color:const Color(0xFF66512E))),
            child:const Icon(Icons.diamond_outlined,color:_gold,size:19)),
          const SizedBox(width:10),
          const Column(crossAxisAlignment:CrossAxisAlignment.start,mainAxisSize:MainAxisSize.min,children:[
            Text('HT FOUNDER STUDIO',style:TextStyle(fontSize:12,fontWeight:FontWeight.w800,letterSpacing:1.1)),
            Text('PRIVATE CONTROL CENTRE',style:TextStyle(fontSize:8,color:Colors.white54,letterSpacing:1.2)),
          ]),
        ]),
        actions:[IconButton(tooltip:'Refresh',onPressed:_busy?null:_loadAll,icon:const Icon(Icons.refresh))],
      ),
      body:_busy && _tracks.isEmpty ? const Center(child:CircularProgressIndicator(color:_gold)) :
        _error!=null ? Center(child:Padding(padding:const EdgeInsets.all(20),child:Text(_error ?? 'Could not load Founder Studio data. Please retry.'))) :
        RefreshIndicator(onRefresh:_loadAll,child:SingleChildScrollView(physics:const AlwaysScrollableScrollPhysics(),padding:const EdgeInsets.fromLTRB(16,16,16,30),child:_page())),
      bottomNavigationBar:SafeArea(top:false,child:Container(
        decoration:const BoxDecoration(color:Color(0xFF0B0C0E),border:Border(top:BorderSide(color:Color(0xFF302A20)))),
        child:SingleChildScrollView(scrollDirection:Axis.horizontal,padding:const EdgeInsets.symmetric(horizontal:10,vertical:10),
          child:Row(children:List.generate(_sections.length,(i){
            final selected=_section==i;
            return Padding(
              padding:const EdgeInsets.symmetric(horizontal:3),
              child:InkWell(
                borderRadius:BorderRadius.circular(13),
                onTap:()=>setState(()=>_section=i),
                child:AnimatedContainer(
                  duration:const Duration(milliseconds:180),
                  padding:const EdgeInsets.symmetric(horizontal:13,vertical:10),
                  decoration:BoxDecoration(
                    color:selected?const Color(0xFF342A19):const Color(0xFF151619),
                    borderRadius:BorderRadius.circular(13),
                    border:Border.all(color:selected?const Color(0xFF8A6D38):const Color(0xFF292A2C)),
                  ),
                  child:Text(_sections[i],style:TextStyle(
                    color:selected?const Color(0xFFE5C984):Colors.white60,
                    fontSize:11,fontWeight:selected?FontWeight.w800:FontWeight.w500)),
                ),
              ),
            );
          })),
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
