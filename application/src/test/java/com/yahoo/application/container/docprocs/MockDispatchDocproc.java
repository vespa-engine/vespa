// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.docprocs;

import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.messagebus.jdisc.MbusRequest;
import com.yahoo.messagebus.routing.Route;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author Christian Andersen
 */
public class MockDispatchDocproc extends DocumentProcessor {
    private final Route route;
    private final URI uri;
    private final CurrentContainer currentContainer;
    private final List<Response> responses = new ArrayList<>();

    public MockDispatchDocproc(CurrentContainer currentContainer) {
        this.route = Route.parse("default");
        this.uri = URI.create("mbus://remotehost/source");
        this.currentContainer = currentContainer;
    }

    @Override
    public Progress process(Processing processing) {
        for (DocumentOperation op : processing.getDocumentOperations()) {
            PutDocumentMessage message = new PutDocumentMessage((DocumentPut)op);
            var future = createRequest(message).dispatch();
            try {
                responses.add(future.get());
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return Progress.DONE;
    }

    private RequestDispatch createRequest(final DocumentMessage message) {
        return new RequestDispatch() {
            @Override
            protected Request newRequest() {
                return new MbusRequest(currentContainer, uri, message.setRoute(route), false);
            }
        };
    }

    public List<Response> getResponses() {
        return responses;
    }
}
