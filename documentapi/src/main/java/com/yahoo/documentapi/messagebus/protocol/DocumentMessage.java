// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Routable;
import com.yahoo.text.Utf8String;

/**
 * @author Simon Thoresen Hult
 */
public abstract class DocumentMessage extends Message {

    private DocumentProtocol.Priority priority = DocumentProtocol.Priority.NORMAL_3;

    /**
     * Constructs a new message with no content.
     */
    public DocumentMessage() {
        // empty
    }

    /**
     * Creates and returns a reply to this message.
     *
     * @return The created reply.
     */
    public abstract DocumentReply createReply();

    @Override
    public void swapState(Routable rhs) {
        super.swapState(rhs);
        if (rhs instanceof DocumentMessage) {
            DocumentMessage msg = (DocumentMessage)rhs;

            DocumentProtocol.Priority pri = this.priority;
            this.priority = msg.priority;
            msg.priority = pri;
        }
    }

    /**
     * Returns the priority tag for this message. This is an optional tag added for VDS that is not interpreted by the
     * document protocol.
     *
     * @return The priority.
     * @deprecated explicit operation priority is deprecated
     */
    @Deprecated(forRemoval = true) // TODO: Remove on Vespa 9
    public DocumentProtocol.Priority getPriority() { return priority; }

    /**
     * Sets the priority tag for this message.
     *
     * @param priority The priority to set.
     * @deprecated specifying explicit operation priority is deprecated
     */
    @Deprecated(forRemoval = true) // TODO: Remove on Vespa 9
    public void setPriority(DocumentProtocol.Priority priority) {
        this.priority = priority;
    }

    @Override
    public int getApproxSize() {
        return 4 + 1; // type + priority // TODO update on Vespa 9 to not include deprecated fields
    }

    @Override
    public Utf8String getProtocol() {
        return DocumentProtocol.NAME;
    }

}
