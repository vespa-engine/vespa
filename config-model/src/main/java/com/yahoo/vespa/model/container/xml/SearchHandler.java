// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;

import java.util.List;

/**
 * Component definition for {@link com.yahoo.search.handler.SearchHandler}
 *
 * @author bjorncs
 */
class SearchHandler extends ProcessingHandler<SearchChains> {

    static final String HANDLER_CLASS = com.yahoo.search.handler.SearchHandler.class.getName();
    static final BindingPattern DEFAULT_BINDING = SystemBindingPattern.fromHttpPath("/search/*");

    SearchHandler(ApplicationContainerCluster cluster,
                  List<BindingPattern> bindings,
                  ContainerThreadpool.UserOptions threadpoolOptions) {
        super(cluster.getSearchChains(), HANDLER_CLASS);
        bindings.forEach(this::addServerBindings);
        Threadpool threadpool = new Threadpool(cluster, threadpoolOptions);
        inject(threadpool);
        addComponent(threadpool);
    }

    private static class Threadpool extends ContainerThreadpool {
        private final ApplicationContainerCluster cluster;

        Threadpool(ApplicationContainerCluster cluster, UserOptions options) {
            super("search-handler", options);
            this.cluster = cluster;
        }

        @Override
        public void getConfig(ContainerThreadpoolConfig.Builder builder) {
            super.getConfig(builder);

            builder.maxThreadExecutionTimeSeconds(190);
            builder.keepAliveTime(5.0);

            // User options overrides below configuration
            if (hasUserOptions()) return;

            double vcpu = cluster.vcpu().orElse(0);
            if (vcpu == 0) {
                builder.maxThreads(500);
                builder.minThreads(500);
                builder.queueSize(0);
            } else {
                // Controls max number of concurrent requests per container
                int workerThreads = Math.max(8, (int)Math.ceil(vcpu * 2.0));
                builder.maxThreads(workerThreads);
                builder.minThreads(workerThreads);

                // This controls your burst handling capability.
                // 0 => No extra burst handling beyond you max concurrent requests (maxthreads).
                // N => N times max concurrent requests as a buffer for handling bursts
                builder.queueSize((int)(workerThreads * 40.0));
            }
        }


    }
}
