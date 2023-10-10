// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.renderers;

import com.yahoo.search.Result;
import com.yahoo.search.rendering.Renderer;

import java.io.IOException;
import java.io.Writer;

/**
 * @author Christian Andersen
 */
public class MockRenderer extends Renderer {

    public MockRenderer() {
    }

    @Override
    public String getEncoding() {
        return "utf-8";
    }

    @Override
    public String getMimeType() {
        return "applications/xml";
    }

    @Override
    protected void render(Writer writer, Result result) throws IOException {
        writer.write("<mock hits=\"" + result.hits().size() + "\" />");
    }

}
