// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@link VespaIndexInspectClient.TermDocumentFrequency} rows as TSV.
 *
 * @author johsol
 */
public final class TsvTermDfWriter implements TermDfWriter {
    private final BufferedWriter out;

    public TsvTermDfWriter(Path path) throws IOException {
        this.out = Files.newBufferedWriter(path);
    }

    @Override
    public void write(VespaIndexInspectClient.TermDocumentFrequency row) throws IOException {
        out.write(row.term());
        out.write('\t');
        out.write(Long.toString(row.documentFrequency()));
        out.newLine();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}