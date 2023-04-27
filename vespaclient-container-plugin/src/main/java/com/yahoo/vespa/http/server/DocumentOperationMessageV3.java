// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.Message;
import com.yahoo.vespaxmlparser.FeedOperation;

/**
 * Keeps an operation with its message.
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

    private static DocumentOperationMessageV3 newUpdateMessage(FeedOperation op, String operationId) {
        var msg = new UpdateDocumentMessage(op.getDocumentUpdate());

        String id = (operationId == null) ? msg.getDocumentUpdate().getId().toString() : operationId;
        return new DocumentOperationMessageV3(id, msg);
    }

    static DocumentOperationMessageV3 newRemoveMessage(FeedOperation op, String operationId) {
        var msg = new RemoveDocumentMessage(op.getDocumentRemove());

        String id = (operationId == null) ? msg.getDocumentId().toString() : operationId;
        return new DocumentOperationMessageV3(id, msg);
    }

    private static DocumentOperationMessageV3 newPutMessage(FeedOperation op, String operationId) {
        var msg = new PutDocumentMessage(op.getDocumentPut());

        String id = (operationId == null) ? msg.getDocumentPut().getId().toString() : operationId;
        return new DocumentOperationMessageV3(id, msg);
    }

    static DocumentOperationMessageV3 create(FeedOperation operation, String operationId, Metric metric) {
        switch (operation.getType()) {
            case DOCUMENT -> {
                metric.add(MetricNames.NUM_PUTS, 1, null);
                return newPutMessage(operation, operationId);
            }
            case REMOVE -> {
                metric.add(MetricNames.NUM_REMOVES, 1, null);
                return newRemoveMessage(operation, operationId);
            }
            case UPDATE -> {
                metric.add(MetricNames.NUM_UPDATES, 1, null);
                return newUpdateMessage(operation, operationId);
            }
            default -> {
                // typical end of feed
                return null;
            }
        }
    }

}
