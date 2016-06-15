// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.config.test.searcher1;

import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * @author bratseth
 */
public class Searcher1 extends Searcher {

    public @Override
    Result search(Query query,Execution execution) {
        Result result=execution.search(query);
        ErrorMessage.createErrorInPluginSearcher("nop"); // Check that we may access legacy packages
        if (result==null)
            result=new Result(query);
        result.hits().add(new Hit("from:searcher1:0"));
        return result;
    }
}
