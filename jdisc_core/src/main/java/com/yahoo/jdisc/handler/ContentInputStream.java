// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

/**
 * <p>This class extends {@link UnsafeContentInputStream} and adds a finalizer to it that calls {@link #close()}. This
 * has a performance impact, but ensures that an unclosed stream does not prevent shutdown.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public final class ContentInputStream extends UnsafeContentInputStream {

    /**
     * <p>Constructs a new ContentInputStream that reads from the given {@link ReadableContentChannel}.</p>
     *
     * @param content The content to read the stream from.
     */
    public ContentInputStream(ReadableContentChannel content) {
        super(content);
    }

    @Override
    public void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
