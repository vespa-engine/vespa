// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.MetricId;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Unknown
 */
public class SystemPollerTest {
    private static final double DELTA = 0.0000000000001;

    private static final String [] perProcStats = {
            "131 (java) S 115 115 85 34816 115 1077952512 16819285 69606636 25448 8182 1310952 380185 256300 41075 20 0 69 0 39175 16913702912 2004355 18446744073709551615 94843481096192 94843481100104 140737227872512 0 0 0 0 4096 16796879 0 0 0 17 1 0 0 2 0 0 94843483200760 94843483201552 94843503726592 140737227877827 140737227878876 140737227878876 140737227882433 0\n",
            "131 (java) S 115 115 85 34816 115 1077952512 16819325 69606636 25480 8182 1311491 380694 256300 41075 20 0 69 0 39175 16913702912 2004353 18446744073709551615 94843481096192 94843481100104 140737227872512 0 0 0 0 4096 16796879 0 0 0 17 1 0 0 2 0 0 94843483200760 94843483201552 94843503726592 140737227877827 140737227878876 140737227878876 140737227882433 0\n"
    };
    private static final String [] totalStats = {
            String.join("\n",
                    "cpu  2262158 26 745678 139770640 28866 0 21092 0 0 0",
                    "cpu0 294842 1 83365 17489116 3671 0 2563 0 0 0",
                    "cpu1 273560 3 107315 17416254 3952 0 2970 0 0 0",
                    "cpu2 287921 1 100781 17444671 4149 0 2760 0 0 0",
                    "cpu3 292114 5 100488 17451934 4137 0 2427 0 0 0",
                    "cpu4 255834 1 91932 17502801 3267 0 2544 0 0 0",
                    "cpu5 280032 0 84827 17509603 3561 0 2641 0 0 0",
                    "cpu6 274831 12 96846 17459038 3189 0 2531 0 0 0",
                    "cpu7 303021 0 80121 17497220 2937 0 2653 0 0 0",
                    "intr 122075142 123 0 0 70 40110 0 0 0 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 986 0 1164882 0 18416 17418 0 3157177 1795096 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0",
                    "ctxt 169897491",
                    "btime 1634013028",
                    "processes 214086",
                    "procs_running 1",
                    "procs_blocked 0",
                    "softirq 32035299 0 4526992 8 5339012 1156237 0 37 10929205 0 10083808"),
            String.join("\n",
                    "cpu  2262521 26 746460 140037592 28870 0 21094 0 0 0",
                    "cpu0 294872 1 83431 17522597 3672 0 2564 0 0 0",
                    "cpu1 273619 3 107436 17449509 3952 0 2971 0 0 0",
                    "cpu2 287976 1 100893 17477999 4149 0 2760 0 0 0",
                    "cpu3 292150 5 100601 17485270 4137 0 2427 0 0 0",
                    "cpu4 255880 1 92024 17536168 3267 0 2544 0 0 0",
                    "cpu5 280088 0 84937 17542968 3564 0 2641 0 0 0",
                    "cpu6 274877 12 96944 17492406 3189 0 2531 0 0 0",
                    "cpu7 303058 0 80192 17530672 2937 0 2653 0 0 0",
                    "intr 122204990 123 0 0 70 40202 0 0 0 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 988 0 1165034 0 18438 17442 0 3160615 1797273 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0",
                    "ctxt 170103457",
                    "btime 1634013028",
                    "processes 214098",
                    "procs_running 2",
                    "procs_blocked 0",
                    "softirq 32061301 0 4533497 8 5339359 1156384 0 37 10947075 0 10084941")
    };
    private static final long [] PER_PROC_JIFFIES = {1691137, 1692185};

    @Test
    public void testSystemPoller() {
        DummyService s = new DummyService(0, "id");
        List<VespaService> services = new ArrayList<>();
        services.add(s);

        assertFalse(s.isAlive());

        long n = SystemPoller.getPidJiffies(s);
        assertEquals(0L, n);
        long[] memusage = SystemPoller.getMemoryUsage(s);
        assertEquals(0L, memusage[0]);
        assertEquals(0L, memusage[1]);
    }

    private static final String smaps =
            "00400000-004de000 r-xp 00000000 fe:01 670312                             /usr/bin/bash\n" +
            "Size:                888 kB\n" +
            "KernelPageSize:        4 kB\n" +
            "MMUPageSize:           4 kB\n" +
            "Rss:                 824 kB\n" +
            "Pss:                 150 kB\n" +
            "Shared_Clean:        824 kB\n" +
            "Shared_Dirty:          0 kB\n" +
            "Private_Clean:         0 kB\n" +
            "Private_Dirty:         0 kB\n" +
            "Referenced:          824 kB\n" +
            "Anonymous:             0 kB\n" +
            "LazyFree:              0 kB\n" +
            "AnonHugePages:         0 kB\n" +
            "ShmemPmdMapped:        0 kB\n" +
            "FilePmdMapped:         0 kB\n" +
            "Shared_Hugetlb:        0 kB\n" +
            "Private_Hugetlb:       0 kB\n" +
            "Swap:                  0 kB\n" +
            "SwapPss:               0 kB\n" +
            "Locked:                0 kB\n" +
            "THPeligible:    0\n" +
            "VmFlags: rd ex mr mw me dw \n" +
            "006dd000-006de000 r--p 000dd000 fe:01 670312                             /usr/bin/bash\n" +
            "Size:                  4 kB\n" +
            "KernelPageSize:        4 kB\n" +
            "MMUPageSize:           4 kB\n" +
            "Rss:                   4 kB\n" +
            "Pss:                   4 kB\n" +
            "Shared_Clean:          0 kB\n" +
            "Shared_Dirty:          0 kB\n" +
            "Private_Clean:         4 kB\n" +
            "Private_Dirty:         0 kB\n" +
            "Referenced:            4 kB\n" +
            "Anonymous:             4 kB\n" +
            "LazyFree:              0 kB\n" +
            "AnonHugePages:         0 kB\n" +
            "ShmemPmdMapped:        0 kB\n" +
            "FilePmdMapped:         0 kB\n" +
            "Shared_Hugetlb:        0 kB\n" +
            "Private_Hugetlb:       0 kB\n" +
            "Swap:                  0 kB\n" +
            "SwapPss:               0 kB\n" +
            "Locked:                0 kB\n" +
            "THPeligible:    0\n" +
            "VmFlags: rd mr mw me dw ac \n";

    @Test
    public void testSmapsParsing() throws IOException {
        BufferedReader br = new BufferedReader(new StringReader(smaps));
        long[] memusage = SystemPoller.getMemoryUsage(br);
        assertEquals(913408L, memusage[0]);
        assertEquals(847872L, memusage[1]);
    }

    @Ignore
    @Test
    public void benchmarkSmapsParsing() throws IOException {
        for (int i=0; i < 100000; i++) {
            BufferedReader br = new BufferedReader(new StringReader(smaps));
            long[] memusage = SystemPoller.getMemoryUsage(br);
            assertEquals(913408L, memusage[0]);
            assertEquals(847872L, memusage[1]);
        }
    }

    @Test
    public void testPerProcessJiffies() {
        assertEquals(PER_PROC_JIFFIES[0], SystemPoller.getPidJiffies(new BufferedReader(new StringReader(perProcStats[0]))));
        assertEquals(PER_PROC_JIFFIES[1], SystemPoller.getPidJiffies(new BufferedReader(new StringReader(perProcStats[1]))));
    }

    @Test
    public
    void testTotalJiffies() {
        SystemPoller.JiffiesAndCpus first = SystemPoller.getTotalSystemJiffies(new BufferedReader(new StringReader(totalStats[0])));
        SystemPoller.JiffiesAndCpus second = SystemPoller.getTotalSystemJiffies(new BufferedReader(new StringReader(totalStats[1])));
        assertEquals(8, first.cpus);
        assertEquals(first.cpus, second.cpus);
        assertEquals(142828460L, first.jiffies);
        assertEquals(143096563L, second.jiffies);
        assertEquals(0.05601124593795943, first.ratioSingleCoreJiffies(1000000), DELTA);
        assertEquals(0.05590630433241083, second.ratioSingleCoreJiffies(1000000), DELTA);
        SystemPoller.JiffiesAndCpus diff = second.diff(first);
        assertEquals(8, diff.cpus);
        assertEquals(268103L, diff.jiffies);
        assertEquals(2.9839278187860634, diff.ratioSingleCoreJiffies(100000), DELTA);
        assertEquals(0.3729909773482579, diff.ratioJiffies(100000), DELTA);
    }

    @Test
    public void testCPUJiffies() {
        String line = "cpu1  102180864 789 56766899 12800020140 1654757 0 0";
        CpuJiffies n = new CpuJiffies(line);
        assertEquals(1, n.getCpuId());
        assertEquals(12960623449L, n.getTotalJiffies());
    }

    @Test
    public void testMetricsComputation() {
        SystemPoller.JiffiesAndCpus prev = SystemPoller.getTotalSystemJiffies(new BufferedReader(new StringReader(totalStats[0])));
        Map<VespaService, Long> lastCpuJiffiesMetrics = new HashMap<>();
        VespaService s1 = new VespaService("s1", "cfgId_1");
        List<VespaService> services = List.of(s1);
        lastCpuJiffiesMetrics.put(s1, SystemPoller.getPidJiffies(new BufferedReader(new StringReader(perProcStats[0]))));

        SystemPoller.JiffiesAndCpus next = SystemPoller.updateMetrics(prev, Instant.ofEpochSecond(1),
                new SystemPoller.GetJiffies() {
                    @Override
                    public SystemPoller.JiffiesAndCpus getTotalSystemJiffies() {
                        return SystemPoller.getTotalSystemJiffies(new BufferedReader(new StringReader(totalStats[1])));
                    }

                    @Override
                    public long getJiffies(VespaService service) {
                        return SystemPoller.getPidJiffies(new BufferedReader(new StringReader(perProcStats[1])));
                    }
                },
                services, lastCpuJiffiesMetrics);

        assertEquals(8, prev.cpus);
        assertEquals(prev.cpus, next.cpus);
        assertEquals(142828460L, prev.jiffies);
        assertEquals(143096563L, next.jiffies);
        SystemPoller.JiffiesAndCpus diff = next.diff(prev);

        Metrics m = s1.getSystemMetrics();
        List<Metric> metricList = m.list();
        assertEquals(4, metricList.size());
        assertEquals(new Metric(MetricId.toMetricId("memory_virt"), 0L, 1), metricList.get(0));
        assertEquals(new Metric(MetricId.toMetricId("memory_rss"), 0L, 1), metricList.get(1));
        long diffProcJiffies = (PER_PROC_JIFFIES[1] - PER_PROC_JIFFIES[0]);
        double expected = 100.0*diff.ratioSingleCoreJiffies(diffProcJiffies);
        assertEquals(3.127156354087795, expected, DELTA);
        double expected_util = 100.0*diff.ratioJiffies(diffProcJiffies);
        assertEquals(0.3908945442609743, expected_util, DELTA);
        assertEquals(new Metric(MetricId.toMetricId("cpu"), expected, 1), metricList.get(2));
        assertEquals(new Metric(MetricId.toMetricId("cpu_util"), expected_util, 1), metricList.get(3));
    }

}
