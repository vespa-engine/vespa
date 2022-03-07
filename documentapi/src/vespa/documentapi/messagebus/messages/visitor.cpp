// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitor.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/document.h>
#include <climits>

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
    _fieldSet(document::AllFields::NAME),
    _visitInconsistentBuckets(false),
    _params(),
    _version(42),
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
    _fieldSet(document::AllFields::NAME),
    _visitInconsistentBuckets(false),
    _params(),
    _version(42),
    _maxBucketsPerVisitor(1)
{}

CreateVisitorMessage::~CreateVisitorMessage() = default;

DocumentReply::UP
CreateVisitorMessage::doCreateReply() const
{
    return std::make_unique<CreateVisitorReply>(DocumentProtocol::REPLY_CREATEVISITOR);
}

uint32_t
CreateVisitorMessage::getType() const
{
    return DocumentProtocol::MESSAGE_CREATEVISITOR;
}

DestroyVisitorMessage::DestroyVisitorMessage() = default;

DestroyVisitorMessage::DestroyVisitorMessage(const string& instanceId) :
    DocumentMessage(),
    _instanceId(instanceId)
{
}

DestroyVisitorMessage::~DestroyVisitorMessage() = default;

DocumentReply::UP
DestroyVisitorMessage::doCreateReply() const
{
    return std::make_unique<DocumentReply>(DocumentProtocol::REPLY_DESTROYVISITOR);
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

VisitorInfoMessage::VisitorInfoMessage() = default;
VisitorInfoMessage::~VisitorInfoMessage() = default;

DocumentReply::UP
VisitorInfoMessage::doCreateReply() const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_VISITORINFO);
}

uint32_t
VisitorInfoMessage::getType() const
{
    return DocumentProtocol::MESSAGE_VISITORINFO;
}

MapVisitorMessage::MapVisitorMessage() = default;

uint32_t
MapVisitorMessage::getApproxSize() const
{
    return _data.getSerializedSize();
}

DocumentReply::UP
MapVisitorMessage::doCreateReply() const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_MAPVISITOR);
}

uint32_t MapVisitorMessage::getType() const
{
    return DocumentProtocol::MESSAGE_MAPVISITOR;
}

DocumentListMessage::Entry::Entry() = default;

DocumentListMessage::Entry::Entry(int64_t timestamp, document::Document::SP doc, bool removeEntry) :
    _timestamp(timestamp),
    _document(std::move(doc)),
    _removeEntry(removeEntry)
{ }

DocumentListMessage::Entry::Entry(const Entry& other) = default;

DocumentListMessage::Entry::Entry(const document::DocumentTypeRepo &repo, document::ByteBuffer& buf)
{
    buf.getLongNetwork(_timestamp);
    vespalib::nbostream stream(buf.getBufferAtPos(), buf.getRemaining());
    _document = std::make_unique<document::Document>(repo, stream);
    buf.incPos(buf.getRemaining() - stream.size());
    uint8_t b;
    buf.getByte(b);
    _removeEntry = b>0;
}

void
DocumentListMessage::Entry::serialize(vespalib::GrowableByteBuffer& buf) const
{
    buf.putLong(_timestamp);
    vespalib::nbostream nbo = _document->serialize();
    buf.putBytes(nbo.data(), nbo.size());
    buf.putByte(_removeEntry ? 1 : 0);
}

DocumentListMessage::DocumentListMessage() = default;

DocumentListMessage::DocumentListMessage(document::BucketId bid) :
    _bucketId(bid),
    _documents()
{
    // empty
}

DocumentReply::UP
DocumentListMessage::doCreateReply() const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DOCUMENTLIST);
}

uint32_t
DocumentListMessage::getType() const
{
    return DocumentProtocol::MESSAGE_DOCUMENTLIST;
}

}
