# macOS copy compatibility

## Problem

Text copied from Gade on macOS can paste into some applications but fail to paste into a terminal.

## Solution

File view name and path actions, the code editor's Command+C action, table copy actions, and connection name copy actions now write plain text through the macOS system clipboard via Java AWT. Other platforms continue to use JavaFX and RichTextFX clipboard handling. See [ClipboardUtils.java](../../src/main/java/se/alipsa/gade/utils/ClipboardUtils.java) and [UnStyledCodeArea.java](../../src/main/java/se/alipsa/gade/UnStyledCodeArea.java).

## Testing

Run `./gradlew test -g ./.gradle-user`. On macOS, copy a file path, code selection, and table cell, then paste each into Terminal and a text editor.

## Impact

Copied text should be available to applications that read the standard macOS plain text pasteboard format.
