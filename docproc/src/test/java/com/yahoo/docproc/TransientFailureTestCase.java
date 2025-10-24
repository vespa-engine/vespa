// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.docproc.impl.DocprocService;
import com.yahoo.docproc.impl.ProcessingEndpoint;
import com.yahoo.docproc.impl.TransientFailureException;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Einar M R Rosenvinge
 */
public class TransientFailureTestCase {

    DocumentType type;

    @Before
    public void setUp() {
        type = new DocumentType("test");
        type.addField("boo", DataType.STRING);
    }

    @Test
    public void testTransientFailures() {
        DocprocService service = new DocprocService("transfail");
        CallStack stack = new CallStack();
        stack.addNext(new OkDocProc()).addNext(new TransientFailDocProc());
        service.setCallStack(stack);
        service.setInService(true);

        EndpointSupportingTransientFailures endpoint = new EndpointSupportingTransientFailures();

        DocumentPut put;

        put = new DocumentPut(type, new DocumentId("id:transfail:test::bad"));
        service.process(put, endpoint);
        while (service.doWork()) { }
        assertEquals(0, endpoint.numOk);
        assertEquals(1, endpoint.numTransientFail);
        assertEquals(0, endpoint.numFail);

        put = new DocumentPut(type, new DocumentId("id:transfail:test::verybad"));
        service.process(put, endpoint);
        while (service.doWork()) { }
        assertEquals(0, endpoint.numOk);
        assertEquals(1, endpoint.numTransientFail);
        assertEquals(1, endpoint.numFail);

        put = new DocumentPut(type, new DocumentId("id:transfail:test::good"));
        service.process(put, endpoint);
        while (service.doWork()) { }
        assertEquals(1, endpoint.numOk);
        assertEquals(1, endpoint.numTransientFail);
        assertEquals(1, endpoint.numFail);

        put = new DocumentPut(type, new DocumentId("id:transfail:test::veryverybad"));
        service.process(put, endpoint);
        while (service.doWork()) { }
        assertEquals(1, endpoint.numOk);
        assertEquals(1, endpoint.numTransientFail);
        assertEquals(2, endpoint.numFail);
    }

    private static class OkDocProc extends SimpleDocumentProcessor {
    }

    private static class TransientFailDocProc extends DocumentProcessor {
        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                if (op.getId().toString().equals("id:transfail:test::bad")) {
                    throw new TransientFailureException("sorry, try again later");
                } else if (op.getId().toString().equals("id:transfail:test::verybad")) {
                    return Progress.FAILED;
                } else if (op.getId().toString().equals("id:transfail:test::veryverybad")) {
                    return Progress.PERMANENT_FAILURE;
                }
            }
            return Progress.DONE;
        }
    }

    private static class EndpointSupportingTransientFailures implements ProcessingEndpoint {
        private volatile int numOk = 0;
        private volatile int numTransientFail = 0;
        private volatile int numFail = 0;

        public void processingDone(Processing processing) {
            numOk++;
        }

        public void processingFailed(Processing processing, Exception exception) {
            if (exception instanceof TransientFailureException) {
                numTransientFail++;
            } else {
                numFail++;
            }
        }
    }

}
