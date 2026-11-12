# Getting your notes onto a computer (Syncthing, Obsidian, AI tools)

The app writes every note you finish (**Save & Exit**) as a plain Markdown file in one folder on the tablet. Share that folder with your computer and the notes show up in Obsidian, or in any tool that reads files, including AI assistants and agents. No account, no cloud: Syncthing copies the folder directly between your devices.

The same guide is in the app: **Settings → Sync**, and it is offered once on the first-run welcome screen.

## What lands in the folder

Default folder: `/storage/emulated/0/Handnotes` (Settings → General → *Vault inbox folder*; a path that starts with `/` is used as is, anything else is relative to `/storage/emulated/0/`).

| File | What it is |
| --- | --- |
| `<title>-<yyyy-MM-dd-HH-mm-ss>.md` | The recognized text, with front matter (`created`, `pages`, `source`, `tags`), `[[wiki links]]` and `#tags` found in the handwriting, and `![[page-N.jpg]]` image embeds. It is written last, so its arrival means the note is complete. |
| `jpg_archive/…-page-N.jpg` | A rendered image of every page. |
| `sb1_archive/…-page-N.sb1` | Every stroke with pressure and timing (the app's own binary format). |
| `glossary.txt` | Optional: one word per line that the handwriting recognizer should prefer (names, terms). Edit it on the computer, it syncs back. |

## Setup in five steps

1. **Choose the notes folder.** Keep the default or pick a folder inside your Obsidian vault on the tablet (Settings → General → *Vault inbox folder*, press Done).
2. **Install Syncthing on the tablet.** Syncthing-Fork from F-Droid (<https://f-droid.org/packages/com.github.catfriend1.syncthingfork/>) or Syncthing from Google Play. Open it and allow storage access.
3. **Share the folder from the tablet.** In Syncthing: *Folders → +*, choose the notes folder, then under *Sharing* add your computer with its device ID (on the computer: *Actions → Show ID*).
4. **Accept it on the computer.** Install Syncthing from <https://syncthing.net/downloads/>, accept the tablet and the folder, and choose where it lands: a folder inside your Obsidian vault, or any folder.
5. **Let your AI read it.** Point your AI tool or agent at that folder on the computer. The notes are ordinary `.md` files, so search, summaries and automations work without any plug-in. Obsidian shows them as normal notes, with the page images embedded.

Only devices you approve in Syncthing receive your notes.

## Tips

- **Nothing arrives?** Open a note and tap *Save & Exit*: only finished notes are exported. Settings → Sync shows how many notes were exported and when.
- **Writing language.** Recognition uses Settings → General → *Writing language*. Languages that are not on the tablet are marked *(download)*: the language packs are downloaded in Boox's own note editor (language button → *More languages*), and the app selects the language when it is installed.
- **Two-way edits.** The sync is a plain folder copy. If you edit a note's `.md` on the computer it syncs back to the tablet, but the app does not re-read it; re-saving the note on the tablet writes the export again.
