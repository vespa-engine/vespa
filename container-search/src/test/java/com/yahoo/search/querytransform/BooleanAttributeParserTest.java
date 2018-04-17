// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.PredicateQueryItem;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author magnarn
 */
public class BooleanAttributeParserTest {

    @Test
    public void requireThatParseHandlesAllFormats() {
        assertParse(null, 0);
        assertParse("{}", 0);
        assertParse("{foo:bar}", 1);
        assertParse("{foo:[bar]}", 1);
        assertParse("{foo:bar, baz:qux}", 2);

        assertParse("{foo:bar, foo:baz}", 2);
        assertParse("{foo:[bar, baz, qux]}", 3);
        assertParse("{foo:[bar, baz, qux], quux:corge}", 4);
        assertParse("{foo:[bar, baz, qux], quux:[corge, grault]}", 5);
        assertParse("{foo:bar, foo:bar, foo:bar}", 3);

        assertParse("{foo:bar:0x1, foo:baz:0xf}", 2);
        assertParse("{foo:[bar:0xbabe, baz:0xbeef, qux:0xfee1], quux:corge:0x1234}", 4);
        assertParse("{foo:bar:[1], foo:baz:[0,1,2,3]}", 2);
        assertParse("{foo:bar:[ 1 ], foo:baz:[ 0 , 1 , 2 , 3 ]}", 2);
        assertParse("{foo:[bar:[4,7],baz:[8,5],qux:[3,2]], quux:corge:[2, 5, 7, 58]}", 4);
    }

    @Test
    public void requireThatIllegalStringsFail() {
        assertException("{foo:[bar:[baz]}");
        assertException("{foo:[bar:baz}");
        assertException("{foo:bar:[0,1,2}");
        assertException("{foo:[bar:[0,1,2],baz:[0,,2]]}");
        assertException("{foo:[bar:[0,1,2],baz:[0,1,2]}");
        assertException("{foo:bar:[64]}");
        assertException("{foo:bar:[-1]}");
        assertException("{foo:bar:[a]}");
        assertException("{foo:bar:[0,1,[2]]}");
        assertException("{foo:bar}extrachars");
    }

    private void assertException(String s) {
        try {
            PredicateQueryItem item = new PredicateQueryItem();
            new BooleanSearcher.PredicateValueAttributeParser(item).parse(s);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void requireThatTermsCanHaveBitmaps() {
        PredicateQueryItem q = assertParse("{foo:bar:0x1}", 1);
        PredicateQueryItem.Entry[] features = new PredicateQueryItem.Entry[q.getFeatures().size()];
        q.getFeatures().toArray(features);
        assertEquals(1l, q.getFeatures().iterator().next().getSubQueryBitmap());
        q = assertParse("{foo:bar:0x1, baz:qux:0xf}", 2);
        Iterator<PredicateQueryItem.Entry> it = q.getFeatures().iterator();
        assertEquals(1l, it.next().getSubQueryBitmap());
        assertEquals(15l, it.next().getSubQueryBitmap());
        q = assertParse("{foo:bar:0xffffffffffffffff}", 1);
        assertEquals(-1l, q.getFeatures().iterator().next().getSubQueryBitmap());
        q = assertParse("{foo:bar:[63]}", 1);

        assertEquals(new BigInteger("ffffffffffffffff", 16).shiftRight(1).add(BigInteger.ONE).longValue(), q.getFeatures().iterator().next().getSubQueryBitmap());
        q = assertParse("{foo:bar:0x7fffffffffffffff}", 1);
        assertEquals(new BigInteger("ffffffffffffffff", 16).shiftRight(1).longValue(), q.getFeatures().iterator().next().getSubQueryBitmap());
        q = assertParse("{foo:bar:[0]}", 1);
        assertEquals(1l, q.getFeatures().iterator().next().getSubQueryBitmap());
        q = assertParse("{foo:bar:[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]}", 1);
        assertEquals(1l, q.getFeatures().iterator().next().getSubQueryBitmap());
        q = assertParse("{foo:bar:[0,2,6,8]}", 1);
        assertEquals(0x145l, q.getFeatures().iterator().next().getSubQueryBitmap());
        q = assertParse("{foo:[bar:[0,8,6,2],baz:[1,3,4,15]]}", 2);
        it = q.getFeatures().iterator();
        assertEquals(0x145l, it.next().getSubQueryBitmap());
        assertEquals(0x801al, it.next().getSubQueryBitmap());
    }

    private PredicateQueryItem assertParse(String s, int numFeatures) {
        PredicateQueryItem item = new PredicateQueryItem();
        BooleanAttributeParser parser = new BooleanSearcher.PredicateValueAttributeParser(item);
        parser.parse(s);
        assertEquals(numFeatures, item.getFeatures().size());
        return item;
    }

}
