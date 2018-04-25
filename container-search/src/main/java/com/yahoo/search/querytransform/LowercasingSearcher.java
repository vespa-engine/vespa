// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * Traverse a query tree and lowercase terms based on decision made in subclasses.
 *
 * @author Steinar Knutsen
 */
public abstract class LowercasingSearcher extends Searcher {

    private final boolean transformWeightedSets;

    public LowercasingSearcher() {
        this(new LowercasingConfig(new LowercasingConfig.Builder()));
    }

    public LowercasingSearcher(LowercasingConfig cfg) {
        this.transformWeightedSets = cfg.transform_weighted_sets();
    }

    @Override
    public Result search(Query query, Execution execution) {
        IndexFacts.Session indexFacts = execution.context().getIndexFacts().newSession(query);
        traverse(query.getModel().getQueryTree(), indexFacts);
        traverseHighlight(query.getPresentation().getHighlight(), indexFacts);
        query.trace("Lowercasing", true, 2);
        return execution.search(query);
    }

    private void traverseHighlight(Highlight highlight, IndexFacts.Session indexFacts) {
        if (highlight == null) return;

        for (AndItem item : highlight.getHighlightItems().values()) {
            traverse(item, indexFacts);
        }
    }

    private void traverse(CompositeItem base, IndexFacts.Session indexFacts) {
        for (Iterator<Item> i = base.getItemIterator(); i.hasNext();) {
            Item next = i.next();
            if (next instanceof WordItem) {
                lowerCase((WordItem) next, indexFacts);
            } else if (next instanceof CompositeItem) {
                traverse((CompositeItem) next, indexFacts);
            } else if (next instanceof WeightedSetItem) {
                if (transformWeightedSets) {
                    lowerCase((WeightedSetItem) next, indexFacts);
                }
            } else if (next instanceof WordAlternativesItem) {
                lowerCase((WordAlternativesItem) next, indexFacts);
            }
        }
    }

    private void lowerCase(WordItem word, IndexFacts.Session indexFacts) {
        if (shouldLowercase(word, indexFacts)) {
            word.setWord(toLowerCase(word.getWord()));
            word.setLowercased(true);
        }
    }

    private static final class WeightedSetToken {
        final String token;
        final String originalToken;
        final int weight;

        WeightedSetToken(String token, String originalToken, int weight) {
            this.token = token;
            this.originalToken = originalToken;
            this.weight = weight;
        }
    }

    private boolean syntheticLowerCaseCheck(String indexName, IndexFacts.Session indexFacts, boolean isFromQuery) {
        WordItem w = new WordItem("not-used", indexName, isFromQuery);
        return shouldLowercase(w, indexFacts);
    }

    private void lowerCase(WeightedSetItem set, IndexFacts.Session indexFacts) {
        if (!syntheticLowerCaseCheck(set.getIndexName(), indexFacts, true)) {
            return;
        }

        List<WeightedSetToken> terms = new ArrayList<>(set.getNumTokens());
        for (Iterator<Map.Entry<Object, Integer>> i = set.getTokens(); i.hasNext();) {
            Map.Entry<Object, Integer> e = i.next();
            if (e.getKey() instanceof String) {
                String originalToken = (String) e.getKey();
                String token = toLowerCase(originalToken);
                if ( ! originalToken.equals(token)) {
                    terms.add(new WeightedSetToken(token, originalToken, e.getValue().intValue()));
                }
            }
        }
        // has to do it in two passes on cause of the "interesting" API in
        // weighted set, and remove before put on cause of the semantics of
        // addInternal as well as changed values...
        for (WeightedSetToken t : terms) {
            set.removeToken(t.originalToken);
            set.addToken(t.token, t.weight);
        }
    }

    private void lowerCase(WordAlternativesItem alternatives, IndexFacts.Session indexFacts) {
        if (!syntheticLowerCaseCheck(alternatives.getIndexName(), indexFacts, alternatives.isFromQuery())) {
            return;
        }
        for (WordAlternativesItem.Alternative term : alternatives.getAlternatives()) {
            String lowerCased = toLowerCase(term.word);
            alternatives.addTerm(lowerCased, term.exactness * .7d);
        }

    }

    /**
     * Override this to control whether a given term should be lowercased.
     *
     * @param word a WordItem or subclass thereof which is a candidate for lowercasing
     * @return whether to convert the term to lower case
     */
    public abstract boolean shouldLowercase(WordItem word, IndexFacts.Session indexFacts);

}
