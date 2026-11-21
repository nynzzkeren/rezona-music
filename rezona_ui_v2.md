# REZONA UI V2 (overrides Stages 1-3 of rezona_master.md)

Reference screenshots are in reference/ (also attached in chat): now_playing.jpg, track_menu.jpg, search_home.jpg, lyrics.jpg, search_results.jpg, home_1.jpg, home_2.jpg, home_3.jpg, library.jpg. They show ANOTHER app's layout. Recreate each screen's LAYOUT, SPACING, PROPORTIONS, and BEHAVIOR as faithfully as possible (near pixel-level for structure). But: all text in English; use Rezona's own thin line icon set (do NOT reuse the reference icons); Satoshi font; Rezona colors from the current cover (no fixed reds/greens); Rezona Liquid Glass material for buttons, pills, dock, sheets, and menus. Never copy its branding.
Global rules: NO streak anywhere. NO boxes or cards behind list content: lists are plain rows on the ambient background (hairline separators at most), sections are headers + content, not containers. Glass is used only for buttons, chips, pills, the dock, the mini player, sheets, and menus. No flat black blocks. Compare every screen side by side with its reference and fix every structural difference before reporting.

## STAGE A: NAVIGATION + DOCK + HOME
Main navigation: 4 tabs: Home, Search, Library, You. The Studio tab is removed (hide Mood Dial, Focus Rooms, Sound Check, Echo profile screens behind a feature flag, do not delete code).
Dock behavior (as in the references): floating glass pill with icon + label, the active tab gets a glass capsule bubble with its label; the mini player floats above it. On scroll down the dock collapses into ONE round glass button (current tab icon) at the left of the screen and the mini player widens to fill the rest of the row; on scroll up it expands again with a spring.
Home (home_1/2/3.jpg), top to bottom:
1. Small "Home" title, then the big time-based greeting ("Good morning/afternoon/evening").
2. Quick picks: 2-column grid of compact items (square cover left, title right, 2-line max), up to 6, no card background (just a subtle glass pill per item is NOT allowed; use plain rows).
3. (No streak card. Nothing replaces it.)
4. Mood chips, horizontally scrollable glass chips: Relax, Feel good, Energize, Sleep, Sad, Workout, Focus. Selecting one filters the feed with a crossfade.
5. Continue: one large rounded cover with title and "N songs".
6. Your staples: 3-column cover grid, 3 rows per page, horizontal paging with page dots; the first item shows an artist chevron like the reference.
7. Listener picks: two wide collage covers (2x2 mosaic of covers) with a play overlay, title and subtitle below.
8. "Similar to <artist>" sections (several, one per top artist, artist avatar circle in the header for some, arrow at the right): horizontal rows of cover cards with title and subtitle.
9. Song mix: list rows (cover, title, artist, 3-dot), paged horizontally 4 rows per page with the next page peeking, "Play all" at the right of the header.
10. Trending songs for you (same paged rows), Covers and remixes (same), Trending community playlists (cover carousel).
All content is real data from the YouTube Music layer plus local history (Stage C). The ambient background is the single continuous cover-tinted layer. Pull to refresh.

## STAGE B: SEARCH
Reference search_home.jpg and search_results.jpg.
- Header: back arrow + glass pill search field ("Search songs, albums, artists...") with clear button. Below, a glass segmented switch: "Online | On device" (on device = local audio via MediaStore with runtime permission and rationale).
- Idle state: "Recently played" (horizontal large covers with title and artist), "Most played" (numbered rows: rank, 56dp cover, title, artist), "Favorite artists" (circular avatars with names, skeleton when loading), "Browse all" (genre/mood tiles in a 2-column grid: Pop, Dangdut, Hip-hop, ...; tiles are glass buttons with generative patterns, not boxes of black). Floating glass mic button (voice search) at the bottom right above the dock.
- Typing state: live suggestions list (magnifier icon, suggestion text, an "insert into field" arrow icon at the right), debounce 200ms, from the YouTube Music suggestions endpoint. Results below as plain rows: 56dp cover, title, artist, 3-dot menu; the first row shows a play overlay on its cover; no source badges. Tapping a row plays it and queues radio.
- Results behavior: tabs/filter chips for Songs, Videos, Albums, Artists, Playlists; "All" shows top result + sections. Infinite scroll via continuation tokens. At least 20 results per page where available.
- BUG TO FIX FIRST: search currently returns only ONE result. Find the root cause (parsing only the first shelf or top card, ignoring musicShelf contents and continuations). Implement filtered search and continuation properly, parse every shelf/renderer type defensively, and add tests using recorded JSON fixtures. Use SimpMusic's kotlinYtmusicScraper (Ytmusic.kt) and InnerTune/Metrolist innertube modules as architecture references (all GPL-3.0: adapt with attribution, add notices to the licenses screen, verify against live responses). Debug screen shows result counts per section and the raw request params.

## STAGE C: DATA LAYER (YouTube Music only)
Remove Spotify and SoundCloud completely (done in Stage 0.9 if not yet). Implement/verify with the same references: home feed with continuation (quick picks, mixes, similar-to-artist, covers/remixes, trending, listener picks), moods and genres, charts, artist/album/playlist pages, radio/up-next from a track, search suggestions, filtered search with continuation. Streams via NewPipeExtractor with caching and refresh. Parse defensively and isolate failures per section. Local history in Room feeds Recently played, Most played, Favorite artists, Your staples, My Top 50, and monthly/weekly top playlists.

## STAGE D: NOW PLAYING (now_playing.jpg)
- Full-bleed cover: a square cover edge-to-edge at the top, extending under the status bar (no panel, no frame). Its bottom blends into a vertical gradient of the cover's dominant dark color down to the controls (same continuous ambient layer behind).
- Top-left overlay: small "Now playing" and below it the queue name in bold italic (for example "Mix Multo"). Tapping it opens the Queue sheet (Up Next / History, reorder, remove).
- Below the cover: title (bold, left aligned) and artist (left aligned); on the right two 48dp glass circle buttons: heart (like) and three-dot menu.
- Progress line with elapsed time at the left and remaining time (with a minus sign) at the right.
- Transport row exactly like the reference: bare icons (shuffle, previous, a large play/pause glyph, next, repeat), no filled button behind; a glass bubble appears only while pressed. Haptics.
- Volume row: speaker-low icon, slider, speaker-high icon, bound to the system music volume.
- SOUND PRESETS ROW (replaces the removed Studio tab): between the volume row and the bottom pill, a row of 5 one-tap glass buttons with tiny labels: Slowed + Reverb, Echo, Lo-fi, Nightcore, Concert. No sliders and no manual tuning. Tap = apply, tap again = back to Normal; only one active at a time, the active one glows. Implement with Media3 PlaybackParameters (speed/pitch) plus audio effects (reverb, EQ shaping, subtle filtering) tuned by you so each sounds clearly different and good; persist the last choice per session.
- Bottom glass pill with 3 buttons: Lyrics, Share, Sleep timer (icons only; the active one has a glass capsule bubble). Share opens a sheet: Share song link, Share song card (image), Share lyrics (text/poster). Sleep timer opens a glass sheet: 15/30/45/60 min, End of track, Custom; a countdown chip appears on the pill while active.
- Gestures: swipe cover left/right = previous/next, double-tap = like, swipe down = collapse to the mini player (shared-element morph).

## STAGE E: THREE-DOT MENU (track_menu.jpg), glass bottom sheet
Top: volume slider row. Then four glass tiles in a row-wrapped grid: Start radio, Add to playlist, Copy link, Share song card. Then grouped plain rows with hairline separators (leading Rezona icon, title, optional one-line description):
View artist (artist name as description); Add to library / Remove from library; Pin to speed dial; Download (saves an app-private offline copy for playback inside Rezona only, no export, no file conversion; show progress and storage use; personal-use note in Settings); Listen together (stub, honest "coming soon"); Details (view song details); Ambient mode (full-screen calm cover view); Equalizer (opens the in-app EQ); System equalizer (opens the Android built-in equalizer if present); Advanced (adjust tempo and pitch; manual sliders live only here).
DO NOT add "Sound style" (replaced by the presets row in the player).
The same sheet component is used for the 3-dot menu on list rows (without the volume row).

## STAGE F: LYRICS (lyrics.jpg)
- No boxes, no highlight block. Plain large bold text, left aligned, on the ambient background.
- Each lyric line is followed by its translation line in a smaller, lighter weight (translation language = device language by default, toggle in lyrics options; on-device translation).
- Active line: brightest white, normal scale; lines farther away get progressively more blur and lower opacity (a smooth falloff). Word-by-word fill on the active line when word timestamps exist (the first letters brighten like the reference), otherwise a smooth line-level fade.
- Instrumental gap: three animated dots at the top of the gap.
- Auto-scroll keeps the active line near the upper third of the viewport with a spring; user scroll pauses auto-follow for 3 seconds; tapping a line seeks.
- Header: compact row with a rounded cover thumbnail, title, artist, an expand icon (back to full player) and a 3-dot menu (lyrics options: reload, search another lyric, copy, translation, romanization, blur, text position, timing offset, source).
- Below it the same progress line, transport, volume, sound presets row, and the 3-button pill with the Lyrics button highlighted. Lyrics from LRCLIB primary, YouTube Music lyrics as fallback; match by title + artist + duration of the actual playing track.
- Verify scrolling is perfectly smooth with no jumps when lines change or the user seeks.

## STAGE G: LIBRARY (library.jpg)
- Title "Library". Text tabs with an underline indicator: All, Playlists, Songs, Albums, Artists, Podcasts (swipeable).
- Controls row: sort chip ("Date added" with direction arrow), then search and grid/list toggle icons at the right.
- Two large square cover cards at the top of All: "Top this month" and "Top this week" (auto playlists from local history, with song counts).
- "Collection" section as plain rows (leading icon, title, chevron): Liked, Downloaded, Cached, On device, My Top 50. A glass floating "+" button (with chevron) at the bottom right for New playlist / Import playlist (M3U, CSV, YouTube Music link) / Backup and restore.
- Real data from Room and the YouTube Music layer, with empty states using the logo arcs.

## STAGE H: YOU TAB
Account card (guest state), History, Stats and Recap, Listen together (stub), Equalizer, Settings (Sound and playback, Appearance, Language and content, Lyrics and translation, Privacy and history, Storage with cache sliders, Backup and import, About with open-source licenses). Plain grouped rows, no card per item. No streak.

## FOR EVERY STAGE
Zero mock data in release behavior; one PlayerController state for all surfaces; lint clean; R8 release build works; smooth on a low-end phone; touch targets >= 48dp. Install via adb if connected, else APK path. Report files changed, differences from the reference you could not match and why, and what is real vs mock. Then stop and wait for approval.