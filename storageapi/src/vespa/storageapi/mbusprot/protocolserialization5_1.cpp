// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protocolserialization5_1.h"
#include "serializationhelper.h"
#include "storagecommand.h"
#include "storagereply.h"
#include "storageprotocol.h"

#include <vespa/messagebus/blob.h>
#include <vespa/messagebus/blobref.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/document/select/orderingspecification.h>
#include <vespa/storageapi/messageapi/returncode.h>

namespace storage {
namespace mbusprot {

api::BucketInfo
ProtocolSerialization5_1::getBucketInfo(document::ByteBuffer& buf) const
{
    uint64_t lastModified(SH::getLong(buf));
    uint32_t crc(SH::getInt(buf));
    uint32_t doccount(SH::getInt(buf));
    uint32_t docsize(SH::getInt(buf));
    uint32_t metacount(SH::getInt(buf));
    uint32_t usedsize(SH::getInt(buf));
    uint8_t flags(SH::getByte(buf));
    bool ready = (flags & BUCKET_READY) != 0;
    bool active = (flags & BUCKET_ACTIVE) != 0;
    return api::BucketInfo(crc, doccount, docsize,
                           metacount, usedsize,
                           ready, active, lastModified);
}

void
ProtocolSerialization5_1::putBucketInfo(
        const api::BucketInfo& info, vespalib::GrowableByteBuffer& buf) const
{
    buf.putLong(info.getLastModified());
    buf.putInt(info.getChecksum());
    buf.putInt(info.getDocumentCount());
    buf.putInt(info.getTotalDocumentSize());
    buf.putInt(info.getMetaCount());
    buf.putInt(info.getUsedFileSize());
    uint8_t flags = (info.isReady() ? BUCKET_READY : 0) |
                    (info.isActive() ? BUCKET_ACTIVE : 0);
    buf.putByte(flags);
}

ProtocolSerialization5_1::ProtocolSerialization5_1(
        const document::DocumentTypeRepo::SP& repo,
        const documentapi::LoadTypeSet& loadTypes)
    : ProtocolSerialization5_0(repo, loadTypes)
{
}

void ProtocolSerialization5_1::onEncode(
        GBBuf& buf, const api::SetBucketStateCommand& msg) const
{
    buf.putLong(msg.getBucketId().getRawId());
    buf.putByte(static_cast<uint8_t>(msg.getState()));
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization5_1::onDecodeSetBucketStateCommand(BBuf& buf) const
{
    document::BucketId bucket(SH::getLong(buf));
    api::SetBucketStateCommand::BUCKET_STATE state(
            static_cast<api::SetBucketStateCommand::BUCKET_STATE>(
                    SH::getByte(buf)));
    api::SetBucketStateCommand::UP msg(
            new api::SetBucketStateCommand(bucket, state));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization5_1::onEncode(
        GBBuf& buf, const api::SetBucketStateReply& msg) const
{
    onEncodeBucketReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization5_1::onDecodeSetBucketStateReply(const SCmd& cmd,
                                                      BBuf& buf) const
{
    api::SetBucketStateReply::UP msg(new api::SetBucketStateReply(
                static_cast<const api::SetBucketStateCommand&>(cmd)));
    onDecodeBucketReply(buf, *msg);
    return api::StorageReply::UP(msg.release());
}

void ProtocolSerialization5_1::onEncode(
        GBBuf& buf, const api::GetCommand& msg) const
{
    buf.putString(msg.getDocumentId().toString());
    buf.putLong(msg.getBucketId().getRawId());
    buf.putLong(msg.getBeforeTimestamp());
    buf.putString(msg.getFieldSet());
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization5_1::onDecodeGetCommand(BBuf& buf) const
{
    document::DocumentId did(SH::getString(buf));
    document::BucketId bucket(SH::getLong(buf));
    api::Timestamp beforeTimestamp(SH::getLong(buf));
    std::string fieldSet(SH::getString(buf));
    api::GetCommand::UP msg(
            new api::GetCommand(bucket, did, fieldSet, beforeTimestamp));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void
ProtocolSerialization5_1::onEncode(
        GBBuf& buf, const api::CreateVisitorCommand& msg) const
{
    buf.putString(msg.getLibraryName());
    buf.putString(msg.getInstanceId());
    buf.putString(msg.getDocumentSelection());
    buf.putInt(msg.getVisitorCmdId());
    buf.putString(msg.getControlDestination());
    buf.putString(msg.getDataDestination());
    buf.putInt(msg.getMaximumPendingReplyCount());
    buf.putLong(msg.getFromTime());
    buf.putLong(msg.getToTime());

    buf.putInt(msg.getBuckets().size());
    for (uint32_t i = 0; i < msg.getBuckets().size(); i++) {
        buf.putLong(msg.getBuckets()[i].getRawId());
    }

    buf.putBoolean(msg.visitRemoves());
    buf.putString(msg.getFieldSet());
    buf.putBoolean(msg.visitInconsistentBuckets());
    buf.putInt(msg.getQueueTimeout());

    uint32_t size = msg.getParameters().getSerializedSize();
    char* docBuffer = buf.allocate(size);
    document::ByteBuffer bbuf(docBuffer, size);
    msg.getParameters().serialize(bbuf);

    onEncodeCommand(buf, msg);

    buf.putInt(msg.getVisitorOrdering());
    buf.putInt(msg.getMaxBucketsPerVisitor());
}

api::StorageCommand::UP
ProtocolSerialization5_1::onDecodeCreateVisitorCommand(BBuf& buf) const
{
    vespalib::stringref libraryName = SH::getString(buf);
    vespalib::stringref instanceId = SH::getString(buf);
    vespalib::stringref selection = SH::getString(buf);
    api::CreateVisitorCommand::UP msg(
            new api::CreateVisitorCommand(libraryName, instanceId, selection));
    msg->setVisitorCmdId(SH::getInt(buf));
    msg->setControlDestination(SH::getString(buf));
    msg->setDataDestination(SH::getString(buf));
    msg->setMaximumPendingReplyCount(SH::getInt(buf));

    msg->setFromTime(SH::getLong(buf));
    msg->setToTime(SH::getLong(buf));
    uint32_t count = SH::getInt(buf);

    if (count > buf.getRemaining()) {
            // Trigger out of bounds exception rather than out of memory error
        buf.incPos(count);
    }

    for (uint32_t i = 0; i < count; i++) {
        msg->getBuckets().push_back(document::BucketId(SH::getLong(buf)));
    }

    if (SH::getBoolean(buf)) {
        msg->setVisitRemoves();
    }

    msg->setFieldSet(SH::getString(buf));

    if (SH::getBoolean(buf)) {
        msg->setVisitInconsistentBuckets();
    }
    msg->setQueueTimeout(SH::getInt(buf));
    msg->getParameters().deserialize(getTypeRepo(), buf);

    onDecodeCommand(buf, *msg);
    msg->setVisitorOrdering(
            (document::OrderingSpecification::Order)SH::getInt(buf));
    msg->setMaxBucketsPerVisitor(SH::getInt(buf));
    msg->setVisitorDispatcherVersion(50);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization5_1::onEncode(
        GBBuf& buf, const api::CreateBucketCommand& msg) const
{
    buf.putLong(msg.getBucketId().getRawId());
    buf.putBoolean(msg.getActive());
    onEncodeBucketInfoCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization5_1::onDecodeCreateBucketCommand(BBuf& buf) const
{
    document::BucketId bid(SH::getLong(buf));
    bool setActive = SH::getBoolean(buf);
    api::CreateBucketCommand::UP msg(new api::CreateBucketCommand(bid));
    msg->setActive(setActive);
    onDecodeBucketInfoCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}


} // mbusprot
} // storage
