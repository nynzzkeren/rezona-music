# REZONA CORE (replaces rezona_master.md)

Re-read desain.md and design-system/MASTER.md. Activate skills: frontend-design, ui-ux-pro-max, mobile-design.
Stay Rezona: Liquid Glass, Satoshi, Haze, spring motion, English UI text. Cover art is the only source of color (plus one soft coral-pink heart accent when a track is liked).
NEVER: streaks or gamification, YouTube/Spotify account-cookie sign-in, donation modals, login (auth comes later), copying another app's branding. Music source is YouTube Music ONLY (no Spotify, no SoundCloud).
Build/install via adb if a device is connected, else give the APK path. Reproduce each bug yourself and tell me the root cause. After each stage: run lint, keep the R8 release build working, check smoothness on a low-end phone, touch targets >= 48dp, report files changed, real vs mock, and install result. Then STOP.

## STAGE 0: BUG CHECKLIST (verify each, fix what is still broken, report status per item)
0.1 BACKGROUND SEAM: ONE full-screen continuous ambient layer drawn behind everything, edge-to-edge (behind status bar and navigation bar), no clipped boxes and no separate top/bottom auras. Base dark gradient + 2-3 large soft radial gradients of the cover colors with long falloff and subtle noise to avoid banding. Crossfade 700ms between tracks. Shared by all screens so there is never a seam while scrolling or navigating.
0.2 LOGO BLACK BOX: in-app logo (loading, empty states, splash, headers) must be a transparent white glyph version of my logo, not an image with a black square. Launcher icon keeps its black adaptive background.
0.3 PLAYER STATE: PlayerController's current-track flow is the single source of truth for every surface (mini player, Now Playing, lyrics header, queue, notification, ambient background). No second mock state. Lyrics come from LRCLIB by title + artist + duration of the real playing track.
0.4 SEARCH: no flow {} emissions from child coroutines (use channelFlow/merge). Search must return full result lists (not 1 result), with timeouts, progressive results, stale-query cancellation, per-section errors with Retry.
0.5 EMPTY COVERS AND SKELETONS: Coil placeholder shimmer, crossfade, error fallback to a generated cover; thumbnails always in sync with the track; neutral shimmer skeletons.
0.6 NO MOCK DATA in release behavior, no hardcoded dates or labels. Debug screen shows REAL/MOCK badges and per-endpoint status.
0.7 End-to-end test: search, tap a result, audio plays, notification works, lyrics sync, Home sections real.

## LIQUID GLASS SYSTEM (applies to EVERY screen)
Rule: no flat black blocks with white text anywhere; no opaque #000/#111-type fills. Audit the whole app (Color.Black, solid surfaces, Material3 Card/Button/Switch/Slider/Checkbox/TextField/Dialog/Snackbar defaults) and replace them; report what you replaced.
GlassSurface variants: Floating (dock, mini player, headers, sheets: real Haze blur), Button, IconButton, Chip, Field, Toggle, Slider, Menu, Dialog, Toast. Layers:
- backdrop: real Haze blur only on large floating surfaces; small controls use "baked glass" (gradient tint + highlights, no per-item blur). Max 3 simultaneous blur layers; in Low intensity replace blur with a scrim gradient.
- tint: vertical gradient, white 8-14% at top to 2-5% at bottom, plus a faint influence of the cover colors.
- specular rim: 1dp gradient border, white 35-45% at the top fading to 5% at the bottom; thin inner highlight along the top edge; subtle inner shadow at the bottom; soft outer shadow.
- pressed state: scale 0.96 with spring, brighter tint and rim, quick release overshoot, haptic tick on primary actions.
- Custom glass Toggle, Slider (with percent bubble), Checkbox, TextField (focus glow), Dialog, Menu, Snackbar. Buttons are glass pills: primary = white-tinted glass with dark text, secondary = clear glass with light text.
- legibility: contrast >= 4.5:1 against the worst-case backdrop; soft scrim behind small text over busy covers.
- List content (rows, settings, library) is NOT boxed: plain rows on the ambient background with hairline separators; glass is for controls and floating surfaces only.
Add a hidden "Glass Gallery" debug screen showing every variant (default, pressed, disabled, focused) over colorful backdrops.