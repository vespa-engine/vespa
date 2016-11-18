// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.search.Result;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

@SuppressWarnings("deprecation")
public class ResultRenderingUtil {

    public static String getRendered(Result result) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Charset cs = Charset.forName("utf-8");
        CharsetDecoder decoder = cs.newDecoder();
        com.yahoo.prelude.templates.SearchRendererAdaptor.callRender(stream, result);
        stream.flush();
        return decoder.decode(ByteBuffer.wrap(stream.toByteArray())).toString();
    }

}
