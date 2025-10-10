// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Queries the OS for maximum fd allowed per process.
 *
 * @author johsol
 */
public class UlimitProbe {

    /** Returns the soft limit for open files (`ulimit -n`), or -1 if unknown. */
    public static long softLimit() {
        return runShellAndParseLong("/bin/sh", "-lc", "ulimit -n");
    }

    /** Returns the hard limit for open files (`ulimit -Hn`), or -1 if unknown. */
    public static long hardLimit() {
        return runShellAndParseLong("/bin/sh", "-lc", "ulimit -Hn");
    }

    private static long runShellAndParseLong(String... cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String out = r.readLine();
                // Example outputs: "10240" or "unlimited"
                if (out == null) return -1L;
                out = out.trim();
                if (out.equalsIgnoreCase("unlimited")) return Long.MAX_VALUE;
                return Long.parseLong(out);
            }
        } catch (NumberFormatException | IOException e) {
            return -1L;
        } finally {
            if (p != null) {
                try { p.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                p.destroyForcibly();
            }
        }
    }
}
