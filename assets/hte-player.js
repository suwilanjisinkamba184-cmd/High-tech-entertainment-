/* HTE audio and video players.
   - Enhances every direct audio track (audio[data-track-play]) with a custom,
     accessible player. Without JavaScript the native audio controls remain.
   - Only one track plays at a time. A now-playing bar keeps the current track
     visible and controllable while the visitor scrolls.
   - Registers lock-screen and headset controls where the browser supports them.
   - Shows a clear message if a track or uploaded video cannot be played.
   Embedded YouTube and Spotify players are left to their official embeds. */
(function () {
  'use strict';

  var AUDIO_SELECTOR = 'audio[data-track-play]:not([data-hte-enhanced])';
  var VIDEO_SELECTOR = 'video[data-hte-video]:not([data-hte-enhanced])';
  var ERROR_TEXT = 'This track could not be played. Check your connection and try again.';
  var VIDEO_ERROR_TEXT = 'This video could not be played. Please try again later.';

  var active = null;       // the audio element currently shown in the now-playing bar
  var bar = null;          // the now-playing bar element

  function formatTime(seconds) {
    if (!isFinite(seconds) || seconds < 0) return '0:00';
    var total = Math.floor(seconds);
    var hours = Math.floor(total / 3600);
    var minutes = Math.floor((total % 3600) / 60);
    var secs = total % 60;
    var mm = hours ? String(minutes).padStart(2, '0') : String(minutes);
    return (hours ? hours + ':' : '') + mm + ':' + String(secs).padStart(2, '0');
  }

  function trackName(audio) {
    var label = audio.getAttribute('aria-label') || '';
    return label.replace(/^Play\s+/i, '').trim() || 'Track';
  }

  function setProgress(input, audio) {
    var duration = audio.duration;
    var percent = isFinite(duration) && duration > 0 ? (audio.currentTime / duration) * 100 : 0;
    input.value = String(percent);
    input.style.setProperty('--progress', percent.toFixed(2) + '%');
    input.setAttribute('aria-valuetext', formatTime(audio.currentTime) + ' of ' + formatTime(duration));
  }

  function setTimeText(node, audio) {
    node.textContent = formatTime(audio.currentTime) + ' / ' + formatTime(audio.duration);
  }

  /* ---------- Now-playing bar ---------- */

  function ensureBar() {
    if (bar) return bar;
    bar = document.createElement('div');
    bar.className = 'hte-now-playing';
    bar.setAttribute('role', 'region');
    bar.setAttribute('aria-label', 'Now playing');
    bar.hidden = true;
    bar.innerHTML =
      '<div class="hte-np-info"><span class="hte-np-kicker">Now playing</span><span class="hte-np-title"></span></div>' +
      '<button type="button" class="hte-np-toggle" aria-label="Play"><span class="hte-np-icon" aria-hidden="true"></span></button>' +
      '<div class="hte-np-progress"><input type="range" class="hte-np-seek" min="0" max="100" step="0.1" value="0" aria-label="Seek"><span class="hte-np-time">0:00 / 0:00</span></div>' +
      '<button type="button" class="hte-np-close" aria-label="Close player">Close</button>';
    document.body.appendChild(bar);

    bar.querySelector('.hte-np-toggle').addEventListener('click', function () {
      if (!active) return;
      if (active.paused) playAudio(active); else active.pause();
    });
    bar.querySelector('.hte-np-close').addEventListener('click', function () {
      if (active) active.pause();
      active = null;
      bar.hidden = true;
      document.body.classList.remove('hte-np-visible');
    });
    var seek = bar.querySelector('.hte-np-seek');
    seek.addEventListener('input', function () {
      if (!active || !isFinite(active.duration)) return;
      active.currentTime = (Number(seek.value) / 100) * active.duration;
    });
    return bar;
  }

  function showBar(audio) {
    ensureBar();
    active = audio;
    bar.hidden = false;
    document.body.classList.add('hte-np-visible');
    bar.querySelector('.hte-np-title').textContent = trackName(audio);
    syncBar();
  }

  function syncBar() {
    if (!bar || !active) return;
    var toggle = bar.querySelector('.hte-np-toggle');
    var playing = !active.paused && !active.ended;
    toggle.setAttribute('aria-label', playing ? 'Pause' : 'Play');
    toggle.classList.toggle('is-playing', playing);
    var seek = bar.querySelector('.hte-np-seek');
    setProgress(seek, active);
    setTimeText(bar.querySelector('.hte-np-time'), active);
    seek.setAttribute('aria-label', 'Seek ' + trackName(active));
  }

  /* ---------- Lock-screen and headset controls ---------- */

  function registerMediaSession(audio) {
    if (!('mediaSession' in navigator) || typeof window.MediaMetadata !== 'function') return;
    try {
      navigator.mediaSession.metadata = new window.MediaMetadata({
        title: trackName(audio),
        artist: 'High-Tech Entertainment',
        album: 'HTE Universe'
      });
      navigator.mediaSession.setActionHandler('play', function () { playAudio(audio); });
      navigator.mediaSession.setActionHandler('pause', function () { audio.pause(); });
    } catch (error) { /* Older browsers may not support every action. */ }
  }

  /* ---------- Playback ---------- */

  function playAudio(audio) {
    if (audio.error) audio.load();
    var attempt = audio.play();
    if (attempt && typeof attempt.catch === 'function') {
      attempt.catch(function (error) {
        if (error && error.name === 'AbortError') return; // interrupted by another play call
        var wrapper = audio.closest('.hte-player');
        if (wrapper) setState(wrapper, 'error', ERROR_TEXT);
      });
    }
  }

  function pauseOthers(current) {
    document.querySelectorAll('audio[data-hte-enhanced]').forEach(function (other) {
      if (other !== current && !other.paused) other.pause();
    });
  }

  function setState(wrapper, state, message) {
    wrapper.setAttribute('data-state', state);
    var status = wrapper.querySelector('.hte-player-status');
    if (status) status.textContent = message || '';
    var button = wrapper.querySelector('.hte-player-play');
    if (!button) return;
    var audio = wrapper.querySelector('audio');
    var name = audio ? trackName(audio) : 'track';
    var playing = audio ? (!audio.paused && !audio.ended) : state === 'playing';
    button.setAttribute('aria-label', (playing ? 'Pause ' : 'Play ') + name);
    button.setAttribute('aria-busy', state === 'loading' ? 'true' : 'false');
  }

  /* ---------- Enhancement ---------- */

  function enhanceAudio(audio) {
    audio.setAttribute('data-hte-enhanced', 'true');
    audio.removeAttribute('controls');
    audio.preload = 'none';
    var name = trackName(audio);

    var wrapper = document.createElement('div');
    wrapper.className = 'hte-player';
    wrapper.setAttribute('data-state', 'idle');
    wrapper.innerHTML =
      '<button type="button" class="hte-player-play" aria-label="Play ' + name.replace(/"/g, '&quot;') + '"><span class="hte-player-icon" aria-hidden="true"></span></button>' +
      '<div class="hte-player-main">' +
        '<input type="range" class="hte-player-seek" min="0" max="100" step="0.1" value="0" aria-label="Seek ' + name.replace(/"/g, '&quot;') + '">' +
        '<div class="hte-player-meta"><span class="hte-player-time">0:00 / 0:00</span><span class="hte-player-status" role="status"></span></div>' +
      '</div>';

    audio.parentNode.insertBefore(wrapper, audio);
    wrapper.appendChild(audio);

    var button = wrapper.querySelector('.hte-player-play');
    var seek = wrapper.querySelector('.hte-player-seek');
    var time = wrapper.querySelector('.hte-player-time');

    button.addEventListener('click', function () {
      if (audio.paused) playAudio(audio); else audio.pause();
    });
    seek.addEventListener('input', function () {
      if (!isFinite(audio.duration)) return;
      audio.currentTime = (Number(seek.value) / 100) * audio.duration;
      setProgress(seek, audio);
      setTimeText(time, audio);
    });

    audio.addEventListener('play', function () {
      pauseOthers(audio);
      setState(wrapper, 'playing', '');
      showBar(audio);
      registerMediaSession(audio);
    });
    audio.addEventListener('playing', function () { setState(wrapper, 'playing', ''); });
    audio.addEventListener('waiting', function () { setState(wrapper, 'loading', 'Loading…'); });
    audio.addEventListener('pause', function () {
      // Browsers also pause when a file fails to load; keep the error message in that case.
      if (audio.ended || audio.error) return;
      setState(wrapper, 'paused', '');
      syncBar();
    });
    audio.addEventListener('ended', function () {
      setState(wrapper, 'idle', '');
      syncBar();
    });
    audio.addEventListener('timeupdate', function () {
      setProgress(seek, audio);
      setTimeText(time, audio);
      if (active === audio) syncBar();
    });
    audio.addEventListener('loadedmetadata', function () {
      setTimeText(time, audio);
      setProgress(seek, audio);
    });
    audio.addEventListener('error', function () {
      setState(wrapper, 'error', ERROR_TEXT);
      if (active === audio) syncBar();
    });
  }

  function enhanceVideo(video) {
    video.setAttribute('data-hte-enhanced', 'true');
    video.addEventListener('error', function () {
      var note = video.parentNode.querySelector('.hte-media-error');
      if (!note) {
        note = document.createElement('div');
        note.className = 'hte-media-error';
        note.setAttribute('role', 'alert');
        note.innerHTML = '<span></span> <button type="button" class="hte-media-retry">Retry video</button>';
        video.parentNode.insertBefore(note, video.nextSibling);
        note.querySelector('button').addEventListener('click', function () {
          note.hidden = true;
          video.hidden = false;
          video.load();
        });
      }
      note.querySelector('span').textContent = VIDEO_ERROR_TEXT;
      note.hidden = false;
      video.hidden = true;
    });
  }

  function enhanceAll() {
    document.querySelectorAll(AUDIO_SELECTOR).forEach(enhanceAudio);
    document.querySelectorAll(VIDEO_SELECTOR).forEach(enhanceVideo);
  }

  enhanceAll();
  // Cards are rendered after the catalogue loads, so watch for new players.
  new MutationObserver(enhanceAll).observe(document.body, { childList: true, subtree: true });
})();
