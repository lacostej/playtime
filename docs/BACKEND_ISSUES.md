# Known backend behaviours and how the app handles them

What we observed through the parental-controls API on our own account, November 2026 (one console, weekday settings with a 0-minute allowance, bedtime alarm and forced termination on). Times in the examples are illustrative. The official app talks to the same server, so it shows the same results.

## Summary

| # | Behaviour | What the app does |
|---|---|---|
| 1 | Yesterday's extra time is added again | Cancels before the first grant of the day |
| 2 | An old bedtime comes back | Reads the result back and reports a mismatch |
| 3 | The bedtime preview can differ from the result | Same: trust the read-back, not the preview |
| 4 | After bedtime, an extension doesn't let them play | Grants enough to move bedtime to where play should stop |
| 5 | Close to bedtime, part of an extension is lost | Plans bedtime and minutes together, previews both |
| 6 | An add sometimes replaces the extension | Checks every step, re-plans once from what landed |
| 7 | HTTP 200 doesn't mean the change was made | Reads the response's `status` |
| 8 | Played time arrives late | Shows when the console last reported |
| 9 | Changes take a few seconds to show | Polls until the change has landed |

## 1. Yesterday's extra time is added again

Extra time granted on one day is added again on a later day, although the day starts with no extra time shown. Example: 40 min granted on Monday; on Tuesday morning, adding 5 min gives 45 min. The day's `extraPlayingTime` reads empty, so the carry-over can't be seen before it happens. It clears after about two days without extensions, and a cancel ("No Bonus Time") clears it at once.

**App:** the first grant of the day starts with a cancel. The planner also prefers cancel-and-re-add whenever keeping the current extension would grant more than asked.

## 2. An old bedtime comes back

Extending with "also extend bedtime" can move bedtime from a stale value from earlier days instead of today's setting, even right after a cancel. Examples: bedtime 19:00, +5 min with bedtime gave 23:50; after lowering a weekday's bedtime from 22:00 to 16:00, +22 min gave 22:22, and the console allowed play after 16:22. A later change to the weekday settings cleared the stale value once. Adding without the bedtime option leaves bedtime alone.

**App:** after every change it reads the extension back from Nintendo. If bedtime differs from the plan, it shows what really happened ("Nintendo applied something different from the plan…") rather than the plan. It does not retry in that case: a retry would build on the same stale base.

## 3. The bedtime preview can differ from the result

The update call's `nextStepDetail.estimatedBedtimeChanges` (shown by the official app as the "also extend bedtime" preview) is computed from today's setting, so it misses case 2.

**App:** same as 2. The app's own preview comes from its planner, and the read-back is the final word.

## 4. After bedtime, an extension doesn't let them play

Once bedtime has passed, an extension moves bedtime from the configured bedtime by the minutes added, not from now. At 21:20 with bedtime 19:15, +15 min gives bedtime 19:30: the extension is recorded but the console stays locked. Giving 15 playable minutes needs about 2 h 25 min, so that bedtime reaches 21:40.

**App:** when bedtime has passed, the planner grants the whole gap from bedtime to "now + what the parent asked", split into the official amounts. The card shows the parent's number ("Play time 30 min → 45 min"), not the much larger timer value Nintendo needs.

## 5. Close to bedtime, part of an extension is lost

Bedtime 19:00, at 19:30, +1 h: bedtime becomes 20:00, so 30 of the 60 minutes can be played. Before bedtime the same choice exists in the opposite direction: the minutes can be granted with or without moving bedtime.

**App:** the planner decides the bedtime option per step, so bedtime ends where play should stop, and the preview shows both "Play time" and "Bedtime" before anything is sent. When bedtime cuts today's total, the app offers "Move bedtime to HH:MM" (see `DESIGN.md`).

## 6. An add sometimes replaces the extension

Seen once: 0 → 25 min sent as cancel, +15, +5, +5. Each step returned 200; after the second +5 the extension read 5 min instead of 25. The only step that failed was an exact repeat of the one before it, sent about four seconds later. That fits "the server treats the add as set to X", which was also suggested in [pynintendoparental#144](https://github.com/pantherale0/pynintendoparental/issues/144), but one case doesn't prove it.

**App:** checks the result after every step. If only the extension is off, it plans again from what Nintendo shows and tries once more, and the activity log records it (`retrying once: Nintendo shows 5 min extra, expected 25`).

## 7. HTTP 200 doesn't mean the change was made

Update and confirm responses carry a `status`. Success is `TO_ADDED`, `TO_CANCELED`, `TO_INFINITY` or `SUCCESS`; `NO_EFFECT`, `OVERTIME_ERROR`, `DURING_LATE_NIGHT_ERROR` and `FAILED` come back with HTTP 200 too (values as listed in pynintendoparental#144).

**App:** logs every status, and stops at a failure status with a plain message ("Nintendo says play time can't be extended any more today.").

## 8. Played time arrives late

The console reports played time in roughly 10-minute steps while playing, and the last part can arrive long after play has stopped (35 of 40 minutes shown for over 20 minutes). `device.lastOnlineCheckedAt` isn't usable to tell how fresh the data is: it showed a date years old on an active console.

**App:** takes the console's last contact as the later of the daily summary's `lastUpdatedAt` and the settings' `synchronizedAt`. While they can play and that is more than 15 minutes old, the header says "last report HH:MM".

## 9. Changes take a few seconds to show

An add or cancel takes about 6 seconds to show in the settings, and the applied total (`fetchExtraPlayingTimeState.extendedPlayingTime`) lags behind that. Right after a cancel, the next update can get HTTP 409.

**App:** after each step it polls every 3 s (up to 30 s) until the duration has changed and the applied total agrees with it. A 409 is retried after 5, 10, then 20 s.

## Protocol notes

As observed, 2026-11:

- **Add before bedtime:** `updateExtraPlayingTime` `TO_ADDED` returns a `nextStepDetail` and changes nothing. `confirmExtraPlayingTime` (with `withBedtime`) applies it.
- **Add after bedtime:** the update returns no `nextStepDetail` and applies the change itself. Confirming as well adds it twice.
- **Cancel:** an update with `TO_CANCELED`, never confirmed.
- **Amounts:** the official app sends 5, 15, 30 or 60 minutes per step. The app splits any amount into those (25 = 15 + 5 + 5).
- **Required headers:** `X-Moon-Os` and `X-Moon-App-Internal-Version`. If requests start failing with `invalid_headers`, the minimum version went up: see the README.
