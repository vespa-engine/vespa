// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.yahoo.component.annotation.Inject;
import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.selection.FederationTarget;
import com.yahoo.search.federation.selection.TargetSelector;
import com.yahoo.search.federation.sourceref.ModifyQueryAndResult;
import com.yahoo.search.federation.sourceref.ResolveResult;
import com.yahoo.search.federation.sourceref.SearchChainInvocationSpec;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.federation.sourceref.SingleTarget;
import com.yahoo.search.federation.sourceref.SourceRefResolver;
import com.yahoo.search.federation.sourceref.SourcesTarget;
import com.yahoo.search.federation.sourceref.VirtualSourceResolver;
import com.yahoo.search.query.Properties;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.HitOrderer;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.AsyncExecution;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.ForkingSearcher;
import com.yahoo.search.searchchain.FutureResult;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.yahoo.collections.CollectionUtil.first;

/**
 * This searcher takes a set of sources, looks them up in config and fire off the correct searchchains.
 *
 * @author Arne Bergene Fossaa
 * @author Tony Vaagenes
 * @author bratseth
 */
@Provides(FederationSearcher.FEDERATION)
@After("*")
public class FederationSearcher extends ForkingSearcher {

    private static final Logger log = Logger.getLogger(FederationSearcher.class.getName());

    /** The name of the query property containing the source name added to the query to each source by this */
    public static final CompoundName SOURCENAME = CompoundName.from("sourceName");
    public static final CompoundName PROVIDERNAME = CompoundName.from("providerName");
    public static final String FEDERATION = "Federation";
    public static final String LOG_COUNT_PREFIX = "count_";

    private final SearchChainResolver searchChainResolver;
    private final SourceRefResolver sourceRefResolver;
    private final VirtualSourceResolver virtualSourceResolver;

    private final TargetSelector<?> targetSelector;
    private final Clock clock = Clock.systemUTC();

    @Inject
    public FederationSearcher(FederationConfig config, SchemaInfo schemaInfo,
                              ComponentRegistry<TargetSelector> targetSelectors) {
        this(createResolver(config),
             createVirtualSourceResolver(config),
             resolveSelector(config.targetSelector(), targetSelectors),
             createSchema2Clusters(schemaInfo));
    }

    // for testing
    public FederationSearcher(SearchChainResolver searchChainResolver,
                              Map<String, List<String>> schema2Clusters) {
        this(searchChainResolver, VirtualSourceResolver.of(), null, schema2Clusters);
    }

    private FederationSearcher(SearchChainResolver searchChainResolver,
                               VirtualSourceResolver virtualSourceResolver,
                               TargetSelector targetSelector,
                               Map<String, List<String>> schema2Clusters) {
        this.searchChainResolver = searchChainResolver;
        sourceRefResolver = new SourceRefResolver(searchChainResolver, schema2Clusters);
        this.targetSelector = targetSelector;
        this.virtualSourceResolver = virtualSourceResolver;
    }

    private static VirtualSourceResolver createVirtualSourceResolver(FederationConfig config) {
        return VirtualSourceResolver.of(config.target().stream().map(FederationConfig.Target::id).collect(Collectors.toUnmodifiableSet()));
    }

    private static TargetSelector resolveSelector(String selectorId,
                                                  ComponentRegistry<TargetSelector> targetSelectors) {
        if (selectorId.isEmpty()) return null;
        return checkNotNull(targetSelectors.getComponent(selectorId),
                "Missing target selector with id '" + selectorId + "'");
    }

    private static Map<String, List<String>> createSchema2Clusters(SchemaInfo schemaInfo) {
        Map<String, List<String>> schema2Clusters = new HashMap<>();
        for (Cluster cluster : schemaInfo.clusters().values()) {
            for (String schema : cluster.schemas()) {
                schema2Clusters.computeIfAbsent(schema, key -> new ArrayList<>()).add(cluster.name());
            }
        }
        return schema2Clusters;
    }

    private static SearchChainResolver createResolver(FederationConfig config) {
        SearchChainResolver.Builder builder = new SearchChainResolver.Builder();

        for (FederationConfig.Target target : config.target()) {
            boolean isDefaultProviderForSource = true;

            for (FederationConfig.Target.SearchChain searchChain : target.searchChain()) {
                if (searchChain.providerId() == null || searchChain.providerId().isEmpty()) {
                    addSearchChain(builder, target, searchChain);
                } else {
                    addSourceForProvider(builder, target, searchChain, isDefaultProviderForSource);
                    isDefaultProviderForSource = false;
                }
            }

            // Allow source groups to use by default.
            if (target.useByDefault())
                builder.useTargetByDefault(target.id());
        }

        return builder.build();
    }

    private static class SearchChaininvocationProxy extends SearchChainInvocationSpec {
        SearchChaininvocationProxy(ComponentId searchChainId, FederationOptions federationOptions, String schema) {
            super(searchChainId, federationOptions, List.of(schema));
        }

        @Override
        public void modifyTargetQuery(Query query) {
            query.getModel().setSources(searchChainId.getName());
            query.getModel().setRestrict(schemas.get(0));
        }
    }

    private static void addSearchChain(SearchChainResolver.Builder builder,
                                       FederationConfig.Target target,
                                       FederationConfig.Target.SearchChain searchChain)
    {
        String id = target.id();
        if (!id.equals(searchChain.searchChainId()))
            throw new RuntimeException("Invalid federation config, " + id + " != " + searchChain.searchChainId());

        ComponentId searchChainId = ComponentId.fromString(id);
        builder.addSearchChain(searchChainId, federationOptions(searchChain), searchChain.documentTypes());
        // Here we make synthetic SearchChain proxies for all cluster.schema combinations possible
        // Given a source on the form searchcluster.schema will rewrite it to source=searchcluster and restrict to schema.
        // TODO Consider solving this in the config model by making many synthetic search clusters
        for (String schema : searchChain.documentTypes()) {
            String virtualChainId = id + "." + schema;
            builder.addSearchChain(ComponentId.fromString(virtualChainId),
                    new SearchChaininvocationProxy(searchChainId, federationOptions(searchChain).setUseByDefault(false), schema));
        }
    }

    private static void addSourceForProvider(SearchChainResolver.Builder builder, FederationConfig.Target target,
                                             FederationConfig.Target.SearchChain searchChain, boolean isDefaultProvider)
    {
        builder.addSourceForProvider(
                ComponentId.fromString(target.id()),
                ComponentId.fromString(searchChain.providerId()),
                ComponentId.fromString(searchChain.searchChainId()),
                isDefaultProvider, federationOptions(searchChain),
                searchChain.documentTypes());
    }

    private static FederationOptions federationOptions(FederationConfig.Target.SearchChain searchChain) {
        return new FederationOptions().
                setOptional(searchChain.optional()).
                setUseByDefault(searchChain.useByDefault()).
                setTimeoutInMilliseconds(searchChain.timeoutMillis()).
                setRequestTimeoutInMilliseconds(searchChain.requestTimeoutMillis());
    }

    private static List<String> extractErrors(List<ResolveResult> results) {
        List<String> errors = List.of();
        for (ResolveResult result : results) {
            if (result.errorMsg() != null) {
                if (errors.isEmpty()) {
                    errors = new ArrayList<>();
                }
                errors.add(result.errorMsg());
            }
        }
        return errors;
    }

    private static List<SearchChainInvocationSpec> extractSpecs(List<ResolveResult> results) {
        List<SearchChainInvocationSpec> errors = List.of();
        for (ResolveResult result : results) {
            if (result.invocationSpec() != null) {
                if (errors.isEmpty()) {
                    errors = List.of(result.invocationSpec());
                } else if (errors.size() == 1) {
                    errors = new ArrayList<>(errors);
                    errors.add(result.invocationSpec());
                } else {
                    errors.add(result.invocationSpec());
                }
            }
        }
        return errors;
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result mergedResults = execution.search(query);

        var targets = getTargets(query.getModel().getSources(), query.properties());
        warnIfUnresolvedSearchChains(extractErrors(targets), mergedResults.hits());

        var prunedTargets = pruneTargetsWithoutDocumentTypes(query.getModel().getRestrict(), extractSpecs(targets));

        var regularTargetHandlers = resolveSearchChains(prunedTargets, execution.searchChainRegistry());
        query.errors().addAll(regularTargetHandlers.errors());

        Set<Target> targetHandlers = new LinkedHashSet<>(regularTargetHandlers.data());
        targetHandlers.addAll(getAdditionalTargets(query, execution, targetSelector));

        traceTargets(query, targetHandlers);

        if (targetHandlers.isEmpty())
            return mergedResults;
        else if (targetHandlers.size() > 1)
            search(query, execution, targetHandlers, mergedResults);
        else if (shouldExecuteTargetLongerThanThread(query, targetHandlers.iterator().next()))
            search(query, execution, targetHandlers, mergedResults); // one target, but search in separate thread
        else
            search(query, execution, first(targetHandlers), mergedResults); // search in this thread
        return mergedResults;
    }

    private void search(Query query, Execution execution, Target target, Result mergedResults) {
        mergeResult(query, target, mergedResults, search(query, execution, target).orElse(createSearchChainTimedOutResult(query, target)));
    }

    private void search(Query query, Execution execution, Collection<Target> targets, Result mergedResults) {
        FederationResult results = search(query, execution, targets);
        results.waitForAll((int)query.getTimeLeft(), clock);

        HitOrderer s = null;
        for (FederationResult.TargetResult targetResult : results.all()) {
            if (s == null)
                s = dirtyCopyIfModifiedOrderer(mergedResults.hits(), targetResult.getOrTimeoutError().hits().getOrderer());
            mergeResult(query, targetResult.target, mergedResults, targetResult.getOrTimeoutError());
        }
    }

    private Optional<Result> search(Query query, Execution execution, Target target) {
        long timeout = target.federationOptions().getSearchChainExecutionTimeoutInMilliseconds(query.getTimeLeft());
        if (timeout <= 0) return Optional.empty();

        Execution newExecution = new Execution(target.getChain(), execution.context());
        Result result = newExecution.search(cloneFederationQuery(query, Window.from(query), timeout, target));
        target.modifyTargetResult(result);
        return Optional.of(result);
    }

    private FederationResult search(Query query, Execution execution, Collection<Target> targets) {
        FederationResult.Builder result = new FederationResult.Builder();
        for (Target target : targets)
            result.add(target, searchAsynchronously(query, execution, Window.from(targets, query), target));
        return result.build();
    }

    private FutureResult searchAsynchronously(Query query, Execution execution, Window window, Target target) {
        long timeout = target.federationOptions().getSearchChainExecutionTimeoutInMilliseconds(query.getTimeLeft());
        if (timeout <= 0)
            return new FutureResult(() -> new Result(query, ErrorMessage.createTimeout("Timed out before federation")), execution, query);
        Query clonedQuery = cloneFederationQuery(query, window, timeout, target);
        return new AsyncExecution(target.getChain(), execution).search(clonedQuery);
    }

    private Query cloneFederationQuery(Query query, Window window, long timeout, Target target) {
        query.getModel().getQueryTree(); // performance: parse query before cloning such that it is only done once
        Query clonedQuery = Query.createNewQuery(query);
        return createFederationQuery(query, clonedQuery, window, timeout, target);
    }

    private Query createFederationQuery(Query query, Query outgoing, Window window, long timeout, Target target) {
        ComponentId chainId = target.getChain().getId();

        String sourceName = chainId.getName();
        outgoing.properties().set(SOURCENAME, sourceName);
        String providerName = chainId.getName();
        if (chainId.getNamespace() != null)
            providerName = chainId.getNamespace().getName();
        outgoing.properties().set(PROVIDERNAME, providerName);

        outgoing.setTimeout(timeout);

        propagatePerSourceQueryProperties(query, outgoing, window, sourceName, providerName);

        //TODO: FederationTarget
        //TODO: only for target produced by this, not others
        target.modifyTargetQuery(outgoing);
        return outgoing;
    }

    private void propagatePerSourceQueryProperties(Query original, Query outgoing, Window window,
                                                   String sourceName, String providerName) {
        outgoing.setHits(window.hits);
        outgoing.setOffset(window.offset);
        original.properties().listProperties(CompoundName.fromComponents("provider", providerName))
                .forEach((k, v) -> outgoing.properties().set(k, v));
        original.properties().listProperties(CompoundName.fromComponents("source", sourceName))
                .forEach((k, v) -> outgoing.properties().set(k, v));
    }

    private ErrorMessage missingSearchChainsErrorMessage(List<String> errors) {
        String message = String.join(" ", new TreeSet<>(errors)) +
                                     " Valid source refs are " + String.join(", ", allSourceRefDescriptions()) +'.';
        return ErrorMessage.createInvalidQueryParameter(message);
    }

    private List<String> allSourceRefDescriptions() {
        return searchChainResolver.allTopLevelTargets().stream().map(com.yahoo.search.federation.sourceref.Target::searchRefDescription).toList();
    }

    private void warnIfUnresolvedSearchChains(List<String> errorMessages, HitGroup errorHitGroup) {
        if (!errorMessages.isEmpty()) {
            errorHitGroup.addError(missingSearchChainsErrorMessage(errorMessages));
        }
    }

    @Override
    public Collection<CommentedSearchChain> getSearchChainsForwarded(SearchChainRegistry registry) {
        List<CommentedSearchChain> searchChains = new ArrayList<>();

        for (var target : searchChainResolver.allTopLevelTargets()) {
            if (target instanceof SourcesTarget) {
                searchChains.addAll(commentedSourceProviderSearchChains((SourcesTarget)target, registry));
            } else if (target instanceof SingleTarget) {
                searchChains.add(commentedSearchChain((SingleTarget)target, registry));
            } else {
                log.warning("Invalid target type " + target.getClass().getName());
            }
        }

        return searchChains;
    }

    private CommentedSearchChain commentedSearchChain(SingleTarget singleTarget, SearchChainRegistry registry) {
        return new CommentedSearchChain("If source refs contains '" + singleTarget.getId() + "'.",
                                        registry.getChain(singleTarget.getId()));
    }

    private List<CommentedSearchChain> commentedSourceProviderSearchChains(SourcesTarget sourcesTarget,
                                                                           SearchChainRegistry registry) {
        List<CommentedSearchChain> commentedSearchChains = new ArrayList<>();
        String ifMatchingSourceRefPrefix = "If source refs contains '" + sourcesTarget.getId() + "' and provider is '";

        commentedSearchChains.add(
                new CommentedSearchChain(ifMatchingSourceRefPrefix + sourcesTarget.defaultProviderSource().provider +
                        "'(or not given).", registry.getChain(sourcesTarget.defaultProviderSource().searchChainId)));

        for (SearchChainInvocationSpec providerSource : sourcesTarget.allProviderSources()) {
            if (!providerSource.equals(sourcesTarget.defaultProviderSource())) {
                commentedSearchChains.add(
                        new CommentedSearchChain(ifMatchingSourceRefPrefix + providerSource.provider + "'.",
                                registry.getChain(providerSource.searchChainId)));
            }
        }
        return commentedSearchChains;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        UniqueExecutionsToResults uniqueExecutionsToResults = new UniqueExecutionsToResults();
        addResultsToFill(result.hits(), result, summaryClass, uniqueExecutionsToResults);
        var resultsForAllChains = uniqueExecutionsToResults.resultsToFill.entrySet();
        int numberOfCallsToFillNeeded = 0;

        for (Entry<Chain<Searcher>, Map<Query, Result>> resultsToFillForAChain : resultsForAllChains) {
            numberOfCallsToFillNeeded += resultsToFillForAChain.getValue().size();
        }

        List<Pair<Result, FutureResult>> futureFilledResults = new ArrayList<>();
        for (Entry<Chain<Searcher>, Map<Query, Result>> resultsToFillForAChain : resultsForAllChains) {
            Chain<Searcher> chain = resultsToFillForAChain.getKey();
            Execution chainExecution = (chain == null) ? execution : new Execution(chain, execution.context());

            for (Entry<Query, Result> resultsToFillForAChainAndQuery : resultsToFillForAChain.getValue().entrySet()) {
                Result resultToFill = resultsToFillForAChainAndQuery.getValue();
                if (numberOfCallsToFillNeeded == 1) {
                    chainExecution.fill(resultToFill, summaryClass);
                    propagateErrors(resultToFill, result);
                } else {
                    AsyncExecution asyncFill = new AsyncExecution(chainExecution);
                    futureFilledResults.add(new Pair<>(resultToFill, asyncFill.fill(resultToFill, summaryClass)));
                }
            }
        }
        for (Pair<Result, FutureResult> futureFilledResult : futureFilledResults) {
            // futureFilledResult is a pair of a result to be filled and the future in which that same result is filled
            Optional<Result> filledResult = futureFilledResult.getSecond().getIfAvailable(result.getQuery().getTimeLeft(), TimeUnit.MILLISECONDS);
            if (filledResult.isPresent()) { // fill completed
                propagateErrors(filledResult.get(), result);
            }
            else { // fill timed out: Remove these hits as they are incomplete and may cause a race when accessed later
                result.hits().addError(futureFilledResult.getSecond().createTimeoutError());
                for (Iterator<Hit> i = futureFilledResult.getFirst().hits().unorderedDeepIterator(); i.hasNext(); ) {
                    // Note that some of these hits may be filled, but as the fill thread may still be working on them,
                    // and we do not synchronize with it, we need to discard all.
                    result.hits().remove(i.next().getId());
                }
            }
        }
    }

    private void propagateErrors(Result source, Result destination) {
        destination.hits().addErrorsFrom(source.hits());
    }

    private void addResultsToFill(HitGroup hitGroup, Result result, String summaryClass,
                                  UniqueExecutionsToResults uniqueExecutionsToResults) {
        for (Hit hit : hitGroup) {
            if (hit instanceof HitGroup) {
                addResultsToFill((HitGroup) hit, result, summaryClass, uniqueExecutionsToResults);
            } else {
                if ( ! hit.isFilled(summaryClass))
                    getSearchChainGroup(hit, result, uniqueExecutionsToResults).hits().add(hit);
            }
        }
    }

    private Result getSearchChainGroup(Hit hit, Result result, UniqueExecutionsToResults uniqueExecutionsToResults) {
        @SuppressWarnings("unchecked")
        Chain<Searcher> chain = (Chain<Searcher>) hit.getSearcherSpecificMetaData(this);
        Query query = hit.getQuery() !=null ? hit.getQuery() : result.getQuery();

        return uniqueExecutionsToResults.get(chain, query);
    }

    /**
     * TODO This is probably a dirty hack for bug 4711376. There are probably better ways.
     * But I will leave that to trd-processing@
     *
     * @param group the merging hitgroup to be updated if necessary
     * @param orderer the per provider hit orderer
     * @return he hitorderer chosen
     */
    private HitOrderer dirtyCopyIfModifiedOrderer(HitGroup group, HitOrderer orderer) {
        if (orderer != null) {
            HitOrderer old = group.getOrderer();
            if (! orderer.equals(old)) {
                group.setOrderer(orderer);
            }
        }

        return orderer;
    }

    private List<ResolveResult> getTargets(Set<String> sources, Properties properties) {
        return sources.isEmpty() ?
                defaultSearchChains(properties):
                resolveSources(sources, properties);
    }


    private List<ResolveResult> resolveSources(Set<String> sourcesInQuery, Properties properties) {
        List<ResolveResult> result = new ArrayList<>();
        Set<String> sources = virtualSourceResolver.resolve(sourcesInQuery);

        for (String source : sources) {
            result.addAll(sourceRefResolver.resolve(asSourceSpec(source), properties));
        }

        return List.copyOf(result);
    }

    public List<ResolveResult> defaultSearchChains(Properties sourceToProviderMap) {
        List<ResolveResult> result = new ArrayList<>();

        for (var target : searchChainResolver.defaultTargets()) {
            result.add(target.responsibleSearchChain(sourceToProviderMap));
        }

        return List.copyOf(result);
    }


    private ComponentSpecification asSourceSpec(String source) {
        try {
            return new ComponentSpecification(source);
        } catch (Exception e) {
            throw new IllegalInputException("The source ref '" + source + "' used for federation is not valid.", e);
        }
    }

    private void traceTargets(Query query, Collection<Target> targets) {
        int traceFederationLevel = 2;
        if ( ! query.getTrace().isTraceable(traceFederationLevel)) return;
        query.trace("Federating to " + targets, traceFederationLevel);
    }

    /**
     * Returns true if we are requested to keep executing a target longer than we're waiting for it.
     * This is useful to populate caches inside targets.
     */
    private boolean shouldExecuteTargetLongerThanThread(Query query, Target target) {
        return target.federationOptions().getRequestTimeoutInMilliseconds() > query.getTimeout();
    }

    private static Result createSearchChainTimedOutResult(Query query, Target target) {
        ErrorMessage timeoutMessage = ErrorMessage.createTimeout("Error in execution of chain '" + target.getId() +
                                                                 "': " + "Chain timed out.");
        timeoutMessage.setSource(target.getId().stringValue());
        return new Result(query, timeoutMessage);
    }

    private void mergeResult(Query query, Target target, Result mergedResults, Result result) {
        ComponentId searchChainId = target.getId();
        Chain<Searcher> searchChain = target.getChain();

        mergedResults.mergeWith(result);
        HitGroup group = result.hits();
        group.setId("source:" + searchChainId.getName());

        group.setSearcherSpecificMetaData(this, searchChain);
        group.setMeta(false); // Set hit groups as non-meta as a default
        group.setAuxiliary(true); // Set hit group as auxiliary so that it doesn't contribute to count
        group.setSource(searchChainId.getName());
        group.setQuery(result.getQuery());

        for (Iterator<Hit> it = group.unorderedDeepIterator(); it.hasNext();) {
            Hit hit = it.next();
            hit.setSearcherSpecificMetaData(this, searchChain);
            hit.setSource(searchChainId.stringValue());

            // This is the backend request meta hit, that is holding logging information
            // See HTTPBackendSearcher, where this hit is created
            if (hit.isMeta() && hit.types().contains("logging")) {
                // Augment this hit with count fields
                hit.setField(LOG_COUNT_PREFIX + "deep", result.getDeepHitCount());
                hit.setField(LOG_COUNT_PREFIX + "total", result.getTotalHitCount());
                int offset = result.getQuery().getOffset();
                hit.setField(LOG_COUNT_PREFIX + "first", offset + 1);
                hit.setField(LOG_COUNT_PREFIX + "last", result.getConcreteHitCount() + offset);
            }

        }
        if (query.getTrace().getLevel()>=4)
            query.trace("Got " + group.getConcreteSize() + " hits from " + group.getId(),false, 4);
        mergedResults.hits().add(group);
    }

    private Results<Target, ErrorMessage> resolveSearchChains(Collection<SearchChainInvocationSpec> prunedTargets,
                                                              SearchChainRegistry registry) {
        Results.Builder<Target, ErrorMessage> targetHandlers = new Results.Builder<>();

        for (SearchChainInvocationSpec target: prunedTargets) {
            Chain<Searcher> chain = registry.getChain(target.searchChainId);
            if (chain == null) {
                targetHandlers.addError(ErrorMessage.createIllegalQuery("Could not find search chain '" 
                                                                        + target.searchChainId + "'"));
            } else {
                targetHandlers.addData(new StandardTarget(target, chain));
            }
        }

        return targetHandlers.build();
    }

    private static <T> List<Target> getAdditionalTargets(Query query, Execution execution, TargetSelector<T> targetSelector) {
        if (targetSelector == null) return List.of();

        ArrayList<Target> result = new ArrayList<>();
        for (FederationTarget<T> target: targetSelector.getTargets(query, execution.searchChainRegistry()))
            result.add(new CustomTarget<>(targetSelector, target));

        return result;
    }

    private Collection<SearchChainInvocationSpec> pruneTargetsWithoutDocumentTypes(Set<String> restrict, List<SearchChainInvocationSpec> targets) {
        if (restrict.isEmpty()) return targets;

        Collection<SearchChainInvocationSpec> prunedTargets = new ArrayList<>();

        for (SearchChainInvocationSpec target : targets) {
            if (target.schemas.isEmpty() || documentTypeIntersectionIsNonEmpty(restrict, target))
                prunedTargets.add(target);
        }

        return prunedTargets;
    }

    private boolean documentTypeIntersectionIsNonEmpty(Set<String> restrict, SearchChainInvocationSpec target) {
        for (String documentType : target.schemas) {
            if (restrict.contains(documentType))
                return true;
        }

        return false;
    }

    /** A map from a unique search chain and query instance to a result */
    private static class UniqueExecutionsToResults {

        /** Implemented as a nested identity hashmap */
        final Map<Chain<Searcher>,Map<Query,Result>> resultsToFill = new IdentityHashMap<>();

        /** Returns a result to fill for a query and chain, by creating it if necessary */
        public Result get(Chain<Searcher> chain, Query query) {
            Map<Query, Result> resultsToFillForAChain = resultsToFill.computeIfAbsent(chain, k -> new IdentityHashMap<>());

            Result resultsToFillForAChainAndQuery = resultsToFillForAChain.get(query);
            if (resultsToFillForAChainAndQuery == null) {
                resultsToFillForAChainAndQuery = new Result(query);
                resultsToFillForAChain.put(query, resultsToFillForAChainAndQuery);
            }

            return resultsToFillForAChainAndQuery;
        }

    }

    /** A target for federation, containing a chain to which a federation query can be forwarded. */
    static abstract class Target implements ModifyQueryAndResult {

        abstract Chain<Searcher> getChain();

        ComponentId getId() {
            return getChain().getId();
        }

        public abstract FederationOptions federationOptions();

        @Override
        public String toString() { return getChain().getId().stringValue(); }

    }

    /**
     * A handler representing a target created by the federation logic. 
     * This is a value object, to ensure that identical target invocations are not invoked multiple times.
     */
    private static class StandardTarget extends Target {

        private final SearchChainInvocationSpec target;
        private final Chain<Searcher> chain;

        public StandardTarget(SearchChainInvocationSpec target, Chain<Searcher> chain) {
            this.target = target;
            this.chain = chain;
        }

        @Override
        Chain<Searcher> getChain() { return chain; }

        @Override
        public void modifyTargetQuery(Query query) { target.modifyTargetQuery(query); }
        @Override
        public void modifyTargetResult(Result result) { target.modifyTargetResult(result); }

        @Override
        public FederationOptions federationOptions() { return target.federationOptions; }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof StandardTarget other)) return false;

            if ( ! Objects.equals(other.chain.getId(), this.chain.getId())) return false;
            if ( ! Objects.equals(other.target, this.target)) return false;
            return true;
        }

        @Override
        public int hashCode() { return Objects.hash(chain.getId(), target); }

    }

    /** A target handler where the target generation logic is delegated to the application provided target selector */
    private static class CustomTarget<T> extends Target {

        private final TargetSelector<T> selector;
        private final FederationTarget<T> target;

        CustomTarget(TargetSelector<T> selector, FederationTarget<T> target) {
            this.selector = selector;
            this.target = target;
        }

        @Override
        Chain<Searcher> getChain() {
            return target.getChain();
        }

        @Override
        public void modifyTargetQuery(Query query) {
            selector.modifyTargetQuery(target, query);
        }

        @Override
        public void modifyTargetResult(Result result) {
            selector.modifyTargetResult(target, result);
        }

        @Override
        public FederationOptions federationOptions() {
            return target.getFederationOptions();
        }

    }

    private record Window(int hits, int offset) {

        public static Window from(Query query) {
            return new Window(query.getHits(), query.getOffset());
        }

        public static Window from(Collection<Target> targets, Query query) {
            if (targets.size() == 1) // preserve requested top-level offsets
                return Window.from(query);
            else // request from offset 0 to enable correct upstream blending into a single top-level hit list
                return new Window(query.getHits() + query.getOffset(), 0);
        }

    }

}
