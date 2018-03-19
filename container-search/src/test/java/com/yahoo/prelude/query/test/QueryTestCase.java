// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests query trees
 *
 * @author bratseth
 */
public class QueryTestCase {

    /** Tests that query hash and equality is value dependent only */
    @Test
    public void testQueryEquality() {
        String query = "RANK (+(AND \"baz gaz faz\" bazar) -\"foo bar foobar\") foofoo xyzzy";
        String filter = "foofoo -\"foo bar foobar\" xyzzy +\"baz gaz faz\" +bazar";

        Item root1 = parseQuery(query, filter, Query.Type.ANY);
        Item root2 = parseQuery(query, filter, Query.Type.ANY);

        assertEquals(root1.hashCode(), root2.hashCode());
        assertEquals(root1, root2);
    }

    /** Check copy of query trees is a deep copy */
    @Test
    public void testDeepCopy() {
        Item root1 = parseQuery("a and b and (c or d) and e rank f andnot g", null, Query.Type.ADVANCED);
        Item root2 = root1.clone();

        assertTrue("Item.clone() should be a deep copy.",nonIdenticalTrees(root1, root2));
    }

    private static Item parseQuery(String query, String filter, Query.Type type) {
        Parser parser = ParserFactory.newInstance(type, new ParserEnvironment());
        return parser.parse(new Parsable().setQuery(query).setFilter(filter));
    }

    // Control two equal trees does not have a "is" relationship for
    // any element
    private boolean nonIdenticalTrees(Item root1, Item root2) {
        if (root1 instanceof CompositeItem) {
            boolean nonID = root1 != root2;
            Iterator<?> i1 = ((CompositeItem) root1).getItemIterator();
            Iterator<?> i2 = ((CompositeItem) root2).getItemIterator();

            while (i1.hasNext() && nonID) {
                nonID &= nonIdenticalTrees((Item) i1.next(), (Item) i2.next());
            }
            return nonID;

        } else {
            return root1 != root2;
        }
    }

}
