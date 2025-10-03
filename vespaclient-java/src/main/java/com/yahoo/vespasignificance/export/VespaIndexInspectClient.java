// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package com.yahoo.vespasignificance.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client around `vespa-index-inspect dumpwords` that returns term -> df map.
 *
 * @author johsol
 */
public final class VespaIndexInspectClient {

    private final String binary;

    /** Use default binary name on PATH (e.g., /usr/bin/vespa-index-inspect). */
    public VespaIndexInspectClient() {
        this("vespa-index-inspect");
    }

    /** Use a specific binary path (absolute if not on PATH). */
    public VespaIndexInspectClient(String binary) {
        this.binary = binary;
    }

    /** Convenience: no timeout. */
    public Map<String, Long> dumpWords(Path indexDir, String field) throws IOException, InterruptedException {
        return dumpWords(indexDir, field, null);
    }

    /**
     * Run `vespa-index-inspect dumpwords --indexdir <dir> --field <field>`.
     */
    public Map<String, Long> dumpWords(Path indexDir, String field, Duration timeout)
            throws IOException, InterruptedException {

        if (indexDir == null) throw new IllegalArgumentException("indexDir is null");
        if (field == null || field.isBlank()) throw new IllegalArgumentException("field is blank");

        List<String> cmd = List.of(
                binary, "dumpwords",
                "--indexdir", indexDir.toString(),
                "--field", field
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // Merge stderr into stdout so we can surface tool errors
        pb.redirectErrorStream(true);

        Process p = pb.start();

        Map<String, Long> out = new HashMap<>(1 << 14); // start with a decent capacity
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                // Expected: term \t number
                int tab = line.indexOf('\t');
                if (tab <= 0 || tab == line.length() - 1) {
                    // skip malformed lines (or log if you prefer)
                    continue;
                }
                String term = line.substring(0, tab);
                String numStr = line.substring(tab + 1).trim();
                try {
                    long n = Long.parseLong(numStr);
                    out.put(term, n);
                } catch (NumberFormatException ignore) {
                    // skip malformed numeric rows
                }
            }
        }

        boolean finished;
        if (timeout == null) {
            finished = true;
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IOException("vespa-index-inspect exited with code " + exit + " for " + indexDir);
            }
        } else {
            finished = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IOException("vespa-index-inspect timed out after " + timeout + " for " + indexDir);
            }
            int exit = p.exitValue();
            if (exit != 0) {
                throw new IOException("vespa-index-inspect exited with code " + exit + " for " + indexDir);
            }
        }

        return out;
    }
}
