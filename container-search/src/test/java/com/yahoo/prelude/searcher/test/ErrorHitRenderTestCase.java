// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.prelude.templates.SearchRendererAdaptor;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.text.XMLWriter;
import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.assertEquals;

/**
 * Tests marking hit properties as XML
 *
 * @author Steinar Knutsen
 */
public class ErrorHitRenderTestCase {

    @Test
    public void testXMLEscaping() throws java.io.IOException {
        ErrorHit h = new DefaultErrorHit("testcase", ErrorMessage.createUnspecifiedError("<>\"&"));

        StringWriter writer = new StringWriter();
        SearchRendererAdaptor.renderMessageDefaultErrorHit(new XMLWriter(writer), h.errors().iterator().next());
        assertEquals("<error source=\"testcase\" error=\"Unspecified error\" code=\"5\">&lt;&gt;\"&amp;</error>\n",
                     writer.toString());
    }

}
