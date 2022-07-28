// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.disk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.FileAttributes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.PrioritizedFileAttributes;
import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.Priority;
import static org.mockito.Mockito.mock;

/**
 * @author freva
 */
public class LinearCleanupRuleTest {

    @Test
    void basic() {
        assertRule(Map.of(), Priority.LOWEST, Priority.HIGHEST);

        assertRule(Map.of(0.0, Priority.LOW, 0.5, Priority.LOW, 1.0, Priority.LOW), Priority.LOW, Priority.LOW);
        assertRule(Map.of(0.0, Priority.LOW, 0.5, Priority.MEDIUM, 1.0, Priority.MEDIUM), Priority.LOW, Priority.MEDIUM);

        assertRule(Map.of(
                        -5.0, Priority.LOW,
                        0.0, Priority.LOW,
                        0.2, Priority.LOW,
                        0.35, Priority.MEDIUM,
                        0.65, Priority.MEDIUM,
                        0.8, Priority.HIGH,
                        1.0, Priority.HIGH,
                        5.0, Priority.HIGH),
                Priority.LOW, Priority.HIGH);
    }

    @Test
    void fail_if_high_priority_lower_than_low() {
        assertThrows(IllegalArgumentException.class, () -> {
            assertRule(Map.of(), Priority.HIGHEST, Priority.LOWEST);
        });
    }

    private static void assertRule(Map<Double, Priority> expectedPriorities, Priority low, Priority high) {
        Map<FileAttributes, Double> fileAttributesByScore = expectedPriorities.keySet().stream()
                .collect(Collectors.toMap(score -> mock(FileAttributes.class), score -> score));
        LinearCleanupRule rule = new LinearCleanupRule(
                () -> List.copyOf(fileAttributesByScore.keySet()), fileAttributesByScore::get, low, high);

        Map<Double, Priority> actualPriorities = rule.prioritize().stream()
                .collect(Collectors.toMap(pfa -> fileAttributesByScore.get(pfa.fileAttributes()), PrioritizedFileAttributes::priority));
        assertEquals(expectedPriorities, actualPriorities);
    }
}