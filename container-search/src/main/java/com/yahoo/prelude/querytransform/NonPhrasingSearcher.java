// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.searchchain.Execution;

import java.util.List;

/**
 * Detects and removes certain phrases from the query.
 *
 * @author bratseth
 */
@After("rawQuery")
@Before("transformedQuery")
public class NonPhrasingSearcher extends Searcher {

    private static final CompoundName suggestonly=new CompoundName("suggestonly");

    private PhraseMatcher phraseMatcher;

    public NonPhrasingSearcher(ComponentId id, QrSearchersConfig config) {
        super(id);
        setupAutomatonFile(config.com().yahoo().prelude().querytransform().NonPhrasingSearcher().automatonfile());
    }

    /**
     * Creates a nonphrasing searcher
     *
     * @param  phraseAutomatonFile the file containing phrases which should be removed
     * @throws IllegalStateException if the automata component is unavailable
     *         in the current environment
     * @throws IllegalArgumentException if the file is not found
     */
    public NonPhrasingSearcher(String phraseAutomatonFile) {
        setupAutomatonFile(phraseAutomatonFile);
    }

    private void setupAutomatonFile(String phraseAutomatonFile) {
        if (phraseAutomatonFile == null || phraseAutomatonFile.trim().equals("")) {
            //no file, just use dummy matcher
            phraseMatcher = PhraseMatcher.getNullMatcher();
        } else {
            //use real matcher
            phraseMatcher = new PhraseMatcher(phraseAutomatonFile);
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        List<PhraseMatcher.Phrase> phrases = phraseMatcher.matchPhrases(query.getModel().getQueryTree().getRoot());
        if (phrases != null && !query.properties().getBoolean(suggestonly, false)) {
            remove(phrases);
            query.trace("Removing stop words",true,2);
        }
        return execution.search(query);
    }

    private void remove(List<PhraseMatcher.Phrase> phrases) {
        // Removing the leaf replace phrases first to preserve
        // the start index of each replace phrase until removing
        for (int i = phrases.size()-1; i >= 0; i-- ) {
            PhraseMatcher.Phrase phrase = phrases.get(i);
            if (phrase.getLength() < phrase.getOwner().getItemCount()) // Don't removeField all
                phrase.remove();
        }
    }

}
