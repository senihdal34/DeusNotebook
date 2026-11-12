# NOTICE

A Deus Notebook (repository name `DeusNotebook`, application id `io.aragonite.notes`)

Copyright (C) 2026 senihdal34 and DeusNotebook contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
Public License, version 3 only, as published by the Free Software Foundation (SPDX: `AGPL-3.0-only`). It is distributed
in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

Complete Corresponding Source: <https://github.com/senihdal34/DeusNotebook>

This file says who the code comes from, under which licence, and what was changed. The same facts are shown inside the
app on the screen "About and legal notices" (Settings, and the menu of the Reading tab).

## 1. Where the code comes from

| Upstream project | Licence | What this project uses |
| --- | --- | --- |
| [Ethran/notable](https://github.com/Ethran/notable) (a maintained fork of [olup/notable](https://github.com/olup/notable)) | GPL-3.0 | The editor and note-taking core: `editor/`, `data/`, `gestures/`, `navigation/`, `ui/` (basic screens, dialogs, Settings), `utils/`, and the import/export code in `io/` (`ExportEngine`, `ImportEngine`, `XoppFile`, PDF rendering and similar). |
| [jdkruzr/aragonite](https://github.com/jdkruzr/aragonite) (a fork of Ethran/notable for Obsidian capture) | GPL-3.0 | The capture, tagging and sync parts: inbox sync, the AC01/SB1 container, Onyx handwriting recognition and its helpers (`io/InboxSyncEngine`, `io/Sb1ContainerWriter`, `io/OnyxHWREngine`, `io/Hwr*`, `io/SyncState`, `io/VaultTagScanner`), the annotation boxes and the tag UI. |
| [bookorbit/bookorbit](https://github.com/bookorbit/bookorbit) | AGPL-3.0 only, plus [additional terms](licenses/bookorbit/ADDITIONAL_TERMS.md) under AGPL section 7 | The design and logic of the Reading tab. See section 2. |

The Planner (`planner/`), the language support (`i18n/`), the new Home screens and the Reading tab's NeoReader data
layer were written for this project.

How the licences combine: GPL-3.0 section 13 and AGPL-3.0 section 13 allow GPL-covered code to be linked with
AGPL-covered code. The combined work is conveyed under the AGPL-3.0-only. The GPL-covered parts keep the terms of the
GPL-3.0 (the GPL text is kept in [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)), the BookOrbit-derived parts keep the
BookOrbit additional terms. New files written for this project are `AGPL-3.0-only`.

Original copyright notices of the upstream projects stay with their authors. This repository's history starts with a
snapshot, so the upstream contributors are named in the upstream repositories linked above, not in this history.

## 2. BookOrbit material

BookOrbit, Copyright (C) 2025-2026 neon. Additional copyright is held by the respective BookOrbit contributors.
Original project: <https://github.com/bookorbit/bookorbit>. BookOrbit is licensed under the GNU Affero General Public
License, version 3 only, with the additional terms in [licenses/bookorbit/ADDITIONAL_TERMS.md](licenses/bookorbit/ADDITIONAL_TERMS.md).
The upstream files are kept unchanged in [licenses/bookorbit/](licenses/bookorbit/): `LICENSE` (AGPL-3.0 text),
`NOTICE` and `ADDITIONAL_TERMS.md`.

Covered BookOrbit material used here, a Kotlin and Jetpack Compose port of BookOrbit's KOReader plugin and board
(`koreader-plugin/bookorbit.koplugin`):

- `app/src/main/java/com/ethran/notable/reading/board/`: the Board (grid layout, boxes, colour rules, line icons,
  decor widgets, gestures, the buddy cat drawing and its pixel art in `app/src/main/res/raw/buddy_cat.txt`).
- `app/src/main/java/com/ethran/notable/reading/badges/`: the badge catalogue and the rules for the next badge.
- `app/src/main/java/com/ethran/notable/reading/chapters/` and the other files of the `reading` package that follow
  the Board's design (the Kotlin files there carry an SPDX header that says so; `ReadingViewModel.kt` is the whole
package's glue code and is covered as well, its header follows with the next change to that file).

The files in that package start with:

```
// SPDX-License-Identifier: AGPL-3.0-only
// Reading board design derived from BookOrbit (https://github.com/bookorbit/bookorbit), AGPL-3.0-only with additional terms, see licenses/bookorbit/
```

What the additional terms require, and where this project does it:

| Requirement (ADDITIONAL_TERMS.md) | Done here |
| --- | --- |
| 1. A prominent About or Legal Notices feature in every interactive interface, with the notice text and a link on "Powered by BookOrbit" | Screen "About and legal notices" (`ui/views/AboutView.kt`), one tap from Settings (every tab) and from the menu button of the Reading tab. No account or admin right is needed. |
| 2. The complete Corresponding Source of the exact version, at no charge | The same screen shows the app version, the build number and the git commit, and links to <https://github.com/senihdal34/DeusNotebook> and to that commit. |
| 3. Modified versions: the statement and a modification date | The statement is in the screen and below. |
| 4. No BookOrbit name or logo as primary name or branding | The app is called "A Deus Notebook". "BookOrbit" appears only in the factual attribution. |
| 5. Keep ADDITIONAL_TERMS.md, the AGPL text, copyright notices and notices of modifications with dates in source distributions | `licenses/bookorbit/`, `LICENSE`, this file, and the SPDX headers in the source files. |

### Modification statement

This is a modified version of BookOrbit and is not an official BookOrbit release.

Modification date: 2026-11-12 (the date of this notice and of the About screen). The port of BookOrbit's board was
started on 2026-10-31 in the project's development repository; this public repository begins with a snapshot of it
(2026-11-12), and later changes are recorded commit by commit.

Main modifications: the board was rewritten from Lua (KOReader plugin) to Kotlin and Jetpack Compose; its data source
was changed from the BookOrbit server and KOReader's statistics to the content providers of NeoReader
(`com.onyx.kreader`) on Onyx Boox devices; the texts were localised (English, Turkish, Spanish, Chinese, Polish);
server-only boxes were replaced by local equivalents or omitted; the badge rules are computed locally.

## 3. Other licences

Libraries, fonts, icons and binary components used by the build are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The Onyx SDK is proprietary and is not covered by the AGPL; see that
file.

## 4. Trademarks

"BookOrbit" is not used as the name of this application. No trademark rights are granted by this file except the
factual attribution that the BookOrbit additional terms require.
