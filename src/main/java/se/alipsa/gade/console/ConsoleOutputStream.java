package se.alipsa.gade.console;

import javafx.application.Platform;

import java.io.IOException;
import java.io.OutputStream;

public class ConsoleOutputStream extends OutputStream {
  ConsoleTextArea console;
  ConsoleComponent consoleComponent;
  StringBuilder buf;
  public ConsoleOutputStream(ConsoleComponent consoleComponent) {
    this.console = consoleComponent.getConsole();
    this.consoleComponent = consoleComponent;
    this.buf = new StringBuilder();
  }

  @Override
  public void write(int b) throws IOException {
    if (buf == null) {
      throw new IOException("This output stream is already closed");
    }
    char letter = (char)b;
    buf.append(letter);
    if (letter == '\n') {
      console.appendFx(buf.toString());
      buf.setLength(0);
      Platform.runLater(() -> consoleComponent.scrollToEnd());
    }
  }

  @Override
  public void close() {
    console.appendFx(buf.toString());
    buf = null;
    Platform.runLater(() -> consoleComponent.promptAndScrollToEnd());
    consoleComponent = null;
    console = null;
  }
}
