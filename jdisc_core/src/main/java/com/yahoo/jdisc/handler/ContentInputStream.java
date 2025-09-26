// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.lang.ref.Cleaner;

/**
 * This class extends {@link UnsafeContentInputStream} and adds a
 * Cleaner (was finalizer) to it that calls {@link #close()}. This has
 * a performance impact, but ensures that an unclosed stream does not
 * prevent shutdown (depending on GC).
 *
 * @author Simon Thoresen Hult
 */
public final class ContentInputStream extends UnsafeContentInputStream {

    private static final class Finalizer implements Runnable {
        private final ReadableContentChannel content;
        Finalizer(ReadableContentChannel content) {
            this.content = content;
        }
        public void run() {
            while (content.read() != null)
                ;
        }
    }

    private static final Cleaner cleaner = Cleaner.create();
    private final Finalizer state;
    private final Cleaner.Cleanable cleanable;

    /**
     * Constructs a new ContentInputStream that reads from the given {@link ReadableContentChannel}.
     *
     * @param content The content to read the stream from.
     */
    public ContentInputStream(ReadableContentChannel content) {
        super(content);
        this.state = new Finalizer(content);
        this.cleanable = cleaner.register(this, state);
    }
}
