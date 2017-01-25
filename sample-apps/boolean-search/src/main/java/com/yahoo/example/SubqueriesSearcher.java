package com.yahoo.example;

import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Inspector;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * A searcher that reads "subqueries" information for field "target",
 * and creates a new field "subqueries" which holds a 64-bit subquery bitmap.
 *
 * @author  Joe Developer
 */
public class SubqueriesSearcher extends Searcher {
    public SubqueriesSearcher() {
    }

    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        execution.fill(result);
        for (Hit hit : result.hits().asList()) {
            if (hit.isMeta()) continue;
            simplifySubqueriesFor("target", hit);
            hit.removeField("summaryfeatures");
        }
        return result;
    }

    /**
     * Reads summaryfeatures for subqueries(field) and adds a
     * new field "subqueries(field)" with a 64-bit subquery bitmap
     * @param field Field to read subqueries for
     * @param hit Hit to read summaryfeatures from and update with the new field
     */
    private void simplifySubqueriesFor(String field, Hit hit) {
        Object o = hit.getField("summaryfeatures");
        if (o instanceof Inspectable) {
            String subqueriesName = "subqueries(" + field + ")";

            Inspectable summaryfeatures = (Inspectable) o;
            Inspector obj = summaryfeatures.inspect();
            long lsb = obj.field(subqueriesName).asLong(0);  // The .lsb suffix is optional, so read both with and without.
            lsb |= obj.field(subqueriesName + ".lsb").asLong(0);
            long msb = obj.field(subqueriesName + ".msb").asLong(0);

            hit.setField(subqueriesName, msb << 32 | lsb);
        }
    }
}
