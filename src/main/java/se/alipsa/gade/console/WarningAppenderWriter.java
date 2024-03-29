package se.alipsa.gade.console;

import java.io.Writer;

public class WarningAppenderWriter extends Writer {

    private final ConsoleTextArea console;

    public WarningAppenderWriter(ConsoleTextArea console) {
        this.console = console;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        console.appendWarningFx(new String(cbuf, off, len));
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}