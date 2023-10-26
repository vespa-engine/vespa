// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "testandsetmessage.h"

namespace document { class DocumentUpdate; }

namespace documentapi {

class UpdateDocumentMessage : public TestAndSetMessage {
private:
    using DocumentUpdateSP = std::shared_ptr<document::DocumentUpdate>;
    DocumentUpdateSP _documentUpdate;
    uint64_t         _oldTime;
    uint64_t         _newTime;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Convenience typedef.
     */
    using UP = std::unique_ptr<UpdateDocumentMessage>;
    using SP = std::shared_ptr<UpdateDocumentMessage>;

    /**
     * Constructs a new document message for deserialization.
     */
    UpdateDocumentMessage();
    ~UpdateDocumentMessage();

    /**
     * Constructs a new document update message.
     *
     * @param documentUpdate The document update to perform.
     */
    UpdateDocumentMessage(DocumentUpdateSP documentUpdate);

    /**
     * Returns the document update to perform.
     *
     * @return The update.
     */
    DocumentUpdateSP stealDocumentUpdate() const { return std::move(_documentUpdate); }
    const document::DocumentUpdate & getDocumentUpdate() const { return *_documentUpdate; }
    /**
     * Sets the document update to perform.
     *
     * @param documentUpdate The document update to set.
     */
    void setDocumentUpdate(DocumentUpdateSP documentUpdate);

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

    bool hasSequenceId() const override;
    uint64_t getSequenceId() const override;
    uint32_t getType() const override;
    string toString() const override { return "updatedocumentmessage"; }
};

}
