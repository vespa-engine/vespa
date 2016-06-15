// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.prelude.templates.SearchRendererAdaptor;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.search.Result;
import com.yahoo.search.rendering.DefaultRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class ResultRenderingUtil {

    public static String getRendered(Result result) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Charset cs = Charset.forName("utf-8");
        CharsetDecoder decoder = cs.newDecoder();
        SearchRendererAdaptor.callRender(stream, result);
        stream.flush();
        return decoder.decode(ByteBuffer.wrap(stream.toByteArray())).toString();
    }

}
