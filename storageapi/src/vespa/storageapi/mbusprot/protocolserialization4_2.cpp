// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protocolserialization4_2.h"
#include "oldreturncodemapper.h"
#include "serializationhelper.h"
#include "storagecommand.h"
#include "storagereply.h"

#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".storage.api.mbusprot.serialization.4_2");

using document::BucketSpace;

namespace storage::mbusprot {

ProtocolSerialization4_2::ProtocolSerialization4_2(
        const std::shared_ptr<const document::DocumentTypeRepo>& repo)
    : ProtocolSerialization(repo)
{
}

void
ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::BatchPutRemoveCommand& msg) const
{
    // Serialization format - allow different types of serialization depending on source.
    buf.putByte(0);
    putBucket(msg.getBucket(), buf);
    buf.putInt(msg.getOperationCount());

    for (uint32_t i = 0; i < msg.getOperationCount(); i++) {
        const api::BatchPutRemoveCommand::Operation& op = msg.getOperation(i);
        buf.putByte((uint8_t)op.type);
        buf.putLong(op.timestamp);

        switch (op.type) {
        case api::BatchPutRemoveCommand::Operation::REMOVE:
            buf.putString(static_cast<const api::BatchPutRemoveCommand::RemoveOperation&>(op).documentId.toString());
            break;
        case api::BatchPutRemoveCommand::Operation::HEADERUPDATE:
        {
            buf.putLong(static_cast<const api::BatchPutRemoveCommand::HeaderUpdateOperation&>(op).timestampToUpdate);

            vespalib::nbostream stream;
            static_cast<const api::BatchPutRemoveCommand::HeaderUpdateOperation&>(op).document->serializeHeader(stream);
            buf.putInt(stream.size());
            buf.putBytes(stream.peek(), stream.size());
            break;
        }
        case api::BatchPutRemoveCommand::Operation::PUT:
            SH::putDocument(static_cast<const api::BatchPutRemoveCommand::PutOperation&>(op).document.get(), buf);
            break;
        }
    }
    onEncodeBucketInfoCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeBatchPutRemoveCommand(BBuf& buf) const
{
    SH::getByte(buf);
    document::Bucket bucket = getBucket(buf);
    std::unique_ptr<api::BatchPutRemoveCommand> cmd(new api::BatchPutRemoveCommand(bucket));
    int length = SH::getInt(buf);

    for (int i = 0; i < length; i++) {
        int type = SH::getByte(buf);
        long timestamp = SH::getLong(buf);

        switch (type) {
        case api::BatchPutRemoveCommand::Operation::REMOVE:
            cmd->addRemove(document::DocumentId(SH::getString(buf)), timestamp);
            break;
        case api::BatchPutRemoveCommand::Operation::HEADERUPDATE:
        {
            long newTimestamp = SH::getLong(buf);
            cmd->addHeaderUpdate(document::Document::SP(
                            SH::getDocument(buf, getTypeRepo())),
                                 timestamp, newTimestamp);
            break;
        }
        case api::BatchPutRemoveCommand::Operation::PUT:
            cmd->addPut(document::Document::SP(SH::getDocument(
                                    buf, getTypeRepo())), timestamp);
            break;
        }
    }

    onDecodeBucketInfoCommand(buf, *cmd);

    return api::StorageCommand::UP(cmd.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::BatchPutRemoveReply& msg) const
{
    buf.putInt(msg.getDocumentsNotFound().size());
    for (uint32_t i = 0; i < msg.getDocumentsNotFound().size(); i++) {
        buf.putString(msg.getDocumentsNotFound()[i].toString());
    }

    onEncodeBucketInfoReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeBatchPutRemoveReply(const SCmd& cmd,
                                                      BBuf& buf) const
{
    api::BatchPutRemoveReply::UP msg(new api::BatchPutRemoveReply(
                static_cast<const api::BatchPutRemoveCommand&>(cmd)));
    uint32_t count = SH::getInt(buf);
    for (uint32_t i = 0; i < count; i++) {
        msg->getDocumentsNotFound().push_back(document::DocumentId(SH::getString(buf)));
    }

    onDecodeBucketInfoReply(buf, *msg);
    return api::StorageReply::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::GetCommand& msg) const
{
    buf.putString(msg.getDocumentId().toString());
    putBucket(msg.getBucket(), buf);
    buf.putLong(msg.getBeforeTimestamp());
    buf.putBoolean(msg.getFieldSet() == "[header]");
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeGetCommand(BBuf& buf) const
{
    document::DocumentId did(SH::getString(buf));
    document::Bucket bucket = getBucket(buf);
    api::Timestamp beforeTimestamp(SH::getLong(buf));
    bool headerOnly(SH::getBoolean(buf));
    api::GetCommand::UP msg(
            new api::GetCommand(bucket, did, headerOnly ? "[header]" : "[all]", beforeTimestamp));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::RemoveCommand& msg) const
{
    buf.putString(msg.getDocumentId().toString());
    putBucket(msg.getBucket(), buf);
    buf.putLong(msg.getTimestamp());
    onEncodeBucketInfoCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeRemoveCommand(BBuf& buf) const
{
    document::DocumentId did(SH::getString(buf));
    document::Bucket bucket = getBucket(buf);
    api::Timestamp timestamp(SH::getLong(buf));
    api::RemoveCommand::UP msg(new api::RemoveCommand(bucket, did, timestamp));
    onDecodeBucketInfoCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::RevertCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    buf.putInt(msg.getRevertTokens().size());
    for (uint32_t i=0, n=msg.getRevertTokens().size(); i<n; ++i) {
        buf.putLong(msg.getRevertTokens()[i]);
    }
    onEncodeBucketInfoCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeRevertCommand(BBuf& buf) const
{
    document::Bucket bucket = getBucket(buf);
    std::vector<api::Timestamp> tokens(SH::getInt(buf));
    for (uint32_t i=0, n=tokens.size(); i<n; ++i) {
        tokens[i] = SH::getLong(buf);
    }
    api::RevertCommand::UP msg(new api::RevertCommand(bucket, tokens));
    onDecodeBucketInfoCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::CreateBucketCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    onEncodeBucketInfoCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeCreateBucketCommand(BBuf& buf) const
{
    document::Bucket bucket = getBucket(buf);
    api::CreateBucketCommand::UP msg(new api::CreateBucketCommand(bucket));
    onDecodeBucketInfoCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::MergeBucketCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    const std::vector<api::MergeBucketCommand::Node>& nodes(msg.getNodes());
    buf.putShort(nodes.size());
    for (uint32_t i=0; i<nodes.size(); ++i) {
        buf.putShort(nodes[i].index);
        buf.putBoolean(nodes[i].sourceOnly);
    }
    buf.putLong(msg.getMaxTimestamp());
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeMergeBucketCommand(BBuf& buf) const
{
    typedef api::MergeBucketCommand::Node Node;
    document::Bucket bucket = getBucket(buf);
    uint16_t nodeCount = SH::getShort(buf);
    std::vector<Node> nodes;
    nodes.reserve(nodeCount);
    for (uint32_t i=0; i<nodeCount; ++i) {
        uint16_t index(SH::getShort(buf));
        bool sourceOnly = SH::getBoolean(buf);
        nodes.push_back(Node(index, sourceOnly));
    }
    api::Timestamp timestamp(SH::getLong(buf));
    api::MergeBucketCommand::UP msg(
            new api::MergeBucketCommand(bucket, nodes, timestamp));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::GetBucketDiffCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    const std::vector<api::MergeBucketCommand::Node>& nodes(msg.getNodes());
    buf.putShort(nodes.size());
    for (uint32_t i=0; i<nodes.size(); ++i) {
        buf.putShort(nodes[i].index);
        buf.putBoolean(nodes[i].sourceOnly);
    }
    buf.putLong(msg.getMaxTimestamp());
    const std::vector<api::GetBucketDiffCommand::Entry>& entries(msg.getDiff());
    buf.putInt(entries.size());
    for (uint32_t i=0; i<entries.size(); ++i) {
        onEncodeDiffEntry(buf, entries[i]);
    }
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeGetBucketDiffCommand(BBuf& buf) const
{
    typedef api::MergeBucketCommand::Node Node;
    document::Bucket bucket = getBucket(buf);
    uint16_t nodeCount = SH::getShort(buf);
    std::vector<Node> nodes;
    nodes.reserve(nodeCount);
    for (uint32_t i=0; i<nodeCount; ++i) {
        uint16_t index(SH::getShort(buf));
        bool sourceOnly = SH::getBoolean(buf);
        nodes.push_back(Node(index, sourceOnly));
    }
    api::Timestamp timestamp = SH::getLong(buf);
    api::GetBucketDiffCommand::UP msg(
            new api::GetBucketDiffCommand(bucket, nodes, timestamp));
    std::vector<api::GetBucketDiffCommand::Entry>& entries(msg->getDiff());
    uint32_t entryCount = SH::getInt(buf);
    if (entryCount > buf.getRemaining()) {
            // Trigger out of bounds exception rather than out of memory error
        buf.incPos(entryCount);
    }
    entries.resize(entryCount);
    for (uint32_t i=0; i<entries.size(); ++i) {
        onDecodeDiffEntry(buf, entries[i]);
    }
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::ApplyBucketDiffCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    const std::vector<api::MergeBucketCommand::Node>& nodes(msg.getNodes());
    buf.putShort(nodes.size());
    for (uint32_t i=0; i<nodes.size(); ++i) {
        buf.putShort(nodes[i].index);
        buf.putBoolean(nodes[i].sourceOnly);
    }
    buf.putInt(msg.getMaxBufferSize());
    const std::vector<api::ApplyBucketDiffCommand::Entry>& entries(
            msg.getDiff());
    buf.putInt(entries.size());
    for (uint32_t i=0; i<entries.size(); ++i) {
        onEncodeDiffEntry(buf, entries[i]._entry);
        buf.putString(entries[i]._docName);
        buf.putInt(entries[i]._headerBlob.size());
        buf.putBytes(&entries[i]._headerBlob[0],
                     entries[i]._headerBlob.size());
        buf.putInt(entries[i]._bodyBlob.size());
        buf.putBytes(&entries[i]._bodyBlob[0],
                     entries[i]._bodyBlob.size());
    }
    onEncodeBucketInfoCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeApplyBucketDiffCommand(BBuf& buf) const
{
    typedef api::MergeBucketCommand::Node Node;
    document::Bucket bucket = getBucket(buf);
    uint16_t nodeCount = SH::getShort(buf);
    std::vector<Node> nodes;
    nodes.reserve(nodeCount);
    for (uint32_t i=0; i<nodeCount; ++i) {
        uint16_t index(SH::getShort(buf));
        bool sourceOnly = SH::getBoolean(buf);
        nodes.push_back(Node(index, sourceOnly));
    }
    uint32_t maxBufferSize(SH::getInt(buf));
    api::ApplyBucketDiffCommand::UP msg(
            new api::ApplyBucketDiffCommand(bucket, nodes, maxBufferSize));
    std::vector<api::ApplyBucketDiffCommand::Entry>& entries(msg->getDiff());
    uint32_t entryCount = SH::getInt(buf);
    if (entryCount > buf.getRemaining()) {
            // Trigger out of bounds exception rather than out of memory error
        buf.incPos(entryCount);
    }
    entries.resize(entryCount);
    for (uint32_t i=0; i<entries.size(); ++i) {
        onDecodeDiffEntry(buf, entries[i]._entry);
        entries[i]._docName = SH::getString(buf);
        uint32_t headerSize = SH::getInt(buf);
        if (headerSize > buf.getRemaining()) {
            buf.incPos(headerSize);
        }
        entries[i]._headerBlob.resize(headerSize);
        buf.getBytes(&entries[i]._headerBlob[0],
                     entries[i]._headerBlob.size());
        uint32_t bodySize = SH::getInt(buf);
        if (bodySize > buf.getRemaining()) {
            buf.incPos(bodySize);
        }
        entries[i]._bodyBlob.resize(bodySize);
        buf.getBytes(&entries[i]._bodyBlob[0],
                     entries[i]._bodyBlob.size());
    }
    onDecodeBucketInfoCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::RequestBucketInfoReply& msg) const
{
    buf.putInt(msg.getBucketInfo().size());
    for (const auto & entry : msg.getBucketInfo()) {
        buf.putLong(entry._bucketId.getRawId());
        putBucketInfo(entry._info, buf);
    }
    onEncodeReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeRequestBucketInfoReply(const SCmd& cmd,
                                                         BBuf& buf) const
{
    api::RequestBucketInfoReply::UP msg(new api::RequestBucketInfoReply(
                static_cast<const api::RequestBucketInfoCommand&>(cmd)));
    api::RequestBucketInfoReply::EntryVector & entries(msg->getBucketInfo());
    uint32_t entryCount = SH::getInt(buf);
    if (entryCount > buf.getRemaining()) {
            // Trigger out of bounds exception rather than out of memory error
        buf.incPos(entryCount);
    }
    entries.resize(entryCount);
    for (auto & entry : entries) {
        entry._bucketId = document::BucketId(SH::getLong(buf));
        entry._info = getBucketInfo(buf);
    }
    onDecodeReply(buf, *msg);
    return api::StorageReply::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::NotifyBucketChangeCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    putBucketInfo(msg.getBucketInfo(), buf);
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeNotifyBucketChangeCommand(BBuf& buf) const
{
    document::Bucket bucket = getBucket(buf);
    api::BucketInfo info(getBucketInfo(buf));
    api::NotifyBucketChangeCommand::UP msg(
            new api::NotifyBucketChangeCommand(bucket, info));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::NotifyBucketChangeReply& msg) const
{
    onEncodeReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeNotifyBucketChangeReply(const SCmd& cmd,
                                                          BBuf& buf) const
{
    api::NotifyBucketChangeReply::UP msg(new api::NotifyBucketChangeReply(
                static_cast<const api::NotifyBucketChangeCommand&>(cmd)));
    onDecodeReply(buf, *msg);
    return api::StorageReply::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::SplitBucketCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    buf.putByte(msg.getMinSplitBits());
    buf.putByte(msg.getMaxSplitBits());
    buf.putInt(msg.getMinByteSize());
    buf.putInt(msg.getMinDocCount());
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeSplitBucketCommand(BBuf& buf) const
{
    document::Bucket bucket = getBucket(buf);
    api::SplitBucketCommand::UP msg(new api::SplitBucketCommand(bucket));
    msg->setMinSplitBits(SH::getByte(buf));
    msg->setMaxSplitBits(SH::getByte(buf));
    msg->setMinByteSize(SH::getInt(buf));
    msg->setMinDocCount(SH::getInt(buf));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void ProtocolSerialization4_2::onEncode(
        GBBuf&, const api::SetBucketStateCommand&) const
{
    throw vespalib::IllegalStateException("Unsupported serialization",
                                          VESPA_STRLOC);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeSetBucketStateCommand(BBuf&) const
{
    throw vespalib::IllegalStateException("Unsupported deserialization",
                                          VESPA_STRLOC);
}

void ProtocolSerialization4_2::onEncode(
        GBBuf&, const api::SetBucketStateReply&) const
{
    throw vespalib::IllegalStateException("Unsupported serialization",
                                          VESPA_STRLOC);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeSetBucketStateReply(const SCmd&,
                                                      BBuf&) const
{
    throw vespalib::IllegalStateException("Unsupported deserialization", VESPA_STRLOC);
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::CreateVisitorCommand& msg) const
{
    putBucketSpace(msg.getBucketSpace(), buf);
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
    buf.putBoolean(msg.getFieldSet() == "[header]");
    buf.putBoolean(msg.visitInconsistentBuckets());
    buf.putInt(msg.getQueueTimeout());

    uint32_t size = msg.getParameters().getSerializedSize();
    char* docBuffer = buf.allocate(size);
    document::ByteBuffer bbuf(docBuffer, size);
    msg.getParameters().serialize(bbuf);

    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeCreateVisitorCommand(BBuf& buf) const
{
    BucketSpace bucketSpace = getBucketSpace(buf);
    vespalib::stringref libraryName = SH::getString(buf);
    vespalib::stringref instanceId = SH::getString(buf);
    vespalib::stringref selection = SH::getString(buf);
    api::CreateVisitorCommand::UP msg(
            new api::CreateVisitorCommand(bucketSpace, libraryName, instanceId, selection));
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
    if (SH::getBoolean(buf)) {
        msg->setFieldSet("[header]");
    }
    if (SH::getBoolean(buf)) {
        msg->setVisitInconsistentBuckets();
    }
    msg->setQueueTimeout(SH::getInt(buf));
    msg->getParameters().deserialize(getTypeRepo(), buf);

    onDecodeCommand(buf, *msg);
    msg->setVisitorDispatcherVersion(42);
    return api::StorageCommand::UP(msg.release());
}

void
ProtocolSerialization4_2::onEncode(
        GBBuf& buf, const api::DestroyVisitorCommand& msg) const
{
    buf.putString(msg.getInstanceId());
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeDestroyVisitorCommand(BBuf& buf) const
{
    vespalib::stringref instanceId = SH::getString(buf);
    api::DestroyVisitorCommand::UP msg(new api::DestroyVisitorCommand(instanceId));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::DestroyVisitorReply& msg) const
{
    onEncodeReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeDestroyVisitorReply(const SCmd& cmd, BBuf& buf) const
{
    api::DestroyVisitorReply::UP msg(new api::DestroyVisitorReply(static_cast<const api::DestroyVisitorCommand&>(cmd)));
    onDecodeReply(buf, *msg);
    return api::StorageReply::UP(msg.release());
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::RemoveLocationCommand& msg) const
{
    buf.putString(msg.getDocumentSelection());
    putBucket(msg.getBucket(), buf);
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeRemoveLocationCommand(BBuf& buf) const
{
    vespalib::stringref documentSelection = SH::getString(buf);
    document::Bucket bucket = getBucket(buf);

    api::RemoveLocationCommand::UP msg;
    msg.reset(new api::RemoveLocationCommand(documentSelection, bucket));
    onDecodeCommand(buf, *msg);
    return api::StorageCommand::UP(msg.release());
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::RemoveLocationReply& msg) const
{
    onEncodeBucketInfoReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeRemoveLocationReply(const SCmd& cmd, BBuf& buf) const
{
    api::RemoveLocationReply::UP msg(new api::RemoveLocationReply(static_cast<const api::RemoveLocationCommand&>(cmd)));
    onDecodeBucketInfoReply(buf, *msg);
    return api::StorageReply::UP(msg.release());
}

// Utility functions for serialization

void
ProtocolSerialization4_2::onEncodeBucketInfoCommand(
            GBBuf& buf, const api::BucketInfoCommand& msg) const
{
    onEncodeCommand(buf, msg);
}

void
ProtocolSerialization4_2::onDecodeBucketInfoCommand(
            BBuf& buf, api::BucketInfoCommand& msg) const
{
    onDecodeCommand(buf, msg);
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::ReturnCode& rc) const
{
        // Convert error code to codes used in 4.2
    buf.putInt(getOldErrorCode(rc.getResult()));
    buf.putString(rc.getMessage());
}

void
ProtocolSerialization4_2::onEncodeDiffEntry(
        GBBuf& buf, const api::GetBucketDiffCommand::Entry& entry) const
{
    buf.putLong(entry._timestamp);
    SH::putGlobalId(entry._gid, buf);
    buf.putInt(entry._headerSize);
    buf.putInt(entry._bodySize);
    buf.putShort(entry._flags);
    buf.putShort(entry._hasMask);
}

void
ProtocolSerialization4_2::onDecodeDiffEntry(
        BBuf& buf, api::GetBucketDiffCommand::Entry& entry) const
{
    entry._timestamp = SH::getLong(buf);
    entry._gid = SH::getGlobalId(buf);
    entry._headerSize = SH::getInt(buf);
    entry._bodySize = SH::getInt(buf);
    entry._flags = SH::getShort(buf);
    entry._hasMask = SH::getShort(buf);
}

}
