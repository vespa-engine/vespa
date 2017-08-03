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

    private static final double PERCENTAGE_THRESHOLD = 0.05;

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

    private static void setReportMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        setMemoryMetrics(nodeRepoHardwareInfo, actualHardware, specReportMetrics);
        setCpuMetrics(nodeRepoHardwareInfo, actualHardware, specReportMetrics);
        setDiskTypeMetrics(nodeRepoHardwareInfo, actualHardware, specReportMetrics);
        setDiskSpaceMetrics(nodeRepoHardwareInfo, actualHardware, specReportMetrics);
        setNetMetrics(nodeRepoHardwareInfo, actualHardware, specReportMetrics);
    }

    private static void setMemoryMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        double expectedMemory = nodeRepoHardwareInfo.getMinMainMemoryAvailableGb();
        double actualMemory = actualHardware.getMinMainMemoryAvailableGb();
        if (!insideThreshold(expectedMemory, actualMemory, PERCENTAGE_THRESHOLD)) {
            specReportMetrics.setExpectedMemoryAvailable(expectedMemory);
            specReportMetrics.setActualMemoryAvailable(actualMemory);
        }
    }

    private static void setCpuMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        int expectedCpuCores = nodeRepoHardwareInfo.getMinCpuCores();
        int actualCpuCores = actualHardware.getMinCpuCores();
        if (expectedCpuCores != actualCpuCores) {
            specReportMetrics.setExpectedcpuCores(expectedCpuCores);
            specReportMetrics.setActualcpuCores(actualCpuCores);
        }
    }

    private static void setDiskTypeMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        DiskType expectedFastDisk = nodeRepoHardwareInfo.getDiskType();
        DiskType actualFastDisk = actualHardware.getDiskType();
        if (expectedFastDisk != null && actualFastDisk != null && expectedFastDisk != actualFastDisk) {
            specReportMetrics.setExpectedDiskType(expectedFastDisk);
            specReportMetrics.setActualDiskType(actualFastDisk);
        }
    }

    private static void setDiskSpaceMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        double expectedDiskSpace = nodeRepoHardwareInfo.getMinDiskAvailableGb();
        double actualDiskSpace = actualHardware.getMinDiskAvailableGb();
        if (!insideThreshold(expectedDiskSpace, actualDiskSpace, PERCENTAGE_THRESHOLD)) {
            specReportMetrics.setExpectedDiskSpaceAvailable(expectedDiskSpace);
            specReportMetrics.setActualDiskSpaceAvailable(actualDiskSpace);
        }
    }

    private static void setNetMetrics(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportMetrics specReportMetrics) {
        double expectedInterfaceSpeed = nodeRepoHardwareInfo.getInterfaceSpeedMbs();
        double actualInterfaceSpeed = actualHardware.getInterfaceSpeedMbs();
        //if (!insideThreshold(expectedInterfaceSpeed, actualInterfaceSpeed, PERCENTAGE_THRESHOLD)) {
            specReportMetrics.setExpectedInterfaceSpeed(expectedInterfaceSpeed);
            specReportMetrics.setActualInterfaceSpeed(actualInterfaceSpeed);
        //} TODO uncomment this if wanted

        if (nodeRepoHardwareInfo.isIpv6Connection() != actualHardware.isIpv6Connection()) {
            specReportMetrics.setActualIpv6Connection(actualHardware.isIpv6Connection());
            specReportMetrics.setExpectedIpv6Connection(nodeRepoHardwareInfo.isIpv6Connection());
        }
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
        boolean equalNetInterfaceSpeed = insideThreshold(nodeRepoHardwareInfo.getInterfaceSpeedMbs(), actualHardware.getInterfaceSpeedMbs(), PERCENTAGE_THRESHOLD);
        boolean equalIpv6Interface = nodeRepoHardwareInfo.getIpv6Interface() == actualHardware.getIpv6Interface();
        boolean equalIpv4Interface = nodeRepoHardwareInfo.getIpv4Interface() == actualHardware.getIpv4Interface();
        boolean equalIpv6Connection = nodeRepoHardwareInfo.isIpv6Connection() == actualHardware.isIpv6Connection();
        specReportDimensions.setNetInterfaceSpeedMatch(equalNetInterfaceSpeed);
        specReportDimensions.setIpv6Match(equalIpv6Interface);
        specReportDimensions.setIpv4Match(equalIpv4Interface);
        return  equalIpv4Interface && equalIpv6Connection; // && equalNetInterfaceSpeed && equalIpv6Interface; TODO include these if wanted.
    }

    private static boolean compareDisk(HardwareInfo nodeRepoHardwareInfo, HardwareInfo actualHardware, SpecReportDimensions specReportDimensions) {
        boolean equalDiskType = nodeRepoHardwareInfo.getDiskType() == actualHardware.getDiskType();
        boolean equalDiskSize = insideThreshold(nodeRepoHardwareInfo.getMinDiskAvailableGb(), actualHardware.getMinDiskAvailableGb(), PERCENTAGE_THRESHOLD);
        specReportDimensions.setDiskTypeMatch(equalDiskType);
        specReportDimensions.setDiskAvailableMatch(equalDiskSize);
        return equalDiskType && equalDiskSize;
    }

    private static boolean insideThreshold(double value1, double value2 , double thresholdPercentage) {
        double lowerThresholdPercentage = 1 - thresholdPercentage;
        double upperThresholdPercentage = 1 + thresholdPercentage;
        return value1 > lowerThresholdPercentage * value2 && value1 < upperThresholdPercentage * value2;
    }

}
