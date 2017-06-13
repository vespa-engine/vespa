// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.OutputStream;

/**
 * @author lulf
 * @since 5.1
 */
public class StderrLogTarget implements LogTarget {

    @Override
    public OutputStream open() {
        return new UncloseableOutputStream(System.err);
    }

    @Override
    public void close() {
        // ignore
    }
}
