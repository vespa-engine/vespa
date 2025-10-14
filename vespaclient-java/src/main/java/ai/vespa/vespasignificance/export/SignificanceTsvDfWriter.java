// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import ai.vespa.vespasignificance.common.VespaSignificanceTsvWriter;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;

/**
 * Adapt {@link VespaSignificanceTsvWriter} to {@link TermDfWriter}.
 *
 * @author johsol
 */
public final class SignificanceTsvDfWriter implements TermDfWriter {
    private final VespaSignificanceTsvWriter out;

    public SignificanceTsvDfWriter(Writer w, long documentCount, boolean sorted, Instant createdAt) throws IOException {
        this.out = new VespaSignificanceTsvWriter(w, documentCount, sorted, createdAt);
    }

    @Override
    public void write(VespaIndexInspectClient.TermDocumentFrequency r) throws IOException {
        out.writeRow(r.term(), r.documentFrequency());
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

