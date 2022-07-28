// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.searcher.ValidateSortingSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Check sorting validation behaves OK.
 *
 * @author Steinar Knutsen
 */
public class ValidateSortingSearcherTestCase {

    private final ValidateSortingSearcher searcher;

    @SuppressWarnings("deprecation")
    public ValidateSortingSearcherTestCase() {
        QrSearchersConfig.Builder qrsCfg = new QrSearchersConfig.Builder();
        qrsCfg.searchcluster(new QrSearchersConfig.Searchcluster.Builder().name("giraffes"));
        ClusterConfig.Builder clusterCfg = new ClusterConfig.Builder().
                clusterId(0).
                clusterName("test");
        String attributesCfg = "file:src/test/java/com/yahoo/prelude/searcher/test/validate_sorting.cfg";
        searcher = new ValidateSortingSearcher(new QrSearchersConfig(qrsCfg),
                                               new ClusterConfig(clusterCfg),
                                               ConfigGetter.getConfig(AttributesConfig.class, attributesCfg));
    }

    @Test
    void testBasicValidation() {
        assertNotNull(quoteAndTransform("+a -b +c"));
        assertNotNull(quoteAndTransform("+a"));
        assertNotNull(quoteAndTransform(null));
        assertEquals("[ASCENDING:[rank]]", quoteAndTransform("+[rank]"));
        assertEquals("[ASCENDING:[docid]]", quoteAndTransform("+[docid]"));
        assertEquals("[ASCENDING:[rank]]", quoteAndTransform("+[relevancy]"));
        assertEquals("[ASCENDING:[rank]]", quoteAndTransform("+[relevance]"));
    }

    @Test
    void testInvalidSpec() {
        assertNull(quoteAndTransform("+a -e +c"));
    }

    @Test
    void testConfigOverride() {
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("title"));
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("uca(title)"));
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("+uca(title)"));
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("uca(title,en_US)"));
    }

    @Test
    void requireThatQueryLocaleIsDefault() {
        assertEquals("[ASCENDING:lowercase(a)]", quoteAndTransform("a"));
        assertEquals("[ASCENDING:uca(a,en_US,PRIMARY)]", transform("a", "en-US"));
        assertEquals("[ASCENDING:uca(a,en_NO,PRIMARY)]", transform("a", "en-NO"));
        assertEquals("[ASCENDING:uca(a,no_NO,PRIMARY)]", transform("a", "no-NO"));

        assertEquals("[ASCENDING:uca(a,en_US,PRIMARY)]", quoteAndTransform("uca(a)"));
        assertEquals("[ASCENDING:uca(a,en_US,PRIMARY)]", transform("uca(a)", "en-US"));
        assertEquals("[ASCENDING:uca(a,en_NO,PRIMARY)]", transform("uca(a)", "en-NO"));
        assertEquals("[ASCENDING:uca(a,no_NO,PRIMARY)]", transform("uca(a)", "no-NO"));
    }

    private String quoteAndTransform(String sorting) {
        return transform(QueryTestCase.httpEncode(sorting), null);
    }

    @SuppressWarnings("deprecation")
    private String transform(String sorting, String language) {
        String q = "/?query=a";
        if (sorting != null) {
            q += "&sorting=" + sorting;
        }
        if (language != null) {
            q += "&language=" + language;
        }
        new Query(q);
        Result r = doSearch(searcher, new Query(q), 0, 10);
        if (r.hits().getError() != null) {
            return null;
        }
        if (r.getQuery().getRanking().getSorting() == null) {
            return "";
        }
        return r.getQuery().getRanking().getSorting().fieldOrders().toString();
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
