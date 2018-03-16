// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "protocolserialization4_2.h"
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage::mbusprot {

class ProtocolSerialization5_0 : public ProtocolSerialization4_2 {
private:
    const documentapi::LoadTypeSet& _loadTypes;

public:
    ProtocolSerialization5_0(const document::DocumentTypeRepo::SP&,
                             const documentapi::LoadTypeSet& loadTypes);

    document::Bucket getBucket(document::ByteBuffer& buf) const override;
    void putBucket(const document::Bucket& bucket, vespalib::GrowableByteBuffer& buf) const override;
    document::BucketSpace getBucketSpace(document::ByteBuffer& buf) const override;
    void putBucketSpace(document::BucketSpace bucketSpace, vespalib::GrowableByteBuffer& buf) const override;
    api::BucketInfo getBucketInfo(document::ByteBuffer& buf) const override;
    void putBucketInfo(const api::BucketInfo& info, vespalib::GrowableByteBuffer& buf) const override;

    void onEncode(GBBuf&, const api::PutCommand&) const override;
    void onEncode(GBBuf&, const api::PutReply&) const override;
    void onEncode(GBBuf&, const api::UpdateCommand&) const override;
    void onEncode(GBBuf&, const api::UpdateReply&) const override;
    void onEncode(GBBuf&, const api::GetReply&) const override;
    void onEncode(GBBuf&, const api::RemoveReply&) const override;
    void onEncode(GBBuf&, const api::RevertReply&) const override;
    void onEncode(GBBuf&, const api::CreateBucketReply&) const override;
    void onEncode(GBBuf&, const api::DeleteBucketCommand&) const override;
    void onEncode(GBBuf&, const api::DeleteBucketReply&) const override;
    void onEncode(GBBuf&, const api::MergeBucketCommand&) const override;
    void onEncode(GBBuf&, const api::MergeBucketReply&) const override;
    void onEncode(GBBuf&, const api::GetBucketDiffReply&) const override;
    void onEncode(GBBuf&, const api::ApplyBucketDiffReply&) const override;
    void onEncode(GBBuf&, const api::SplitBucketReply&) const override;
    void onEncode(GBBuf&, const api::JoinBucketsCommand&) const override;
    void onEncode(GBBuf&, const api::JoinBucketsReply&) const override;
    void onEncode(GBBuf&, const api::RequestBucketInfoCommand&) const override;

    void onEncodeBucketInfoReply(GBBuf&, const api::BucketInfoReply&) const override;
    virtual void onEncodeBucketReply(GBBuf&, const api::BucketReply&) const;

    void onEncode(GBBuf&, const api::CreateVisitorCommand& msg) const override;
    void onEncode(GBBuf&, const api::CreateVisitorReply& msg) const override;
    void onEncodeCommand(GBBuf&, const api::StorageCommand&) const override;
    void onEncodeReply(GBBuf&, const api::StorageReply&) const override;

    SCmd::UP onDecodePutCommand(BBuf&) const override;
    SRep::UP onDecodePutReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeUpdateCommand(BBuf&) const override;
    SRep::UP onDecodeUpdateReply(const SCmd&, BBuf&) const override;
    SRep::UP onDecodeGetReply(const SCmd&, BBuf&) const override;
    SRep::UP onDecodeRemoveReply(const SCmd&, BBuf&) const override;
    SRep::UP onDecodeRevertReply(const SCmd&, BBuf&) const override;
    SRep::UP onDecodeCreateBucketReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeDeleteBucketCommand(BBuf&) const override;
    SRep::UP onDecodeDeleteBucketReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeMergeBucketCommand(BBuf&) const override;
    SRep::UP onDecodeMergeBucketReply(const SCmd&, BBuf&) const override;
    SRep::UP onDecodeGetBucketDiffReply(const SCmd&, BBuf&) const override;
    SRep::UP onDecodeApplyBucketDiffReply(const SCmd&, BBuf&) const override;
    SRep::UP onDecodeSplitBucketReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeJoinBucketsCommand(BBuf& buf) const override;
    SRep::UP onDecodeJoinBucketsReply(const SCmd& cmd, BBuf& buf) const override;
    SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const override;
    SCmd::UP onDecodeRequestBucketInfoCommand(BBuf& buf) const override;

    void onDecodeBucketInfoReply(BBuf&, api::BucketInfoReply&) const override;
    virtual void onDecodeBucketReply(BBuf&, api::BucketReply&) const;
    SRep::UP onDecodeCreateVisitorReply(const SCmd& cmd, BBuf& buf) const override;
    void onDecodeCommand(BBuf& buf, api::StorageCommand& msg) const override;
    void onDecodeReply(BBuf&, api::StorageReply&) const override;
};

}
