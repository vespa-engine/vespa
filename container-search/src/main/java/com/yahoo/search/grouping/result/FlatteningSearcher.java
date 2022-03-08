// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;

import java.util.Iterator;

/**
 * Flattens a grouping result into a flat list of hits on the top level in the returned result.
 * Useful when using grouping to create hits with diversity and similar.
 *
 * @author bratseth
 */
@Before(GroupingExecutor.COMPONENT_NAME)
public class FlatteningSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        if ( ! query.properties().getBoolean("flatten", true)) return execution.search(query);

        query.trace("Flattening groups", 2);
        int originalHits = query.getHits();
        query.setHits(0);
        Result result = execution.search(query);
        query.setHits(originalHits);
        flatten(result.hits(), result);
        return result;
    }

    public void flatten(HitGroup hits, Result result) {
        int hitsLeft = hits.size(); // Iterate only through the initial size
        for (Iterator<Hit> i = hits.iterator(); i.hasNext() && hitsLeft-- > 0;) {
            Hit hit = i.next();
            if (hit instanceof HitGroup) {
                flatten((HitGroup)hit, result);
                i.remove();
            } else {
                result.hits().add(hit);
            }
        }
    }

}
