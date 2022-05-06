// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.rewriters;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.fsa.FSA;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.*;
import com.yahoo.component.ComponentId;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.search.query.rewrite.RewritesConfig;
import com.yahoo.prelude.querytransform.PhraseMatcher;

/**
 * This rewriter would add rewrites to entities (e.g abbreviation, synonym, etc)<br>
 * to boost precision
 * - FSA dict: [normalized original query]\t[rewrite 1]\t[rewrite 2]\t[etc]<br>
 * - Features:<br>
 *   RewritesAsUnitEquiv flag: add proximity boosted rewrites<br>
 *   PartialPhraseMatch flag: whether to match whole phrase or partial phrase<br>
 *   MaxRewrites flag: the maximum number of rewrites to be added<br>
 *
 * @author Karen Sze Wing Lee
 */
@Provides("GenericExpansionRewriter")
public class GenericExpansionRewriter extends QueryRewriteSearcher {

    // Flag for skipping this rewriter if the query has been rewritten
    private final boolean SKIP_REWRITER_IF_REWRITTEN = false;

    // Name of the rewriter
    public static final String REWRITER_NAME = "GenericExpansionRewriter";

    // Generic expansion dictionary name
    public static final String GENERIC_EXPAND_DICT = "GenericExpansion";

    // Default generic expansion dictionary file name
    public static final String GENERIC_EXPAND_DICT_FILENAME = "GenericExpansionRewriter.fsa";

    // PhraseMatcher created from FSA dict
    private PhraseMatcher phraseMatcher;

    private Logger logger;


    /**
     * Constructor for GenericExpansionRewriter.
     * Load configs using default format
     */
    @Inject
    public GenericExpansionRewriter(ComponentId id,
                        FileAcquirer fileAcquirer,
                        RewritesConfig config) {
        super(id, fileAcquirer, config);
    }

    /**
     * Constructor for GenericExpansionRewriter unit test.
     * Load configs using default format
     */
    public GenericExpansionRewriter(RewritesConfig config,
                        HashMap<String, File> fileList) {
        super(config, fileList);
    }

    /**
     * Instance creation time config loading besides FSA.
     * Create PhraseMatcher from FSA dict
     */
    public boolean configure(FileAcquirer fileAcquirer,
                             RewritesConfig config,
                             HashMap<String, File> fileList) {
        logger = Logger.getLogger(GenericExpansionRewriter.class.getName());
        FSA fsa = (FSA)rewriterDicts.get(GENERIC_EXPAND_DICT);
        if (fsa==null) {
            RewriterUtils.error(logger, "Error retrieving FSA dictionary: " + GENERIC_EXPAND_DICT);
            return false;
        }
        // Create Phrase Matcher
        RewriterUtils.log(logger, "Creating PhraseMatcher");
        try {
            phraseMatcher = new PhraseMatcher(fsa, false);
        } catch (IllegalArgumentException e) {
            RewriterUtils.error(logger, "Error creating phrase matcher");
            return false;
        }

        // Match single word as well
        phraseMatcher.setMatchSingleItems(true);

        // Return all matches instead of only the longest match
        phraseMatcher.setMatchAll(true);

        return true;
    }

    /**
     * Main logic of rewriter<br>
     * - Retrieve rewrites from FSA dict<br>
     * - rewrite query using features that are enabled by user
     */
    public HashMap<String, Object> rewrite(Query query,
                                           String dictKey) throws RuntimeException {

        Boolean rewritten = false;

        // Pass the original dict key to the next rewriter
        HashMap<String, Object> result = new HashMap<>();
        result.put(RewriterConstants.REWRITTEN, rewritten);
        result.put(RewriterConstants.DICT_KEY, dictKey);

        RewriterUtils.log(logger, query,
                         "In GenericExpansionRewriter, query used for dict retrieval=[" + dictKey + "]");

        // Retrieve flags for choosing between whole query match
        // or partial query match
        String partialPhraseMatch = getQPConfig(query, RewriterConstants.PARTIAL_PHRASE_MATCH);

        if(partialPhraseMatch==null) {
            RewriterUtils.error(logger, query, "Required param " + RewriterConstants.PARTIAL_PHRASE_MATCH +
                                               " is not set, skipping rewriter");
            throw new RuntimeException("Required param " + RewriterConstants.PARTIAL_PHRASE_MATCH +
                                       " is not set, skipping rewriter");
        }

        // Retrieve max number of rewrites allowed
        int maxNumRewrites = 0;
        String maxNumRewritesStr = getQPConfig(query, RewriterConstants.MAX_REWRITES);
        if(maxNumRewritesStr!=null) {
            maxNumRewrites = Integer.parseInt(maxNumRewritesStr);
            RewriterUtils.log(logger, query,
                              "Limiting max number of rewrites to: " + maxNumRewrites);
        } else {
            RewriterUtils.log(logger, query, "No limit on number of rewrites");
        }

        // Retrieve flags for choosing whether to add
        // the rewrites as phrase, default to false
        String rewritesAsUnitEquiv = getQPConfig(query, RewriterConstants.REWRITES_AS_UNIT_EQUIV);
        if(rewritesAsUnitEquiv==null) {
            rewritesAsUnitEquiv = "false";
        }

        Set<PhraseMatcher.Phrase> matches;

        // Partial Phrase Matching
        if(partialPhraseMatch.equalsIgnoreCase("true")) {
            RewriterUtils.log(logger, query, "Partial phrase matching");

            // Retrieve longest non overlapping matches
            matches = RewriterFeatures.getNonOverlappingPartialPhraseMatches(phraseMatcher, query);

        // Full Phrase Matching if set to anything else
        } else {
            RewriterUtils.log(logger, query, "Full phrase matching");

            // Retrieve longest non overlapping matches
            matches = RewriterFeatures.getNonOverlappingFullPhraseMatches(phraseMatcher, query);
        }

        if(matches==null) {
            return result;
        }

        // Add expansions to the query
        query = RewriterFeatures.addExpansions(query, matches, null, maxNumRewrites, false,
                                               rewritesAsUnitEquiv.equalsIgnoreCase("true"));

        rewritten = true;

        RewriterUtils.log(logger, query, "GenericExpansionRewriter final query: " + query.toDetailString());

        result.put(RewriterConstants.REWRITTEN, rewritten);

        return result;
    }

    /**
     * Get the flag which specifies whether this rewriter
     * should be skipped if the query has been rewritten
     *
     * @return true if rewriter should be skipped, false
     *         otherwise
     */
    public boolean getSkipRewriterIfRewritten() {
        return SKIP_REWRITER_IF_REWRITTEN;
    }

   /**
    * Get the name of the rewriter
    *
    * @return Name of the rewriter
    */
   public String getRewriterName() {
       return REWRITER_NAME;
   }

   /**
    * Get default FSA dictionary names
    *
    * @return Pair of FSA dictionary name and filename
    */
   public HashMap<String, String> getDefaultFSAs() {
       HashMap<String, String> defaultDicts = new HashMap<>();
       defaultDicts.put(GENERIC_EXPAND_DICT, GENERIC_EXPAND_DICT_FILENAME);
       return defaultDicts;
   }
}
