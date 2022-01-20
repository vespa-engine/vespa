// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A list of the productions of a rule
 *
 * @author bratseth
 */
public class ProductionList {

    private final List<Production> productions = new java.util.ArrayList<>();

    /** True to replace by the production, false to add it */
    private boolean replacing = true;

    public void addProduction(Production term) {
        term.setReplacing(replacing);
        term.setPosition(productions.size());
        productions.add(term);
    }

    /** True to replace, false to add, default true */
    void setReplacing(boolean replacing) {
        for (Iterator<Production> i = productions.iterator(); i.hasNext(); ) {
            Production production = i.next();
            production.setReplacing(replacing);
        }

        this.replacing = replacing;
    }

    /** Returns an unmodifiable view of the productions in this */
    public List<Production> productionList() { return Collections.unmodifiableList(productions); }

    public int getTermCount() { return productions.size(); }

    void addMatchReferences(Set<String> matchReferences) {
        for (Iterator<Production> i = productions.iterator(); i.hasNext(); ) {
            Production term = i.next();
            term.addMatchReferences(matchReferences);
        }
    }

    public void produce(RuleEvaluation e) {
        for (int i = 0; i < productions.size(); i++) {
            productions.get(i).produce(e, i);
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        for (Iterator<Production> i = productions.iterator(); i.hasNext(); ) {
            buffer.append(i.next());
            if (i.hasNext())
                buffer.append(" ");
        }
        return buffer.toString();
    }

}
