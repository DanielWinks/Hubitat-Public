# Sonos Favorites — Using `loadFavorite*` from Rule Machine (RM)

This document shows how to call the Sonos Advanced `loadFavorite` / `loadFavoriteFull` commands from **Rule Machine**, how to pass literal parameters and variables, plus ready-to-use examples and common RM gotchas.

---

## Quick facts

- Recommended device in RM: **`Sonos Advanced Favorites`** (UI shortcuts + single-parameter helpers).
- `loadFavorite(<favoriteId>)` — quick play (replace queue + play).
- `loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)` — full control.
- Shortcut helpers (append, next, shuffle, queued, repeat-one, no-repeat, no-shuffle) exist for common flows.

---

## How to call these commands from Rule Machine

1. Open your Rule in Rule Machine and add an **Action**.
2. Choose **Control device** → pick `Sonos Advanced Favorites` (or a `Sonos Advanced Player` when necessary).
3. Select **Custom command** and choose the `loadFavorite*` command you want.
4. Fill the parameter fields in the order shown (use the exact allowed strings below).
5. Save the action and the Rule.

Important: `loadFavoriteFull` will show six fields in this order: `favoriteId`, `repeatMode`, `queueMode`, `shuffleMode`, `autoPlay`, `crossfadeMode`.

---

## Allowed parameter values (use these exact strings)

- repeatMode: `repeat all` | `repeat one` | `off`
- queueMode: `replace` | `append` | `insert` | `insert_next`
- shuffleMode: `on` | `off`
- autoPlay: `true` | `false`
- crossfadeMode: `on` | `off`

Note: `favoriteId` is a string (get it from `getFavorites()` or the device UI).

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

| Shortcut                        | Equivalent `loadFavoriteFull(...)`                |
| ------------------------------- | ------------------------------------------------- |
| `loadFavoriteAndAppend(favId)`  | `repeat all`, `append`, `off`, `false`, `on`      |
| `loadFavoriteNext(favId)`       | `repeat all`, `insert_next`, `off`, `false`, `on` |
| `loadFavoriteAndShuffle(favId)` | `repeat all`, `replace`, `on`, `true`, `on`       |
| `loadFavoriteQueued(favId)`     | `repeat all`, `replace`, `off`, `false`, `on`     |
| `loadFavoriteRepeatOne(favId)`  | `repeat one`, `replace`, `off`, `true`, `on`      |
| `loadFavoriteNoRepeat(favId)`   | `off`, `replace`, `off`, `true`, `on`             |
| `loadFavoriteNoShuffle(favId)`  | `repeat all`, `replace`, `off`, `true`, `on`      |

Use these directly in RM when they match your intent — they require only the `favoriteId` parameter.

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
- Several shortcut helpers (`loadFavoriteAndAppend`, `loadFavoriteNext`, `loadFavoriteAndShuffle`, `loadFavoriteQueued`, `loadFavoriteRepeatOne`, `loadFavoriteNoRepeat`, `loadFavoriteNoShuffle`) for common flows.

---

## Parameters (what they do)

- `favoriteId` — Sonos favorite identifier (string). Get IDs with `getFavorites()` on the `Sonos Advanced Favorites` device.

- `repeatMode` (one of):
  - `repeat all` — playlist repeats after finishing
  - `repeat one` — repeat the current track
  - `off` — no repeat

- `queueMode` (one of):
  - `replace` — clear current queue, load the favorite (default)
  - `append` — add the favorite to the end of the current queue
  - `insert` — insert the favorite into the queue (Sonos decides exact placement)
  - `insert_next` — insert so the favorite plays next (after current track)

- `shuffleMode`: `on` or `off` — whether shuffle is enabled for the loaded favorite.

- `autoPlay`: `true` or `false` — if `true`, playback will start once the favorite is loaded. Note: some services (e.g. Amazon Music) may ignore this flag — the driver applies a 3s autoplay workaround when necessary.

- `crossfadeMode`: `on` or `off` — enable/disable crossfade between tracks.

---

## Defaults (when you call the simple `loadFavorite(favoriteId)`)

- `repeatMode` = `repeat all`
- `queueMode` = `replace`
- `shuffleMode` = `off`
- `autoPlay` = `true`
- `crossfadeMode` = `on`

So `loadFavorite('<id>')` is equivalent to:

```
loadFavoriteFull('<id>', 'repeat all', 'replace', 'off', 'true', 'on')
```

---

## Shortcut commands — exact behavior

- `loadFavoriteAndAppend(favoriteId)`
  - repeat: `repeat all`, queue: `append`, shuffle: `off`, autoplay: `false`, crossfade: `on`
  - Effect: add the favorite to the end of the queue but do NOT start playback.

- `loadFavoriteNext(favoriteId)`
  - repeat: `repeat all`, queue: `insert_next`, shuffle: `off`, autoplay: `false`, crossfade: `on`
  - Effect: insert the favorite so it will play next (no autoplay).

- `loadFavoriteAndShuffle(favoriteId)`
  - repeat: `repeat all`, queue: `replace`, shuffle: `on`, autoplay: `true`, crossfade: `on`
  - Effect: replace queue, enable shuffle, start playback.

- `loadFavoriteQueued(favoriteId)`
  - repeat: `repeat all`, queue: `replace`, shuffle: `off`, autoplay: `false`, crossfade: `on`
  - Effect: replace the queue but do NOT start playback (useful for scheduling).

- `loadFavoriteRepeatOne(favoriteId)`
  - repeat: `repeat one`, queue: `replace`, shuffle: `off`, autoplay: `true`, crossfade: `on`
  - Effect: play the favorite immediately and repeat the current track.

- `loadFavoriteNoRepeat(favoriteId)`
  - repeat: `off`, queue: `replace`, shuffle: `off`, autoplay: `true`, crossfade: `on`
  - Effect: play once, no repeat.

- `loadFavoriteNoShuffle(favoriteId)`
  - repeat: `repeat all`, queue: `replace`, shuffle: `off`, autoplay: `true`, crossfade: `on`
  - Effect: ensure shuffle is off when playing the favorite.

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
