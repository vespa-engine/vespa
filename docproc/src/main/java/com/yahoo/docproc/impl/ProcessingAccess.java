package com.yahoo.docproc.impl;

import com.yahoo.docproc.CallStack;
import com.yahoo.document.DocumentOperation;

import java.util.List;

/**
 * Bridge to access protected (originally package private) methods in {@link com.yahoo.docproc.Processing}.
 *
 * @author gjoranv
 */
public abstract class ProcessingAccess {

    protected ProcessingEndpoint getEndpoint() {
        throw new UnsupportedOperationException("docproc.Processing must override this method!");
    }

    protected void setEndpoint(ProcessingEndpoint endpoint) {
        throw new UnsupportedOperationException("docproc.Processing must override this method!");
    }

    protected void setCallStack(CallStack callStack) {
        throw new UnsupportedOperationException("docproc.Processing must override this method!");
    }

    protected List<DocumentOperation> getOnceOperationsToBeProcessed() {
        throw new UnsupportedOperationException("docproc.Processing must override this method!");
    }

}
