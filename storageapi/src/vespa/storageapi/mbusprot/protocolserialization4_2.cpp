// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protocolserialization4_2.h"
#include "oldreturncodemapper.h"
#include "serializationhelper.h"
#include "storagecommand.h"
#include "storagereply.h"

#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/fieldset/fieldsets.h>

#include <vespa/log/log.h>
LOG_SETUP(".storage.api.mbusprot.serialization.4_2");

using document::BucketSpace;
using document::AllFields;

namespace storage::mbusprot {

ProtocolSerialization4_2::ProtocolSerialization4_2(
        const std::shared_ptr<const document::DocumentTypeRepo>& repo)
    : LegacyProtocolSerialization(repo)
{
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::GetCommand& msg) const
{
    buf.putString(msg.getDocumentId().toString());
    putBucket(msg.getBucket(), buf);
    buf.putLong(msg.getBeforeTimestamp());
    buf.putBoolean(false);
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeGetCommand(BBuf& buf) const
{
    document::DocumentId did(SH::getString(buf));
    document::Bucket bucket = getBucket(buf);
    api::Timestamp beforeTimestamp(SH::getLong(buf));
    bool headerOnly(SH::getBoolean(buf)); // Ignored header only flag
    (void) headerOnly;
    auto msg = std::make_unique<api::GetCommand>(bucket, did, AllFields::NAME, beforeTimestamp);
    onDecodeCommand(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::RemoveCommand& msg) const
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
    auto msg = std::make_unique<api::RemoveCommand>(bucket, did, timestamp);
    onDecodeBucketInfoCommand(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::RevertCommand& msg) const
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
    auto msg = std::make_unique<api::RevertCommand>(bucket, tokens);
    onDecodeBucketInfoCommand(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::CreateBucketCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    onEncodeBucketInfoCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeCreateBucketCommand(BBuf& buf) const
{
    document::Bucket bucket = getBucket(buf);
    auto msg = std::make_unique<api::CreateBucketCommand>(bucket);
    onDecodeBucketInfoCommand(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::MergeBucketCommand& msg) const
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
    auto msg = std::make_unique<api::MergeBucketCommand>(bucket, nodes, timestamp);
    onDecodeCommand(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::GetBucketDiffCommand& msg) const
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
    auto msg = std::make_unique<api::GetBucketDiffCommand>(bucket, nodes, timestamp);
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
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::ApplyBucketDiffCommand& msg) const
{
    putBucket(msg.getBucket(), buf);
    const std::vector<api::MergeBucketCommand::Node>& nodes(msg.getNodes());
    buf.putShort(nodes.size());
    for (uint32_t i=0; i<nodes.size(); ++i) {
        buf.putShort(nodes[i].index);
        buf.putBoolean(nodes[i].sourceOnly);
    }
    buf.putInt(0x400000);
    const std::vector<api::ApplyBucketDiffCommand::Entry>& entries(msg.getDiff());
    buf.putInt(entries.size());
    for (uint32_t i=0; i<entries.size(); ++i) {
        onEncodeDiffEntry(buf, entries[i]._entry);
        buf.putString(entries[i]._docName);
        buf.putInt(entries[i]._headerBlob.size());
        buf.putBytes(&entries[i]._headerBlob[0], entries[i]._headerBlob.size());
        buf.putInt(entries[i]._bodyBlob.size());
        buf.putBytes(&entries[i]._bodyBlob[0], entries[i]._bodyBlob.size());
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
    (void) SH::getInt(buf); // Unused field
    auto msg = std::make_unique<api::ApplyBucketDiffCommand>(bucket, nodes);
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
        buf.getBytes(&entries[i]._headerBlob[0], entries[i]._headerBlob.size());
        uint32_t bodySize = SH::getInt(buf);
        if (bodySize > buf.getRemaining()) {
            buf.incPos(bodySize);
        }
        entries[i]._bodyBlob.resize(bodySize);
        buf.getBytes(&entries[i]._bodyBlob[0], entries[i]._bodyBlob.size());
    }
    onDecodeBucketInfoCommand(buf, *msg);
    return msg;
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
ProtocolSerialization4_2::onDecodeRequestBucketInfoReply(const SCmd& cmd, BBuf& buf) const
{
    auto msg  = std::make_unique<api::RequestBucketInfoReply>(static_cast<const api::RequestBucketInfoCommand&>(cmd));
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
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::NotifyBucketChangeCommand& msg) const
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
    auto msg = std::make_unique<api::NotifyBucketChangeCommand>(bucket, info);
    onDecodeCommand(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::NotifyBucketChangeReply& msg) const
{
    onEncodeReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeNotifyBucketChangeReply(const SCmd& cmd,BBuf& buf) const
{
    auto msg = std::make_unique<api::NotifyBucketChangeReply>(static_cast<const api::NotifyBucketChangeCommand&>(cmd));
    onDecodeReply(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::SplitBucketCommand& msg) const
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
    auto msg = std::make_unique<api::SplitBucketCommand>(bucket);
    msg->setMinSplitBits(SH::getByte(buf));
    msg->setMaxSplitBits(SH::getByte(buf));
    msg->setMinByteSize(SH::getInt(buf));
    msg->setMinDocCount(SH::getInt(buf));
    onDecodeCommand(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf&, const api::SetBucketStateCommand&) const
{
    throw vespalib::IllegalStateException("Unsupported serialization", VESPA_STRLOC);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeSetBucketStateCommand(BBuf&) const
{
    throw vespalib::IllegalStateException("Unsupported deserialization", VESPA_STRLOC);
}

void ProtocolSerialization4_2::onEncode(GBBuf&, const api::SetBucketStateReply&) const
{
    throw vespalib::IllegalStateException("Unsupported serialization", VESPA_STRLOC);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeSetBucketStateReply(const SCmd&, BBuf&) const
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
    buf.putBoolean(false);
    buf.putBoolean(msg.visitInconsistentBuckets());
    buf.putInt(vespalib::count_ms(msg.getQueueTimeout()));
    msg.getParameters().serialize(buf);

    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeCreateVisitorCommand(BBuf& buf) const
{
    BucketSpace bucketSpace = getBucketSpace(buf);
    vespalib::stringref libraryName = SH::getString(buf);
    vespalib::stringref instanceId = SH::getString(buf);
    vespalib::stringref selection = SH::getString(buf);
    auto msg = std::make_unique<api::CreateVisitorCommand>(bucketSpace, libraryName, instanceId, selection);
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
        msg->setFieldSet(AllFields::NAME);
    }
    if (SH::getBoolean(buf)) {
        msg->setVisitInconsistentBuckets();
    }
    msg->setQueueTimeout(std::chrono::milliseconds(SH::getInt(buf)));
    msg->getParameters().deserialize(buf);

    onDecodeCommand(buf, *msg);
    msg->setVisitorDispatcherVersion(42);
    return msg;
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::DestroyVisitorCommand& msg) const
{
    buf.putString(msg.getInstanceId());
    onEncodeCommand(buf, msg);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeDestroyVisitorCommand(BBuf& buf) const
{
    vespalib::stringref instanceId = SH::getString(buf);
    auto msg = std::make_unique<api::DestroyVisitorCommand>(instanceId);
    onDecodeCommand(buf, *msg);
    return msg;
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::DestroyVisitorReply& msg) const
{
    onEncodeReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeDestroyVisitorReply(const SCmd& cmd, BBuf& buf) const
{
    auto msg = std::make_unique<api::DestroyVisitorReply>(static_cast<const api::DestroyVisitorCommand&>(cmd));
    onDecodeReply(buf, *msg);
    return msg;
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

    auto msg = std::make_unique<api::RemoveLocationCommand>(documentSelection, bucket);
    onDecodeCommand(buf, *msg);
    return msg;
}

void
ProtocolSerialization4_2::onEncode(GBBuf& buf, const api::RemoveLocationReply& msg) const
{
    onEncodeBucketInfoReply(buf, msg);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeRemoveLocationReply(const SCmd& cmd, BBuf& buf) const
{
    auto msg = std::make_unique<api::RemoveLocationReply>(static_cast<const api::RemoveLocationCommand&>(cmd));
    onDecodeBucketInfoReply(buf, *msg);
    return msg;
}

void ProtocolSerialization4_2::onEncode(GBBuf&, const api::StatBucketCommand&) const {
    throw vespalib::IllegalStateException("StatBucketCommand not expected for legacy protocol version", VESPA_STRLOC);
}

api::StorageCommand::UP
ProtocolSerialization4_2::onDecodeStatBucketCommand(BBuf&) const {
    throw vespalib::IllegalStateException("StatBucketCommand not expected for legacy protocol version", VESPA_STRLOC);
}

void ProtocolSerialization4_2::onEncode(GBBuf&, const api::StatBucketReply&) const {
    throw vespalib::IllegalStateException("StatBucketReply not expected for legacy protocol version", VESPA_STRLOC);
}

api::StorageReply::UP
ProtocolSerialization4_2::onDecodeStatBucketReply(const SCmd&, BBuf&) const {
    throw vespalib::IllegalStateException("StatBucketReply not expected for legacy protocol version", VESPA_STRLOC);
}

// Utility functions for serialization

void
ProtocolSerialization4_2::onEncodeBucketInfoCommand(GBBuf& buf, const api::BucketInfoCommand& msg) const
{
    onEncodeCommand(buf, msg);
}

void
ProtocolSerialization4_2::onDecodeBucketInfoCommand(BBuf& buf, api::BucketInfoCommand& msg) const
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
ProtocolSerialization4_2::onEncodeDiffEntry(GBBuf& buf, const api::GetBucketDiffCommand::Entry& entry) const
{
    buf.putLong(entry._timestamp);
    SH::putGlobalId(entry._gid, buf);
    buf.putInt(entry._headerSize);
    buf.putInt(entry._bodySize);
    buf.putShort(entry._flags);
    buf.putShort(entry._hasMask);
}

void
ProtocolSerialization4_2::onDecodeDiffEntry(BBuf& buf, api::GetBucketDiffCommand::Entry& entry) const
{
    entry._timestamp = SH::getLong(buf);
    entry._gid = SH::getGlobalId(buf);
    entry._headerSize = SH::getInt(buf);
    entry._bodySize = SH::getInt(buf);
    entry._flags = SH::getShort(buf);
    entry._hasMask = SH::getShort(buf);
}

}
