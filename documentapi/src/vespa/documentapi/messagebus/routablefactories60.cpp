// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "routablefactories60.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/vespalib/objects/nbostream.h>

using document::FixedBucketSpaces;
using vespalib::nbostream;
using std::make_shared;
using std::make_unique;

namespace documentapi {

bool
RoutableFactories60::DocumentMessageFactory::encode(const mbus::Routable &obj, vespalib::GrowableByteBuffer &out) const
{
    const auto &msg = static_cast<const DocumentMessage&>(obj);
    out.putByte(msg.getPriority());
    out.putInt(0);  // LoadType
    return doEncode(msg, out);
}

mbus::Routable::UP
RoutableFactories60::DocumentMessageFactory::decode(document::ByteBuffer &in) const
{
    uint8_t pri;
    in.getByte(pri);
    (void) decodeInt(in);

    DocumentMessage::UP msg = doDecode(in);
    if (msg) {
        msg->setPriority((Priority::Value)pri);
    }

    return msg;
}

bool
RoutableFactories60::DocumentReplyFactory::encode(const mbus::Routable &obj, vespalib::GrowableByteBuffer &out) const
{
    const DocumentReply &msg = static_cast<const DocumentReply&>(obj);
    out.putByte(msg.getPriority());
    return doEncode(msg, out);
}

mbus::Routable::UP
RoutableFactories60::DocumentReplyFactory::decode(document::ByteBuffer &in) const
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
RoutableFactories60::CreateVisitorMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<CreateVisitorMessage>();

    msg->setLibraryName(decodeString(buf));
    msg->setInstanceId(decodeString(buf));
    msg->setControlDestination(decodeString(buf));
    msg->setDataDestination(decodeString(buf));
    msg->setDocumentSelection(decodeString(buf));
    msg->setMaximumPendingReplyCount(decodeInt(buf));

    int32_t len = decodeInt(buf);
    msg->getBuckets().reserve(len);
    for (int32_t i = 0; i < len; i++) {
        int64_t val;
        buf.getLong(val); // NOT using getLongNetwork
        msg->getBuckets().push_back(document::BucketId(val));
    }

    msg->setFromTimestamp(decodeLong(buf));
    msg->setToTimestamp(decodeLong(buf));
    msg->setVisitRemoves(decodeBoolean(buf));
    msg->setFieldSet(decodeString(buf));
    msg->setVisitInconsistentBuckets(decodeBoolean(buf));
    msg->getParameters().deserialize(buf);
    msg->setVisitorDispatcherVersion(50);
    decodeInt(buf); // Unused legacy visitor ordering
    msg->setMaxBucketsPerVisitor(decodeInt(buf));
    msg->setBucketSpace(decodeBucketSpace(buf));

    return msg;
}

bool
RoutableFactories60::CreateVisitorMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const CreateVisitorMessage&>(obj);

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
    buf.putString(msg.getFieldSet());
    buf.putBoolean(msg.visitInconsistentBuckets());


    msg.getParameters().serialize(buf);

    buf.putInt(0); // Unused legacy visitor ordering
    buf.putInt(msg.getMaxBucketsPerVisitor());
    return encodeBucketSpace(msg.getBucketSpace(), buf);
}

bool RoutableFactories60::CreateVisitorMessageFactory::encodeBucketSpace(
        vespalib::stringref bucketSpace,
        vespalib::GrowableByteBuffer& buf) const {
    doEncodeBucketSpace(bucketSpace, buf);
    return true;
}

string RoutableFactories60::CreateVisitorMessageFactory::decodeBucketSpace(document::ByteBuffer &buf) const {
    return doDecodeBucketSpace(buf);
}

DocumentMessage::UP
RoutableFactories60::DestroyVisitorMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<DestroyVisitorMessage>();
    msg->setInstanceId(decodeString(buf));
    return msg;
}

bool
RoutableFactories60::DestroyVisitorMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const DestroyVisitorMessage &msg = static_cast<const DestroyVisitorMessage&>(obj);
    buf.putString(msg.getInstanceId());
    return true;
}

DocumentReply::UP
RoutableFactories60::CreateVisitorReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<CreateVisitorReply>(DocumentProtocol::REPLY_CREATEVISITOR);
    reply->setLastBucket(document::BucketId((uint64_t)decodeLong(buf)));
    vdslib::VisitorStatistics vs;
    vs.setBucketsVisited(decodeInt(buf));
    vs.setDocumentsVisited(decodeLong(buf));
    vs.setBytesVisited(decodeLong(buf));
    vs.setDocumentsReturned(decodeLong(buf));
    vs.setBytesReturned(decodeLong(buf));
    decodeLong(buf); // was SecondPassDocumentsReturned
    decodeLong(buf); // was SecondPassBytesReturned
    reply->setVisitorStatistics(vs);
    return reply;
}

bool
RoutableFactories60::CreateVisitorReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &reply = static_cast<const CreateVisitorReply&>(obj);
    buf.putLong(reply.getLastBucket().getRawId());
    buf.putInt(reply.getVisitorStatistics().getBucketsVisited());
    buf.putLong(reply.getVisitorStatistics().getDocumentsVisited());
    buf.putLong(reply.getVisitorStatistics().getBytesVisited());
    buf.putLong(reply.getVisitorStatistics().getDocumentsReturned());
    buf.putLong(reply.getVisitorStatistics().getBytesReturned());
    buf.putLong(0); // was SecondPassDocumentsReturned
    buf.putLong(0); // was SecondPassBytesReturned
    return true;
}

DocumentReply::UP
RoutableFactories60::DestroyVisitorReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DESTROYVISITOR);
}

bool
RoutableFactories60::DestroyVisitorReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentReply::UP
RoutableFactories60::DocumentIgnoredReplyFactory::doDecode(document::ByteBuffer& ) const
{
    return std::make_unique<DocumentIgnoredReply>();
}

bool
RoutableFactories60::DocumentIgnoredReplyFactory::doEncode(const DocumentReply&, vespalib::GrowableByteBuffer& ) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories60::DocumentListMessageFactory::doDecode(document::ByteBuffer &buf) const
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
RoutableFactories60::DocumentListMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const DocumentListMessage&>(obj);

    buf.putLong(msg.getBucketId().getRawId());
    buf.putInt(msg.getDocuments().size());
    for (const auto & document : msg.getDocuments()) {
        document.serialize(buf);
    }

    return true;
}

DocumentReply::UP
RoutableFactories60::DocumentListReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DOCUMENTLIST);
}

bool
RoutableFactories60::DocumentListReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories60::DocumentSummaryMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<DocumentSummaryMessage>();

    msg->deserialize(buf);

    return msg;
}

bool
RoutableFactories60::DocumentSummaryMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const DocumentSummaryMessage &msg = static_cast<const DocumentSummaryMessage&>(obj);
    msg.serialize(buf);

    return true;
}

DocumentReply::UP
RoutableFactories60::DocumentSummaryReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_DOCUMENTSUMMARY);
}

bool
RoutableFactories60::DocumentSummaryReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories60::EmptyBucketsMessageFactory::doDecode(document::ByteBuffer &buf) const
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
RoutableFactories60::EmptyBucketsMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const EmptyBucketsMessage&>(obj);

    buf.putInt(msg.getBucketIds().size());
    for (const auto & bucketId : msg.getBucketIds()) {
        buf.putLong(bucketId.getRawId());
    }

    return true;
}

DocumentReply::UP
RoutableFactories60::EmptyBucketsReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_EMPTYBUCKETS);
}

bool
RoutableFactories60::EmptyBucketsReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

bool RoutableFactories60::GetBucketListMessageFactory::encodeBucketSpace(vespalib::stringref bucketSpace,
                                                                         vespalib::GrowableByteBuffer &buf) const
{
    doEncodeBucketSpace(bucketSpace, buf);
    return true;
}

string RoutableFactories60::GetBucketListMessageFactory::decodeBucketSpace(document::ByteBuffer &buf) const {
    return doDecodeBucketSpace(buf);
}

DocumentMessage::UP
RoutableFactories60::GetBucketListMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    document::BucketId bucketId(decodeLong(buf));
    auto msg = std::make_unique<GetBucketListMessage>(bucketId);
    msg->setBucketSpace(decodeBucketSpace(buf));
    return msg;
}

bool
RoutableFactories60::GetBucketListMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const GetBucketListMessage&>(obj);
    buf.putLong(msg.getBucketId().getRawId());
    return encodeBucketSpace(msg.getBucketSpace(), buf);
}

DocumentReply::UP
RoutableFactories60::GetBucketListReplyFactory::doDecode(document::ByteBuffer &buf) const
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
RoutableFactories60::GetBucketListReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &reply = static_cast<const GetBucketListReply&>(obj);

    const std::vector<GetBucketListReply::BucketInfo> &buckets = reply.getBuckets();
    buf.putInt(buckets.size());
    for (const auto & bucketInfo : buckets) {
        buf.putLong(bucketInfo._bucket.getRawId());
        buf.putString(bucketInfo._bucketInformation);
    }

    return true;
}

DocumentMessage::UP
RoutableFactories60::GetBucketStateMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<GetBucketStateMessage>();

    msg->setBucketId(document::BucketId((uint64_t)decodeLong(buf)));

    return msg;
}

bool
RoutableFactories60::GetBucketStateMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const GetBucketStateMessage&>(obj);
    buf.putLong(msg.getBucketId().getRawId());
    return true;
}

DocumentReply::UP
RoutableFactories60::GetBucketStateReplyFactory::doDecode(document::ByteBuffer &buf) const
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
RoutableFactories60::GetBucketStateReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetBucketStateReply &reply = static_cast<const GetBucketStateReply&>(obj);

    buf.putInt(reply.getBucketState().size());
    for (const auto & state : reply.getBucketState()) {
        state.serialize(buf);
    }

    return true;
}

DocumentMessage::UP
RoutableFactories60::GetDocumentMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    document::DocumentId docId = decodeDocumentId(buf);
    return std::make_unique<GetDocumentMessage>(docId, decodeString(buf));
}

bool
RoutableFactories60::GetDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const GetDocumentMessage &msg = static_cast<const GetDocumentMessage&>(obj);

    encodeDocumentId(msg.getDocumentId(), buf);
    buf.putString(msg.getFieldSet());
    return true;
}

namespace {

std::shared_ptr<document::Document>
decodeDocument(const document::DocumentTypeRepo & repo, document::ByteBuffer & buf) {
    vespalib::nbostream stream(buf.getBufferAtPos(), buf.getRemaining());
    auto doc = std::make_shared<document::Document>(repo, stream);
    buf.incPos(buf.getRemaining() - stream.size());
    return doc;
}

}
DocumentReply::UP
RoutableFactories60::GetDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<GetDocumentReply>();

    bool hasDocument = decodeBoolean(buf);
    document::Document * document = nullptr;
    if (hasDocument) {
        auto doc = decodeDocument(_repo, buf);
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
RoutableFactories60::GetDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
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
RoutableFactories60::MapVisitorMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<MapVisitorMessage>();
    msg->getData().deserialize(buf);
    return msg;
}

bool
RoutableFactories60::MapVisitorMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const MapVisitorMessage &msg = static_cast<const MapVisitorMessage&>(obj);
    msg.getData().serialize(buf);

    return true;
}

DocumentReply::UP
RoutableFactories60::MapVisitorReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_MAPVISITOR);
}

bool
RoutableFactories60::MapVisitorReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

void
RoutableFactories60::PutDocumentMessageFactory::decodeInto(PutDocumentMessage & msg, document::ByteBuffer & buf) const {
    msg.setDocument(decodeDocument(_repo, buf));
    msg.setTimestamp(static_cast<uint64_t>(decodeLong(buf)));
    decodeTasCondition(msg, buf);
}

bool
RoutableFactories60::PutDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    auto & msg = static_cast<const PutDocumentMessage &>(obj);
    nbostream stream;

    msg.getDocument().serialize(stream);
    buf.putBytes(stream.peek(), stream.size());
    buf.putLong(static_cast<int64_t>(msg.getTimestamp()));
    encodeTasCondition(buf, msg);

    return true;
}

DocumentReply::UP
RoutableFactories60::PutDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = make_unique<WriteDocumentReply>(DocumentProtocol::REPLY_PUTDOCUMENT);
    reply->setHighestModificationTimestamp(decodeLong(buf));
    return reply;
}

bool
RoutableFactories60::PutDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const WriteDocumentReply& reply = (const WriteDocumentReply&)obj;
    buf.putLong(reply.getHighestModificationTimestamp());
    return true;
}
        
void
RoutableFactories60::RemoveDocumentMessageFactory::decodeInto(RemoveDocumentMessage & msg, document::ByteBuffer & buf) const {
    msg.setDocumentId(decodeDocumentId(buf));
    decodeTasCondition(msg, buf);
}

bool
RoutableFactories60::RemoveDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const RemoveDocumentMessage &msg = static_cast<const RemoveDocumentMessage&>(obj);
    encodeDocumentId(msg.getDocumentId(), buf);
    encodeTasCondition(buf, msg);
    return true;
}

DocumentReply::UP
RoutableFactories60::RemoveDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<RemoveDocumentReply>();
    reply->setWasFound(decodeBoolean(buf));
    reply->setHighestModificationTimestamp(decodeLong(buf));
    return reply;
}

bool
RoutableFactories60::RemoveDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const RemoveDocumentReply &reply = static_cast<const RemoveDocumentReply&>(obj);
    buf.putBoolean(reply.getWasFound());
    buf.putLong(reply.getHighestModificationTimestamp());
    return true;
}

DocumentMessage::UP
RoutableFactories60::RemoveLocationMessageFactory::doDecode(document::ByteBuffer &buf) const
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
RoutableFactories60::RemoveLocationMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const RemoveLocationMessage &msg = static_cast<const RemoveLocationMessage&>(obj);
    buf.putString(msg.getDocumentSelection());
    return true;
}

DocumentReply::UP
RoutableFactories60::RemoveLocationReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<DocumentReply>(DocumentProtocol::REPLY_REMOVELOCATION);
}

bool
RoutableFactories60::RemoveLocationReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentMessage::UP
RoutableFactories60::SearchResultMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<SearchResultMessage>();
    msg->deserialize(buf);
    return msg;
}

bool
RoutableFactories60::SearchResultMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto & msg = static_cast<const SearchResultMessage&>(obj);
    msg.serialize(buf);

    return true;
}

DocumentMessage::UP
RoutableFactories60::QueryResultMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<QueryResultMessage>();
    msg->getSearchResult().deserialize(buf);
    msg->getDocumentSummary().deserialize(buf);

    return msg;
}

bool
RoutableFactories60::QueryResultMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const QueryResultMessage&>(obj);

    msg.getSearchResult().serialize(buf);
    msg.getDocumentSummary().serialize(buf);

    return true;
}

DocumentReply::UP
RoutableFactories60::SearchResultReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_SEARCHRESULT);
}

bool
RoutableFactories60::SearchResultReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentReply::UP
RoutableFactories60::QueryResultReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_QUERYRESULT);
}

bool
RoutableFactories60::QueryResultReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

bool RoutableFactories60::StatBucketMessageFactory::encodeBucketSpace(vespalib::stringref bucketSpace,
                                                                      vespalib::GrowableByteBuffer &buf) const
{
    doEncodeBucketSpace(bucketSpace, buf);
    return true;
}

string RoutableFactories60::StatBucketMessageFactory::decodeBucketSpace(document::ByteBuffer &buf) const {
    return doDecodeBucketSpace(buf);
}

DocumentMessage::UP
RoutableFactories60::StatBucketMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    auto msg = std::make_unique<StatBucketMessage>();

    msg->setBucketId(document::BucketId(decodeLong(buf)));
    msg->setDocumentSelection(decodeString(buf));
    msg->setBucketSpace(decodeBucketSpace(buf));

    return msg;
}

bool
RoutableFactories60::StatBucketMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const StatBucketMessage&>(obj);

    buf.putLong(msg.getBucketId().getRawId());
    buf.putString(msg.getDocumentSelection());
    return encodeBucketSpace(msg.getBucketSpace(), buf);
}

DocumentReply::UP
RoutableFactories60::StatBucketReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<StatBucketReply>();
    reply->setResults(decodeString(buf));
    return reply;
}

bool
RoutableFactories60::StatBucketReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &reply = static_cast<const StatBucketReply&>(obj);
    buf.putString(reply.getResults());
    return true;
}

DocumentMessage::UP
RoutableFactories60::StatDocumentMessageFactory::doDecode(document::ByteBuffer &) const
{
    return DocumentMessage::UP(); // TODO: remove message type
}

bool
RoutableFactories60::StatDocumentMessageFactory::doEncode(const DocumentMessage &, vespalib::GrowableByteBuffer &) const
{
    return false;
}

DocumentReply::UP
RoutableFactories60::StatDocumentReplyFactory::doDecode(document::ByteBuffer &) const
{
    return DocumentReply::UP(); // TODO: remove reply type
}

bool
RoutableFactories60::StatDocumentReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return false;
}

void
RoutableFactories60::UpdateDocumentMessageFactory::decodeInto(UpdateDocumentMessage & msg, document::ByteBuffer & buf) const {
    vespalib::nbostream stream(buf.getBufferAtPos(), buf.getRemaining());
    msg.setDocumentUpdate(document::DocumentUpdate::createHEAD(_repo, stream));
    buf.incPos(stream.rp());
    msg.setOldTimestamp(static_cast<uint64_t>(decodeLong(buf)));
    msg.setNewTimestamp(static_cast<uint64_t>(decodeLong(buf)));
    decodeTasCondition(msg, buf);
}

bool
RoutableFactories60::UpdateDocumentMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const UpdateDocumentMessage&>(obj);

    vespalib::nbostream stream;
    msg.getDocumentUpdate().serializeHEAD(stream);
    buf.putBytes(stream.peek(), stream.size());
    buf.putLong((int64_t)msg.getOldTimestamp());
    buf.putLong((int64_t)msg.getNewTimestamp());
    encodeTasCondition(buf, msg);

    return true;
}

DocumentReply::UP
RoutableFactories60::UpdateDocumentReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<UpdateDocumentReply>();
    reply->setWasFound(decodeBoolean(buf));
    reply->setHighestModificationTimestamp(decodeLong(buf));
    return reply;
}

bool
RoutableFactories60::UpdateDocumentReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &reply = static_cast<const UpdateDocumentReply&>(obj);
    buf.putBoolean(reply.getWasFound());
    buf.putLong(reply.getHighestModificationTimestamp());
    return true;
}

DocumentMessage::UP
RoutableFactories60::VisitorInfoMessageFactory::doDecode(document::ByteBuffer &buf) const
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
RoutableFactories60::VisitorInfoMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &msg = static_cast<const VisitorInfoMessage&>(obj);

    buf.putInt(msg.getFinishedBuckets().size());
    for (const auto & bucketId : msg.getFinishedBuckets()) {
        uint64_t val = bucketId.getRawId();
        buf.putBytes((const char*)&val, 8);
    }
    buf.putString(msg.getErrorMessage());

    return true;
}

DocumentReply::UP
RoutableFactories60::VisitorInfoReplyFactory::doDecode(document::ByteBuffer &) const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_VISITORINFO);
}

bool
RoutableFactories60::VisitorInfoReplyFactory::doEncode(const DocumentReply &, vespalib::GrowableByteBuffer &) const
{
    return true;
}

DocumentReply::UP
RoutableFactories60::WrongDistributionReplyFactory::doDecode(document::ByteBuffer &buf) const
{
    auto reply = std::make_unique<WrongDistributionReply>();
    reply->setSystemState(decodeString(buf));
    return reply;
}

bool
RoutableFactories60::WrongDistributionReplyFactory::doEncode(const DocumentReply &obj, vespalib::GrowableByteBuffer &buf) const
{
    const auto &reply = static_cast<const WrongDistributionReply&>(obj);
    buf.putString(reply.getSystemState());
    return true;
}

string
RoutableFactories60::decodeString(document::ByteBuffer &in)
{
    int32_t len = decodeInt(in);
    string ret = string(in.getBufferAtPos(), len);
    in.incPos(len);
    return ret;
}

bool
RoutableFactories60::decodeBoolean(document::ByteBuffer &in)
{
    char ret;
    in.getBytes(&ret, 1);
    return (bool)ret;
}

int32_t
RoutableFactories60::decodeInt(document::ByteBuffer &in)
{
    int32_t ret;
    in.getIntNetwork(ret);
    return ret;
}

int64_t
RoutableFactories60::decodeLong(document::ByteBuffer &in)
{
    int64_t ret;
    in.getLongNetwork(ret);
    return ret;
}

document::DocumentId
RoutableFactories60::decodeDocumentId(document::ByteBuffer &in)
{
    nbostream stream(in.getBufferAtPos(), in.getRemaining());
    document::DocumentId ret(stream);
    in.incPos(stream.rp());
    return ret;
}

void
RoutableFactories60::encodeDocumentId(const document::DocumentId &id, vespalib::GrowableByteBuffer &out)
{
    string str = id.toString();
    out.putBytes(str.c_str(), str.size() + 1);
}

void RoutableFactories60::decodeTasCondition(DocumentMessage & docMsg, document::ByteBuffer & buf) {
    auto & msg = static_cast<TestAndSetMessage &>(docMsg);
    msg.setCondition(TestAndSetCondition(decodeString(buf)));
}

void RoutableFactories60::encodeTasCondition(vespalib::GrowableByteBuffer & buf, const DocumentMessage & docMsg) {
    auto & msg = static_cast<const TestAndSetMessage &>(docMsg);
    buf.putString(msg.getCondition().getSelection());
}

void RoutableFactories60::doEncodeBucketSpace(
        vespalib::stringref bucketSpace,
        vespalib::GrowableByteBuffer& buf) {
    buf.putString(bucketSpace);
}
string RoutableFactories60::doDecodeBucketSpace(document::ByteBuffer& buf) {
    return decodeString(buf);
}

}
