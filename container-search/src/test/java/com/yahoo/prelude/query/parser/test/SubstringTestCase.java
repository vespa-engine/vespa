// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser.test;

import org.junit.jupiter.api.Test;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Check Substring in conjunction with query tokenization and parsing behaves properly.
 *
 * @author Steinar Knutsen
 */
public class SubstringTestCase {

    @Test
    final void testTokenLengthAndLowercasing() {
        Query q = new Query("/?query=\u0130");
        WordItem root = (WordItem) ((CompositeItem) q.getModel().getQueryTree().getRoot()).getItem(0);
        assertEquals("\u0130", root.getRawWord());
    }


    @Test
    final void testBug5968479() {
        String first = "\u0130\u015EBANKASI";
        String second = "GAZ\u0130EM\u0130R";
        Query q = new Query("/?query=" + enc(first) + "%20" + enc(second));
        CompositeItem root = (CompositeItem) q.getModel().getQueryTree().getRoot();
        assertEquals(first, ((WordItem) root.getItem(0)).getRawWord());
        assertEquals(second, ((WordItem) root.getItem(1)).getRawWord());
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
