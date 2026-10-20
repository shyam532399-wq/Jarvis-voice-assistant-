# Jarvis Assistant -- Setup Wizard

Same onboarding flow as the Maya app you recorded: a 3-step setup wizard
(Gemini key -> permissions -> default assistant) plus a minimal home
screen after setup finishes.

## What's actually implemented
- Step 1: paste/test a Gemini API key (live network call to
  `generativelanguage.googleapis.com`, tells you if it works), stored
  encrypted with `EncryptedSharedPreferences`.
- Step 2: one "Allow all" button that requests mic, notifications (13+),
  camera, phone, contacts, SMS, location in one batch, with live
  pending/granted status per row. Continue unlocks once mic is granted,
  same as Maya's.
- Step 3: requests `RoleManager.ROLE_ASSISTANT` on Android 10+ so Jarvis
  shows up in Settings > Default apps > Digital assistant app.
- Setup state persists, so the app skips straight to the home screen next
  launch.

## Honest limitation
Being *selectable* as the default assistant (step 3) is not the same as
Maya's "launch on long-press home / side button" behavior -- that needs a
full `VoiceInteractionService` + `VoiceInteractionSessionService`, which
is a separate, bigger component and is **not** built yet. Right now the
app only opens by tapping its icon. Say the word if you want that built
next -- it's a real chunk of work, not a one-file add.

## Not included here (yet)
Wake word, Gemini voice chat loop, call answer/reject, open/close apps,
lock/unlock, volume, flashlight -- all the stuff from your earlier Jarvis
build. This zip is just the onboarding flow matching the video; wire your
existing logic into `HomeScreen.kt` where the Start/Stop button is.

## Build
Same as your other builds: push this to GitHub, Actions runs
`.github/workflows/build.yml`, download the `JarvisAssistant-debug-apk`
artifact.
