# cleanyourtv-foryou

A variant of [CleanYourTV](https://github.com/Gathin23/cleanyourtv)'s launcher with a **For You** row, a calmer
**home redesign** lightly inspired by Apple TV, and a few remote and screensaver fixes for Google TV. The debloat tools, lists and everything else are CleanYourTV's, unchanged:
start with its [README](README.md) and [PROMPT.md](PROMPT.md).

Tested on a **Sony BRAVIA VU31** (Google TV 14, build UKR1.240726.001, India). Other Google TV sets should work but
haven't been tried.

![Home on the Sony BRAVIA VU31 with the redesign: the greeting, clock and date on the left line, four round frosted buttons on the right (Inputs, the green Private DNS shield, Wi-Fi, Settings), the Continue and Popular tabs in one capsule, the first Continue card lifted on the left line with its title and "S3 E5 · JioHotstar · 6 min left" below, a frosted dock of five dimmed app tiles, and the next row peeking in at the bottom, all over the focused card's artwork, heavily blurred and darkened](docs/variant/home.jpg)

*Home on the test TV, November 2026, with the [home redesign](#the-home-redesign): the first Continue card is lifted
on the left alignment line, its artwork fills the background, heavily blurred and darkened, and the details show the
episode, the app and the time left. Also visible: the round **Private DNS shield** next to Wi-Fi, the frosted dock
with dimmed tiles, and the next row of apps peeking in at the bottom.*

## What this variant adds

| Feature | What it does | Turn it off |
|---|---|---|
| **For You** row above the dock | Two tabs, **Continue** and **Popular**, filled only from your **dock apps** (see below). OK opens that exact title or episode in its app. The big app name above the dock (`dock_title`) is off to make room. It shows with **0 rows below the dock**; with 1 to 3 rows (Launcher settings) the dock rises and the row hides itself rather than cover the clock and the top buttons. While a card is focused, its artwork, softly blurred and darkened, fills the background behind Home; it fades back to the wallpaper when you leave the row. | `for_you` false (and `dock_title` true) in `config.xml` |
| **Home redesign** | A calmer, darker home: Inter type on fixed alignment lines, round frosted top buttons, a Continue / Popular capsule, cards that lift when focused (no outlines), a frosted dock of 4 to 7 dimmed tiles, the focused card's art as a heavily blurred backdrop, and soft, interruptible motion. Details and how it was tested: [The home redesign](#the-home-redesign). | `redesign` false in `config.xml` |
| **Screensaver starts after** | A row in Launcher settings (hold OK on the gear → Launcher settings): 1, 5, 10, 15 or 30 minutes. Google TV has no on-screen setting for this. | remove the row's code, or just don't use it |
| **Update bell** in the top bar | A round bell, styled like the other top buttons, appears left of Inputs only while the Play Store has app updates waiting (with the stock launcher disabled nothing else shows them). OK opens them in the Play Store. | `update_bell` false in `config.xml` |
| **Private DNS shield** in the top bar | Only when Private DNS is in strict mode (for example `dns.adguard-dns.com`): a round shield left of the network pill. **Green**: a fresh name lookup answers. **Amber**: still waiting. In strict mode lookups hang for about a minute after the TV wakes from standby (measured on the test TV), and an app opened then can stay on "Can't connect" until it is force-stopped. **Red**: no answer for two minutes. Status only (focus skips it) unless installed with `DNS_SWITCH=1`: then OK on it offers **Pause for 30 minutes** (AdGuard comes back by itself; the menu shows "Paused until 11:51 PM"), **Turn off** and **Turn on**, and while off it stays as a grey shield. Otherwise hidden with Private DNS off or automatic. | `dns_shield` false in `config.xml` |
| **Remote app buttons** row in Launcher settings | **On**: the remote's YouTube, Netflix, Prime Video and Settings buttons work, and a held volume key moves one step per press (Sony drops key repeats while the helper filters keys). **Off**: holding volume ramps again (on the test TV, Volume + held 3 s went 22 to 39), and those buttons do nothing. Takes effect at once, and switching it Off shows a short message saying which buttons stop working; a theme push resets it to `key_filter`. | don't use it; `key_filter` sets the default |
| **Wi-Fi menu** | OK on the Wi-Fi icon opens a small glass menu, like Inputs, instead of going straight to Settings: connected or not, the band (2.4 / 5 GHz), signal strength and whether the internet works, then **Wi-Fi Settings…**. Without Location on, Android hides the network's name and the nearby networks from apps, and no app may switch networks since Android 10, so choosing a network is still done in Wi-Fi Settings. | the pill's long press still opens Wi-Fi settings directly |
| **Down in the screensaver** | Opens Ambient mode's own *Screensaver settings* panel, as the stock Google TV home did. | `dream_down_component` empty in `config.xml` |
| Remote buttons (Sony VU31 defaults) | `app_keys`: YouTube (201/202), Netflix (191), Prime Video (189) and the **Settings** button (83), which do nothing once the stock launcher is disabled. | edit `app_keys` for your remote |

### The top-bar menus

| Private DNS shield (AdGuard on) | Paused for 30 minutes | Wi-Fi |
|---|---|---|
| ![The shield's glass menu under the green shield: Private DNS: dns.adguard-dns.com, Pause for 30 minutes, Turn off](docs/variant/dns-menu.jpg) | ![The grey shield's menu: Paused until 4:10 am, Turn on, Turn off](docs/variant/dns-paused.jpg) | ![The Wi-Fi menu: Connected · 5 GHz · Good signal, and Wi-Fi Settings…](docs/variant/wifi-menu.jpg) |

The shield's menu needs `DNS_SWITCH=1` at install (see Permissions below); without it the shield is a status light.

### Where For You gets its cards

Everything comes from what your dock apps publish to Android TV itself; there is **no outside service, account or
API key**:

- **Continue**: Android TV's *Watch Next* list (in progress, next episode, new episode), newest first, with progress
  bars. On the test TV: Netflix, Prime Video and JioHotstar publish it; Apple TV and YouTube don't.
- **Popular**: each dock app's own recommendation row (for example Prime Video, JioHotstar's *Top Picks For You*,
  Apple TV, YouTube's *Recommended for you*), taken from each app in turn so no app fills the row. Netflix publishes
  no row. Duplicate rows are removed; YouTube Music is skipped.
- Change `dock_apps` and the tabs follow.

## The home redesign

Switched on by `<bool name="redesign">true</bool>` in `config.xml`; with it false, Home is the For You layout above.

### How it was made

The redesign was built from a written spec, one step at a time, on its own branch, and checked on the test TV after
every step with screenshots before anything was committed:

- **One place for every size and colour.** All sizes are in px at 1920×1080 converted to dp for the TV's density, in
  `dimens_redesign.xml` and `colors_redesign.xml`; nothing is scattered through the code. No text is smaller than
  24 px (about 1.4 cm on a 50-inch screen, readable from a sofa about 3 m away).
- **What was kept.** The four top buttons keep their icons and behaviour (only their surface changed), the dark night
  look stays, and the next app row still peeks in at the bottom as the cue to scroll.
- **Nothing personal in the code.** The greeting name comes from settings, not from the code.
- **Honest data.** "22 min left" appears only when the app publishes the position and length; Popular shows the year
  or genre only when the app gives them. Nothing is made up.
- **Reduced motion respected.** With Android's *Remove animations* on, every move is instant: no lift, slide,
  crossfade, stagger or bounce.
- **Upstream's tests still pass** for the launcher code, resources and switch lists the redesign touches.

### What it changes

- **Layout and type:** alignment lines 128 px from each edge; the greeting, clock, tabs, focused card, title and dock
  all sit on the left one. Inter is bundled (no web fonts).
- **Top buttons and tabs:** 84 px round buttons in white at 14%; Continue and Popular in one capsule. Selected,
  focused and unselected tabs are told apart by fill and weight, never by an outline.
- **Content row:** 16:9 cards, 24 px apart. The focused card lifts (scale, shadow, a light sheen) on the left line
  and the cards slide under it, so the title never moves; the last card also reaches the line. The row fades out
  over the last 240 px before the right edge. Progress bars are inset and white on a dark track.
- **Dock:** a frosted panel between the alignment lines with 4 to 7 apps (Launcher settings); tiles shrink to fit,
  stay 5:3, and are dimmed (65%, half saturation) until focused.
- **Backdrop:** the focused card's art shrunk to 64 px wide, blurred, darkened to about 55% and graded with two
  gradients, all baked into one small bitmap, so faces and lettering can't be read.
- **Motion:** a 350 ms decelerating slide; the old card shrinks at once while the new one lifts 60 ms later and lands
  as the row settles; the title crossfades with a small rise; a new press retargets the slide from its current
  position and speed; held, the row slides steadily (two cards per repeat after a second) and only the card it stops
  on lifts; a bounce at either end; a 300 ms tab slide; and Continue reorders animated after you come back from an app.
- **Behaviour:** Home and wake land on the first Continue card; empty Continue opens on Popular; Back slides to the
  first card, or goes up from the dock; a long press on a card offers **Remove from Continue** (or **Hide from
  Popular**) and **Go to show**.

### How rendering and motion were tested on the TV

Everything below was measured on the test TV over ADB, with the TV awake on Home (the screensaver closed first) and
the same script each time, so the numbers before and after are comparable.

**Method**

- **Frame times:** `dumpsys gfxinfo com.gathin.tvhome reset`, then **10 presses of Right** along the row,
  0.6 s apart (one shell command, so the timing is the same each run), then `dumpsys gfxinfo … framestats`. Read:
  janky frames, the 50th / 90th / 99th percentile frame times, slow GPU command issue, missed vsync and slow UI
  thread counts, plus each frame's own time from the framestats block.
- **Idle:** gfxinfo reset, nothing pressed for 6 s, then the frame count. Home must draw **no** frames when idle.
- **Overdraw:** Android's *Debug GPU overdraw* (`debug.hwui.overdraw show`) with a screenshot, to see how many times
  each pixel is painted (none, blue 2×, green 3×, pink 4×, red 5× or more).
- **What is on screen:** the view hierarchy with bounds (`dumpsys activity top`), to find hidden full-screen views,
  backgrounds and layers.
- **Motion timing:** during testing, each finished slide logged its real duration (`logcat -s RdMotion`) to check
  that the row really used the intended 350 ms curve; that log line was removed before release.
- **Behaviour checks:** Back from a card and from the dock, a tab switch, *Remove animations* on (all three
  animation scales set to 0, then put back exactly as they were), and an empty Continue list.

**What was found** (before)

- Most of the screen was red in the overdraw view: under the content, the window background, the root background,
  two backdrop images and a gradient layer of two full-screen gradients were all drawn every frame.
- Dimmed cards and tiles (70% / 65% / 35% opacity) were each rendered into an offscreen buffer every frame.
- The right-edge fade used a full-height offscreen layer that reached well above and below the row.
- Shadows under the round buttons and a blurred shadow under the dock panel were large extra draws.
- The backdrop blur ran on the main thread when a card was focused.

**What was changed**

- The backdrop is one bitmap with the blur, darkening and gradients baked in, made on a background thread and cached
  per card; the cards either side of the focused one are made in advance. One view shows it; during a crossfade the
  old and new bitmaps are the only full-screen draws. The window and root backgrounds were removed.
- Cards and tiles tell Android their children don't overlap, so their dimming needs no offscreen buffer; card
  placeholders are removed once the art covers them; corners come from view outlines.
- The right-edge fade is one hardware layer the size of the row (a gradient with `DST_IN`), made once.
- The button and dock shadows were removed (they barely showed on the dark background).
- Card art is decoded at its largest shown size (236 px high) and uploaded to the GPU when it loads, not in the frame
  that first shows it.
- The title block has a fixed height, and all motion is translation and alpha, so nothing is laid out mid-animation.

**Results** (10 × Right)

| | Before | After the rendering fixes | After the motion retune |
|---|---|---|---|
| Janky frames | 93 of 216 (43%) | 8 of 268 (3.0%) | 7 of 268 (2.6%) |
| 50th / 90th / 99th percentile | 38 / 101 / 105 ms | 16 / 19 / 21 ms | 16 / 20 / 22 ms |
| Slow GPU command issue | 93 | 8 | 7 |
| Missed vsync | 48 | 0 | 0 |
| Slow UI thread | 26 | 0 | 0 |
| Frames drawn while idle (6 s) | 0 | 0 | 0 |

- **Overdraw after:** the background is drawn once (no colour), the card row and dock panel are blue, cards and
  tiles blue to green; only small spots (a tab's text, some peeking tiles) reach pink.
- **Slides** measured 354 to 369 ms for a 350 ms setting (the rest is frame rounding); two quick presses merged into
  one 362 ms slide.
- **Reduced motion:** no slides logged; moves were instant. **Empty Continue:** Home opened on Popular.

**Caveats**

- The "high input latency" count stays high in these runs because the presses are injected from the shell with
  older timestamps; real remote presses don't show it.
- About a third of frames during a slide still take 17 to 22 ms on this TV, so motion runs a little below 60 fps.
- After a long session of installs and tests the TV felt jittery until it was restarted; measure on a freshly
  restarted TV.
- Android can't blur what is behind a view, so the "frosted" button, tab and dock surfaces are plain translucent
  fills. The card shadow is one elevation shadow, not the two layers of the spec.

## Permissions and network use

The original launcher has no network access and no extra permissions, by design. This variant adds:

| Permission | Why | Granted by |
|---|---|---|
| `INTERNET` | Download the **artwork links the apps themselves publish**, only while Home is on screen; cached on the TV (at most 150 pictures). Nothing else is sent anywhere. | install |
| `READ_TV_LISTINGS` | Read Watch Next and the apps' rows on the TV | `variant-install.sh` (`pm grant`) |
| `WRITE_SETTINGS` | The *Screensaver starts after* row writes Android's screensaver delay | `variant-install.sh` (`appops`) |
| `WRITE_SECURE_SETTINGS` (opt-in) | Only with `DNS_SWITCH=1`: the shield's menu switches Private DNS (`private_dns_mode`). **Broad**: it allows changing any secure or global setting; the launcher uses it for nothing else. Upstream's install revokes it by default | `variant-install.sh` with `DNS_SWITCH=1` (`pm grant`) |
| Notification access | The update bell reads **only** the Play Store's "updates available" notifications; every other notification is ignored and nothing is stored | `variant-install.sh` (`cmd notification allow_listener`) |

## Install

1. Debloat and set up ADB with CleanYourTV's [PROMPT.md](PROMPT.md) or README, as usual.
2. Edit `launcher/app/src/main/res/values/config.xml`: at least `dock_apps` (your apps, in order) and, if you like,
   `greeting_name` (for "GOOD EVENING, <NAME>"). A theme's `config.json` can set `greeting_name` too, without a rebuild.
3. Build and install (JDK 17, Android SDK 34): `TV_HOST=<tv ip> ./variant-install.sh` (add `DNS_SWITCH=1` for the shield's Private DNS menu)
4. Switch USB debugging off again and restart the TV; `tools/adb-closed.py <tv ip>` should report the port closed.

## Known limits

- Opening a card needs the app to publish a link; every entry on the test TV did.
- Artwork needs internet; without it the cards show the app's name on its colour (with the redesign: a dark grey
  card).
- With the redesign, **Remove from Continue** only hides the card on this Home: apps don't let other apps edit their
  Watch Next entries, so the show is still in the app's own list. **Go to show** opens whatever link the app gives.
- On the test TV, holding volume moves one step per press while the remote helper filters keys (switch it with the Remote app buttons row): the key-repeat trade in
  [docs/devices/tcl-55p71b-g09-key-repeat.md](docs/devices/tcl-55p71b-g09-key-repeat.md).

- With Private DNS in strict mode, give the TV a moment after waking: the shield turns green when lookups work.
  If an app was opened too early and says it can't connect, force-stop it (Settings → Apps) instead of restarting.

## Staying up to date with CleanYourTV

This repository keeps CleanYourTV's full history. To bring in its updates:

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # once
git fetch upstream
git merge upstream/main          # resolve any conflicts in the launcher files, rebuild, test on the TV
```

The changed code is marked `Local change (not upstream)`: `ForYou.java`, `ArtCache.java`, `ForYouAdapter.java`,
`AppUpdates.java`, `UpdateWatchService.java`, `ic_bell.xml`, `DnsShield.java`, `ic_shield.xml`,
`item_foryou.xml`, and sections of `MainActivity.java`, `InputKeyService.java` and `LauncherSettingsActivity.java`.
The home redesign adds `Redesign.java`, `FadeRow.java`, `CardFrame.java`, `dimens_redesign.xml`,
`colors_redesign.xml` and the `rd_*` drawables, and changes parts of `ForYouBackdrop.java`, `AppAdapter.java`,
`DockDecoration.java`, `Config.java` and the layouts.

## Credits and licence

- [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin23 and its contributors: the debloat tools and the
  launcher this is built on. MIT, see [LICENSE](LICENSE).
- The update bell's idea comes from [Talkies](https://github.com/rajupp/talkies) and [Matinee](https://github.com/Jarvis322/matinee) (both MIT).
- The idea of a Continue row from Watch Next and the apps' rows comes from
  [Talkies](https://github.com/rajupp/talkies) (MIT); the code here is written for this launcher.
- [Inter](https://rsms.me/inter/) by Rasmus Andersson, bundled for the home redesign, under the SIL Open Font
  License 1.1 ([launcher/fonts/INTER-OFL.txt](launcher/fonts/INTER-OFL.txt)).
- This variant is released under the same MIT licence.
