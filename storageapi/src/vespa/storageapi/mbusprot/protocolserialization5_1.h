// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/mbusprot/protocolserialization5_0.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {
namespace mbusprot {

class ProtocolSerialization5_1 : public ProtocolSerialization5_0
{
    enum BucketState {
        BUCKET_READY  = 0x1,
        BUCKET_ACTIVE = 0x2,
    };
public:
    ProtocolSerialization5_1(const document::DocumentTypeRepo::SP&,
                             const documentapi::LoadTypeSet& loadTypes);

    virtual api::BucketInfo getBucketInfo(document::ByteBuffer& buf) const;
    virtual void putBucketInfo(const api::BucketInfo& info,
                               vespalib::GrowableByteBuffer& buf) const;

protected:
    virtual void onEncode(GBBuf&, const api::SetBucketStateCommand&) const;
    virtual void onEncode(GBBuf&, const api::SetBucketStateReply&) const;
    virtual void onEncode(GBBuf&, const api::GetCommand&) const;
    virtual void onEncode(GBBuf&, const api::CreateVisitorCommand&) const;
    virtual void onEncode(GBBuf&, const api::CreateBucketCommand&) const;

    virtual SCmd::UP onDecodeSetBucketStateCommand(BBuf&) const;
    virtual SRep::UP onDecodeSetBucketStateReply(const SCmd&, BBuf&) const;
    virtual SCmd::UP onDecodeGetCommand(BBuf&) const;
    virtual SCmd::UP onDecodeCreateVisitorCommand(BBuf&) const;
    virtual SCmd::UP onDecodeCreateBucketCommand(BBuf&) const;
};

} // mbusprot
} // storage

