// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import com.yahoo.component.ComponentId;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * Only returns the first hit for a query.
 *
 * @author <a href="mailto:Steinar.Knutsen@europe.yahoo-inc.com">Steinar Knutsen</a>
 */
public class DummySearcher extends Searcher {

    public DummySearcher() {
    }

    public DummySearcher(ComponentId id) {
        super(id);
    }

    public Result search(com.yahoo.search.Query query, Execution execution) {
        Result result=new Result(query);
        result.hits().add(new Hit("http://a.com/b", 100));

        return result;
    }
}
