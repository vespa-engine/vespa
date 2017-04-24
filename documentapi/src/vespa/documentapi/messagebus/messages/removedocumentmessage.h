// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/documentid.h>
#include <vespa/documentapi/messagebus/messages/testandsetmessage.h>

namespace documentapi {

class RemoveDocumentMessage : public TestAndSetMessage {
private:
    document::DocumentId _documentId; // The identifier of the document to remove.

protected:
    // Implements DocumentMessage.
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Convenience typedef.
     */
    typedef std::unique_ptr<RemoveDocumentMessage> UP;
    typedef std::shared_ptr<RemoveDocumentMessage> SP;

    /**
     * Constructs a new remove document message with no content.
     */
    RemoveDocumentMessage();

    /**
     * Constructs a new remove document message with a given document id.
     *
     * @param id The identifier of the document to remove.
     */
    RemoveDocumentMessage(const document::DocumentId& id);

    /**
     * Returns the identifier of the document to remove.
     *
     * @return The document id.
     */
    const document::DocumentId& getDocumentId() const;

    /**
     * Sets the identifier of the document to remove.
     *
     * @param documentId The document id to set.
     */
    void setDocumentId(const document::DocumentId& documentId);

    // Overrides DocumentMessage.
    bool hasSequenceId() const override;

    // Overrides DocumentMessage.
    uint64_t getSequenceId() const override;

    // Implements DocumentMessage.
    uint32_t getType() const override;

    string toString() const override { return "removedocumentmessage"; }
};

}

