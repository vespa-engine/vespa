// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablefactories51.h"
#include <vespa/documentapi/documentapi.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/document/document.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::nbostream;

namespace documentapi {

bool
RoutableFactories51::DocumentMessageFactory::encode(const mbus::Routable &obj, vespalib::GrowableByteBuffer &out) const
{
    const DocumentMessage &msg = static_cast<const DocumentMessage&>(obj);
    out.putByte(msg.getPriority());
    out.putInt(msg.getLoadType().getId());
    return doEncode(msg, out);
}

mbus::Routable::UP
RoutableFactories51::DocumentMessageFactory::decode(document::ByteBuffer &in,
                                                  const LoadTypeSet& loadTypes) const
{
    uint8_t pri;
    in.getByte(pri);
    uint32_t loadClass = decodeInt(in);

    DocumentMessage::UP msg = doDecode(in);
    if (msg.get() != NULL) {
        msg->setPriority((Priority::Value)pri);
        msg->setLoadType(loadTypes[loadClass]);
    }

    return mbus::Routable::UP(msg.release());
}

bool
RoutableFactories51::DocumentReplyFactory::encode(const mbus::Routable &obj, vespalib::GrowableByteBuffer &out) const
{
    const DocumentReply &msg = static_cast<const DocumentReply&>(obj);
    out.putByte(msg.getPriority());
    return doEncode(msg, out);
}

mbus::Routable::UP
RoutableFactories51::DocumentReplyFactory::decode(document::ByteBuffer &in, const LoadTypeSet&) const
{
    uint8_t pri;
    in.getByte(pri);
    DocumentReply::UP reply = doDecode(in);
    if (reply.get() != NULL) {
        reply->setPriority((Priority::Value)pri);
    }
    return mbus::Routable::UP(reply.release());
}

DocumentMessage::UP
RoutableFactories51::CreateVisitorMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    DocumentMessage::UP ret(new CreateVisitorMessage());
    CreateVisitorMessage &msg = static_cast<CreateVisitorMessage&>(*ret);

    msg.setLibraryName(decodeString(buf));
    msg.setInstanceId(decodeString(buf));
    msg.setControlDestination(decodeString(buf));
    msg.setDataDestination(decodeString(buf));
    msg.setDocumentSelection(decodeString(buf));
    msg.setMaximumPendingReplyCount(decodeInt(buf));

    int32_t len = decodeInt(buf);
    msg.getBuckets().reserve(len);
    for (int32_t i = 0; i < len; i++) {
        int64_t val;
        buf.getLong(val); // NOT using getLongNetwork
        msg.getBuckets().push_back(document::BucketId(val));
    }

    msg.setFromTimestamp(decodeLong(buf));
    msg.setToTimestamp(decodeLong(buf));
    msg.setVisitRemoves(decodeBoolean(buf));
    msg.setFieldSet(decodeString(buf));
    msg.setVisitInconsistentBuckets(decodeBoolean(buf));
    msg.getParameters().deserialize(_repo, buf);
    msg.setVisitorDispatcherVersion(50);
    msg.setVisitorOrdering((document::OrderingSpecification::Order)decodeInt(buf));
    msg.setMaxBucketsPerVisitor(decodeInt(buf));

    return ret;
}

bool
RoutableFactories51::CreateVisitorMessageFactory::doEncode(const DocumentMessage &obj, vespalib::GrowableByteBuffer &buf) const
{
    const CreateVisitorMessage &msg = static_cast<const CreateVisitorMessage&>(obj);

    buf.putString(msg.getLibraryName());
    buf.putString(msg.getInstanceId());
    buf.putString(msg.getControlDestination());
    buf.putString(msg.getDataDestination());
    buf.putString(msg.getDocumentSelection());
    buf.putInt(msg.getMaximumPendingReplyCount());
    buf.putInt(msg.getBuckets().size());

    const std::vector<document::BucketId> &buckets = msg.getBuckets();
    for (std::vector<document::BucketId>::const_iterator it = buckets.begin();
         it != buckets.end(); ++it)
    {
        uint64_t val = it->getRawId();
        buf.putBytes((const char*)&val, 8);
    }

    buf.putLong(msg.getFromTimestamp());
    buf.putLong(msg.getToTimestamp());
    buf.putBoolean(msg.visitRemoves());
    buf.putString(msg.getFieldSet());
    buf.putBoolean(msg.visitInconsistentBuckets());

    int len = msg.getParameters().getSerializedSize();
    char *tmp = buf.allocate(len);
    document::ByteBuffer dbuf(tmp, len);
    msg.getParameters().serialize(dbuf);

    buf.putInt(msg.getVisitorOrdering());
    buf.putInt(msg.getMaxBucketsPerVisitor());

    return true;
}

DocumentMessage::UP
RoutableFactories51::GetDocumentMessageFactory::doDecode(document::ByteBuffer &buf) const
{
    return DocumentMessage::UP(
            new GetDocumentMessage(decodeDocumentId(buf),
                                   decodeString(buf)));
}

bool
RoutableFactories51::GetDocumentMessageFactory::doEncode(const DocumentMessage &obj,
                                                         vespalib::GrowableByteBuffer &buf) const
{
    const GetDocumentMessage &msg = static_cast<const GetDocumentMessage&>(obj);

    encodeDocumentId(msg.getDocumentId(), buf);
    buf.putString(msg.getFieldSet());
    return true;
}

DocumentReply::UP
RoutableFactories51::DocumentIgnoredReplyFactory::doDecode(document::ByteBuffer& buf) const
{
    (void) buf;
    return DocumentReply::UP(new DocumentIgnoredReply());
}

bool
RoutableFactories51::DocumentIgnoredReplyFactory::doEncode(
        const DocumentReply& obj,
        vespalib::GrowableByteBuffer& buf) const
{
    (void) obj;
    (void) buf;
    return true;
}

}
