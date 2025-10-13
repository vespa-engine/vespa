// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Vespa significance tsv format. Streamable intermediate format for merging and generation of significance model
 * from many nodes.
 * <p>
 * Format:
 * <pre>
 * #VESPA_SIGNIFICANCE_TSV\tv1
 * document_count\tN
 * sorted\t(true|false)
 * created_at\tISO-8601 instant
 * --END-HEADER--
 * term\tdf\n ...
 * </pre>
 *
 * @author johsol
 */
public final class VespaSignificanceTsvReader implements AutoCloseable {

    public static final String MAGIC = "#VESPA_SIGNIFICANCE_TSV";
    public static final String VERSION = "v1";
    public static final String HEADER_END = "--END-HEADER--";

    public static final String DOCUMENT_COUNT_HEADER = "document_count";
    public static final String SORTED_HEADER = "sorted";
    public static final String CREATED_AT_HEADER = "created_at";

    /** Parsed header */
    public record Header(long documentCount, boolean sorted, Instant createdAt, Map<String, String> raw) {
    }

    private final BufferedReader bufferedReader;
    private final Header header;

    private String currentTerm;
    private long currentDf;
    private boolean eof;

    // for sorted validation
    private final boolean enforceSorted;
    private String lastTerm;

    /** Open and validate header. */
    public VespaSignificanceTsvReader(Path path) throws IOException {
        this.bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        this.header = readAndValidateHeader(this.bufferedReader);
        this.enforceSorted = this.header.sorted;
    }

    public Header header() {
        return header;
    }

    /**
     * Advance to next data line.
     * <p>
     * Returns true if parsed a row and false if EOF.
     */
    public boolean next() throws IOException {
        if (eof) return false;

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.isBlank()) continue;

            int tab = line.indexOf('\t');
            if (tab <= 0) {
                throw new IllegalArgumentException("Bad data line (missing tab): " + truncate(line));
            }
            String term = line.substring(0, tab);
            String dfStr = line.substring(tab + 1);

            if (term.indexOf('\t') >= 0) {
                throw new IllegalArgumentException("Term contains tab: " + truncate(term));
            }
            long df;
            try {
                df = Long.parseLong(dfStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad DF (not a number): " + truncate(dfStr));
            }

            if (enforceSorted && lastTerm != null && term.compareTo(lastTerm) <= 0) {
                throw new IllegalArgumentException("Not strictly ascending: '" + term + "' <= '" + lastTerm + "'");
            }
            lastTerm = term;

            this.currentTerm = term;
            this.currentDf = df;
            return true;
        }

        eof = true;
        return false;
    }

    public String term() {
        return currentTerm;
    }

    public long df() {
        return currentDf;
    }

    @Override
    public void close() throws IOException {
        bufferedReader.close();
    }

    private static Header readAndValidateHeader(BufferedReader bufferedReader) throws IOException {
        String line1 = bufferedReader.readLine();
        if (line1 == null) throw new IllegalArgumentException("Empty file");
        String expected = MAGIC + "\t" + VERSION;
        if (!Objects.equals(line1, expected)) {
            throw new IllegalArgumentException("Bad magic/version. Expected '" + expected + "', got: " + truncate(line1));
        }

        var kv = new LinkedHashMap<String, String>();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (Objects.equals(line, HEADER_END)) break;
            if (line.isBlank()) continue;

            int tab = line.indexOf('\t');
            if (tab <= 0) {
                throw new IllegalArgumentException("Malformed header line (expected key<TAB>value): " + truncate(line));
            }
            String key = line.substring(0, tab).trim();
            String val = line.substring(tab + 1).trim();
            if (key.isEmpty()) throw new IllegalArgumentException("Empty header key");
            kv.put(key, val);
        }
        if (line == null) throw new IllegalArgumentException("Missing header terminator: " + HEADER_END);

        List<String> missingKeys = new ArrayList<>();
        String documentCountString = kv.get(DOCUMENT_COUNT_HEADER);
        if (documentCountString == null) {
            missingKeys.add(DOCUMENT_COUNT_HEADER);
        }

        String sortedStr = kv.get(SORTED_HEADER);
        if (sortedStr == null) {
            missingKeys.add(SORTED_HEADER);
        }

        String createdStr = kv.get(CREATED_AT_HEADER);
        if (createdStr == null) {
            missingKeys.add(CREATED_AT_HEADER);
        }

        if (!missingKeys.isEmpty()) {
            throw new IllegalArgumentException("Header missing required keys: " + String.join(", ", missingKeys));
        }

        long docCount;
        try {
            docCount = Long.parseLong(documentCountString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("document_count must be integer");
        }
        if (docCount < 0) throw new IllegalArgumentException("document_count must be >= 0");

        boolean sorted;
        if ("true".equalsIgnoreCase(sortedStr)) sorted = true;
        else if ("false".equalsIgnoreCase(sortedStr)) sorted = false;
        else throw new IllegalArgumentException("sorted must be 'true' or 'false'");

        Instant createdAt;
        try {
            createdAt = Instant.parse(createdStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("created_at must be ISO-8601 instant, e.g. 2025-10-13T11:59:00Z");
        }

        return new Header(docCount, sorted, createdAt, Collections.unmodifiableMap(kv));
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        s = s.replace("\n", "\\n");
        return (s.length() > 120) ? s.substring(0, 120) + "…" : s;
    }

    /** Container for materializing the whole file. */
    public record Loaded(Header header, SortedMap<String, Long> df) {
    }

    /** Parse entire file into memory (merging duplicate terms). */
    public static Loaded parseAll(Path path) throws IOException {
        try (var reader = new VespaSignificanceTsvReader(path)) {
            SortedMap<String, Long> df = new TreeMap<>();
            while (reader.next()) {
                df.merge(reader.term(), reader.df(), Long::sum);
            }
            return new Loaded(reader.header(), Collections.unmodifiableSortedMap(df));
        }
    }
}
