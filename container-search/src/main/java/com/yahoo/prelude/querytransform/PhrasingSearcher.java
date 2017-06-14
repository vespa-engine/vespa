// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;


import java.util.List;

/**
 * Detects query phrases. When a phrase is detected in the query,
 * the query is mutated to reflect this fact.
 *
 * @author bratseth
 * @author Einar M R Rosenvinge
 */
@After(PhaseNames.RAW_QUERY)
@Before(PhaseNames.TRANSFORMED_QUERY)
@Provides(PhrasingSearcher.PHRASE_REPLACEMENT)
public class PhrasingSearcher extends Searcher {

    private static final CompoundName suggestonly=new CompoundName("suggestonly");

    public static final String PHRASE_REPLACEMENT = "PhraseReplacement";

    private PhraseMatcher phraseMatcher;

    @Inject
    public PhrasingSearcher(ComponentId id, QrSearchersConfig config) {
        super(id);
        setupAutomatonFile(config.com().yahoo().prelude().querytransform().PhrasingSearcher().automatonfile());
    }

    public PhrasingSearcher(String phraseAutomatonFile) {
        setupAutomatonFile(phraseAutomatonFile);
    }

    private void setupAutomatonFile(String phraseAutomatonFile) {
        if (phraseAutomatonFile == null || phraseAutomatonFile.trim().equals("")) {
            //no file, just use dummy matcher
            phraseMatcher = PhraseMatcher.getNullMatcher();
        } else {
            //use real matcher
            phraseMatcher = new PhraseMatcher(phraseAutomatonFile,true);
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (phraseMatcher.isEmpty()) return execution.search(query);
        
        List<PhraseMatcher.Phrase> replacePhrases = phraseMatcher.matchPhrases(query.getModel().getQueryTree().getRoot());
        if (replacePhrases != null && ! query.properties().getBoolean(suggestonly, false)) {
            replace(replacePhrases);
            query.trace("Replacing phrases", true, 2);
        }
        return execution.search(query);
    }

    /** Replaces all phrases longer than one word with a PhraseItem */
    private void replace(List<PhraseMatcher.Phrase> phrases) {
        // Replacing the leaf replace phrases first to preserve
        // the start index of each replace phrase until replacement
        for (int i = phrases.size()-1; i >= 0; i--) {
            PhraseMatcher.Phrase phrase = phrases.get(i);
            if (phrase.getLength() > 1)
                phrase.replace();
        }
    }

}
