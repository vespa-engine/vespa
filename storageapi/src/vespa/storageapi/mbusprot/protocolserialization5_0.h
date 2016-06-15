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

    virtual api::BucketInfo getBucketInfo(document::ByteBuffer& buf) const;
    virtual void putBucketInfo(const api::BucketInfo& info,
                               vespalib::GrowableByteBuffer& buf) const;

    virtual void onEncode(GBBuf&, const api::PutCommand&) const;
    virtual void onEncode(GBBuf&, const api::PutReply&) const;
    virtual void onEncode(GBBuf&, const api::UpdateCommand&) const;
    virtual void onEncode(GBBuf&, const api::UpdateReply&) const;
    virtual void onEncode(GBBuf&, const api::GetReply&) const;
    virtual void onEncode(GBBuf&, const api::RemoveReply&) const;
    virtual void onEncode(GBBuf&, const api::RevertReply&) const;
    virtual void onEncode(GBBuf&, const api::CreateBucketReply&) const;
    virtual void onEncode(GBBuf&, const api::DeleteBucketCommand&) const;
    virtual void onEncode(GBBuf&, const api::DeleteBucketReply&) const;
    virtual void onEncode(GBBuf&, const api::MergeBucketCommand&) const;
    virtual void onEncode(GBBuf&, const api::MergeBucketReply&) const;
    virtual void onEncode(GBBuf&, const api::GetBucketDiffReply&) const;
    virtual void onEncode(GBBuf&, const api::ApplyBucketDiffReply&) const;
    virtual void onEncode(GBBuf&, const api::SplitBucketReply&) const;
    virtual void onEncode(GBBuf&, const api::MultiOperationReply&) const;
    virtual void onEncode(GBBuf&, const api::JoinBucketsCommand&) const;
    virtual void onEncode(GBBuf&, const api::JoinBucketsReply&) const;
    virtual void onEncode(GBBuf&, const api::RequestBucketInfoCommand&) const;

    virtual void onEncodeBucketInfoReply(GBBuf&, const api::BucketInfoReply&) const;
    virtual void onEncodeBucketReply(GBBuf&, const api::BucketReply&) const;

    virtual void onEncode(GBBuf&, const api::CreateVisitorCommand& msg) const;
    virtual void onEncode(GBBuf&, const api::CreateVisitorReply& msg) const;
    virtual void onEncodeCommand(GBBuf&, const api::StorageCommand&) const;
    virtual void onEncodeReply(GBBuf&, const api::StorageReply&) const;

    virtual SCmd::UP onDecodePutCommand(BBuf&) const;
    virtual SRep::UP onDecodePutReply(const SCmd&, BBuf&) const;
    virtual SCmd::UP onDecodeUpdateCommand(BBuf&) const;
    virtual SRep::UP onDecodeUpdateReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeGetReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeRemoveReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeRevertReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeCreateBucketReply(const SCmd&, BBuf&) const;
    virtual SCmd::UP onDecodeDeleteBucketCommand(BBuf&) const;
    virtual SRep::UP onDecodeDeleteBucketReply(const SCmd&, BBuf&) const;
    virtual SCmd::UP onDecodeMergeBucketCommand(BBuf&) const;
    virtual SRep::UP onDecodeMergeBucketReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeGetBucketDiffReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeApplyBucketDiffReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeSplitBucketReply(const SCmd&, BBuf&) const;
    virtual SRep::UP onDecodeMultiOperationReply(const SCmd&, BBuf&) const;
    virtual SCmd::UP onDecodeJoinBucketsCommand(BBuf& buf) const;
    virtual SRep::UP onDecodeJoinBucketsReply(const SCmd& cmd, BBuf& buf) const;
    virtual SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const;
    virtual SCmd::UP onDecodeRequestBucketInfoCommand(BBuf& buf) const;

    virtual void onDecodeBucketInfoReply(BBuf&, api::BucketInfoReply&) const;
    virtual void onDecodeBucketReply(BBuf&, api::BucketReply&) const;
    virtual SRep::UP onDecodeCreateVisitorReply(const SCmd& cmd, BBuf& buf) const;
    virtual void onDecodeCommand(BBuf& buf, api::StorageCommand& msg) const;
    virtual void onDecodeReply(BBuf&, api::StorageReply&) const;
};

} // mbusprot
} // storage

