// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.common;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Writes vespa significance tsv files described in {@link VespaSignificanceTsvReader}.
 *
 * @author johsol
 */
public final class VespaSignificanceTsvWriter implements AutoCloseable {

    private final BufferedWriter bw;
    private final boolean sorted;
    private String lastTerm;

    public static final String MAGIC = VespaSignificanceTsvReader.MAGIC;
    public static final String VERSION = VespaSignificanceTsvReader.VERSION;
    public static final String HEADER_END = VespaSignificanceTsvReader.HEADER_END;

    public VespaSignificanceTsvWriter(Path out,
                                      long documentCount,
                                      boolean sorted,
                                      Instant createdAt) throws IOException {
        this.bw = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(out), StandardCharsets.UTF_8));
        this.sorted = sorted;
        writeHeader(documentCount, sorted, createdAt);
    }

    public VespaSignificanceTsvWriter(Writer writer,
                                      long documentCount,
                                      boolean sorted,
                                      Instant createdAt) throws IOException {
        this.bw = (writer instanceof BufferedWriter)
                ? (BufferedWriter) writer
                : new BufferedWriter(writer, 1 << 16);
        this.sorted = sorted;
        writeHeader(documentCount, sorted, createdAt);
    }

    private void writeHeader(long documentCount, boolean sorted, Instant createdAt) throws IOException {
        bw.write(MAGIC);
        bw.write('\t');
        bw.write(VERSION);
        bw.write('\n');
        bw.write(VespaSignificanceTsvReader.DOCUMENT_COUNT_HEADER);
        bw.write("\t");
        bw.write(Long.toString(documentCount));
        bw.write('\n');
        bw.write(VespaSignificanceTsvReader.SORTED_HEADER);
        bw.write("\t");
        bw.write(sorted ? "true" : "false");
        bw.write('\n');
        bw.write(VespaSignificanceTsvReader.CREATED_AT_HEADER);
        bw.write("\t");
        bw.write(Objects.requireNonNull(createdAt, "created_at").toString());
        bw.write('\n');
        bw.write(HEADER_END);
        bw.write('\n');
    }

    /** Append one data row. If sorted==true, enforces strict ascending order. */
    public void writeRow(String term, long df) throws IOException {
        if (term == null || term.isEmpty()) throw new IllegalArgumentException("term is empty");
        if (term.indexOf('\t') >= 0) throw new IllegalArgumentException("term contains tab");
        if (df < 0) throw new IllegalArgumentException("negative df");
        if (sorted && lastTerm != null && term.compareTo(lastTerm) <= 0) {
            throw new IllegalArgumentException("Not strictly ascending: '" + term + "' <= '" + lastTerm + "'");
        }
        lastTerm = term;

        bw.write(term);
        bw.write('\t');
        bw.write(Long.toString(df));
        bw.write('\n');
    }

    public void flush() throws IOException {
        bw.flush();
    }

    @Override
    public void close() throws IOException {
        bw.close();
    }
}
