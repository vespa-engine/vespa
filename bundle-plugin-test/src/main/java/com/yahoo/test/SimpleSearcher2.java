// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;


/**
 * Test class for integration tests. Used to verify that nested classes are visited.
 *
 * Do not replace the fully qualified class names with imports!
 */
public class SimpleSearcher2 extends SimpleSearcher {
    public void dummy() {}

    private class InnerClassProcessor extends com.yahoo.processing.Processor {
        @Override
        public com.yahoo.processing.Response process(com.yahoo.processing.Request request,
                                                     com.yahoo.processing.execution.Execution execution) {
            return null;
        }
    }

    private static class NestedStaticClass {
        private com.yahoo.metrics.simple.Counter counter;

        @com.google.inject.Inject
        public NestedStaticClass() { }
    }

}
