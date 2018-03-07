// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;

import java.util.Arrays;
import java.util.List;

/**
 * This is a container for all {@link Annotation}s constants used by built-in Vespa features. These must be in sync with
 * the corresponding class in the C++ file 'document/datatype/annotationtype.h'.
 *
 * @author Simon Thoresen
 */
@SuppressWarnings({ "UnusedDeclaration" })
public final class AnnotationTypes {

    private AnnotationTypes() {
        // unreachable
    }

    public static final AnnotationType TERM = new AnnotationType("term", DataType.STRING, 1);
    public static final AnnotationType TOKEN_TYPE = new AnnotationType("token_type", DataType.INT, 2);
    public static final AnnotationType CANONICAL = new AnnotationType("canonical", DataType.STRING, 3);
    public static final AnnotationType NORMALIZED = new AnnotationType("normalized", DataType.STRING, 4);
    public static final AnnotationType READING = new AnnotationType("reading", DataType.STRING, 5);
    public static final AnnotationType STEM = new AnnotationType("stem", DataType.STRING, 6);
    public static final AnnotationType TRANSFORMED = new AnnotationType("transformed", DataType.STRING, 7);
    public static final AnnotationType PROXIMITY_BREAK = new AnnotationType("proximity_break", DataType.DOUBLE, 8);
    public static final AnnotationType SPECIAL_TOKEN = new AnnotationType("special_token", 9);

    public static final List<AnnotationType> ALL_TYPES = Arrays.asList(TERM, TOKEN_TYPE, CANONICAL, NORMALIZED, READING,
                                                                       STEM, TRANSFORMED, PROXIMITY_BREAK,
                                                                       SPECIAL_TOKEN);
}
