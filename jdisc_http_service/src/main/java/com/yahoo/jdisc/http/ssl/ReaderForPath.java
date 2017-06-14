// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import java.io.Reader;
import java.nio.file.Path;

/**
 * A reader along with the path used to construct it.
 *
 * @author tonytv
 */
public final class ReaderForPath {

    public final Reader reader;
    public final Path path;

    public ReaderForPath(Reader reader, Path path) {
        this.reader = reader;
        this.path = path;
    }

}
