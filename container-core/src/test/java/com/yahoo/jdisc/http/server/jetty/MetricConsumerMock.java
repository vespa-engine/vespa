// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Module;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.MetricConsumer;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
class MetricConsumerMock {

    static final Metric.Context STATIC_CONTEXT = new Metric.Context() {};

    private final MetricConsumer mockitoMock = mock(MetricConsumer.class);

    MetricConsumerMock() {
        when(mockitoMock.createContext(anyMap())).thenReturn(STATIC_CONTEXT);
    }

    MetricConsumer mockitoMock() { return mockitoMock; }
    Module asGuiceModule() { return binder -> binder.bind(MetricConsumer.class).toInstance(mockitoMock); }

}
