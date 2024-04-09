package com.yahoo.search.significance;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

@Provides(SignificanceSearcher.SIGNIFICANCE)
public class SignificanceSearcher extends Searcher {

    public final static String SIGNIFICANCE = "Significance";
    private final SignificanceModelRegistry significanceModelRegistry;


    @Inject
    public SignificanceSearcher(SignificanceModelRegistry significanceModelRegistry) {
        this.significanceModelRegistry = significanceModelRegistry;
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (significanceModelRegistry == null) return execution.search(query);


        setIDF(query.getModel().getQueryTree().getRoot());

        return execution.search(query);
    }

    private void setIDF(Item root) {
        if (root == null || root instanceof NullItem) return;

        if (root instanceof WordItem) {

            SignificanceModel significanceModel = significanceModelRegistry.getModel(root.getLanguage());

            var documentFrequency = significanceModel.documentFrequency(((WordItem) root).getWord());
            long nq_i             = documentFrequency.frequency();
            long N                = documentFrequency.corpusSize();
            double idf            = calculateIDF(N, nq_i);

            ((WordItem) root).setSignificance(idf);
        } else if (root instanceof CompositeItem) {
            for (int i = 0; i < ((CompositeItem) root).getItemCount(); i++) {
                setIDF(((CompositeItem) root).getItem(i));
            }
        }
    }

    private static double calculateIDF(long N, long nq_i) {
        return Math.log(1 + (N - nq_i + 0.5) / (nq_i + 0.5));
    }
}


