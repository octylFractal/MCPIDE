MCPIDE works as follows:

### Project Creation
1. Pick an MCP CSV release. Recent versions will be fetched from Forge's Maven 
server and offered for use, or a local zip can be selected.
2. Pick a corresponding Minecraft JAR. Recent versions will be fetched from
Forge's Maven server and offered for use, or a local jar can be selected.
3. Unpack and rewrite the MCP CSVs into SRG format, store those in the project
metadata.

### Project Initialization
1. Follow ForgeGradle's steps, decompile the JAR.
2. Store the decompiled SRG-name-only source.

### Project Usage
1. Apply the stored SRG-name mappings to the source upon opening a file.
2. Allow renaming SRG-name elements, and store the mappings into both the
original SRG-name mappings file, and a new "to be exported" file.
3. Allow exporting said file as MCPBot commands.