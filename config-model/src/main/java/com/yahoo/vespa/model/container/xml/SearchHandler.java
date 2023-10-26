// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.List;

import static com.yahoo.container.bundle.BundleInstantiationSpecification.fromSearchAndDocproc;

/**
 * Component definition for {@link com.yahoo.search.handler.SearchHandler}
 *
 * @author bjorncs
 */
class SearchHandler extends ProcessingHandler<SearchChains> {

    static final Class<?> SEARCH_HANDLER = com.yahoo.search.handler.SearchHandler.class;
    static final Class<?> EXECUTION_FACTORY = com.yahoo.search.searchchain.ExecutionFactory.class;
    static final String HANDLER_CLASSNAME = SEARCH_HANDLER.getName();
    static final String EXECUTION_FACTORY_CLASSNAME = EXECUTION_FACTORY.getName();

    static final BundleInstantiationSpecification HANDLER_SPEC = fromSearchAndDocproc(HANDLER_CLASSNAME);
    static final BindingPattern DEFAULT_BINDING = SystemBindingPattern.fromHttpPath("/search/*");

    SearchHandler(DeployState ds,
                  ApplicationContainerCluster cluster,
                  List<BindingPattern> bindings,
                  Element threadpoolOptions) {
        super(cluster.getSearchChains(), HANDLER_SPEC, new Threadpool(ds, threadpoolOptions));
        bindings.forEach(this::addServerBindings);
    }

    static List<BindingPattern> bindingPattern(Collection<Integer> ports) {
        if (ports.isEmpty()) return List.of(DEFAULT_BINDING);
        return ports.stream()
                .map(s -> (BindingPattern)SystemBindingPattern.fromHttpPortAndPath(s, DEFAULT_BINDING.path()))
                .toList();
    }

    private static class Threadpool extends ContainerThreadpool {

        private final int threads;

        Threadpool(DeployState ds, Element options) {
            super(ds, "search-handler", options);
            threads = ds.featureFlags().searchHandlerThreadpool();
        }

        @Override
        public void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder) {
            builder.maxThreadExecutionTimeSeconds(190)
                    .keepAliveTime(5.0)
                    .maxThreads(-threads)
                    .minThreads(-threads)
                    .queueSize(-40);
        }

    }

}
