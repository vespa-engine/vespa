// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ContainerSubsystem;
import com.yahoo.vespa.model.container.search.searchchain.LocalProvider;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.StreamingSearchCluster;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.model.container.xml.PlatformBundles.searchAndDocprocBundle;

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

    public static final String QUERY_PROFILE_REGISTRY_CLASS = CompiledQueryProfileRegistry.class.getName();

    private ApplicationContainerCluster owningCluster;
    private final List<AbstractSearchCluster> searchClusters = new LinkedList<>();
    private final Options options;

    private QueryProfiles queryProfiles;
    private SemanticRules semanticRules;
    private PageTemplates pageTemplates;

    public ContainerSearch(ApplicationContainerCluster cluster, SearchChains chains, Options options) {
        super(chains);
        this.owningCluster = cluster;
        this.options = options;

        owningCluster.addComponent(Component.fromClassAndBundle(QUERY_PROFILE_REGISTRY_CLASS, searchAndDocprocBundle));
    }

    public void connectSearchClusters(Map<String, AbstractSearchCluster> searchClusters) {
        this.searchClusters.addAll(searchClusters.values());
        initializeDispatchers(searchClusters.values());
        initializeSearchChains(searchClusters);
    }

    /** Adds a Dispatcher component to the owning container cluster for each search cluster */
    private void initializeDispatchers(Collection<AbstractSearchCluster> searchClusters) {
        for (AbstractSearchCluster searchCluster : searchClusters) {
            if ( ! ( searchCluster instanceof IndexedSearchCluster)) continue;
            var dispatcher = new DispatcherComponent((IndexedSearchCluster)searchCluster);
            owningCluster.addComponent(dispatcher);
        }
    }

    // public for testing
    public void initializeSearchChains(Map<String, ? extends AbstractSearchCluster> searchClusters) {
        getChains().initialize(searchClusters);
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
        if (queryProfiles != null) {
            queryProfiles.getConfig(builder);
        }
    }

    @Override
    public void getConfig(SemanticRulesConfig.Builder builder) {
        if (semanticRules != null) semanticRules.getConfig(builder);
    }

    @Override
    public void getConfig(PageTemplatesConfig.Builder builder) {
        if (pageTemplates != null) pageTemplates.getConfig(builder);
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        for (AbstractSearchCluster sc : searchClusters) {
            sc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        for (AbstractSearchCluster sc : searchClusters) {
            sc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(QrSearchersConfig.Builder builder) {
        for (int i = 0; i < searchClusters.size(); i++) {
    	    AbstractSearchCluster sys = findClusterWithId(searchClusters, i);
    		QrSearchersConfig.Searchcluster.Builder scB = new QrSearchersConfig.Searchcluster.Builder().
    				name(sys.getClusterName());
    		for (AbstractSearchCluster.SchemaSpec spec : sys.getLocalSDS()) {
    			scB.searchdef(spec.getSearchDefinition().getName());
    		}
    		scB.rankprofiles(new QrSearchersConfig.Searchcluster.Rankprofiles.Builder().configid(sys.getConfigId()));
    		scB.indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.valueOf(sys.getIndexingModeName()));
    		if ( ! (sys instanceof IndexedSearchCluster)) {
            	scB.storagecluster(new QrSearchersConfig.Searchcluster.Storagecluster.Builder().
            			routespec(((StreamingSearchCluster)sys).getStorageRouteSpec()));
            }
            builder.searchcluster(scB);
    	}
    }

    private static AbstractSearchCluster findClusterWithId(List<AbstractSearchCluster> clusters, int index) {
        for (AbstractSearchCluster sys : clusters) {
            if (sys.getClusterIndex() == index)
                return sys;
        }
        throw new IllegalArgumentException("No search cluster with index " + index + " exists");
    }

    public Options getOptions() {
        return options;
    }

    /** Encapsulates qrserver options. */
    public static class Options {

        Map<String, QrsCache> cacheSettings = new LinkedHashMap<>();

    }

}
