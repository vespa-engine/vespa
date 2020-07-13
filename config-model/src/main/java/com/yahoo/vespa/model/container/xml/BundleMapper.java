// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author gjoranv
 * @author Ulf Lilleengen
 */
public class BundleMapper {

    private enum JarSuffix {
        JAR_WITH_DEPS("-jar-with-dependencies.jar"),
        DEPLOY("-deploy.jar");

        public final String suffix;

        JarSuffix(String suffix) {
            this.suffix = suffix;
        }
    }

    public static final Path LIBRARY_PATH = Paths.get(Defaults.getDefaults().underVespaHome("lib/jars"));

    public static final String searchAndDocprocBundle = "container-search-and-docproc";

    private static final Map<String, String> bundleFromClass;

    public static Optional<String> getBundle(String className) {
        return Optional.ofNullable(bundleFromClass.get(className));
    }

    public static Path absoluteBundlePath(String fileName) {
        if (fileName == null) return null;
        return LIBRARY_PATH.resolve(Paths.get(fileName + JarSuffix.JAR_WITH_DEPS.suffix));
    }

     //This is a hack to allow users to declare components from the search-and-docproc bundle without naming the bundle.
    static {
        bundleFromClass = new HashMap<>();

        bundleFromClass.put("com.yahoo.docproc.AbstractConcreteDocumentFactory", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.docproc.DocumentProcessor", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.docproc.SimpleDocumentProcessor", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.docproc.util.JoinerDocumentProcessor", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.docproc.util.SplitterDocumentProcessor", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.example.TimingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.language.simple.SimpleLinguistics", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.cluster.ClusterSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.fastsearch.FastSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.fastsearch.VespaBackEndSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.CJKSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.CollapsePhraseSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.LiteralBoostSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.NoRankingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.NonPhrasingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.NormalizingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.PhrasingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.RecallSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.querytransform.StemmingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.BlendingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.FieldCollapsingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.FillSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.JSONDebugSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.JuniperSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.MultipleResultsSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.PosSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.QuotingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.searcher.ValidateSortingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.semantics.SemanticSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.statistics.StatisticsSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.prelude.templates.SearchRendererAdaptor", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.Searcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.cluster.ClusterSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.cluster.PingableSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.FederationSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.ForwardingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.http.ConfiguredHTTPClientSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.http.ConfiguredHTTPProviderSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.http.HTTPClientSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.http.HTTPProviderSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.http.HTTPSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.news.NewsSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.federation.vespa.VespaSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.grouping.GroupingQueryParser", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.grouping.GroupingValidator", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.grouping.vespa.GroupingExecutor", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.handler.SearchWithRendererHandler", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.pagetemplates.PageTemplate", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.pagetemplates.PageTemplateSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.pagetemplates.engine.Resolver", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.pagetemplates.engine.resolvers.RandomResolver", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.pagetemplates.model.Renderer", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.query.rewrite.QueryRewriteSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.query.rewrite.SearchChainDispatcherSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.query.rewrite.rewriters.GenericExpansionRewriter", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.query.rewrite.rewriters.MisspellRewriter", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.query.rewrite.rewriters.NameRewriter", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.querytransform.AllLowercasingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.querytransform.DefaultPositionSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.querytransform.LowercasingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.querytransform.NGramSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.querytransform.VespaLowercasingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.rendering.Renderer", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.rendering.SectionedRenderer", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.searchchain.ForkingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.searchchain.example.ExampleSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.searchers.CacheControlSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.statistics.PeakQpsSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.search.statistics.TimingSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.vespa.streamingvisitors.MetricsSearcher", searchAndDocprocBundle);
        bundleFromClass.put("com.yahoo.vespa.streamingvisitors.VdsStreamingSearcher", searchAndDocprocBundle);
    }

}
