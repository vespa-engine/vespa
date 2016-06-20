package com.mydomain.example;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * A searcher adding a new hit.
 *
 * @author  Joe Developer
 */
public class ExampleSearcher extends Searcher {
    public static final  String hitId = "ExampleHit";
    private final String message;

    public ExampleSearcher(MessageConfig config) {
        message = config.message();
    }

    public Result search(Query query, Execution execution) {
        Hit hit = new Hit(hitId);
        hit.setField("message", message);

        Result result = execution.search(query);
        result.hits().add(hit);
        return result;
    }
}
