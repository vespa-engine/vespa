// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * A representation of an XML chunk.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class XMLString {

    private final String content;

    public XMLString(String content) {
        this.content = content;
    }

    public String toString() {
        return content;
    }

}
