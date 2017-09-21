// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import java.util.List;

/**
 * Cost data model for a cluster. I.e one cluster within one Vespa application in one zone.
 *
 * @author smorgrav
 */
// TODO: Make immutable
// TODO: Enforce constraints
// TODO: Document content
public class CostCluster {

    private int count;
    private String resource;
    private double utilization;
    private int tco;
    private String flavor;
    private int waste;
    private String type;
    private double utilMem;
    private double utilCpu;
    private double utilDisk;
    private double utilDiskBusy;
    private double usageMem;
    private double usageCpu;
    private double usageDisk;
    private double usageDiskBusy;
    private List<String> hostnames;
    
    /** Create an empty (invalid) cluster cost */
    public CostCluster() {}

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public List<String> getHostnames() {
        return hostnames;
    }

    public void setHostnames(List<String> hostnames) {
        this.hostnames = hostnames;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public int getTco() {
        return tco;
    }

    public void setTco(int tco) {
        this.tco = tco;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getUtilization() {
        return utilization;
    }

    public void setUtilization(float utilization) {
        validateUtilRatio(utilization);
        this.utilization = utilization;
    }

    public int getWaste() {
        return waste;
    }

    public void setWaste(int waste) {
        this.waste = waste;
    }

    public double getUsageCpu() {
        return usageCpu;
    }

    public void setUsageCpu(float usageCpu) {
        validateUsageRatio(usageCpu);
        this.usageCpu = usageCpu;
    }

    public double getUsageDisk() {
        return usageDisk;
    }

    public void setUsageDisk(float usageDisk) {
        validateUsageRatio(usageDisk);
        this.usageDisk = usageDisk;
    }

    public double getUsageMem() {
        return usageMem;
    }

    public void setUsageMem(float usageMem) {
        validateUsageRatio(usageMem);
        this.usageMem = usageMem;
    }

    public double getUtilCpu() {
        return utilCpu;
    }

    public void setUtilCpu(float utilCpu) {
        validateUtilRatio(utilCpu);
        this.utilCpu = utilCpu;
    }

    public double getUtilDisk() {
        return utilDisk;
    }

    public void setUtilDisk(float utilDisk) {
        validateUtilRatio(utilDisk);
        this.utilDisk = utilDisk;
    }

    public double getUtilMem() {
        return utilMem;
    }

    public void setUtilMem(float utilMem) {
        validateUsageRatio(utilMem);
        this.utilMem = utilMem;
    }

    public double getUsageDiskBusy() {
        return usageDiskBusy;
    }

    public void setUsageDiskBusy(float usageDiskBusy) {
        validateUsageRatio(usageDiskBusy);
        this.usageDiskBusy = usageDiskBusy;
    }

    public double getUtilDiskBusy() {
        return utilDiskBusy;
    }

    public void setUtilDiskBusy(float utilDiskBusy) {
        validateUtilRatio(utilDiskBusy);
        this.utilDiskBusy = utilDiskBusy;
    }

    private void validateUsageRatio(float ratio) {
        if (ratio < 0) throw new IllegalArgumentException("Usage cannot be negative");
        if (ratio > 1) throw new IllegalArgumentException("Usage exceed 1 (using more than it has available)");
    }

    private void validateUtilRatio(float ratio) {
        if (ratio < 0) throw new IllegalArgumentException("Utilization cannot be negative");
    }
}
