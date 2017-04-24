// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/mbusprot/protocolserialization4_2.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {
namespace mbusprot {

class ProtocolSerialization5_0 : public ProtocolSerialization4_2 {
private:
    const documentapi::LoadTypeSet& _loadTypes;

public:
    ProtocolSerialization5_0(const document::DocumentTypeRepo::SP&,
                             const documentapi::LoadTypeSet& loadTypes);

    virtual api::BucketInfo getBucketInfo(document::ByteBuffer& buf) const override;
    virtual void putBucketInfo(const api::BucketInfo& info,
                               vespalib::GrowableByteBuffer& buf) const override;

    virtual void onEncode(GBBuf&, const api::PutCommand&) const override;
    virtual void onEncode(GBBuf&, const api::PutReply&) const override;
    virtual void onEncode(GBBuf&, const api::UpdateCommand&) const override;
    virtual void onEncode(GBBuf&, const api::UpdateReply&) const override;
    virtual void onEncode(GBBuf&, const api::GetReply&) const override;
    virtual void onEncode(GBBuf&, const api::RemoveReply&) const override;
    virtual void onEncode(GBBuf&, const api::RevertReply&) const override;
    virtual void onEncode(GBBuf&, const api::CreateBucketReply&) const override;
    virtual void onEncode(GBBuf&, const api::DeleteBucketCommand&) const override;
    virtual void onEncode(GBBuf&, const api::DeleteBucketReply&) const override;
    virtual void onEncode(GBBuf&, const api::MergeBucketCommand&) const override;
    virtual void onEncode(GBBuf&, const api::MergeBucketReply&) const override;
    virtual void onEncode(GBBuf&, const api::GetBucketDiffReply&) const override;
    virtual void onEncode(GBBuf&, const api::ApplyBucketDiffReply&) const override;
    virtual void onEncode(GBBuf&, const api::SplitBucketReply&) const override;
    virtual void onEncode(GBBuf&, const api::MultiOperationReply&) const override;
    virtual void onEncode(GBBuf&, const api::JoinBucketsCommand&) const override;
    virtual void onEncode(GBBuf&, const api::JoinBucketsReply&) const override;
    virtual void onEncode(GBBuf&, const api::RequestBucketInfoCommand&) const override;

    virtual void onEncodeBucketInfoReply(GBBuf&, const api::BucketInfoReply&) const override;
    virtual void onEncodeBucketReply(GBBuf&, const api::BucketReply&) const;

    virtual void onEncode(GBBuf&, const api::CreateVisitorCommand& msg) const override;
    virtual void onEncode(GBBuf&, const api::CreateVisitorReply& msg) const override;
    virtual void onEncodeCommand(GBBuf&, const api::StorageCommand&) const override;
    virtual void onEncodeReply(GBBuf&, const api::StorageReply&) const override;

    virtual SCmd::UP onDecodePutCommand(BBuf&) const override;
    virtual SRep::UP onDecodePutReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeUpdateCommand(BBuf&) const override;
    virtual SRep::UP onDecodeUpdateReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeGetReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeRemoveReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeRevertReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeCreateBucketReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeDeleteBucketCommand(BBuf&) const override;
    virtual SRep::UP onDecodeDeleteBucketReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeMergeBucketCommand(BBuf&) const override;
    virtual SRep::UP onDecodeMergeBucketReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeGetBucketDiffReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeApplyBucketDiffReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeSplitBucketReply(const SCmd&, BBuf&) const override;
    virtual SRep::UP onDecodeMultiOperationReply(const SCmd&, BBuf&) const override;
    virtual SCmd::UP onDecodeJoinBucketsCommand(BBuf& buf) const override;
    virtual SRep::UP onDecodeJoinBucketsReply(const SCmd& cmd, BBuf& buf) const override;
    virtual SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const override;
    virtual SCmd::UP onDecodeRequestBucketInfoCommand(BBuf& buf) const override;

    virtual void onDecodeBucketInfoReply(BBuf&, api::BucketInfoReply&) const override;
    virtual void onDecodeBucketReply(BBuf&, api::BucketReply&) const;
    virtual SRep::UP onDecodeCreateVisitorReply(const SCmd& cmd, BBuf& buf) const override;
    virtual void onDecodeCommand(BBuf& buf, api::StorageCommand& msg) const override;
    virtual void onDecodeReply(BBuf&, api::StorageReply&) const override;
};

} // mbusprot
} // storage

