// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public interface ProcessingEndpoint {

    public void processingDone(Processing processing);

    public void processingFailed(Processing processing, Exception exception);

}
