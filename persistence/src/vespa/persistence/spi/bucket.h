// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::Bucket
 * \ingroup spi
 *
 * \brief Wrapper class for a bucket identifier.
 *
 * We don't want the persistence implementation having to know how to map
 * buckets to partitions. Thus we want the service layer to always provide a
 * partition identifier together with bucket identifiers. This wrapper class
 * exist to ensure we always have partition, and to make interfaces look
 * simpler.
 */

#pragma once

#include <persistence/spi/types.h>
#include <vespa/document/bucket/bucket.h>

namespace storage {
namespace spi {

class Bucket {
    document::Bucket _bucket;
    PartitionId _partition;

public:
    Bucket() : _bucket(document::BucketSpace::invalid(), document::BucketId(0)), _partition(0) {}
    Bucket(const document::Bucket& b, PartitionId p)
        : _bucket(b), _partition(p) {}

    const document::Bucket &getBucket() const { return _bucket; }
    document::BucketId getBucketId() const { return _bucket.getBucketId(); }
    document::BucketSpace getBucketSpace() const { return _bucket.getBucketSpace(); }
    PartitionId getPartition() const { return _partition; }

    /** Convert easily to a document bucket id to make class easy to use. */
    operator document::BucketId() const { return _bucket.getBucketId(); }

    bool operator==(const Bucket& o) const {
        return (_bucket == o._bucket && _partition == o._partition);
    }

    vespalib::string toString() const;
};

vespalib::asciistream& operator<<(vespalib::asciistream& out, const Bucket& bucket);
std::ostream& operator<<(std::ostream& out, const Bucket& bucket);

} // spi
} // storage

