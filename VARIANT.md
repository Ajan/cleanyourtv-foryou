# cleanyourtv-foryou

A variant of [CleanYourTV](https://github.com/Gathin23/cleanyourtv)'s launcher with a **For You** row and a few
remote and screensaver fixes for Google TV. The debloat tools, lists and everything else are CleanYourTV's, unchanged:
start with its [README](README.md) and [PROMPT.md](PROMPT.md).

Tested on a **Sony BRAVIA VU31** (Google TV 14, build UKR1.240726.001, India). Other Google TV sets should work but
haven't been tried.

## What this variant adds

| Feature | What it does | Turn it off |
|---|---|---|
| **For You** row above the dock | Two tabs, **Continue** and **Popular**, filled only from your **dock apps** (see below). OK opens that exact title or episode in its app. The big app name above the dock (`dock_title`) is off to make room. It shows with **0 rows below the dock**; with 1 to 3 rows (Launcher settings) the dock rises and the row hides itself rather than cover the clock and the top buttons. | `for_you` false (and `dock_title` true) in `config.xml` |
| **Screensaver starts after** | A row in Launcher settings (hold OK on the gear → Launcher settings): 1, 5, 10, 15 or 30 minutes. Google TV has no on-screen setting for this. | remove the row's code, or just don't use it |
| **Down in the screensaver** | Opens Ambient mode's own *Screensaver settings* panel, as the stock Google TV home did. | `dream_down_component` empty in `config.xml` |
| Remote buttons (Sony VU31 defaults) | `app_keys`: YouTube (201/202), Netflix (191), Prime Video (189) and the **Settings** button (83), which do nothing once the stock launcher is disabled. | edit `app_keys` for your remote |

### Where For You gets its cards

Everything comes from what your dock apps publish to Android TV itself; there is **no outside service, account or
API key**:

- **Continue**: Android TV's *Watch Next* list (in progress, next episode, new episode), newest first, with progress
  bars. On the test TV: Netflix, Prime Video and JioHotstar publish it; Apple TV and YouTube don't.
- **Popular**: each dock app's own recommendation row (for example Prime Video, JioHotstar's *Top Picks For You*,
  Apple TV, YouTube's *Recommended for you*), taken from each app in turn so no app fills the row. Netflix publishes
  no row. Duplicate rows are removed; YouTube Music is skipped.
- Change `dock_apps` and the tabs follow.

## Permissions and network use

The original launcher has no network access and no extra permissions, by design. This variant adds:

| Permission | Why | Granted by |
|---|---|---|
| `INTERNET` | Download the **artwork links the apps themselves publish**, only while Home is on screen; cached on the TV (at most 150 pictures). Nothing else is sent anywhere. | install |
| `READ_TV_LISTINGS` | Read Watch Next and the apps' rows on the TV | `variant-install.sh` (`pm grant`) |
| `WRITE_SETTINGS` | The *Screensaver starts after* row writes Android's screensaver delay | `variant-install.sh` (`appops`) |

## Install

1. Debloat and set up ADB with CleanYourTV's [PROMPT.md](PROMPT.md) or README, as usual.
2. Edit `launcher/app/src/main/res/values/config.xml`: at least `dock_apps` (your apps, in order) and, if you like,
   `greeting_name` (for "GOOD EVENING, <NAME>"). A theme's `config.json` can set `greeting_name` too, without a rebuild.
3. Build and install (JDK 17, Android SDK 34): `TV_HOST=<tv ip> ./variant-install.sh`
4. Switch USB debugging off again and restart the TV; `tools/adb-closed.py <tv ip>` should report the port closed.

## Known limits

- Opening a card needs the app to publish a link; every entry on the test TV did.
- Artwork needs internet; without it the cards show the app's name on its colour.
- On the test TV, holding volume moves one step per press while the remote helper is on: the key-repeat trade in
  [docs/devices/tcl-55p71b-g09-key-repeat.md](docs/devices/tcl-55p71b-g09-key-repeat.md).

## Staying up to date with CleanYourTV

This repository keeps CleanYourTV's full history. To bring in its updates:

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # once
git fetch upstream
git merge upstream/main          # resolve any conflicts in the launcher files, rebuild, test on the TV
```

The changed code is marked `Local change (not upstream)`: `ForYou.java`, `ArtCache.java`, `ForYouAdapter.java`,
`item_foryou.xml`, and sections of `MainActivity.java`, `InputKeyService.java` and `LauncherSettingsActivity.java`.

## Credits and licence

- [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin23 and its contributors: the debloat tools and the
  launcher this is built on. MIT, see [LICENSE](LICENSE).
- The idea of a Continue row from Watch Next and the apps' rows comes from
  [Talkies](https://github.com/rajupp/talkies) (MIT); the code here is written for this launcher.
- This variant is released under the same MIT licence.
