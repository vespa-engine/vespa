// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/base/documentid.h>

namespace documentapi {

class GetDocumentMessage : public DocumentMessage {
private:
    document::DocumentId    _documentId; // The identifier of the document to retrieve.
    string                  _fieldSet; // Comma-separated list of fields to return
    std::optional<uint16_t> _debug_replica_node_id;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Convenience typedef.
     */
    using UP = std::unique_ptr<GetDocumentMessage>;
    using SP = std::shared_ptr<GetDocumentMessage>;

    /**
     * Constructs a new message for deserialization.
     */
    GetDocumentMessage();

    /**
     * Constructs a new document get message.
     *
     * @param documentId The identifier of the document to retrieve.
     */
    explicit GetDocumentMessage(const document::DocumentId &documentId);

    /**
     * Constructs a new document get message.
     *
     * @param documentId The identifier of the document to retrieve.
     * @param fieldSet The fields to retrieve (comma-separated)
     */
    GetDocumentMessage(const document::DocumentId &documentId, std::string_view fieldSet);

    ~GetDocumentMessage();

    /**
     * Returns the identifier of the document to retrieve.
     *
     * @return The document id.
     */
    const document::DocumentId &getDocumentId() const;

    /**
     * Sets the identifier of the document to retrieve.
     *
     * @param documentId The document id to set.
     */
    void setDocumentId(const document::DocumentId &documentId);

    /**
     * Returns the fields to be retrieved by the get.
     */
    const string& getFieldSet() const { return _fieldSet; }

    [[nodiscard]] std::optional<uint16_t> debug_replica_node_id() const noexcept { return _debug_replica_node_id; }
    void set_debug_replica_node_id(std::optional<uint16_t> node_id) noexcept { _debug_replica_node_id = node_id; }
    [[nodiscard]] bool has_debug_replica_node_id() const noexcept { return _debug_replica_node_id.has_value(); }

    uint32_t getType() const override;
    string toString() const override { return "getdocumentmessage"; }
};

}
