// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.disk;

import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.PrioritizedFileAttributes;
import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.Priority;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.FileAttributes;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameStartsWith;

/**
 * @author freva
 */
public class CoredumpCleanupRule {

    private static final Comparator<FileAttributes> CORE_DUMP_FILE_ATTRIBUTE_COMPARATOR = Comparator
            .comparing((FileAttributes fa) -> !fa.filename().contains("vespa-"))
            .thenComparing(FileAttributes::lastModifiedTime);

    public static DiskCleanupRule forContainer(Path containerCrashPath) {
        return new ContainerCoredumpCleanupRule(containerCrashPath);
    }

    public static DiskCleanupRule forHost(Path processedCoredumpsPath) {
        return new HostCoredumpCleanupRule(processedCoredumpsPath);
    }

    /** Assigns MEDIUM priority to the oldest, unprocessed coredump and HIGHEST for the remaining */
    private static class ContainerCoredumpCleanupRule implements DiskCleanupRule {
        private final Path containerCrashPath;

        private ContainerCoredumpCleanupRule(Path containerCrashPath) {
            this.containerCrashPath = containerCrashPath;
        }

        @Override
        public Collection<PrioritizedFileAttributes> prioritize() {
            List<FileAttributes> fileAttributes = FileFinder.files(containerCrashPath)
                    .maxDepth(1).stream()
                    .sorted(CORE_DUMP_FILE_ATTRIBUTE_COMPARATOR)
                    .collect(Collectors.toList());

            return mapFirstAndRemaining(fileAttributes, Priority.MEDIUM, Priority.HIGHEST).collect(Collectors.toList());
        }
    }

    /** Assigns MEDIUM priority to the first coredump of the day for each container, HIGH for the remaining */
    private static class HostCoredumpCleanupRule implements DiskCleanupRule {
        private final Path processedCoredumpsPath;

        private HostCoredumpCleanupRule(Path processedCoredumpsPath) {
            this.processedCoredumpsPath = processedCoredumpsPath;
        }

        @Override
        public Collection<PrioritizedFileAttributes> prioritize() {
            Map<String, List<FileAttributes>> fileAttributesByContainerDay = FileFinder.files(processedCoredumpsPath)
                    .match(nameStartsWith(CoredumpHandler.COREDUMP_FILENAME_PREFIX))
                    .stream()
                    .sorted(CORE_DUMP_FILE_ATTRIBUTE_COMPARATOR)
                    .collect(Collectors.groupingBy(
                            // Group FileAttributes by string [container-name]_[day of year], e.g. zt00534-v6-2_234
                            fa -> containerNameFromProcessedCoredumpPath(fa.path()) + "_" + dayOfYear(fa.lastModifiedTime()),
                            Collectors.collectingAndThen(
                                    Collectors.toCollection(ArrayList::new),
                                    l -> { l.sort(CORE_DUMP_FILE_ATTRIBUTE_COMPARATOR); return l; } )));

            return fileAttributesByContainerDay.values().stream()
                    .flatMap(fa -> mapFirstAndRemaining(fa, Priority.MEDIUM, Priority.HIGH))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Maps list of FileAttributes into list of PrioritizedFileAttributes where the first FileAttribute is given
     * {@code first} priority, while the remaining FileAttributes are given {@code remaining} priority */
    private static Stream<PrioritizedFileAttributes> mapFirstAndRemaining(List<FileAttributes> fileAttributes, Priority first, Priority remaining) {
        return IntStream.range(0, fileAttributes.size())
                .mapToObj(i -> new PrioritizedFileAttributes(fileAttributes.get(i), i == 0 ? first : remaining));
    }

    /** Extracts container-name from path under processed-coredumps or empty string */
    private static String containerNameFromProcessedCoredumpPath(Path path) {
        if (path.getNameCount() < 3) return ""; // Path is too short
        return path.getName(path.getNameCount() - 3).toString();
    }

    /** Returns day number of the year (1-365 (or 366 for leap years)) */
    private static int dayOfYear(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).get(ChronoField.DAY_OF_YEAR);
    }
}
