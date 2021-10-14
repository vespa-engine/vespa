// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Enable ANTLR to do case insensitive comparisons when reading from files without throwing away the case in the token.
 */
class CaseInsensitiveInputStream extends ANTLRInputStream {

    public CaseInsensitiveInputStream() {
        super();
    }

    public CaseInsensitiveInputStream(InputStream input) throws IOException {
        super(input);
    }

    public CaseInsensitiveInputStream(InputStream input, int size) throws IOException {
        super(input, size);
    }
    
    public CaseInsensitiveInputStream(char[] data, int numberOfActualCharsInArray) throws IOException {
        super(data, numberOfActualCharsInArray);
    }
    
    public CaseInsensitiveInputStream(String input) throws IOException {
        super(input);
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
