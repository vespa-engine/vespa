// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.linguistics;

import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;

import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class AnnotatorConfig implements Cloneable {

    private Language language;
    private StemMode stemMode;
    private boolean removeAccents;
    private boolean lowercase;
    private int maxTermOccurrences;
    private int maxTokenLength;
    private int maxTokenizeLength;

    public static final int DEFAULT_MAX_TERM_OCCURRENCES;
    private static final int DEFAULT_MAX_TOKEN_LENGTH;
    private static final int DEFAULT_MAX_TOKENIZE_LENGTH;

    static {
        IlscriptsConfig defaults = new IlscriptsConfig(new IlscriptsConfig.Builder());
        DEFAULT_MAX_TERM_OCCURRENCES = defaults.maxtermoccurrences();
        DEFAULT_MAX_TOKEN_LENGTH = defaults.maxtokenlength();
        DEFAULT_MAX_TOKENIZE_LENGTH = defaults.fieldmatchmaxlength();
    }

    public AnnotatorConfig() {
        language = Language.ENGLISH;
        stemMode = StemMode.NONE;
        removeAccents = false;
        lowercase = true;
        maxTermOccurrences = DEFAULT_MAX_TERM_OCCURRENCES;
        maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;
        maxTokenizeLength = DEFAULT_MAX_TOKENIZE_LENGTH;
    }

    public AnnotatorConfig(AnnotatorConfig other) {
        language = other.language;
        stemMode = other.stemMode;
        removeAccents = other.removeAccents;
        lowercase = other.lowercase;
        maxTermOccurrences = other.maxTermOccurrences;
        maxTokenLength = other.maxTokenLength;
        maxTokenizeLength = other.maxTokenizeLength;
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

    public boolean getLowercase() {
        return lowercase;
    }

    public AnnotatorConfig setLowercase(boolean lowercase) {
        this.lowercase = lowercase;
        return this;
    }

    public int getMaxTermOccurrences() {
        return maxTermOccurrences;
    }

    public AnnotatorConfig setMaxTermOccurrences(int maxTermCount) {
        this.maxTermOccurrences = maxTermCount;
        return this;
    }

    public AnnotatorConfig setMaxTokenLength(int maxTokenLength) {
        this.maxTokenLength = maxTokenLength;
        return this;
    }

    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    public static int getDefaultMaxTokenLength() { return DEFAULT_MAX_TOKEN_LENGTH; }

    public AnnotatorConfig setMaxTokenizeLength(int maxTokenizeLength) {
        this.maxTokenizeLength = maxTokenizeLength;
        return this;
    }

    public int getMaxTokenizeLength() {
        return maxTokenizeLength;
    }

    public boolean hasNonDefaultMaxTokenLength() {
        return maxTokenLength != DEFAULT_MAX_TOKEN_LENGTH;
    }

    public boolean hasNonDefaultMaxTokenizeLength() {
        return maxTokenizeLength != DEFAULT_MAX_TOKENIZE_LENGTH;
    }

    public boolean hasNonDefaultMaxTermOccurrences() {
        return maxTermOccurrences != DEFAULT_MAX_TERM_OCCURRENCES;
    }

    public LinguisticsParameters asLinguisticsParameters() {
        return new LinguisticsParameters(language, stemMode, removeAccents, lowercase);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnnotatorConfig other)) return false;
        if (!language.equals(other.language)) return false;
        if (!stemMode.equals(other.stemMode)) return false;
        if (removeAccents != other.removeAccents) return false;
        if (lowercase != other.lowercase) return false;
        if (maxTermOccurrences != other.maxTermOccurrences) return false;
        if (maxTokenLength != other.maxTokenLength) return false;
        if (maxTokenizeLength != other.maxTokenizeLength) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), language.hashCode(), stemMode.hashCode(),
                            removeAccents, lowercase, maxTermOccurrences, maxTokenLength, maxTokenizeLength);
    }

    @Override
    public String toString() {
        return "annotator config" + parameterString();
    }

    public String parameterString() {
        StringBuilder ret = new StringBuilder();
        if (getRemoveAccents())
            ret.append(" normalize");
        if ( ! getLowercase())
            ret.append(" keep-case");
        if (getStemMode() != StemMode.NONE)
            ret.append(" stem:\"" + getStemMode() + "\"");
        if (hasNonDefaultMaxTokenizeLength())
            ret.append(" max-length:" + getMaxTokenizeLength());
        if (hasNonDefaultMaxTokenLength())
            ret.append(" max-token-length:" + getMaxTokenLength());
        if (hasNonDefaultMaxTermOccurrences())
            ret.append(" max-occurrences:" + getMaxTermOccurrences());
        return ret.toString();
    }

}
