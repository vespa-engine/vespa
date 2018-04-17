// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result.test;

import com.yahoo.prelude.templates.SearchRendererAdaptor;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorMessage;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class DefaultErrorHitTestCase {

    @Test
    public void testErrorHitRenderingWithException() throws IOException {
        NullPointerException cause=null;
        try {
            Object a=null;
            a.toString();
        }
        catch (NullPointerException e) {
            cause=e;
        }
        StringWriter w=new StringWriter();
        SearchRendererAdaptor.simpleRenderDefaultErrorHit(w, new DefaultErrorHit("test", new ErrorMessage(79, "Myerror", "Mydetail", cause)));
        String sep = System.getProperty("line.separator");
        assertEquals(
                "<errordetails>\n" +
                "  <error source=\"test\" error=\"Myerror\" code=\"79\">Mydetail\n" +
                "    <cause>\n" +
                "java.lang.NullPointerException" + sep +
                "\tat "
                ,w.toString().substring(0, 119+sep.length()));
    }

}
