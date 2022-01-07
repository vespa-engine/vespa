// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.IOException;
import java.io.Writer;

/**
 * This is a basic writer for presenting text. Its has the pattern as
 * java.io.Writer, but it allows for more overrides for speed.
 * This introduces additional interfaces in addition to the java.lang.Writer.
 * The purpose is to allow for optimizations.
 *
 * @author baldersheim
 */
public abstract class GenericWriter extends Writer {

    public GenericWriter write(char c) throws java.io.IOException {
        char[] t = new char[1];
        t[0] = c;
        try {
            write(t, 0, 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public GenericWriter write(CharSequence s) throws java.io.IOException {
        for (int i=0, m=s.length(); i < m; i++) {
            write(s.charAt(i));
        }
        return this;
    }

    public GenericWriter write(long i) throws java.io.IOException {
        write(String.valueOf(i));
        return this;
    }

    public GenericWriter write(short i) throws java.io.IOException {
        write(String.valueOf(i));
        return this;
    }

    public GenericWriter write(byte i) throws java.io.IOException {
        write(String.valueOf(i));
        return this;
    }

    public GenericWriter write(double i) throws java.io.IOException {
        write(String.valueOf(i));
        return this;
    }

    public GenericWriter write(float i) throws java.io.IOException {
        write(String.valueOf(i));
        return this;
    }

    public GenericWriter write(boolean i) throws java.io.IOException {
        write(String.valueOf(i));
        return this;
    }

    public GenericWriter write(AbstractUtf8Array v) throws java.io.IOException {
        write(v.toString());
        return this;
    }

}
