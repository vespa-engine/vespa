// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.document.TokensMode;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates the linguistics settings of fields, and clears the tokens setting of fields which
 * cannot produce alternative tokens, such that all the places which resolve the stemming of a
 * field agree on the outcome.
 * <p>
 * This must run after the match type of a field is final (which it is after
 * {@link AttributesImplicitWord}), and before {@link WordMatch}, {@link ExactMatch} and
 * {@link NGramMatch} force the stemming of a field to NONE: until then the stemming setting of a
 * field is still exactly what the user wrote, which is what the warnings here report on.
 *
 * @author arnej27959
 */
public class LinguisticsSettings extends Processor {

    public LinguisticsSettings(Schema schema, DeployLogger deployLogger,
                               RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;
        // Struct fields are not visited: a linguistics block is only accepted in a field body,
        // and its settings are not propagated to the struct fields of that field
        for (SDField field : schema.allConcreteFields()) {
            processField(field, validate);
        }
    }

    private void processField(SDField field, boolean validate) {
        if (field.getIndexLinguisticsTokens() == null && field.getSearchLinguisticsTokens() == null) return;

        String cannotProduceAlternatives = cannotProduceAlternatives(field);
        if (cannotProduceAlternatives != null) {
            if (validate)
                warn(schema, field,
                     "tokens is set in the linguistics block, but " + cannotProduceAlternatives +
                     ", so it is ignored.");
            field.setIndexLinguisticsTokens(null);
            field.setSearchLinguisticsTokens(null);
            return;
        }
        if ( ! validate) return;

        warnAboutOneSidedTokens(field);
        warnAboutIgnoredStemming(field);
        warnAboutIgnoredIndexStemming(field);
    }

    /**
     * Returns why this field cannot produce alternatives to the original token, or null if it can.
     * Word, exact and gram matching, and uri fields, are not compatible with stemming: WordMatch,
     * ExactMatch, NGramMatch and UriHack all force the stemming of such fields to NONE.
     */
    private String cannotProduceAlternatives(SDField field) {
        if (field.isOfTypeOrNested(DataType.URI))
            return "this is a uri field, which is not tokenized by the linguistics implementation";
        if ( ! field.isOfTypeOrNested(DataType.STRING))
            return "this is not a string field";
        MatchType matchType = field.getMatching().getType();
        if (matchType != MatchType.TEXT)
            return "matching is " + matchType.toString().toLowerCase() + ", which produces a single token";
        return null;
    }

    private void warnAboutOneSidedTokens(SDField field) {
        boolean indexTokensSet = field.getIndexLinguisticsTokens() != null;
        boolean searchTokensSet = field.getSearchLinguisticsTokens() != null;
        if (indexTokensSet == searchTokensSet) return;

        String setFor = indexTokensSet ? "indexing" : "searching";
        String notSetFor = indexTokensSet ? "searching" : "indexing";
        warn(schema, field,
             "tokens is set for " + setFor + " but not for " + notSetFor + ", which will use the " +
             "stemming setting instead. This may lead to recall issues.");
    }

    /**
     * The stemming setting of the field loses to its tokens setting: say so, but only for the
     * sides where the two actually differ. A stemming setting which says the same as tokens is
     * redundant, not ignored, and not worth a warning.
     */
    private void warnAboutIgnoredStemming(SDField field) {
        Stemming stemming = field.getStemming();
        if (stemming == null) return;

        boolean ignoredWhenIndexing = overrides(field.getIndexLinguisticsTokens(), stemming);
        boolean ignoredWhenSearching = overrides(field.getSearchLinguisticsTokens(), stemming);
        if ( ! ignoredWhenIndexing && ! ignoredWhenSearching) return;

        String ignoredWhen = ignoredWhenIndexing && ignoredWhenSearching ? ""
                                                                         : ignoredWhenIndexing ? " when indexing" : " when searching";
        warn(schema, field,
             "stemming: " + stemming.getName() + " is ignored" + ignoredWhen +
             " because this field sets tokens in its linguistics block.");
    }

    /** Returns whether the given tokens setting is set and says something else than the given stemming. */
    private static boolean overrides(TokensMode tokens, Stemming stemming) {
        return tokens != null && tokens.toStemming() != stemming;
    }

    /** An index level stemming setting loses to the tokens setting of the field: say so, if they differ. */
    private void warnAboutIgnoredIndexStemming(SDField field) {
        TokensMode tokens = field.getSearchLinguisticsTokens();
        if (tokens == null) return;

        Stemming indexStemming = field.getStemmingOfIndex(schema);
        if (indexStemming == null || indexStemming == tokens.toStemming()) return;
        warn(schema, field,
             "stemming: " + indexStemming.getName() + " of index '" + field.getName() +
             "' is ignored because this field sets tokens in its linguistics block.");
    }

}
