package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;
import com.yahoo.vespa.hosted.node.verification.spec.yamasreport.SpecReportDimensions;
import com.yahoo.vespa.hosted.node.verification.spec.yamasreport.SpecReportMetrics;
import com.yahoo.vespa.hosted.node.verification.spec.yamasreport.YamasSpecReport;

/**
 * Created by olaa on 04/07/2017.
 * Compares two HardwareInfo objects
 */
public class HardwareNodeComparator {

    public static YamasSpecReport compare(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware) {
        Boolean equalHardware = true;
        YamasSpecReport yamasSpecReport = new YamasSpecReport();
        SpecReportDimensions specReportDimensions = new SpecReportDimensions();
        SpecReportMetrics specReportMetrics = new SpecReportMetrics();

        if (nodeRepoHardwareInfo == null || actualHardware == null) {
            return yamasSpecReport;
        }

        setReportMetrics(nodeRepoHardwareInfo, actualHardware, specReportMetrics);

        equalHardware &= compareMemory(nodeRepoHardwareInfo, actualHardware, specReportDimensions);
        equalHardware &= compareCPU(nodeRepoHardwareInfo, actualHardware, specReportDimensions);
        equalHardware &= compareNetInterface(nodeRepoHardwareInfo, actualHardware, specReportDimensions);
        equalHardware &= compareDisk(nodeRepoHardwareInfo, actualHardware, specReportDimensions);

        specReportMetrics.setMatch(equalHardware);
        yamasSpecReport.setDimensions(specReportDimensions);
        yamasSpecReport.setMetrics(specReportMetrics);

        return yamasSpecReport;
    }

    private static void setReportMetrics(HardwareInfo node, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        setMemoryMetrics(node, actualHardware, specReportMetrics);
        setCpuMetrics(node, actualHardware, specReportMetrics);
        setDiskTypeMetrics(node, actualHardware, specReportMetrics);
        setDiskSpaceMetrics(node, actualHardware, specReportMetrics);
        setNetMetrics(node, actualHardware, specReportMetrics);
    }

    private static void setMemoryMetrics(HardwareInfo node, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        double expectedMemory = node.getMinMainMemoryAvailableGb();
        double actualMemory = actualHardware.getMinMainMemoryAvailableGb();
        if (!insideThreshold(expectedMemory, actualMemory)) {
            specReportMetrics.setExpectedMemoryAvailable(expectedMemory);
            specReportMetrics.setActualMemoryAvailable(actualMemory);
        }
    }

    private static void setCpuMetrics(HardwareInfo node, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        int expectedCpuCores = node.getMinCpuCores();
        int actualCpuCores = actualHardware.getMinCpuCores();
        if (expectedCpuCores != actualCpuCores) {
            specReportMetrics.setExpectedcpuCores(expectedCpuCores);
            specReportMetrics.setActualcpuCores(actualCpuCores);
        }
    }

    private static void setDiskTypeMetrics(HardwareInfo node, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        DiskType expectedFastDisk = node.getDiskType();
        DiskType actualFastDisk = actualHardware.getDiskType();
        if (expectedFastDisk != null && actualFastDisk != null && expectedFastDisk != actualFastDisk) {
            specReportMetrics.setExpectedDiskType(expectedFastDisk);
            specReportMetrics.setActualDiskType(actualFastDisk);
        }
    }

    private static void setDiskSpaceMetrics(HardwareInfo node, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        double expectedDiskSpace = node.getMinDiskAvailableGb();
        double actualDiskSpace = actualHardware.getMinDiskAvailableGb();
        if (!insideThreshold(expectedDiskSpace, actualDiskSpace)) {
            specReportMetrics.setExpectedDiskSpaceAvailable(expectedDiskSpace);
            specReportMetrics.setActualDiskSpaceAvailable(actualDiskSpace);
        }
    }

    private static void setNetMetrics(HardwareInfo node, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        double expectedInterfaceSpeed = node.getInterfaceSpeedMbs();
        double actualInterfaceSpeed = actualHardware.getInterfaceSpeedMbs();
        if (!insideThreshold(expectedInterfaceSpeed, actualInterfaceSpeed)) {
            specReportMetrics.setExpectedInterfaceSpeed(expectedInterfaceSpeed);
            specReportMetrics.setActualInterfaceSpeed(actualInterfaceSpeed);
        }
    }

    private static boolean compareCPU(HardwareInfo node, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalCPU = node.getMinCpuCores() == actualHardware.getMinCpuCores();
        specReportDimensions.setCpuCoresMatch(equalCPU);
        return equalCPU;
    }

    private static boolean compareMemory(HardwareInfo node, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalMemory = insideThreshold(node.getMinMainMemoryAvailableGb(), actualHardware.getMinMainMemoryAvailableGb());
        specReportDimensions.setMemoryMatch(equalMemory);
        return equalMemory;
    }

    private static boolean compareNetInterface(HardwareInfo node, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalNetInterfaceSpeed = insideThreshold(node.getInterfaceSpeedMbs(), actualHardware.getInterfaceSpeedMbs());
        boolean equalIpv6 = node.getIpv6Connectivity() == actualHardware.getIpv6Connectivity();
        boolean equalIpv4 = node.getIpv4Connectivity() == actualHardware.getIpv4Connectivity();
        specReportDimensions.setNetInterfaceSpeedMatch(equalNetInterfaceSpeed);
        specReportDimensions.setIpv6Match(equalIpv6);
        specReportDimensions.setIpv4Match(equalIpv4);
        return equalNetInterfaceSpeed && equalIpv6 && equalIpv4;

    }

    private static boolean compareDisk(HardwareInfo node, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalDiskType = node.getDiskType() == actualHardware.getDiskType();
        boolean equalDiskSize = insideThreshold(node.getMinDiskAvailableGb(), actualHardware.getMinDiskAvailableGb());
        specReportDimensions.setDiskTypeMatch(equalDiskType);
        specReportDimensions.setDiskAvailableMatch(equalDiskSize);
        return equalDiskType && equalDiskSize;
    }

    private static boolean insideThreshold(double value1, double value2) {
        double lowerThresholdPercentage = 0.8;
        double upperThresholdPercentage = 1.2;
        return value1 > lowerThresholdPercentage * value2 && value1 < upperThresholdPercentage * value2;
    }

}
