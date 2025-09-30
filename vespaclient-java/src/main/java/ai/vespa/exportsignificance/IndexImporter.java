// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.exportsignificance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses vespa-index-inspect to dump index in dir and parses term frequency pair.
 *
 * @author johsol
 */
public class IndexImporter {

    final String VESPA_INDEX_INSPECT = "vespa-index-inspect";

    public Map<String, Long> importFromIndex(String field, Path dir) {
        ProcessBuilder pb = new ProcessBuilder("bash", "-lc", buildCommandLine(field, dir));
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, Long> result = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                var components = line.split("\t");
                var term = components[0].strip();
                Long value = Long.parseLong(components[1].strip());
                result.put(term, value);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private String buildCommandLine(String field, Path dir) {
        return VESPA_INDEX_INSPECT +
                " dumpwords " +
                "--index " +
                dir +
                " --field " +
                field;
    }

}
