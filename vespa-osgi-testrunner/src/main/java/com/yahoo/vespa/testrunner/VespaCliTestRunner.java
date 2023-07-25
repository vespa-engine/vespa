// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.defaults.Defaults;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.testrunner.TestRunner.Status.ERROR;
import static com.yahoo.vespa.testrunner.TestRunner.Status.FAILURE;
import static com.yahoo.vespa.testrunner.TestRunner.Status.NO_TESTS;
import static com.yahoo.vespa.testrunner.TestRunner.Status.RUNNING;
import static com.yahoo.vespa.testrunner.TestRunner.Status.SUCCESS;
import static com.yahoo.yolean.Exceptions.uncheck;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author jonmv
 */
public class VespaCliTestRunner implements TestRunner {

    private static final Logger logger = Logger.getLogger(VespaCliTestRunner.class.getName());

    private final SortedMap<Long, LogRecord> log = new ConcurrentSkipListMap<>();
    private final Path artifactsPath;
    private final Path testsPath;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.NOT_STARTED);
    private final Path vespaHome;

    private Path vespaCliRoot = null;

    @Inject
    public VespaCliTestRunner(VespaCliTestRunnerConfig config) {
        this(config.artifactsPath(), config.testsPath(), Path.of(Defaults.getDefaults().vespaHome()));
    }

    VespaCliTestRunner(Path artifactsPath, Path testsPath, Path vespaHome) {
        this.artifactsPath = artifactsPath;
        this.testsPath = testsPath;
        this.vespaHome = vespaHome;
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

    void runTests(Suite suite, byte[] config) {
        Process process = null;
        try {
            TestConfig testConfig = TestConfig.fromJson(config);
            ProcessBuilder builder = testRunProcessBuilder(suite, testConfig);
            if (builder == null) {
                status.set(NO_TESTS);
                return;
            }

            process = builder.start();
            HtmlLogger htmlLogger = new HtmlLogger();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            in.lines().forEach(line -> log(htmlLogger.toLog(line)));
            int exitCode = process.waitFor();
            status.set(exitCode == 0 ? SUCCESS : FAILURE);
        }
        catch (Exception e) {
            if (process != null)
                process.destroyForcibly();

            log(Level.SEVERE, "Failed running tests", e);
            status.set(ERROR);
        }
    }

    private Path ensureDirectoryForVespaCli(String dir) {
        if (vespaCliRoot == null) {
            vespaCliRoot = uncheck(() -> Files.createTempDirectory(VespaCliTestRunner.class.getSimpleName()));
            vespaCliRoot.toFile().deleteOnExit();
        }
        return uncheck(() -> Files.createDirectories(vespaCliRoot.resolve(dir)));
    }

    ProcessBuilder testRunProcessBuilder(Suite suite, TestConfig config) throws IOException {
        Optional<Path> suitePath = getChildDirectory(testsPath, toSuiteDirectoryName(suite));
        if (suitePath.isEmpty())
            return null;

        ProcessBuilder builder = new ProcessBuilder("vespa", "test", suitePath.get().toAbsolutePath().toString(),
                                                    "--application", config.application().toFullString(),
                                                    "--zone", config.zone().value(),
                                                    "--target", config.system().isPublic() ? "cloud" : "hosted");
        builder.redirectErrorStream(true);
        // The CI environment variables tells Vespa CLI to omit certain warnings that do not apply to CI environments
        builder.environment().put("CI", "true");
        builder.environment().put("VESPA_CLI_CLOUD_CI", "true");
        builder.environment().put("VESPA_CLI_CLOUD_SYSTEM", config.system().value());
        builder.environment().put("VESPA_CLI_HOME", ensureDirectoryForVespaCli("cli-home").toString());
        builder.environment().put("VESPA_CLI_CACHE_DIR", ensureDirectoryForVespaCli("cli-cache").toString());
        builder.environment().put("VESPA_CLI_ENDPOINTS", toEndpointsConfig(config));
        Credentials credentials = getCredentials(config);
        builder.environment().put("VESPA_CLI_DATA_PLANE_KEY_FILE", credentials.privateKeyFile().toString());
        builder.environment().put("VESPA_CLI_DATA_PLANE_CERT_FILE", credentials.certificateFile().toString());
        return builder;
    }

    private record Credentials(Path privateKeyFile, Path certificateFile) {}

    private Credentials getCredentials(TestConfig config) {
        final Path privateKeyFile;
        final Path certificateFile;
        if (config.system().isPublic()) {
            privateKeyFile = artifactsPath.resolve("key");
            certificateFile = artifactsPath.resolve("cert");
        } else {
            Path siaRoot = vespaHome.resolve("var/vespa/sia");
            List<AthenzIdentity> services = SiaUtils.findSiaServices(siaRoot);
            if (services.isEmpty()) {
                throw new IllegalArgumentException("No service credentials in " + siaRoot + ". Application has no " +
                                                   "Athenz service, and may not access read / write protected resources");
            }
            if (services.size() > 1) {
                throw new IllegalStateException("More than one set of service credentials in " + siaRoot + ":\n"
                                                + services.stream().map(AthenzIdentity::getFullName).collect(Collectors.joining("\n")));
            }
            privateKeyFile = SiaUtils.getPrivateKeyFile(siaRoot, services.get(0));
            certificateFile = SiaUtils.getCertificateFile(siaRoot, services.get(0));
        }
        return new Credentials(privateKeyFile.toAbsolutePath(), certificateFile.toAbsolutePath());
    }

    private static String toSuiteDirectoryName(Suite suite) {
        return switch (suite) {
            case SYSTEM_TEST -> "system-test";
            case STAGING_SETUP_TEST -> "staging-setup";
            case STAGING_TEST -> "staging-test";
            case PRODUCTION_TEST -> "production-test";
        };
    }

    private void log(Level level, String message, Throwable thrown) {
        LogRecord record = new LogRecord(level, message);
        record.setThrown(thrown);
        log(record);
    }

    private void log(LogRecord record) {
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
