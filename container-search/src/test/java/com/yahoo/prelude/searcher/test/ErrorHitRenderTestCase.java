// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.prelude.templates.SearchRendererAdaptor;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.text.XMLWriter;

import java.io.StringWriter;

/**
 * Tests marking hit properties as XML
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ErrorHitRenderTestCase extends junit.framework.TestCase {

    public ErrorHitRenderTestCase(String name) {
        super(name);
    }

    public void testXMLEscaping() throws java.io.IOException {
        ErrorHit h = new DefaultErrorHit("testcase",
                                  ErrorMessage.createUnspecifiedError("<>\"&"));

        StringWriter writer = new StringWriter();
        SearchRendererAdaptor.renderMessageDefaultErrorHit(new XMLWriter(writer), h.errors().iterator().next());
        assertEquals("<error source=\"testcase\" error=\"Unspecified error\" code=\"5\">&lt;&gt;\"&amp;</error>\n",
                     writer.toString());

    }
}
