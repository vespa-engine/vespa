// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DocumentPut;

/**
 * Document processor used for a simple, silly test.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class IncrementingDocumentProcessor extends SimpleDocumentProcessor {
    int counter = 0;

    @Override
    public void process(DocumentPut put) {
        System.err.println(counter + " DocumentPut: " + put);
        counter++;
    }
}
