// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

/**
 * @author Simon Thoresen Hult
 */
public class UpdateDocumentReply extends WriteDocumentReply {

    private boolean found = true;

    /**
     * Constructs a new reply with no content.
     */
    public UpdateDocumentReply() {
        super(DocumentProtocol.REPLY_UPDATEDOCUMENT);
    }

    /**
     * Returns whether or not the document was found and updated.
     *
     * @return true if document was found
     */
    public boolean wasFound() {
        return found;
    }

    /**
     * Sets whether or not the document was found and updated.
     *
     * @param found True if the document was found
     */
    public void setWasFound(boolean found) {
        this.found = found;
    }
}
