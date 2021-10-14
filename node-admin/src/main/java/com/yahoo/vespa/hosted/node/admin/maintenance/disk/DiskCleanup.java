// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.disk;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.DiskSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.PrioritizedFileAttributes;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author freva
 */
public class DiskCleanup {

    private static final Logger logger = Logger.getLogger(DiskCleanup.class.getName());
    private static final Comparator<PrioritizedFileAttributes> PRIORITIZED_FILE_ATTRIBUTES_COMPARATOR = Comparator
            .comparing(PrioritizedFileAttributes::priority)
            .thenComparingLong(f -> f.fileAttributes().size())
            .reversed();

    public boolean cleanup(TaskContext context, List<DiskCleanupRule> rules, long bytesToRemove) {
        if (bytesToRemove <= 0) return false;

        long[] btr = new long[] { bytesToRemove };
        List<Path> deletedPaths = new ArrayList<>();
        try {
            rules.stream()
                    .flatMap(rule -> rule.prioritize().stream())
                    .sorted(PRIORITIZED_FILE_ATTRIBUTES_COMPARATOR)
                    .takeWhile(fa -> btr[0] > 0)
                    .forEach(pfa -> {
                        if (uncheck(() -> Files.deleteIfExists(pfa.fileAttributes().path()))) {
                            btr[0] -= pfa.fileAttributes().size();
                            deletedPaths.add(pfa.fileAttributes().path());
                        }
                    });

        } finally {
            String wantedDeleteSize = DiskSize.of(bytesToRemove).asString();
            String deletedSize = DiskSize.of(bytesToRemove - btr[0]).asString();
            if (deletedPaths.size() > 20) {
                context.log(logger, "Deleted %d files (%s) because disk was getting full", deletedPaths.size(), deletedSize);
            } else if (deletedPaths.size() > 0) {
                context.log(logger, "Deleted %s because disk was getting full from: %s", deletedSize, deletedPaths);
            } else {
                context.log(logger, "Wanted to delete %s, but failed to find any files to delete", wantedDeleteSize);
            }
        }

        return !deletedPaths.isEmpty();
    }
}
