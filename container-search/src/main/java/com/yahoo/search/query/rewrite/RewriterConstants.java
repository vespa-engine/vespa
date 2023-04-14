// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.vespa.defaults.Defaults;

/**
 * Contains common constant strings used by rewriters
 *
 * @author Karen Sze Wing Lee
 */
public class RewriterConstants {

    /** Config flag for addUnitToOriginalQuery */
    public static final String ORIGINAL_AS_UNIT = "OriginalAsUnit";

    /** Config flag for addUnitEquivToOriginalQuery */
    public static final String ORIGINAL_AS_UNIT_EQUIV = "OriginalAsUnitEquiv";

    /** Config flag for addRewritesAsEquiv(false) */
    public static final String REWRITES_AS_EQUIV = "RewritesAsEquiv";

    /** Config flag for addRewritesAsEquiv(true) */
    public static final String REWRITES_AS_UNIT_EQUIV = "RewritesAsUnitEquiv";

    /** Config flag for addExpansions */
    public static final String PARTIAL_PHRASE_MATCH = "PartialPhraseMatch";

    /** Config flag for max number of rewrites added per rewriter */
    public static final String MAX_REWRITES = "MaxRewrites";

    /** Config flag for considering QSS Rewrite in spell correction */
    public static final String QSS_RW = "QSSRewrite";

    /** Config flag for considering QSS Suggest in spell correction */
    public static final String QSS_SUGG = "QSSSuggest";

    /** Config flag for expansion index name */
    public static final String EXPANSION_INDEX = "ExpansionIndex";

    /** Name for market chain retrieval from user param */
    public static final String REWRITER_CHAIN = "QRWChain";

    /** Name for rewrite metadata retrieval from query properties */
    public static final CompoundName REWRITE_META = CompoundName.from("RewriteMeta");

    /** Name for rewritten field retrieval from query properties */
    public static final String REWRITTEN = "Rewritten";

    /** Name for new dictionary key field retrieval from query properties */
    public static final String DICT_KEY = "DictKey";

    /** Default dictionaries dir */
    public static final String DEFAULT_DICT_DIR = Defaults.getDefaults().underVespaHome("share/qrw_data/");
}
