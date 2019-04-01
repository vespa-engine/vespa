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

    // DeleteBucket
    void onEncode(GBBuf&, const api::DeleteBucketCommand&) const override;
    void onEncode(GBBuf&, const api::DeleteBucketReply&) const override;
    SCmd::UP onDecodeDeleteBucketCommand(BBuf&) const override;
    SRep::UP onDecodeDeleteBucketReply(const SCmd&, BBuf&) const override;

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

private:
    template <typename ProtobufType, typename Func>
    std::unique_ptr<api::StorageCommand> decode_bucket_request(document::ByteBuffer& in_buf, Func&& f) const;
    template <typename ProtobufType, typename Func>
    std::unique_ptr<api::StorageReply> decode_bucket_info_response(document::ByteBuffer& in_buf, Func&& f) const;
};

}
}
