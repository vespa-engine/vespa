// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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

    public static Set<Path> commonVespaBundles() {
        var bundles = new LinkedHashSet<Path>();
        commonVespaBundles.stream().map(PlatformBundles::absoluteBundlePath).forEach(bundles::add);
        return Collections.unmodifiableSet(bundles);
    }

    public static Path absoluteBundlePath(String fileName) {
        return absoluteBundlePath(fileName, JarSuffix.JAR_WITH_DEPS);
    }

    public static Path absoluteBundlePath(String fileName, JarSuffix jarSuffix) {
        if (fileName == null) return null;
        return LIBRARY_PATH.resolve(Paths.get(fileName + jarSuffix.suffix));
    }

    public static boolean isSearchAndDocprocClass(String className) {
        return searchAndDocprocComponents.contains(className);
    }

    // Bundles that must be loaded for all container types.
    private static final List<String> commonVespaBundles = List.of(
            "zkfacade",
            "zookeeper-server"  // TODO: not necessary in metrics-proxy.
    );

    // This is a hack to allow users to declare components from the search-and-docproc bundle without naming the bundle.
    private static final Set<String> searchAndDocprocComponents = Set.of(
            "com.yahoo.docproc.AbstractConcreteDocumentFactory",
            "com.yahoo.docproc.DocumentProcessor",
            "com.yahoo.docproc.SimpleDocumentProcessor",
            "com.yahoo.docproc.util.JoinerDocumentProcessor",
            "com.yahoo.docproc.util.SplitterDocumentProcessor",
            "com.yahoo.example.TimingSearcher",
            "com.yahoo.language.simple.SimpleLinguistics",
            "com.yahoo.prelude.cluster.ClusterSearcher",
            "com.yahoo.prelude.fastsearch.FastSearcher",
            "com.yahoo.prelude.fastsearch.VespaBackEndSearcher",
            "com.yahoo.prelude.querytransform.CJKSearcher",
            "com.yahoo.prelude.querytransform.CollapsePhraseSearcher",
            "com.yahoo.prelude.querytransform.LiteralBoostSearcher",
            "com.yahoo.prelude.querytransform.NoRankingSearcher",
            "com.yahoo.prelude.querytransform.NonPhrasingSearcher",
            "com.yahoo.prelude.querytransform.NormalizingSearcher",
            "com.yahoo.prelude.querytransform.PhrasingSearcher",
            "com.yahoo.prelude.querytransform.RecallSearcher",
            "com.yahoo.prelude.querytransform.StemmingSearcher",
            "com.yahoo.prelude.searcher.BlendingSearcher",
            "com.yahoo.prelude.searcher.FieldCollapsingSearcher",
            "com.yahoo.prelude.searcher.FillSearcher",
            "com.yahoo.prelude.searcher.JSONDebugSearcher",
            "com.yahoo.prelude.searcher.JuniperSearcher",
            "com.yahoo.prelude.searcher.MultipleResultsSearcher",
            "com.yahoo.prelude.searcher.PosSearcher",
            "com.yahoo.prelude.searcher.QuotingSearcher",
            "com.yahoo.prelude.searcher.ValidateSortingSearcher",
            "com.yahoo.prelude.semantics.SemanticSearcher",
            "com.yahoo.prelude.statistics.StatisticsSearcher",
            "com.yahoo.prelude.templates.SearchRendererAdaptor",
            "com.yahoo.search.Searcher",
            "com.yahoo.search.cluster.ClusterSearcher",
            "com.yahoo.search.cluster.PingableSearcher",
            "com.yahoo.search.federation.FederationSearcher",
            "com.yahoo.search.federation.ForwardingSearcher",
            "com.yahoo.search.federation.http.ConfiguredHTTPClientSearcher",
            "com.yahoo.search.federation.http.ConfiguredHTTPProviderSearcher",
            "com.yahoo.search.federation.http.HTTPClientSearcher",
            "com.yahoo.search.federation.http.HTTPProviderSearcher",
            "com.yahoo.search.federation.http.HTTPSearcher",
            "com.yahoo.search.federation.news.NewsSearcher",
            "com.yahoo.search.federation.vespa.VespaSearcher",
            "com.yahoo.search.grouping.GroupingQueryParser",
            "com.yahoo.search.grouping.GroupingValidator",
            "com.yahoo.search.grouping.vespa.GroupingExecutor",
            "com.yahoo.search.handler.SearchWithRendererHandler",
            "com.yahoo.search.pagetemplates.PageTemplate",
            "com.yahoo.search.pagetemplates.PageTemplateSearcher",
            "com.yahoo.search.pagetemplates.engine.Resolver",
            "com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver",
            "com.yahoo.search.pagetemplates.engine.resolvers.RandomResolver",
            "com.yahoo.search.pagetemplates.model.Renderer",
            "com.yahoo.search.query.rewrite.QueryRewriteSearcher",
            "com.yahoo.search.query.rewrite.SearchChainDispatcherSearcher",
            "com.yahoo.search.query.rewrite.rewriters.GenericExpansionRewriter",
            "com.yahoo.search.query.rewrite.rewriters.MisspellRewriter",
            "com.yahoo.search.query.rewrite.rewriters.NameRewriter",
            "com.yahoo.search.querytransform.AllLowercasingSearcher",
            "com.yahoo.search.querytransform.DefaultPositionSearcher",
            "com.yahoo.search.querytransform.LowercasingSearcher",
            "com.yahoo.search.querytransform.NGramSearcher",
            "com.yahoo.search.querytransform.VespaLowercasingSearcher",
            "com.yahoo.search.rendering.Renderer",
            "com.yahoo.search.rendering.SectionedRenderer",
            "com.yahoo.search.searchchain.ForkingSearcher",
            "com.yahoo.search.searchchain.example.ExampleSearcher",
            "com.yahoo.search.searchers.CacheControlSearcher",
            "com.yahoo.search.statistics.PeakQpsSearcher",
            "com.yahoo.search.statistics.TimingSearcher",
            "com.yahoo.vespa.streamingvisitors.MetricsSearcher",
            "com.yahoo.vespa.streamingvisitors.VdsStreamingSearcher"
    );

}
