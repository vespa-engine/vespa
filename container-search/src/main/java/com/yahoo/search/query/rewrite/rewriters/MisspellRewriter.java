// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.rewriters;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.*;
import com.yahoo.component.ComponentId;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.search.query.rewrite.RewritesConfig;

/**
 * This rewriter would retrieve spell corrected query from QLAS and
 * add it to the original query tree as equiv<br>
 * - Features:<br>
 *   RewritesAsEquiv flag: add rewrites to original query as equiv
 *
 * @author Karen Sze Wing Lee
 */
@After("QLAS")
@Provides("MisspellRewriter")
public class MisspellRewriter extends QueryRewriteSearcher {

    // Flag for skipping this rewriter if the query has been rewritten
    private final boolean SKIP_REWRITER_IF_REWRITTEN = false;

    // Name of the rewriter
    public static final String REWRITER_NAME = "MisspellRewriter";

    private Logger logger = Logger.getLogger(MisspellRewriter.class.getName());

    /**
     * Constructor for MisspellRewriter
     */
    @Inject
    public MisspellRewriter(ComponentId id) {
        super(id);
    }

    /**
     * Constructor for MisspellRewriter unit test
     */
    public MisspellRewriter() {
        super();
    }

    /**
     * Instance creation time config loading besides FSA.
     * Empty for this rewriter
     */
    public boolean configure(FileAcquirer fileAcquirer,
                             RewritesConfig config,
                             HashMap<String, File> fileList) {
        return true;
    }

    /**
     * Main logic of rewriter<br>
     * - Retrieve spell corrected query from QLAS<br>
     * - Add spell corrected query as equiv
     */
    public HashMap<String, Object> rewrite(Query query,
                                           String dictKey) throws RuntimeException {

        Boolean rewritten = false;

        HashMap<String, Object> result = new HashMap<>();
        result.put(RewriterConstants.REWRITTEN, rewritten);
        result.put(RewriterConstants.DICT_KEY, dictKey);

        RewriterUtils.log(logger, query,
                         "In MisspellRewriter");

        // Retrieve flags for enabling the features
        String qssRw = getQPConfig(query, RewriterConstants.QSS_RW);
        String qssSugg = getQPConfig(query, RewriterConstants.QSS_SUGG);

        boolean isQSSRw = false;
        boolean isQSSSugg = false;

        if(qssRw!=null) {
            isQSSRw = qssRw.equalsIgnoreCase("true");
        }
        if(qssSugg!=null) {
            isQSSSugg = qssSugg.equalsIgnoreCase("true");
        }

        // Rewrite is not enabled
        if(!isQSSRw && !isQSSSugg) {
            return result;
        }

        // Retrieve spell corrected query from QLAS
        String rewrites = RewriterUtils.getSpellCorrected(query, isQSSRw, isQSSSugg);

        // No rewrites
        if(rewrites==null) {
            RewriterUtils.log(logger, query, "No rewrite is retrieved");
            return result;
        } else {
            RewriterUtils.log(logger, query, "Retrieved spell corrected query: " +
                              rewrites);
        }

        // Adding rewrite to the query tree
        query = RewriterFeatures.addRewritesAsEquiv(query, dictKey, rewrites, false, 0);

        rewritten = true;
        RewriterUtils.log(logger, query, "MisspellRewriter final query: " +
                          query.toDetailString());

        result.put(RewriterConstants.REWRITTEN, rewritten);
        result.put(RewriterConstants.DICT_KEY, rewrites);

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
       return null;
   }
}
