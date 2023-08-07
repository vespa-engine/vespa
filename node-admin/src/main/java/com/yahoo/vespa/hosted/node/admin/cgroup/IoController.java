// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import ai.vespa.validation.Validation;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;

/**
 * Represents a cgroup v2 IO controller, i.e. all io.* files.
 *
 * @author freva
 */
public class IoController {
    private static final Logger logger = Logger.getLogger(IoController.class.getName());
    private final Cgroup cgroup;

    IoController(Cgroup cgroup) {
        this.cgroup = cgroup;
    }

    public record Device(int major, int minor) implements Comparable<Device> {
        public Device {
            // https://www.halolinux.us/kernel-architecture/representation-of-major-and-minor-numbers.html
            Validation.requireInRange(major, "device major", 0, 0xFFF);
            Validation.requireInRange(minor, "device minor", 0, 0xFFFFF);
        }

        private String toFileContent() { return major + ":" + minor; }
        private static Device fromString(String device) {
            String[] parts = device.split(":");
            return new Device(parseInt(parts[0]), parseInt(parts[1]));
        }

        @Override
        public int compareTo(Device o) {
            return major != o.major ? Integer.compare(major, o.major) : Integer.compare(minor, o.minor);
        }
    }

    /**
     * Defines max allowed IO:
     * <ul>
     *     <li><b>rbps</b>: Read bytes per seconds</li>
     *     <li><b>riops</b>: Read IO operations per seconds</li>
     *     <li><b>wbps</b>: Write bytes per seconds</li>
     *     <li><b>wiops</b>: Write IO operations per seconds</li>
     * </ul>.
     */
    public record Max(Size rbps, Size wbps, Size riops, Size wiops) {
        public static Max UNLIMITED = new Max(Size.max(), Size.max(), Size.max(), Size.max());

        // Keys can be specified in any order, this is the order they are outputted in from io.max
        // https://github.com/torvalds/linux/blob/c1a515d3c0270628df8ae5f5118ba859b85464a2/block/blk-throttle.c#L1541
        private String toFileContent() { return "rbps=%s wbps=%s riops=%s wiops=%s".formatted(rbps, wbps, riops, wiops); }

        public static Max fromString(String max) {
            String[] parts = max.split(" ");
            Size rbps = Size.max(), riops = Size.max(), wbps = Size.max(), wiops = Size.max();
            for (String part : parts) {
                if (part.isEmpty()) continue;
                String[] kv = part.split("=");
                if (kv.length != 2) throw new IllegalArgumentException("Invalid io.max format: " + max);
                switch (kv[0]) {
                    case "rbps" -> rbps = Size.from(kv[1]);
                    case "riops" -> riops = Size.from(kv[1]);
                    case "wbps" -> wbps = Size.from(kv[1]);
                    case "wiops" -> wiops = Size.from(kv[1]);
                    default -> throw new IllegalArgumentException("Unknown key " + kv[0]);
                }
            }
            return new Max(rbps, wbps, riops, wiops);
        }
    }

    /**
     * Returns the maximum allowed IO usage, by device, or empty if cgroup is not found.
     *
     * @see Max
     */
    public Optional<Map<Device, Max>> readMax() {
        return cgroup.readIfExists("io.max")
                     .map(content -> content
                             .lines()
                             .map(line -> {
                                 String[] parts = line.strip().split(" ", 2);
                                 return Map.entry(Device.fromString(parts[0]), Max.fromString(parts[1]));
                             })
                             .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public boolean updateMax(TaskContext context, Device device, Max max) {
        Max prevMax = readMax()
                .map(maxByDevice -> maxByDevice.get(device))
                .orElse(Max.UNLIMITED);
        if (prevMax.equals(max)) return false;

        UnixPath path = cgroup.unixPath().resolve("io.max");
        context.recordSystemModification(logger, "Updating %s for device %s from '%s' to '%s'",
                path, device.toFileContent(), prevMax.toFileContent(), max.toFileContent());
        path.writeUtf8File(device.toFileContent() + ' ' + max.toFileContent() + '\n');
        return true;
    }

}
