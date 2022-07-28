// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.jdisc.metric.MetricConsumerProvider;
import com.yahoo.jdisc.application.ContainerThread;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ContainerThreadFactoryTest {

    @Test
    void requireThatMetricConsumerProviderCanNotBeNull() {
        try {
            new ContainerThreadFactory(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatThreadsCreatedAreJDiscContainerThreads() {
        assertEquals(ContainerThread.class,
                new ContainerThreadFactory(Mockito.mock(MetricConsumerProvider.class))
                        .newThread(Mockito.mock(Runnable.class))
                        .getClass());
    }

    @Test
    void requireThatThreadFactoryCallsProvider() {
        MetricConsumerProvider provider = Mockito.mock(MetricConsumerProvider.class);
        ThreadFactory factory = new ContainerThreadFactory(provider);
        factory.newThread(Mockito.mock(Runnable.class));
        Mockito.verify(provider, Mockito.times(1)).newInstance();
        factory.newThread(Mockito.mock(Runnable.class));
        Mockito.verify(provider, Mockito.times(2)).newInstance();
    }
}
