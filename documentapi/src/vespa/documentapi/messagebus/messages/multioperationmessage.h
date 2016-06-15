// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdslib/container/parameters.h>
#include <vespa/vdslib/container/documentlist.h>
#include <vespa/vdslib/container/operationlist.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

/**
 * @class MultiOperationMessage
 * @ingroup message
 *
 * @brief Encapsulates a set of operations (PUT, REMOVE, UPDATE).
 */
class MultiOperationMessage : public VisitorMessage {
private:
    document::BucketId   _bucketId;
    std::vector<char>    _buffer;
    vdslib::DocumentList _operations;
    bool                 _keepTimeStamps;

protected:
    DocumentReply::UP doCreateReply() const;

public:
    typedef std::unique_ptr<MultiOperationMessage> UP;

    MultiOperationMessage(const document::DocumentTypeRepo::SP & repo, const document::BucketId& bucketId);
    MultiOperationMessage(const document::DocumentTypeRepo::SP & repo, const document::BucketId& bucketId, int bufferSize);
    MultiOperationMessage(const document::DocumentTypeRepo::SP & repo, const document::BucketId& bucketId,
                          const std::vector<char>& buffer, bool keepTimeStamps = false);
    MultiOperationMessage(const document::BucketId& bucketId, vdslib::DocumentList& docList, bool keepTimeStamps = false);

    static mbus::Message::UP create(const document::DocumentTypeRepo::SP & repo, const document::BucketId& bucketId, const vdslib::OperationList& operations);

    std::vector<char>& getBuffer() { return _buffer; }
    const std::vector<char>& getBuffer() const { return _buffer; }

    void setOperations(const document::DocumentTypeRepo::SP & repo, const std::vector<char>& buffer);
    void setOperations(vdslib::DocumentList& operations);
    const vdslib::DocumentList& getOperations() const { return _operations; }

    void serialize(document::ByteBuffer& buf) const;

    uint32_t getApproxSize() const;

    uint32_t getType() const;
    const document::BucketId& getBucketId() const { return _bucketId; }

    bool keepTimeStamps() const { return _keepTimeStamps;}
    void keepTimeStamps(bool b) { _keepTimeStamps = b;}

    string toString() const { return "multioperationmessage"; }

private:
    void verifyBucketId() const;
};

}

