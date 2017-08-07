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
        if (nodeRepoHardwareInfo.getInterfaceSpeedMbs() > actualHardware.getInterfaceSpeedMbs()) {
            specReportMetrics.setExpectedInterfaceSpeed(expectedInterfaceSpeed);
            specReportMetrics.setActualInterfaceSpeed(actualInterfaceSpeed);
        }

        if (nodeRepoHardwareInfo.isIpv6Connection() != actualHardware.isIpv6Connection()) {
            specReportMetrics.setActualIpv6Connection(actualHardware.isIpv6Connection());
            specReportMetrics.setExpectedIpv6Connection(nodeRepoHardwareInfo.isIpv6Connection());
        }

    private static boolean compareCPU(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalCPU = nodeRepoHardwareInfo.getMinCpuCores() == actualHardware.getMinCpuCores();
        specReportDimensions.setCpuCoresMatch(equalCPU);
        return equalCPU;
    }

    private static boolean compareMemory(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalMemory = insideThreshold(nodeRepoHardwareInfo.getMinMainMemoryAvailableGb(), actualHardware.getMinMainMemoryAvailableGb(), PERCENTAGE_THRESHOLD);
        specReportDimensions.setMemoryMatch(equalMemory);
        return equalMemory;
    }

    private static boolean compareNetInterface(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalNetInterfaceSpeed = nodeRepoHardwareInfo.getInterfaceSpeedMbs() <= actualHardware.getInterfaceSpeedMbs();
        boolean equalIpv6Interface = !nodeRepoHardwareInfo.getIpv6Interface() || actualHardware.getIpv6Interface();
        boolean equalIpv4Interface = !nodeRepoHardwareInfo.getIpv4Interface() || actualHardware.getIpv4Interface();
        boolean equalIpv6Connection = !nodeRepoHardwareInfo.isIpv6Connection() || actualHardware.isIpv6Connection();
        specReportDimensions.setNetInterfaceSpeedMatch(equalNetInterfaceSpeed);
        specReportDimensions.setIpv6Match(equalIpv6Interface);
        specReportDimensions.setIpv4Match(equalIpv4Interface);
        return  equalIpv4Interface && equalIpv6Connection && equalNetInterfaceSpeed && equalIpv6Interface;
    }

    private static boolean compareDisk(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalDiskType = nodeRepoHardwareInfo.getDiskType() == actualHardware.getDiskType();
        boolean equalDiskSize = insideThreshold(nodeRepoHardwareInfo.getMinDiskAvailableGb(), actualHardware.getMinDiskAvailableGb(), PERCENTAGE_THRESHOLD);
        specReportDimensions.setDiskTypeMatch(equalDiskType);
        specReportDimensions.setDiskAvailableMatch(equalDiskSize);
        return equalDiskType && equalDiskSize;
    }

    private static boolean outsideThreshold(double value1, double value2 , double thresholdPercentage) {
        double lowerThresholdPercentage = 1 - thresholdPercentage;
        double upperThresholdPercentage = 1 + thresholdPercentage;
        return value1 < lowerThresholdPercentage * value2 || value1 > upperThresholdPercentage * value2;
    }

}
