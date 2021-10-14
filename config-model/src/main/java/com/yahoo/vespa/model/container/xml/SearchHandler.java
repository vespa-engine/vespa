// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
            builder.maxThreads(-2).minThreads(-2).queueSize(-40);
        }


    }
}
