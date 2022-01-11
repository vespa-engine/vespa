// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemList;
import com.yahoo.language.process.StemMode;

import java.util.List;
import java.util.Objects;

/**
 * Linguistics for a rule base
 *
 * @author bratseth
 */
public class RuleBaseLinguistics {

    private final StemMode stemMode;
    private final Language language;
    private final Linguistics linguistics;

    /** Creates a rule base with default settings */
    public RuleBaseLinguistics(Linguistics linguistics) {
        this(StemMode.BEST, Language.ENGLISH, linguistics);
    }


    public RuleBaseLinguistics(StemMode stemMode, Language language, Linguistics linguistics) {
        this.stemMode = Objects.requireNonNull(stemMode);
        this.language = Objects.requireNonNull(language);
        this.linguistics = Objects.requireNonNull(linguistics);
    }

    public RuleBaseLinguistics withStemMode(StemMode stemMode) {
        return new RuleBaseLinguistics(stemMode, language, linguistics);
    }

    public RuleBaseLinguistics withLanguage(Language language) {
        return new RuleBaseLinguistics(stemMode, language, linguistics);
    }

    public Linguistics linguistics() { return linguistics; }

    /** Processes this term according to the linguistics of this rule base */
    public String process(String term) {
        if (stemMode == StemMode.NONE) return term;
        List<StemList> stems = linguistics.getStemmer().stem(term, StemMode.BEST, language);
        if (stems.isEmpty()) return term;
        if (stems.get(0).isEmpty()) return term;
        return stems.get(0).get(0);
    }

}
