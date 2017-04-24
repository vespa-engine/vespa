// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/documentapi/messagebus/messages/testandsetmessage.h>

namespace documentapi {

class PutDocumentMessage : public TestAndSetMessage {
private:
    document::Document::SP _document;
    uint64_t _time;

protected:
    // Implements DocumentMessage.
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Convenience typedef.
     */
    typedef std::unique_ptr<PutDocumentMessage> UP;
    typedef std::shared_ptr<PutDocumentMessage> SP;

    /**
     * Constructs a new document message for deserialization.
     */
    PutDocumentMessage();

    /**
     * Constructs a new document put message.
     *
     * @param document The document to put.
     */
    PutDocumentMessage(document::Document::SP document);

    /**
     * Returns the document to put.
     *
     * @return The document.
     */
    document::Document::SP getDocument();

    /**
     * Returns the document to put.
     *
     * @return The document.
     */
    std::shared_ptr<const document::Document> getDocument() const;

    /**
     * Sets the document to put.
     *
     * @param document The document to set.
     */
    void setDocument(document::Document::SP document);

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
};

}

