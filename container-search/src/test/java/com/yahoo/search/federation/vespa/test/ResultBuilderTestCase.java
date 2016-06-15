// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa.test;

import java.util.Iterator;

import junit.framework.TestCase;

import com.yahoo.net.URI;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.federation.vespa.ResultBuilder;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.HitGroup;

/**
 * Test XML parsing of results.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@SuppressWarnings("deprecation")
public class ResultBuilderTestCase extends TestCase {

    public ResultBuilderTestCase (String name) {
        super(name);
    }

    private boolean quickCompare(double a, double b) {
        double z = Math.min(Math.abs(a), Math.abs(b));
        if (Math.abs((a - b)) < (z / 1e14)) {
            return true;
        } else {
            return false;
        }
    }

    public void testSimpleResult() {
        boolean gotErrorDetails = false;
        ResultBuilder r = new ResultBuilder();
        Result res = r.parse("file:src/test/java/com/yahoo/prelude/searcher/test/testhit.xml", new Query("?query=a"));
        assertEquals(3, res.getConcreteHitCount());
        assertEquals(4, res.getHitCount());
        ErrorHit e = (ErrorHit) res.hits().get(0);
        // known problem, if the same error is the main error is
        // in details, it'll be added twice. Not sure how to fix that,
        // because old Vespa systems give no error details, and there
        // is no way of nuking an existing error if the details exist.
        for (Iterator<?> i = e.errorIterator(); i.hasNext();) {
            ErrorMessage err = (ErrorMessage) i.next();
            assertEquals(5, err.getCode());
            String details = err.getDetailedMessage();
            if (details != null) {
                gotErrorDetails = true;
                assertEquals("An error as ordered", details.trim());
            }
        }
        assertTrue("Error details are missing", gotErrorDetails);
        assertEquals(new URI("http://def"), res.hits().get(1).getId());
        assertEquals("test/stuff\\tsome/other", res.hits().get(2).getField("category"));
        assertEquals("<field>habla</field>"
                + "<hi>blbl</hi><br />&lt;&gt;&amp;fdlkkgj&lt;/field&gt;;lk<a b=\"1\" c=\"2\" />"
                + "<x><y><z /></y></x>", res.hits().get(3).getField("annoying").toString());
    }

    public void testNestedResult() {
        ResultBuilder r = new ResultBuilder();
        Result res = r.parse("file:src/test/java/com/yahoo/search/federation/vespa/test/nestedhits.xml", new Query("?query=a"));
        assertNull(res.hits().getError());
        assertEquals(3, res.hits().size());
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", res.hits().get(0).getField("guid").toString());
        HitGroup g1 = (HitGroup) res.hits().get(1);
        HitGroup g2 = (HitGroup) res.hits().get(2);
        assertEquals(15, g1.size());
        assertEquals("reward_for_thumb", g1.get(1).getField("id").toString());
        assertEquals(10, g2.size());
        HitGroup g3 = (HitGroup) g2.get(3);
        assertEquals("badge", g3.getTypeString());
        assertEquals(2, g3.size());
        assertEquals("badge/Topic Explorer 5", g3.get(0).getField("name").toString());
    }

    public void testWeirdDocumentID() {
        ResultBuilder r = new ResultBuilder();
        Result res = r.parse("file:src/test/java/com/yahoo/search/federation/vespa/test/idhits.xml", new Query("?query=a"));
        assertNull(res.hits().getError());
        assertEquals(3, res.hits().size());
        assertEquals(new URI("nalle"), res.hits().get(0).getId());
        assertEquals(new URI("tralle"), res.hits().get(1).getId());
        assertEquals(new URI("kalle"), res.hits().get(2).getId());
    }
}
