// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.selection;

import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.selection.FederationTarget;

import java.util.Collection;

/**
 * Allows adding extra targets that the federation searcher should federate to.
 *
 * For each federation search call, the federation searcher will call targetSelector.getTargets.
 *
 * Then, for each target, it will:
 * 1) call modifyTargetQuery(target, query)
 * 2) call modifyTargetResult(target, result)
 *
 * @author Tony Vaagenes
 */
public interface TargetSelector<T> {

    Collection<FederationTarget<T>> getTargets(Query query, ChainRegistry<Searcher> searcherChainRegistry);

    /**
     * For modifying the query before sending it to a the target
     */
    void modifyTargetQuery(FederationTarget<T> target, Query query);

    /**
     * For modifying the result produced by the target.
     */
    void modifyTargetResult(FederationTarget<T> target, Result result);

}
