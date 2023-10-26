// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;

/**
 * Superclass of ranking expression optimizers
 *
 * @author bratseth
 */
public abstract class Optimizer {

    private boolean enabled=true;

    /** Sets whether this optimizer is enabled. Default true */
    public void setEnabled(boolean enabled) { this.enabled=enabled; }

    /** Returns whether this is enabled */
    public boolean isEnabled() { return enabled; }

    public abstract void optimize(RankingExpression expression, ContextIndex context, OptimizationReport report);

}
