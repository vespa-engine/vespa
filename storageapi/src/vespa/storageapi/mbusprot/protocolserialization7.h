// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "protocolserialization6_0.h"
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {
namespace mbusprot {

/**
 * Protocol serialization version that uses Protocol Buffers for all its binary
 * encoding and decoding.
 *
 * TODO stop inheriting from _versioned_ protocol impl once all methods are implemented here.
 */
class ProtocolSerialization7 : public ProtocolSerialization6_0 {
public:
    ProtocolSerialization7(const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                             const documentapi::LoadTypeSet &loadTypes);

    // Put
    void onEncode(GBBuf&, const api::PutCommand&) const override;
    void onEncode(GBBuf&, const api::PutReply&) const override;
    SCmd::UP onDecodePutCommand(BBuf&) const override;
    SRep::UP onDecodePutReply(const SCmd&, BBuf&) const override;

    // Update
    void onEncode(GBBuf&, const api::UpdateCommand&) const override;
    void onEncode(GBBuf&, const api::UpdateReply&) const override;
    SCmd::UP onDecodeUpdateCommand(BBuf&) const override;
    SRep::UP onDecodeUpdateReply(const SCmd&, BBuf&) const override;

    // Remove
    void onEncode(GBBuf&, const api::RemoveCommand&) const override;
    void onEncode(GBBuf&, const api::RemoveReply&) const override;
    SCmd::UP onDecodeRemoveCommand(BBuf&) const override;
    SRep::UP onDecodeRemoveReply(const SCmd&, BBuf&) const override;

    // Get
    void onEncode(GBBuf&, const api::GetCommand&) const override;
    void onEncode(GBBuf&, const api::GetReply&) const override;
    SCmd::UP onDecodeGetCommand(BBuf&) const override;
    SRep::UP onDecodeGetReply(const SCmd&, BBuf&) const override;

    // Revert - TODO this is deprecated, no?
    void onEncode(GBBuf&, const api::RevertCommand&) const override;
    void onEncode(GBBuf&, const api::RevertReply&) const override;
    SCmd::UP onDecodeRevertCommand(BBuf&) const override;
    SRep::UP onDecodeRevertReply(const SCmd&, BBuf&) const override;

    // DeleteBucket
    void onEncode(GBBuf&, const api::DeleteBucketCommand&) const override;
    void onEncode(GBBuf&, const api::DeleteBucketReply&) const override;
    SCmd::UP onDecodeDeleteBucketCommand(BBuf&) const override;
    SRep::UP onDecodeDeleteBucketReply(const SCmd&, BBuf&) const override;

    // CreateBucket
    void onEncode(GBBuf&, const api::CreateBucketCommand&) const override;
    void onEncode(GBBuf&, const api::CreateBucketReply&) const override;
    SCmd::UP onDecodeCreateBucketCommand(BBuf&) const override;
    SRep::UP onDecodeCreateBucketReply(const SCmd&, BBuf&) const override;

    // MergeBucket
    void onEncode(GBBuf&, const api::MergeBucketCommand&) const override;
    void onEncode(GBBuf&, const api::MergeBucketReply&) const override;
    SCmd::UP onDecodeMergeBucketCommand(BBuf&) const override;
    SRep::UP onDecodeMergeBucketReply(const SCmd&, BBuf&) const override;

    // GetBucketDiff
    void onEncode(GBBuf&, const api::GetBucketDiffCommand&) const override;
    void onEncode(GBBuf&, const api::GetBucketDiffReply&) const override;
    SCmd::UP onDecodeGetBucketDiffCommand(BBuf&) const override;
    SRep::UP onDecodeGetBucketDiffReply(const SCmd&, BBuf&) const override;

    // ApplyBucketDiff
    void onEncode(GBBuf&, const api::ApplyBucketDiffCommand&) const override;
    void onEncode(GBBuf&, const api::ApplyBucketDiffReply&) const override;
    SCmd::UP onDecodeApplyBucketDiffCommand(BBuf&) const override;
    SRep::UP onDecodeApplyBucketDiffReply(const SCmd&, BBuf&) const override;

    // RequestBucketInfo
    void onEncode(GBBuf&, const api::RequestBucketInfoCommand&) const override;
    void onEncode(GBBuf&, const api::RequestBucketInfoReply&) const override;
    SCmd::UP onDecodeRequestBucketInfoCommand(BBuf&) const override;
    SRep::UP onDecodeRequestBucketInfoReply(const SCmd&, BBuf&) const override;

    // NotifyBucketChange
    void onEncode(GBBuf&, const api::NotifyBucketChangeCommand&) const override;
    void onEncode(GBBuf&, const api::NotifyBucketChangeReply&) const override;
    SCmd::UP onDecodeNotifyBucketChangeCommand(BBuf&) const override;
    SRep::UP onDecodeNotifyBucketChangeReply(const SCmd&, BBuf&) const override;

    // SplitBucket
    void onEncode(GBBuf&, const api::SplitBucketCommand&) const override;
    void onEncode(GBBuf&, const api::SplitBucketReply&) const override;
    SCmd::UP onDecodeSplitBucketCommand(BBuf&) const override;
    SRep::UP onDecodeSplitBucketReply(const SCmd&, BBuf&) const override;

    // JoinBuckets
    void onEncode(GBBuf&, const api::JoinBucketsCommand&) const override;
    void onEncode(GBBuf&, const api::JoinBucketsReply&) const override;
    SCmd::UP onDecodeJoinBucketsCommand(BBuf&) const override;
    SRep::UP onDecodeJoinBucketsReply(const SCmd&, BBuf&) const override;

    // SetBucketState
    void onEncode(GBBuf&, const api::SetBucketStateCommand&) const override;
    void onEncode(GBBuf&, const api::SetBucketStateReply&) const override;
    SCmd::UP onDecodeSetBucketStateCommand(BBuf&) const override;
    SRep::UP onDecodeSetBucketStateReply(const SCmd&, BBuf&) const override;

    // CreateVisitor
    void onEncode(GBBuf&, const api::CreateVisitorCommand&) const override;
    void onEncode(GBBuf&, const api::CreateVisitorReply&) const override;
    SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const override;
    SRep::UP onDecodeCreateVisitorReply(const SCmd&, BBuf&) const override;

    // DestroyVisitor
    void onEncode(GBBuf&, const api::DestroyVisitorCommand&) const override;
    void onEncode(GBBuf&, const api::DestroyVisitorReply&) const override;
    SCmd::UP onDecodeDestroyVisitorCommand(BBuf&) const override;
    SRep::UP onDecodeDestroyVisitorReply(const SCmd&, BBuf&) const override;

    // RemoveLocation
    void onEncode(GBBuf&, const api::RemoveLocationCommand&) const override;
    void onEncode(GBBuf&, const api::RemoveLocationReply&) const override;
    SCmd::UP onDecodeRemoveLocationCommand(BBuf&) const override;
    SRep::UP onDecodeRemoveLocationReply(const SCmd&, BBuf&) const override;

private:
    template <typename ProtobufType, typename Func>
    std::unique_ptr<api::StorageCommand> decode_request(document::ByteBuffer& in_buf, Func&& f) const;
    template <typename ProtobufType, typename Func>
    std::unique_ptr<api::StorageReply> decode_response(document::ByteBuffer& in_buf, Func&& f) const;
    template <typename ProtobufType, typename Func>
    std::unique_ptr<api::StorageCommand> decode_bucket_request(document::ByteBuffer& in_buf, Func&& f) const;
    template <typename ProtobufType, typename Func>
    std::unique_ptr<api::StorageReply> decode_bucket_response(document::ByteBuffer& in_buf, Func&& f) const;
    template <typename ProtobufType, typename Func>
    std::unique_ptr<api::StorageReply> decode_bucket_info_response(document::ByteBuffer& in_buf, Func&& f) const;
};

}
}
