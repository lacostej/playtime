# playtime (working title)

An Android app (iOS later) that gives parents a simpler way to manage today's play time and bedtime: one wheel for today's total, and a preview of exactly what will change before you confirm. Works with Nintendo Switch Parental Controls.

> This is an independent, unofficial project. It is not affiliated with, endorsed by, or sponsored by Nintendo Co., Ltd. "Nintendo" and "Nintendo Switch" are trademarks of Nintendo. This app communicates only with Nintendo's servers using your own account; use it at your own risk.

## Layout

- `shared/`: platform-independent Kotlin (planner, API client). Reused for iOS later.
- `androidApp/`: Android UI (to come).

## Build and test

```
./gradlew :shared:jvmTest
```

## Credits

API knowledge builds on the open-source [`pynintendoparental`](https://github.com/pantherale0/pynintendoparental) project.

## License

MIT, see `LICENSE`.
