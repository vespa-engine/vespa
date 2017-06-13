// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.OutputStream;

/**
 * @author lulf
 * @since 5.1
 */
public class StdoutLogTarget implements LogTarget {

    @Override
    public OutputStream open() {
        return new UncloseableOutputStream(System.out);
    }

    @Override
    public void close() {
        // ignore
    }
}
