// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protocolserialization4_2.h"
#include "serializationhelper.h"
#include "storagecommand.h"
#include "storagereply.h"
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/vespalib/util/exceptions.h>


#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".storage.api.mbusprot.serialization.base");

namespace storage::mbusprot {

ProtocolSerialization::ProtocolSerialization(const std::shared_ptr<const document::DocumentTypeRepo>& repo)
    : _repo(repo)
{
}

mbus::Blob
ProtocolSerialization::encode(const api::StorageMessage& msg) const
{
    vespalib::GrowableByteBuffer buf;

    buf.putInt(msg.getType().getId());
    switch (msg.getType().getId()) {
    case api::MessageType::PUT_ID:
        onEncode(buf, static_cast<const api::PutCommand&>(msg));
        break;
    case api::MessageType::PUT_REPLY_ID:
        onEncode(buf, static_cast<const api::PutReply&>(msg));
        break;
    case api::MessageType::UPDATE_ID:
        onEncode(buf, static_cast<const api::UpdateCommand&>(msg));
        break;
    case api::MessageType::UPDATE_REPLY_ID:
        onEncode(buf, static_cast<const api::UpdateReply&>(msg));
        break;
    case api::MessageType::GET_ID:
        onEncode(buf, static_cast<const api::GetCommand&>(msg));
        break;
    case api::MessageType::GET_REPLY_ID:
        onEncode(buf, static_cast<const api::GetReply&>(msg));
        break;
    case api::MessageType::REMOVE_ID:
        onEncode(buf, static_cast<const api::RemoveCommand&>(msg));
        break;
    case api::MessageType::REMOVE_REPLY_ID:
        onEncode(buf, static_cast<const api::RemoveReply&>(msg));
        break;
    case api::MessageType::REVERT_ID:
        onEncode(buf, static_cast<const api::RevertCommand&>(msg));
        break;
    case api::MessageType::REVERT_REPLY_ID:
        onEncode(buf, static_cast<const api::RevertReply&>(msg));
        break;
    case api::MessageType::DELETEBUCKET_ID:
        onEncode(buf, static_cast<const api::DeleteBucketCommand&>(msg));
        break;
    case api::MessageType::DELETEBUCKET_REPLY_ID:
        onEncode(buf, static_cast<const api::DeleteBucketReply&>(msg));
        break;
    case api::MessageType::CREATEBUCKET_ID:
        onEncode(buf, static_cast<const api::CreateBucketCommand&>(msg));
        break;
    case api::MessageType::CREATEBUCKET_REPLY_ID:
        onEncode(buf, static_cast<const api::CreateBucketReply&>(msg));
        break;
    case api::MessageType::MERGEBUCKET_ID:
        onEncode(buf, static_cast<const api::MergeBucketCommand&>(msg));
        break;
    case api::MessageType::MERGEBUCKET_REPLY_ID:
        onEncode(buf, static_cast<const api::MergeBucketReply&>(msg));
        break;
    case api::MessageType::GETBUCKETDIFF_ID:
        onEncode(buf, static_cast<const api::GetBucketDiffCommand&>(msg));
        break;
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        onEncode(buf, static_cast<const api::GetBucketDiffReply&>(msg));
        break;
    case api::MessageType::APPLYBUCKETDIFF_ID:
        onEncode(buf, static_cast<const api::ApplyBucketDiffCommand&>(msg));
        break;
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        onEncode(buf, static_cast<const api::ApplyBucketDiffReply&>(msg));
        break;
    case api::MessageType::REQUESTBUCKETINFO_ID:
        onEncode(buf, static_cast<const api::RequestBucketInfoCommand&>(msg));
        break;
    case api::MessageType::REQUESTBUCKETINFO_REPLY_ID:
        onEncode(buf, static_cast<const api::RequestBucketInfoReply&>(msg));
        break;
    case api::MessageType::NOTIFYBUCKETCHANGE_ID:
        onEncode(buf, static_cast<const api::NotifyBucketChangeCommand&>(msg));
        break;
    case api::MessageType::NOTIFYBUCKETCHANGE_REPLY_ID:
        onEncode(buf, static_cast<const api::NotifyBucketChangeReply&>(msg));
        break;
    case api::MessageType::SPLITBUCKET_ID:
        onEncode(buf, static_cast<const api::SplitBucketCommand&>(msg));
        break;
    case api::MessageType::SPLITBUCKET_REPLY_ID:
        onEncode(buf, static_cast<const api::SplitBucketReply&>(msg));
        break;
    case api::MessageType::JOINBUCKETS_ID:
        onEncode(buf, static_cast<const api::JoinBucketsCommand&>(msg));
        break;
    case api::MessageType::JOINBUCKETS_REPLY_ID:
        onEncode(buf, static_cast<const api::JoinBucketsReply&>(msg));
        break;
    case api::MessageType::VISITOR_CREATE_ID:
        onEncode(buf, static_cast<const api::CreateVisitorCommand&>(msg));
        break;
    case api::MessageType::VISITOR_CREATE_REPLY_ID:
        onEncode(buf, static_cast<const api::CreateVisitorReply&>(msg));
        break;
    case api::MessageType::VISITOR_DESTROY_ID:
        onEncode(buf, static_cast<const api::DestroyVisitorCommand&>(msg));
        break;
    case api::MessageType::VISITOR_DESTROY_REPLY_ID:
        onEncode(buf, static_cast<const api::DestroyVisitorReply&>(msg));
        break;
    case api::MessageType::REMOVELOCATION_ID:
        onEncode(buf, static_cast<const api::RemoveLocationCommand&>(msg));
        break;
    case api::MessageType::REMOVELOCATION_REPLY_ID:
        onEncode(buf, static_cast<const api::RemoveLocationReply&>(msg));
        break;
    case api::MessageType::BATCHPUTREMOVE_ID:
        onEncode(buf, static_cast<const api::BatchPutRemoveCommand&>(msg));
        break;
    case api::MessageType::BATCHPUTREMOVE_REPLY_ID:
        onEncode(buf, static_cast<const api::BatchPutRemoveReply&>(msg));
        break;
    case api::MessageType::SETBUCKETSTATE_ID:
        onEncode(buf, static_cast<const api::SetBucketStateCommand&>(msg));
        break;
    case api::MessageType::SETBUCKETSTATE_REPLY_ID:
        onEncode(buf, static_cast<const api::SetBucketStateReply&>(msg));
        break;
    default:
        LOG(error, "Trying to encode unhandled type %s",
            msg.getType().toString().c_str());
        break;
    }

    mbus::Blob retVal(buf.position());
    memcpy(retVal.data(), buf.getBuffer(), buf.position());
    return retVal;
}

StorageCommand::UP
ProtocolSerialization::decodeCommand(mbus::BlobRef data) const
{
    LOG(spam, "Decode %d bytes of data.", data.size());
    if (data.size() < sizeof(int32_t)) {
        std::ostringstream ost;
        ost << "Request of size " << data.size() << " is not big enough to be "
            "able to store a request.";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }

    document::ByteBuffer buf(data.data(), data.size());
    int type;
    buf.getIntNetwork(type);
    SCmd::UP cmd;
    switch (type) {
    case api::MessageType::PUT_ID:
        cmd = onDecodePutCommand(buf); break;
    case api::MessageType::UPDATE_ID:
        cmd = onDecodeUpdateCommand(buf); break;
    case api::MessageType::GET_ID:
        cmd = onDecodeGetCommand(buf); break;
    case api::MessageType::REMOVE_ID:
        cmd = onDecodeRemoveCommand(buf); break;
    case api::MessageType::REVERT_ID:
        cmd = onDecodeRevertCommand(buf); break;
    case api::MessageType::CREATEBUCKET_ID:
        cmd = onDecodeCreateBucketCommand(buf); break;
    case api::MessageType::DELETEBUCKET_ID:
        cmd = onDecodeDeleteBucketCommand(buf); break;
    case api::MessageType::MERGEBUCKET_ID:
        cmd = onDecodeMergeBucketCommand(buf); break;
    case api::MessageType::GETBUCKETDIFF_ID:
        cmd = onDecodeGetBucketDiffCommand(buf); break;
    case api::MessageType::APPLYBUCKETDIFF_ID:
        cmd = onDecodeApplyBucketDiffCommand(buf); break;
    case api::MessageType::REQUESTBUCKETINFO_ID:
        cmd = onDecodeRequestBucketInfoCommand(buf); break;
    case api::MessageType::NOTIFYBUCKETCHANGE_ID:
        cmd = onDecodeNotifyBucketChangeCommand(buf); break;
    case api::MessageType::SPLITBUCKET_ID:
        cmd = onDecodeSplitBucketCommand(buf); break;
    case api::MessageType::JOINBUCKETS_ID:
        cmd = onDecodeJoinBucketsCommand(buf); break;
    case api::MessageType::VISITOR_CREATE_ID:
        cmd = onDecodeCreateVisitorCommand(buf); break;
    case api::MessageType::VISITOR_DESTROY_ID:
        cmd = onDecodeDestroyVisitorCommand(buf); break;
    case api::MessageType::REMOVELOCATION_ID:
        cmd = onDecodeRemoveLocationCommand(buf); break;
    case api::MessageType::BATCHPUTREMOVE_ID:
        cmd = onDecodeBatchPutRemoveCommand(buf); break;
    case api::MessageType::SETBUCKETSTATE_ID:
        cmd = onDecodeSetBucketStateCommand(buf); break;
    default:
    {
        std::ostringstream ost;
        ost << "Unknown storage command type " << type;
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    }
    return std::make_unique<StorageCommand>(std::move(cmd));
}

StorageReply::UP
ProtocolSerialization::decodeReply(mbus::BlobRef data, const api::StorageCommand& cmd) const
{
    LOG(spam, "Decode %d bytes of data.", data.size());
    if (data.size() < sizeof(int32_t)) {
        std::ostringstream ost;
        ost << "Request of size " << data.size() << " is not big enough to be "
            "able to store a request.";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }

    document::ByteBuffer buf(data.data(), data.size());
    int type;
    buf.getIntNetwork(type);
    SRep::UP reply;
    switch (type) {
    case api::MessageType::PUT_REPLY_ID:
        reply = onDecodePutReply(cmd, buf); break;
    case api::MessageType::UPDATE_REPLY_ID:
        reply = onDecodeUpdateReply(cmd, buf); break;
    case api::MessageType::GET_REPLY_ID:
        reply = onDecodeGetReply(cmd, buf); break;
    case api::MessageType::REMOVE_REPLY_ID:
        reply = onDecodeRemoveReply(cmd, buf); break;
    case api::MessageType::REVERT_REPLY_ID:
        reply = onDecodeRevertReply(cmd, buf); break;
    case api::MessageType::CREATEBUCKET_REPLY_ID:
        reply = onDecodeCreateBucketReply(cmd, buf); break;
    case api::MessageType::DELETEBUCKET_REPLY_ID:
        reply = onDecodeDeleteBucketReply(cmd, buf); break;
    case api::MessageType::MERGEBUCKET_REPLY_ID:
        reply = onDecodeMergeBucketReply(cmd, buf); break;
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        reply = onDecodeGetBucketDiffReply(cmd, buf); break;
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        reply = onDecodeApplyBucketDiffReply(cmd, buf); break;
    case api::MessageType::REQUESTBUCKETINFO_REPLY_ID:
        reply = onDecodeRequestBucketInfoReply(cmd, buf); break;
    case api::MessageType::NOTIFYBUCKETCHANGE_REPLY_ID:
        reply = onDecodeNotifyBucketChangeReply(cmd, buf); break;
    case api::MessageType::SPLITBUCKET_REPLY_ID:
        reply = onDecodeSplitBucketReply(cmd, buf); break;
    case api::MessageType::JOINBUCKETS_REPLY_ID:
        reply = onDecodeJoinBucketsReply(cmd, buf); break;
    case api::MessageType::VISITOR_CREATE_REPLY_ID:
        reply = onDecodeCreateVisitorReply(cmd, buf); break;
    case api::MessageType::VISITOR_DESTROY_REPLY_ID:
        reply = onDecodeDestroyVisitorReply(cmd, buf); break;
    case api::MessageType::REMOVELOCATION_REPLY_ID:
        reply = onDecodeRemoveLocationReply(cmd, buf); break;
    case api::MessageType::BATCHPUTREMOVE_REPLY_ID:
        reply = onDecodeBatchPutRemoveReply(cmd, buf); break;
    case api::MessageType::SETBUCKETSTATE_REPLY_ID:
        reply = onDecodeSetBucketStateReply(cmd, buf); break;
    default:
    {
        std::ostringstream ost;
        ost << "Unknown message type " << type;
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    }
    return std::make_unique<StorageReply>(std::move(reply));
}

}
