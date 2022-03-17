// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import com.google.common.jimfs.Jimfs;
import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.routing.RoutingTable;
import com.yahoo.vespa.hosted.routing.TestUtil;
import com.yahoo.vespa.hosted.routing.mock.RoutingStatusMock;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.concurrent.Sleeper;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class NginxTest {

    @Test
    public void load_routing_table() {
        NginxTester tester = new NginxTester();
        tester.clock.setInstant(Instant.parse("2022-01-01T15:00:00Z"));

        // Load routing table
        RoutingTable table0 = TestUtil.readRoutingTable("lbservices-config");
        tester.load(table0)
              .assertVerifiedConfig(1)
              .assertLoadedConfig(true)
              .assertConfigContents("nginx.conf")
              .assertTemporaryConfigRemoved(true)
              .assertMetric(Nginx.CONFIG_RELOADS_METRIC, 1)
              .assertMetric(Nginx.OK_CONFIG_RELOADS_METRIC, 1)
              .assertMetric(Nginx.GENERATED_UPSTREAMS_METRIC, 5);

        // Loading the same table again does nothing
        tester.load(table0)
              .assertVerifiedConfig(1)
              .assertLoadedConfig(false)
              .assertConfigContents("nginx.conf")
              .assertTemporaryConfigRemoved(true)
              .assertMetric(Nginx.CONFIG_RELOADS_METRIC, 1)
              .assertMetric(Nginx.OK_CONFIG_RELOADS_METRIC, 1)
              .assertMetric(Nginx.GENERATED_UPSTREAMS_METRIC, 5);

        // A new table is loaded
        Map<RoutingTable.Endpoint, RoutingTable.Target> newEntries = new HashMap<>(table0.asMap());
        newEntries.put(new RoutingTable.Endpoint("endpoint1", RoutingMethod.sharedLayer4),
                       RoutingTable.Target.create(ApplicationId.from("t1", "a1", "i1"),
                                                  ClusterSpec.Id.from("default"),
                                                  ZoneId.from("prod", "us-north-1"),
                                                  List.of(new RoutingTable.Real("host42", 4443, 1, true))));
        RoutingTable table1 = new RoutingTable(newEntries, 43);

        // Verification of new table fails enough times to exhaust retries
        tester.processExecuter.withFailCount(10);
        try {
            tester.load(table1);
            fail("Expected exception");
        } catch (Exception ignored) {}
        tester.assertVerifiedConfig(5)
              .assertLoadedConfig(false)
              .assertConfigContents("nginx.conf")
              .assertTemporaryConfigRemoved(false)
              .assertMetric(Nginx.CONFIG_RELOADS_METRIC, 1)
              .assertMetric(Nginx.OK_CONFIG_RELOADS_METRIC, 1);

        // Verification succeeds, with few enough failures
        tester.processExecuter.withFailCount(3);
        tester.load(table1)
              .assertVerifiedConfig(3)
              .assertLoadedConfig(true)
              .assertConfigContents("nginx-updated.conf")
              .assertTemporaryConfigRemoved(true)
              .assertRotatedFiles("nginxl4.conf-2022-01-01-15:00:00.000")
              .assertMetric(Nginx.CONFIG_RELOADS_METRIC, 2)
              .assertMetric(Nginx.OK_CONFIG_RELOADS_METRIC, 2);

        // Some time passes and new tables are loaded. Old rotated files are removed
        tester.clock.advance(Duration.ofDays(3));
        tester.load(table0);
        tester.clock.advance(Duration.ofDays(4).plusSeconds(1));
        tester.load(table1)
              .assertRotatedFiles("nginxl4.conf-2022-01-04-15:00:00.000",
                                  "nginxl4.conf-2022-01-08-15:00:01.000");
        tester.clock.advance(Duration.ofDays(4));
        tester.load(table1) // Same table is loaded again, which is a no-op, but old rotated files are still removed
              .assertRotatedFiles("nginxl4.conf-2022-01-08-15:00:01.000");
    }

    private static class NginxTester {

        private final FileSystem fileSystem =  Jimfs.newFileSystem();
        private final ManualClock clock = new ManualClock();
        private final RoutingStatusMock routingStatus = new RoutingStatusMock();
        private final ProcessExecuterMock processExecuter = new ProcessExecuterMock();
        private final MockMetric metric = new MockMetric();
        private final Nginx nginx = new Nginx(fileSystem, processExecuter, Sleeper.NOOP, clock, routingStatus, metric);

        public NginxTester load(RoutingTable table) {
            processExecuter.clearHistory();
            nginx.load(table);
            return this;
        }

        public NginxTester assertMetric(String name, double expected) {
            assertEquals("Metric " + name + " has expected value", expected, metric.metrics().get(name).get(Map.of()), Double.MIN_VALUE);
            return this;
        }

        public NginxTester assertConfigContents(String expectedConfig) {
            String expected = Exceptions.uncheck(() -> Files.readString(TestUtil.testFile(expectedConfig)));
            String actual = Exceptions.uncheck(() -> Files.readString(NginxPath.config.in(fileSystem)));
            assertEquals(expected, actual);
            return this;
        }

        public NginxTester assertTemporaryConfigRemoved(boolean removed) {
            Path path = NginxPath.temporaryConfig.in(fileSystem);
            assertEquals(path + (removed ? " does not exist" : " exists"), removed, !Files.exists(path));
            return this;
        }

        public NginxTester assertRotatedFiles(String... expectedRotatedFiles) {
            List<String> rotatedFiles = Exceptions.uncheck(() -> Files.list(NginxPath.root.in(fileSystem))
                                                                      .map(path -> path.getFileName().toString())
                                                                      .filter(filename -> filename.contains(".conf-"))
                                                                      .collect(Collectors.toList()));
            assertEquals(List.of(expectedRotatedFiles), rotatedFiles);
            return this;
        }

        public NginxTester assertVerifiedConfig(int times) {
            for (int i = 0; i < times; i++) {
                assertEquals("/usr/bin/sudo /opt/vespa/bin/vespa-verify-nginx", processExecuter.history().get(i));
            }
            return this;
        }

        public NginxTester assertLoadedConfig(boolean loaded) {
            String reloadCommand = "/usr/bin/sudo /opt/vespa/bin/vespa-reload-nginx";
            if (loaded) {
                assertEquals(reloadCommand, processExecuter.history().get(processExecuter.history().size() - 1));
            } else {
                assertTrue("Config is not loaded",
                           processExecuter.history.stream().noneMatch(command -> command.equals(reloadCommand)));
            }
            return this;
        }

    }

    private static class ProcessExecuterMock extends ProcessExecuter {

        private final List<String> history = new ArrayList<>();

        private int wantedFailCount = 0;
        private int currentFailCount = 0;

        public List<String> history() {
            return Collections.unmodifiableList(history);
        }

        public ProcessExecuterMock clearHistory() {
            history.clear();
            return this;
        }

        public ProcessExecuterMock withFailCount(int count) {
            this.wantedFailCount = count;
            this.currentFailCount = 0;
            return this;
        }

        @Override
        public Pair<Integer, String> exec(String command) {
            history.add(command);
            int exitCode = 0;
            String out = "";
            if (++currentFailCount <= wantedFailCount) {
                exitCode = 1;
                out = "failing to unit test";
            }
            return new Pair<>(exitCode, out);
        }

        @Override
        public Pair<Integer, String> exec(String[] command) {
            return exec(String.join(" ", command));
        }

    }

}
