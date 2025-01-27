package ai.vespa.metrics.set;

import ai.vespa.metrics.Unit;
import ai.vespa.metrics.VespaMetrics;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static ai.vespa.metrics.Suffix.count;
import static ai.vespa.metrics.Suffix.last;
import static ai.vespa.metrics.Suffix.max;
import static ai.vespa.metrics.Suffix.sum;

public enum MicrometerMetrics implements VespaMetrics {
    JVM_MEMORY_COMMITTED("jvm.memory.committed",Unit.BYTE, "The amount of memory in bytes that is committed for the Java virtual machine to use"),
    JVM_MEMORY_MAX("jvm.memory.max", Unit.BYTE, "The maximum amount of memory in bytes that can be used for memory management"),
    JVM_MEMORY_USED("jvm.memory.used",Unit.BYTE, "The amount of used memory"),
    JVM_BUFFER_COUNT("jvm.buffer.count",Unit.BUFFER, "An estimate of the number of buffers in the pool"),
    JVM_BUFFER_MEMORY_USED("jvm.buffer.memory.used",Unit.BYTE, "An estimate of the memory that the Java virtual machine is using for this buffer pool"),
    JVM_BUFFER_TOTAL_CAPACITY("jvm.buffer.total.capacity",Unit.BYTE, "An estimate of the total capacity of the buffers in this pool"),
    JVM_CLASSES_LOADED("jvm.classes.loaded",Unit.CLASS, "The number of classes that are currently loaded in the Java virtual machine"),
    JVM_CLASSES_UNLOADED("jvm.classes.unloaded",Unit.CLASS, "The total number of classes unloaded since the Java virtual machine has started execution"),
    JVM_GC_PAUSE("jvm.gc.pause",Unit.MILLISECOND, "Time spent in GC pause"),
    JVM_THREADS_LIVE("jvm.threads.live",Unit.THREAD, "The current number of live threads including both daemon and non-daemon threads"),
    JVM_THREADS_STATES("jvm.threads.states",Unit.THREAD, "The current number of threads (in each state)"),
    JVM_GC_OVERHEAD("jvm.gc.overhead",Unit.PERCENTAGE, "An approximation of the percent of CPU time used by GC activities"),
    JVM_MEMORY_USAGE_AFTER_GC("jvm.memory.usage.after.gc",Unit.PERCENTAGE, "The percentage of long-lived heap pool used after the last GC event");

    MicrometerMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    private final String name;
    private final Unit unit;
    private final String description;

    @Override public String baseName() { return name; }
    @Override public Unit unit() { return unit; }
    @Override public String description() { return description; }

    public static MetricSet asMetricSet() {
        return new MetricSet.Builder("default-micrometer")
                .metric(JVM_MEMORY_COMMITTED, EnumSet.of(sum, count, max))
                .metric(JVM_MEMORY_MAX, EnumSet.of(sum, count, max))
                .metric(JVM_MEMORY_USED, EnumSet.of(sum, count, max))
                .metric(JVM_BUFFER_COUNT, EnumSet.of(sum, count, max))
                .metric(JVM_BUFFER_MEMORY_USED, EnumSet.of(sum, count, max))
                .metric(JVM_BUFFER_TOTAL_CAPACITY, EnumSet.of(sum, count, max))
                .metric(JVM_CLASSES_LOADED, EnumSet.of(sum, count, max))
                .metric(JVM_CLASSES_UNLOADED, EnumSet.of(last))
                .metric(JVM_GC_PAUSE, EnumSet.of(sum, count, max))
                .metric(JVM_THREADS_LIVE, EnumSet.of(sum, count, max))
                .metric(JVM_THREADS_STATES, EnumSet.of(sum, count, max))
                .metric(JVM_MEMORY_COMMITTED, EnumSet.of(sum, count, max))
                .metric(JVM_GC_OVERHEAD, EnumSet.of(sum, count, max))
                .metric(JVM_MEMORY_USAGE_AFTER_GC, EnumSet.of(sum, count, max))
                .build();
    }


}
