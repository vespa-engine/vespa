// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multioperationmessage.h"
#include <vespa/vdslib/container/mutabledocumentlist.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/bucket/bucketidfactory.h>

namespace documentapi {

MultiOperationMessage::MultiOperationMessage(const document::DocumentTypeRepo::SP & repo, const document::BucketId& bucketId) :
    VisitorMessage(),
    _bucketId(bucketId),
    _buffer(0),
    _operations(repo, 0, 0),
    _keepTimeStamps(false)
{
}

MultiOperationMessage::MultiOperationMessage(const document::DocumentTypeRepo::SP & repo, const document::BucketId& bucketId, int bufferSize) :
    VisitorMessage(),
    _bucketId(bucketId),
    _buffer(bufferSize),
    _operations(repo, &_buffer[0], _buffer.size(), false),
    _keepTimeStamps(false)
{
}


MultiOperationMessage::MultiOperationMessage(const document::DocumentTypeRepo::SP & repo,
                                             const document::BucketId& bucketId,
                                             const std::vector<char> &buffer,
					     bool timeStamps) :
    VisitorMessage(),
    _bucketId(bucketId),
    _operations(repo, 0, 0),
    _keepTimeStamps(timeStamps)
{
    setOperations(repo, buffer);
}

MultiOperationMessage::MultiOperationMessage(const document::BucketId& bucketId,
                                             vdslib::DocumentList &operations,
                                             bool timeStamps) :
    VisitorMessage(),
    _bucketId(bucketId),
    _buffer(),
    _operations(operations.getTypeRepo(), 0, 0),
    _keepTimeStamps(timeStamps)
{
    _buffer.resize(operations.getBufferSize());
    memcpy(&_buffer[0], operations.getBuffer(), _buffer.size());
    _operations = vdslib::DocumentList(operations.getTypeRepo(), &_buffer[0], _buffer.size(), true);
}

MultiOperationMessage::~MultiOperationMessage() {
}

void
MultiOperationMessage::setOperations(const document::DocumentTypeRepo::SP & repo, const std::vector<char> &buffer)
{
    _buffer = buffer;
    if (_buffer.size() > 0) {
        _operations = vdslib::DocumentList(repo, &_buffer[0], _buffer.size(), true);
    }
    verifyBucketId();
}

void
MultiOperationMessage::setOperations(vdslib::DocumentList &operations)
{
    if (&_buffer[0] == operations.getBuffer()) {
        _buffer.resize(operations.getBufferSize());
    } else {
        _buffer.resize(operations.getBufferSize());
        memcpy(&_buffer[0], operations.getBuffer(), _buffer.size());
    }
    _operations = vdslib::DocumentList(operations.getTypeRepo(), &_buffer[0], _buffer.size(), true);
    verifyBucketId();
}

void
MultiOperationMessage::verifyBucketId() const {
    document::BucketIdFactory fac;

    for (vdslib::DocumentList::const_iterator iter = _operations.begin();
         iter != _operations.end();
         iter++) {
        document::DocumentId docId = iter->getDocumentId();
        document::BucketId bucketId = fac.getBucketId(docId);
        bucketId.setUsedBits(_bucketId.getUsedBits());
        if (bucketId != _bucketId) {
            throw vespalib::IllegalArgumentException(vespalib::make_string("Operations added to a MultiOperationMessage must belong to the specified bucketId. Document %s with bucket id %s does not match bucket id %s", docId.toString().c_str(), bucketId.toString().c_str(), _bucketId.toString().c_str()));
        }
    }
}

mbus::Message::UP
MultiOperationMessage::create(const document::DocumentTypeRepo::SP & repo, const document::BucketId& bucketId, const vdslib::OperationList &opl) {
    std::unique_ptr<MultiOperationMessage> msg(
            new MultiOperationMessage(repo, bucketId, opl.getRequiredBufferSize()));
    std::vector<char> &buf = msg->getBuffer();
    vdslib::MutableDocumentList mdl(repo, &(buf[0]), buf.size());
    if (! mdl.addOperationList(opl)) {
        abort();
    }
    msg->setOperations(mdl);
    return mbus::Message::UP(msg.release());
}

uint32_t
MultiOperationMessage::getApproxSize() const
{
    return _operations.getBufferSize();
}

DocumentReply::UP
MultiOperationMessage::doCreateReply() const
{
    return DocumentReply::UP(new VisitorReply(DocumentProtocol::REPLY_MULTIOPERATION));
}

uint32_t MultiOperationMessage::getType() const
{
    return DocumentProtocol::MESSAGE_MULTIOPERATION;
}

}
