package com.yahoo.search.federation.sourceref;

import com.yahoo.search.Query;
import com.yahoo.search.Result;

public interface ModifyQueryAndResult {
    void modifyTargetQuery(Query query);
    void modifyTargetResult(Result result);
}
