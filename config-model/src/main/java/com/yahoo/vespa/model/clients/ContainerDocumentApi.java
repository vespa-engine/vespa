// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import com.yahoo.vespaclient.config.FeederConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Einar M R Rosenvinge
 */
public class ContainerDocumentApi implements FeederConfig.Producer {

    public static final String vespaClientBundleSpecification = "vespaclient-container-plugin";
    private final Options options;

    public ContainerDocumentApi(ContainerCluster cluster, Options options) {
        this.options = options;
        setupLegacySearchers(cluster);
        setupHandlers(cluster);
    }

    private void setupHandlers(ContainerCluster cluster) {
        cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandler", "feed"));
        cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerGet", "get"));
        cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerVisit", "visit"));
        cluster.addComponent(newVespaClientHandler("com.yahoo.document.restapi.resource.RestApi", "document/v1/*"));
        cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerCompatibility", "document"));
        cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerStatus", "feedstatus"));
        cluster.addComponent(newVespaClientHandler("com.yahoo.vespa.http.server.FeedHandler", ContainerCluster.RESERVED_URI_PREFIX + "/feedapi"));
    }

    private void setupLegacySearchers(ContainerCluster cluster) {
        Set<ComponentSpecification> inherited = new TreeSet<>();

        SearchChain vespaGetChain = new SearchChain(new ChainSpecification(new ComponentId("vespaget"),
                new ChainSpecification.Inheritance(inherited, null), new ArrayList<>(), new TreeSet<>()));
        vespaGetChain.addInnerComponent(newVespaClientSearcher("com.yahoo.storage.searcher.GetSearcher"));

        SearchChain vespaVisitChain = new SearchChain(new ChainSpecification(new ComponentId("vespavisit"),
                new ChainSpecification.Inheritance(inherited, null), new ArrayList<>(), new TreeSet<>()));
        vespaVisitChain.addInnerComponent(newVespaClientSearcher("com.yahoo.storage.searcher.VisitSearcher"));

        SearchChains chains;
        if (cluster.getSearch() != null) {
            chains = cluster.getSearchChains();
        } else {
            chains = new SearchChains(cluster, "searchchain");
        }
        chains.add(vespaGetChain);
        chains.add(vespaVisitChain);

        if (cluster.getSearch() == null) {
            ContainerSearch containerSearch = new ContainerSearch(cluster, chains, new ContainerSearch.Options());
            cluster.setSearch(containerSearch);

            ProcessingHandler<SearchChains> searchHandler = new ProcessingHandler<>(chains, 
                                                                                    "com.yahoo.search.handler.SearchHandler");
            searchHandler.addServerBindings("http://*/search/*", "https://*/search/*");
            cluster.addComponent(searchHandler);
        }
    }

    private Handler newVespaClientHandler(String componentId, String bindingSuffix) {
        Handler<AbstractConfigProducer<?>> handler = new Handler<>(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(componentId, null, vespaClientBundleSpecification), ""));

        for (String rootBinding : options.bindings) {
            handler.addServerBindings(rootBinding + bindingSuffix,
                    rootBinding + bindingSuffix + '/');
        }
        return handler;
    }

    private Searcher newVespaClientSearcher(String componentSpec) {
        return new Searcher<>(new ChainedComponentModel(
                BundleInstantiationSpecification.getFromStrings(componentSpec, null, vespaClientBundleSpecification),
                new Dependencies(null, null, null)));
    }

    @Override
    public void getConfig(FeederConfig.Builder builder) {
        if (options.abortondocumenterror != null)
            builder.abortondocumenterror(options.abortondocumenterror);
        if (options.route!= null)
            builder.route(options.route);
        if (options.maxpendingdocs != null)
            builder.maxpendingdocs(options.maxpendingdocs);
        if (options.maxpendingbytes != null)
            builder.maxpendingbytes(options.maxpendingbytes);
        if (options.retryenabled != null)
            builder.retryenabled(options.retryenabled);
        if (options.retrydelay != null)
            builder.retrydelay(options.retrydelay);
        if (options.timeout != null)
            builder.timeout(options.timeout);
        if (options.tracelevel != null)
            builder.tracelevel(options.tracelevel);
        if (options.mbusport != null)
            builder.mbusport(options.mbusport);
        if (options.docprocChain != null)
            builder.docprocchain(options.docprocChain);
    }

    public static final class Options {
        private final Collection<String> bindings;
        private final Boolean abortondocumenterror;
        private final String route;
        private final Integer maxpendingdocs;
        private final Integer maxpendingbytes;
        private final Boolean retryenabled;
        private final Double retrydelay;
        private final Double timeout;
        private final Integer tracelevel;
        private final Integer mbusport;
        private final String docprocChain;

        public Options(Collection<String> bindings,
                       Boolean abortondocumenterror,
                       String route,
                       Integer maxpendingdocs,
                       Integer maxpendingbytes,
                       Boolean retryenabled,
                       Double retrydelay,
                       Double timeout,
                       Integer tracelevel,
                       Integer mbusport,
                       String docprocChain) {

            this.bindings = Collections.unmodifiableCollection(bindings);
            this.abortondocumenterror = abortondocumenterror;
            this.route = route;
            this.maxpendingdocs = maxpendingdocs;
            this.maxpendingbytes = maxpendingbytes;
            this.retryenabled = retryenabled;
            this.retrydelay = retrydelay;
            this.timeout = timeout;
            this.tracelevel = tracelevel;
            this.mbusport = mbusport;
            this.docprocChain = docprocChain;
        }
    }

}
