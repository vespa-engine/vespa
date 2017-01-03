package com.yahoo.example;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * A searcher adding a new hit.
 *
 * @author Joe Developer
 */
public class ExampleSearcher extends Searcher {

    public static final String hitId = "ExampleHit";
    private final String message;

    public ExampleSearcher(MessageConfig config) {
        message = config.message();
    }

    public Result search(Query query, Execution execution) {
        // Pass on to the next searcher to execute the query
        Result result = execution.search(query);

        // Add an extra hit to the result
        Hit hit = new Hit(hitId);
        hit.setField("message", message);
        result.hits().add(hit);

        // Return it to the upstream searcher, or to rendering
        return result;
    }

}
