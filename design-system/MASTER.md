# REZONA MASTER DESIGN SYSTEM (MASTER.md)
**Version:** 3.1.0 · Strict Monochrome & Pure Resonance Edition  
**Platform:** Android Native (Jetpack Compose, Kotlin, AndroidX Media3 ExoPlayer)  
**Status:** REVISED SPECIFICATION AWAITING APPROVAL  

---

## 1. Brand Identity & Visual Philosophy

- **Application Name:** **Rezona**
- **Brand Mark:** Three concentric rounded arcs radiating outward from a solid origin node — a pure glyph of acoustic propagation and resonance.
- **Visual Essence:** **Strict Monochrome Minimalism with Liquid Glass**. No decorative colors, no saturated rainbow gradients, no neon buttons. The app is an instrument of black, obsidian glass, and pure light. The album artwork is the sole bearer of color in the entire universe of the application.
- **Tone of Voice:** Silent, precise, architectonic, high-fidelity. English UI throughout.

---

## 2. Color System: Obsidian Silver Monochrome Palette

Rezona strictly adheres to the **Obsidian Silver** monochrome hierarchy derived directly from the reference specification, paired with high-translucency glass and pure white specular light.

### 2.1 The Obsidian Silver Palette (Exact Specification)
| Token Name | Hex Value | Role & Usage |
| :--- | :--- | :--- |
| `ObsidianBlack` | `#08090B` | **Background utama** — Pure OLED root background floor |
| `DeepCharcoal` | `#111318` | **Surface / card** — Base card background & bottom sheet container |
| `Graphite` | `#1C1F26` | **Elevated surface** — Elevated chips, circular action discs, active track tile |
| `SmokySilver` | `#858B96` | **Secondary elements** — Subtitles, artist names, timestamps, section tags |
| `PearlWhite` | `#F4F5F7` | **Text utama** — Primary headings, track titles, active lyrics |
| `SoftSilver` | `#C4C8D0` | **Icon dan highlight** — Secondary icons, inactive navigation tabs, borders |
| `PureWhite` | `#FFFFFF` | **Interactive Accent** — Active playback controls, specular rim glares, scrubber thumb |

### 2.2 Translucent Liquid Glass Overlays
Overlaid across the Obsidian Silver base, liquid glass cards utilize translucency combined with live hardware backdrop blur:
- **Glass Fill (Standard Card):** `Color(0x111318).copy(alpha = 0.65f)`
- **Glass Fill (Elevated Disc/Pill):** `Color(0x1C1F26).copy(alpha = 0.75f)`
- **Floating Dock & Mini Player:** `Color(0x08090B).copy(alpha = 0.70f)` with live `Haze` backdrop blur.

### 2.3 Specular Rims & Glass Borders (White-to-Transparent Only)
All glass containers use optical directional gradients that simulate top-down light catching the beveled edge of milled physical glass:
- **Card Edge (Level 1):** `1.0.dp` stroke with `Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x08FFFFFF)))`
- **Floating Dock & Mini Player Edge (Level 2):** `1.25.dp` stroke with `Brush.verticalGradient(listOf(Color(0x60FFFFFF), Color(0x18FFFFFF), Color(0x04FFFFFF)))`
- **Modal Rim (Level 3):** `1.5.dp` top-light specular highlight `listOf(Color(0x80FFFFFF), Color(0x20FFFFFF), Color.Transparent)`

### 2.4 Heavily Desaturated Ambient Artwork Bleed
- The background is **not** saturated with neon colors.
- Extracted artwork colors are strictly clamped to a **maximum 20%–25% saturation** and **low luminance (0.08–0.20)**.
- It acts like a faint mist of light in an otherwise obsidian-dark room (`blur(90.dp)`), allowing the app to read strictly as black and white while grounding the playing track organically.

---

## 3. Typography: Satoshi (Geometric Modernism)

Rezona avoids generic system Roboto in favor of **Satoshi** (bundled in `res/font/` as `satoshi_bold.otf`, `satoshi_medium.otf`, and `satoshi_regular.otf`), known for its geometric clarity, tight apertures, and crisp readability over glass.

### 3.1 Type Scale & Hierarchy
| Role | Font Style | Size | Weight | Line Height | Tracking | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Display** | Satoshi | 28sp | Bold | 34sp | -0.5sp | Root View Titles ("Home", "Search", "Studio", "Library") |
| **Headline** | Satoshi | 20sp | Bold | 26sp | -0.2sp | "Now Playing", Modal titles |
| **Section** | Satoshi | 17sp | Bold | 22sp | 0.0sp | "Speed Dial", "Your Most Played", "Echo Mode" |
| **Title Medium**| Satoshi | 14sp | Medium | 18sp | 0.1sp | Track names on lists and bento cards |
| **Body Standard**| Satoshi | 13sp | Regular| 18sp | 0.2sp | Descriptions, album titles, lyrics lines |
| **Caption** | Satoshi | 11sp | Medium | 15sp | 0.4sp | Artist names, time indicators, durations |
| **Micro Badge** | Satoshi | 10sp | Bold | 13sp | 1.0sp | Stream badges: "YOUTUBE", "SPOTIFY", "SOUNDCLOUD" |

---

## 4. True Liquid Glass & Backdrop Blur Architecture

`Modifier.blur()` in Compose only blurs an element's own contents. Real Liquid Glass requires **hardware-accelerated backdrop blur** where content scrolling beneath the glass is dynamically blurred in real time.

### 4.1 Implementation Technique: Haze Engine
- **Engine:** Chris Banes' **Haze** (`dev.chrisbanes.haze:haze`).
- **Mechanism:**
  1. A root `val hazeState = remember { HazeState() }` is scoped to the application shell.
  2. The scrollable content viewport (`ScreenHost` and LazyColumns) is registered with `Modifier.hazeSource(state = hazeState)`.
  3. The floating `RezonaBottomBar` and `RezonaMiniPlayer` are wrapped with `Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())`.
- **Hardware Acceleration (Android 12+ / API 31+):** Employs GPU `RenderEffect` pipelines with zero frame-drop at 60–120Hz.
- **Graceful Fallback (API 24–30):** Automatically drops down to a high-density dual-scrim gradient with specular borders, ensuring flawless performance without crashing older Android builds.

---

## 5. Navigation Shell: 4-Tab Floating Lens Dock

The bottom bar features **4 primary tabs**:
1. **Home** (Icon: Modern house outline)
2. **Search** (Icon: Precision optic lens)
3. **Studio** (Icon: Sliders / waveform audio lab)
4. **Library** (Icon: Stacked sound library discs)

### 5.1 Interactive Behaviors
- **Optical Lens Active Bubble:** The selected tab scales to `1.12x` inside a high-index frosted capsule with a specular white highlight border and a small `PureWhite` 3dp bead indicator resting underneath.
- **Horizontal Swipe Gestures:** Dragging left or right anywhere across the dock switches tabs with bouncy spring physics (`dampingRatio = 0.65f, stiffness = 380f`).
- **Scroll Auto-Folding:** Scrolling down on feeds collapses the dock into a sleek compact pill, snapping the Mini Player lower. Scrolling up unfolds the bar instantly.
- **Tablet / Large Screen:** Shifts automatically into a vertical `RezonaNavigationRail` (`width = 84.dp`) on screens `>= 600.dp`.

---

## 6. Comprehensive Feature Set & Screen Architecture

Rezona implements all signature features requested, without login, accounts, or extraneous third-party payment modals.

### 6.1 Feature Blueprint
1. **Source Blend:** Multi-source audio pipeline aggregating search, metadata, and streams from YouTube Music, Spotify, and SoundCloud into a unified playback engine.
2. **Echo Mode:** Virtual spatial reverb processor with 4 acoustic profiles (*Intimate Studio*, *Glass Hall*, *Cathedral*, *Deep Void*).
3. **Mood Dial:** Rotary touch control allowing users to dial between sonic energy states (*Chill*, *Deep Focus*, *High Energy*, *Night Resonance*).
4. **Waveform Scrubber with Sections:** High-precision canvas audio waveform seeking with annotated track segments (*Intro*, *Verse*, *Chorus*, *Drop*, *Outro*).
5. **Lyrics Studio:** Real-time word-by-word / timestamped lyrics runner + typography poster generator (exportable as Story 9:16 or Square 1:1 image cards).
6. **Smart Transitions:** Seamless crossfade engine (`0s - 12s`) and gapless audio track transitions.
7. **Time Capsule:** Chronological listening history log allowing users to relive sessions from specific days and hours.
8. **Focus Rooms:** Layered background generator blending procedural white noise, rain on glass, or vinyl crackle beneath music.
9. **Sessions:** Algorithmic non-stop continuous radio sessions based on acoustic seed tracks.
10. **Offline Smart Cache:** Automatic predictive audio caching of frequent tracks into local encrypted storage.
11. **Gesture Player:** Full-screen touch gestures (two-finger horizontal swipe for next/prev, vertical slide for volume, pinch to minimize).
12. **Sound Check:** Audio hardware route inspector displaying active output device (Bluetooth LDAC, USB-C DAC, Speaker) and stream sample rate/bit-depth.
13. **Queue Time Machine:** Bi-directional queue manager to pull previously played tracks back into current rotation.
14. **Daily Rezona:** Daily curated soundscape and discovery drops updated every 24 hours.

---

## 7. Strict Build Order & Step-by-Step Delivery

Per `desain.md`, implementation will proceed screen-by-screen in this exact sequence, with thorough self-review and zero compile errors at every milestone:

1. **Phase 1: Design Tokens, Core Primitives & Assets**
   - Package label update to **"Rezona"** in strings, manifest, and window title.
   - Replace app icons (`mipmap-*`) and splash drawables with the new 3-arc + dot Rezona logo.
   - Bundle **Satoshi** font family in `res/font/`.
   - Setup `RezonaTheme.kt`, monochrome color tokens, and Haze blur configuration.
2. **Phase 2: Navigation Shell & Floating Lens Dock**
   - `RezonaApp.kt`: Root atmosphere with desaturated artwork ambient glow (`HazeState` provider).
   - `RezonaBottomBar.kt`:
     - Frosted liquid glass capsule ("Navbar lensa") with beveled specular rim (`1.25.dp`).
     - 4 tabs: `Home` (House), `Search` (Optic lens), `Studio` (Waveform sliders), `Library` (Stacked discs).
     - Optical lens magnification (`1.12x`) on active tab with a `PureWhite` 3dp indicator bead beneath.
     - Horizontal swipe gestures across dock to switch tabs.
     - Scroll auto-folding: collapses downward on fast feed scroll down, unfolds on scroll up.
   - `RezonaNavigationRail.kt`: Responsive vertical dock for tablet screens (>= 600dp).
3. **Phase 3: Home Screen (`HomeScreen.kt`) — Exact Liquid Glass Blueprint**
   - **Header Bar:**
     - Left: Digital clock & time greeting + Large bold title **"Home"** (Pearl White, Satoshi Bold 28sp).
     - Right: 4 frosted glass circular action discs (Graphite `#1C1F26` with specular rim):
       1. `Time Capsule` (Clock/History icon)
       2. `Sound Check` (Equalizer audio levels icon)
       3. `Sessions` (People/Group listening icon)
       4. `Settings` (Precision gear icon)
   - **Mood Filter Row:**
     - Horizontally scrollable frosted liquid glass pills (`Chill`, `Focus`, `Party`, `Energy`).
     - Translucent background with `1.0.dp` specular border; active pill highlighted in Pearl White/Graphite.
   - **Speed Dial (3x3 Bento Grid):**
     - Exactly 8 square album art cards with rounded corners (12dp), track title beneath (Pearl White 13sp), playing track marked with subtle play badge.
     - 9th slot is a tactile **5-dot matrix card** (dice quincunx pattern) triggering Instant Mix / More.
     - 3 pagination pill indicators resting below the grid (first elongated/active in PureWhite, two inactive dots in SmokySilver).
   - **Community Stage ("From Community"):**
     - Wide liquid glass card with live session/performance cover art.
     - Two floating action buttons anchored on the right:
       - Frosted circular glass button with **Microphone icon** (Lyrics / Live sing-along).
       - Elevated squircle button with **Shuffle icon** (Instant shuffle drop).
   - **Daily Rezona:**
     - Curated 24h soundscape drop with audio waveform badge.
4. **Phase 4: Now Playing & Floating Mini Player**
   - `MiniPlayer.kt`:
     - Floating liquid glass capsule resting directly above the navbar lens.
     - Left: Rounded square album art thumbnail (42dp) with subtle desaturated glow.
     - Center: Track title (`PearlWhite`, Satoshi Bold 14sp) + Artist name (`SmokySilver`, Satoshi 12sp).
     - Right: Heart outline icon, circular frosted glass Play/Pause button, Skip Next icon.
     - Bottom edge: Continuous hairline progress bar (`2dp` height) indicating track elapsed time.
     - Tap gesture expands the full modal with fluid spring physics.
   - `ExpandedPlayerModal.kt`:
     - Full-screen liquid glass modal with real-time backdrop blur.
     - High-resolution album art with soft specular edge.
     - **Waveform Scrubber with Sections:** Interactive multi-segment canvas waveform with track annotations (Intro, Verse, Chorus, Drop, Outro).
     - **Lyrics Studio Runner:** Real-time synchronized lyrics + typography poster export generator.
     - **Gesture Controls:** Horizontal two-finger swipe (prev/next), vertical drag (volume), pinch down (dismiss).
     - **Sound Check Badge:** Active hardware output indicator (Bluetooth LDAC, USB-C DAC, Speaker) with bit-depth and sample rate.
5. **Phase 5: Search Screen (`SearchScreen.kt`)**
   - Top search capsule with clear and voice search trigger.
   - Source Blend pills: All, YouTube Music, Spotify, SoundCloud.
   - Quick Cards carousel.
   - "Your most played" numbered ranking (#1 to #6).
   - "Your favourite artists" circular avatar carousel.
6. **Phase 6: Studio Screen (`StudioScreen.kt`)**
   - The central acoustic workshop: Echo Mode spatial reverb, Focus Rooms ambient mixer, Sessions auto-mix launcher, Waveform Lab.
7. **Phase 7: Library Screen (`LibraryScreen.kt`)**
   - Filter pills (All, Playlists, Liked, Albums, Downloads).
   - Liked Songs hero card.
   - Offline Smart Cache status & Time Capsule history portal.
8. **Phase 8: Polish, Self-Review & Release Build**
   - Verify every screen: one clear focal point, text readability on glass, thumb-friendly touch targets (min 48dp), zero layout overflow.
   - Full Gradle build: `./gradlew assembleRelease --no-daemon` verification.
