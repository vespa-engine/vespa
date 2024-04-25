// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "testandsetmessage.h"
#include <optional>

namespace document { class DocumentUpdate; }

namespace documentapi {

class UpdateDocumentMessage : public TestAndSetMessage {
private:
    using DocumentUpdateSP = std::shared_ptr<document::DocumentUpdate>;
    DocumentUpdateSP    _documentUpdate;
    uint64_t            _oldTime;
    uint64_t            _newTime;
    std::optional<bool> _create_if_missing;

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
    ~UpdateDocumentMessage() override;

    /**
     * Constructs a new document update message.
     *
     * @param documentUpdate The document update to perform.
     */
    explicit UpdateDocumentMessage(DocumentUpdateSP documentUpdate);

    /**
     * Returns the document update to perform.
     *
     * @return The update.
     */
    [[nodiscard]] DocumentUpdateSP stealDocumentUpdate() const { return std::move(_documentUpdate); }
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
    [[nodiscard]] uint64_t getOldTimestamp() const noexcept { return _oldTime; }

    /**
     * Sets the timestamp required for this update to be applied.
     *
     * @param time The timestamp to set.
     */
    void setOldTimestamp(uint64_t time) noexcept { _oldTime = time; }

    /**
     * Returns the timestamp to assign to the updated document.
     *
     * @return The document timestamp.
     */
    [[nodiscard]] uint64_t getNewTimestamp() const noexcept { return _newTime; }

    /**
     * Sets the timestamp to assign to the updated document.
     *
     * @param time The timestamp to set.
     */
    void setNewTimestamp(uint64_t time) { _newTime = time; }

    void set_cached_create_if_missing(bool create) noexcept {
        _create_if_missing = create;
    }

    [[nodiscard]] bool has_cached_create_if_missing() const noexcept {
        return _create_if_missing.has_value();
    }
    // Note: iff has_cached_create_if_missing() == false, this will trigger a deserialization of the
    // underlying DocumentUpdate instance, which might throw an exception on deserialization failure.
    // Otherwise, this is noexcept.
    [[nodiscard]] bool create_if_missing() const;

    bool hasSequenceId() const override;
    uint64_t getSequenceId() const override;
    uint32_t getType() const override;
    string toString() const override { return "updatedocumentmessage"; }
};

}
