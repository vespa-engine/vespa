// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.test;

import com.yahoo.document.DocumentRemove;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.local.LocalDocumentAccess;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.RPCMessageBus;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;

import java.util.Arrays;

/**
 * Mock-up destination used for testing.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class Destination implements MessageHandler {

    private final DestinationSession session;
    private final DocumentAccess access;
    private final SyncSession local;
    private final RPCMessageBus bus;

    public Destination(String slobrokConfigId, String documentManagerConfigId) {

        DocumentAccessParams params = new DocumentAccessParams();
        params.setDocumentManagerConfigId(documentManagerConfigId);
        access = new LocalDocumentAccess(params);
        local = access.createSyncSession(new SyncParameters.Builder().build());
        bus = new RPCMessageBus(Arrays.asList((Protocol)new DocumentProtocol(access.getDocumentTypeManager())),
                                new RPCNetworkParams()
                                        .setIdentity(new Identity("test/destination"))
                                        .setSlobrokConfigId(slobrokConfigId),
                                "file:src/test/cfg/messagebus.cfg");
        session = bus.getMessageBus().createDestinationSession("session", true, this);
    }

    protected void sendReply(Reply reply) {
        session.reply(reply);
    }

    public void handleMessage(Message msg) {
        Reply reply = ((DocumentMessage)msg).createReply();
        try {
            switch (msg.getType()) {

            case DocumentProtocol.MESSAGE_GETDOCUMENT:
                reply = new GetDocumentReply(local.get(((GetDocumentMessage)msg).getDocumentId()));
                break;

            case DocumentProtocol.MESSAGE_PUTDOCUMENT:
                local.put(((PutDocumentMessage)msg).getDocumentPut());
                break;

            case DocumentProtocol.MESSAGE_REMOVEDOCUMENT:
                local.remove(new DocumentRemove(((RemoveDocumentMessage)msg).getDocumentId()));
                break;

            case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:
                local.update(((UpdateDocumentMessage)msg).getDocumentUpdate());
                break;

            default:
                throw new UnsupportedOperationException("Unsupported message type '" + msg.getType() + "'.");
            }
        } catch (Exception e) {
            reply = new EmptyReply();
            reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, e.toString()));
        }
        msg.swapState(reply);
        session.reply(reply);
    }

    public void shutdown() {
        local.destroy();
        access.shutdown();
        session.destroy();
        bus.getMessageBus().destroy();
    }

}
