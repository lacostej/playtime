# Design notes: the wheel screen

Distilled from the prototype iterations and the Android build, November 2026. The screenshots in `screenshots/` show each case (`scripts/screenshots.sh` retakes them).

## What the design addresses

Extending play time today means picking fixed amounts (5, 15, 30, 60 min) and reading back "Extended by X" plus a bedtime. That leaves several things to the parent:

- **Amounts:** 20 minutes takes two taps, odd amounts several.
- **Taking time back:** the only way down is removing all extra time, then working out what's left and adding it again.
- **Seeing the result:** nothing says "they can play N more minutes", or what a change does to today's total.
- **Bedtime:** after bedtime, or close to it, an extension may give less play than it says (see `BACKEND_ISSUES.md`), with nothing on screen to show it.
- **Freshness:** played time arrives late, and nothing shows how old it is.
- **One-day changes:** limits and bedtime are per weekday. A one-off day means changing the weekday and remembering to change it back. The app only covers the generous direction (see 11 below, and Limitations in the README).

## 1. One control for one question

- The screen answers "how much can they play today, and until when?" with a single wheel. Chips, −/+ buttons and a mode toggle were all tried and removed.
- The wheel shows **today's total** (played + still to play), in hours and minutes. Turning it changes the total. The part already played can't be taken back: the wheel stops there.
- **One turn = one hour**, like a parking app. More time means turning round again.
- **Touching never changes the value; only turning does** (relative drag from anywhere on the ring). Steps of 5 minutes, with a haptic tick.
- **Tap away from the wheel to reset it** to the current setting. A visible Reset button was tried and rejected as too heavy.

## 2. Show the parent's model, hide Nintendo's mechanics

- The screen speaks in "play time" and "bedtime". Cancels, carry-over clearing, splitting into 60/30/15/5 and the confirm protocol stay out of sight. They are in the activity log for troubleshooting.
- When the API needs a different number from what the parent means, show the parent's number. After bedtime, giving 15 min can need several hours on Nintendo's timer, but the card says "30 min → 45 min".
- Only send what the official app could send: the official amounts, and the bedtime option's choice.

## 3. Preview, then confirm

- Before anything is sent, a card shows **before → after** for the two things that matter, **Play time** and **Bedtime**, in aligned columns (label, now, after). Unchanged rows have no "after".
- "Today's limits" above the wheel says it once: everything here is for today only and ends at midnight. The weekday settings are not touched.
- A one-line summary sits right above the button: "● 25 min played ● 35 min to play".

## 4. Say only what is true

- No end time like "until 18:05": that only holds if they start now. Say "35 min to play", and add "before bedtime" when bedtime is what limits it.
- Played time comes from Nintendo with a lag. While they can play, and the console's last report is more than 15 minutes old, the header says "last report HH:MM". When they can't play, the lag doesn't matter and nothing is shown.
- After sending, read the result back from Nintendo and show what really happened. If it differs from the plan, say so.
- A refusal from Nintendo is shown in plain words, not as a generic error.

## 5. Each fact once

- The total appears on the wheel and in the card's before → after. The change (+10 min) appears only under the wheel number. Played / to play appear only in the summary line, which doubles as the colour key.
- The button label doesn't repeat the value: "Confirm" or "Stop time".

## 6. One colour code everywhere

| Meaning | Colour |
|---|---|
| More time | green (wheel change, card value, button) |
| Less time | orange (same three places) |
| Stops play, bedtime passed, 0 min left | red |
| Already played | brown segment of the ring |
| Minutes bedtime makes unplayable | faded green with a reddish border, drawn opaque so it looks the same on every lap |
| Bedtime | a moon on the ring, only where bedtime actually limits play and only on the lap shown |
| Moving bedtime | the moon's brown (card value, the line under the wheel) |
| Nothing to change | grey button |

## 7. Nothing jumps

- Space for "+X min" is reserved, so the number doesn't move when it appears.
- The line under the wheel has a fixed slot, whether it shows something or not.
- The confirmation ("✓ Done · checked with Nintendo") appears inside the button for a moment, instead of a message that pushes the layout.
- The refresh spinner has reserved space in the header.
- Values used for display are snapped, so labels don't flicker while dragging.

## 8. Ask before taking time away

- Stop time and reducing time ask for confirmation and say what will happen (a running game will be closed). The confirm button is red for Stop time and orange for Reduce.
- Adding time doesn't ask: it's reversible, and it's what parents do most.

## 9. Offer the fix where the problem shows

When bedtime cuts today's total (the faded part of the ring), a line under the wheel says so and offers the fix: "☾ Bedtime cuts 30 min · Move bedtime to 19:30".

- Tapping it, the moon or the faded part sets up the change: the card shows Bedtime 19:00 → 19:30 and the button turns into a green Confirm. Tap away to undo.
- It is only offered while the wheel is untouched. Turning the wheel already decides about bedtime.
- The time named in the link is the planned result, rounded to what the official amounts allow, so the preview holds no surprise.
- Rejected: wiggling the handle to reveal "Adjust bedtime". It's invisible, and it changes what the red Stop button does after an accidental touch.

## 10. Refresh gently

- The app refreshes when it comes to the foreground (if the data is over a minute old), every 2 minutes while open, and on pull-to-refresh. After a failure it waits 15 minutes before the next automatic try. These numbers follow the Home Assistant integration's polling.
- A small spinner shows a refresh; "Updated N min ago" says how old the data is, and turns orange after a failed refresh.

## 11. Keep it small and say what it can't do

- Limitation: no "stricter day" (earlier bedtime or lower limit just for today). The API only changes a day through extra time, so it would need a temporary weekday change plus a restore afterwards. Not decided.
- No developer view in the app. The activity log (About → Activity log) is the troubleshooting tool. It holds no tokens or device IDs.

## Open questions

- Should the wheel start from what they can really play (played + time left before bedtime) instead of Nintendo's timer? It matters after an after-bedtime grant, where the timer is much larger than the real play time.
- An explanation line when bedtime has passed ("Nintendo counts from 19:15, so 3 h 50 min are added; bedtime stops play at 23:05").
- Beyond one turn, the played (brown) segment isn't drawn on later laps.
- Accessibility: the wheel needs a non-drag way to change the value (e.g. a slider role stepping by 5 min).
