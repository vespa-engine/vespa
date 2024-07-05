// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/bucket/bucketid.h>

namespace documentapi {

class StatBucketMessage : public DocumentMessage {
private:
    document::BucketId _bucketId;
    string _documentSelection;
    string _bucketSpace;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Constructs a new message with no content.
     */
    StatBucketMessage();

    /**
     * Constructs a new message with initial content.
     *
     * @param bucketId The bucket whose list to retrieve.
     */
    StatBucketMessage(document::BucketId bucket, const string& documentSelection);

    ~StatBucketMessage();

    /**
     * Returns the bucket to stat.
     *
     * @return The bucket id.
     */
    document::BucketId getBucketId() const { return _bucketId; }

    /**
     * Set the bucket to stat.
     *
     * @param bucketId The identifier to set.
     */
    void setBucketId(document::BucketId bucketId) { _bucketId = bucketId; };

    /**
     * Returns the document selection used to filter the documents
     * returned.
     *
     * @return The selection string.
     */
    const string &getDocumentSelection() const { return _documentSelection; };

    /**
     * Sets the document selection used to filter the documents returned.
     *
     * @param value The selection string to set.
     */
    void setDocumentSelection(const string &value) { _documentSelection = value; };

    const string &getBucketSpace() const { return _bucketSpace; }
    void setBucketSpace(const string &value) { _bucketSpace = value; }
    uint32_t getType() const override;
    string toString() const override { return "statbucketmessage"; }
};

}
