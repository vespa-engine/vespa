// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

/**
 * A collection of facts (addressed by namespace.fact in conditions)
 * over which we may write conditions
 *
 * @author bratseth
 */
public abstract class NameSpace {

    public abstract boolean matches(String term,RuleEvaluation e);

    // TODO: public abstract void produce(RuleEvaluation e);

}
