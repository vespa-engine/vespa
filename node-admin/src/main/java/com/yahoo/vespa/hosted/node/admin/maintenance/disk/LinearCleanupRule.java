// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.disk;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.FileAttributes;

/**
 * Prioritizes files by first scoring them with the given scoring function and then mapping the scores to a
 * priority within the given range.
 * The priority room is evenly split between given lowest and highest priority for range [0, 1.0). Scores below 0
 * are assigned lowest, while scores at or higher than 1 are assigned highest priority.
 *
 * Typical use-case is for log files. The scoring function calculates the file age and normalizes it by dividing it
 * by expected max age of log files. The oldest logs will then by prioritized by highest given priority.
 *
 * @author freva
 */
public class LinearCleanupRule implements DiskCleanupRule {
    private final Supplier<List<FileAttributes>> lister;
    private final Function<FileAttributes, Priority> prioritizer;

    public LinearCleanupRule(Supplier<List<FileAttributes>> lister,
                             Function<FileAttributes, Double> scorer, Priority lowest, Priority highest) {
        if (lowest.ordinal() > highest.ordinal())
            throw new IllegalArgumentException("Lowest priority: " + lowest + " is higher than highest priority: " + highest);

        this.lister = lister;

        Priority[] values = Priority.values();
        int range = highest.ordinal() - lowest.ordinal() + 1;
        this.prioritizer = fa -> {
            int ordinal = (int) (lowest.ordinal() + scorer.apply(fa) * range);
            return values[Math.max(lowest.ordinal(), Math.min(highest.ordinal(), ordinal))];
        };
    }

    @Override
    public Collection<PrioritizedFileAttributes> prioritize() {
        return lister.get().stream()
                .map(fa -> new PrioritizedFileAttributes(fa, prioritizer.apply(fa)))
                .collect(Collectors.toList());
    }
}
