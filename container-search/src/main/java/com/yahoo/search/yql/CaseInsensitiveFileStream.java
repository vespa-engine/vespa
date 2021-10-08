// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CharStream;

import java.io.IOException;

/**
 * Enable ANTLR to do case insensitive comparisons when reading from files without throwing away the case in the token.
 */

class CaseInsensitiveFileStream extends ANTLRFileStream {

    public CaseInsensitiveFileStream(String fileName) throws IOException {
        super(fileName);
    }

    public CaseInsensitiveFileStream(String fileName, String encoding) throws IOException {
        super(fileName, encoding);
    }

    @Override
    public int LA(int i) {
        if (i == 0) {
            return 0;
        }
        if (i < 0) {
            i++; // e.g., translate LA(-1) to use offset 0
        }

        if ((p + i - 1) >= n) {
            return CharStream.EOF;
        }
        return Character.toLowerCase(data[p + i - 1]);
    }

}
