// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.querytransform.NormalizingSearcher;
import com.yahoo.search.searchchain.Execution;

import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bratseth
 */
public class NormalizingSearcherTestCase {

    private static final Linguistics linguistics = new SimpleLinguistics();

    @Test
    void testNoNormalizingNecssary() {
        Query query = new Query("/search?query=bilen&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) bilen", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testAttributeQuery() {
        Query query = new Query("/search?query=attribute:" + enc("b\u00e9yonc\u00e8 b\u00e9yonc\u00e8") + "&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) attribute:b\u00e9yonc\u00e8 beyonce", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testOneTermNormalizing() {
        Query query = new Query("/search?query=b\u00e9yonc\u00e8&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) beyonce", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testOneTermNoNormalizingDifferentSearchDef() {
        Query query = new Query("/search?query=b\u00e9yonc\u00e8&search=cluster1&restrict=type2");
        createExecution().search(query);
        assertEquals("WEAKAND(100) béyoncè", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testTwoTermQuery() throws UnsupportedEncodingException {
        Query query = new Query("/search?query=" + enc("b\u00e9yonc\u00e8 beyonc\u00e9") + "&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) beyonce beyonce", query.getModel().getQueryTree().getRoot().toString());
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testPhraseQuery() {
        Query query = new Query("/search?query=" + enc("\"b\u00e9yonc\u00e8 beyonc\u00e9\"") + "&search=cluster1&restrict=type1");
        query.getTrace().setLevel(2);
        createExecution().search(query);
        assertEquals("WEAKAND(100) \"beyonce beyonce\"", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testLiteralBoost() {
        Query query = new Query("/search?query=nop&search=cluster1&restrict=type1");
        List<WordAlternativesItem.Alternative> terms = new ArrayList<>();
        Substring origin = new Substring(0, 5, "h\u00F4tels");
        terms.add(new WordAlternativesItem.Alternative("h\u00F4tels", 1.0d));
        terms.add(new WordAlternativesItem.Alternative("h\u00F4tel", 0.7d));
        query.getModel().getQueryTree().setRoot(new WordAlternativesItem("default", true, origin, terms));
        createExecution().search(query);
        WordAlternativesItem w = (WordAlternativesItem) query.getModel().getQueryTree().getRoot();
        assertEquals(4, w.getAlternatives().size());
        boolean foundHotel = false;
        for (WordAlternativesItem.Alternative a : w.getAlternatives()) {
            if ("hotel".equals(a.word)) {
                foundHotel = true;
                assertEquals(.7d * .7d, a.exactness, 1e-15);
            }
        }
        assertTrue(foundHotel, "Did not find the expected normalized form \"hotel\".");
    }


    @Test
    void testPhraseSegmentNormalization() {
        Query query = new Query("/search?query=&search=cluster1&restrict=type1");
        PhraseSegmentItem phraseSegment = new PhraseSegmentItem("default", false, false);
        phraseSegment.addItem(new WordItem("net"));
        query.getModel().getQueryTree().setRoot(phraseSegment);
        assertEquals("'net'", query.getModel().getQueryTree().getRoot().toString());
        createExecution().search(query);
        assertEquals("'net'", query.getModel().getQueryTree().getRoot().toString());
    }

    private Execution createExecution() {
        return new Execution(new NormalizingSearcher(linguistics),
                             Execution.Context.createContextStub(createIndexFacts(), linguistics));
    }

    private IndexFacts createIndexFacts() {
        Map<String, List<String>> clusters = new LinkedHashMap<>();
        clusters.put("cluster1", Arrays.asList("type1", "type2", "type3"));
        clusters.put("cluster2", Arrays.asList("type4", "type5"));
        Collection<SearchDefinition> searchDefs = ImmutableList.of(
                createSearchDefinitionWithFields("type1", true),
                createSearchDefinitionWithFields("type2", false),
                new SearchDefinition("type3"),
                new SearchDefinition("type4"),
                new SearchDefinition("type5"));
        return new IndexFacts(new IndexModel(clusters, searchDefs));
    }

    private SearchDefinition createSearchDefinitionWithFields(String name, boolean normalize) {
        SearchDefinition type = new SearchDefinition(name);

        Index defaultIndex = new Index("default");
        defaultIndex.setNormalize(normalize);
        type.addIndex(defaultIndex);

        Index absoluteIndex = new Index("absolute");
        absoluteIndex.setNormalize(normalize);
        type.addIndex(absoluteIndex);

        Index normalizercheckIndex = new Index("normalizercheck");
        normalizercheckIndex.setNormalize(normalize);
        type.addIndex(normalizercheckIndex);

        Index attributeIndex = new Index("attribute");
        attributeIndex.setAttribute(true);
        type.addIndex(attributeIndex);

        return type;
    }

}
