// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.docproc.Processing;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.Response;

import java.net.URI;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public interface RequestContext {

    List<Processing> getProcessings();

    String getServiceName();

    URI getUri();

    boolean isProcessable();

    void processingDone(List<Processing> processing);

    void processingFailed(ErrorCode error, String msg);

    void processingFailed(Exception exception);

    /** Returns whether this request has timed out */
    default boolean hasExpired() { return false;}

    void skip();

    enum ErrorCode {

        //transient:
        ERROR_ABORTED(Response.Status.TEMPORARY_REDIRECT, DocumentProtocol.ERROR_ABORTED),
        ERROR_BUSY(Response.Status.TEMPORARY_REDIRECT, DocumentProtocol.ERROR_BUSY),
        //fatal:
        ERROR_PROCESSING_FAILURE(Response.Status.INTERNAL_SERVER_ERROR, DocumentProtocol.ERROR_PROCESSING_FAILURE);

        private final int discStatus;
        private final int documentProtocolStatus;

        ErrorCode(int discStatus, int documentProtocolStatus) {
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
