# Copy file or directory path

## Problem

The File view context menu could copy an item's name, but not its location on disk.

## Solution

The **copy path** action sits next to **copy name** and copies the selected file or directory's absolute path to the clipboard. See [DynamicContextMenu.java](../../src/main/java/se/alipsa/gade/inout/DynamicContextMenu.java).

## Testing

Run `./gradlew test -g ./.gradle-user`.

## Impact

Users can paste the full path into a terminal, script, or file dialog.
