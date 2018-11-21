// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.messagebus;

import com.yahoo.docproc.Processing;
import com.yahoo.document.*;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Message;

import java.util.logging.Logger;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class MessageFactory {

    private final static Logger log = Logger.getLogger(MessageFactory.class.getName());
    private final Message requestMsg;
    private final LoadType loadType;
    private final DocumentProtocol.Priority priority;

    public MessageFactory(DocumentMessage requestMsg) {
        this.requestMsg = requestMsg;
        loadType = requestMsg.getLoadType();
        priority = requestMsg.getPriority();
    }

    public DocumentMessage fromDocumentOperation(Processing processing, DocumentOperation documentOperation) {
        DocumentMessage msg = newMessage(documentOperation);
        msg.setLoadType(loadType);
        msg.setPriority(priority);
        msg.setRoute(requestMsg.getRoute());
        msg.setTimeReceivedNow();
        msg.setTimeRemaining(requestMsg.getTimeRemainingNow());
        msg.getTrace().setLevel(requestMsg.getTrace().getLevel());
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Created '" + msg.getClass().getName() +
                                    "', route = '" + msg.getRoute() +
                                    "', priority = '" + msg.getPriority().name() +
                                    "', load type = '" + msg.getLoadType() +
                                    "', trace level = '" + msg.getTrace().getLevel() +
                                    "', time remaining = '" + msg.getTimeRemaining() + "'.");
        }
        return msg;
    }

    private static DocumentMessage newMessage(DocumentOperation documentOperation) {
        final TestAndSetMessage message;

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
