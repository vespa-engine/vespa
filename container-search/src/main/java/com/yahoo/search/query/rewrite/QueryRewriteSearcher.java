// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite;

import com.google.inject.Inject;
import com.yahoo.search.*;
import com.yahoo.config.*;
import com.yahoo.search.query.rewrite.RewritesConfig.FsaDict;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.fsa.FSA;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.component.ComponentId;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * <p>A template class for all rewriters</p>
 *
 * <p>All rewriters extending this class would need to implement the
 * rewrite method which contains the rewriter's main logic,
 * getSkipRewriterIfRewritten method which indicates whether this
 * rewriter should be skipped if the query has been rewritten,
 * getRewriterName method which returns the name of the rewriter used
 * in query profile, configure method which contains any instance
 * creation time configuration besides the default FSA loading, and
 * getDefaultDicts method which return the pair of dictionary name
 * and filename.</p>
 *
 * <p>Common rewrite features are in RewriterFeatures.java.
 * Common rewriter utils are in RewriterUtils.java.</p>
 *
 * @author Karen Sze Wing Lee
 */
public abstract class QueryRewriteSearcher extends Searcher {

    // Indicate whether rewriter is properly initiated
    private boolean isOk = false;

    protected final Logger logger = Logger.getLogger(QueryRewriteSearcher.class.getName());

    // HashMap which store the rewriter dicts
    // It has the following format:
    // HashMap<String(e.g. dictionary name, etc),
    //         Object(e.g. FSA, etc)>>
    protected HashMap<String, Object> rewriterDicts = new HashMap<>();

    /**
     * Constructor for this rewriter.
     * Prepare the data needed by the rewriter
     * @param id Component ID (see vespa's search container doc for more detail)
     * @param fileAcquirer Required param for retrieving file type config
     *                     (see vespa's search container doc for more detail)
     * @param config Config from vespa-services.xml (see vespa's search
     *               container doc for more detail)
     */
    @Inject
    protected QueryRewriteSearcher(ComponentId id,
                                   FileAcquirer fileAcquirer,
                                   RewritesConfig config) {
        super(id);
        RewriterUtils.log(logger, "In QueryRewriteSearcher(ComponentId id, " +
                                  "FileAcquirer fileAcquirer, " +
                                  "RewritesConfig config)");
        isOk = loadFSADicts(fileAcquirer, config, null);
        isOk = isOk && configure(fileAcquirer, config, null);
        if(isOk) {
            RewriterUtils.log(logger, "Rewriter is configured properly");
        } else {
            RewriterUtils.log(logger, "Rewriter is not configured properly");
        }
    }

    /**
     * Constructor for unit test.
     * Prepare the data needed by the rewriter
     * @param config Config from vespa-services.xml (see vespa's search
     *               container doc for more detail)
     * @param fileList pairs of file name and file handler for unit tests
     */
    protected QueryRewriteSearcher(RewritesConfig config,
                                   HashMap<String, File> fileList) {
        RewriterUtils.log(logger, "In QueryRewriteSearcher(RewritesConfig config, " +
                                  "HashMap<String, File> fileList)");
        isOk = loadFSADicts(null, config, fileList);
        isOk = isOk && configure(null, config, fileList);
        if(isOk) {
            RewriterUtils.log(logger, "Rewriter is configured properly");
        } else {
            RewriterUtils.log(logger, "Rewriter is not configured properly");
        }
    }

    /**
     * Empty constructor.
     * Do nothing at instance creation time
     */
    protected QueryRewriteSearcher(ComponentId id) {
        super(id);
        RewriterUtils.log(logger, "In QueryRewriteSearcher(Component id)");
        RewriterUtils.log(logger, "Configuring rewriter: " + getRewriterName());
        isOk = true;
        RewriterUtils.log(logger, "Rewriter is configured properly");
    }

    /**
     * Empty constructor for unit test.
     * Do nothing at instance creation time
     */
    protected QueryRewriteSearcher() {
        RewriterUtils.log(logger, "In QueryRewriteSearcher()");
        RewriterUtils.log(logger, "Configuring rewriter: " + getRewriterName());
        isOk = true;
        RewriterUtils.log(logger, "Rewriter is configured properly");
    }

    /**
     * Load the dicts specified in vespa-services.xml
     *
     * @param fileAcquirer Required param for retrieving file type config
     *                     (see vespa's search container doc for more detail)
     * @param config Config from vespa-services.xml (see vespa's search
     *               container doc for more detail)
     * @param fileList pairs of file name and file handler for unit tests
     * @return boolean true if loaded successfully, false otherwise
     */
    private boolean loadFSADicts(FileAcquirer fileAcquirer,
                                 RewritesConfig config,
                                 HashMap<String, File> fileList)
                                 throws RuntimeException {

        // Check if getRewriterName method is properly implemented
        String rewriterName = getRewriterName();
        if(rewriterName==null) {
            RewriterUtils.error(logger, "Rewriter required method is not properly implemented: ");
            return false;
        }

        RewriterUtils.log(logger, "Configuring rewriter: " + rewriterName);

        // Check if there's no config need to be loaded
        if(config==null || (fileAcquirer==null && fileList==null)) {
            RewriterUtils.log(logger, "No FSA dictionary file need to be loaded");
            return true;
        }

        // Check if config contains the FSADict param
        if(config.fsaDict()==null) {
            RewriterUtils.error(logger, "FSADict is not properly set in config");
            return false;
        }

        RewriterUtils.log(logger, "Loading rewriter dictionaries");

        // Retrieve FSA names and paths
        ListIterator<FsaDict> fsaList = config.fsaDict().listIterator();

        // Load default dictionaries if no user dictionaries is configured
        if(!fsaList.hasNext()) {
            RewriterUtils.log(logger, "Loading default dictionaries");
            HashMap<String, String> defaultFSAs = getDefaultFSAs();

            if(defaultFSAs==null) {
                RewriterUtils.log(logger, "No default FSA dictionary is configured");
                return true;
            }
            Iterator<Map.Entry<String, String>> defaultFSAList = defaultFSAs.entrySet().iterator();
            while(defaultFSAList.hasNext()) {
                try{
                    Map.Entry<String, String> currFSA = defaultFSAList.next();
                    String fsaName = currFSA.getKey();
                    String fsaPath = currFSA.getValue();

                    RewriterUtils.log(logger,
                                      "FSA file location for " + fsaName + ": " + fsaPath);

                    // Load FSA
                    FSA fsa = RewriterUtils.loadFSA(RewriterConstants.DEFAULT_DICT_DIR + fsaPath, null);

                    // Store FSA into dictionary map
                    rewriterDicts.put(fsaName, fsa);
                } catch (IOException e) {
                    RewriterUtils.error(logger, "Error loading FSA dictionary: " +
                                        e.getMessage());
                    return false;
                }
            }
        } else {
            // Load user configured dictionaries
            while(fsaList.hasNext()) {
                try{
                    FsaDict currFSA = fsaList.next();
                    // fsaName and fsaPath are not null
                    // or else vespa config server would not have been
                    // able to start up
                    String fsaName = currFSA.name();
                    FileReference fsaPath = currFSA.path();

                    RewriterUtils.log(logger,
                                      "FSA file location for " + fsaName + ": " + fsaPath);

                    // Retrieve FSA File handler
                    File fsaFile = null;
                    if(fileAcquirer!=null) {
                        fsaFile = fileAcquirer.waitFor(fsaPath, 5, TimeUnit.MINUTES);
                    } else if(fileList!=null) {
                        fsaFile = fileList.get(fsaName);
                    }

                    if(fsaFile==null) {
                        RewriterUtils.error(logger, "Error loading FSA dictionary file handler");
                        return false;
                    }

                    // Load FSA
                    FSA fsa = RewriterUtils.loadFSA(fsaFile, null);

                    // Store FSA into dictionary map
                    rewriterDicts.put(fsaName, fsa);
                } catch (InterruptedException e1) {
                    RewriterUtils.error(logger, "Error loading FSA dictionary file handler: " +
                                        e1.getMessage());
                    return false;
                } catch (IOException e2) {
                    RewriterUtils.error(logger, "Error loading FSA dictionary: " +
                                        e2.getMessage());
                    return false;
                }
            }
        }
        RewriterUtils.log(logger, "Successfully loaded rewriter dictionaries");
        return true;
    }

    /**
     * Perform instance creation time configuration besides the
     * default FSA loading
     *
     * @param fileAcquirer Required param for retrieving file type config
     *                     (see vespa's search container doc for more detail)
     * @param config Config from vespa-services.xml (see vespa's search
     *               container doc for more detail)
     * @param fileList pairs of file name and file handler for unit tests
     * @return boolean true if loaded successfully, false otherwise
     */
    public abstract boolean configure(FileAcquirer fileAcquirer,
                                      RewritesConfig config,
                                      HashMap<String, File> fileList)
                                      throws RuntimeException;

    /**
     * Perform main rewrite logics for this searcher<br>
     * - Skip to next rewriter if query is previously
     *   rewritten and getSkipRewriterIfRewritten() is
     *   true for this rewriter<br>
     * - Execute rewriter's main rewrite logic<br>
     * - Pass to the next rewriter the query to be used
     *   for dictionary retrieval<br>
     */
    @Override
    public Result search(Query query, Execution execution) {
        RewriterUtils.log(logger, query, "Executing " + getRewriterName());

        // Check if rewriter is properly initialized
        if(!isOk) {
            RewriterUtils.error(logger, query, "Rewriter is not properly initialized");
            return execution.search(query);
        }

        RewriterUtils.log(logger, query, "Original query: " + query.toDetailString());

        // Retrieve metadata passed by previous rewriter
        HashMap<String, Object> rewriteMeta = RewriterUtils.getRewriteMeta(query);

        // This key would be updated by each rewriter to specify
        // the key to be used for dict retrieval in next
        // rewriter downstream. This controls whether the
        // next rewriter should use the rewritten query or the
        // original query for dict retrieval. e.g. rewriters
        // following misspell rewriter should use the rewritten
        // query by misspell rewriter for dict retrieval
        String prevDictKey = (String)rewriteMeta.get(RewriterConstants.DICT_KEY);

        // Whether the query has been rewritten
        Boolean prevRewritten = (Boolean)rewriteMeta.get(RewriterConstants.REWRITTEN);

        // Check if rewriter should be skipped if the query
        // has been rewritten
        if(prevRewritten && getSkipRewriterIfRewritten()) {
            RewriterUtils.log(logger, query, "Skipping rewriter since the " +
                              "query has been rewritten");
            return execution.search(query);
        }

        // Store rewriter result
        HashMap<String, Object> rewriterResult = null;
        query.getModel().getQueryTree(); // performance: parse query before cloning such that it is only done once
        Query originalQueryObj = query.clone();

        try {
            // Execute rewriter's main rewrite logic
            rewriterResult = rewrite(query, prevDictKey);

        } catch (RuntimeException e) {
            RewriterUtils.error(logger, originalQueryObj, "Error executing this rewriter, " +
                                "skipping to next rewriter: " + e.getMessage());
            return execution.search(originalQueryObj);
        }

        // Check if rewriter result is set properly
        if(rewriterResult==null) {
            RewriterUtils.error(logger, originalQueryObj, "Rewriter result are not set properly, " +
                                "skipping to next rewriter");
            return execution.search(originalQueryObj);
        }

        // Retrieve results from rewriter
        Boolean rewritten = (Boolean)rewriterResult.get(RewriterConstants.REWRITTEN);
        String dictKey = (String)rewriterResult.get(RewriterConstants.DICT_KEY);

        if(rewritten==null || dictKey==null) {
            RewriterUtils.error(logger, originalQueryObj, "Rewriter result are not set properly, " +
                                "skipping to next rewriter");
            return execution.search(originalQueryObj);
        }

        // Retrieve results from rewriter
        rewriteMeta.put(RewriterConstants.REWRITTEN, (rewritten || prevRewritten));
        rewriteMeta.put(RewriterConstants.DICT_KEY, dictKey);

        // Pass metadata to the next rewriter
        RewriterUtils.setRewriteMeta(query, rewriteMeta);

        RewriterUtils.log(logger, query, "Final query: " + query.toDetailString());

        return execution.search(query);
    }

    /**
     * Perform the main rewrite logic
     *
     * @param query Query object from searcher
     * @param dictKey the key passed from previous rewriter
     *                to be treated as "original query from user"
     *                For example, if previous is misspell rewriter,
     *                it would pass the corrected query as the
     *                "original query from user". For other rewriters which
     *                add variants, abbr, etc to the query, the original
     *                query should be passed as a key. This rewriter could
     *                still choose to ignore this key. This key
     *                is not the rewritten query itself. For example,
     *                if original query is (willl smith) and the
     *                rewritten query is (willl smith) OR (will smith)
     *                the key to be passed could be (will smith)
     * @return HashMap which contains the key value pairs:<br>
     *         - whether this query has been rewritten by this
     *           rewriter<br>
     *           key: rewritten<br>
     *           value: true or false<br>
     *         - the key to be treated as "original query from user" in next
     *           rewriter downstream, for example, misspell rewriter
     *           would pass the corrected query as the "original query from
     *           user" to the next rewriter. For other rewriters which
     *           add variants, abbr, etc to the query, the original
     *           query should be passed as a key. This key is not necessarily
     *           consumed by the next rewriter. The next rewriter
     *           can still choose to ignore this key.<br>
     *           key: newDictKey<br>
     *           value: new dict key<br>
     */
    protected abstract HashMap<String, Object> rewrite(Query query,
                                                       String dictKey) throws RuntimeException;

    /**
     * Check whether rewriter should be skipped if
     * the query has been rewritten by other rewriter
     *
     * @return boolean Whether rewriter should be skipped
     */
    protected abstract boolean getSkipRewriterIfRewritten();

    /**
     * Retrieve rewriter name
     * It should match the name used in query profile
     *
     * @return Name of the rewriter
     */
    public abstract String getRewriterName();

   /**
    * Get default FSA dictionary names
    *
    * @return Pair of FSA dictionary name and filename
    */
   public abstract HashMap<String, String> getDefaultFSAs();

    /**
     * Get config parameter value set in query profile
     *
     * @param query Query object from the searcher
     * @param paramName parameter to be retrieved
     * @return parameter value or null if not found
     */
    protected String getQPConfig(Query query,
                                 String paramName) {
       return RewriterUtils.getQPConfig(query, getRewriterName(), paramName);
    }

    /**
     * Retrieve rewrite from FSA given the original query
     *
     * @param query Query object from searcher
     * @param dictName FSA dictionary name
     * @param key The original query used to retrieve rewrite
     *            from the dictionary
     * @return String The retrieved rewrites, null if query
     *         doesn't exist
     */
    protected String getRewriteFromFSA(Query query,
                                       String dictName,
                                       String key) throws RuntimeException {
        return RewriterUtils.getRewriteFromFSA(query, rewriterDicts, dictName, key);
    }
}
