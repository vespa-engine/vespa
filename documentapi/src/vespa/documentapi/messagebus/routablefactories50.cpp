// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablefactories50.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/select/parser.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/vespalib/objects/nbostream.h>

using document::FixedBucketSpaces;
using vespalib::nbostream;
using std::make_unique;
using std::make_shared;

namespace documentapi {

bool
RoutableFactories50::DocumentMessageFactory::encode(const mbus::Routable &obj, vespalib::GrowableByteBuffer &out) const
{
    const DocumentMessage &msg = static_cast<const DocumentMessage&>(obj);
    out.putByte(msg.getPriority());
    out.putInt(msg.getLoadType().getId());
    return doEncode(msg, out);
}

mbus::Routable::UP
RoutableFactories50::DocumentMessageFactory::decode(document::ByteBuffer &in, const LoadTypeSet& loadTypes) const
{
    uint8_t pri;
    in.getByte(pri);
    uint32_t loadClass = decodeInt(in);

    DocumentMessage::UP msg = doDecode(in);
    if (msg) {
        msg->setPriority((Priority::Value)pri);
        msg->setLoadType(loadTypes[loadClass]);
    }

    return mbus::Routable::UP(msg.release());
}

bool
RoutableFactories50::DocumentReplyFactory::encode(const mbus::Routable &obj, vespalib::GrowableByteBuffer &out) const
{
    const DocumentReply &msg = static_cast<const DocumentReply&>(obj);
    out.putByte(msg.getPriority());
    return doEncode(msg, out);
}

mbus::Routable::UP
RoutableFactories50::DocumentReplyFactory::decode(document::ByteBuffer &in, const LoadTypeSet&) const
{
    uint8_t pri;
    in.getByte(pri);
    DocumentReply::UP reply = doDecode(in);
    if (reply) {
        reply->setPriority((Priority::Value)pri);
    }
    return mbus::Routable::UP(reply.release());
}

////////////////////////////////////////////////////////////////////////////////
//
// Factories
//
////////////////////////////////////////////////////////////////////////////////

DocumentMessage::UP
RoutableFactories50::BatchDocumentUpdateMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    uint64_t userId = (uint64_t)decodeLong(buf);
    string group = decodeString(buf);

    auto msg = (group.length())
               ? std::make_unique<BatchDocumentUpdateMessage>(group)
               : std::make_unique<BatchDocumentUpdateMessage>(userId);

    uint32_t len = decodeInt(buf);
    for (uint32_t i = 0; i < len; i++) {
        document::DocumentUpdate::SP upd = document::DocumentUpdate::createHEAD(_repo, buf);
        msg->addUpdate(upd);
    }

    return msg;
}

bool
RoutableFactories50::BatchDocumentUpdateMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const BatchDocumentUpdateMessage &msg = static_cast<const BatchDocumentUpdateMessage&>(obj);

    buf.putLong(msg.getUserId());
    buf.putString(msg.getGroup());
    buf.putInt(msg.getUpdates().size());

    vespalib::nbostream stream;
    for (const auto & update : msg.getUpdates()) {
        update->serializeHEAD(stream);
    }
    buf.putBytes(stream.c_str(), stream.size());

    return true;
}

DocumentReply::UP
RoutableFactories50::BatchDocumentUpdateReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<BatchDocumentUpdateReply>();
    reply->setHighestModificationTimestamp(decodeLong(buf));
    std::vector<bool>& notFound = reply->getDocumentsNotFound();
    notFound.resize(decodeInt(buf));
    for (std::size_t i = 0; i < notFound.size(); ++i) {
        notFound[i] = decodeBoolean(buf);
    }
    return reply;
}

bool
RoutableFactories50::BatchDocumentUpdateReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const BatchDocumentUpdateReply& reply = static_cast<const BatchDocumentUpdateReply&>(obj);
    buf.putLong(reply.getHighestModificationTimestamp());
    const std::vector<bool>& notFoundV = reply.getDocumentsNotFound();
    buf.putInt(notFoundV.size());
    for (bool notFound : notFoundV) {
        buf.putBoolean(notFound);
    }
    return true;
}

DocumentMessage::UP
RoutableFactories50::CreateVisitorMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<CreateVisitorMessage>();

    msg->setLibraryName(decodeString(buf));
    msg->setInstanceId(decodeString(buf));
    msg->setControlDestination(decodeString(buf));
    msg->setDataDestination(decodeString(buf));
    msg->setDocumentSelection(decodeString(buf));
    msg->setMaximumPendingReplyCount(decodeInt(buf));

    int32_t len = decodeInt(buf);
    for (int32_t i = 0; i < len; i++) {
        int64_t val;
        buf.getLong(val); // NOT using getLongNetwork
        msg->getBuckets().push_back(document::BucketId(val));
    }

    msg->setFromTimestamp(decodeLong(buf));
    msg->setToTimestamp(decodeLong(buf));
    msg->setVisitRemoves(decodeBoolean(buf));
    msg->setVisitHeadersOnly(decodeBoolean(buf));
    msg->setVisitInconsistentBuckets(decodeBoolean(buf));
    msg->getParameters().deserialize(_repo, buf);
    msg->setVisitorDispatcherVersion(50);
    msg->setVisitorOrdering((document::OrderingSpecification::Order)decodeInt(buf));
    msg->setMaxBucketsPerVisitor(decodeInt(buf));

    return msg;
}

bool
RoutableFactories50::CreateVisitorMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const CreateVisitorMessage &msg = static_cast<const CreateVisitorMessage&>(obj);

    buf.putString(msg.getLibraryName());
    buf.putString(msg.getInstanceId());
    buf.putString(msg.getControlDestination());
    buf.putString(msg.getDataDestination());
    buf.putString(msg.getDocumentSelection());
    buf.putInt(msg.getMaximumPendingReplyCount());
    buf.putInt(msg.getBuckets().size());

    for (const auto & bucketId : msg.getBuckets()) {
        uint64_t val = bucketId.getRawId();
        buf.putBytes((const char*)&val, 8);
    }

    buf.putLong(msg.getFromTimestamp());
    buf.putLong(msg.getToTimestamp());
    buf.putBoolean(msg.visitRemoves());
    buf.putBoolean(msg.visitHeadersOnly());
    buf.putBoolean(msg.visitInconsistentBuckets());

    int len = msg.getParameters().getSerializedSize();
    char *tmp = buf.allocate(len);
    document::ByteBuffer dbuf(tmp, len);
    msg.getParameters().serialize(dbuf);

    buf.putInt(msg.getVisitorOrdering());
    buf.putInt(msg.getMaxBucketsPerVisitor());

    return true;
}

DocumentReply::UP
RoutableFactories50::CreateVisitorReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<CreateVisitorReply>(DocumentProtocol::REPLY_CREATEVISITOR);
    reply->setLastBucket(document::BucketId((uint64_t)decodeLong(buf)));
    vdslib::VisitorStatistics vs;
    vs.setBucketsVisited(decodeInt(buf));
    vs.setDocumentsVisited(decodeLong(buf));
    vs.setBytesVisited(decodeLong(buf));
    vs.setDocumentsReturned(decodeLong(buf));
    vs.setBytesReturned(decodeLong(buf));
    vs.setSecondPassDocumentsReturned(decodeLong(buf));
    vs.setSecondPassBytesReturned(decodeLong(buf));
    reply->setVisitorStatistics(vs);

    return reply;
}

bool
RoutableFactories50::CreateVisitorReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const CreateVisitorReply &reply = static_cast<const CreateVisitorReply&>(obj);
    buf.putLong(reply.getLastBucket().getRawId());
    buf.putInt(reply.getVisitorStatistics().getBucketsVisited());
    buf.putLong(reply.getVisitorStatistics().getDocumentsVisited());
    buf.putLong(reply.getVisitorStatistics().getBytesVisited());
    buf.putLong(reply.getVisitorStatistics().getDocumentsReturned());
    buf.putLong(reply.getVisitorStatistics().getBytesReturned());
    buf.putLong(reply.getVisitorStatistics().getSecondPassDocumentsReturned());
    buf.putLong(reply.getVisitorStatistics().getSecondPassBytesReturned());
    return true;
}

DocumentMessage::UP
RoutableFactories50::DestroyVisitorMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<DestroyVisitorMessage>();
    msg->setInstanceId(decodeString(buf));
    return msg;
}

bool
RoutableFactories50::DestroyVisitorMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const DestroyVisitorMessage &msg = static_cast<const DestroyVisitorMessage&>(obj);
    buf.putString(msg.getInstanceId());
    return true;
}

DocumentReply::UP
RoutableFactories50::DestroyVisitorReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DESTROYVISITOR);
}

bool
RoutableFactories50::DestroyVisitorReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories50::DocumentListMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<DocumentListMessage>();
    msg->setBucketId(document::BucketId(decodeLong(buf)));

    int32_t len = decodeInt(buf);
    for (int32_t i = 0; i < len; i++) {
        DocumentListMessage::Entry entry(_repo, buf);
        msg->getDocuments().push_back(entry);
    }

    return msg;
}

bool
RoutableFactories50::DocumentListMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const DocumentListMessage &msg = static_cast<const DocumentListMessage&>(obj);

    buf.putLong(msg.getBucketId().getRawId());
    buf.putInt(msg.getDocuments().size());
    for (const auto & document : msg.getDocuments()) {
        int len = document.getSerializedSize();
        char *tmp = buf.allocate(len);
        document::ByteBuffer dbuf(tmp, len);
        document.serialize(dbuf);
    }

    return true;
}

DocumentReply::UP
RoutableFactories50::DocumentListReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DOCUMENTLIST);
}

bool
RoutableFactories50::DocumentListReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories50::DocumentSummaryMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<DocumentSummaryMessage>();

    msg->deserialize(buf);

    return msg;
}

bool
RoutableFactories50::DocumentSummaryMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const DocumentSummaryMessage &msg = static_cast<const DocumentSummaryMessage&>(obj);

    int32_t len = msg.getSerializedSize();
    char *tmp = buf.allocate(len);
    document::ByteBuffer dbuf(tmp, len);
    msg.serialize(dbuf);

    return true;
}

DocumentReply::UP
RoutableFactories50::DocumentSummaryReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DOCUMENTSUMMARY);
}

bool
RoutableFactories50::DocumentSummaryReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories50::EmptyBucketsMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<EmptyBucketsMessage>();

    int32_t len = decodeInt(buf);
    std::vector<document::BucketId> buckets(len);
    for (int32_t i = 0; i < len; ++i) {
        buckets[i] = document::BucketId(decodeLong(buf));
    }
    msg->getBucketIds().swap(buckets);

    return msg;
}

bool
RoutableFactories50::EmptyBucketsMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const EmptyBucketsMessage &msg = static_cast<const EmptyBucketsMessage&>(obj);

    buf.putInt(msg.getBucketIds().size());
    for (const auto & bucketId : msg.getBucketIds()) {
        buf.putLong(bucketId.getRawId());
    }

    return true;
}

DocumentReply::UP
RoutableFactories50::EmptyBucketsReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_EMPTYBUCKETS);
}

bool
RoutableFactories50::EmptyBucketsReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

bool RoutableFactories50::GetBucketListMessageFactory::encodeBucketSpace(vespalib::stringref bucketSpace,
                                                                         vespalib::GrowableByteBuffer& ) const
{
    return (bucketSpace == FixedBucketSpaces::default_space_name());
}

string RoutableFactories50::GetBucketListMessageFactory::decodeBucketSpace(document::ByteBuffer&) const {
    return FixedBucketSpaces::default_space_name();
}

DocumentMessage::UP
RoutableFactories50::GetBucketListMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    document::BucketId bucketId(decodeLong(buf));
    auto msg = std::make_unique<GetBucketListMessage>(bucketId);
    msg->setBucketSpace(decodeBucketSpace(buf));
    return msg;
}

bool
RoutableFactories50::GetBucketListMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetBucketListMessage &msg = static_cast<const GetBucketListMessage&>(obj);
    buf.putLong(msg.getBucketId().getRawId());
    return encodeBucketSpace(msg.getBucketSpace(), buf);
}

DocumentReply::UP
RoutableFactories50::GetBucketListReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<GetBucketListReply>();

    int32_t len = decodeInt(buf);
    reply->getBuckets().reserve(len);
    for (int32_t i = 0; i < len; i++) {
        GetBucketListReply::BucketInfo info;
        info._bucket = document::BucketId((uint64_t)decodeLong(buf));
        info._bucketInformation = decodeString(buf);
        reply->getBuckets().push_back(info);
    }

    return reply;
}

bool
RoutableFactories50::GetBucketListReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetBucketListReply &reply = static_cast<const GetBucketListReply&>(obj);

    const std::vector<GetBucketListReply::BucketInfo> &buckets = reply.getBuckets();
    buf.putInt(buckets.size());
    for (const auto & bucketInfo : buckets) {
        buf.putLong(bucketInfo._bucket.getRawId());
        buf.putString(bucketInfo._bucketInformation);
    }

    return true;
}

DocumentMessage::UP
RoutableFactories50::GetBucketStateMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<GetBucketStateMessage>();

    msg->setBucketId(document::BucketId((uint64_t)decodeLong(buf)));

    return msg;
}

bool
RoutableFactories50::GetBucketStateMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetBucketStateMessage &msg = static_cast<const GetBucketStateMessage&>(obj);
    buf.putLong(msg.getBucketId().getRawId());
    return true;
}

DocumentReply::UP
RoutableFactories50::GetBucketStateReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<GetBucketStateReply>();

    int32_t len = decodeInt(buf);
    reply->getBucketState().reserve(len);
    for (int32_t i = 0; i < len; i++) {
        reply->getBucketState().emplace_back(buf);
    }

    return reply;
}

bool
RoutableFactories50::GetBucketStateReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetBucketStateReply &reply = static_cast<const GetBucketStateReply&>(obj);

    buf.putInt(reply.getBucketState().size());
    for (const auto & state : reply.getBucketState()) {
        state.serialize(buf);
    }

    return true;
}

DocumentMessage::UP
RoutableFactories50::GetDocumentMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<GetDocumentMessage>();
    msg->setDocumentId(decodeDocumentId(buf));
    msg->setFlags(decodeInt(buf));

    return msg;
}

bool
RoutableFactories50::GetDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetDocumentMessage &msg = static_cast<const GetDocumentMessage&>(obj);

    encodeDocumentId(msg.getDocumentId(), buf);
    buf.putInt(msg.getFlags());

    return true;
}

DocumentReply::UP
RoutableFactories50::GetDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<GetDocumentReply>();

    bool hasDocument = decodeBoolean(buf);
    document::Document * document = nullptr;
    if (hasDocument) {
        auto doc = std::make_shared<document::Document>(_repo, buf);
        document = doc.get();
        reply->setDocument(std::move(doc));
    }
    int64_t lastModified = decodeLong(buf);
    reply->setLastModified(lastModified);
    if (hasDocument) {
        document->setLastModified(lastModified);
    }

    return reply;
}

bool
RoutableFactories50::GetDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetDocumentReply &reply = static_cast<const GetDocumentReply&>(obj);

    buf.putByte(reply.hasDocument() ? 1 : 0);
    if (reply.hasDocument()) {
        nbostream stream;
        reply.getDocument().serialize(stream);
        buf.putBytes(stream.peek(), stream.size());
    }
    buf.putLong(reply.getLastModified());

    return true;
}

DocumentMessage::UP
RoutableFactories50::MapVisitorMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<MapVisitorMessage>();
    msg->getData().deserialize(_repo, buf);
    return msg;
}

bool
RoutableFactories50::MapVisitorMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const MapVisitorMessage &msg = static_cast<const MapVisitorMessage&>(obj);

    int32_t len = msg.getData().getSerializedSize();
    char *tmp = buf.allocate(len);
    document::ByteBuffer dbuf(tmp, len);
    msg.getData().serialize(dbuf);

    return true;
}

DocumentReply::UP
RoutableFactories50::MapVisitorReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_MAPVISITOR);
}

bool
RoutableFactories50::MapVisitorReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

void
RoutableFactories50::PutDocumentMessageFactory::decodeInto(PutDocumentMessage & msg, document::ByteBuffer & buf) const {
    msg.setDocument(make_shared<document::Document>(_repo, buf));
    msg.setTimestamp(static_cast<uint64_t>(decodeLong(buf)));
}

bool
RoutableFactories50::PutDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    auto & msg = static_cast<const PutDocumentMessage &>(obj);
    nbostream stream;

    msg.getDocument().serialize(stream);
    buf.putBytes(stream.peek(), stream.size());
    buf.putLong(static_cast<int64_t>(msg.getTimestamp()));

    return true;
}

DocumentReply::UP
RoutableFactories50::PutDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = make_unique<WriteDocumentReply>(DocumentProtocol::REPLY_PUTDOCUMENT);
    reply->setHighestModificationTimestamp(decodeLong(buf));

    // Doing an explicit move here to force converting result to an rvalue.
    // This is done automatically in GCC >= 5.
    return std::move(reply);
}

bool
RoutableFactories50::PutDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const WriteDocumentReply& reply = (const WriteDocumentReply&)obj;
    buf.putLong(reply.getHighestModificationTimestamp());
    return true;
}
        
void
RoutableFactories50::RemoveDocumentMessageFactory::decodeInto(RemoveDocumentMessage & msg, document::ByteBuffer & buf) const {
    msg.setDocumentId(decodeDocumentId(buf));
}

bool
RoutableFactories50::RemoveDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const RemoveDocumentMessage &msg = static_cast<const RemoveDocumentMessage&>(obj);
    encodeDocumentId(msg.getDocumentId(), buf);
    return true;
}

DocumentReply::UP
RoutableFactories50::RemoveDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<RemoveDocumentReply>();
    reply->setWasFound(decodeBoolean(buf));
    reply->setHighestModificationTimestamp(decodeLong(buf));
    return reply;
}

bool
RoutableFactories50::RemoveDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const RemoveDocumentReply &reply = static_cast<const RemoveDocumentReply&>(obj);
    buf.putBoolean(reply.getWasFound());
    buf.putLong(reply.getHighestModificationTimestamp());
    return true;
}

DocumentMessage::UP
RoutableFactories50::RemoveLocationMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    string selection = decodeString(buf);

    document::BucketIdFactory factory;
    document::select::Parser parser(_repo, factory);

    auto msg = std::make_unique<RemoveLocationMessage>(factory, parser, selection);
    // FIXME bucket space not part of wire format, implicitly limiting to only default space for now.
    msg->setBucketSpace(document::FixedBucketSpaces::default_space_name());
    return msg;
}

bool
RoutableFactories50::RemoveLocationMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const RemoveLocationMessage &msg = static_cast<const RemoveLocationMessage&>(obj);
    buf.putString(msg.getDocumentSelection());
    return true;
}

DocumentReply::UP
RoutableFactories50::RemoveLocationReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<DocumentReply>(DocumentProtocol::REPLY_REMOVELOCATION);
}

bool
RoutableFactories50::RemoveLocationReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories50::SearchResultMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<SearchResultMessage>();
    msg->deserialize(buf);
    return msg;
}

bool
RoutableFactories50::SearchResultMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const SearchResultMessage &msg = static_cast<const SearchResultMessage&>(obj);

    int len = msg.getSerializedSize();
    char *tmp = buf.allocate(len);
    document::ByteBuffer dbuf(tmp, len);
    msg.serialize(dbuf);

    return true;
}

DocumentMessage::UP
RoutableFactories50::QueryResultMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<QueryResultMessage>();
    msg->getSearchResult().deserialize(buf);
    msg->getDocumentSummary().deserialize(buf);

    return msg;
}

bool
RoutableFactories50::QueryResultMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const QueryResultMessage &msg = static_cast<const QueryResultMessage&>(obj);

    int len = msg.getSearchResult().getSerializedSize() + msg.getDocumentSummary().getSerializedSize();
    char *tmp = buf.allocate(len);
    document::ByteBuffer dbuf(tmp, len);
    msg.getSearchResult().serialize(dbuf);
    msg.getDocumentSummary().serialize(dbuf);

    return true;
}

DocumentReply::UP
RoutableFactories50::SearchResultReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_SEARCHRESULT);
}

bool
RoutableFactories50::SearchResultReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentReply::UP
RoutableFactories50::QueryResultReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_QUERYRESULT);
}

bool
RoutableFactories50::QueryResultReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

bool RoutableFactories50::StatBucketMessageFactory::encodeBucketSpace(vespalib::stringref bucketSpace,
                                                                      vespalib::GrowableByteBuffer& ) const
{
    return (bucketSpace == FixedBucketSpaces::default_space_name());
}

string RoutableFactories50::StatBucketMessageFactory::decodeBucketSpace(document::ByteBuffer&) const {
    return FixedBucketSpaces::default_space_name();
}

DocumentMessage::UP
RoutableFactories50::StatBucketMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<StatBucketMessage>();

    msg->setBucketId(document::BucketId(decodeLong(buf)));
    msg->setDocumentSelection(decodeString(buf));
    msg->setBucketSpace(decodeBucketSpace(buf));

    return msg;
}

bool
RoutableFactories50::StatBucketMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const StatBucketMessage &msg = static_cast<const StatBucketMessage&>(obj);

    buf.putLong(msg.getBucketId().getRawId());
    buf.putString(msg.getDocumentSelection());
    return encodeBucketSpace(msg.getBucketSpace(), buf);
}

DocumentReply::UP
RoutableFactories50::StatBucketReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<StatBucketReply>();
    reply->setResults(decodeString(buf));
    return reply;
}

bool
RoutableFactories50::StatBucketReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const StatBucketReply &reply = static_cast<const StatBucketReply&>(obj);
    buf.putString(reply.getResults());
    return true;
}

DocumentMessage::UP
RoutableFactories50::StatDocumentMessageFactory::doDecode(document::ByteBuffer &) const
{
    return DocumentMessage::UP(); // TODO: remove message type
}

bool
RoutableFactories50::StatDocumentMessageFactory::doEncode(const DocumentMessage &, vespalib::GrowableByteBuffer &) const
{
    return false;
}

DocumentReply::UP
RoutableFactories50::StatDocumentReplyFactory::doDecode(document::ByteBuffer &) const
{
    return DocumentReply::UP(); // TODO: remove reply type
}

bool
RoutableFactories50::StatDocumentReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return false;
}

void
RoutableFactories50::UpdateDocumentMessageFactory::decodeInto(UpdateDocumentMessage & msg, document::ByteBuffer & buf) const {
    msg.setDocumentUpdate(document::DocumentUpdate::createHEAD(_repo, buf));
    msg.setOldTimestamp(static_cast<uint64_t>(decodeLong(buf)));
    msg.setNewTimestamp(static_cast<uint64_t>(decodeLong(buf)));
}

bool
RoutableFactories50::UpdateDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const UpdateDocumentMessage &msg = static_cast<const UpdateDocumentMessage&>(obj);

    vespalib::nbostream stream;
    msg.getDocumentUpdate().serializeHEAD(stream);
    buf.putBytes(stream.peek(), stream.size());
    buf.putLong((int64_t)msg.getOldTimestamp());
    buf.putLong((int64_t)msg.getNewTimestamp());

    return true;
}

DocumentReply::UP
RoutableFactories50::UpdateDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<UpdateDocumentReply>();
    reply->setWasFound(decodeBoolean(buf));
    reply->setHighestModificationTimestamp(decodeLong(buf));
    return reply;
}

bool
RoutableFactories50::UpdateDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const UpdateDocumentReply &reply = static_cast<const UpdateDocumentReply&>(obj);
    buf.putBoolean(reply.getWasFound());
    buf.putLong(reply.getHighestModificationTimestamp());
    return true;
}

DocumentMessage::UP
RoutableFactories50::VisitorInfoMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<VisitorInfoMessage>();

    int32_t len = decodeInt(buf);
    msg->getFinishedBuckets().reserve(len);
    for (int32_t i = 0; i < len; i++) {
        int64_t val;
        buf.getLong(val); // NOT using getLongNetwork
        msg->getFinishedBuckets().emplace_back(val);
    }
    msg->setErrorMessage(decodeString(buf));

    return msg;
}

bool
RoutableFactories50::VisitorInfoMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const VisitorInfoMessage &msg = static_cast<const VisitorInfoMessage&>(obj);

    buf.putInt(msg.getFinishedBuckets().size());
    for (const auto & bucketId : msg.getFinishedBuckets()) {
        uint64_t val =bucketId.getRawId();
        buf.putBytes((const char*)&val, 8);
    }
    buf.putString(msg.getErrorMessage());

    return true;
}

DocumentReply::UP
RoutableFactories50::VisitorInfoReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_VISITORINFO);
}

bool
RoutableFactories50::VisitorInfoReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentReply::UP
RoutableFactories50::WrongDistributionReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<WrongDistributionReply>();
    reply->setSystemState(decodeString(buf));
    return reply;
}

bool
RoutableFactories50::WrongDistributionReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const WrongDistributionReply &reply = static_cast<const WrongDistributionReply&>(obj);
    buf.putString(reply.getSystemState());
    return true;
}

void
RoutableFactories50::FeedMessageFactory::myDecode(FeedMessage &msg, document::ByteBuffer &buf) const
{
    msg.setName(decodeString(buf));
    msg.setGeneration(decodeInt(buf));
    msg.setIncrement(decodeInt(buf));
}

void
RoutableFactories50::FeedMessageFactory::myEncode(const FeedMessage &msg, vespalib::GrowableByteBuffer &buf) const
{
    buf.putString(msg.getName());
    buf.putInt(msg.getGeneration());
    buf.putInt(msg.getIncrement());
}

DocumentReply::UP
RoutableFactories50::FeedReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<FeedReply>(getType());

    std::vector<FeedAnswer> &answers = reply->getFeedAnswers();
    int32_t len = decodeInt(buf);
    answers.reserve(len);
    for (int32_t i = 0; i < len; ++i) {
        int32_t typeCode = decodeInt(buf);
        int32_t wantedIncrement = decodeInt(buf);
        string recipient = decodeString(buf);
        string moreInfo = decodeString(buf);
        answers.emplace_back(typeCode, wantedIncrement, recipient, moreInfo);
    }
    return reply;
}

bool
RoutableFactories50::FeedReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const FeedReply &reply = static_cast<const FeedReply&>(obj);
    buf.putInt(reply.getFeedAnswers().size());
    for (const auto & answer : reply.getFeedAnswers()) {
        buf.putInt(answer.getAnswerCode());
        buf.putInt(answer.getWantedIncrement());
        buf.putString(answer.getRecipient());
        buf.putString(answer.getMoreInfo());
    }
    return true;
}

}
