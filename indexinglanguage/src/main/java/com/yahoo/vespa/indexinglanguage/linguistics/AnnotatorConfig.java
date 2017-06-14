// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.linguistics;

import com.yahoo.language.Language;
import com.yahoo.language.process.StemMode;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;

/**
 * @author Simon Thoresen
 */
public class AnnotatorConfig implements Cloneable {

    private Language language;
    private StemMode stemMode;
    private boolean removeAccents;
    private int maxTermOccurences;

    public static final int DEFAULT_MAX_TERM_OCCURRENCES;

    static {
        IlscriptsConfig defaults = new IlscriptsConfig(new IlscriptsConfig.Builder());
        DEFAULT_MAX_TERM_OCCURRENCES = defaults.maxtermoccurrences();
    }

    public AnnotatorConfig() {
        language = Language.ENGLISH;
        stemMode = StemMode.NONE;
        removeAccents = false;
        maxTermOccurences = DEFAULT_MAX_TERM_OCCURRENCES;
    }

    public AnnotatorConfig(AnnotatorConfig rhs) {
        language = rhs.language;
        stemMode = rhs.stemMode;
        removeAccents = rhs.removeAccents;
        maxTermOccurences = rhs.maxTermOccurences;
    }

    public Language getLanguage() {
        return language;
    }

    public AnnotatorConfig setLanguage(Language language) {
        this.language = language;
        return this;
    }

    public StemMode getStemMode() {
        return stemMode;
    }

    public AnnotatorConfig setStemMode(StemMode stemMode) {
        this.stemMode = stemMode;
        return this;
    }

    public AnnotatorConfig setStemMode(String name) {
        this.stemMode = StemMode.valueOf(name);
        return this;
    }

    public boolean getRemoveAccents() {
        return removeAccents;
    }

    public AnnotatorConfig setRemoveAccents(boolean removeAccents) {
        this.removeAccents = removeAccents;
        return this;
    }

    public int getMaxTermOccurrences() {
        return maxTermOccurences;
    }

    public AnnotatorConfig setMaxTermOccurrences(int maxTermCount) {
        this.maxTermOccurences = maxTermCount;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnnotatorConfig)) {
            return false;
        }
        AnnotatorConfig rhs = (AnnotatorConfig)obj;
        if (!language.equals(rhs.language)) {
            return false;
        }
        if (!stemMode.equals(rhs.stemMode)) {
            return false;
        }
        if (removeAccents != rhs.removeAccents) {
            return false;
        }
        if (maxTermOccurences != rhs.maxTermOccurences) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + language.hashCode() + stemMode.hashCode() +
               Boolean.valueOf(removeAccents).hashCode() + maxTermOccurences;
    }
}
