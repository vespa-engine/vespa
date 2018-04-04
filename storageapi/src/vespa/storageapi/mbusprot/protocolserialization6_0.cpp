// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protocolserialization6_0.h"
#include "serializationhelper.h"

namespace storage {
namespace mbusprot {

ProtocolSerialization6_0::ProtocolSerialization6_0(const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                                                   const documentapi::LoadTypeSet &loadTypes)
    : ProtocolSerialization5_2(repo, loadTypes)
{
}

document::Bucket
ProtocolSerialization6_0::getBucket(document::ByteBuffer &buf) const
{
    document::BucketSpace bucketSpace(SH::getLong(buf));
    document::BucketId bucketId(SH::getLong(buf));
    return document::Bucket(bucketSpace, bucketId);
}

void
ProtocolSerialization6_0::putBucket(const document::Bucket &bucket, vespalib::GrowableByteBuffer &buf) const
{
    buf.putLong(bucket.getBucketSpace().getId());
    buf.putLong(bucket.getBucketId().getRawId());
}

document::BucketSpace
ProtocolSerialization6_0::getBucketSpace(document::ByteBuffer &buf) const
{
    return document::BucketSpace(SH::getLong(buf));
}

void
ProtocolSerialization6_0::putBucketSpace(document::BucketSpace bucketSpace, vespalib::GrowableByteBuffer &buf) const
{
    buf.putLong(bucketSpace.getId());
}

}
}
