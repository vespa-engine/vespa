// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import com.yahoo.data.XmlProducer;

/**
 * A representation of an XML chunk.
 *
 * @author Steinar Knutsen
 */
public class XMLString implements XmlProducer {

    private final String content;

    public XMLString(String content) {
        this.content = content;
    }

    public String toString() {
        return content;
    }

    public StringBuilder writeXML(StringBuilder target) {
        target.append(content);
        return target;
    }

    @Override
    public String toXML() {
        return content;
    }

}
