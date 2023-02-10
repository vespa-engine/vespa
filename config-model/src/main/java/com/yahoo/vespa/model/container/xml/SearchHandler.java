// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;

import java.util.List;
import java.util.Optional;

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
    static final BindingPattern DEFAULT_BINDING = bindingPattern(Optional.empty());

    SearchHandler(ApplicationContainerCluster cluster,
                  List<BindingPattern> bindings,
                  ContainerThreadpool.UserOptions threadpoolOptions) {
        super(cluster.getSearchChains(), HANDLER_SPEC, new Threadpool(threadpoolOptions));
        bindings.forEach(this::addServerBindings);
    }

    static BindingPattern bindingPattern(Optional<String> port) {
        String path = "/search/*";
        return port
                .filter(s -> !s.isBlank())
                .map(s -> SystemBindingPattern.fromHttpPortAndPath(s, path))
                .orElseGet(() -> SystemBindingPattern.fromHttpPath(path));
    }

    private static class Threadpool extends ContainerThreadpool {

        Threadpool(UserOptions options) {
            super("search-handler", options);
        }

        @Override
        public void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder) {
            builder.maxThreadExecutionTimeSeconds(190)
                    .keepAliveTime(5.0)
                    .maxThreads(-2)
                    .minThreads(-2)
                    .queueSize(-40);
        }

    }

}
