// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.interpretation;

import java.util.HashMap;

/**
 * A modification of a text.
 *
 * This class represents a possible rewrite of an original text. Reasons for rewrite may be due to possible
 * spelling errors in the text or to query expansion.
 *
 * @author Arne Bergene Fossaa
 */
public class Modification extends HashMap<String,Object>{

    public final static AnnotationClass MODIFICATION_CLASS = new AnnotationClass("modification");

    private final String text;
    private final Annotations annotations;

    public Modification(String text) {
        this.text = text;
        Span span = new Span(this);
        this.annotations = span.annotate(MODIFICATION_CLASS);
    }

    public String getText() {
        return text;
    }

    public Annotations getAnnotation() {
        return annotations;
    }

}

