// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;
import com.yahoo.search.Query;


/**
 * Unit test for the helper methods placed in
 * com.yahoo.prelude.query.ItemHelper.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ItemHelperTestCase {

    @Test
    final void testGetNumTerms() {
        ItemHelper helper = new ItemHelper();
        Query q = new Query("/?query=" + enc("a b c"));
        assertEquals(3, helper.getNumTerms(q.getModel().getQueryTree().getRoot()));
    }

    @Test
    final void testGetPositiveTerms() {
        ItemHelper helper = new ItemHelper();
        Query q = new Query("/?query=" + enc("a b c \"d e\" -f"));
        List<IndexedItem> l = new ArrayList<>();
        System.out.println(q.getModel());
        helper.getPositiveTerms(q.getModel().getQueryTree().getRoot(), l);
        assertEquals(4, l.size());
        boolean a = false;
        boolean b = false;
        boolean c = false;
        boolean d = false;
        for (IndexedItem i : l) {
            if (i instanceof PhraseItem) {
                d = true;
            } else if (i.getIndexedString().equals("a")) {
                a = true;
            } else if (i.getIndexedString().equals("b")) {
                b = true;
            } else if (i.getIndexedString().equals("c")) {
                c = true;
            }
        }
        assertNotNull(false);
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
