# DeusNotebook

**A handwriting notebook for Onyx Boox e-ink tablets.** Plan your week with a pen, capture thoughts as Markdown for
Obsidian, and keep an eye on your reading, all in one calm, black-and-white app built for a 10.3" e-ink screen.

No animation, no shadows, no gradients. Everything is drawn for high contrast and fast e-ink refresh.

<p align="center">
  <img src="docs/screenshots/01-planner.png" width="31%" alt="Weekly planner">
  <img src="docs/screenshots/03-reading-home.png" width="31%" alt="Reading board">
  <img src="docs/screenshots/04-reading-insights.png" width="31%" alt="Reading insights">
</p>

## What you can do with it

**Plan your week on paper that still thinks.**
The start page is a handwritten weekly planner: seven days as rows, five lines each, checkboxes you tick with the pen.
Tap a date to open that day's long to-do page. What you write on a day's first lines stays in sync with the week page.

**Capture a thought and send it to your vault.**
Write a page, tag it, press save. Handwriting recognition turns the strokes into text and the note lands in your
Obsidian inbox as Markdown with front matter, a rendered image of the page, and the full stroke data. Draw a box around a
word to turn it into a `[[wiki link]]` or a `#tag`.

**Be understood in two languages.**
A personal word list teaches the recogniser your names, projects and technical terms. Turkish and English mix freely
and Turkish letters (İ ı ş ğ ç ö ü) survive the whole trip, including file names and tags.

**Keep your notes in order.**
A newspaper-style Home shows your latest note large, the rest in a grid, topics as tabs, and favorites in their own tab.
Press and hold a note to pin it. Boox note names become topics automatically.

**See your reading at a glance.**
The Reading tab is a dashboard that reads the Boox reader's own library and reading log: what you are reading now,
your streak, minutes per day, a year heat map, when you read, reading records, a yearly goal, highlights and a small
reading buddy. Tap a book and it opens in the Boox reader at the page you left.

<p align="center">
  <img src="docs/screenshots/05-reading-library.png" width="45%" alt="Reading board, library page">
</p>

**A good pen and a quiet screen.**
Low-latency pen input through the Onyx Pen SDK, palm guard against a resting hand, undo and redo, selection, a ruler
tool and a pen button that switches tools.

## Everyday uses

- **Morning page.** Open the planner, write the day's three things, tick them off as the day goes.
- **Meeting notes.** One page per meeting, tagged with the project, saved straight into your vault.
- **Reading log without effort.** Read in the Boox reader as usual; the board keeps the streak and the yearly goal.
- **Research capture.** Annotate with boxes: link a person, tag a topic, and let the export turn it into graph links.
- **Review on the computer.** Notes arrive as Markdown, images and stroke files in one folder; sync it with Syncthing.

## Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and the Android SDK. The Onyx Maven repository is plain HTTP (`allowInsecureProtocol`), see
`settings.gradle`. Pen input only works on Onyx hardware; the rest runs on any Android 10+ device.
Run the unit tests with `./gradlew testDebugUnitTest`.

On first start grant file access. On Boox devices also long-press the app icon and choose "Unfreeze app" so the system
does not close it in the background.

## Sync to your computer

Notes are written to `Documents/primary/inbox` (changeable in Settings). Share that folder with
[Syncthing](https://syncthing.net) and point Obsidian at it. A step-by-step guide is in Settings, Sync.

## Credits

DeusNotebook stands on other people's work, and gratefully so.

- **[Ethran/notable](https://github.com/Ethran/notable)**: the open-source note-taking app for Boox devices this
  project grew from. The editor, stroke storage and most of the plumbing are theirs.
- **[jdkruzr/aragonite](https://github.com/jdkruzr/aragonite)**: the fork that turned Notable into an Obsidian
  capture tool (inbox export, tags, annotation boxes, stroke container). The export pipeline here comes from it.
- **[BookOrbit](https://github.com/bookorbit/bookorbit)**: the Reading tab follows BookOrbit's Board: its page layout,
  tiles, badges and look. BookOrbit is licensed under AGPL-3.0; the Reading code here is a Kotlin port of that design.
- [Onyx Pen SDK](https://github.com/onyx-intl), [Jetpack Compose](https://developer.android.com/jetpack/compose),
  [Coil](https://coil-kt.github.io/coil/).

## License

GNU General Public License v3.0, see [LICENSE](./LICENSE). The Reading tab is derived from BookOrbit (AGPL-3.0); keep
that in mind if you distribute a modified version.
