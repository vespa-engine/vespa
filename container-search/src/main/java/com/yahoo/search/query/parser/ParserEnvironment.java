// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.parser;

import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.language.process.SpecialTokens;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * This class encapsulates the environment of a {@link Parser}. In case you are creating a parser from within a
 * {@link Searcher}, you can use the {@link #fromExecutionContext(Execution.Context)} factory for convenience.
 *
 * @author Simon Thoresen Hult
 */
public final class ParserEnvironment {

    private IndexFacts indexFacts = new IndexFacts();
    private Linguistics linguistics = new SimpleLinguistics();
    private SpecialTokens specialTokens = SpecialTokens.empty();

    public IndexFacts getIndexFacts() {
        return indexFacts;
    }

    public ParserEnvironment setIndexFacts(IndexFacts indexFacts) {
        this.indexFacts = indexFacts;
        return this;
    }

    public Linguistics getLinguistics() {
        return linguistics;
    }

    public ParserEnvironment setLinguistics(Linguistics linguistics) {
        this.linguistics = linguistics;
        return this;
    }

    public SpecialTokens getSpecialTokens() {
        return specialTokens;
    }

    public ParserEnvironment setSpecialTokens(SpecialTokens specialTokens) {
        this.specialTokens = specialTokens;
        return this;
    }

    public static ParserEnvironment fromExecutionContext(Execution.Context context) {
        ParserEnvironment env = new ParserEnvironment();
        if (context == null) return env;

        if (context.getIndexFacts() != null)
            env.setIndexFacts(context.getIndexFacts());

        if (context.getLinguistics() != null)
            env.setLinguistics(context.getLinguistics());

        if (context.getTokenRegistry() != null)
            env.setSpecialTokens(context.getTokenRegistry().getSpecialTokens("default"));

        return env;
    }

    public static ParserEnvironment fromParserEnvironment(ParserEnvironment environment) {
        return new ParserEnvironment()
                .setIndexFacts(environment.indexFacts)
                .setLinguistics(environment.linguistics)
                .setSpecialTokens(environment.specialTokens);
    }
}
