# playtime (working title)

An Android app (iOS later) that gives parents a simpler way to manage today's play time and bedtime: one wheel for today's total, and a preview of exactly what will change before you confirm. Works with Nintendo Switch Parental Controls.

![The interface in demo mode: the wheel, the change preview, and the confirmation dialogs](docs/screenshots/overview.png)

*Interface, work in progress.*

> This is an independent, unofficial project. It is not affiliated with, endorsed by, or sponsored by Nintendo Co., Ltd. "Nintendo" and "Nintendo Switch" are trademarks of Nintendo. This app communicates only with Nintendo's servers using your own account; use it at your own risk.

## Layout

- `shared/`: platform-independent Kotlin (planner, API client). Reused for iOS later.
- `androidApp/`: Android UI (to come).

## Build and test

```
./gradlew :shared:jvmTest
PLAYTIME_TOKEN_FILE=/absolute/path/to/session-token ./gradlew :shared:readOnlyCheck   # reads your account, never writes
```

If Nintendo starts rejecting requests with `invalid_headers`, the minimum app version went up: compare `APP_INTERNAL_VERSION` in `shared/.../config/NintendoApi.kt` with `MOBILE_APP_BUILD` in upstream `pynintendoparental/const.py`.

## Credits

API knowledge builds on the open-source [`pynintendoparental`](https://github.com/pantherale0/pynintendoparental) project.

## License

MIT, see `LICENSE`.
