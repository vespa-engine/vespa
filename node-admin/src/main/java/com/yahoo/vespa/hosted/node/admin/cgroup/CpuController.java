// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;

/**
 * Represents a cgroup v2 CPU controller, i.e. all files named cpu.*
 *
 * @author hakonhall
 */
public class CpuController {
    private static final Logger logger = Logger.getLogger(CpuController.class.getName());

    private final ControlGroup cgroup;

    CpuController(ControlGroup cgroup) {
        this.cgroup = cgroup;
    }

    /**
     * The maximum bandwidth limit of the format "QUOTA PERIOD", which indicates that the cgroup may consume
     * up to QUOTA in each PERIOD duration. A quota of "max" indicates no limit.
     */
    public record Max(Size quota, int period) {
        public String toFileContent() { return quota + " " + period; }
    }

    /**
     * Returns the maximum CPU usage, or empty if cgroup is not found.
     *
     * @see Max
     */
    public Optional<Max> readMax() {
        return cgroup.unixPath()
                     .resolve("cpu.max")
                     .readUtf8FileIfExists()
                     .map(content -> {
                         String[] parts = content.strip().split(" ");
                         return new Max(Size.from(parts[0]), parseInt(parts[1]));
                     });
    }

    /**
     * Update CPU quota and period for the given container ID.  Set quota to -1 value for unlimited.
     *
     * @see #readMax()
     * @see Max
     */
    public boolean updateMax(NodeAgentContext context, int quota, int period) {
        Max max = new Max(quota < 0 ? Size.max() : Size.from(quota), period);
        return convergeFileContent(context, "cpu.max", max.toFileContent());
    }

    /** @return The weight in the range [1, 10000], or empty if not found. */
    private Optional<Integer> readWeight() {
        return cgroup.unixPath()
                     .resolve("cpu.weight")
                     .readUtf8FileIfExists()
                     .map(content -> parseInt(content.strip()));
    }

    /** @return The number of shares allocated to this cgroup for purposes of CPU time scheduling, or empty if not found. */
    public Optional<Integer> readShares() {
        return readWeight().map(CpuController::weightToShares);
    }

    public boolean updateShares(NodeAgentContext context, int shares) {
        return convergeFileContent(context, "cpu.weight", Integer.toString(sharesToWeight(shares)));
    }

    // Must be same as in crun: https://github.com/containers/crun/blob/72c6e60ade0e4716fe2d8353f0d97d72cc8d1510/src/libcrun/cgroup.c#L3061
    // TODO: Migrate to weights
    public static int sharesToWeight(int shares) { return (int) (1 + ((shares - 2L) * 9999) / 262142); }
    public static int weightToShares(int weight) { return (int) (2 + ((weight - 1L) * 262142) / 9999); }

    private boolean convergeFileContent(NodeAgentContext context, String filename, String content) {
        UnixPath path = cgroup.unixPath().resolve(filename);
        String currentContent = path.readUtf8File().strip();
        if (currentContent.equals(content)) return false;

        context.recordSystemModification(logger, "Updating " + path + " from " + currentContent + " to " + content);
        path.writeUtf8File(content);
        return true;
    }

    public enum StatField {
        TOTAL_USAGE_USEC("usage_usec"),
        USER_USAGE_USEC("user_usec"),
        SYSTEM_USAGE_USEC("system_usec"),
        TOTAL_PERIODS("nr_periods"),
        THROTTLED_PERIODS("nr_throttled"),
        THROTTLED_TIME_USEC("throttled_usec");

        private final String name;

        StatField(String name) {
            this.name = name;
        }

        long parseValue(String value) {
            return Long.parseLong(value);
        }

        static Optional<StatField> fromField(String fieldName) {
            return Arrays.stream(values())
                         .filter(field -> fieldName.equals(field.name))
                         .findFirst();
        }
    }

    public Map<StatField, Long> readStats() {
        return cgroup.unixPath()
                     .resolve("cpu.stat")
                     .readAllLines()
                     .stream()
                     .map(line -> line.split("\\s+"))
                     .filter(parts -> parts.length == 2)
                     .flatMap(parts -> StatField.fromField(parts[0]).stream().map(field -> new Pair<>(field, field.parseValue(parts[1]))))
                     .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

}
