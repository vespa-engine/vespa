// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.messagebus.Reply;
import com.yahoo.text.Utf8String;

/**
 * This class implements a generic document protocol reply that can be reused by document messages that require no
 * special reply implementation while still allowing applications to distinguish between types.
 *
 * @author Simon Thoresen Hult
 */
public class DocumentReply extends Reply {

    private final int type;
    private DocumentProtocol.Priority priority = DocumentProtocol.Priority.NORMAL_3;

    /**
     * Constructs a new reply of given type.
     *
     * @param type The type code to assign to this.
     */
    public DocumentReply(int type) {
        this.type = type;
    }

    /**
     * Returns the priority tag for this message.
     * @return The priority.
     */
    public DocumentProtocol.Priority getPriority() {
        return priority;
    }

    /**
     * Sets the priority tag for this message.
     *
     * @param priority The priority to set.
     */
    public void setPriority(DocumentProtocol.Priority priority) {
        this.priority = priority;
    }

    @Override
    public Utf8String getProtocol() {
        return DocumentProtocol.NAME;
    }

    @Override
    public final int getType() {
        return type;
    }
}
