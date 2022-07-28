// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.disk;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.PrioritizedFileAttributes;
import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.Priority;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class CoredumpCleanupRuleTest {

    private final FileSystem fileSystem = TestFileSystem.create();

    @Test
    void for_container_test() throws IOException {
        Path path = fileSystem.getPath("/test/path");
        DiskCleanupRule rule = CoredumpCleanupRule.forContainer(path);

        assertPriorities(rule, Map.of());

        createFile(path.resolve("core1"), Instant.ofEpochSecond(232));
        assertPriorities(rule, Map.of("/test/path/core1", Priority.MEDIUM));

        createFile(path.resolve("core2"), Instant.ofEpochSecond(123));
        assertPriorities(rule, Map.of(
                "/test/path/core2", Priority.MEDIUM,
                "/test/path/core1", Priority.HIGHEST));

        createFile(path.resolve("vespa-proton-bin.core.325"), Instant.ofEpochSecond(456));
        createFile(path.resolve("vespa-distributor.core.764"), Instant.ofEpochSecond(256));
        var expected = Map.of(
                "/test/path/core2", Priority.HIGHEST,
                "/test/path/core1", Priority.HIGHEST,
                "/test/path/vespa-proton-bin.core.325", Priority.HIGHEST,
                "/test/path/vespa-distributor.core.764", Priority.MEDIUM);
        assertPriorities(rule, expected);

        // processing core has no effect on this
        Files.createDirectories(path.resolve("processing/abcd-1234"));
        createFile(path.resolve("processing/abcd-1234/core5"), Instant.ofEpochSecond(67));
        assertPriorities(rule, expected);
    }

    @Test
    void for_host_test() throws IOException {
        Path path = fileSystem.getPath("/test/path");
        DiskCleanupRule rule = CoredumpCleanupRule.forHost(path);

        assertPriorities(rule, Map.of());

        createFile(path.resolve("h123a/abcd-1234/dump_core1"), Instant.parse("2020-04-21T19:21:00Z"));
        createFile(path.resolve("h123a/abcd-1234/metadata.json"), Instant.parse("2020-04-21T19:26:00Z"));
        assertPriorities(rule, Map.of("/test/path/h123a/abcd-1234/dump_core1", Priority.MEDIUM));

        createFile(path.resolve("h123a/abcd-efgh/dump_core1"), Instant.parse("2020-04-21T07:13:00Z"));
        createFile(path.resolve("h123a/56ad-af42/dump_vespa-distributor.321"), Instant.parse("2020-04-21T23:37:00Z"));
        createFile(path.resolve("h123a/4324-a23d/dump_core2"), Instant.parse("2020-04-22T04:56:00Z"));
        createFile(path.resolve("h123a/8534-7da3/dump_vespa-proton-bin.123"), Instant.parse("2020-04-19T15:35:00Z"));

        // Also create a core for a second container: h123b
        createFile(path.resolve("h123b/db1a-ab34/dump_core1"), Instant.parse("2020-04-21T07:01:00Z"));
        createFile(path.resolve("h123b/7392-59ad/dump_vespa-proton-bin.342"), Instant.parse("2020-04-22T12:05:00Z"));

        assertPriorities(rule, Map.of(
                "/test/path/h123a/abcd-1234/dump_core1", Priority.HIGH,
                "/test/path/h123a/abcd-efgh/dump_core1", Priority.HIGH,

                // Although it is the oldest core of the day for h123a, it is the first one that starts with vespa-
                "/test/path/h123a/56ad-af42/dump_vespa-distributor.321", Priority.MEDIUM,
                "/test/path/h123a/4324-a23d/dump_core2", Priority.MEDIUM,
                "/test/path/h123a/8534-7da3/dump_vespa-proton-bin.123", Priority.MEDIUM,
                "/test/path/h123b/db1a-ab34/dump_core1", Priority.MEDIUM,
                "/test/path/h123b/7392-59ad/dump_vespa-proton-bin.342", Priority.MEDIUM
        ));
    }

    private static void createFile(Path path, Instant instant) throws IOException {
        Files.createDirectories(path.getParent());
        Files.createFile(path);
        Files.setLastModifiedTime(path, FileTime.from(instant));
    }

    private static void assertPriorities(DiskCleanupRule rule, Map<String, Priority> expected) {
        Map<String, Priority> actual = rule.prioritize().stream()
                .collect(Collectors.toMap(pfa -> pfa.fileAttributes().path().toString(), PrioritizedFileAttributes::priority));

        assertEquals(new TreeMap<>(expected), new TreeMap<>(actual));
    }
}