// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.cluster.PingableSearcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;

/**
 * A lightweight searcher to forward all incoming requests to a single search
 * chain defined in config. An alternative to federation searcher when standard
 * semantics are not necessary for the application.
 *
 * @see FederationSearcher
 * @since 5.0.13
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@After("*")
public class ForwardingSearcher extends PingableSearcher {
    private final ComponentSpecification target;

    public ForwardingSearcher(final SearchchainForwardConfig config) {
        if (config.target() == null) {
            throw new RuntimeException(
                    "Configuration value searchchain-forward.target was null.");
        }
        try {
            target = new ComponentSpecification(config.target());
        } catch (RuntimeException e) {
            throw new RuntimeException(
                    "Failed constructing the component specification from searchchain-forward.target: "
                            + config.target(), e);
        }
    }

    @Override
    public Result search(final Query query, final Execution execution) {
        Execution next = createForward(execution);

        if (next == null) {
            return badResult(query);
        } else {
            return next.search(query);
        }
    }

    private Result badResult(final Query query) {
        final ErrorMessage error = noSearchchain();
        return new Result(query, error);
    }

    @Override
    public Pong ping(final Ping ping, final Execution execution) {
        Execution next = createForward(execution);

        if (next == null) {
            return badPong();
        } else {
            return next.ping(ping);
        }
    }

    private Pong badPong() {
        final Pong pong = new Pong();
        pong.addError(noSearchchain());
        return pong;
    }

    @Override
    public void fill(final Result result, final String summaryClass,
            final Execution execution) {
        Execution next = createForward(execution);
        if (next == null) {
            badFill(result.hits());
            return;
        } else {
            next.fill(result, summaryClass);
        }
    }

    private void badFill(HitGroup hits) {
        hits.addError(noSearchchain());
    }

    private Execution createForward(Execution execution) {
        Chain<Searcher> targetChain = execution.context().searchChainRegistry()
                .getComponent(target);
        if (targetChain == null) {
            return null;
        }
        return new Execution(targetChain, execution.context());
    }

    private ErrorMessage noSearchchain() {
        return ErrorMessage
                .createServerIsMisconfigured("Could not get search chain matching component specification: " + target);
    }
}
