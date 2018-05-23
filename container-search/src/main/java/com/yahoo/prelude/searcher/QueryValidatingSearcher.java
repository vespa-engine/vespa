// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * Ensures hits is 1000 or less and offset is 1000 or less.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class QueryValidatingSearcher extends Searcher {

    public Result search(Query query, Execution execution) {
        if (query.getHits() > 1000) {
            Result result = new Result(query);
            ErrorMessage error
                = ErrorMessage.createInvalidQueryParameter("Too many hits (more than 1000) requested.");
            result.hits().addError(error);
            return result;
        }
        if (query.getOffset() > 1000) {
            Result result = new Result(query);
            ErrorMessage error
                = ErrorMessage.createInvalidQueryParameter("Offset too high (above 1000).");
            result.hits().addError(error);
            return result;
        }
        return execution.search(query);
    }

}
