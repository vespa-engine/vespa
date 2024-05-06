// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.significance;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.language.Language;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;

import java.util.HashSet;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;

/**
 * Sets significance values on word items in the query tree.
 *
 * @author MariusArhaug
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
        var rankProfileName = query.getRanking().getProfile();

        // Determine significance setup per schema for the given rank profile
        var perSchemaSetup = schemaInfo.newSession(query).schemas().stream()
                .collect(Collectors.toMap(Schema::name, schema ->
                        // Fallback to disabled if the rank profile is not found in the schema
                        // This will result in a failure later (in a "backend searcher") anyway.
                        Optional.ofNullable(schema.rankProfiles().get(rankProfileName))
                                .map(RankProfile::useSignificanceModel).orElse(false)));
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

        Language language = query.getModel().getParsingLanguage();
        Optional<SignificanceModel> model = significanceModelRegistry.getModel(language);

        if (model.isEmpty()) return execution.search(query);

        setIDF(query.getModel().getQueryTree().getRoot(), model.get());

        return execution.search(query);
    }

    private void setIDF(Item root, SignificanceModel significanceModel) {
        if (root == null || root instanceof NullItem) return;

        if (root instanceof WordItem) {

            var documentFrequency = significanceModel.documentFrequency(((WordItem) root).getWord());
            long N                = documentFrequency.corpusSize();
            long nq_i             = documentFrequency.frequency();
            double idf            = calculateIDF(N, nq_i);

            ((WordItem) root).setSignificance(idf);
        } else if (root instanceof CompositeItem) {
            for (int i = 0; i < ((CompositeItem) root).getItemCount(); i++) {
                setIDF(((CompositeItem) root).getItem(i), significanceModel);
            }
        }
    }

    public static double calculateIDF(long N, long nq_i) {
        return Math.log(1 + (N - nq_i + 0.5) / (nq_i + 0.5));
    }
}


