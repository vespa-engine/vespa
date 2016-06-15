// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/update/documentupdate.h>
#include <vespa/documentapi/messagebus/messages/testandsetmessage.h>

namespace documentapi {

class UpdateDocumentMessage : public TestAndSetMessage {
private:
    document::DocumentUpdate::SP _documentUpdate;
    uint64_t                                    _oldTime;
    uint64_t                                    _newTime;

protected:
    // Implements DocumentMessage.
    DocumentReply::UP doCreateReply() const;

public:
    /**
     * Convenience typedef.
     */
    typedef std::unique_ptr<UpdateDocumentMessage> UP;
    typedef std::shared_ptr<UpdateDocumentMessage> SP;

    /**
     * Constructs a new document message for deserialization.
     */
    UpdateDocumentMessage();

    /**
     * Constructs a new document update message.
     *
     * @param documentUpdate The document update to perform.
     */
    UpdateDocumentMessage(document::DocumentUpdate::SP documentUpdate);

    /**
     * Returns the document update to perform.
     *
     * @return The update.
     */
    document::DocumentUpdate::SP getDocumentUpdate();

    /**
     * Returns the document update to perform.
     *
     * @return The update.
     */
    std::shared_ptr<const document::DocumentUpdate> getDocumentUpdate() const;

    /**
     * Sets the document update to perform.
     *
     * @param documentUpdate The document update to set.
     */
    void setDocumentUpdate(document::DocumentUpdate::SP documentUpdate);

    /**
     * Returns the timestamp required for this update to be applied.
     *
     * @return The document timestamp.
     */
    uint64_t getOldTimestamp() const { return _oldTime; }

    /**
     * Sets the timestamp required for this update to be applied.
     *
     * @param time The timestamp to set.
     */
    void setOldTimestamp(uint64_t time) { _oldTime = time; }

    /**
     * Returns the timestamp to assign to the updated document.
     *
     * @return The document timestamp.
     */
    uint64_t getNewTimestamp() const { return _newTime; }

    /**
     * Sets the timestamp to assign to the updated document.
     *
     * @param time The timestamp to set.
     */
    void setNewTimestamp(uint64_t time) { _newTime = time; }

    // Overrides DocumentMessage.
    bool hasSequenceId() const;

    // Overrides DocumentMessage.
    uint64_t getSequenceId() const;

    // Implements DocumentMessage.
    uint32_t getType() const;

    string toString() const { return "updatedocumentmessage"; }
};

}

