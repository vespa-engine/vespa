package com.yahoo.example;

import com.yahoo.data.access.simple.Value;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * @author  Joe Developer
*/
public class MockBackend extends Searcher {

    public MockBackend() {
    }

    public @Override
    Result search(Query query,Execution execution) {
        Result result=new Result(query);
        for (int i = 0; i < 3; ++i) {
            Hit hit = new Hit("mock-hit:" + i);
            Value.ObjectValue summaryfeatures = new Value.ObjectValue();
            summaryfeatures.put("subqueries(target).lsb", new Value.LongValue(0x3));
            summaryfeatures.put("subqueries(target).msb", new Value.LongValue(0x1));
            hit.setField("summaryfeatures", summaryfeatures);
            result.hits().add(hit);
        }
        return result;
    }
}
