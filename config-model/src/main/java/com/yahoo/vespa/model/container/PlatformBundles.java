// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NOTE: Stable ordering of bundles in config is handled by {@link ContainerCluster#addPlatformBundle(Path)}
 *
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
    public static final String SEARCH_AND_DOCPROC_BUNDLE = BundleInstantiationSpecification.CONTAINER_SEARCH_AND_DOCPROC;

    // Bundles that must be loaded for all container types.
    public static final Set<Path> COMMON_VESPA_BUNDLES = toBundlePaths(
            "container-spifly.jar",  // Aries SPIFly repackaged
            // Used by vespa-athenz, zkfacade, other vespa bundles and nearly all hosted apps.
            // TODO Vespa 9: stop installing and providing servlet-api. Seems difficult, though.
            "javax.servlet-api-3.1.0.jar"
    );

    public static final Set<Path> VESPA_SECURITY_BUNDLES = toBundlePaths(
            "jdisc-security-filters",
            "vespa-athenz"
    );

    public static final Set<Path> VESPA_ZK_BUNDLES = toBundlePaths(
            "zkfacade",
            "zookeeper-server"
    );

    public static final Set<Path> SEARCH_AND_DOCPROC_BUNDLES = toBundlePaths(
            SEARCH_AND_DOCPROC_BUNDLE,
            "docprocs",
            "linguistics-components"
    );

    private static Set<Path> toBundlePaths(String... bundleNames) {
        return Stream.of(bundleNames)
                .map(PlatformBundles::absoluteBundlePath)
                .collect(Collectors.toSet());
    }

    public static Path absoluteBundlePath(String fileName) {
        return absoluteBundlePath(fileName, JarSuffix.JAR_WITH_DEPS);
    }

    public static Path absoluteBundlePath(String fileName, JarSuffix jarSuffix) {
        if (fileName == null) return null;
        String fullFilename = fileName.endsWith(".jar") ? fileName : fileName + jarSuffix.suffix;
        return LIBRARY_PATH.resolve(Paths.get(fullFilename));
    }

    public static boolean isSearchAndDocprocClass(String className) {
        return searchAndDocprocComponents.contains(className);
    }

    // This is a hack to allow users to declare components from the search-and-docproc bundle without naming the bundle.
    private static final Set<String> searchAndDocprocComponents = Set.of(
            "com.yahoo.docproc.AbstractConcreteDocumentFactory",
            "com.yahoo.docproc.DocumentProcessor",
            "com.yahoo.docproc.SimpleDocumentProcessor",
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
            "com.yahoo.vespa.streamingvisitors.MetricsSearcher",
            "com.yahoo.vespa.streamingvisitors.VdsStreamingSearcher"
    );

}
