// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * Represents an element of a hit property which is markup for
 * separating dynamic snippets.
 *
 * @author Steinar Knutsen
 */
public class SeparatorFieldPart extends MarkupFieldPart {

    public SeparatorFieldPart(String content) {
        super(content);
    }

}
