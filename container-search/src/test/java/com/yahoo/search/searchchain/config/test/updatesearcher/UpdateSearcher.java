// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.config.test.updatesearcher;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * @author bratseth
 */
public class UpdateSearcher extends com.yahoo.search.Searcher {

    public String test = "update";

    @Override
    public Result search(Query query,Execution execution) {
        Result result=execution.search(query);
        if (result==null)
            result=new Result(query);
        result.hits().add(new Hit("from:updatesearcher:0"));
        return result;
    }
}
