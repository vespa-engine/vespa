// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.MetricConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class ForwardingMetricConsumerTest {

    @Test
    void requireThatAllMethodsAreForwarded() {
        MetricConsumer fooConsumer = Mockito.mock(MetricConsumer.class);
        Metric.Context fooCtx = Mockito.mock(Metric.Context.class);
        Mockito.when(fooConsumer.createContext(Mockito.<Map<String, ?>>any())).thenReturn(fooCtx);

        MetricConsumer barConsumer = Mockito.mock(MetricConsumer.class);
        Metric.Context barCtx = Mockito.mock(Metric.Context.class);
        Mockito.when(barConsumer.createContext(Mockito.<Map<String, ?>>any())).thenReturn(barCtx);

        MetricConsumer fwdConsumer = new ForwardingMetricConsumer(new MetricConsumer[]{fooConsumer, barConsumer});

        Map<String, ?> properties = new HashMap<>();
        Metric.Context ctx = fwdConsumer.createContext(properties);
        assertNotNull(ctx);
        Mockito.verify(fooConsumer, Mockito.times(1)).createContext(properties);
        Mockito.verify(barConsumer, Mockito.times(1)).createContext(properties);

        fwdConsumer.add("a", 69, ctx);
        Mockito.verify(fooConsumer, Mockito.times(1)).add("a", 69, fooCtx);
        Mockito.verify(barConsumer, Mockito.times(1)).add("a", 69, barCtx);

        fwdConsumer.set("b", 96, ctx);
        Mockito.verify(fooConsumer, Mockito.times(1)).set("b", 96, fooCtx);
        Mockito.verify(barConsumer, Mockito.times(1)).set("b", 96, barCtx);
    }
}
