package ${package};

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.chain.After;

/**
 * Searcher that adds some simple output to the trace of the query.
 */
@After("MinimalQueryInserter")
public class Searcher extends com.yahoo.search.Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        query.trace("This is from the sample searcher", 20);
        return execution.search(query);
    }

}
