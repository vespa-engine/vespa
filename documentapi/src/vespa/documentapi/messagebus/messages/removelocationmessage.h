// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/bucket/bucketid.h>

namespace document::select { class Parser; }
namespace document { class BucketIdFactory; }

namespace documentapi {

/**
 * Message (VDS only) to remove an entire location for users using user or group schemes for their documents.
 * A location in this context is either a user id or a group name.
 */
class RemoveLocationMessage : public DocumentMessage {
public:
    RemoveLocationMessage(const document::BucketIdFactory& factory, document::select::Parser& parser, const string& documentSelection);
    ~RemoveLocationMessage();

    const string& getDocumentSelection() const { return _documentSelection; }
    const document::BucketId& getBucketId() const { return _bucketId; };
    const string &getBucketSpace() const { return _bucketSpace; }
    void setBucketSpace(const string &value) { _bucketSpace = value; }
    uint32_t getType() const override;
    string toString() const override { return "removelocationmessage"; }
protected:
    DocumentReply::UP doCreateReply() const override;

private:
    string _documentSelection;
    document::BucketId _bucketId;
    string _bucketSpace;
};

}

