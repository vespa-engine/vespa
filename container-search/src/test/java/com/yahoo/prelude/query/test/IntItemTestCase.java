// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class IntItemTestCase {

    @Test
    void testEquals() {
        Query q1 = new Query("/?query=123%20456%20789");
        Query q2 = new Query("/?query=123%20456");

        WeakAndItem andItem = (WeakAndItem) q2.getModel().getQueryTree().getRoot();
        var item = new IntItem(789L, "");
        item.setFromQuery(true);
        andItem.addItem(item);

        assertEquals(q1, q2);
    }

}
