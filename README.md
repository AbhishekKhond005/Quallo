# Quallo

Quallo is an offline desktop planner for game development. Project Nightfall, a sample vertical-slice board, opens on first launch. Select **Project guide** in the sidebar for six hands-on lessons that take you from scoping a build through playtest and handoff.

## Install

Download the package for your system from [GitHub Releases](https://github.com/AbhishekKhond005/Quallo/releases).

| Platform | Package |
| --- | --- |
| Windows x64 | Run the MSI and open Quallo from Start. The ZIP contains a portable copy with `Quallo.exe`. |
| Debian or Ubuntu x64 | Install the DEB with `sudo apt install ./quallo_1.0.0_amd64.deb`, then launch Quallo from the application menu. |
| RPM-based Linux x64 | Install the RPM with your distro’s package manager. |
| Other Linux x64 distributions | Extract the Linux tarball and launch `Quallo/bin/Quallo` from the extracted folder. |

Every package includes its Java runtime. The Linux app uses the system's desktop libraries. The Windows installer is unsigned.

## Manage a game project

- Capture unplanned work in Inbox and move it onto a project board when it’s ready. Organize the work in workspaces, boards, and lists. Drag cards to move or reorder them. Use list settings for WIP limits and completion behavior.
- Track features, bugs, assets, playtests, and milestones. Cards support priorities, dates, estimates, labels, assignees, checklists, custom fields, dependencies, comments, and files.
- Switch between Board, Table, Calendar, Timeline, and Overview. Workspace Table shows work across boards. My work collects your assigned and watched cards and cards with checklist items assigned to you.
- Archive or restore work, turn boards and cards into reusable templates, and add rules that complete, reopen, or label cards when they enter a list.
- Export a board as CSV. Export or restore a ZIP workspace backup, including attachments, from Settings.

Quallo runs on one local profile. Assignees are planning labels for people on your project. Shared editing and cross-device sync are outside this release. Transfer a workspace between computers using its ZIP backup.

On Windows, the data folder is `%LOCALAPPDATA%\Quallo`. On Linux, it is `$XDG_DATA_HOME/quallo` or `~/.local/share/quallo`. The app copies attachments into that folder when you add them. The maximum size per attachment is 250 MiB.

## Build and test

Use a JDK 21 or newer. The source app has no third-party Java dependencies.

```sh
./scripts/build.sh
java -jar build/input/quallo.jar
./scripts/test.sh
xvfb-run -a -s "-screen 0 1600x1024x24" ./scripts/test.sh --desktop
```

On Windows, run `./scripts/build.ps1` from PowerShell. The packaging scripts are `scripts/package-linux.sh` and `scripts/package-windows.ps1`. Linux package builds use fakeroot and optionally rpm-build. Windows package builds use WiX 3. The GitHub desktop workflow builds and smoke-tests the native packages on both platforms before publishing a tagged release.

## Artwork

The built-in ImageGen tool created the sample [Nightfall cover](src/main/resources/quallo/nightfall.jpg). The prompt requested a cinematic 3:1 concept painting of a dark alien alpine valley, with a tiny orbital station and a pale lavender eclipse toward the right. It specified a quiet left side for the project title, a near-black and plum palette, and original indie game concept art with no lettering.
