// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Runs vespa-fileheader-inspect.
 * <p>
 * We use {@code selector.dat} inside an index directory to get doc id limit on node.
 *
 * @author johsol
 */
public final class VespaFileHeaderInspectClient {

    private final String executable;
    private final ProcessStarter processStarter;
    private final Duration waitForExit;

    /** Use default executable on PATH. */
    public VespaFileHeaderInspectClient() {
        this("vespa-fileheader-inspect");
    }

    public VespaFileHeaderInspectClient(String executable) {
        this(executable, cmd -> new ProcessBuilder(cmd).redirectErrorStream(true).start(), Duration.ofSeconds(5));
    }

    /** For tests to inject a fake process. */
    public VespaFileHeaderInspectClient(String executable,
                                        ProcessStarter processStarter,
                                        Duration waitForExit) {
        this.executable = Objects.requireNonNull(executable, "executable");
        if (executable.isBlank()) {
            throw new IllegalArgumentException("executable is blank");
        }
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        this.waitForExit = Objects.requireNonNull(waitForExit, "waitForExit");
    }

    @FunctionalInterface
    public interface ProcessStarter {
        Process start(List<String> cmd) throws IOException;
    }

    /**
     * Read the doc id limit by running {@code vespa-fileheader-inspect <indexDir>/selector.dat}.
     */
    public long readDocIdLimit(Path indexDir) throws IOException {
        Objects.requireNonNull(indexDir, "indexDir");
        Path selector = indexDir.resolve("selector.dat");
        if (!Files.isRegularFile(selector)) {
            throw new IOException("Missing selector.dat: " + selector);
        }

        var cmd = List.of(executable, selector.toString());
        var p = processStarter.start(cmd);
        try (var br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            Long value = parseDocIdLimit(br);
            waitAndCheckExit(p);
            if (value == null) {
                throw new IOException("docIdLimit not found in vespa-fileheader-inspect output for " + selector);
            }
            return value;
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        } finally {
            p.getInputStream().close();
        }
    }

    /**
     * Parse {@code docIdLimit} from a Reader of the CLI table output.
     * Tolerates both {@code docIdLimit} and {@code Doc id limit}.
     */
    static Long parseDocIdLimit(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        try (var br = (reader instanceof BufferedReader b) ? b : new BufferedReader(reader)) {
            Long spaced = null;

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) != '|') continue;        // skip borders/headers
                // Split table row: | Tag | Type | Value |
                String[] parts = line.split("\\|");
                if (parts.length < 4) continue; // need at least "", " Tag ", " Type ", " Value ", ""
                String tag = parts[1].trim();
                String value = parts[3].trim(); // third column is "Value"
                if (tag.isEmpty() || value.isEmpty()) continue;

                if (tag.equals("Doc id limit")) {
                    spaced = Long.parseLong(value);
                }
            }

            return spaced;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void waitAndCheckExit(Process p) throws IOException {
        try {
            if (!p.waitFor(waitForExit.toSeconds(), TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor(3, TimeUnit.SECONDS);
            }
            int exit = p.exitValue();
            if (exit != 0) throw new IOException(executable + " exited with code " + exit);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for " + executable + " exit", ie);
        }
    }
}
