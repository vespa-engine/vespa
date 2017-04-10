// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/mbusprot/protocolserialization.h>

namespace storage {
namespace mbusprot {

class ProtocolSerialization4_2 : public ProtocolSerialization {
public:
    ProtocolSerialization4_2(const document::DocumentTypeRepo::SP&);

protected:
    virtual void onEncode(GBBuf&, const api::GetCommand&) const override;
    virtual void onEncode(GBBuf&, const api::RemoveCommand&) const override;
    virtual void onEncode(GBBuf&, const api::RevertCommand&) const override;
    virtual void onEncode(GBBuf&, const api::CreateBucketCommand&) const override;
    virtual void onEncode(GBBuf&, const api::MergeBucketCommand&) const override;
    virtual void onEncode(GBBuf&, const api::GetBucketDiffCommand&) const override;
    virtual void onEncode(GBBuf&, const api::ApplyBucketDiffCommand&) const override;
    virtual void onEncode(GBBuf&, const api::RequestBucketInfoReply&) const override;
    virtual void onEncode(GBBuf&, const api::NotifyBucketChangeCommand&) const override;
    virtual void onEncode(GBBuf&, const api::NotifyBucketChangeReply&) const override;
    virtual void onEncode(GBBuf&, const api::SplitBucketCommand&) const override;
    virtual void onEncode(GBBuf&, const api::MultiOperationCommand&) const override;
    virtual void onEncode(GBBuf&, const api::CreateVisitorCommand&) const override;
    virtual void onEncode(GBBuf&, const api::DestroyVisitorCommand&) const override;
    virtual void onEncode(GBBuf&, const api::DestroyVisitorReply&) const override;
    virtual void onEncode(GBBuf&, const api::RemoveLocationCommand&) const override;
    virtual void onEncode(GBBuf&, const api::RemoveLocationReply&) const override;

    // Not supported on 4.2, but implemented here for simplicity.
    virtual void onEncode(GBBuf&, const api::BatchPutRemoveCommand&) const override;
    virtual void onEncode(GBBuf&, const api::BatchPutRemoveReply&) const override;
    virtual void onEncode(GBBuf&, const api::SetBucketStateCommand&) const override;
    virtual void onEncode(GBBuf&, const api::SetBucketStateReply&) const override;

    virtual void onEncodeBucketInfoCommand(GBBuf&, const api::BucketInfoCommand&) const;
    virtual void onEncodeBucketInfoReply(GBBuf&, const api::BucketInfoReply&) const = 0;
    virtual void onEncodeCommand(GBBuf&, const api::StorageCommand&) const = 0;
    virtual void onEncodeReply(GBBuf&, const api::StorageReply&) const = 0;

    virtual void onEncodeDiffEntry(GBBuf&, const api::GetBucketDiffCommand::Entry&) const;
    virtual void onEncode(GBBuf&, const api::ReturnCode&) const;
    virtual SCmd::UP onDecodeGetCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeRemoveCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeRevertCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeCreateBucketCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeMergeBucketCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeGetBucketDiffCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeApplyBucketDiffCommand(BBuf&) const override;
    virtual SRep::UP onDecodeRequestBucketInfoReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeNotifyBucketChangeCommand(BBuf&) const override;
    virtual SRep::UP onDecodeNotifyBucketChangeReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeSplitBucketCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeSetBucketStateCommand(BBuf&) const override;
    virtual SRep::UP onDecodeSetBucketStateReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeMultiOperationCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeDestroyVisitorCommand(BBuf&) const override;
    virtual SRep::UP onDecodeDestroyVisitorReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeRemoveLocationCommand(BBuf&) const override;
    virtual SRep::UP onDecodeRemoveLocationReply(const SCmd&, BBuf&) const override;

    // Not supported on 4.2, but implemented here for simplicity.
    virtual SCmd::UP onDecodeBatchPutRemoveCommand(BBuf&) const override;
    virtual SRep::UP onDecodeBatchPutRemoveReply(const SCmd&, BBuf&) const override;

    virtual void onDecodeBucketInfoCommand(BBuf&, api::BucketInfoCommand&) const;
    virtual void onDecodeBucketInfoReply(BBuf&, api::BucketInfoReply&) const = 0;
    virtual void onDecodeCommand(BBuf& buf, api::StorageCommand& msg) const = 0;
    virtual void onDecodeReply(BBuf&, api::StorageReply&) const = 0;

    virtual void onDecodeDiffEntry(BBuf&, api::GetBucketDiffCommand::Entry&) const;
};

} // mbusprot
} // storage

