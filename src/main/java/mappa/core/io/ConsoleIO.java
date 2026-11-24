package mappa.core.io;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** The single owner of System.in for the whole program. */
public class ConsoleIO implements GameIO {
    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleIO() {
        this(System.in, System.out);
    }

    public ConsoleIO(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in);
        this.out = out;
    }

    @Override
    public String readLine() {
        try {
            return scanner.nextLine();
        } catch (NoSuchElementException e) {
            throw new InputClosedException("Console input closed");
        }
    }

    @Override
    public void print(String text) {
        out.print(text);
        out.flush();
    }
}
