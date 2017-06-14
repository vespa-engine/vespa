// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.StructuredData;
import com.yahoo.search.searchchain.Execution;

import java.util.Iterator;

/**
 * Save the query in the incoming state to a meta hit in the result.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */

public class JSONDebugSearcher extends Searcher {
    public static final String JSON_FIELD = "JSON field: ";
    public static final String STRUCT_FIELD = "Structured data field (as json): ";
    public static final String FEATURE_FIELD = "Feature data field (as json): ";

    private static CompoundName PROPERTYNAME = new CompoundName("dumpjson");

    public Result search(com.yahoo.search.Query query, Execution execution) {
        Result r = execution.search(query);
        String propertyName = query.properties().getString(PROPERTYNAME);
        if (propertyName != null) {
            execution.fill(r);
            for (Iterator<Hit> i = r.hits().deepIterator(); i.hasNext();) {
                Hit h = i.next();
                if (h instanceof FastHit) {
                    FastHit hit = (FastHit) h;
                    Object o = hit.getField(propertyName);
                    if (o instanceof JSONString) {
                        JSONString j = (JSONString) o;
                        r.getQuery().trace(JSON_FIELD + j.getContent(), false, 5);
                    }
                    if (o instanceof StructuredData) {
                        StructuredData d = (StructuredData) o;
                        r.getQuery().trace(STRUCT_FIELD + d.toJson(), false, 5);
                    }
                    if (o instanceof FeatureData) {
                        FeatureData d = (FeatureData) o;
                        r.getQuery().trace(FEATURE_FIELD + d.toJson(), false, 5);
                    }
                }
            }
        }
        return r;
    }
}
