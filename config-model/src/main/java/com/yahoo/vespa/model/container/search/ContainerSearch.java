// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.search.ranking.RankProfilesEvaluatorFactory;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ContainerSubsystem;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.StreamingSearchCluster;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.handler.observability.SearchStatusExtension;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.vespa.model.container.PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE;

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
        PageTemplatesConfig.Producer,
        SchemaInfoConfig.Producer {

    public static final String QUERY_PROFILE_REGISTRY_CLASS = CompiledQueryProfileRegistry.class.getName();

    private final ApplicationContainerCluster owningCluster;
    private final List<SearchCluster> searchClusters = new LinkedList<>();
    private final Collection<String> schemasWithGlobalPhase;
    private final boolean globalPhase;

    private QueryProfiles queryProfiles;
    private SemanticRules semanticRules;
    private PageTemplates pageTemplates;

    public ContainerSearch(DeployState deployState, ApplicationContainerCluster cluster, SearchChains chains) {
        super(chains);
        this.globalPhase = deployState.featureFlags().enableGlobalPhase();
        this.schemasWithGlobalPhase = getSchemasWithGlobalPhase(deployState);
        this.owningCluster = cluster;

        owningCluster.addComponent(Component.fromClassAndBundle(CompiledQueryProfileRegistry.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(com.yahoo.search.schema.SchemaInfo.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(SearchStatusExtension.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(RankProfilesEvaluatorFactory.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(com.yahoo.search.ranking.GlobalPhaseRanker.class, SEARCH_AND_DOCPROC_BUNDLE));
        cluster.addSearchAndDocprocBundles();
    }

    private static Collection<String> getSchemasWithGlobalPhase(DeployState state) {
        return state.rankProfileRegistry().all().stream()
                .filter(rp -> rp.getGlobalPhase() != null).map(rp -> rp.schema().getName()).collect(Collectors.toSet());
    }

    public void connectSearchClusters(Map<String, SearchCluster> searchClusters) {
        this.searchClusters.addAll(searchClusters.values());
        initializeDispatchers(searchClusters.values());
        initializeSearchChains(searchClusters);
    }

    /** Adds a Dispatcher component to the owning container cluster for each search cluster */
    private void initializeDispatchers(Collection<SearchCluster> searchClusters) {
        for (SearchCluster searchCluster : searchClusters) {
            if (searchCluster instanceof IndexedSearchCluster indexed) {
                var dispatcher = new DispatcherComponent(indexed);
                owningCluster.addComponent(dispatcher);
            }
            if (globalPhase) {
                for (var documentDb : searchCluster.getDocumentDbs()) {
                    if (!schemasWithGlobalPhase.contains(documentDb.getSchemaName())) continue;
                    var factory = new RankProfilesEvaluatorComponent(documentDb);
                    if (! owningCluster.getComponentsMap().containsKey(factory.getComponentId())) {
                        owningCluster.addComponent(factory);
                    }
                }
            }
        }
    }

    // public for testing
    public void initializeSearchChains(Map<String, ? extends SearchCluster> searchClusters) {
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
        for (SearchCluster sc : searchClusters) {
            sc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        for (SearchCluster sc : searchClusters) {
            sc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        for (SearchCluster sc : searchClusters) {
            sc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(QrSearchersConfig.Builder builder) {
        for (int i = 0; i < searchClusters.size(); i++) {
            SearchCluster sys = findClusterWithId(searchClusters, i);
            QrSearchersConfig.Searchcluster.Builder scB = new QrSearchersConfig.Searchcluster.Builder().
                    name(sys.getClusterName());
            for (SchemaInfo spec : sys.schemas().values()) {
                scB.searchdef(spec.fullSchema().getName());
            }
            scB.rankprofiles(new QrSearchersConfig.Searchcluster.Rankprofiles.Builder().configid(sys.getConfigId()));
            scB.indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.valueOf(sys.getIndexingModeName()));
            scB.globalphase(globalPhase);
            if ( ! (sys instanceof IndexedSearchCluster)) {
                scB.storagecluster(new QrSearchersConfig.Searchcluster.Storagecluster.Builder().
                                           routespec(((StreamingSearchCluster)sys).getStorageRouteSpec()));
            }
            builder.searchcluster(scB);
        }
    }

    private static SearchCluster findClusterWithId(List<SearchCluster> clusters, int index) {
        for (SearchCluster sys : clusters) {
            if (sys.getClusterIndex() == index)
                return sys;
        }
        throw new IllegalArgumentException("No search cluster with index " + index + " exists");
    }

}
