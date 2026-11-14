# Changelog

## 0.3.0 (2026-11-14)

**Planner**
- New Month, Quarter and Year pages, with a Year / Quarter / Month / Week / Day zoom strip on every planner page that keeps your date in focus.
- Previous / This / Next controls in the header of the Month, Quarter and Year pages.
- "Focus" areas on those pages grow with a plus button. Small dots mark the days you wrote on.
- Landscape: planner pages are one fixed portrait sheet, shown centred and scaled, so ink lines up with the rows in both orientations. Ink written on earlier weeks is moved once, safely, to the new row positions (a copy of the database is kept first).
- Week and day mirroring keeps working for existing weeks.

**Notes**
- Writing activity card under the notes: a heatmap of the days you wrote, with 3 / 6 / 12 month and per-year views, streaks and days per week. Every cell has a visible outline.
- Note titles and excerpts are cleaned of broken characters. Exported Markdown image links open in Obsidian even for titles with `;` or Turkish letters.

**Editor**
- New tool rail on the left or right edge with a clear icon set, pen pop-up (type, colour, size), undo and redo, favorite and a more menu. It folds away while writing (can be turned off). The old top toolbar is gone.
- The pen list is simpler: ballpoint, fountain and highlighter. Colour is a setting of the pen. Notes written with the old pens look exactly as before.
- Highlighter strokes are always drawn under handwriting, also in thumbnails and exports.
- Capture pages have one Back and one Save & Exit. Fixed a thick black bar at the bottom of unscrolled pages and a pink guide line on landscape planner pages.

**Under the hood**
- Faster Home: tag lookups use two queries instead of two per topic. Fixed several refresh races.
- First-run page redesigned in the house style, with a language step.
- Update check, bug reports and releases point to this project.

## 0.2.0 (2026-11-12)

First public release.
