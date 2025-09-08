// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.ReconfigurableDispatcher;
import com.yahoo.search.handler.observability.SearchStatusExtension;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.search.ranking.RankProfilesEvaluatorFactory;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ContainerSubsystem;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.SearchCluster;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.model.container.PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class ContainerSearch extends ContainerSubsystem<SearchChains> implements
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        QrSearchersConfig.Producer,
        QueryProfilesConfig.Producer,
        SemanticRulesConfig.Producer,
        PageTemplatesConfig.Producer,
        SchemaInfoConfig.Producer
{

    public static final String QUERY_PROFILE_REGISTRY_CLASS = CompiledQueryProfileRegistry.class.getName();

    private final ApplicationContainerCluster owningCluster;
    private final List<SearchCluster> searchClusters = new LinkedList<>();
    private final Collection<String> schemasWithGlobalPhase;
    private final ApplicationPackage app;
    private final boolean useLegacyWandQueryParsing;

    private QueryProfiles queryProfiles;
    private SemanticRules semanticRules;
    private PageTemplates pageTemplates;

    public ContainerSearch(DeployState deployState, ApplicationContainerCluster cluster, SearchChains chains) {
        super(chains);
        this.schemasWithGlobalPhase = getSchemasWithGlobalPhase(deployState);
        this.app = deployState.getApplicationPackage();
        this.owningCluster = cluster;
        this.useLegacyWandQueryParsing = deployState.featureFlags().useLegacyWandQueryParsing();

        owningCluster.addComponent(Component.fromClassAndBundle(CompiledQueryProfileRegistry.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(com.yahoo.search.schema.SchemaInfo.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(SearchStatusExtension.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(RankProfilesEvaluatorFactory.class, SEARCH_AND_DOCPROC_BUNDLE));
        owningCluster.addComponent(Component.fromClassAndBundle(com.yahoo.search.ranking.GlobalPhaseRanker.class, SEARCH_AND_DOCPROC_BUNDLE));
        cluster.addSearchAndDocprocBundles();
    }

    private static Collection<String> getSchemasWithGlobalPhase(DeployState state) {
        var res = new HashSet<String>();
        for (var schema : state.getSchemas()) {
            for (var rp : state.rankProfileRegistry().rankProfilesOf(schema)) {
                if (rp.getGlobalPhase() != null) {
                    res.add(schema.getName());
                }
            }
        }
        return res;
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
                // For local testing, using Application, there is no cloud config, and we need to use the static dispatcher.
                Class<? extends Dispatcher> dispatcherClass = System.getProperty("vespa.local", "false").equals("true")
                                                              ? Dispatcher.class
                                                              : ReconfigurableDispatcher.class;
                var dispatcher = new DispatcherComponent(indexed, dispatcherClass);
                owningCluster.addComponent(dispatcher);
            }
            for (var documentDb : searchCluster.getDocumentDbs()) {
                // if ( ! schemasWithGlobalPhase.contains(documentDb.getSchemaName())) continue;
                var factory = new RankProfilesEvaluatorComponent(documentDb);
                if ( ! owningCluster.getComponentsMap().containsKey(factory.getComponentId())) {
                    var onnxModels = documentDb.getDerivedConfiguration().getRankProfileList().getOnnxModels();
                    onnxModels.asMap().forEach(
                            (__, model) -> owningCluster.onnxModelCostCalculator().registerModel(app.getFile(model.getFilePath()), model.onnxModelOptions()));
                    owningCluster.addComponent(factory);
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
        searchClusters.forEach(sc -> builder.searchcluster(sc.getQrSearcherConfig()));
        if (useLegacyWandQueryParsing) {
            builder.parserSettings(cfg -> cfg
                                   .keepImplicitAnds(true)
                                   .keepSegmentAnds(true)
                                   .keepIdeographicPunctuation(true));
        }
    }

}
