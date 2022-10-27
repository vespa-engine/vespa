// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.testrunner.HtmlLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.logging.Level.SEVERE;

/**
 * @author valerijf
 * @author jvenstad
 */
public class TestRunner implements com.yahoo.vespa.testrunner.TestRunner {

    private static final Logger logger = Logger.getLogger(TestRunner.class.getName());
    private static final Path vespaHome = Paths.get(Defaults.getDefaults().vespaHome());
    private static final String settingsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                              "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n" +
                                              "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                              "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">\n" +
                                              "    <mirrors>\n" +
                                              "        <mirror>\n" +
                                              "            <id>maven central</id>\n" +
                                              "            <mirrorOf>*</mirrorOf>\n" + // Use this for everything!
                                              "            <url>https://repo.maven.apache.org/maven2/</url>\n" +
                                              "        </mirror>\n" +
                                              "    </mirrors>\n" +
                                              "</settings>";

    private final Path artifactsPath;
    private final Path testPath;
    private final Path configFile;
    private final Path settingsFile;
    private final Function<TestProfile, ProcessBuilder> testBuilder;
    private final SortedMap<Long, LogRecord> log = new ConcurrentSkipListMap<>();

    private volatile Status status = Status.NOT_STARTED;

    @Inject
    public TestRunner(TestRunnerConfig config) {
        this(config.artifactsPath(),
             vespaHome.resolve("tmp/test"),
             vespaHome.resolve("tmp/config.json"),
             vespaHome.resolve("tmp/settings.xml"),
             profile -> mavenProcessFrom(profile, config));
    }

    TestRunner(Path artifactsPath, Path testPath, Path configFile, Path settingsFile, Function<TestProfile, ProcessBuilder> testBuilder) {
        this.artifactsPath = artifactsPath;
        this.testPath = testPath;
        this.configFile = configFile;
        this.settingsFile = settingsFile;
        this.testBuilder = testBuilder;
    }

    static ProcessBuilder mavenProcessFrom(TestProfile profile, TestRunnerConfig config) {
        List<String> command = new ArrayList<>();
        command.add("mvn"); // mvn must be in PATH of the jDisc containers
        command.add("test");

        command.add("--batch-mode"); // Run in non-interactive (batch) mode (disables output color)
        command.add("--show-version"); // Display version information WITHOUT stopping build
        command.add("--settings"); // Need to override repository settings in ymaven config >_<
        command.add(vespaHome.resolve("tmp/settings.xml").toString());

        // Disable maven download progress indication
        command.add("-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn");
        command.add("-Dstyle.color=always"); // Enable ANSI color codes again
        command.add("-DfailIfNoTests=" + profile.failIfNoTests());
        command.add("-Dvespa.test.config=" + vespaHome.resolve("tmp/config.json"));
        if (config.useAthenzCredentials())
            command.add("-Dvespa.test.credentials.root=" + Defaults.getDefaults().underVespaHome("var/vespa/sia"));
        else if (config.useTesterCertificate())
            command.add("-Dvespa.test.credentials.root=" + config.artifactsPath());
        command.add(String.format("-DargLine=-Xms%1$dm -Xmx%1$dm", config.surefireMemoryMb()));
        command.add("-Dmaven.repo.local=" + vespaHome.resolve("tmp/.m2/repository"));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().merge("MAVEN_OPTS", " -Djansi.force=true", String::concat);
        builder.directory(vespaHome.resolve("tmp/test").toFile());
        builder.redirectErrorStream(true);
        return builder;
    }

    @Override
    public synchronized CompletableFuture<?> test(Suite suite, byte[] testConfig) {
        if (status == Status.RUNNING)
            throw new IllegalArgumentException("Tests are already running; should not receive this request now.");

        log.clear();

        if ( ! hasTestsJar()) {
            status = Status.NO_TESTS;
            return CompletableFuture.completedFuture(null);
        }

        status = Status.RUNNING;
        return CompletableFuture.runAsync(() -> runTests(toProfile(suite), testConfig));
    }

    @Override
    public Collection<LogRecord> getLog(long after) {
        return log.tailMap(after + 1).values();
    }

    @Override
    public synchronized Status getStatus() {
        return status;
    }

    private boolean hasTestsJar() {
        return listFiles(artifactsPath).stream().anyMatch(file -> file.toString().endsWith("tests.jar"));
    }

    private void runTests(TestProfile testProfile, byte[] testConfig) {
        ProcessBuilder builder = testBuilder.apply(testProfile);
        {
            LogRecord record = new LogRecord(Level.INFO,
                                             String.format("Starting %s. Artifacts directory: %s Config file: %s\nCommand to run: %s\nEnv: %s\n",
                                                           testProfile.name(), artifactsPath, configFile,
                                                           String.join(" ", builder.command()),
                                                           builder.environment()));
            log.put(record.getSequenceNumber(), record);
            logger.log(record);
            log.put(record.getSequenceNumber(), record);
            logger.log(record);
        }

        boolean success;
        try {
            writeTestApplicationPom(testProfile);
            Files.write(configFile, testConfig);
            Files.write(settingsFile, settingsXml.getBytes());

            Process mavenProcess = builder.start();
            BufferedReader in = new BufferedReader(new InputStreamReader(mavenProcess.getInputStream()));
            HtmlLogger htmlLogger = new HtmlLogger();
            in.lines().forEach(line -> {
                LogRecord html = htmlLogger.toLog(line);
                log.put(html.getSequenceNumber(), html);
            });
            success = mavenProcess.waitFor() == 0;
        }
        catch (Exception exception) {
            LogRecord record = new LogRecord(SEVERE, "Failed to execute maven command: " + String.join(" ", builder.command()));
            record.setThrown(exception);
            logger.log(record);
            log.put(record.getSequenceNumber(), record);
            status = Status.ERROR;
            return;
        }
        status = success ? Status.SUCCESS : Status.FAILURE;
    }

    private void writeTestApplicationPom(TestProfile testProfile) throws IOException {
        List<Path> files = listFiles(artifactsPath);
        Path testJar = files.stream().filter(file -> file.toString().endsWith("tests.jar")).findFirst()
                       .orElseThrow(() -> new NoTestsException("No file ending with 'tests.jar' found under '" + artifactsPath + "'!"));
        String pomXml = PomXmlGenerator.generatePomXml(testProfile, files, testJar);
        testPath.toFile().mkdirs();
        Files.write(testPath.resolve("pom.xml"), pomXml.getBytes());
    }

    private static List<Path> listFiles(Path directory) {
        try (Stream<Path> element = Files.walk(directory)) {
            return element
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list files under " + directory, e);
        }
    }


    static class NoTestsException extends RuntimeException {
        private NoTestsException(String message) { super(message); }
    }

    static TestProfile toProfile(Suite suite) {
        switch (suite) {
            case SYSTEM_TEST: return TestProfile.SYSTEM_TEST;
            case STAGING_SETUP_TEST: return TestProfile.STAGING_SETUP_TEST;
            case STAGING_TEST: return TestProfile.STAGING_TEST;
            case PRODUCTION_TEST: return TestProfile.PRODUCTION_TEST;
            default: throw new IllegalArgumentException("Unknown test suite '" + suite + "'");
        }
    }

}
