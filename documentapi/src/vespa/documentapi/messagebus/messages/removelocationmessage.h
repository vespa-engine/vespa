// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketselector.h>
#include <vespa/document/select/parser.h>

namespace documentapi {

/**
 * Message (VDS only) to remove an entire location for users using userdoc or groupdoc schemes for their documents.
 * A location in this context is either a user id or a group name.
 */
class RemoveLocationMessage : public DocumentMessage {
public:
    RemoveLocationMessage(const document::BucketIdFactory& factory, document::select::Parser& parser, const string& documentSelection);
    ~RemoveLocationMessage();

    const string& getDocumentSelection() const { return _documentSelection; }
    const document::BucketId& getBucketId() const { return _bucketId; };
    uint32_t getType() const override;
    string toString() const override { return "removelocationmessage"; }
protected:
    DocumentReply::UP doCreateReply() const override;

private:
    string _documentSelection;
    document::BucketId _bucketId;
};

}

