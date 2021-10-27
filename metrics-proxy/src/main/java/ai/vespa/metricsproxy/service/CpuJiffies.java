// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

class CpuJiffies {

    private int cpuId;
    private long jiffies;

    CpuJiffies(String line) {
        parseLine(line);
    }

    private void parseLine(String line) {
        String elems[];
        String cpuId;
        long jiffies;

        elems = line.split("\\s+");
        cpuId = elems[0].substring(3);
        if (cpuId.length() == 0) {
            this.cpuId = -1;
        } else {
            this.cpuId = Integer.parseInt(cpuId);
        }

        jiffies = 0;
        for (int i = 1; i < elems.length; i++) {
            jiffies += Long.parseLong(elems[i].replaceAll("[\\n\\r]+", ""));
        }

        this.jiffies = jiffies;
    }

    public int getCpuId() {
        return cpuId;
    }

    public long getTotalJiffies() {
        return jiffies;
    }

}
