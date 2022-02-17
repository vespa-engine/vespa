// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.config.DocumentmanagerConfig;

/**
 * @author bratseth
 */
public class TestDocProc extends DocumentProcessor {

    public TestDocProc(DocumentmanagerConfig config) {
    }

    @Override
    public Progress process(Processing processing) {
        return Progress.DONE;
    }

}
