![MCPIDE](./docs/icon+name.png)
================
An IDE for writing mappings for MCP.

[![Build Status](https://travis-ci.org/octylFractal/MCPIDE.svg?branch=master)](https://travis-ci.org/octylFractal/MCPIDE)
![Powered by JavaFX](https://img.shields.io/badge/powered%20by-JavaFX-33cc66.svg)
[![GitHub release](https://img.shields.io/github/release/octylFractal/MCPIDE.svg)](https://github.com/octylFractal/MCPIDE/releases)

## Usage
To use MCPIDE, simply download the appropriate release for your platform.

Once you have opened it, you will need to open a project directory (`File > Open Project`).
MCPIDE will automatically re-open the most recent project on start.

MCPIDE will ask you for which MCP config and names to use on first project start. You will usually
want to use the latest available, and if you `Select From > Maven`, MCPIDE will suggest Maven
versions for download. It will handle decompiling Minecraft and loading the names zip. The
decompile process may take a few minutes, so please be patient.

### Saving
MCPIDE will not write mapping changes until you save. If you attempt to exit MCPIDE normally,
it will prompt you to save before quitting if you have made changes. You can also save manually
by going to `File > Save`.

### Main Window
The main window has three parts: the file tree, the editor pane, and the status bar.

#### File Tree
The file tree contains the current project's files. You can expand folders using the arrow if there are files.
There are currently no filters.

#### Editor Pane
The editor pane consists of tabs at the top, which represent the current file in the view.
Below the tabs is the contents of the current file, which **cannot** be edited. You can click on a mapping,
and then go to `Edit > Rename`, or use the associated shortcut. Type into the box that pops up, then hit 
`Enter` to submit it. All files will be refreshed to use the new mapping. There is no undo (yet)!

Re-nameable identifiers are marked with underlines.

#### Status Bar
Various status messages will appear here while async operations are occurring. If something isn't happening,
double-check that this is empty before reporting a bug.

### Viewing Replacements
You can view all replacements by going to `View > Exportable Mappings`. You can select mappings, then hit
`Delete` or `Backspace` to delete them (after confirmation). There is no undo (yet)!

### Exporting
To export to MCP commands, go to `File > Export`. This will export the commands to the project directory,
in a file titled `exported-commands.txt`.

If you would like to send these commands to MCPBot in a simple way, check out
[mcp-irc-export](https://github.com/octylFractal/mcp-irc-export).

TODO:
- Fix macOS menu being "java" in unbundled versions, dependent on [JDK-8091007](https://bugs.openjdk.java.net/browse/JDK-8091007)
