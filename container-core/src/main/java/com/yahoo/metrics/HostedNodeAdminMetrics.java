package com.yahoo.metrics;

/**
 * @author yngveaasheim
 */

// TODO: Move to hosted repo.
public enum HostedNodeAdminMetrics implements VespaMetrics {

    // System metrics
    CPU_UTIL("cpu.util", Unit.PERCENTAGE, "CPU utilisation"),
    CPU_SYS_UTIL("cpu.sys.util", Unit.PERCENTAGE, "System CPU utilisation"),
    CPU_THROTTLED_TIME("cpu.throttled_time.rate", Unit.PERCENTAGE, "Part of the time CPU is exhausted (CPU throttling enforced)"),
    CPU_THROTTLED_CPU_TIME("cpu.throttled_cpu_time.rate", Unit.PERCENTAGE, "Part of the time CPU is exhausted (CPU throttling enforced)"),
    CPU_VCPUS("cpu.vcpus", Unit.ITEM, "Number of virtual CPU threads allocation to the node"),
    DISK_LIMIT("disk.limit", Unit.BYTE, "Amount of disk space available on the node"),
    DISK_USED("disk.used", Unit.BYTE, "Amount of disk space used by the node"),
    DISK_UTIL("disk.util", Unit.PERCENTAGE, "Disk space utilisation"),
    MEM_LIMIT("mem.limit", Unit.BYTE, "Amount of memory available on the node"),
    MEM_USED("mem.used", Unit.BYTE, "Amount of memory used by the node"),
    MEM_UTIL("mem.util", Unit.PERCENTAGE, "Memory utilisation"),
    MEM_TOTAL_USED("mem_total.used", Unit.BYTE, "Total amount of memory used by the node, including OS buffer caches"),
    MEM_TOTAL_UTIL("mem_total.util", Unit.PERCENTAGE, "Total memory utilisation"),
    GPU_UTIL("gpu.util", Unit.PERCENTAGE, "GPU utilisation"),
    GPU_MEM_USED("gpu.memory.used", Unit.BYTE, "GPU memory used"),
    GPU_MEM_TOTAL("gpu.memory.total", Unit.BYTE, "GPU memory available"),


    // Network metrics
    NET_IN_BYTES("net.in.bytes", Unit.BYTE, "Network bytes received (rxBytes) (COUNT metric)"),
    NET_IN_ERROR("net.in.errors", Unit.FAILURE, "Network receive errors (rxErrors)"),
    NET_IN_DROPPED("net.in.dropped", Unit.PACKET, "Inbound network packets dropped (rxDropped)"),
    NET_OUT_BYTES("net.in.bytes", Unit.BYTE, "Network bytes sent (txBytes) (COUNT metric)"),
    NET_OUT_ERROR("net.in.errors", Unit.FAILURE, "Network send errors (txErrors)"),
    NET_OUT_DROPPED("net.in.dropped", Unit.PACKET, "Outbound network packets dropped (txDropped)"),
    BANDWIDTH_LIMIT("bandwidth.limit", Unit.BYTE_PER_SECOND, "Available network bandwidth");

    private final String name;
    private final Unit unit;
    private final String description;

    HostedNodeAdminMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}

