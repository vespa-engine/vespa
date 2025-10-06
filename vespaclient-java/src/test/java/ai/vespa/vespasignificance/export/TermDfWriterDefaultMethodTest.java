// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing default methods on {@link TermDfWriter} interface.
 *
 * @author johsol
 */
class TermDfWriterDefaultMethodTest {

    static final class ThrowingWriter implements TermDfWriter {
        @Override
        public void write(VespaIndexInspectClient.TermDocumentFrequency row) throws IOException {
            throw new IOException("boom");
        }

        @Override
        public void close() {
        }
    }

    static final class RecordingWriter implements TermDfWriter {
        int n = 0;
        boolean flushed = false;
        boolean closed = false;

        @Override
        public void write(VespaIndexInspectClient.TermDocumentFrequency row) {
            n++;
        }

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void writeAllPropagatesIOExceptionFromWrite() {
        var writer = new ThrowingWriter();
        var rows = Stream.of(new VespaIndexInspectClient.TermDocumentFrequency("a", 1));
        var ex = assertThrows(IOException.class, () -> writer.writeAll(rows));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void writeAllClosesInputStream() throws Exception {
        var writer = new RecordingWriter();
        AtomicBoolean closed = new AtomicBoolean(false);
        var rows = Stream.of(new VespaIndexInspectClient.TermDocumentFrequency("a", 1)).onClose(() -> closed.set(true));

        writer.writeAll(rows);
        assertTrue(closed.get(), "writeAll should close the input stream");
        assertEquals(1, writer.n);
    }

}
