// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.test;

import java.util.*;
import java.io.File;

import com.yahoo.search.searchchain.*;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.query.rewrite.rewriters.*;
import com.yahoo.search.query.rewrite.RewritesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test Cases for GenericExpansionRewriter
 *
 * @author Karen Lee
 */
public class GenericExpansionRewriterTestCase {

    private QueryRewriteSearcherTestUtils utils;
    private final String CONFIG_PATH = "file:src/test/java/com/yahoo/search/query/rewrite/test/" +
                                       "test_generic_expansion_rewriter.cfg";
    private final String GENERIC_EXPAND_DICT_PATH = "src/test/java/com/yahoo/search/query/rewrite/test/" +
                                                    "generic_expansion.fsa";
    private final String REWRITER_NAME = GenericExpansionRewriter.REWRITER_NAME;

    /**
     * Load the GenericExpansionRewriterSearcher and prepare the
     * execution object
     */
    @BeforeEach
    public void setUp() {
        RewritesConfig config = QueryRewriteSearcherTestUtils.createConfigObj(CONFIG_PATH);
        HashMap<String, File> fileList = new HashMap<>();
        fileList.put(GenericExpansionRewriter.GENERIC_EXPAND_DICT, new File(GENERIC_EXPAND_DICT_PATH));
        GenericExpansionRewriter searcher = new GenericExpansionRewriter(config, fileList);

        Execution execution = QueryRewriteSearcherTestUtils.createExecutionObj(searcher);
        utils = new QueryRewriteSearcherTestUtils(execution);
    }

    /**
     * MaxRewrites=3, PartialPhraseMatch is on, type=adv case
     */
    @Test
    void testPartialPhraseMaxRewriteAdvType() {
        utils.assertRewrittenQuery("?query=(modern new york city travel phone number) OR (travel agency) OR travel&type=adv&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true&" +
                REWRITER_NAME + "." + RewriterConstants.MAX_REWRITES + "=3",
                "query 'OR (AND modern (OR (AND rewrite11 rewrite12) rewrite2 rewrite3 " +
                        "(AND new york city travel)) (OR pn (AND phone number))) ta (AND travel agency) " +
                        "tr travel'");
    }

    /**
     * PartialPhraseMatch is off, type=adv case
     */
    @Test
    void testPartialPhraseNoMaxRewriteAdvType() {
        utils.assertRewrittenQuery("?query=(modern new york city travel phone number) OR (travel agency) OR travel&type=adv&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=false",
                "query 'OR (AND modern new york city travel phone number) " +
                        "ta (AND travel agency) tr travel'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is off, type=adv, added filter case
     */
    @Test
    void testFullPhraseNoMaxRewriteAdvTypeFilter() {
        utils.assertRewrittenQuery("?query=ca OR (modern new york city travel phone number) OR (travel agency) OR travel&" +
                "type=adv&filter=citystate:santa clara ca&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=false",
                "query 'RANK (OR california ca (AND modern new york city travel phone number) " +
                        "ta (AND travel agency) tr travel) |citystate:santa |clara |ca'");
    }

    /**
     * MaxRewrites=0 (i.e No MaxRewrites), PartialPhraseMatch is on, type=adv, added filter case
     */
    @Test
    void testPartialPhraseNoMaxRewriteAdvTypeFilter() {
        utils.assertRewrittenQuery("?query=ca OR (modern new york city travel phone number) OR (travel agency) OR travel&" +
                "type=adv&filter=citystate:santa clara ca&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true&" +
                REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV + "=true&" +
                REWRITER_NAME + "." + RewriterConstants.MAX_REWRITES + "=0",
                "query 'RANK (OR california ca (AND modern (OR \"rewrite11 rewrite12\" " +
                        "rewrite2 rewrite3 rewrite4 rewrite5 (AND new york city travel)) " +
                        "(OR pn (AND phone number))) ta (AND travel agency) tr travel) " +
                        "|citystate:santa |clara |ca'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is off, single word, added filter case
     */
    @Test
    void testFullPhraseNoMaxRewriteSingleWordFilter() {
        utils.assertRewrittenQuery("?query=ca&type=all&" +
                "filter=citystate:santa clara ca&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=false",
                "query 'RANK (OR california ca) |citystate:santa |clara |ca'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is on, single word, added filter case
     */
    @Test
    void testPartialPhraseNoMaxRewriteSingleWordFilter() {
        utils.assertRewrittenQuery("?query=ca&type=all&" +
                "filter=citystate:santa clara ca&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true",
                "query 'RANK (OR california ca) |citystate:santa |clara |ca'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is off, multi word, added filter case
     */
    @Test
    void testFullPhraseNoMaxRewriteMultiWordFilter() {
        utils.assertRewrittenQuery("?query=travel agency&type=all&" +
                "filter=citystate:santa clara ca&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=false",
                "query 'RANK (OR ta (AND travel agency)) |citystate:santa |clara |ca'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is on, multi word, added filter case
     */
    @Test
    void testPartialPhraseNoMaxRewriteMultiWordFilter() {
        utils.assertRewrittenQuery("?query=modern new york city travel phone number&" +
                "filter=citystate:santa clara ca&type=all&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true",
                "query 'RANK (AND modern (OR (AND rewrite11 rewrite12) rewrite2 rewrite3 " +
                        "rewrite4 rewrite5 (AND new york city travel)) (OR pn (AND phone number))) " +
                        "|citystate:santa |clara |ca'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is off, single word
     */
    @Test
    void testFullPhraseNoMaxRewriteSingleWord() {
        utils.assertRewrittenQuery("?query=ca&type=all&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=false",
                "query 'OR california ca'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is on, single word
     */
    @Test
    void testPartialPhraseNoMaxRewriteSingleWord() {
        utils.assertRewrittenQuery("?query=ca&type=all&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true",
                "query 'OR california ca'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is off, multi word
     */
    @Test
    void testFullPhraseNoMaxRewriteMultiWord() {
        utils.assertRewrittenQuery("?query=travel agency&type=all&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=false",
                "query 'OR ta (AND travel agency)'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is off, multi word, no full match
     */
    @Test
    void testFullPhraseNoMaxRewriteMultiWordNoMatch() {
        utils.assertRewrittenQuery("?query=nyc travel agency&type=all&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=false",
                "query 'AND nyc travel agency'");
    }

    /**
     * No MaxRewrites, PartialPhraseMatch is on, multi word
     */
    @Test
    void testPartialPhraseNoMaxRewriteMultiWord() {
        utils.assertRewrittenQuery("?query=modern new york city travel phone number&type=all&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true",
                "query 'AND modern (OR (AND rewrite11 rewrite12) rewrite2 rewrite3 rewrite4 rewrite5 " +
                        "(AND new york city travel)) (OR pn (AND phone number))'");
    }

    /**
     * Matching multiple word in RANK subtree
     * Dictionary contain the word "travel agency", the word "agency" and the word "travel"
     * Should rewrite travel but not travel agency in this case
     */
    @Test
    void testPartialPhraseMultiWordRankTree() {
        utils.assertRewrittenQuery("?query=travel RANK agency&type=adv&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true",
                "query 'RANK (OR tr travel) agency'");
    }

    /**
     * Matching multiple word in RANK subtree
     * Dictionary contain the word "travel agency", the word "agency" and the word "travel"
     * Should rewrite travel but not travel agency in this case
     */
    @Test
    void testFullPhraseMultiWordRankTree() {
        utils.assertRewrittenQuery("?query=travel RANK agency&type=adv&" +
                REWRITER_NAME + "." + RewriterConstants.PARTIAL_PHRASE_MATCH + "=true",
                "query 'RANK (OR tr travel) agency'");
    }

}

