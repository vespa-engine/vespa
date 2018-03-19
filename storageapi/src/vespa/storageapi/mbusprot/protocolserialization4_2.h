// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "protocolserialization.h"

namespace storage::mbusprot {

class ProtocolSerialization4_2 : public ProtocolSerialization {
public:
    ProtocolSerialization4_2(const document::DocumentTypeRepo::SP&);

protected:
    void onEncode(GBBuf&, const api::GetCommand&) const override;
    void onEncode(GBBuf&, const api::RemoveCommand&) const override;
    void onEncode(GBBuf&, const api::RevertCommand&) const override;
    void onEncode(GBBuf&, const api::CreateBucketCommand&) const override;
    void onEncode(GBBuf&, const api::MergeBucketCommand&) const override;
    void onEncode(GBBuf&, const api::GetBucketDiffCommand&) const override;
    void onEncode(GBBuf&, const api::ApplyBucketDiffCommand&) const override;
    void onEncode(GBBuf&, const api::RequestBucketInfoReply&) const override;
    void onEncode(GBBuf&, const api::NotifyBucketChangeCommand&) const override;
    void onEncode(GBBuf&, const api::NotifyBucketChangeReply&) const override;
    void onEncode(GBBuf&, const api::SplitBucketCommand&) const override;
    void onEncode(GBBuf&, const api::CreateVisitorCommand&) const override;
    void onEncode(GBBuf&, const api::DestroyVisitorCommand&) const override;
    void onEncode(GBBuf&, const api::DestroyVisitorReply&) const override;
    void onEncode(GBBuf&, const api::RemoveLocationCommand&) const override;
    void onEncode(GBBuf&, const api::RemoveLocationReply&) const override;

    // Not supported on 4.2, but implemented here for simplicity.
    void onEncode(GBBuf&, const api::BatchPutRemoveCommand&) const override;
    void onEncode(GBBuf&, const api::BatchPutRemoveReply&) const override;
    void onEncode(GBBuf&, const api::SetBucketStateCommand&) const override;
    void onEncode(GBBuf&, const api::SetBucketStateReply&) const override;

    virtual void onEncodeBucketInfoCommand(GBBuf&, const api::BucketInfoCommand&) const;
    virtual void onEncodeBucketInfoReply(GBBuf&, const api::BucketInfoReply&) const = 0;
    virtual void onEncodeCommand(GBBuf&, const api::StorageCommand&) const = 0;
    virtual void onEncodeReply(GBBuf&, const api::StorageReply&) const = 0;

    virtual void onEncodeDiffEntry(GBBuf&, const api::GetBucketDiffCommand::Entry&) const;
    virtual void onEncode(GBBuf&, const api::ReturnCode&) const;
    SCmd::UP onDecodeGetCommand(BBuf&) const override;
    SCmd::UP onDecodeRemoveCommand(BBuf&) const override;
    SCmd::UP onDecodeRevertCommand(BBuf&) const override;
    SCmd::UP onDecodeCreateBucketCommand(BBuf&) const override;
    SCmd::UP onDecodeMergeBucketCommand(BBuf&) const override;
    SCmd::UP onDecodeGetBucketDiffCommand(BBuf&) const override;
    SCmd::UP onDecodeApplyBucketDiffCommand(BBuf&) const override;
    SRep::UP onDecodeRequestBucketInfoReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeNotifyBucketChangeCommand(BBuf&) const override;
    SRep::UP onDecodeNotifyBucketChangeReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeSplitBucketCommand(BBuf&) const override;
    SCmd::UP onDecodeSetBucketStateCommand(BBuf&) const override;
    SRep::UP onDecodeSetBucketStateReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const override;
    SCmd::UP onDecodeDestroyVisitorCommand(BBuf&) const override;
    SRep::UP onDecodeDestroyVisitorReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeRemoveLocationCommand(BBuf&) const override;
    SRep::UP onDecodeRemoveLocationReply(const SCmd&, BBuf&) const override;

    // Not supported on 4.2, but implemented here for simplicity.
    SCmd::UP onDecodeBatchPutRemoveCommand(BBuf&) const override;
    SRep::UP onDecodeBatchPutRemoveReply(const SCmd&, BBuf&) const override;

    virtual void onDecodeBucketInfoCommand(BBuf&, api::BucketInfoCommand&) const;
    virtual void onDecodeBucketInfoReply(BBuf&, api::BucketInfoReply&) const = 0;
    virtual void onDecodeCommand(BBuf& buf, api::StorageCommand& msg) const = 0;
    virtual void onDecodeReply(BBuf&, api::StorageReply&) const = 0;

    virtual void onDecodeDiffEntry(BBuf&, api::GetBucketDiffCommand::Entry&) const;
};

}
