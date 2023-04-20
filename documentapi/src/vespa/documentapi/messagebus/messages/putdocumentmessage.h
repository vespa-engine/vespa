// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "testandsetmessage.h"

namespace document { class Document; }
namespace documentapi {

class PutDocumentMessage : public TestAndSetMessage {
private:
    using DocumentSP = std::shared_ptr<document::Document>;
    DocumentSP _document;
    uint64_t   _time;
    bool _create_if_non_existent = false;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    using UP = std::unique_ptr<PutDocumentMessage>;
    using SP = std::shared_ptr<PutDocumentMessage>;

    /**
     * Constructs a new document message for deserialization.
     */
    PutDocumentMessage();

    /**
     * Constructs a new document put message.
     *
     * @param document The document to put.
     */
    PutDocumentMessage(DocumentSP document);
    ~PutDocumentMessage();

    /**
     * Returns the document to put.
     *
     * @return The document.
     */
    const DocumentSP & getDocumentSP() const { return _document; }
    DocumentSP stealDocument() { return std::move(_document); }
    const document::Document & getDocument() const { return *_document; }

    /**
     * Sets the document to put.
     *
     * @param document The document to set.
     */
    void setDocument(DocumentSP document);

    /**
     * Returns the timestamp of the document to put.
     *
     * @return The document timestamp.
     */
    uint64_t getTimestamp() const { return _time; }

    /**
     * Sets the timestamp of the document to put.
     *
     * @param time The timestamp to set.
     */
    void setTimestamp(uint64_t time) { _time = time; }
    bool hasSequenceId() const override;
    uint64_t getSequenceId() const override;
    uint32_t getType() const override;
    string toString() const override { return "putdocumentmessage"; }

    void set_create_if_non_existent(bool value) noexcept { _create_if_non_existent = value; }
    bool get_create_if_non_existent() const noexcept { return _create_if_non_existent; }
};

}
