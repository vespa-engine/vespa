// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryType;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class ItemsTest {

    @Test
    public void testTextItem() {
        var chain = new SearchChain(ComponentId.fromString("myChain"));
        var query = new Query();
        var execution = new Execution(chain, Execution.Context.createContextStub());
        query.getModel().setType(QueryType.from(Query.Type.LINGUISTICS).setComposite(QueryType.Composite.and));
        var item = Items.text("myField", "my text", query, execution);
        assertEquals("AND myField:my myField:text", item.toString());
    }

}
