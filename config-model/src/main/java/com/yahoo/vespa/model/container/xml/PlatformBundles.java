// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.vespa.defaults.Defaults;
import org.tensorflow.op.Op;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author gjoranv
 * @author Ulf Lilleengen
 */
public class PlatformBundles {

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

    private static final Set<String> searchAndDocprocComponents;

    public static boolean isSearchAndDocprocClass(String className) {
        return searchAndDocprocComponents.contains(className);
    }

    public static Path absoluteBundlePath(String fileName) {
        if (fileName == null) return null;
        return LIBRARY_PATH.resolve(Paths.get(fileName + JarSuffix.JAR_WITH_DEPS.suffix));
    }

    // This is a hack to allow users to declare components from the search-and-docproc bundle without naming the bundle.
    static {
        searchAndDocprocComponents = new HashSet<>();

        searchAndDocprocComponents.add("com.yahoo.docproc.AbstractConcreteDocumentFactory");
        searchAndDocprocComponents.add("com.yahoo.docproc.DocumentProcessor");
        searchAndDocprocComponents.add("com.yahoo.docproc.SimpleDocumentProcessor");
        searchAndDocprocComponents.add("com.yahoo.docproc.util.JoinerDocumentProcessor");
        searchAndDocprocComponents.add("com.yahoo.docproc.util.SplitterDocumentProcessor");
        searchAndDocprocComponents.add("com.yahoo.example.TimingSearcher");
        searchAndDocprocComponents.add("com.yahoo.language.simple.SimpleLinguistics");
        searchAndDocprocComponents.add("com.yahoo.prelude.cluster.ClusterSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.fastsearch.FastSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.fastsearch.VespaBackEndSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.CJKSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.CollapsePhraseSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.LiteralBoostSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.NoRankingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.NonPhrasingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.NormalizingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.PhrasingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.RecallSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.querytransform.StemmingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.BlendingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.FieldCollapsingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.FillSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.JSONDebugSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.JuniperSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.MultipleResultsSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.PosSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.QuotingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.searcher.ValidateSortingSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.semantics.SemanticSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.statistics.StatisticsSearcher");
        searchAndDocprocComponents.add("com.yahoo.prelude.templates.SearchRendererAdaptor");
        searchAndDocprocComponents.add("com.yahoo.search.Searcher");
        searchAndDocprocComponents.add("com.yahoo.search.cluster.ClusterSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.cluster.PingableSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.FederationSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.ForwardingSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.http.ConfiguredHTTPClientSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.http.ConfiguredHTTPProviderSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.http.HTTPClientSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.http.HTTPProviderSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.http.HTTPSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.news.NewsSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.federation.vespa.VespaSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.grouping.GroupingQueryParser");
        searchAndDocprocComponents.add("com.yahoo.search.grouping.GroupingValidator");
        searchAndDocprocComponents.add("com.yahoo.search.grouping.vespa.GroupingExecutor");
        searchAndDocprocComponents.add("com.yahoo.search.handler.SearchWithRendererHandler");
        searchAndDocprocComponents.add("com.yahoo.search.pagetemplates.PageTemplate");
        searchAndDocprocComponents.add("com.yahoo.search.pagetemplates.PageTemplateSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.pagetemplates.engine.Resolver");
        searchAndDocprocComponents.add("com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver");
        searchAndDocprocComponents.add("com.yahoo.search.pagetemplates.engine.resolvers.RandomResolver");
        searchAndDocprocComponents.add("com.yahoo.search.pagetemplates.model.Renderer");
        searchAndDocprocComponents.add("com.yahoo.search.query.rewrite.QueryRewriteSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.query.rewrite.SearchChainDispatcherSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.query.rewrite.rewriters.GenericExpansionRewriter");
        searchAndDocprocComponents.add("com.yahoo.search.query.rewrite.rewriters.MisspellRewriter");
        searchAndDocprocComponents.add("com.yahoo.search.query.rewrite.rewriters.NameRewriter");
        searchAndDocprocComponents.add("com.yahoo.search.querytransform.AllLowercasingSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.querytransform.DefaultPositionSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.querytransform.LowercasingSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.querytransform.NGramSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.querytransform.VespaLowercasingSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.rendering.Renderer");
        searchAndDocprocComponents.add("com.yahoo.search.rendering.SectionedRenderer");
        searchAndDocprocComponents.add("com.yahoo.search.searchchain.ForkingSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.searchchain.example.ExampleSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.searchers.CacheControlSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.statistics.PeakQpsSearcher");
        searchAndDocprocComponents.add("com.yahoo.search.statistics.TimingSearcher");
        searchAndDocprocComponents.add("com.yahoo.vespa.streamingvisitors.MetricsSearcher");
        searchAndDocprocComponents.add("com.yahoo.vespa.streamingvisitors.VdsStreamingSearcher");
    }

}
