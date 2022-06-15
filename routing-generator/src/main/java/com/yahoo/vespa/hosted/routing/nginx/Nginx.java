// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.jdisc.Metric;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.routing.Router;
import com.yahoo.vespa.hosted.routing.RoutingTable;
import com.yahoo.vespa.hosted.routing.status.RoutingStatus;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.concurrent.Sleeper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This loads a {@link RoutingTable} into a running Nginx process.
 *
 * @author mpolden
 */
public class Nginx implements Router {

    private static final Logger LOG = Logger.getLogger(Nginx.class.getName());
    private static final int EXEC_ATTEMPTS = 5;

    static final String GENERATED_UPSTREAMS_METRIC = "upstreams_generated";
    static final String CONFIG_RELOADS_METRIC = "upstreams_nginx_reloads";
    static final String OK_CONFIG_RELOADS_METRIC = "upstreams_nginx_reloads_succeeded";

    private final FileSystem fileSystem;
    private final ProcessExecuter processExecuter;
    private final Sleeper sleeper;
    private final Clock clock;
    private final RoutingStatus routingStatus;
    private final Metric metric;

    private final Object monitor = new Object();

    public Nginx(FileSystem fileSystem, ProcessExecuter processExecuter, Sleeper sleeper, Clock clock, RoutingStatus routingStatus, Metric metric) {
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.processExecuter = Objects.requireNonNull(processExecuter);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.clock = Objects.requireNonNull(clock);
        this.routingStatus = Objects.requireNonNull(routingStatus);
        this.metric = Objects.requireNonNull(metric);
    }

    @Override
    public void load(RoutingTable table) {
        synchronized (monitor) {
            try {
                table = table.routingMethod(RoutingMethod.sharedLayer4); // This router only supports layer 4 endpoints
                testConfig(table);
                loadConfig(table.asMap().size());
                gcConfig();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Write given routing table to a temporary config file and test it */
    private void testConfig(RoutingTable table) throws IOException {
        String config = NginxConfig.from(table, routingStatus);
        Files.createDirectories(NginxPath.root.in(fileSystem));
        atomicWriteString(NginxPath.temporaryConfig.in(fileSystem), config);

        // This retries config testing because it can fail due to external factors, such as hostnames not resolving in
        // DNS. Retrying can be removed if we switch to having only IP addresses in config
        retryingExec("/usr/bin/sudo /opt/vespa/bin/vespa-verify-nginx");
    }

    /** Load tested config into Nginx */
    private void loadConfig(int upstreamCount) throws IOException {
        Path configPath = NginxPath.config.in(fileSystem);
        Path tempConfigPath = NginxPath.temporaryConfig.in(fileSystem);
        try {
            String currentConfig = Files.readString(configPath);
            String newConfig = Files.readString(tempConfigPath);
            if (currentConfig.equals(newConfig)) {
                Files.deleteIfExists(tempConfigPath);
                return;
            }
            Path rotatedConfig = NginxPath.config.rotatedIn(fileSystem, clock.instant());
            atomicCopy(configPath, rotatedConfig);
        } catch (NoSuchFileException ignored) {
            // Fine, not enough files exist to compare or rotate
        }
        Files.move(tempConfigPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        metric.add(CONFIG_RELOADS_METRIC, 1, null);
        // Retry reload. Same rationale for retrying as in testConfig()
        LOG.info("Loading new configuration file from " + configPath);
        retryingExec("/usr/bin/sudo /opt/vespa/bin/vespa-reload-nginx");
        metric.add(OK_CONFIG_RELOADS_METRIC, 1, null);
        metric.set(GENERATED_UPSTREAMS_METRIC, upstreamCount, null);
    }

    /** Remove old config files */
    private void gcConfig() throws IOException {
        Instant oneWeekAgo = clock.instant().minus(Duration.ofDays(7));
        // Rotated files have the format <basename>-yyyy-MM-dd-HH:mm:ss.SSS
        String configBasename = NginxPath.config.in(fileSystem).getFileName().toString();
        try (var entries = Files.list(NginxPath.root.in(fileSystem))) {
            entries.filter(Files::isRegularFile)
                   .filter(path -> path.getFileName().toString().startsWith(configBasename))
                   .filter(path -> rotatedAt(path).map(instant -> instant.isBefore(oneWeekAgo))
                                                  .orElse(false))
                   .forEach(path -> Exceptions.uncheck(() -> Files.deleteIfExists(path)));
        }
    }

    /** Returns the time given path was rotated */
    private Optional<Instant> rotatedAt(Path path) {
        String[] parts = path.getFileName().toString().split("-", 2);
        if (parts.length != 2) return Optional.empty();
        return Optional.of(LocalDateTime.from(NginxPath.ROTATED_SUFFIX_FORMAT.parse(parts[1])).toInstant(ZoneOffset.UTC));
    }

    /** Run given command. Retries after a delay on failure */
    private void retryingExec(String command) {
        boolean success = false;
        for (int attempt = 1; attempt <= EXEC_ATTEMPTS; attempt++) {
            String errorMessage;
            try {
                Pair<Integer, String> result = processExecuter.exec(command);
                if (result.getFirst() == 0) {
                    success = true;
                    break;
                }
                errorMessage = result.getSecond();
            } catch (IOException e) {
                errorMessage = Exceptions.toMessageString(e);
            }
            Duration duration = Duration.ofSeconds((long) Math.pow(2, attempt));
            LOG.log(Level.WARNING, "Failed to run " + command + " on attempt " + attempt + ": " + errorMessage +
                                   ". Retrying in " + duration);
            sleeper.sleep(duration);
        }
        if (!success) {
            throw new RuntimeException("Failed to run " + command + " successfully after " + EXEC_ATTEMPTS +
                                       " attempts, giving up");
        }
    }

    /** Apply pathOperation to a temporary file, then atomically move the temporary file to path */
    private void atomicWrite(Path path, PathOperation pathOperation) throws IOException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(path.getParent(), "nginx", "");
            pathOperation.run(tempFile);
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private void atomicCopy(Path src, Path dst) throws IOException {
        atomicWrite(dst, (tempFile) -> Files.copy(src, tempFile,
                                                  StandardCopyOption.REPLACE_EXISTING,
                                                  StandardCopyOption.COPY_ATTRIBUTES));
    }

    private void atomicWriteString(Path path, String content) throws IOException {
        atomicWrite(path, (tempFile) -> Files.writeString(tempFile, content));
    }

    @FunctionalInterface
    private interface PathOperation {
        void run(Path path) throws IOException;
    }

}
