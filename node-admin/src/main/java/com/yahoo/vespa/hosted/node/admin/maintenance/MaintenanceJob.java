package com.yahoo.vespa.hosted.node.admin.maintenance;

/**
 * @author valerijf
 */
public class MaintenanceJob {
    private String[] args;

    public MaintenanceJob(String[] args) {
        this.args = args;
    }

    public String[] getArgs() {
        return args;
    }
}
