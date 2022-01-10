// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.docprocs;

import com.yahoo.component.annotation.Inject;
import com.yahoo.application.MockApplicationConfig;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;

/**
 * @author Christian Andersen
 */
public class MockDocproc extends DocumentProcessor {
    private Processing lastProcessing;
    private MockApplicationConfig config;

    @Inject
    public MockDocproc(MockApplicationConfig config) {
        this.config = config;
    }

    @Override
    public Progress process(Processing processing) {
        this.lastProcessing = processing;
        return Progress.DONE;
    }

    public Processing getLastProcessing() {
        return lastProcessing;
    }

    public MockApplicationConfig getConfig() {
        return config;
    }
}
