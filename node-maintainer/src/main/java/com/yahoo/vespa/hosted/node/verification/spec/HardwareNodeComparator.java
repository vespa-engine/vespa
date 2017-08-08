package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;
import com.yahoo.vespa.hosted.node.verification.spec.report.VerificationReport;

/**
 * Created by olaa on 04/07/2017.
 * Compares two HardwareInfo objects and stores divergent values in a VerificationReport
 */
public class HardwareNodeComparator {

    private static final double PERCENTAGE_THRESHOLD = 0.05;

    public static VerificationReport compare(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware) {
        VerificationReport verificationReport = new VerificationReport();
        if (nodeRepoHardwareInfo == null || actualHardware == null) {
            return verificationReport;
        }
        setReportMetrics(nodeRepoHardwareInfo, actualHardware, verificationReport);
        return verificationReport;
    }

    private static void setReportMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, VerificationReport verificationReport) {
        setMemoryMetrics(nodeRepoHardwareInfo, actualHardware, verificationReport);
        setCpuMetrics(nodeRepoHardwareInfo, actualHardware, verificationReport);
        setDiskTypeMetrics(nodeRepoHardwareInfo, actualHardware, verificationReport);
        setDiskSpaceMetrics(nodeRepoHardwareInfo, actualHardware, verificationReport);
        setNetMetrics(nodeRepoHardwareInfo, actualHardware, verificationReport);
    }

    private static void setMemoryMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, VerificationReport verificationReport) {
        double expectedMemory = nodeRepoHardwareInfo.getMinMainMemoryAvailableGb();
        double actualMemory = actualHardware.getMinMainMemoryAvailableGb();
        if (outsideThreshold(expectedMemory, actualMemory, PERCENTAGE_THRESHOLD)) {
            verificationReport.setActualMemoryAvailable(actualMemory);
        }
    }

    private static void setCpuMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, VerificationReport verificationReport) {
        int expectedCpuCores = nodeRepoHardwareInfo.getMinCpuCores();
        int actualCpuCores = actualHardware.getMinCpuCores();
        if (expectedCpuCores != actualCpuCores) {
            verificationReport.setActualcpuCores(actualCpuCores);
        }
    }

    private static void setDiskTypeMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, VerificationReport verificationReport) {
        DiskType expectedFastDisk = nodeRepoHardwareInfo.getDiskType();
        DiskType actualFastDisk = actualHardware.getDiskType();
        if (expectedFastDisk != null && actualFastDisk != null && expectedFastDisk != actualFastDisk) {
            verificationReport.setActualDiskType(actualFastDisk);
        }
    }

    private static void setDiskSpaceMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, VerificationReport verificationReport) {
        double expectedDiskSpace = nodeRepoHardwareInfo.getMinDiskAvailableGb();
        double actualDiskSpace = actualHardware.getMinDiskAvailableGb();
        if (outsideThreshold(expectedDiskSpace, actualDiskSpace, PERCENTAGE_THRESHOLD)) {
            verificationReport.setActualDiskSpaceAvailable(actualDiskSpace);
        }
    }

    private static void setNetMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, VerificationReport verificationReport) {
        double expectedInterfaceSpeed = nodeRepoHardwareInfo.getInterfaceSpeedMbs();
        double actualInterfaceSpeed = actualHardware.getInterfaceSpeedMbs();
        if (expectedInterfaceSpeed > actualInterfaceSpeed) {
            verificationReport.setActualInterfaceSpeed(actualInterfaceSpeed);
        }

        if (nodeRepoHardwareInfo.isIpv6Connection() && !actualHardware.isIpv6Connection()) {
            verificationReport.setActualIpv6Connection(actualHardware.isIpv6Connection());
        }
    }

    private static boolean outsideThreshold(double value1, double value2 , double thresholdPercentage) {
        double lowerThresholdPercentage = 1 - thresholdPercentage;
        double upperThresholdPercentage = 1 + thresholdPercentage;
        return value1 < lowerThresholdPercentage * value2 || value1 > upperThresholdPercentage * value2;
    }

}
