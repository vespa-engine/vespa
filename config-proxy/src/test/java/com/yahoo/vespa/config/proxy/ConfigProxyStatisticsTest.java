// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:musum@yahoo-inc.com">Harald Musum</a>
 * @since 5.1.9
 */
public class ConfigProxyStatisticsTest {

    @Test
    public void basic() {
        ConfigProxyStatistics statistics = new ConfigProxyStatistics();
        assertThat(statistics.getEventInterval(), is(ConfigProxyStatistics.defaultEventInterval));
        assertThat(statistics.processedRequests(), is(0L));
        assertThat(statistics.errors(), is(0L));
        assertThat(statistics.delayedResponses(), is(0L));

        statistics.delayedResponses(1);
        statistics.incProcessedRequests();
        statistics.incRpcRequests();
        statistics.incErrorCount();

        assertThat(statistics.processedRequests(), is(1L));
        assertThat(statistics.rpcRequests(), is(1L));
        assertThat(statistics.errors(), is(1L));
        assertThat(statistics.delayedResponses(), is(1L));

        statistics.decDelayedResponses();
        assertThat(statistics.delayedResponses(), is(0L));


        Long eventInterval = 1L;
        statistics = new ConfigProxyStatistics(eventInterval);
        assertThat(statistics.getEventInterval(), is(eventInterval));

        Thread t = new Thread(statistics);
        t.start();

        statistics.stop();
    }
}
