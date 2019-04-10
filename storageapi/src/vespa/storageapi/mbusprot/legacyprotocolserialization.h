// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "protocolserialization.h"

namespace storage::mbusprot {

/*
 * Utility base class for pre-v7 (protobuf) serialization implementations.
 *
 * TODO remove on Vespa 8 alongside legacy serialization formats.
 */
class LegacyProtocolSerialization : public ProtocolSerialization {
    const std::shared_ptr<const document::DocumentTypeRepo> _repo;
public:
    explicit LegacyProtocolSerialization(const std::shared_ptr<const document::DocumentTypeRepo>& repo)
        : _repo(repo)
    {}

    const document::DocumentTypeRepo& getTypeRepo() const { return *_repo; }
    const std::shared_ptr<const document::DocumentTypeRepo> getTypeRepoSp() const { return _repo; }

    virtual document::Bucket getBucket(document::ByteBuffer& buf) const = 0;
    virtual void putBucket(const document::Bucket& bucket, vespalib::GrowableByteBuffer& buf) const = 0;
    virtual document::BucketSpace getBucketSpace(document::ByteBuffer& buf) const = 0;
    virtual void putBucketSpace(document::BucketSpace bucketSpace, vespalib::GrowableByteBuffer& buf) const = 0;
    virtual api::BucketInfo getBucketInfo(document::ByteBuffer& buf) const = 0;
    virtual void putBucketInfo(const api::BucketInfo& info, vespalib::GrowableByteBuffer& buf) const = 0;
};

} // storage::mbusprot
