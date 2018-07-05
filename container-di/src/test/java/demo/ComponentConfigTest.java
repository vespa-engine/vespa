// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package demo;

import com.yahoo.config.test.ThreadPoolConfig;
import com.yahoo.container.di.componentgraph.Provider;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertNotNull;


/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class ComponentConfigTest extends Base {
    public static class ThreadPoolExecutorProvider implements Provider<Executor> {
        private ExecutorService executor;

        public ThreadPoolExecutorProvider(ThreadPoolConfig config) {
            executor = Executors.newFixedThreadPool(config.numThreads());
        }

        @Override
        public Executor get() {
            return executor;
        }

        @Override
        public void deconstruct() {
            executor.shutdown();
        }
    }

    @Test
    public void require_that_non_components_can_be_configured() {
        register(ThreadPoolExecutorProvider.class);
        addConfig(new ThreadPoolConfig(new ThreadPoolConfig.Builder().numThreads(4)),
                toId(ThreadPoolExecutorProvider.class));
        complete();

        Executor executor = getInstance(Executor.class);
        assertNotNull(executor);
    }
}
