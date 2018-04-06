// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ContainerSubsystem;
import com.yahoo.vespa.model.container.search.searchchain.HttpProvider;
import com.yahoo.vespa.model.container.search.searchchain.LocalProvider;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.QrStartConfig;
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
import java.util.Optional;

/**
 * @author gjoranv
 * @author tonytv
 */
public class ContainerSearch extends ContainerSubsystem<SearchChains>
    implements
    	IndexInfoConfig.Producer,
    	IlscriptsConfig.Producer,
    	QrStartConfig.Producer,
    	QueryProfilesConfig.Producer,
        SemanticRulesConfig.Producer,
    	PageTemplatesConfig.Producer
{

    private final List<AbstractSearchCluster> systems = new LinkedList<>();
    private Options options = null;

    // For legacy qrs clusters only.
    private BinaryScaledAmount totalCacheSize = new BinaryScaledAmount();

    private QueryProfiles queryProfiles;
    private SemanticRules semanticRules;
    private PageTemplates pageTemplates;
    private final ContainerCluster owningCluster;
    private final Optional<Integer> memoryPercentage;

    public ContainerSearch(ContainerCluster cluster, SearchChains chains, Options options) {
        super(chains);
        this.options = options;
        this.owningCluster = cluster;
        this.memoryPercentage = cluster.getMemoryPercentage();
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
        getChains().initialize(searchClusters, totalCacheSize);

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

    public void setTotalCacheSize(BinaryScaledAmount totalCacheSize) {
        this.totalCacheSize = totalCacheSize;
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
    public void getConfig(QrStartConfig.Builder qsB) {
    	QrStartConfig.Jvm.Builder internalBuilder = new QrStartConfig.Jvm.Builder();
        if (memoryPercentage.isPresent()) {
            internalBuilder.heapSizeAsPercentageOfPhysicalMemory(memoryPercentage.get());
        }
    	else if (owningCluster.isHostedVespa()) {
            if (owningCluster.getHostClusterId().isPresent())
                internalBuilder.heapSizeAsPercentageOfPhysicalMemory(17);
            else
                internalBuilder.heapSizeAsPercentageOfPhysicalMemory(60);
        }
        qsB.jvm(internalBuilder.directMemorySizeCache(totalCacheSizeMb()));
    }

    private int totalCacheSizeMb() {
        if (!totalCacheSize.equals(new BinaryScaledAmount())) {
            return (int) totalCacheSize.as(BinaryPrefix.mega);
        } else {
            return totalHttpProviderCacheSize();
        }
    }

    private int totalHttpProviderCacheSize() {
        int totalCacheSizeMb = 0;
        for (HttpProvider provider: getChains().httpProviders())
            totalCacheSizeMb += provider.cacheSizeMB();

        return totalCacheSizeMb;
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

    public void getConfig(QrSearchersConfig.Builder builder, String hostName) {
        for (int i = 0; i < systems.size(); i++) {
    	    AbstractSearchCluster sys = findClusterWithId(systems, i);
    		QrSearchersConfig.Searchcluster.Builder scB = new QrSearchersConfig.Searchcluster.Builder().name(sys.getClusterName());
    		for (AbstractSearchCluster.SearchDefinitionSpec spec : sys.getLocalSDS()) {
    			scB.searchdef(spec.getSearchDefinition().getName());
    		}
    		scB.rankprofiles(new QrSearchersConfig.Searchcluster.Rankprofiles.Builder().configid(sys.getConfigId()));
    		scB.indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.valueOf(sys.getIndexingModeName()));
    		if (sys instanceof IndexedSearchCluster) {
    			scB.rowbits(sys.getRowBits());
                for (Dispatch tld: ((IndexedSearchCluster)sys).getTLDs()) {
                    if (hostName.equals(tld.getHostname())) {
                        scB.dispatcher(new QrSearchersConfig.Searchcluster.Dispatcher.Builder().
                                host(tld.getHostname()).
                                port(tld.getDispatchPort()));
                    }
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
        public Map<String, QrsCache> cacheSettings = new LinkedHashMap<>();
    }
}
