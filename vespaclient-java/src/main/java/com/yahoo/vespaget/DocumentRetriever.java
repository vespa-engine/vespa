// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaget;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.MessageBusSyncSession;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Trace;
import com.yahoo.text.Utf8;
import com.yahoo.vespaclient.ClusterDef;
import com.yahoo.vespaclient.ClusterList;

import java.util.Iterator;

/**
 * The document retriever is responsible for retrieving documents using the Document API and printing the result to standard out.
 *
 * @author bjorncs
 */
@SuppressWarnings("removal") // TODO: Remove on Vespa 9
public class DocumentRetriever {

    private final ClusterList clusterList;
    private final DocumentAccessFactory documentAccessFactory;
    private final ClientParameters params;

    private MessageBusSyncSession session;
    private MessageBusDocumentAccess documentAccess;

    public DocumentRetriever(ClusterList clusterList,
                             DocumentAccessFactory documentAccessFactory,
                             ClientParameters params) {
        this.clusterList = clusterList;
        this.documentAccessFactory = documentAccessFactory;
        this.params = params;
    }

    public void shutdown() {
        try {
            if (session != null) {
                session.destroy();
            }
        } catch (IllegalStateException e) {
            // Ignore exception on shutdown
        }
        try {
            if (documentAccess != null) {
                documentAccess.shutdown();
            }
        } catch (IllegalStateException e) {
            // Ignore exception on shutdown
        }
    }

    public void retrieveDocuments() throws DocumentRetrieverException {
        boolean first = true;
        String route = params.cluster.isEmpty() ? params.route : resolveClusterRoute(params.cluster);

        MessageBusParams messageBusParams = createMessageBusParams(params.configId, params.timeout, route);
        documentAccess = documentAccessFactory.createDocumentAccess(messageBusParams);
        session = documentAccess.createSyncSession(new SyncParameters.Builder().build());
        int trace = params.traceLevel;
        if (trace > 0) {
            session.setTraceLevel(trace);
        }

        Iterator<String> iter = params.documentIds;
        if (params.jsonOutput && !params.printIdsOnly) {
            System.out.println('[');
        }
        while (iter.hasNext()) {
            if (params.jsonOutput && !params.printIdsOnly) {
                if (!first) {
                    System.out.println(',');
                } else {
                    first = false;
                }
            }
            String docid = iter.next();
            Message msg = createDocumentRequest(docid);
            Reply reply = session.syncSend(msg);
            printReply(reply);
        }
        if (params.jsonOutput && !params.printIdsOnly) {
            System.out.println(']');
        }
    }

    private String resolveClusterRoute(String clusterName) throws DocumentRetrieverException {
        if (clusterList.getStorageClusters().isEmpty()) {
            throw new DocumentRetrieverException("The Vespa cluster does not have any content clusters declared.");
        }

        ClusterDef clusterDef = null;
        for (ClusterDef c : clusterList.getStorageClusters()) {
            if (c.getName().equals(clusterName)) {
                clusterDef = c;
            }
        }
        if (clusterDef == null) {
            String names = createClusterNamesString();
            throw new DocumentRetrieverException(String.format(
                    "The Vespa cluster contains the content clusters %s, not %s. Please select a valid vespa cluster.",
                    names, clusterName));
        }
        return clusterDef.getRoute();
    }

    private MessageBusParams createMessageBusParams(String configId, double timeout, String route) {
        MessageBusParams messageBusParams = new MessageBusParams();
        messageBusParams.setRoute(route);
        messageBusParams.setProtocolConfigId(configId);
        messageBusParams.setRoutingConfigId(configId);
        messageBusParams.setDocumentManagerConfigId(configId);

        if (timeout > 0) {
            messageBusParams.getSourceSessionParams().setTimeout(timeout);
        }
        return messageBusParams;
    }

    private Message createDocumentRequest(String docid) {
        GetDocumentMessage msg = new GetDocumentMessage(new DocumentId(docid), params.fieldSet);
        msg.setPriority(params.priority); // TODO: Remove on Vespa 9
        msg.setRetryEnabled(!params.noRetry);
        return msg;
    }

    private void printReply(Reply reply) {
        Trace trace = reply.getTrace();
        if (!trace.getRoot().isEmpty()) {
            System.out.println(trace);
        }

        if (reply.hasErrors()) {
            System.err.print("Request failed: ");
            for (int i = 0; i < reply.getNumErrors(); i++) {
                System.err.printf("\n  %s", reply.getError(i));
            }
            System.err.println();
            return;
        }

        if (!(reply instanceof GetDocumentReply)) {
            System.err.printf("Unexpected reply %s: '%s'\n", reply.getType(), reply.toString());
            return;
        }

        GetDocumentReply documentReply = (GetDocumentReply) reply;
        Document document = documentReply.getDocument();

        if (document == null) {
            System.out.println("Document not found.");
            return;
        }

        if (params.showDocSize) {
            System.out.printf("Document size: %d bytes.\n", document.getSerializedSize());
        }
        if (params.printIdsOnly) {
            System.out.println(document.getId());
        } else {
            if (params.jsonOutput) {
                System.out.print(Utf8.toString(JsonWriter.toByteArray(document)));
            } else {
                System.out.print(document.toXML("  "));
            }
        }
    }

    private String createClusterNamesString() {
        StringBuilder names = new StringBuilder();
        for (ClusterDef c : clusterList.getStorageClusters()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(c.getName());
        }
        return names.toString();
    }
}
