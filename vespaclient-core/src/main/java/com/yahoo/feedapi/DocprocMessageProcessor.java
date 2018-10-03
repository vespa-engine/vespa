// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocprocService;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.*;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vdslib.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DocprocMessageProcessor implements MessageProcessor {
    private final DocprocService docproc;
    private final ComponentRegistry<DocprocService> docprocServiceRegistry;

    public DocprocMessageProcessor(DocprocService docproc, ComponentRegistry<DocprocService> docprocServiceRegistry) {
        this.docproc = docproc;
        this.docprocServiceRegistry = docprocServiceRegistry;
    }

    @Override
    public void process(Message m) {
        try {
            List<DocumentOperation> documentBases = new ArrayList<DocumentOperation>();

            if (m.getType() == DocumentProtocol.MESSAGE_PUTDOCUMENT) {
                documentBases.add(((PutDocumentMessage) m).getDocumentPut());
            } else if (m.getType() == DocumentProtocol.MESSAGE_UPDATEDOCUMENT) {
                documentBases.add(((UpdateDocumentMessage) m).getDocumentUpdate());
            } else if (m.getType() == DocumentProtocol.MESSAGE_REMOVEDOCUMENT) {
                documentBases.add(((RemoveDocumentMessage) m).getDocumentRemove());
            }

            if (docproc != null) {
                processDocumentOperations(documentBases, m);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void processDocumentOperations(List<DocumentOperation> documentOperations, Message m) throws Exception {
        Processing processing = Processing.createProcessingFromDocumentOperations(docproc.getName(), documentOperations, new CallStack(docproc.getCallStack()));
        processing.setServiceName(docproc.getName());
        processing.setDocprocServiceRegistry(docprocServiceRegistry);
        processing.setVariable("route", m.getRoute());
        processing.setVariable("timeout", m.getTimeRemaining());

        DocumentProcessor.Progress progress = docproc.getExecutor().process(processing);
        while (DocumentProcessor.Progress.LATER.equals(progress)) {
            Thread.sleep(50);
            progress = docproc.getExecutor().process(processing);
        }

        if (progress == DocumentProcessor.Progress.FAILED
                || progress == DocumentProcessor.Progress.PERMANENT_FAILURE) {
            throw new RuntimeException("Processing of " + documentOperations + " failed: " + progress + ".");
        }

        m.setRoute((Route) processing.getVariable("route"));
        m.setTimeRemaining((Long) processing.getVariable("timeout"));
    }

}
