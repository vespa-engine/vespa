// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result.test;

import java.io.IOException;

import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.templates.test.TilingTestCase;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ArrayOutputTestCase {

    @Test
    public void testArrayOutput() throws IOException {
        Result r=new Result(new Query("?query=ignored"));
        Hit hit=new Hit("test");
        hit.setField("phone",new XMLString("\n      <item>408-555-1234</item>" + "\n      <item>408-555-5678</item>\n    "));
        r.hits().add(hit);

        String rendered = TilingTestCase.getRendered(r);
        String[] lines= rendered.split("\n");
        assertEquals("    <field name=\"phone\">",lines[4]);
        assertEquals("      <item>408-555-1234</item>",lines[5]);
        assertEquals("      <item>408-555-5678</item>",lines[6]);
        assertEquals("    </field>",lines[7]);
    }

}
