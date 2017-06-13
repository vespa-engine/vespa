// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.demo;

import com.yahoo.processing.Response;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Render a response as plain text. First line is whether an error occurred,
 * second rendering initialization time stamp, then each line is response data
 * (indented according to its place in the hierarchic response), and the last
 * line is time stamp for when the renderer was finished.
 */
public class DemoRenderer extends AsynchronousSectionedRenderer<Response> {

    /**
     * Indent size for rendering hierarchic response data.
     */
    public static final int INDENT_SIZE = 4;

    // Response heading
    private String heading;

    // Just a utility to write strings to output stream
    private Writer writer;

    // current indent in the rendered tree
    String indent;

    /**
     * No global, shared state to set.
     */
    public DemoRenderer() {
    }

    @Override
    public void beginResponse(OutputStream stream) throws IOException {
        writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8.newEncoder());
        if (getResponse().data().request().errors().size() == 0) {
            writer.write("OK\n");
        } else {
            writer.write("Oops!\n");
        }
        writer.write(heading);
        writer.write('\n');
    }

    /**
     * Indent {@link #INDENT_SIZE} spaces for each level in the tree.
     */
    @Override
    public void beginList(DataList<?> list) throws IOException {
        indent = spaces((getRecursionLevel() - 1) * INDENT_SIZE);
    }

    @Override
    public void data(Data data) throws IOException {
        if (!(data instanceof DataProcessor.DemoData)) {
            return;
        }
        writer.write(indent);
        writer.write(((DataProcessor.DemoData) data).content());
        writer.write('\n');
    }

    private static String spaces(int len) {
        StringBuilder s = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            s.append(' ');
        }
        return s.toString();
    }

    /**
     * Out-dent one level if not at outermost level.
     */
    @Override
    public void endList(DataList<?> list) throws IOException {
        if (indent.length() == 0) {
            return;
        }
        indent = spaces(indent.length() - INDENT_SIZE);
    }

    @Override
    public void endResponse() throws IOException {
        writer.write("Rendering finished work: " + System.currentTimeMillis());
        writer.write('\n');
        writer.close();
    }

    @Override
    public String getEncoding() {
        return StandardCharsets.UTF_8.name();
    }

    @Override
    public String getMimeType() {
        return "text/plain";
    }

    /**
     * Initialize mutable, per-result set state here.
     */
    @Override
    public void init() {
        long time = System.currentTimeMillis();

        super.init(); // Important! The base class needs to initialize itself.
        heading = "Renderer initialized: " + time;
    }
}
