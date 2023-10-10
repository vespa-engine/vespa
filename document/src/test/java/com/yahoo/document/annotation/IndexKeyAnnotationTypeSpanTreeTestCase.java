// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class IndexKeyAnnotationTypeSpanTreeTestCase extends SpanTreeTestCase {

    @Override
    public void setUp() {
        super.setUp();
        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
    }
}
