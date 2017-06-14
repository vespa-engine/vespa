// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.demo;

import com.yahoo.search.Result;
import com.yahoo.search.rendering.Renderer;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

/**
 * Render result sets as plain text. First line is whether an error occurred,
 * second rendering initialization time stamp, then each line is the ID of each
 * document returned, and the last line is time stamp for when the renderer was
 * finished.
 */
public class DemoRenderer extends Renderer {
    private String heading;

    /**
     * No global, shared state to set.
     */
    public DemoRenderer() {
    }

    @Override
    protected void render(Writer writer, Result result) throws IOException {
        if (result.hits().getErrorHit() == null) {
            writer.write("OK\n");
        } else {
            writer.write("Oops!\n");
        }
        writer.write(heading);
        writer.write("\n");
        renderHits(writer, result.hits());
        writer.write("Rendering finished work: " + System.currentTimeMillis());
        writer.write("\n");
    }

    private void renderHits(Writer writer, HitGroup hits) throws IOException {
        for (Iterator<Hit> i = hits.deepIterator(); i.hasNext();) {
            Hit h = i.next();
            if (h.types().contains("summary")) {
                String id = h.getDisplayId();
                if (id != null) {
                    writer.write(id);
                    writer.write("\n");
                }
            }
        }
    }

    @Override
    public String getEncoding() {
        return "utf-8";
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
        heading = "Renderer initialized: " + time;
    }

}
