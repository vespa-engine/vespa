// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.messagebus;

import com.yahoo.docproc.Processing;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.TestAndSetMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import java.util.logging.Level;
import com.yahoo.messagebus.Message;

import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
class MessageFactory {

    private final static Logger log = Logger.getLogger(MessageFactory.class.getName());
    private final Message requestMsg;

    public MessageFactory(DocumentMessage requestMsg) {
        this.requestMsg = requestMsg;
    }

    public DocumentMessage fromDocumentOperation(Processing processing, DocumentOperation documentOperation) {
        DocumentMessage message = newMessage(documentOperation);
        message.setRoute(requestMsg.getRoute());
        message.setTimeReceivedNow();
        message.setTimeRemaining(requestMsg.getTimeRemainingNow());
        message.getTrace().setLevel(requestMsg.getTrace().getLevel());
        log.log(Level.FINE, () -> "Created '" + message.getClass().getName() +
                                  "', route = '" + message.getRoute() +
                                  "', trace level = '" + message.getTrace().getLevel() +
                                  "', time remaining = '" + message.getTimeRemaining() + "'.");
        return message;
    }

    private static DocumentMessage newMessage(DocumentOperation documentOperation) {
        TestAndSetMessage message;

        if (documentOperation instanceof DocumentPut) {
            message = new PutDocumentMessage(((DocumentPut)documentOperation));
        } else if (documentOperation instanceof DocumentUpdate) {
            message = new UpdateDocumentMessage((DocumentUpdate)documentOperation);
        } else if (documentOperation instanceof DocumentRemove) {
            message = new RemoveDocumentMessage(documentOperation.getId());
        } else {
            throw new UnsupportedOperationException(documentOperation.getClass().getName());
        }

        message.setCondition(documentOperation.getCondition());
        return message;
    }

}
