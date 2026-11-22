(() => {
  'use strict';
  const mount = () => {
    const hero = document.querySelector('main .hero');
    const targets = [
      { key: 'music', label: 'Music', selector: '#public-tracks' },
      { key: 'artists', label: 'Artists', selector: '#public-artists' },
      { key: 'releases', label: 'Releases', selector: '#public-releases' },
      { key: 'videos', label: 'Videos', selector: '#public-videos' }
    ].map(item => ({ ...item, container: document.querySelector(item.selector) })).filter(item => item.container);
    if (!hero || !targets.length || document.getElementById('hte-site-search')) return;

    const panel = document.createElement('section');
    panel.id = 'hte-site-search';
    panel.className = 'hte-search-band';
    panel.setAttribute('aria-label', 'Search the HTE Universe catalogue');
    panel.innerHTML = `
      <div class="hte-search-shell">
        <div class="hte-search-copy">
          <span class="hte-search-kicker">DISCOVER THE UNIVERSE</span>
          <h2>Find your next sound.</h2>
          <p>Search published music, artists, releases and official videos in one place.</p>
        </div>
        <form class="hte-search-form" role="search" aria-label="Search HTE Universe">
          <label class="hte-search-input-wrap">
            <span class="hte-search-sr-only">Search music, artists, releases and videos</span>
            <svg class="hte-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8"></circle><path d="m16 16 4.5 4.5"></path></svg>
            <input id="hte-universe-search" type="search" name="q" placeholder="Search a song, artist, genre or video…" autocomplete="off" spellcheck="false">
          </label>
          <button class="hte-search-submit" type="submit">Search</button>
          <button class="hte-search-clear" type="reset">Clear</button>
        </form>
        <div class="hte-search-filters" role="group" aria-label="Filter search category">
          <button class="hte-search-filter" type="button" data-category="all" aria-pressed="true">All</button>
          <button class="hte-search-filter" type="button" data-category="music" aria-pressed="false">Music</button>
          <button class="hte-search-filter" type="button" data-category="artists" aria-pressed="false">Artists</button>
          <button class="hte-search-filter" type="button" data-category="releases" aria-pressed="false">Releases</button>
          <button class="hte-search-filter" type="button" data-category="videos" aria-pressed="false">Videos</button>
        </div>
        <p class="hte-search-status" role="status" aria-live="polite">Search across the HTE public catalogue.</p>
      </div>`;
    hero.insertAdjacentElement('afterend', panel);

    const form = panel.querySelector('form');
    const input = panel.querySelector('#hte-universe-search');
    const status = panel.querySelector('.hte-search-status');
    const filters = Array.from(panel.querySelectorAll('[data-category]'));
    let category = 'all';
    let query = '';

    const apply = () => {
      let total = 0;
      let visible = 0;
      let loading = false;
      let firstMatch = null;

      targets.forEach(target => {
        const section = target.container.closest('section');
        const active = category === 'all' || category === target.key;
        if (section) section.classList.toggle('hte-search-section-hidden', !active);
        const cards = Array.from(target.container.querySelectorAll(':scope > article'));
        const empty = target.container.querySelector('.empty');
        if (active && empty && /loading/i.test(empty.textContent || '')) loading = true;

        cards.forEach(card => {
          const text = (card.innerText || card.textContent || '').normalize('NFKC').toLocaleLowerCase();
          const matches = !query || query.split(/\s+/).filter(Boolean).every(term => text.includes(term));
          const show = active && matches;
          card.classList.toggle('hte-search-card-hidden', !show);
          if (active) {
            total += 1;
            if (matches) {
              visible += 1;
              if (!firstMatch) firstMatch = card;
            }
          }
        });
      });

      if (!query) {
        status.textContent = category === 'all'
          ? 'Search across tracks, artists, releases and official videos.'
          : 'Browsing ' + targets.find(item => item.key === category).label + '. Enter a search to narrow the results.';
        status.dataset.state = 'ready';
      } else if (!visible && loading) {
        status.textContent = 'The catalogue is still loading. Search results will appear as published content arrives.';
        status.dataset.state = 'loading';
      } else if (!visible) {
        status.textContent = 'No matches found. Try another title, artist or genre.';
        status.dataset.state = 'empty';
      } else {
        status.textContent = visible + ' result' + (visible === 1 ? '' : 's') + ' found' + (total > visible ? ' from ' + total + ' items' : '') + '.';
        status.dataset.state = 'results';
      }
      return { visible, firstMatch };
    };

    const setCategory = next => {
      category = next;
      filters.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.category === category)));
      apply();
    };

    input.addEventListener('input', () => {
      query = input.value.trim().toLocaleLowerCase();
      apply();
    });
    form.addEventListener('submit', event => {
      event.preventDefault();
      query = input.value.trim().toLocaleLowerCase();
      const result = apply();
      if (result.firstMatch) result.firstMatch.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
    form.addEventListener('reset', () => {
      window.setTimeout(() => {
        query = '';
        setCategory('all');
        input.focus();
      }, 0);
    });
    filters.forEach(button => button.addEventListener('click', () => setCategory(button.dataset.category)));
    input.addEventListener('keydown', event => {
      if (event.key === 'Escape' && input.value) {
        input.value = '';
        query = '';
        apply();
      }
    });

    targets.forEach(target => {
      const observer = new MutationObserver(apply);
      observer.observe(target.container, { childList: true, subtree: true });
    });
    apply();
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount, { once: true });
  else mount();
})();