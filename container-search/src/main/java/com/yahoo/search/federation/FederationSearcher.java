// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.errorhandling.Results;
import com.yahoo.errorhandling.Results.Builder;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.selection.FederationTarget;
import com.yahoo.search.federation.selection.TargetSelector;
import com.yahoo.search.federation.sourceref.SearchChainInvocationSpec;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.federation.sourceref.SingleTarget;
import com.yahoo.search.federation.sourceref.SourceRefResolver;
import com.yahoo.search.federation.sourceref.SourcesTarget;
import com.yahoo.search.federation.sourceref.UnresolvedSearchChainException;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.properties.QueryProperties;
import com.yahoo.search.query.properties.SubProperties;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.HitOrderer;
import com.yahoo.search.searchchain.AsyncExecution;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.ForkingSearcher;
import com.yahoo.search.searchchain.FutureResult;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.apache.commons.lang.StringUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.yahoo.collections.CollectionUtil.first;
import static com.yahoo.container.util.Util.quote;
import static com.yahoo.search.federation.StrictContractsConfig.PropagateSourceProperties;

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

    public static final String FEDERATION = "Federation";

    private static final Logger log = Logger.getLogger(FederationSearcher.class.getName());

    /** The name of the query property containing the source name added to the query to each source by this */
    public final static CompoundName SOURCENAME = new CompoundName("sourceName");
    public final static CompoundName PROVIDERNAME = new CompoundName("providerName");

    /** Logging field name constants */
    public static final String LOG_COUNT_PREFIX = "count_";

    private final SearchChainResolver searchChainResolver;
    private final PropagateSourceProperties.Enum propagateSourceProperties;
    private final SourceRefResolver sourceRefResolver;
    private final CopyOnWriteHashMap<CompoundKey, CompoundName> map = new CopyOnWriteHashMap<>();

    private final boolean strictSearchchain;
    private final TargetSelector<?> targetSelector;

    private final Clock clock = Clock.systemUTC();

    private static final List<CompoundName> queryAndHits = ImmutableList.of(Query.OFFSET, Query.HITS);

    @Inject
    public FederationSearcher(FederationConfig config, StrictContractsConfig strict,
                              ComponentRegistry<TargetSelector> targetSelectors) {
        this(createResolver(config), strict.searchchains(), strict.propagateSourceProperties(),
             resolveSelector(config.targetSelector(), targetSelectors));
    }

    private static TargetSelector resolveSelector(String selectorId, 
                                                  ComponentRegistry<TargetSelector> targetSelectors) {
        if (selectorId.isEmpty()) return null;
        return checkNotNull(targetSelectors.getComponent(selectorId),
                            "Missing target selector with id" + quote(selectorId));
    }

    // for testing
    public FederationSearcher(ComponentId id, SearchChainResolver searchChainResolver) {
        this(searchChainResolver, false, PropagateSourceProperties.ALL, null);
    }

    private FederationSearcher(SearchChainResolver searchChainResolver, boolean strictSearchchain,
                               PropagateSourceProperties.Enum propagateSourceProperties,
                               TargetSelector targetSelector) {
        this.searchChainResolver = searchChainResolver;
        sourceRefResolver = new SourceRefResolver(searchChainResolver);
        this.strictSearchchain = strictSearchchain;
        this.propagateSourceProperties = propagateSourceProperties;
        this.targetSelector = targetSelector;
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

            //Allow source groups to use by default.
            if (target.useByDefault())
                builder.useTargetByDefault(target.id());
        }

        return builder.build();
    }

    private static void addSearchChain(SearchChainResolver.Builder builder,
                                       FederationConfig.Target target, FederationConfig.Target.SearchChain searchChain) {
        if (!target.id().equals(searchChain.searchChainId()))
            throw new RuntimeException("Invalid federation config, " + target.id() + " != " + searchChain.searchChainId());

        builder.addSearchChain(ComponentId.fromString(searchChain.searchChainId()),
                               federationOptions(searchChain), searchChain.documentTypes());
    }

    private static void addSourceForProvider(SearchChainResolver.Builder builder, FederationConfig.Target target,
                                             FederationConfig.Target.SearchChain searchChain, boolean isDefaultProvider) {
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

    @Override
    public Result search(Query query, Execution execution) {
        Result mergedResults = execution.search(query);

        Results<SearchChainInvocationSpec, UnresolvedSearchChainException> targets =
                getTargets(query.getModel().getSources(), query.properties(), execution.context().getIndexFacts());
        warnIfUnresolvedSearchChains(targets.errors(), mergedResults.hits());

        Collection<SearchChainInvocationSpec> prunedTargets =
                pruneTargetsWithoutDocumentTypes(query.getModel().getRestrict(), targets.data());

        Results<Target, ErrorMessage> regularTargetHandlers = resolveSearchChains(prunedTargets, execution.searchChainRegistry());
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
        Optional<Result> result = search(query, execution, target);
        if (result.isPresent()) {
            mergeResult(query, target, mergedResults, result.get());
        } else {
            addSearchChainTimedOutError(query, target.getId());
        }
    }

    private void search(Query query, Execution execution, Collection<Target> targets, Result mergedResults) {
        FederationResult results = search(query, execution, targets);
        results.waitForAll((int)query.getTimeLeft(), clock);

        HitOrderer s = null;
        for (FederationResult.TargetResult targetResult : results.all()) {
            if ( ! targetResult.successfullyCompleted()) {
                addSearchChainTimedOutError(query, targetResult.target.getId());
            } else {
                if (s == null) {
                    s = dirtyCopyIfModifiedOrderer(mergedResults.hits(), targetResult.getOrTimeoutError().hits().getOrderer());
                }
                mergeResult(query, targetResult.target, mergedResults, targetResult.getOrTimeoutError());
            }
        }
    }

    private Optional<Result> search(Query query, Execution execution, Target target) {
        long timeout = target.federationOptions().getSearchChainExecutionTimeoutInMilliseconds(query.getTimeLeft());
        if (timeout <= 0) {
            return Optional.empty();
        }
        Execution newExecution = new Execution(target.getChain(), execution.context());
        if (strictSearchchain) {
            query.resetTimeout();
            return Optional.of(newExecution.search(createFederationQuery(query, query, Window.from(query), timeout, target)));
        } else {
            return Optional.of(newExecution.search(cloneFederationQuery(query, Window.from(query), timeout, target)));
        }
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

        switch (propagateSourceProperties) {
            case ALL:
                propagatePerSourceQueryProperties(query, outgoing, window, sourceName, providerName,
                                                  Query.nativeProperties);
                break;
            case OFFSET_HITS:
                propagatePerSourceQueryProperties(query, outgoing, window, sourceName, providerName,
                                                  queryAndHits);
                break;
        }

        //TODO: FederationTarget
        //TODO: only for target produced by this, not others
        target.modifyTargetQuery(outgoing);
        return outgoing;
    }

    private void propagatePerSourceQueryProperties(Query original, Query outgoing, Window window,
                                                   String sourceName, String providerName,
                                                   List<CompoundName> queryProperties) {
        for (CompoundName key : queryProperties) {
            Object value = getSourceOrProviderProperty(original, key, sourceName, providerName, window.get(key));
            if (value != null)
                outgoing.properties().set(key, value);
        }
    }

    private Object getSourceOrProviderProperty(Query query, CompoundName propertyName,
                                                String sourceName, String providerName,
                                                Object defaultValue) {
        Object result = getProperty(query, new SourceKey(sourceName, propertyName.toString()));
        if (result == null)
            result = getProperty(query, new ProviderKey(providerName, propertyName.toString()));
        if (result == null)
            result = defaultValue;
        return result;
    }

    private Object getProperty(Query query, CompoundKey key) {
        CompoundName name = map.get(key);
        if (name == null) {
            name = new CompoundName(key.toString());
            map.put(key, name);
        }
        return query.properties().get(name);
    }

    private ErrorMessage missingSearchChainsErrorMessage(List<UnresolvedSearchChainException> unresolvedSearchChainExceptions) {
        String message =  StringUtils.join(getMessagesSet(unresolvedSearchChainExceptions), ' ') + 
                          " Valid source refs are " + StringUtils.join(allSourceRefDescriptions().iterator(), ", ") +'.';
        return ErrorMessage.createInvalidQueryParameter(message);
    }

    private List<String> allSourceRefDescriptions() {
        List<String> descriptions = new ArrayList<>();

        for (com.yahoo.search.federation.sourceref.Target target : searchChainResolver.allTopLevelTargets()) {
            descriptions.add(target.searchRefDescription());
        }
        return descriptions;
    }

    private Set<String> getMessagesSet(List<UnresolvedSearchChainException> unresolvedSearchChainExceptions) {
        Set<String> messages = new LinkedHashSet<>();
        for (UnresolvedSearchChainException exception : unresolvedSearchChainExceptions) {
            messages.add(exception.getMessage());
        }
        return messages;
    }

    private void warnIfUnresolvedSearchChains(List<UnresolvedSearchChainException> missingTargets,
                                      HitGroup errorHitGroup) {
        if (!missingTargets.isEmpty()) {
            errorHitGroup.addError(missingSearchChainsErrorMessage(missingTargets));
        }
    }

    @Override
    public Collection<CommentedSearchChain> getSearchChainsForwarded(SearchChainRegistry registry) {
        List<CommentedSearchChain> searchChains = new ArrayList<>();

        for (com.yahoo.search.federation.sourceref.Target target : searchChainResolver.allTopLevelTargets()) {
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

    /** 
     * Returns the set of properties set for the source or provider given in the query (if any).
     *
     * If the query has not set sourceName or providerName, null will be returned 
     */
    public static Properties getSourceProperties(Query query) {
        String sourceName = query.properties().getString(SOURCENAME);
        String providerName = query.properties().getString(PROVIDERNAME);
        if (sourceName == null || providerName == null)
            return null;
        Properties sourceProperties = new SubProperties("source." + sourceName, query.properties());
        Properties providerProperties = new SubProperties("provider." + providerName, query.properties());
        sourceProperties.chain(providerProperties);
        return sourceProperties;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        UniqueExecutionsToResults uniqueExecutionsToResults = new UniqueExecutionsToResults();
        addResultsToFill(result.hits(), result, summaryClass, uniqueExecutionsToResults);
        Set<Entry<Chain<Searcher>, Map<Query, Result>>> resultsForAllChains = 
                uniqueExecutionsToResults.resultsToFill.entrySet();
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
                    // Note that some of these hits may be filled, but as the fill thread may still be working on them
                    // and we do not synchronize with it we need to discard all
                    Hit removed = result.hits().remove(i.next().getId());
                }
            }
        }
    }

    private void propagateErrors(Result source, Result destination) {
        ErrorMessage error = source.hits().getError();
        if (error != null)
            destination.hits().addError(error);
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
     * @param group  The merging hitgroup to be updated if necessary
     * @param orderer The per provider hit orderer.
     * @return The hitorderer chosen
     */
    private HitOrderer dirtyCopyIfModifiedOrderer(HitGroup group, HitOrderer orderer) {
        if (orderer != null) {
            HitOrderer old = group.getOrderer();
            if ((old == null) || ! orderer.equals(old)) {
                group.setOrderer(orderer);
            }
        }

        return orderer;
    }

    private Results<SearchChainInvocationSpec, UnresolvedSearchChainException> getTargets(Set<String> sources, Properties properties, IndexFacts indexFacts) {
        return sources.isEmpty() ?
                defaultSearchChains(properties):
                resolveSources(sources, properties, indexFacts);
    }

    private Results<SearchChainInvocationSpec, UnresolvedSearchChainException> resolveSources(Set<String> sources, Properties properties, IndexFacts indexFacts) {
        Results.Builder<SearchChainInvocationSpec, UnresolvedSearchChainException> result = new Builder<>();

        for (String source : sources) {
            try {
                result.addAllData(sourceRefResolver.resolve(asSourceSpec(source), properties, indexFacts));
            } catch (UnresolvedSearchChainException e) {
                result.addError(e);
            }
        }

        return result.build();
    }

    public Results<SearchChainInvocationSpec, UnresolvedSearchChainException> defaultSearchChains(Properties sourceToProviderMap) {
        Results.Builder<SearchChainInvocationSpec, UnresolvedSearchChainException> result = new Builder<>();

        for (com.yahoo.search.federation.sourceref.Target target : searchChainResolver.defaultTargets()) {
            try {
                result.addData(target.responsibleSearchChain(sourceToProviderMap));
            } catch (UnresolvedSearchChainException e) {
                result.addError(e);
            }
        }

        return result.build();
    }


    private ComponentSpecification asSourceSpec(String source) {
        try {
            return new ComponentSpecification(source);
        } catch(Exception e) {
            throw new IllegalArgumentException("The source ref '" + source + "' used for federation is not valid.", e);
        }
    }

    private void traceTargets(Query query, Collection<Target> targets) {
        int traceFederationLevel = 2;
        if ( ! query.isTraceable(traceFederationLevel)) return;
        query.trace("Federating to " + targets, traceFederationLevel);
    }

    /**
     * Returns true if we are requested to keep executing a target longer than we're waiting for it.
     * This is useful to populate caches inside targets.
     */
    private boolean shouldExecuteTargetLongerThanThread(Query query, Target target) {
        return target.federationOptions().getRequestTimeoutInMilliseconds() > query.getTimeout();
    }

    private static void addSearchChainTimedOutError(Query query, ComponentId searchChainId) {
        ErrorMessage timeoutMessage = ErrorMessage.createTimeout("The search chain '" + searchChainId + "' timed out.");
        timeoutMessage.setSource(searchChainId.stringValue());
        query.errors().add(timeoutMessage);
    }

    private void mergeResult(Query query, Target target, Result mergedResults, Result result) {
        target.modifyTargetResult(result);
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
        if (query.getTraceLevel()>=4)
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
        if (targetSelector == null) return Collections.emptyList();

        ArrayList<Target> result = new ArrayList<>();
        for (FederationTarget<T> target: targetSelector.getTargets(query, execution.searchChainRegistry()))
            result.add(new CustomTarget<>(targetSelector, target));

        return result;
    }

    private Collection<SearchChainInvocationSpec> pruneTargetsWithoutDocumentTypes(Set<String> restrict, List<SearchChainInvocationSpec> targets) {
        if (restrict.isEmpty()) return targets;

        Collection<SearchChainInvocationSpec> prunedTargets = new ArrayList<>();

        for (SearchChainInvocationSpec target : targets) {
            if (target.documentTypes.isEmpty() || documentTypeIntersectionIsNonEmpty(restrict, target))
                prunedTargets.add(target);
        }

        return prunedTargets;
    }

    private boolean documentTypeIntersectionIsNonEmpty(Set<String> restrict, SearchChainInvocationSpec target) {
        for (String documentType : target.documentTypes) {
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
            Map<Query,Result> resultsToFillForAChain = resultsToFill.get(chain);
            if (resultsToFillForAChain == null) {
                resultsToFillForAChain = new IdentityHashMap<>();
                resultsToFill.put(chain,resultsToFillForAChain);
            }

            Result resultsToFillForAChainAndQuery = resultsToFillForAChain.get(query);
            if (resultsToFillForAChainAndQuery == null) {
                resultsToFillForAChainAndQuery = new Result(query);
                resultsToFillForAChain.put(query, resultsToFillForAChainAndQuery);
            }

            return resultsToFillForAChainAndQuery;
        }

    }

    /** A target for federation, containing a chain to which a federation query can be forwarded. */
    static abstract class Target {

        abstract Chain<Searcher> getChain();
        abstract void modifyTargetQuery(Query query);
        abstract void modifyTargetResult(Result result);

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
        void modifyTargetQuery(Query query) {}
        @Override
        void modifyTargetResult(Result result) {}

        @Override
        public FederationOptions federationOptions() { return target.federationOptions; }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! ( o instanceof StandardTarget)) return false;

            StandardTarget other = (StandardTarget)o;
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

    private static class CompoundKey {

        private final String sourceName;
        private final String propertyName;

        CompoundKey(String sourceName, String propertyName) {
            this.sourceName = sourceName;
            this.propertyName = propertyName;
        }

        @Override
        public int hashCode() {
            return sourceName.hashCode() ^ propertyName.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            CompoundKey rhs = (CompoundKey) o;
            return sourceName.equals(rhs.sourceName) && propertyName.equals(rhs.propertyName);
        }

        @Override
        public String toString() {
            return sourceName + '.' + propertyName;
        }
    }

    private static class SourceKey extends CompoundKey {

        public static final String SOURCE = "source.";

        SourceKey(String sourceName, String propertyName) {
            super(sourceName, propertyName);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ 7;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof SourceKey) && super.equals(o);
        }

        @Override
        public String toString() {
            return SOURCE + super.toString();
        }
    }

    private static class ProviderKey extends CompoundKey {

        public static final String PROVIDER = "provider.";

        ProviderKey(String sourceName, String propertyName) {
            super(sourceName, propertyName);
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ 17;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof ProviderKey) && super.equals(o);
        }

        @Override
        public String toString() {
            return PROVIDER + super.toString();
        }

    }
    
    private static class Window {
        
        private final int hits;
        private final int offset;
        
        public Window(int hits, int offset) {
            this.hits = hits;
            this.offset = offset;
        }

        public Integer get(CompoundName parameterName) {
            if (parameterName.equals(Query.HITS)) return hits;
            if (parameterName.equals(Query.OFFSET)) return offset;
            return null;
        }
        
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
