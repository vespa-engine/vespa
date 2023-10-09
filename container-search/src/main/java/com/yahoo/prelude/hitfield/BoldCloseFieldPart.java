// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * Represents an element of a hit property which is markup, representing
 * end of a bolded area.
 *
 * @author Steinar Knutsen
 */
public class BoldCloseFieldPart extends MarkupFieldPart {

    public BoldCloseFieldPart(String content) {
        super(content);
    }

}
