// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.prelude.fastsearch.IndexedBackend;
import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.model.container.ContainerModelEvaluation.EVALUATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.LINGUISTICS_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.ONNXRUNTIME_BUNDLE_NAME;

/**
 * NOTE: Stable ordering of bundles in config is handled by {@link ContainerCluster#addPlatformBundle(Path)}
 *
 * @author gjoranv
 * @author Ulf Lilleengen
 */
public class PlatformBundles {

    public enum JarSuffix {
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
            LINGUISTICS_BUNDLE_NAME,
            "lucene-linguistics",
            EVALUATION_BUNDLE_NAME,
            INTEGRATION_BUNDLE_NAME,
            ONNXRUNTIME_BUNDLE_NAME
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
            com.yahoo.docproc.AbstractConcreteDocumentFactory.class.getName(),
            com.yahoo.docproc.DocumentProcessor.class.getName(),
            com.yahoo.docproc.SimpleDocumentProcessor.class.getName(),
            com.yahoo.language.simple.SimpleLinguistics.class.getName(),
            com.yahoo.prelude.cluster.ClusterSearcher.class.getName(),
            IndexedBackend.class.getName(),
            com.yahoo.prelude.fastsearch.VespaBackend.class.getName(),
            com.yahoo.prelude.querytransform.CJKSearcher.class.getName(),
            com.yahoo.prelude.querytransform.CollapsePhraseSearcher.class.getName(),
            com.yahoo.prelude.querytransform.LiteralBoostSearcher.class.getName(),
            com.yahoo.prelude.querytransform.NoRankingSearcher.class.getName(),
            com.yahoo.prelude.querytransform.NonPhrasingSearcher.class.getName(),
            com.yahoo.prelude.querytransform.NormalizingSearcher.class.getName(),
            com.yahoo.prelude.querytransform.PhrasingSearcher.class.getName(),
            com.yahoo.prelude.querytransform.RecallSearcher.class.getName(),
            com.yahoo.prelude.querytransform.StemmingSearcher.class.getName(),
            com.yahoo.prelude.searcher.BlendingSearcher.class.getName(),
            com.yahoo.prelude.searcher.FieldCollapsingSearcher.class.getName(),
            com.yahoo.prelude.searcher.FillSearcher.class.getName(),
            com.yahoo.prelude.searcher.JSONDebugSearcher.class.getName(),
            com.yahoo.prelude.searcher.JuniperSearcher.class.getName(),
            com.yahoo.prelude.searcher.MultipleResultsSearcher.class.getName(),
            com.yahoo.prelude.searcher.PosSearcher.class.getName(),
            com.yahoo.prelude.searcher.QuotingSearcher.class.getName(),
            com.yahoo.prelude.searcher.ValidateSortingSearcher.class.getName(),
            com.yahoo.prelude.semantics.SemanticSearcher.class.getName(),
            com.yahoo.prelude.statistics.StatisticsSearcher.class.getName(),
            com.yahoo.search.Searcher.class.getName(),
            com.yahoo.search.cluster.ClusterSearcher.class.getName(),
            com.yahoo.search.cluster.PingableSearcher.class.getName(),
            com.yahoo.search.federation.FederationSearcher.class.getName(),
            com.yahoo.search.federation.ForwardingSearcher.class.getName(),
            com.yahoo.search.grouping.GroupingQueryParser.class.getName(),
            com.yahoo.search.grouping.GroupingValidator.class.getName(),
            com.yahoo.search.grouping.vespa.GroupingExecutor.class.getName(),
            com.yahoo.search.pagetemplates.PageTemplate.class.getName(),
            com.yahoo.search.pagetemplates.PageTemplateSearcher.class.getName(),
            com.yahoo.search.pagetemplates.engine.Resolver.class.getName(),
            com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver.class.getName(),
            com.yahoo.search.pagetemplates.engine.resolvers.RandomResolver.class.getName(),
            com.yahoo.search.pagetemplates.model.Renderer.class.getName(),
            com.yahoo.search.query.rewrite.QueryRewriteSearcher.class.getName(),
            com.yahoo.search.query.rewrite.SearchChainDispatcherSearcher.class.getName(),
            com.yahoo.search.query.rewrite.rewriters.GenericExpansionRewriter.class.getName(),
            com.yahoo.search.query.rewrite.rewriters.MisspellRewriter.class.getName(),
            com.yahoo.search.query.rewrite.rewriters.NameRewriter.class.getName(),
            com.yahoo.search.querytransform.AllLowercasingSearcher.class.getName(),
            com.yahoo.search.querytransform.DefaultPositionSearcher.class.getName(),
            com.yahoo.search.querytransform.LowercasingSearcher.class.getName(),
            com.yahoo.search.querytransform.NGramSearcher.class.getName(),
            com.yahoo.search.querytransform.VespaLowercasingSearcher.class.getName(),
            com.yahoo.search.rendering.Renderer.class.getName(),
            com.yahoo.search.rendering.SectionedRenderer.class.getName(),
            com.yahoo.search.searchchain.ForkingSearcher.class.getName(),
            com.yahoo.search.searchers.CacheControlSearcher.class.getName(),
            com.yahoo.search.searchers.RateLimitingSearcher.class.getName(),
            com.yahoo.vespa.streamingvisitors.MetricsSearcher.class.getName(),
            com.yahoo.vespa.streamingvisitors.StreamingBackend.class.getName()
    );

}
