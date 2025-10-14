// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.generate;

import ai.vespa.vespasignificance.common.VespaSignificanceTsvReader;
import io.airlift.compress.zstd.ZstdInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Reads a Vespa Significance TSV (see {@link VespaSignificanceTsvReader}) file and produces a (term -> df) map and
 * documentCount.
 *
 * @author johsol
 */
public final class VstsvFormatStrategy implements FormatStrategy {

    private final Path input;
    private final String languageKey;

    public VstsvFormatStrategy(Path input) {
        this.input = input;
        this.languageKey = "un";
    }

    @Override
    public Result build() throws IOException {
        try (Reader r = openReader(input)) {
            try (var vstsv = new VespaSignificanceTsvReader(r)) {
                long docCount = vstsv.header().documentCount();
                SortedMap<String, Long> df = new TreeMap<>();
                while (vstsv.next()) {
                    df.merge(vstsv.term(), vstsv.df(), Long::sum);
                }
                return new Result(df, docCount);
            }
        }
    }

    @Override
    public String languageKey() {
        return languageKey;
    }

    private static Reader openReader(Path p) throws IOException {
        var in = Files.newInputStream(p);
        var bin = new BufferedInputStream(in, 1 << 16);
        if (p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zst")) {
            return new InputStreamReader(new ZstdInputStream(bin), StandardCharsets.UTF_8);
        }
        return new InputStreamReader(bin, StandardCharsets.UTF_8);
    }
}
