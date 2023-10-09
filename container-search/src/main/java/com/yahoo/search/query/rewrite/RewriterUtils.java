// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite;

import com.yahoo.fsa.FSA;
import java.util.logging.Level;
import com.yahoo.search.Query;
import com.yahoo.search.intent.model.IntentModel;
import com.yahoo.search.intent.model.InterpretationNode;
import com.yahoo.text.interpretation.Annotations;
import com.yahoo.text.interpretation.Modification;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * Contains common utilities used by rewriters
 *
 * @author Karen Sze Wing Lee
 */
public class RewriterUtils {

    private static final Logger utilsLogger = Logger.getLogger(RewriterUtils.class.getName());

    // Tracelevel for debug log of this rewriter
    private static final int TRACELEVEL = 3;

    /**
     * Load FSA from file
     *
     * @param file FSA dictionary file object
     * @param query Query object from the searcher, could be null if not available
     * @return FSA The FSA object for the input file path
     */
    public static FSA loadFSA(File file, Query query) throws IOException {
        log(utilsLogger, query, "Loading FSA file");
        String filePath = null;

        try {
            filePath = file.getAbsolutePath();
        } catch (SecurityException e1) {
            error(utilsLogger, query, "No read access for the FSA file");
            throw new IOException("No read access for the FSA file");
        }

        FSA fsa = loadFSA(filePath, query);

        return fsa;
    }

    /**
     * Load FSA from file
     *
     * @param filename FSA dictionary file path
     * @param query Query object from the searcher, could be null if not available
     * @return FSA The FSA object for the input file path
     */
    public static FSA loadFSA(String filename, Query query) throws IOException {
        log(utilsLogger, query, "Loading FSA file from: " + filename);

        if(!new File(filename).exists()) {
            error(utilsLogger, query, "File does not exist : " + filename);
            throw new IOException("File does not exist : " + filename);
        }

        FSA fsa;
        try {
            fsa = new FSA(filename);
        } catch (RuntimeException e) {
            error(utilsLogger, query, "Invalid FSA file");
            throw new IOException("Invalid FSA file");
        }

        if (!fsa.isOk()) {
            error(utilsLogger, query, "Unable to load FSA file from : " + filename);
            throw new IOException("Not able to load FSA file from : " + filename);
        }
        log(utilsLogger, query, "Loaded FSA successfully from file : " + filename);
        return fsa;
    }

    /**
     * Retrieve rewrite from FSA given the original query
     *
     * @param query Query object from searcher
     * @param dictName FSA dictionary name
     * @param rewriterDicts list of rewriter dictionaries
     *                      It has the following format:
     *                      HashMap&lt;dictionary name, FSA&gt;
     * @param key The original query used to retrieve rewrite
     *            from the dictionary
     * @return String The retrieved rewrites, null if query
     *         doesn't exist
     */
    public static String getRewriteFromFSA(Query query,
                                           HashMap<String, Object> rewriterDicts,
                                           String dictName,
                                           String key) throws RuntimeException {
        if(rewriterDicts==null) {
            error(utilsLogger, query, "HashMap containing rewriter dicts is null");
            throw new RuntimeException("HashMap containing rewriter dicts is null");
        }

        FSA fsa = (FSA)rewriterDicts.get(dictName);

        if(fsa==null) {
            error(utilsLogger, query, "Error retrieving FSA dictionary: " + dictName);
            throw new RuntimeException("Error retrieving FSA dictionary: " + dictName);
        }

        String result = null;
        result = fsa.lookup(key);
        log(utilsLogger, query, "Retrieved rewrite: " + result);

        return result;
    }

    /**
     * Get config parameter value set in query profile
     *
     * @param query Query object from the searcher
     * @param rewriterName Name of the rewriter
     * @param paramName parameter to be retrieved
     * @return parameter value or null if not found
     */
    public static String getQPConfig(Query query,
                                     String rewriterName,
                                     String paramName) {
        log(utilsLogger, query, "Retrieving config parameter value of: " +
            rewriterName + "." + paramName);

        return getUserParam(query, rewriterName + "." + paramName);
    }

    /**
     * Get rewriter chain value
     *
     * @param query Query object from the searcher
     * @return parameter value or null if not found
     */
    public static String getRewriterChain(Query query) {
        log(utilsLogger, query, "Retrieving rewriter chain value: " +
            RewriterConstants.REWRITER_CHAIN);

        return getUserParam(query, RewriterConstants.REWRITER_CHAIN);
    }

    /**
     * Get user param value
     *
     * @param query Query object from the searcher
     * @param paramName parameter to be retrieved
     * @return parameter value or null if not found
     */
    public static String getUserParam(Query query, String paramName) {
        log(utilsLogger, query, "Retrieving user param value: " + paramName);

        if (paramName == null) {
            error(utilsLogger, query, "Parameter name is null");
            return null;
        }

        String paramValue = null;
        paramValue = query.properties().getString(paramName);
        log(utilsLogger, query, "Param value retrieved is: " + paramValue);

        return paramValue;
    }

    /**
     * Retrieve metadata passed by previous rewriter from query properties
     * Initialize values if this is the first rewriter
     *
     * @param query Query object from the searcher
     * @return hashmap containing the metadata
     */
    public static HashMap<String, Object> getRewriteMeta(Query query) {
       log(utilsLogger, query, "Retrieving metadata passed by previous rewriter");

        @SuppressWarnings("unchecked")
        HashMap<String, Object> rewriteMeta =
                (HashMap<String, Object>)query.properties().get(RewriterConstants.REWRITE_META);

       if (rewriteMeta == null) {
           log(utilsLogger, query, "No metadata available from previous rewriter");
           rewriteMeta = new HashMap<>();
           rewriteMeta.put(RewriterConstants.REWRITTEN, false);
           rewriteMeta.put(RewriterConstants.DICT_KEY, getNormalizedOriginalQuery(query));
       } else {
           if((Boolean)rewriteMeta.get(RewriterConstants.REWRITTEN)) {
               log(utilsLogger, query, "Query has been rewritten by previous rewriters");
           } else {
               log(utilsLogger, query, "Query has not been rewritten by previous rewriters");
           }
           log(utilsLogger, query, "Dict key passed by previous rewriter: " +
                                   rewriteMeta.get(RewriterConstants.DICT_KEY));
       }

       return rewriteMeta;
    }

    /**
     * Pass metadata to the next rewriter through query properties
     *
     * @param query Query object from the searcher
     * @param metadata HashMap containing the metadata
     */
    public static void setRewriteMeta(Query query, HashMap<String, Object> metadata) {
        log(utilsLogger, query, "Passing metadata to the next rewriter");

        query.properties().set(RewriterConstants.REWRITE_META, metadata);
        log(utilsLogger, query, "Successfully passed metadata to the next rewriter");
    }


    /**
     * Retrieve spell corrected query with highest score from QLAS
     *
     * @param query Query object from the searcher
     * @param qss_rw Whether to consider qss_rw modification
     * @param qss_sugg Whether ot consider qss_sugg modification
     * @return Spell corrected query or null if not found
     */
    public static String getSpellCorrected(Query query,
                                           boolean qss_rw,
                                           boolean qss_sugg)
                                           throws RuntimeException {
        log(utilsLogger, query, "Retrieving spell corrected query");

        // Retrieve Intent Model
        IntentModel intentModel = IntentModel.getFrom(query);
        if(intentModel==null) {
            error(utilsLogger, query, "Unable to retrieve intent model");
            throw new RuntimeException("Not able to retrieve intent model");
        }

        double max_score = 0;
        String spellCorrected = null;

        // Iterate through all interpretations to get a spell corrected
        // query with highest score
        for (InterpretationNode interpretationNode : intentModel.children()) {
            Modification modification = interpretationNode.getInterpretation()
                                                          .getModification();
            Annotations annotations = modification.getAnnotation();
            Double score = annotations.getDouble("score");

            // Check if it's higher than the max score
            if(score!=null && score>max_score) {
                Boolean isQSSRewrite = annotations.getBoolean("qss_rw");
                Boolean isQSSSuggest = annotations.getBoolean("qss_sugg");

                // Check if it's qss_rw or qss_sugg
                if((qss_rw && isQSSRewrite!=null && isQSSRewrite) ||
                   (qss_sugg && isQSSSuggest!=null && isQSSSuggest)) {
                    max_score = score;
                    spellCorrected = modification.getText();
                }
            }
        }

        if(spellCorrected!=null) {
            log(utilsLogger, query, "Successfully retrieved spell corrected query: " +
                spellCorrected);
        } else {
            log(utilsLogger, query, "No spell corrected query is retrieved");
        }

        return spellCorrected;
    }

    /**
     * Retrieve normalized original query from query object
     *
     * @param query Query object from searcher
     * @return normalized query
     */
    public static String getNormalizedOriginalQuery(Query query) {
        return toLowerCase(query.getModel().getQueryString()).trim();
    }

    /**
     * Log message
     *
     * @param logger Logger used for this msg
     * @param msg Log message
     */
    public static void log(Logger logger, String msg) {
        logger.log(Level.FINE, () -> logger.getName() + ": " + msg);
    }

    /**
     * Log message
     *
     * @param logger Logger used for this msg
     * @param query Query object from searcher
     * @param msg Log message
     */
    public static void log(Logger logger, Query query, String msg) {
        if(query!=null) {
            query.trace(logger.getName() + ": " + msg, true, TRACELEVEL);
        }
        logger.log(Level.FINE, () -> logger.getName() + ": " + msg);
    }

    /**
     * Print error message
     *
     * @param logger Logger used for this msg
     * @param msg Error message
     */
    public static void error(Logger logger, String msg) {
        logger.severe(logger.getName() + ": " + msg);
    }

    /**
     * Print error message
     *
     * @param logger Logger used for this msg
     * @param query Query object from searcher
     * @param msg Error message
     */
    public static void error(Logger logger, Query query, String msg) {
        if (query != null)
            query.trace(logger.getName() + ": " + msg, true, TRACELEVEL);
        logger.severe(logger.getName() + ": " + msg);
    }

}
