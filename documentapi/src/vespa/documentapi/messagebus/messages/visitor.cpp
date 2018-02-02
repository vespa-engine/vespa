// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitor.h"
#include <climits>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

using document::FixedBucketSpaces;

namespace documentapi {

CreateVisitorMessage::CreateVisitorMessage() :
    DocumentMessage(),
    _libName(),
    _instanceId(),
    _controlDestination(),
    _dataDestination(),
    _bucketSpace(FixedBucketSpaces::default_space_name()),
    _docSelection(),
    _maxPendingReplyCount(8),
    _buckets(),
    _fromTime(0),
    _toTime(0),
    _visitRemoves(false),
    _fieldSet("[all]"),
    _visitInconsistentBuckets(false),
    _params(),
    _version(42),
    _ordering(document::OrderingSpecification::ASCENDING),
    _maxBucketsPerVisitor(1)
{}

CreateVisitorMessage::CreateVisitorMessage(const string& libraryName,
                                           const string& instanceId,
                                           const string& controlDestination,
                                           const string& dataDestination) :
    DocumentMessage(),
    _libName(libraryName),
    _instanceId(instanceId),
    _controlDestination(controlDestination),
    _dataDestination(dataDestination),
    _bucketSpace(FixedBucketSpaces::default_space_name()),
    _docSelection(),
    _maxPendingReplyCount(8),
    _buckets(),
    _fromTime(0),
    _toTime(0),
    _visitRemoves(false),
    _fieldSet("[all]"),
    _visitInconsistentBuckets(false),
    _params(),
    _version(42),
    _ordering(document::OrderingSpecification::ASCENDING),
    _maxBucketsPerVisitor(1)
{}

CreateVisitorMessage::~CreateVisitorMessage() {}

DocumentReply::UP
CreateVisitorMessage::doCreateReply() const
{
    return DocumentReply::UP(new CreateVisitorReply(DocumentProtocol::REPLY_CREATEVISITOR));
}

uint32_t
CreateVisitorMessage::getType() const
{
    return DocumentProtocol::MESSAGE_CREATEVISITOR;
}

DestroyVisitorMessage::DestroyVisitorMessage() :
    DocumentMessage(),
    _instanceId()
{
}

DestroyVisitorMessage::DestroyVisitorMessage(const string& instanceId) :
    DocumentMessage(),
    _instanceId(instanceId)
{
}

DestroyVisitorMessage::~DestroyVisitorMessage() {
}

DocumentReply::UP
DestroyVisitorMessage::doCreateReply() const
{
    return DocumentReply::UP(new DocumentReply(DocumentProtocol::REPLY_DESTROYVISITOR));
}

uint32_t
DestroyVisitorMessage::getType() const
{
    return DocumentProtocol::MESSAGE_DESTROYVISITOR;
}

VisitorReply::VisitorReply(uint32_t type) :
    WriteDocumentReply(type)
{
    // empty
}

CreateVisitorReply::CreateVisitorReply(uint32_t type) :
    DocumentReply(type),
    _lastBucket(document::BucketId(INT_MAX))
{
}

VisitorInfoMessage::VisitorInfoMessage() :
    VisitorMessage(),
    _finishedBuckets(),
    _errorMessage()
{
}

VisitorInfoMessage::~VisitorInfoMessage() {
}

DocumentReply::UP
VisitorInfoMessage::doCreateReply() const
{
    return DocumentReply::UP(new VisitorReply(DocumentProtocol::REPLY_VISITORINFO));
}

uint32_t
VisitorInfoMessage::getType() const
{
    return DocumentProtocol::MESSAGE_VISITORINFO;
}

MapVisitorMessage::MapVisitorMessage() :
    _data()
{
    // empty
}

uint32_t
MapVisitorMessage::getApproxSize() const
{
    return _data.getSerializedSize();
}

DocumentReply::UP
MapVisitorMessage::doCreateReply() const
{
    return DocumentReply::UP(new VisitorReply(DocumentProtocol::REPLY_MAPVISITOR));
}

uint32_t MapVisitorMessage::getType() const
{
    return DocumentProtocol::MESSAGE_MAPVISITOR;
}

DocumentListMessage::Entry::Entry()
{
    // empty
}

DocumentListMessage::Entry::Entry(int64_t timestamp,
                                  document::Document::SP doc,
                                  bool removeEntry) :
    _timestamp(timestamp),
    _document(doc),
    _removeEntry(removeEntry)
{
    // empty
}

DocumentListMessage::Entry::Entry(const Entry& other) :
    _timestamp(other._timestamp),
    _document(other._document),
    _removeEntry(other._removeEntry)
{
    // empty
}

DocumentListMessage::Entry::Entry(const document::DocumentTypeRepo &repo,
                                  document::ByteBuffer& buf)
{
    buf.getLongNetwork(_timestamp);
    _document.reset(new document::Document(repo, buf));
    uint8_t b;
    buf.getByte(b);
    _removeEntry = b>0;
}

void
DocumentListMessage::Entry::serialize(document::ByteBuffer& buf) const
{
    buf.putLongNetwork(_timestamp);
    _document->serialize(buf);
    buf.putByte(_removeEntry ? 1 : 0);
}

uint32_t
DocumentListMessage::Entry::getSerializedSize() const
{
    return sizeof(int64_t) + sizeof(uint8_t)
        + _document->serialize()->getLength();
}

DocumentListMessage::DocumentListMessage() :
    _bucketId(),
    _documents()
{
    // empty
}

DocumentListMessage::DocumentListMessage(document::BucketId bid) :
    _bucketId(bid),
    _documents()
{
    // empty
}

DocumentReply::UP
DocumentListMessage::doCreateReply() const
{
    return DocumentReply::UP(new VisitorReply(DocumentProtocol::REPLY_DOCUMENTLIST));
}

uint32_t
DocumentListMessage::getType() const
{
    return DocumentProtocol::MESSAGE_DOCUMENTLIST;
}

}
