package com.yahoo.config.provision;

/**
 * Resources used by a sidecar container.
 * 
 * @author glebashnik
 */
public record SidecarResources(double maxCpu, double minCpu, double memoryGiB, boolean hasGpu) {
    public SidecarResources withMaxCpu(double maxCpu) {
        return new SidecarResources(maxCpu, minCpu, memoryGiB, hasGpu);
    }

    public SidecarResources withMinCpu(double minCpu) {
        return new SidecarResources(maxCpu, minCpu, memoryGiB, hasGpu);
    }

    public SidecarResources withMemoryGiB(double memoryGiB) {
        return new SidecarResources(maxCpu, minCpu, memoryGiB, hasGpu);
    }

    public SidecarResources withGpu(boolean hasGpu) {
        return new SidecarResources(maxCpu, minCpu, memoryGiB, hasGpu);
    }
}