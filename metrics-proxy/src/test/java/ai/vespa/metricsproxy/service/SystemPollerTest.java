// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Unknown
 */
public class SystemPollerTest {

    @Test
    public void testSystemPoller() {
        DummyService s = new DummyService(0, "id");
        List<VespaService> services = new ArrayList<>();
        services.add(s);

        SystemPoller poller = new SystemPoller(services, 60*5);
        assertThat(s.isAlive(), is(false));

        long n = poller.getPidJiffies(s);
        assertThat(n, is(0L));
        long[] memusage = poller.getMemoryUsage(s);
        assertThat(memusage[0], is(0L));
        assertThat(memusage[1], is(0L));
    }

    @Test
    public void testCPUJiffies() {
        String line = "cpu1  102180864 789 56766899 12800020140 1654757 0 0";
        CpuJiffies n = new CpuJiffies(line);
        assertThat(n.getCpuId(), is(1));
        assertThat(n.getTotalJiffies(), is(12960623449L));
    }

}
