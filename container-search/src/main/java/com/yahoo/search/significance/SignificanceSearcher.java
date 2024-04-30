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
import com.yahoo.search.query.Ranking;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.util.HashMap;
import java.util.Optional;

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
    private final SignificanceModelRegistry significanceModelRegistry;
    private final RankProfilesConfig rankProfilesConfig;

    private final HashMap<String, Boolean> useModel = new HashMap<>();


    @Inject
    public SignificanceSearcher(SignificanceModelRegistry significanceModelRegistry, RankProfilesConfig rankProfilesConfig) {
        this.significanceModelRegistry = significanceModelRegistry;
        this.rankProfilesConfig = rankProfilesConfig;

        for (RankProfilesConfig.Rankprofile profile : rankProfilesConfig.rankprofile()) {
            for (RankProfilesConfig.Rankprofile.Fef.Property property : profile.fef().property()) {
                if (property.name().equals("vespa.significance.use_model")) {
                    useModel.put(profile.name(), Boolean.parseBoolean(property.value()));
                }
            }
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        Ranking ranking = query.getRanking();
        if (!useModel.containsKey(ranking.getProfile()) || !useModel.get(ranking.getProfile())) return execution.search(query);

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


