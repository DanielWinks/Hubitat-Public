# Sonos Favorites — Using `loadFavorite*` from Rule Machine (RM)

This document shows how to call the Sonos Advanced `loadFavorite` / `loadFavoriteFull` commands from **Rule Machine**, how to pass literal parameters and variables, plus ready-to-use examples and common RM gotchas.

---

## Quick facts

- Recommended device in RM: **`Sonos Advanced Favorites`** (UI shortcuts + single-parameter helpers).
- `loadFavorite(<favoriteId>)` — quick play (replace queue + play).
- `loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)` — full control.
- Shortcut helpers (append, next, shuffle, queued, repeat-one, no-repeat, no-shuffle, no-crossfade, append-and-play, shuffle-no-repeat) exist for common flows.

---

## How to call these commands from Rule Machine

1. Open your Rule in Rule Machine and add an **Action**.
2. Choose **Control device** → pick `Sonos Advanced Favorites` (or a `Sonos Advanced Player` when necessary).
3. Select **Custom command** and choose the `loadFavorite*` command you want.
4. Fill the parameter fields in the order shown (use the exact allowed strings below).
5. Save the action and the Rule.

Important: `loadFavoriteFull` will show six fields in this order: `favoriteId`, `repeatMode`, `queueMode`, `shuffleMode`, `autoPlay`, `crossfadeMode`.

---

## Allowed parameter values (use these exact strings in Rule Machine)

### repeatMode
- `repeat all` — Loop the entire playlist/favorite when it finishes
- `repeat one` — Repeat the current track continuously
- `off` — Play through once and stop

### queueMode
- `replace` — Clear the current queue and load this favorite (most common)
- `append` — Add to the end of the current queue
- `insert` — Insert into the queue (Sonos decides where)
- `insert_next` — Insert as the next track to play

### shuffleMode
- `on` — Play tracks in random order
- `off` — Play in the original order

### autoPlay
- `true` — Start playing immediately after loading
- `false` — Load into queue but don't start playback (manual play later)

### crossfadeMode
- `on` — Smooth transitions between tracks
- `off` — Distinct separation between tracks

### favoriteId
A string identifier for your Sonos favorite (e.g., `'28'`, `'15'`). Get these IDs by running `getFavorites()` on your Sonos Advanced Favorites device and checking the device page or logs.

---

## Passing Rule Machine variables into the command

- Create a String variable (e.g. `favId`) earlier in your action sequence.
- In the `favoriteId` field click **Insert Variable** and select `favId`.
- You can insert variables for any parameter (e.g., `queueMode`, `repeatMode`).

Example (using a variable named `favId`):

- Action: Control device → Sonos Advanced Favorites → `loadFavorite`
  - `favoriteId` = (insert variable) `favId`

---

## RM examples (copy/paste logic)

1. Play a favorite now (replace queue + play)

- Action: Control device → `Sonos Advanced Favorites` → `loadFavorite`
  - `favoriteId` = `<FAV_ID>`

2. Insert a favorite so it plays next (no autoplay)

- Action: Control device → `Sonos Advanced Favorites` → `loadFavoriteFull`
  - `favoriteId` = `<FAV_ID>`
  - `repeatMode` = `repeat all`
  - `queueMode` = `insert_next`
  - `shuffleMode` = `off`
  - `autoPlay` = `false`
  - `crossfadeMode` = `on`

3. Variable-driven playlist selector

- Action 1: Set variable `favId` = `spotify:playlist:...` (or selected from conditions)
- Action 2: Control device → `Sonos Advanced Favorites` → `loadFavorite` → `favoriteId` = `favId`

4. Full-control example (replace queue, no autoplay)

- Control device → `Sonos Advanced Favorites` → `loadFavoriteFull` with params:
  - `favoriteId` = `<FAV_ID>`
  - `repeatMode` = `off`
  - `queueMode` = `replace`
  - `shuffleMode` = `off`
  - `autoPlay` = `false`
  - `crossfadeMode` = `off`

---

## Shortcut commands (what they do in RM)

Each shortcut is equivalent to calling `loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)` with specific values pre-configured:

| Shortcut Command | Repeat Mode | Queue Mode | Shuffle | Auto-Play | Crossfade | Use Case |
|-----------------|-------------|------------|---------|-----------|-----------|----------|
| `loadFavoriteAndAppend(favId)` | repeat all | **append** | off | **no** | on | Add to end of queue without interrupting current playback |
| `loadFavoriteNext(favId)` | repeat all | **insert_next** | off | **no** | on | Queue to play after current track finishes |
| `loadFavoriteAndShuffle(favId)` | repeat all | replace | **on** | yes | on | Play now with shuffle enabled |
| `loadFavoriteQueued(favId)` | repeat all | replace | off | **no** | on | Load into queue but don't start playing (for scheduling) |
| `loadFavoriteRepeatOne(favId)` | **repeat one** | replace | off | yes | on | Play and repeat the current track |
| `loadFavoriteNoRepeat(favId)` | **off** | replace | off | yes | on | Play once through without repeating |
| `loadFavoriteNoShuffle(favId)` | repeat all | replace | **off** | yes | on | Explicitly ensure shuffle is disabled |
| `loadFavoriteNoCrossfade(favId)` | off | replace | off | yes | **off** | Podcasts, audiobooks, or any spoken content (no track overlap) |
| `loadFavoriteAppendAndPlay(favId)` | repeat all | **append** | off | **yes** | on | Add to queue and start playing (party mode - queue grows as you play) |
| `loadFavoriteShuffleNoRepeat(favId)` | **off** | replace | **on** | yes | on | Shuffle playlist/album but play through only once |

**Bold values** show what makes each shortcut unique. All shortcuts require only the `favoriteId` parameter, making them perfect for Rule Machine where you want simple, one-click actions.

---

## Troubleshooting & best practices for RM

- Getting `favoriteId`: run **getFavorites()** on the `Sonos Advanced Favorites` device and copy the ID from the UI or logs.
- Amazon Music may ignore `autoPlay`; the driver will attempt an automatic retry, but you can add a small **Wait** then a `playerPlay()` action as a fallback.
- When controlling groups, call the **group** device or the coordinator — follower devices will delegate automatically.
- When using variables, make sure the RM variable is a **String**.
- If playback doesn't start, add a short `Wait` (2–5s) before checking `transportStatus` or calling `playerPlay()`.

---

## Want a ready-to-import RM example?

I can add a sample Rule Machine export json or a screenshot that you can import/use — tell me which you prefer and I’ll add it to this README. ✨

## At-a-glance ✅

- `loadFavorite(favoriteId)` — default, replaces queue and starts playback.
- `loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)` — full control over how a favorite is loaded.
- Several shortcut helpers (`loadFavoriteAndAppend`, `loadFavoriteNext`, `loadFavoriteAndShuffle`, `loadFavoriteQueued`, `loadFavoriteRepeatOne`, `loadFavoriteNoRepeat`, `loadFavoriteNoShuffle`, `loadFavoriteNoCrossfade`, `loadFavoriteAppendAndPlay`, `loadFavoriteShuffleNoRepeat`) for common flows.

---

## Parameter Reference — Detailed Explanations

### favoriteId (required)
Your Sonos favorite's unique identifier — a number string like `'28'` or `'15'`.

**How to find it**: Run the `getFavorites()` command on your `Sonos Advanced Favorites` device, then check the device page. You'll see all your favorites listed with their IDs.

### repeatMode
Controls what happens when the favorite finishes playing:
- **`repeat all`** — The entire playlist/album loops continuously
- **`repeat one`** — The current track repeats over and over (useful for meditation tracks, kids' songs)
- **`off`** — Plays through once then stops

### queueMode
Determines how the favorite is added to Sonos:
- **`replace`** *(most common)* — Clears whatever's currently playing and loads this favorite
- **`append`** — Adds to the end of the queue without stopping current playback (builds a playlist)
- **`insert`** — Adds to the queue (Sonos picks the insertion point based on internal logic)
- **`insert_next`** — Inserts so this favorite plays after the current track finishes

### shuffleMode
- **`on`** — Plays tracks in random order (great for playlists)
- **`off`** — Plays in the original sequence (important for albums)

### autoPlay
- **`true`** — Starts playing immediately once the favorite loads
- **`false`** — Loads the favorite into the queue but waits for manual play

**Note**: Some streaming services (especially Amazon Music) ignore the autoPlay flag. The driver detects this and automatically schedules a play command 3 seconds later as a workaround.

### crossfadeMode
- **`on`** — Smooth, overlapping transitions between tracks (no silence)
- **`off`** — Distinct separation between tracks (traditional album experience)

---

## Defaults (when you call the simple `loadFavorite(favoriteId)`)

When you use the basic `loadFavorite(favoriteId)` command without specifying options, these defaults apply:

- **repeatMode** = `repeat all` — playlist will loop after finishing
- **queueMode** = `replace` — clears current queue and loads this favorite
- **shuffleMode** = `off` — plays in original order
- **autoPlay** = `true` — starts playing immediately
- **crossfadeMode** = `on` — smooth transitions between tracks

**In other words**, calling:
```groovy
loadFavorite('28')
```

is exactly the same as calling:
```groovy
loadFavoriteFull('28', 'repeat all', 'replace', 'off', 'true', 'on')
```

This gives you a quick "play now" command while still having access to full control when you need it.

---

## Shortcut commands — exact behavior

### `loadFavoriteAndAppend(favoriteId)`
- **Parameters**: repeatMode=`repeat all`, queueMode=`append`, shuffleMode=`off`, autoPlay=`false`, crossfadeMode=`on`
- **Effect**: Adds the favorite to the end of the current queue without interrupting playback
- **When to use**: Building a playlist while music is playing

### `loadFavoriteNext(favoriteId)`
- **Parameters**: repeatMode=`repeat all`, queueMode=`insert_next`, shuffleMode=`off`, autoPlay=`false`, crossfadeMode=`on`
- **Effect**: Inserts the favorite to play after the current track finishes
- **When to use**: "Play this next" without stopping current track

### `loadFavoriteAndShuffle(favoriteId)`
- **Parameters**: repeatMode=`repeat all`, queueMode=`replace`, shuffleMode=`on`, autoPlay=`true`, crossfadeMode=`on`
- **Effect**: Replaces queue, enables shuffle, and starts playback immediately
- **When to use**: Playing a playlist/album in random order

### `loadFavoriteQueued(favoriteId)`
- **Parameters**: repeatMode=`repeat all`, queueMode=`replace`, shuffleMode=`off`, autoPlay=`false`, crossfadeMode=`on`
- **Effect**: Loads the favorite into the queue but doesn't start playing
- **When to use**: Scheduling music to be ready (press play manually later)

### `loadFavoriteRepeatOne(favoriteId)`
- **Parameters**: repeatMode=`repeat one`, queueMode=`replace`, shuffleMode=`off`, autoPlay=`true`, crossfadeMode=`on`
- **Effect**: Plays the favorite and repeats the current track continuously
- **When to use**: Looping a single song (e.g., kids' music, meditation tracks)

### `loadFavoriteNoRepeat(favoriteId)`
- **Parameters**: repeatMode=`off`, queueMode=`replace`, shuffleMode=`off`, autoPlay=`true`, crossfadeMode=`on`
- **Effect**: Plays through the favorite once and stops
- **When to use**: Playing an album start-to-finish without repeating

### `loadFavoriteNoShuffle(favoriteId)`
- **Parameters**: repeatMode=`repeat all`, queueMode=`replace`, shuffleMode=`off`, autoPlay=`true`, crossfadeMode=`on`
- **Effect**: Explicitly ensures shuffle is disabled when playing
- **When to use**: When you want to guarantee album/playlist plays in original order

### `loadFavoriteNoCrossfade(favoriteId)`
- **Parameters**: repeatMode=`off`, queueMode=`replace`, shuffleMode=`off`, autoPlay=`true`, crossfadeMode=`off`
- **Effect**: Plays with no track overlap and no repeat (clean separation between tracks)
- **When to use**: Podcasts, audiobooks, talk radio, or any spoken content where crossfade would be confusing

### `loadFavoriteAppendAndPlay(favoriteId)`
- **Parameters**: repeatMode=`repeat all`, queueMode=`append`, shuffleMode=`off`, autoPlay=`true`, crossfadeMode=`on`
- **Effect**: Adds to the end of queue and starts playing if nothing is currently playing
- **When to use**: Party mode - guests adding songs that should start immediately if the queue is empty

### `loadFavoriteShuffleNoRepeat(favoriteId)`
- **Parameters**: repeatMode=`off`, queueMode=`replace`, shuffleMode=`on`, autoPlay=`true`, crossfadeMode=`on`
- **Effect**: Plays through the favorite in random order once, then stops
- **When to use**: "Shuffle this album once" - variety without endless looping

---

## Notes & implementation details 🔧

- Grouped players: follower devices delegate to the group coordinator — the coordinator performs the actual `loadFavoriteFull` operation.
- Retry behavior: when `autoPlay`/`playOnCompletion` is enabled the driver will attempt retries if playback doesn't start — retry intervals: **2s, 5s, 10s, 30s**.
- Service quirks: some streaming services ignore `playOnCompletion` (Amazon Music is a known example). The driver detects this and schedules a manual play as a workaround.
- `queueMode` strings are case-insensitive; the player driver upper-cases the action before sending it to Sonos.

---

## Examples

- Play favorite now (replace queue + play):

```
device.loadFavorite('<FAV_ID>')
```

- Add a favorite to the end of the queue without starting playback:

```
device.loadFavoriteAndAppend('<FAV_ID>')
```

- Insert a favorite so it plays next (no autoplay):

```
device.loadFavoriteNext('<FAV_ID>')
```

- Load a favorite with explicit options (full control):

```
device.loadFavoriteFull('<FAV_ID>', 'repeat all', 'append', 'off', 'false', 'on')
```

---

## Where to call these

- `Sonos Advanced Player` device (`SonosAdvPlayer`)
- `Sonos Advanced Favorites` device (`SonosAdvFavorites`) — UI shortcuts available
- From Rule Machine / WebCore / custom apps by invoking the device command.

---

If you want, I can add this README to the `Readme/` folder or a driver-level README next to `SonosAdvFavorites.groovy`. Which location do you prefer? 💡
