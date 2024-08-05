// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.significance;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.language.Language;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.DocumentFrequency;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.ranking.Significance;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;

import java.util.HashSet;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;

/**
 * Sets significance values on word items in the query tree.
 *
 * @author Marius Arhaug
 */
@Provides(SignificanceSearcher.SIGNIFICANCE)
@Before(STEMMING)
public class SignificanceSearcher extends Searcher {

    public final static String SIGNIFICANCE = "Significance";

    private static final Logger log = Logger.getLogger(SignificanceSearcher.class.getName());

    private final SignificanceModelRegistry significanceModelRegistry;
    private final SchemaInfo schemaInfo;

    @Inject
    public SignificanceSearcher(SignificanceModelRegistry significanceModelRegistry, SchemaInfo schemaInfo) {
        this.significanceModelRegistry = significanceModelRegistry;
        this.schemaInfo = schemaInfo;
    }

    @Override
    public Result search(Query query, Execution execution) {
        var ranking = query.getRanking();
        var rankProfileName = query.getRanking().getProfile();

        Optional<Boolean> useSignificanceModelOverride = ranking.getSignificance().getUseModel();

        if (useSignificanceModelOverride.isPresent() && !useSignificanceModelOverride.get()) {
            return execution.search(query);
        }
        if (useSignificanceModelOverride.isPresent()) {
            return calculateAndSetSignificance(query, execution);
        }
        // Determine significance setup per schema for the given rank profile
        var perSchemaSetup = schemaInfo.newSession(query).schemas().stream()
                .collect(Collectors.toMap(Schema::name, schema ->
                        // Fallback to disabled if the rank profile is not found in the schema
                        // This will result in a failure later (in a "backend searcher") anyway.
                        Optional.ofNullable(schema.rankProfiles().get(rankProfileName))
                                .map(RankProfile::useSignificanceModel).orElse(false)));
        log.log(Level.FINE, () -> "Significance setup per schema: " + perSchemaSetup);
        var uniqueSetups = new HashSet<>(perSchemaSetup.values());

        // Fail if the significance setup for the selected schemas are conflicting
        if (uniqueSetups.size() > 1) {
            var result = new Result(query);
            result.hits().addError(
                    ErrorMessage.createIllegalQuery(
                            ("Inconsistent 'significance' configuration for the rank profile '%s' in the schemas %s. " +
                                    "Use 'restrict' to limit the query to a subset of schemas " +
                                    "(https://docs.vespa.ai/en/schemas.html#multiple-schemas). " +
                                    "Specify same 'significance' configuration for all selected schemas " +
                                    "(https://docs.vespa.ai/en/reference/schema-reference.html#significance).")
                                    .formatted(rankProfileName, perSchemaSetup.keySet())));
            return result;
        }

        if (perSchemaSetup.isEmpty()) return execution.search(query);
        var useSignificanceModel = uniqueSetups.iterator().next();
        if (!useSignificanceModel) return execution.search(query);

        return calculateAndSetSignificance(query, execution);
    }

    private Result calculateAndSetSignificance(Query query, Execution execution) {
        try {
            var significanceModel = getSignificanceModelFromQueryLanguage(query);
            log.log(Level.FINE, () -> "Got model for language %s: %s"
                    .formatted(query.getModel().getParsingLanguage(), significanceModel.getId()));

            setIDF(query.getModel().getQueryTree().getRoot(), significanceModel);

            return execution.search(query);
        } catch (IllegalArgumentException e) {
            var result = new Result(query);
            result.hits().addError(
                    ErrorMessage.createIllegalQuery(e.getMessage()));
            return result;
        }
    }

    private SignificanceModel getSignificanceModelFromQueryLanguage(Query query) throws IllegalArgumentException {
        Language explicitLanguage = query.getModel().getLanguage();
        Language implicitLanguage = query.getModel().getParsingLanguage();

        if (explicitLanguage == null && implicitLanguage == null) {
            throw new IllegalArgumentException("No language found in query");
        }

        if (explicitLanguage != null) {
            if (explicitLanguage == Language.UNKNOWN) {
                return handleFallBackToUnknownLanguage();
            }
            var model = significanceModelRegistry.getModel(explicitLanguage);
            if (model.isEmpty()) {
                throw new IllegalArgumentException("No significance model available for set language " + explicitLanguage);
            }
            return model.get();
        }

        if (implicitLanguage == Language.UNKNOWN) {
            return handleFallBackToUnknownLanguage();
        }
        var model = significanceModelRegistry.getModel(implicitLanguage);
        if (model.isEmpty()) {
            throw new IllegalArgumentException("No significance model available for implicit language " + implicitLanguage);
        }
        return model.get();
    }

    private SignificanceModel handleFallBackToUnknownLanguage() throws IllegalArgumentException {
        var unknownModel = significanceModelRegistry.getModel(Language.UNKNOWN);
        var englishModel = significanceModelRegistry.getModel(Language.ENGLISH);

        if (unknownModel.isEmpty() && englishModel.isEmpty()) {
            throw new IllegalArgumentException("No significance model available for unknown or english language");
        }

        return unknownModel.orElseGet(englishModel::get);
    }

    private void setIDF(Item root, SignificanceModel significanceModel) {
        if (root == null || root instanceof NullItem) return;

        if (root instanceof WordItem wi) {
            var word = wi.getWord();
            var documentFrequency = significanceModel.documentFrequency(word);
            long N                = documentFrequency.corpusSize();
            long nq_i             = documentFrequency.frequency();
            log.log(Level.FINE, () -> "Setting document frequency for " + word + " to {frequency: " + nq_i + ", count: " + N + "}");
            wi.setDocumentFrequency(new DocumentFrequency(nq_i, N));
        } else if (root instanceof CompositeItem ci) {
            for (int i = 0; i < ci.getItemCount(); i++) {
                setIDF(ci.getItem(i), significanceModel);
            }
        }
    }
}


