// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucket.h>
#include <vespa/messagebus/routable.h>
#include <vespa/storageapi/mbusprot/storagemessage.h>
#include <vespa/storageapi/message/bucket.h>

namespace document {
    class ByteBuffer;
}
namespace mbus {
    class Blob;
    class BlobRef;
}
namespace vespalib {
    class GrowableByteBuffer;
}
namespace storage::api {
class StorageCommand;
class StorageReply;
class PutCommand;
class PutReply;
class GetCommand;
class GetReply;
class RemoveCommand;
class RemoveReply;
class RevertCommand;
class RevertReply;
class DeleteBucketCommand;
class DeleteBucketReply;
class CreateBucketCommand;
class CreateBucketReply;
class MergeBucketCommand;
class MergeBucketReply;
class GetBucketDiffCommand;
class GetBucketDiffReply;
class ApplyBucketDiffCommand;
class ApplyBucketDiffReply;
class RequestBucketInfoCommand;
class RequestBucketInfoReply;
class NotifyBucketChangeCommand;
class NotifyBucketChangeReply;
class SplitBucketCommand;
class SplitBucketReply;
class StatBucketCommand;
class StatBucketReply;
class JoinBucketsCommand;
class JoinBucketsReply;
class SetBucketStateCommand;
class SetBucketStateReply;
class CreateVisitorCommand;
class RemoveLocationCommand;
class RemoveLocationReply;
}

namespace storage::mbusprot {

class SerializationHelper;
class StorageCommand;
class StorageReply;

class ProtocolSerialization {
public:
    virtual mbus::Blob encode(const api::StorageMessage&) const;
    virtual std::unique_ptr<StorageCommand> decodeCommand(mbus::BlobRef) const;
    virtual std::unique_ptr<StorageReply> decodeReply(
                            mbus::BlobRef, const api::StorageCommand&) const;
protected:
    ProtocolSerialization() = default;
    virtual ~ProtocolSerialization() = default;

    typedef api::StorageCommand SCmd;
    typedef api::StorageReply SRep;
    typedef document::ByteBuffer BBuf;
    typedef vespalib::GrowableByteBuffer GBBuf;
    typedef SerializationHelper SH;
    typedef StorageMessage SM;

    virtual void onEncode(GBBuf&, const api::PutCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::PutReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::UpdateCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::UpdateReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::GetCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::GetReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::RemoveCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::RemoveReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::RevertCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::RevertReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::DeleteBucketCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::DeleteBucketReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::CreateBucketCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::CreateBucketReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::MergeBucketCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::MergeBucketReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::GetBucketDiffCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::GetBucketDiffReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::ApplyBucketDiffCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::ApplyBucketDiffReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::RequestBucketInfoCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::RequestBucketInfoReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::NotifyBucketChangeCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::NotifyBucketChangeReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::SplitBucketCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::SplitBucketReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::JoinBucketsCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::JoinBucketsReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::SetBucketStateCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::SetBucketStateReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::CreateVisitorCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::CreateVisitorReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::DestroyVisitorCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::DestroyVisitorReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::RemoveLocationCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::RemoveLocationReply&) const = 0;
    virtual void onEncode(GBBuf&, const api::StatBucketCommand&) const = 0;
    virtual void onEncode(GBBuf&, const api::StatBucketReply&) const = 0;

    virtual SCmd::UP onDecodePutCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodePutReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeUpdateCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeUpdateReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeGetCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeGetReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeRemoveCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeRemoveReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeRevertCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeRevertReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeDeleteBucketCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeDeleteBucketReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeCreateBucketCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeCreateBucketReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeMergeBucketCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeMergeBucketReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeGetBucketDiffCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeGetBucketDiffReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeApplyBucketDiffCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeApplyBucketDiffReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeRequestBucketInfoCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeRequestBucketInfoReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeNotifyBucketChangeCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeNotifyBucketChangeReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeSplitBucketCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeSplitBucketReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeJoinBucketsCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeJoinBucketsReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeSetBucketStateCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeSetBucketStateReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeCreateVisitorReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeDestroyVisitorCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeDestroyVisitorReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeRemoveLocationCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeRemoveLocationReply(const SCmd&, BBuf&) const = 0;
    virtual SCmd::UP onDecodeStatBucketCommand(BBuf&) const = 0;
    virtual SRep::UP onDecodeStatBucketReply(const SCmd&, BBuf&) const = 0;
};

}
