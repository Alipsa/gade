# Bash and Python Language Support

## Problem

Gade had first class tabs for Groovy, R, SAS, JavaScript, SQL and the various markup types, but
none for the two languages most likely to sit next to a Groovy analysis project: shell scripts and
Python. Opening a `.sh` or `.py` file fell through to the plain text tab, so there was no syntax
highlighting, no `#` comment toggling, and no way to run a build or release script without leaving
the application.

## Solution

Two new code types, `BASH` and `PYTHON`, added to [`CodeType`](../../src/main/java/se/alipsa/gade/code/CodeType.java),
each with its own tab package following the existing tab conventions.

### Bash

[`BashTab`](../../src/main/java/se/alipsa/gade/code/bashtab/BashTab.java) extends `ExecutableTab`,
so it gets the standard Run button, plus an `Args:` text field for the arguments to pass to the
script. Arguments are split on whitespace, with single or double quotes grouping an argument that
contains spaces.

[`BashTask`](../../src/main/java/se/alipsa/gade/code/bashtab/BashTask.java) runs the script as an
external process and streams its output to the console through the same appenders the JavaScript
tab uses, stdout as normal text and stderr as warnings:

```
bash -c <editor content> <script path> <args...>
```

Passing the content rather than the path means the editor buffer runs as it is, with no need to
save first. `bash -c` assigns the first argument after the script to `$0`, so `$0` is still the
script path and `$1` onwards are the arguments from the field.

Notes on the process handling:

- The script runs from Gade's working directory, the one the file tree points at, the same as a
  Groovy script does. The file tree changes it by setting the `user.dir` property
  ([`FileTree`](../../src/main/java/se/alipsa/gade/inout/FileTree.java)), which does not move the
  actual current directory of the JVM, so it has to be passed to the `ProcessBuilder` explicitly.
  A `ProcessBuilder` left to its own default would start the script in the Gade installation
  directory. [`RuntimeProcessRunner`](../../src/main/java/se/alipsa/gade/runtime/RuntimeProcessRunner.java)
  does the same thing for the Groovy subprocess.
- The child stdin is closed immediately after start. Nothing ever writes to it, so a script that
  calls `read` would otherwise block forever on a pipe that never receives anything.
- Output is decoded with the platform native encoding (`native.encoding`), which since JDK 18 is
  no longer the same as `Charset.defaultCharset()`.
- A non-zero exit code is reported in the console rather than raised as an error dialog, since
  exiting non-zero is a normal outcome for a shell script.
- Interrupting from the console destroys the process and its descendants.
- Any process still running when Gade shuts down is destroyed by a shutdown hook. A child process
  otherwise outlives the JVM that started it.

Python has no execution support. [`PythonTab`](../../src/main/java/se/alipsa/gade/code/pythontab/PythonTab.java)
is an editing tab only, the same as the R and SAS tabs.

### Editing

- `.sh` and `.bash` files open in a Bash tab, `.py` files in a Python tab, both by extension and
  by mime type, wired in [`FileOpener`](../../src/main/java/se/alipsa/gade/inout/FileOpener.java).
- `File -> New File` has Bash and Python entries.
- ctrl+shift+C toggles `#` line comments in both tabs.
- ctrl+Enter in a Bash tab runs the selection, or the whole script when nothing is selected. Unlike
  the Groovy and JavaScript tabs there is no session carrying state between runs, so running a
  single line in isolation would rarely do what the user means.

### Highlighting

Both highlighters are regex based, like the existing ones. Two cases needed care:

- Strings are matched so that an unclosed quote cannot swallow the rest of the file. Bash strings
  are kept single line, which is what makes the common `'\''` quote-escaping idiom safe. Python
  triple quoted strings do span lines, and are matched reluctantly so that a docstring containing
  a single `"` stays one string.
- Bash positional (`$1`) and special (`$#`, `$?`, `$$`, `$!`, `$@`, `$*`, `$-`) parameters are
  matched as variables. This matters beyond looks: without it the `#` of `$#` is picked up by the
  comment pattern and greys out the rest of the line.

## Testing

- `BashTextAreaTest` covers keyword, string, comment and variable styles, special and positional
  parameters, and three runaway-string regressions, including the real world `grep`/`sed` idiom in
  `src/test/resources/bash/releaseSnippet.sh`.
- `PythonTextAreaTest` covers the basic styles plus escaped quotes, docstrings containing quote
  characters, multi-line docstrings, and an unterminated `"""`.
- `BashTabTest` covers argument parsing, command construction, and the working directory
  resolution.

Run them with `./gradlew test --tests "se.alipsa.gade.code.bashtab.*" --tests "se.alipsa.gade.code.pythontab.*" -g ./.gradle-user`.

## Impact

Shell and Python files now open in a proper editor tab instead of the plain text tab, and shell
scripts can be run from Gade. Running a script requires `bash` on the `PATH`; on Windows without
it the Run action surfaces an error. Highlighting is regex based, so constructs that need real
parsing are approximated: heredoc bodies are highlighted as code, and Python f-string
interpolations are highlighted as ordinary string content.
