// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.docproc.Processing;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.Response;

import java.net.URI;
import java.util.List;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public interface RequestContext {

    public List<Processing> getProcessings();

    public String getServiceName();

    public URI getUri();

    public boolean isProcessable();

    public int getApproxSize();

    public int getPriority();

    public void processingDone(List<Processing> processing);

    public void processingFailed(ErrorCode error, String msg);

    public void processingFailed(Exception exception);

    /**
     * Will check if the given timeout has expired
     * @return true if the timeout has expired.
     */
    public default boolean hasExpired() { return false;}

    public void skip();

    public enum ErrorCode {
        //transient:
        ERROR_ABORTED(Response.Status.TEMPORARY_REDIRECT, DocumentProtocol.ERROR_ABORTED),
        ERROR_BUSY(Response.Status.TEMPORARY_REDIRECT, DocumentProtocol.ERROR_BUSY),
        //fatal:
        ERROR_PROCESSING_FAILURE(Response.Status.INTERNAL_SERVER_ERROR, DocumentProtocol.ERROR_PROCESSING_FAILURE);


        private int discStatus;
        private int documentProtocolStatus;

        private ErrorCode(int discStatus, int documentProtocolStatus) {
            this.discStatus = discStatus;
            this.documentProtocolStatus = documentProtocolStatus;
        }

        public int getDiscStatus() {
            return discStatus;
        }

        public int getDocumentProtocolStatus() {
            return documentProtocolStatus;
        }
    }
}
