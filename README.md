# ClosetIQ

Nothing new — just what you already own. Your skin reading picks which forgotten
garment gets its day, and VTO shows it on you.

## Running it

Two processes. Backend first.

```bash
cd backend && npm install && npm start
```

Then run the app from Android Studio on an **emulator** (it reaches the backend at
`10.0.2.2:3000`). On a physical device, change `BACKEND_BASE_URL` in `app/build.gradle.kts`
to your Mac's LAN IP and add that IP to `app/src/main/res/xml/network_security_config.xml`.

Check the backend is up:

```bash
curl -s http://localhost:3000/health
```

## Gemma or YouCam

One line in `backend/.env`:

```
PROVIDER=gemma
```

`gemma` runs the local Ollama mock — free, no credits. `youcam` hits the real API once
`backend/providers/youCamProvider.js` is implemented. **Nothing in the Android app changes
either way**, and the YouCam credentials never leave the backend.

## The tests are the spec

```bash
./gradlew :app:testDebugUnitTest
```

31 of these fail right now. Every failure is a `TODO()` in `app/src/main/java/com/closetiq/android/domain/`.
Make them pass, in this order:

1. `RankDormantUseCase.dormancyScore` — smallest, start here
2. `PaletteEngine.buildPalette` + `paletteFit`
3. `SkinStateModifier.skinDayFit` + `explain`
4. `ScoreGarmentUseCase.invoke` — depends on all three
5. `DominantColor.dominantLab` — only needed for photographed items

Everything else is written. The seeded closet, the async task machine, the screens and
the provider swap all work today.

## Layout

```
app/src/main/java/com/closetiq/android/
  domain/    pure Kotlin, no Android imports — the TODOs live here
  data/      Room, Retrofit, image handling, repositories
  ui/        Compose screens + ViewModels
  AppContainer.kt   dependency injection, by hand
backend/
  providers/ gemmaMockProvider.js | youCamProvider.js
```
