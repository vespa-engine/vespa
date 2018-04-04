// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "protocolserialization5_0.h"

namespace storage {
namespace mbusprot {

class ProtocolSerialization5_1 : public ProtocolSerialization5_0
{
    enum BucketState {
        BUCKET_READY  = 0x1,
        BUCKET_ACTIVE = 0x2,
    };
public:
    ProtocolSerialization5_1(const std::shared_ptr<const document::DocumentTypeRepo>&,
                             const documentapi::LoadTypeSet& loadTypes);

    api::BucketInfo getBucketInfo(document::ByteBuffer& buf) const override;
    void putBucketInfo(const api::BucketInfo& info, vespalib::GrowableByteBuffer& buf) const override;

protected:
    void onEncode(GBBuf&, const api::SetBucketStateCommand&) const override;
    void onEncode(GBBuf&, const api::SetBucketStateReply&) const override;
    void onEncode(GBBuf&, const api::GetCommand&) const override;
    void onEncode(GBBuf&, const api::CreateVisitorCommand&) const override;
    void onEncode(GBBuf&, const api::CreateBucketCommand&) const override;

    SCmd::UP onDecodeSetBucketStateCommand(BBuf&) const override;
    SRep::UP onDecodeSetBucketStateReply(const SCmd&, BBuf&) const override;
    SCmd::UP onDecodeGetCommand(BBuf&) const override;
    SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const override;
    SCmd::UP onDecodeCreateBucketCommand(BBuf&) const override;
};

} // mbusprot
} // storage
