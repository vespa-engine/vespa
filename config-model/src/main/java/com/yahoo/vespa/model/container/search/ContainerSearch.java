// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ContainerSubsystem;
import com.yahoo.vespa.model.container.search.searchchain.LocalProvider;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.search.Dispatch;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.StreamingSearchCluster;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class ContainerSearch extends ContainerSubsystem<SearchChains>
    implements
    	IndexInfoConfig.Producer,
    	IlscriptsConfig.Producer,
    	QrSearchersConfig.Producer,
    	QueryProfilesConfig.Producer,
        SemanticRulesConfig.Producer,
    	PageTemplatesConfig.Producer {

    private final List<AbstractSearchCluster> systems = new LinkedList<>();
    private final Options options;

    private QueryProfiles queryProfiles;
    private SemanticRules semanticRules;
    private PageTemplates pageTemplates;

    public ContainerSearch(ContainerCluster cluster, SearchChains chains, Options options) {
        super(chains);
        this.options = options;
        cluster.addComponent(getFS4ResourcePool());

    }

    private Component<?, ComponentModel> getFS4ResourcePool() {
        BundleInstantiationSpecification spec = BundleInstantiationSpecification.
                getInternalSearcherSpecificationFromStrings(FS4ResourcePool.class.getName(), null);
        return new Component<>(new ComponentModel(spec));
    }

    public void connectSearchClusters(Map<String, AbstractSearchCluster> searchClusters) {
        systems.addAll(searchClusters.values());
        initializeSearchChains(searchClusters);
    }

    // public for testing
    public void initializeSearchChains(Map<String, ? extends AbstractSearchCluster> searchClusters) {
        getChains().initialize(searchClusters);

        QrsCache defaultCacheOptions = getOptions().cacheSettings.get("");
        if (defaultCacheOptions != null) {
            for (LocalProvider localProvider: getChains().localProviders()) {
                localProvider.setCacheSize(defaultCacheOptions.size);
            }
        }

        for (LocalProvider localProvider: getChains().localProviders()) {
            QrsCache cacheOptions = getOptions().cacheSettings.get(localProvider.getClusterName());
            if (cacheOptions != null) {
                localProvider.setCacheSize(cacheOptions.size);
            }
        }
    }

    public void setQueryProfiles(QueryProfiles queryProfiles) {
        this.queryProfiles = queryProfiles;
    }

    public void setSemanticRules(SemanticRules semanticRules) {
        this.semanticRules = semanticRules;
    }

    public void setPageTemplates(PageTemplates pageTemplates) {
        this.pageTemplates = pageTemplates;
    }

    @Override
    public void getConfig(QueryProfilesConfig.Builder builder) {
        if (queryProfiles!=null) queryProfiles.getConfig(builder);
    }

    @Override
    public void getConfig(SemanticRulesConfig.Builder builder) {
        if (semanticRules!=null) semanticRules.getConfig(builder);
    }

    @Override
    public void getConfig(PageTemplatesConfig.Builder builder) {
        if (pageTemplates!=null) pageTemplates.getConfig(builder);
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        for (AbstractSearchCluster sc : systems) {
            sc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        for (AbstractSearchCluster sc : systems) {
            sc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(QrSearchersConfig.Builder builder) {
        for (int i = 0; i < systems.size(); i++) {
    	    AbstractSearchCluster sys = findClusterWithId(systems, i);
    		QrSearchersConfig.Searchcluster.Builder scB = new QrSearchersConfig.Searchcluster.Builder().
    				name(sys.getClusterName());
    		for (AbstractSearchCluster.SearchDefinitionSpec spec : sys.getLocalSDS()) {
    			scB.searchdef(spec.getSearchDefinition().getName());
    		}
    		scB.rankprofiles(new QrSearchersConfig.Searchcluster.Rankprofiles.Builder().configid(sys.getConfigId()));
    		scB.indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.valueOf(sys.getIndexingModeName()));
    		if (sys instanceof IndexedSearchCluster) {
                for (Dispatch tld: ((IndexedSearchCluster)sys).getTLDs()) {
                	scB.dispatcher(new QrSearchersConfig.Searchcluster.Dispatcher.Builder().
                			host(tld.getHostname()).
                			port(tld.getDispatchPort()));
                }
            } else {
            	scB.storagecluster(new QrSearchersConfig.Searchcluster.Storagecluster.Builder().
            			routespec(((StreamingSearchCluster)sys).getStorageRouteSpec()));
            }
            builder.searchcluster(scB);
    	}
    }

    private static AbstractSearchCluster findClusterWithId(List<AbstractSearchCluster> clusters, int index) {
        for (AbstractSearchCluster sys : clusters) {
            if (sys.getClusterIndex() == index) {
                return sys;
            }
        }
        throw new IllegalArgumentException("No search cluster with index " + index + " exists");
    }

    public Options getOptions() {
        return options;
    }

    /**
     * Struct that encapsulates qrserver options.
     */
    public static class Options {
        Map<String, QrsCache> cacheSettings = new LinkedHashMap<>();
    }
}
