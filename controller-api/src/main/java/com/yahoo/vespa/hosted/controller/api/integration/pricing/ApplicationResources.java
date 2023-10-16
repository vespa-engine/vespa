package com.yahoo.vespa.hosted.controller.api.integration.pricing;

import java.math.BigDecimal;

import static java.math.BigDecimal.ZERO;

/**
 * @param applicationName    name of the application
 * @param vcpu               vcpus summed over all clusters, instances, zones
 * @param memoryGb           memory in Gb summed over all clusters, instances, zones
 * @param diskGb             disk in Gb summed over all clusters, instances, zones
 * @param gpuMemoryGb        GPU memory in Gb summed over all clusters, instances, zones
 * @param enclaveVcpu        vcpus summed over all clusters, instances, zones
 * @param enclaveMemoryGb    memory in Gb summed over all clusters, instances, zones
 * @param enclaveDiskGb      disk in Gb summed over all clusters, instances, zones
 * @param enclaveGpuMemoryGb GPU memory in Gb summed over all clusters, instances, zones
 */
public record ApplicationResources(String applicationName, BigDecimal vcpu, BigDecimal memoryGb, BigDecimal diskGb,
                                   BigDecimal gpuMemoryGb, BigDecimal enclaveVcpu, BigDecimal enclaveMemoryGb,
                                   BigDecimal enclaveDiskGb, BigDecimal enclaveGpuMemoryGb) {

    public static ApplicationResources create(String applicationName, BigDecimal vcpu, BigDecimal memoryGb,
                                              BigDecimal diskGb, BigDecimal gpuMemoryGb) {
        return new ApplicationResources(applicationName, vcpu, memoryGb, diskGb, gpuMemoryGb, ZERO, ZERO, ZERO, ZERO);
    }

    public static ApplicationResources createEnclave(String applicationName, BigDecimal vcpu, BigDecimal memoryGb,
                                                     BigDecimal diskGb, BigDecimal gpuMemoryGb) {
        return new ApplicationResources(applicationName, ZERO, ZERO, ZERO, ZERO, vcpu, memoryGb, diskGb, gpuMemoryGb);
    }

    public boolean enclave() { return enclaveVcpu().compareTo(ZERO) > 0; }

}
