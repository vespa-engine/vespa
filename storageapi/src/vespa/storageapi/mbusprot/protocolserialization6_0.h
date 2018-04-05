// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "protocolserialization5_2.h"
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {
namespace mbusprot {

/**
 * Protocol serialization version adding decoding and encoding
 * of bucket space to almost all commands.
 */
class ProtocolSerialization6_0 : public ProtocolSerialization5_2
{
public:
    ProtocolSerialization6_0(const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                             const documentapi::LoadTypeSet &loadTypes);

    document::Bucket getBucket(document::ByteBuffer &buf) const override;
    void putBucket(const document::Bucket &bucket, vespalib::GrowableByteBuffer &buf) const override;
    document::BucketSpace getBucketSpace(document::ByteBuffer &buf) const override;
    void putBucketSpace(document::BucketSpace bucketSpace, vespalib::GrowableByteBuffer &buf) const override;
};

}
}
