// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.documentapi.messagebus.protocol.SearchColumnPolicy;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vdslib.BucketDistribution;
import com.yahoo.component.chain.dependencies.Before;

import java.util.Iterator;
import java.util.logging.Logger;


/**
 * Searcher that does efficient key/value lookup using Vespa search as a
 * backend. It does so by bypassing the first phase ranking, and only performs
 * the second phase summary fetching.
 *
 * The keys to find are input as a comma-seprated list using the <i>keys</i>
 * query parameter. Each key should match a part of a document id. Given the key
 * 'foo', and document id namespace 'mynamespace', the document id matched will
 * be 'id:mynamespace:keyvalue::foo'.
 *
 * To scale the throughput with the number of partitions, the searcher uses the
 * same hashing mechanisms as the document API to find out which node each key
 * belongs to. The searcher then dispatches a summary request to retrieve keys
 * and returns the result.
 *
 * @author <a href="lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
@Before(GroupingExecutor.COMPONENT_NAME)
public class KeyValueSearcher extends Searcher {

    private static final Logger log = Logger.getLogger(KeyValueSearcher.class.getName());
    private final BucketIdFactory factory = new BucketIdFactory();
    private final BucketDistribution distribution;
    private final String summaryClass;
    private final String idSchemePrefix;
    private final int numRowBits;
    private final int traceLevel = 5;

    public KeyValueSearcher(KeyvalueConfig config) {
        this.summaryClass = config.summaryName();
        this.idSchemePrefix = createIdSchemePrefix(config);
        this.distribution = new BucketDistribution(config.numparts(), SearchColumnPolicy.DEFAULT_NUM_BUCKET_BITS);
        this.numRowBits = calcNumRowBits(config.numrows());
        log.config("Configuring " + KeyValueSearcher.class.getName() + " with " + config.numparts() + " partitions and doc id scheme '" + idSchemePrefix + "'");
    }

    private String createIdSchemePrefix(KeyvalueConfig config) {
        if (config.docIdScheme().equals(KeyvalueConfig.DocIdScheme.Enum.DOC_SCHEME)) {
            return "doc:" + config.docIdNameSpace() + ":";
        } else {
            return "id:" + config.docIdNameSpace() + ":" + config.docIdType() + "::";
        }
    }

    public Hit createHit(Query query, String key) {
        String docId = createDocId(key.trim());
        BucketId id = factory.getBucketId(new DocumentId(docId));
        int partition = getPartition(id);

        FastHit hit = new FastHit();
        hit.setGlobalId(new GlobalId(IdString.createIdString(docId)));
        hit.setQuery(query);
        hit.setFillable();
        hit.setCached(false);
        hit.setPartId(partition << numRowBits, numRowBits);
        hit.setRelevance(1.0);
        hit.setIgnoreRowBits(true);
        hit.setDistributionKey(42);
        return hit;
    }

    private String createDocId(String key) {
        return idSchemePrefix + key;
    }


    @Override
    public Result search(Query query, Execution execution) {
        String keyProp = query.properties().getString("keys");
        query.getPresentation().setSummary(summaryClass);
        if (keyProp == null || keyProp.length() == 0) {
            return new Result(query, new ErrorMessage(ErrorMessage.NULL_QUERY, "'keys' parameter not set or empty."));
        }
        String[] keyList = keyProp.split(",");
        Model model = query.getModel();
        QueryTree tree = model.getQueryTree();
        QueryCanonicalizer.canonicalize(tree);
        if (tree.isEmpty()) {
        	tree.setRoot(new IntItem(String.valueOf(keyProp.hashCode())));
        }

        Result result = new Result(query);
        for (String key : keyList) {
            result.hits().add(createHit(query, key));
        }
        execution.fill(result, summaryClass);
        if (query.isTraceable(traceLevel)) {
            traceResult(query, result);
        }
        int totalHits = 0;
        Iterator<Hit> hitIterator = result.hits().iterator();
        while (hitIterator.hasNext()) {
            Hit hit = hitIterator.next();
            if (hit.isFillable() && hit.isFilled(summaryClass)) {
                totalHits++;
            } else {
                hitIterator.remove();
            }
        }
        if (totalHits != keyList.length) {
            ErrorMessage error = new ErrorMessage(1, "Some keys could not be fetched");
            result.hits().setError(error);
        }
        result.setTotalHitCount(totalHits);
        return result;
    }

    private void traceResult(Query query, Result result) {
        Iterator<Hit> hitIterator = result.hits().iterator();
        while (hitIterator.hasNext()) {
            Hit hit = hitIterator.next();
            if (hit.isFillable() && hit.isFilled(summaryClass)) {
                query.trace("Found filled hit: " + hit, traceLevel);
            } else {
                query.trace("Found hit that was not filled/fillable: " + hit, traceLevel);
            }
        }
        query.trace("Error hit: " + result.hits().getErrorHit(), traceLevel);
    }

    private int getPartition(BucketId bucketId) {
        return distribution.getColumn(bucketId);
    }

    private static int calcNumRowBits(int numRows) {
        if (numRows < 1) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < 30; ++i) {
            if (numRows - 1 < 1 << i) {
                return i;
            }
        }
        return 31;
    }
}
