// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestConfig;
import com.google.inject.Inject;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.vespa.testrunner.TestRunner.Status.ERROR;
import static com.yahoo.vespa.testrunner.TestRunner.Status.FAILURE;
import static com.yahoo.vespa.testrunner.TestRunner.Status.RUNNING;
import static com.yahoo.vespa.testrunner.TestRunner.Status.SUCCESS;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author jonmv
 */
public class VespaCliTestRunner implements TestRunner {

    private static final Logger logger = Logger.getLogger(VespaCliTestRunner.class.getName());

    private final SortedMap<Long, LogRecord> log = new ConcurrentSkipListMap<>();
    private final Path artifactsPath;
    private AtomicReference<Status> status = new AtomicReference<>(Status.NOT_STARTED);

    @Inject
    public VespaCliTestRunner(VespaCliTestRunnerConfig config) {
        this(config.artifactsPath().resolve("artifacts"));
    }

    VespaCliTestRunner(Path artifactsPath) {
        this.artifactsPath = artifactsPath;
    }

    @Override
    public Collection<LogRecord> getLog(long after) {
        return log.tailMap(after + 1).values();
    }

    @Override
    public Status getStatus() {
        return status.get();
    }

    @Override
    public CompletableFuture<?> test(Suite suite, byte[] config) {
        if (status.getAndSet(RUNNING) == RUNNING)
            throw new IllegalStateException("Tests already running, not supposed to be started now");

        return CompletableFuture.runAsync(() -> runTests(suite, config));
    }

    @Override
    public boolean isSupported() {
        return getChildDirectory(artifactsPath, "tests").isPresent();
    }

    void runTests(Suite suite, byte[] config) {
        Process process = null;
        try {
            TestConfig testConfig = TestConfig.fromJson(config);
            Path credentialsPath = artifactsPath.resolve(testConfig.application().toFullString());
            copyCredentials(credentialsPath);

            process = testRunProcessBuilder(suite, testConfig).start();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            in.lines().forEach(line -> {
                if (line.length() > 1 << 13)
                    line = line.substring(0, 1 << 13) + " ... (this log entry was truncated due to size)";

                log(Level.INFO, line, null);
            });
            status.set(process.waitFor() == 0 ? SUCCESS : process.waitFor() == 3 ? FAILURE : ERROR);
        }
        catch (Exception e) {
            if (process != null)
                process.destroyForcibly();

            log(Level.SEVERE, "Failed running tests", e);
            status.set(ERROR);
        }
    }

    void copyCredentials(Path credentialsPath) throws IOException {
        Files.createDirectories(credentialsPath);
        Files.copy(artifactsPath.resolve("key"), credentialsPath.resolve("data-plane-private-key.pem"));
        Files.copy(artifactsPath.resolve("cert"), credentialsPath.resolve("data-plane-public-cert.pem"));
    }

    ProcessBuilder testRunProcessBuilder(Suite suite, TestConfig config) throws IOException {
        Path suitePath = getChildDirectory(artifactsPath, "tests")
                .flatMap(testsPath -> getChildDirectory(testsPath, toSuiteDirectoryName(suite)))
                .orElseThrow(() -> new IllegalStateException("No tests found, for suite '" + suite + "'"));

        ProcessBuilder builder = new ProcessBuilder("vespa", "test", suitePath.toAbsolutePath().toString(),
                                                    "--application", config.application().toFullString(),
                                                    "--endpoints", toEndpointsConfig(config));
        builder.redirectErrorStream(true);
        return builder;
    }

    private static String toSuiteDirectoryName(Suite suite) {
        switch (suite) {
            case SYSTEM_TEST: return "system-test";
            case STAGING_SETUP_TEST: return "staging-setup";
            case STAGING_TEST: return "staging-test";
            default: throw new IllegalArgumentException("Unsupported test suite '" + suite + "'");
        }
    }

    private void log(Level level, String message, Throwable thrown) {
        LogRecord record = new LogRecord(level, message);
        record.setThrown(thrown);
        logger.log(record);
        log.put(record.getSequenceNumber(), record);
    }

    private static Optional<Path> getChildDirectory(Path parent, String name) {
        try (Stream<Path> children = Files.list(parent)) {
            return children.filter(Files::isDirectory)
                           .filter(path -> path.endsWith(name))
                           .findAny();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to list files under " + parent, e);
        }
    }

    static String toEndpointsConfig(TestConfig config) throws IOException {
        Cursor root = new Slime().setObject();
        Cursor endpointsArray = root.setArray("endpoints");
        config.deployments().get(config.zone()).forEach((cluster, url) -> {
            Cursor endpointObject = endpointsArray.addObject();
            endpointObject.setString("cluster", cluster);
            endpointObject.setString("url", url.toString());
        });
        return new String(SlimeUtils.toJsonBytes(root), UTF_8);
    }

}
