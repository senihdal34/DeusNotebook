# Third-party notices

A Deus Notebook is licensed under the AGPL-3.0-only (see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md)). It uses the
components below, each under its own licence. This file lists what the build ships directly; transitive dependencies
are not listed one by one (run `./gradlew :app:dependencies` for the full tree). Licences were taken from each
artifact's published POM where one is available (checked 2026-11-12), otherwise from the project's own licence. Where a licence is Apache-2.0 the full text is in
[licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

Code taken from other apps (Notable, Aragonite, BookOrbit) is described in [NOTICE.md](NOTICE.md), not here.

## 1. Proprietary components: not covered by the AGPL

**Onyx SDK (Onyx International, Inc.)**

- `com.onyx.android.sdk:onyxsdk-device:1.3.2`, `onyxsdk-pen:1.5.1`, `onyxsdk-base:1.8.3`, loaded from Onyx's Maven
  repository (`http://repo.boox.com/repository/maven-public/`, see `settings.gradle`).
- `app/libs/onyxsdk-base-1.6.27.aar`, `onyxsdk-device-1.2.9.aar`, `onyxsdk-pen-1.4.1.aar`: older binary copies of the
  same SDK, inherited from the upstream Notable repository. The current `app/build.gradle` does not reference them
  (the `fileTree` line is commented out), but they are kept in the repository and must not be removed without checking
  the build.

These are proprietary binaries owned by Onyx International, Inc. They are **not** licensed under the AGPL-3.0 or any
open-source licence that this project knows of; their POM files name no licence, and this project has not found a
published licence text for them. They come with the SDK terms that Onyx publishes, if any. They are not part of this
project's Corresponding Source and are only linked, as upstream Notable and Aragonite already do. Anyone who
redistributes a build should check Onyx's terms. NeoReader and the Boox firmware services (handwriting recognition,
content providers) are separate Onyx apps that this app talks to at run time; they are not included.

**Firebase Analytics (Google LLC)**: `com.google.firebase:firebase-analytics-ktx` (BoM 33.16.0). Licence named in its
POM: Android Software Development Kit License (<https://developer.android.com/studio/terms.html>). Proprietary Google
SDK, inherited from upstream. `app/google-services.json` is the Firebase configuration file inherited with it.

## 2. Open-source libraries used by the app

| Component | Version | Licence |
| --- | --- | --- |
| AndroidX (core-ktx, lifecycle, activity-compose, fragment, navigation-compose, room, hilt-navigation-compose, graphics-core, input-motionprediction) | see `app/build.gradle` | Apache-2.0 |
| Jetpack Compose (ui, material, runtime, material-icons-extended) | `compose_version` in `build.gradle` | Apache-2.0 |
| Jetpack Ink (`androidx.ink:*`) | 1.0.0 | Apache-2.0 |
| Dagger Hilt (`com.google.dagger:hilt-android`) | 2.59.2 | Apache-2.0 |
| Kotlin standard library, kotlinx-serialization-json | 1.10.0 | Apache-2.0 |
| [Coil](https://coil-kt.github.io/coil/) (`io.coil-kt:coil-compose`) | 2.7.0 | Apache-2.0 |
| RxJava 2, RxAndroid | 2.2.21, 2.1.1 | Apache-2.0 |
| [Klaxon](https://github.com/cbeust/klaxon) | 5.6 | Apache-2.0 |
| Apache Commons Compress | 1.28.0 | Apache-2.0 |
| [lz4-java](https://github.com/yawkat/lz4-java) (`at.yawk.lz4`) | 1.10.3 | Apache-2.0 |
| [hiddenapibypass](https://github.com/LSPosed/AndroidHiddenApiBypass) (LSPosed) | 6.1 | Apache-2.0 |
| [MuPDF](https://mupdf.com) (`com.artifex.mupdf:fitz`, Artifex Software) | 1.26.10 | AGPL-3.0 (its POM names the GNU Affero General Public License; this project's own AGPL-3.0-only licence matches) |
| [Shipbook SDK](https://github.com/ShipBook/ShipBookSDK-Android) | 1.+ (resolves to the latest 1.x) | MIT |
| [jnanoid](https://github.com/aventrix/jnanoid) | 2.0.0 | MIT |
| [Compose Icons, Feather set](https://github.com/DevSrSouza/compose-icons) | 1.0.0 | MIT (the Feather icons themselves are MIT, Cole Bemis) |

Test-only libraries (JUnit 4, kotlin-test, AndroidX test, Room testing) are not shipped in the app.

MuPDF's AGPL requires that the Corresponding Source of the combined work is offered to users. This project does that
(see the About screen and [NOTICE.md](NOTICE.md)).

## 3. Fonts: SIL Open Font License 1.1

`app/src/main/res/font/` holds Noto fonts. They were taken from the KOReader Android app (per the comment in
`reading/board/BoardUi.kt`), where they are bundled under the same licence. Each font file names the SIL Open Font
License 1.1 in its own metadata (`http://scripts.sil.org/OFL`), and its copyright line in the font's name table is:

- `notosans_regular.ttf`, `notosans_bold.ttf`, `notosans_italic.ttf`, `notosans_bolditalic.ttf` (Noto Sans, version
  2.003): Copyright 2015 Google Inc. All Rights Reserved.
- `notoserif_regular.ttf`, `notoserif_bold.ttf`, `notoserif_italic.ttf`, `notoserif_bolditalic.ttf` (Noto Serif,
  version 2.003): Copyright 2015 Google Inc. All Rights Reserved.
- `notonaskharabic_regular.ttf` (Noto Naskh Arabic, version 2.001): Copyright 2019 Google LLC. All Rights Reserved.
- `notosansbengaliui_bold.ttf` (Noto Sans Bengali UI Bold, version 2.001): Copyright 2017 Google Inc. All Rights
  Reserved.
- `notosansdevanagariui_bold.ttf` (Noto Sans Devanagari UI Bold, version 2.001): Copyright 2015 Google Inc. All Rights
  Reserved.

This project did not modify the fonts. The licence text follows. The Noto fonts are free to use, embed and
redistribute with software; they may not be sold on their own.

```text
This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
http://scripts.sil.org/OFL

-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION AND CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

## 4. Icons: Lucide (ISC) and Feather (MIT)

The board's line icons (`reading/board/BoardIconData.kt`) are path data from [Lucide](https://lucide.dev), via the
BookOrbit plugin. Lucide is under the ISC licence; the icons it derived from Feather are under the MIT licence. Their
licence text:

```text
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

---

The following Lucide icons are derived from the Feather project:

airplay, alert-circle, alert-octagon, alert-triangle, aperture, arrow-down-circle, arrow-down-left, arrow-down-right, arrow-down, arrow-left-circle, arrow-left, arrow-right-circle, arrow-right, arrow-up-circle, arrow-up-left, arrow-up-right, arrow-up, at-sign, calendar, cast, check, chevron-down, chevron-left, chevron-right, chevron-up, chevrons-down, chevrons-left, chevrons-right, chevrons-up, circle, clipboard, clock, code, columns, command, compass, corner-down-left, corner-down-right, corner-left-down, corner-left-up, corner-right-down, corner-right-up, corner-up-left, corner-up-right, crosshair, database, divide-circle, divide-square, dollar-sign, download, external-link, feather, frown, hash, headphones, help-circle, info, italic, key, layout, life-buoy, link-2, link, loader, lock, log-in, log-out, maximize, meh, minimize, minimize-2, minus-circle, minus-square, minus, monitor, moon, more-horizontal, more-vertical, move, music, navigation-2, navigation, octagon, pause-circle, percent, plus-circle, plus-square, plus, power, radio, rss, search, server, share, shopping-bag, sidebar, smartphone, smile, square, table-2, tablet, target, terminal, trash-2, trash, triangle, tv, type, upload, x-circle, x-octagon, x-square, x, zoom-in, zoom-out

The MIT License (MIT) (for the icons listed above)

Copyright (c) 2013-present Cole Bemis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 5. Images and other assets

- `app/src/main/res/drawable/iris.jpg`: a background image ("Iris") offered in the page background selector. It was
  added by the upstream Notable project (commit "added migration file and defoult image for notebooks covers", April
  2025). **Its original source and licence are not documented anywhere that this project could find, so its provenance
  is unverified.** It is kept because the app offers it as a page background. If you know where it comes from, or want
  it removed, please open an issue.
- `app/src/main/res/drawable-nodpi/logo_deusnote.png` and the launcher icons in `app/src/main/res/mipmap-*`: artwork
  of this project. The launcher icons may descend from upstream Notable's icons; this has not been checked.
- `app/src/main/res/raw/buddy_cat.txt`: pixel art of the board's buddy cat, from BookOrbit (see
  [NOTICE.md](NOTICE.md)).
