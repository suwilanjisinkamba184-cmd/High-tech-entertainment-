/* Site search for the public homepage.
   Filters the music cards (#public-tracks) and video cards (#public-videos)
   that app.js and videos.js render, so it works with whatever the catalogue
   loads. Every word typed must match (for example "ace trap" finds
   "Pa Duty" by Ace Trap). Reapplies when the lists load or refresh. */
(function () {
  'use strict';

  var form = document.getElementById('site-search-form');
  var input = document.getElementById('site-search-input');
  if (!form || !input) return;

  var status = document.getElementById('site-search-status');
  var clearButton = document.getElementById('site-search-clear');
  var links = document.getElementById('site-search-links');
  var tracksBox = document.getElementById('public-tracks');
  var videosBox = document.getElementById('public-videos');
  var chips = form.parentElement.querySelectorAll('[data-search-query]');

  function normalise(text) {
    return String(text || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/\s+/g, ' ')
      .trim();
  }

  // Only the visible text of a card counts: titles, artist and genre lines,
  // and video kickers. Button labels such as "Play" are ignored.
  function searchableText(card) {
    var parts = [];
    card.querySelectorAll('h3, p, .video-card-kicker, .muted').forEach(function (node) {
      parts.push(node.textContent);
    });
    return normalise(parts.join(' '));
  }

  function cardsIn(box, selector) {
    if (!box) return [];
    return Array.prototype.filter.call(box.children, function (node) {
      return node.matches(selector);
    });
  }

  function plural(count, word) {
    return count + ' ' + word + (count === 1 ? '' : 's');
  }

  function apply() {
    var raw = input.value.trim();
    var words = normalise(raw).split(' ').filter(Boolean);
    var tracks = cardsIn(tracksBox, '.music-card, .media-card');
    var videos = cardsIn(videosBox, '.video-card');

    var matchTracks = 0;
    var matchVideos = 0;

    function filter(cards) {
      var count = 0;
      cards.forEach(function (card) {
        var text = searchableText(card);
        var match = words.every(function (word) { return text.indexOf(word) !== -1; });
        card.hidden = words.length > 0 && !match;
        if (match || words.length === 0) count++;
      });
      return count;
    }

    matchTracks = filter(tracks);
    matchVideos = filter(videos);

    clearButton.hidden = raw === '';

    if (!words.length) {
      status.textContent = '';
      links.hidden = true;
      return;
    }

    links.hidden = false;
    if (!tracks.length && !videos.length) {
      status.textContent = 'The catalogue is still loading. Try your search again in a moment.';
      return;
    }
    if (!matchTracks && !matchVideos) {
      status.textContent = 'No music or videos match “' + raw + '”. Try a shorter word, an artist name, or a genre.';
      return;
    }
    status.textContent = 'Found ' + plural(matchTracks, 'track') + ' and ' + plural(matchVideos, 'video') + ' for “' + raw + '”.';
  }

  function setQueryParam(value) {
    try {
      var url = new URL(window.location.href);
      if (value) url.searchParams.set('q', value);
      else url.searchParams.delete('q');
      window.history.replaceState(null, '', url.pathname + url.search + url.hash);
    } catch (error) { /* Some embedded browsers block history changes; search still works. */ }
  }

  var timer = null;
  input.addEventListener('input', function () {
    window.clearTimeout(timer);
    timer = window.setTimeout(function () {
      apply();
      setQueryParam(input.value.trim());
    }, 120);
  });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    apply();
    setQueryParam(input.value.trim());
    var target = document.getElementById('music');
    if (target && input.value.trim()) target.scrollIntoView({ block: 'start' });
  });

  clearButton.addEventListener('click', function () {
    input.value = '';
    apply();
    setQueryParam('');
    input.focus();
  });

  input.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && input.value) {
      event.preventDefault();
      clearButton.click();
    }
  });

  Array.prototype.forEach.call(chips, function (chip) {
    chip.addEventListener('click', function () {
      input.value = chip.getAttribute('data-search-query') || '';
      apply();
      setQueryParam(input.value.trim());
      input.focus();
    });
  });

  // Keep results right when the catalogue loads or is refreshed.
  [tracksBox, videosBox].forEach(function (box) {
    if (!box) return;
    new MutationObserver(apply).observe(box, { childList: true });
  });

  // Support shared links such as ?q=afrobeat.
  var initial = new URLSearchParams(window.location.search).get('q');
  if (initial) {
    input.value = initial.slice(0, 120);
  }
  apply();
})();
