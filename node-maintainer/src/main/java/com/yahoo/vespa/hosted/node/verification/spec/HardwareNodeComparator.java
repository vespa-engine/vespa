// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;

/**
 * Compares two HardwareInfo objects and stores divergent values in a SpecVerificationReport
 *
 * @author olaaun
 * @author sgrostad
 */
public class HardwareNodeComparator {

    private static final double PERCENTAGE_THRESHOLD = 0.05;

    public static SpecVerificationReport compare(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware) {
        SpecVerificationReport specVerificationReport = new SpecVerificationReport();
        if (nodeRepoHardwareInfo == null || actualHardware == null) {
            return specVerificationReport;
        }
        setReportMetrics(nodeRepoHardwareInfo, actualHardware, specVerificationReport);
        return specVerificationReport;
    }

    private static void setReportMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecVerificationReport specVerificationReport) {
        setMemoryMetrics(nodeRepoHardwareInfo, actualHardware, specVerificationReport);
        setCpuMetrics(nodeRepoHardwareInfo, actualHardware, specVerificationReport);
        setDiskTypeMetrics(nodeRepoHardwareInfo, actualHardware, specVerificationReport);
        setDiskSpaceMetrics(nodeRepoHardwareInfo, actualHardware, specVerificationReport);
        setNetMetrics(nodeRepoHardwareInfo, actualHardware, specVerificationReport);
    }

    private static void setMemoryMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecVerificationReport specVerificationReport) {
        double expectedMemory = nodeRepoHardwareInfo.getMinMainMemoryAvailableGb();
        double actualMemory = actualHardware.getMinMainMemoryAvailableGb();
        if (belowThreshold(expectedMemory, actualMemory, PERCENTAGE_THRESHOLD)) {
            specVerificationReport.setActualMemoryAvailable(actualMemory);
        }
    }

    private static void setCpuMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecVerificationReport specVerificationReport) {
        int expectedCpuCores = nodeRepoHardwareInfo.getMinCpuCores();
        int actualCpuCores = actualHardware.getMinCpuCores();
        if (expectedCpuCores != actualCpuCores) {
            specVerificationReport.setActualcpuCores(actualCpuCores);
        }
    }

    private static void setDiskTypeMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecVerificationReport specVerificationReport) {
        DiskType expectedFastDisk = nodeRepoHardwareInfo.getDiskType();
        DiskType actualFastDisk = actualHardware.getDiskType();
        if (expectedFastDisk != null && actualFastDisk != null && expectedFastDisk != actualFastDisk) {
            specVerificationReport.setActualDiskType(actualFastDisk);
        }
    }

    private static void setDiskSpaceMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecVerificationReport specVerificationReport) {
        double expectedDiskSpace = nodeRepoHardwareInfo.getMinDiskAvailableGb();
        double actualDiskSpace = actualHardware.getMinDiskAvailableGb();
        if (belowThreshold(expectedDiskSpace, actualDiskSpace, PERCENTAGE_THRESHOLD)) {
            specVerificationReport.setActualDiskSpaceAvailable(actualDiskSpace);
        }
    }

    private static void setNetMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecVerificationReport specVerificationReport) {
        double expectedInterfaceSpeed = nodeRepoHardwareInfo.getInterfaceSpeedMbs();
        double actualInterfaceSpeed = actualHardware.getInterfaceSpeedMbs();
        if (expectedInterfaceSpeed > actualInterfaceSpeed) {
            specVerificationReport.setActualInterfaceSpeed(actualInterfaceSpeed);
        }

        if (nodeRepoHardwareInfo.isIpv6Connection() && !actualHardware.isIpv6Connection()) {
            specVerificationReport.setActualIpv6Connection(actualHardware.isIpv6Connection());
        }
    }

    private static boolean belowThreshold(double expected, double actual, double thresholdPercentage) {
        double lowerThresholdPercentage = 1 - thresholdPercentage;
        return actual < expected * lowerThresholdPercentage;
    }

}
