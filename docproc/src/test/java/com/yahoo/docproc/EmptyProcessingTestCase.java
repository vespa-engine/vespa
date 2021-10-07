// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import org.junit.Test;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class EmptyProcessingTestCase {

    @Test
    public void emptyProcessing() {
        DocprocService service = new DocprocService("juba");
        DocumentProcessor processor = new IncrementingDocumentProcessor();
        CallStack stack = new CallStack("juba");
        stack.addLast(processor);
        service.setCallStack(stack);
        service.setInService(true);

        Processing proc = new Processing();
        proc.setServiceName("juba");

        service.process(proc);

        while (service.doWork()) { }

    }
}
