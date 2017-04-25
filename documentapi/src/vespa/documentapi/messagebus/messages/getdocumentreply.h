// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentacceptedreply.h"
#include <vespa/document/fieldvalue/document.h>

namespace documentapi {

class GetDocumentReply : public DocumentAcceptedReply {
private:
    document::Document::SP _document;
    uint64_t               _lastModified;

public:
    /**
     * Convenience typedef.
     */
    typedef std::unique_ptr<GetDocumentReply> UP;
    typedef std::shared_ptr<GetDocumentReply> SP;

    /**
     * Constructs a new reply for deserialization.
     */
    GetDocumentReply();

    /**
     * Constructs a new document get reply.
     *
     * @param document The document requested.
     */
    GetDocumentReply(document::Document::SP document);

    /**
     * Returns the document retrieved.
     *
     * @return The document.
     */
    document::Document::SP getDocument();

    /**
     * Returns the document retrieved.
     *
     * @return The document.
     */
    std::shared_ptr<const document::Document> getDocument() const;

    /**
     * Sets the document retrieved.
     *
     * @param document The document.
     */
    void setDocument(document::Document::SP document);

    /**
     * Returns the date the document was last modified.
     *
     * @return The date.
     */
    uint64_t getLastModified() const { return _lastModified; };

    /**
     * Set the date the document was last modified.
     *
     * @param lastModified The date.
     */
    void setLastModified(uint64_t lastModified);

    string toString() const override { return "getdocumentreply"; }
};

}

