// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

/**
 * @author Einar M R Rosenvinge
 */
public interface ProcessingEndpoint {

    void processingDone(Processing processing);

    void processingFailed(Processing processing, Exception exception);

}
