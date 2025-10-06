// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Spawns a <code>vespa-index-inspect</code> process and creates a stream of {@link TermDocumentFrequency} rows from its
 * stdout.
 *
 * @author johsol
 */
public record VespaIndexInspectClient(String executable, ProcessStarter processStarter) {

    /**
     * Use default executable name on PATH.
     */
    public VespaIndexInspectClient() {
        this("vespa-index-inspect");
    }

    VespaIndexInspectClient(String executable) {
        this(executable, cmd -> new ProcessBuilder(cmd).redirectErrorStream(true).start());
    }

    /**
     * For tests to inject a fake process.
     */
    public VespaIndexInspectClient {
        Objects.requireNonNull(executable, "executable is null");
        if (executable.isBlank()) throw new IllegalArgumentException("executable is blank");
        Objects.requireNonNull(processStarter, "processStarter is null");
    }

    /**
     * For tests to inject a fake process.
     */
    @FunctionalInterface
    public interface ProcessStarter {
        Process start(List<String> cmd) throws IOException;
    }

    /**
     * Simple tuple for one row.
     */
    public record TermDocumentFrequency(String term, long documentFrequency) {
        @Override
        public String toString() {
            return term + "\t" + documentFrequency;
        }
    }

    /**
     * Parses a single TermDocumentFrequency from a line.
     */
    static TermDocumentFrequency parseDumpWordsLine(String line) {
        if (line == null || line.isEmpty()) return null;

        var tab = line.indexOf('\t');
        if (tab <= 0 || tab == line.length() - 1) return null;

        var term = line.substring(0, tab);
        var numStr = line.substring(tab + 1).trim();
        try {
            var n = Long.parseLong(numStr);
            return new TermDocumentFrequency(term, n);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * Creates a stream of rows from <code>vespa-index-inspect dumpwords --indexdir indexDir --field field</code>.
     * Caller must consume or close the returned Stream to release resources.
     * <p>
     * When the returned Stream is closed, the child process exit code is validated; if it
     * exited non-zero, the close action throws an {@link UncheckedIOException} wrapping an
     * {@link IOException}.
     */
    public Stream<TermDocumentFrequency> streamDumpWords(Path indexDir, String field) throws IOException {
        Objects.requireNonNull(indexDir, "indexDir is null");
        Objects.requireNonNull(field, "field is null");
        if (field.isBlank()) throw new IllegalArgumentException("field is blank");

        var cmd = List.of(executable, "dumpwords", "--indexdir", indexDir.toString(), "--field", field);
        var p = processStarter.start(cmd);
        var br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        var it = new DumpWordsProcessIterator(p, br);

        var stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL), false);
        return stream.onClose(() -> {
            try {
                it.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * For testing {@link DumpWordsLineIterator} without process. Package private.
     */
    static Stream<TermDocumentFrequency> streamDumpWords(Reader reader) {
        var bufferedReader = (reader instanceof BufferedReader b) ? b : new BufferedReader(reader);
        var it = new DumpWordsLineIterator(bufferedReader);
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL),
                false
        ).onClose(() -> {
            try {
                bufferedReader.close();
            } catch (IOException ignored) {
            }
        });
    }

    /**
     * Iterator that parses lines and validates process exit on close.
     * <p>
     * Uses {@link DumpWordsLineIterator} to parse the lines from the stdout of the process.
     */
    private static final class DumpWordsProcessIterator implements Iterator<TermDocumentFrequency>, AutoCloseable {
        private final Process process;
        private boolean closed;
        private final BufferedReader br;
        private final Iterator<TermDocumentFrequency> delegate;

        DumpWordsProcessIterator(Process process, BufferedReader br) {
            this.process = process;
            this.br = br;
            this.delegate = new DumpWordsLineIterator(br);
        }

        @Override
        public boolean hasNext() {
            return !closed && delegate.hasNext();
        }

        @Override
        public TermDocumentFrequency next() {
            if (closed) throw new NoSuchElementException();
            return delegate.next();
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            IOException first = null;
            try {
                br.close();
            } catch (IOException e) {
                first = e;
            }

            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
                var exit = process.exitValue();
                if (exit != 0) {
                    IOException ioe = new IOException("vespa-index-inspect exited with code " + exit);
                    if (first != null) ioe.addSuppressed(first);
                    throw ioe;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (first != null) throw first;
                throw new IOException("Interrupted waiting for process exit", ie);
            }
            if (first != null) throw first;
        }
    }

    /**
     * Parses {@link TermDocumentFrequency} from a buffered reader.
     */
    private static final class DumpWordsLineIterator implements Iterator<TermDocumentFrequency> {
        private final BufferedReader br;
        private TermDocumentFrequency nextRow;
        private boolean eof;

        DumpWordsLineIterator(BufferedReader br) {
            this.br = br;
        }

        @Override
        public boolean hasNext() {
            if (nextRow != null) return true;
            if (eof) return false;
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    var parsed = parseDumpWordsLine(line);
                    if (parsed != null) {
                        nextRow = parsed;
                        return true;
                    }
                }
                eof = true;
                return false;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public TermDocumentFrequency next() {
            if (!hasNext()) throw new NoSuchElementException();
            var out = nextRow;
            nextRow = null;
            return out;
        }
    }
}
