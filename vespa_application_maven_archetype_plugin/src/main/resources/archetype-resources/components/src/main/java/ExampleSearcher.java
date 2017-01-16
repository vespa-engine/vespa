// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ${package};

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.example.ExampleConfig;

/**
 * A searcher adding a new hit.
 *
 * @author  Joe Developer
 */
public class ExampleSearcher extends Searcher {
    private final String message;

    public ExampleSearcher(ExampleConfig config) {
        message = config.message();
    }

    public Result search(Query query,Execution execution) {
        Hit hit = new Hit("ExampleHit");
        hit.setField("message", message);

        Result result = execution.search(query);
        result.hits().add(hit);
        return result;
    }
}
