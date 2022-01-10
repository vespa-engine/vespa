// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.rewriters;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.*;
import com.yahoo.component.ComponentId;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.search.query.rewrite.RewritesConfig;

/**
 * This rewriter would add rewrites to name entities to boost precision<br>
 * - FSA dict: [normalized original query]\t[rewrite 1]\t[rewrite 2]\t[etc]<br>
 * - Features:<br>
 *   OriginalAsUnit flag: add proximity boosting to original query<br>
 *   RewritesAsUnitEquiv flag: add proximity boosted rewrites to original query<br>
 *   RewritesAsEquiv flag: add rewrites to original query<br>
 *
 * @author Karen Sze Wing Lee
 */
@Provides("NameRewriter")
public class NameRewriter extends QueryRewriteSearcher {

    // Flag for skipping this rewriter if the query has been rewritten
    private final boolean SKIP_REWRITER_IF_REWRITTEN = false;

    // Name of the rewriter
    public static final String REWRITER_NAME = "NameRewriter";

    // Name entity expansion dictionary name
    public static final String NAME_ENTITY_EXPAND_DICT = "NameEntityExpansion";

    // Default Name entity expansion dictionary file name
    public static final String NAME_ENTITY_EXPAND_DICT_FILENAME = "NameRewriter.fsa";

    private Logger logger;

    /**
     * Constructor for NameRewriter<br>
     * Load configs using default format
     */
    @Inject
    public NameRewriter(ComponentId id,
                        FileAcquirer fileAcquirer,
                        RewritesConfig config) {
        super(id, fileAcquirer, config);
    }

    /**
     * Constructor for NameRewriter unit test<br>
     * Load configs using default format
     */
    public NameRewriter(RewritesConfig config,
                        HashMap<String, File> fileList) {
        super(config, fileList);
    }

    /**
     * Instance creation time config loading besides FSA<br>
     * Empty for this rewriter
     */
    public boolean configure(FileAcquirer fileAcquirer,
                             RewritesConfig config,
                             HashMap<String, File> fileList) {
        logger = Logger.getLogger(NameRewriter.class.getName());
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
                         "In NameRewriter, query used for dict retrieval=[" + dictKey + "]");

        // Retrieve rewrite from FSA dict using normalized query
        String rewrites = super.getRewriteFromFSA(query, NAME_ENTITY_EXPAND_DICT, dictKey);
        RewriterUtils.log(logger, query, "Retrieved rewrites: " + rewrites);

        // No rewrites
        if(rewrites==null) {
            RewriterUtils.log(logger, query, "No rewrite is retrieved");
            return result;
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

        // Retrieve flags for enabling the features
        String originalAsUnit = getQPConfig(query, RewriterConstants.ORIGINAL_AS_UNIT);
        String originalAsUnitEquiv = getQPConfig(query, RewriterConstants.ORIGINAL_AS_UNIT_EQUIV);
        String rewritesAsUnitEquiv = getQPConfig(query, RewriterConstants.REWRITES_AS_UNIT_EQUIV);
        String rewritesAsEquiv = getQPConfig(query, RewriterConstants.REWRITES_AS_EQUIV);

        // Add proximity boosting to original query and keeping
        // the original query if it's enabled
        if(originalAsUnitEquiv!=null && originalAsUnitEquiv.equalsIgnoreCase("true")) {
            RewriterUtils.log(logger, query, "OriginalAsUnitEquiv is enabled");
            query = RewriterFeatures.addUnitToOriginalQuery(query, dictKey, true);
            RewriterUtils.log(logger, query,
                              "Query after OriginalAsUnitEquiv: " + query.toDetailString());
            rewritten = true;

        // Add proximity boosting to original query
        // if it's enabled
        } else if(originalAsUnit!=null && originalAsUnit.equalsIgnoreCase("true")) {
            RewriterUtils.log(logger, query, "OriginalAsUnit is enabled");
            query = RewriterFeatures.addUnitToOriginalQuery(query, dictKey, false);
            RewriterUtils.log(logger, query,
                              "Query after OriginalAsUnit: " + query.toDetailString());
            rewritten = true;
        }

        // Add rewrites as unit equiv if it's enabled
        if(rewritesAsUnitEquiv!=null && rewritesAsUnitEquiv.equalsIgnoreCase("true")) {
            RewriterUtils.log(logger, query, "RewritesAsUnitEquiv is enabled");
            //query = RewriterFeatures.addRewritesAsEquiv(query, dictKey, rewrites, true, maxNumRewrites);
            query = RewriterFeatures.addRewritesAsEquiv(query, dictKey, rewrites, true, maxNumRewrites);
            RewriterUtils.log(logger, query,
                              "Query after RewritesAsUnitEquiv: " + query.toDetailString());
            rewritten = true;

        // Add rewrites as equiv if it's enabled
        } else if(rewritesAsEquiv!=null && rewritesAsEquiv.equalsIgnoreCase("true")) {
            RewriterUtils.log(logger, query, "RewritesAsEquiv is enabled");
            //query = RewriterFeatures.addRewritesAsEquiv(query, dictKey, rewrites, false, maxNumRewrites);
            query = RewriterFeatures.addRewritesAsEquiv(query, dictKey, rewrites, false, maxNumRewrites);
            RewriterUtils.log(logger, query,
                              "Query after RewritesAsEquiv: " + query.toDetailString());
            rewritten = true;
        }

        RewriterUtils.log(logger, query, "NameRewriter final query: " + query.toDetailString());

        result.put(RewriterConstants.REWRITTEN, rewritten);

        return result;
    }

    /**
     * Get the flag which specifies whether this rewriter.
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
       defaultDicts.put(NAME_ENTITY_EXPAND_DICT, NAME_ENTITY_EXPAND_DICT_FILENAME);
       return defaultDicts;
   }
}
