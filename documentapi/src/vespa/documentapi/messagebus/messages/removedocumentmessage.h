// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "testandsetmessage.h"
#include <vespa/document/base/documentid.h>

namespace documentapi {

class RemoveDocumentMessage : public TestAndSetMessage {
private:
    document::DocumentId _documentId; // The identifier of the document to remove.
    uint64_t             _persisted_timestamp;

protected:
    // Implements DocumentMessage.
    DocumentReply::UP doCreateReply() const override;

public:
    using UP = std::unique_ptr<RemoveDocumentMessage>;
    using SP = std::shared_ptr<RemoveDocumentMessage>;

    /**
     * Constructs a new remove document message with no content.
     */
    RemoveDocumentMessage();

    /**
     * Constructs a new remove document message with a given document id.
     *
     * @param id The identifier of the document to remove.
     */
    explicit RemoveDocumentMessage(const document::DocumentId& id);

    ~RemoveDocumentMessage() override;

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

    void set_persisted_timestamp(uint64_t ts) noexcept { _persisted_timestamp = ts; }
    // When a visitor client receives a Remove as part of the visiting operation, this
    // timestamp represents the wall clock time of the tombstone's creation (i.e. the
    // time the original document was removed).
    // If zero, the content node was too old to support this feature.
    [[nodiscard]] uint64_t persisted_timestamp() const noexcept { return _persisted_timestamp; }

    // Overrides DocumentMessage.
    bool hasSequenceId() const override;

    // Overrides DocumentMessage.
    uint64_t getSequenceId() const override;

    // Implements DocumentMessage.
    uint32_t getType() const override;

    string toString() const override { return "removedocumentmessage"; }
};

}

