// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.routing.ErrorDirective;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;
import com.yahoo.yolean.Exceptions;

/**
 * Keeps an operation with its message.
 *
 * This implementation is based on V2, but the code is restructured.
 *
 * @author dybis
 */
class DocumentOperationMessageV3 {

    private final String operationId;
    private final Message message;

    private DocumentOperationMessageV3(String operationId, Message message) {
        this.operationId = operationId;
        this.message = message;
    }

    Message getMessage() {
        return message;
    }

    String getOperationId() {
        return operationId;
    }

    static DocumentOperationMessageV3 newErrorMessage(String operationId, Exception exception) {
        Message feedErrorMessageV3 = new FeedErrorMessage(operationId);
        DocumentOperationMessageV3 msg = new DocumentOperationMessageV3(operationId, feedErrorMessageV3);
        Hop hop = new Hop();
        hop.addDirective(new ErrorDirective(Exceptions.toMessageString(exception)));
        Route route = new Route();
        route.addHop(hop);
        feedErrorMessageV3.setRoute(route);
        return msg;
    }

    static DocumentOperationMessageV3 newUpdateMessage(VespaXMLFeedReader.Operation op, String operationId) {
        DocumentUpdate update = op.getDocumentUpdate();
        update.setCondition(op.getCondition());
        Message msg = new UpdateDocumentMessage(update);

        String id = (operationId == null) ? update.getId().toString() : operationId;
        return new DocumentOperationMessageV3(id, msg);
    }

    static DocumentOperationMessageV3 newRemoveMessage(VespaXMLFeedReader.Operation op, String operationId) {
        DocumentRemove remove = new DocumentRemove(op.getRemove());
        remove.setCondition(op.getCondition());
        Message msg = new RemoveDocumentMessage(remove);

        String id = (operationId == null) ? remove.getId().toString() : operationId;
        return new DocumentOperationMessageV3(id, msg);
    }

    static DocumentOperationMessageV3 newPutMessage(VespaXMLFeedReader.Operation op, String operationId) {
        DocumentPut put = new DocumentPut(op.getDocument());
        put.setCondition(op.getCondition());
        Message msg = new PutDocumentMessage(put);

        String id = (operationId == null) ? put.getId().toString() : operationId;
        return new DocumentOperationMessageV3(id, msg);
    }

    static DocumentOperationMessageV3 create(VespaXMLFeedReader.Operation operation, String operationId, Metric metric) {
        switch (operation.getType()) {
            case DOCUMENT:
                metric.add(MetricNames.NUM_PUTS, 1, null /*metricContext*/);
                return newPutMessage(operation, operationId);
            case REMOVE:
                metric.add(MetricNames.NUM_REMOVES, 1, null /*metricContext*/);
                return newRemoveMessage(operation, operationId);
            case UPDATE:
                metric.add(MetricNames.NUM_UPDATES, 1, null /*metricContext*/);
                return newUpdateMessage(operation, operationId);
            default:
                // typical end of feed
                return null;
        }
    }

}
