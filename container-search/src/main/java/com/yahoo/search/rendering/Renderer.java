// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.io.ByteWriter;
import com.yahoo.processing.Request;
import com.yahoo.processing.execution.Execution;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.CompletableFuture;

/**
 * Renders a search result to a writer synchronously
 * - the result is completely rendered when the render method returns.
 * The renderers are cloned just before rendering,
 * and must therefore obey the following contract:
 *
 * <ol>
 * <li>At construction time, only final members shall be initialized, and these
 * must refer to immutable data only.</li>
 * <li>State mutated during rendering shall be initialized in the init method.</li>
 * </ol>
 *
 * @author Tony Vaagenes
 */
abstract public class Renderer extends com.yahoo.processing.rendering.Renderer<Result> {

    /**
     * Renders synchronously and returns when rendering is complete.
     *
     * @return a future which is always completed to true
     */
    @Override
    public final CompletableFuture<Boolean> renderResponse(OutputStream stream, Result response, Execution execution, Request request) {
        Writer writer = null;
        try {
            writer = createWriter(stream, response);
            render(writer, response);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (writer != null)
                try { writer.close(); } catch (IOException e2) {};
        }
        CompletableFuture<Boolean> completed = new CompletableFuture<>();
        completed.complete(true);
        return completed;
    }

    /**
     * Renders the result to the writer.
     */
    protected abstract void render(Writer writer, Result result) throws IOException;

    private Writer createWriter(OutputStream stream,Result result) {
        Charset cs = Charset.forName(getCharacterEncoding(result));
        CharsetEncoder encoder = cs.newEncoder();
        return new ByteWriter(stream, encoder);
    }

    public String getCharacterEncoding(Result result) {
        String encoding = result.getQuery().getModel().getEncoding();
        return (encoding != null) ? encoding : getEncoding();
    }

    /**
     * @return The summary class to fill the hits with if no summary class was
     * specified in the query presentation.
     */
    public String getDefaultSummaryClass() {
        return null;
    }

    /** Returns the encoding of the query, or the encoding given by the template if none is set */
    public final String getRequestedEncoding(Query query) {
        String encoding = query.getModel().getEncoding();
        if (encoding != null) return encoding;
        return getEncoding();
    }

    /**
     * Used to create a separate instance for each result to render.
     */
    @Override
    public Renderer clone() {
        return (Renderer) super.clone();
    }

}
