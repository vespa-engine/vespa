// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * An interface for writing {@link VespaIndexInspectClient.TermDocumentFrequency} rows.
 *
 * @author johsol
 */
public interface TermDfWriter extends AutoCloseable {

    /**
     * Write a single row. Implementations may buffer.
     */
    void write(VespaIndexInspectClient.TermDocumentFrequency row) throws IOException;

    /**
     * Write multiple rows. Closes the stream after consumption.
     */
    default long writeAll(Stream<VespaIndexInspectClient.TermDocumentFrequency> rows) throws IOException {
        try (rows) {
            long count = 0;
            var it = rows.iterator();
            while (it.hasNext()) {
                write(it.next());   // can throw IOException and will propagate
                count++;
            }
            return count;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Flush without closing.
     */
    default void flush() throws IOException {
    }

    @Override
    void close() throws IOException;
}
