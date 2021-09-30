// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::Bucket
 * \ingroup spi
 *
 * \brief Wrapper class for a bucket identifier.
 *
 */

#pragma once

#include <vespa/document/bucket/bucket.h>

namespace storage::spi {

class Bucket {
    document::Bucket _bucket;

public:
    Bucket() noexcept : _bucket(document::BucketSpace::invalid(), document::BucketId(0)) {}
    explicit Bucket(const document::Bucket& b) noexcept
        : _bucket(b) {}

    const document::Bucket &getBucket() const noexcept { return _bucket; }
    document::BucketId getBucketId() const noexcept { return _bucket.getBucketId(); }
    document::BucketSpace getBucketSpace() const noexcept { return _bucket.getBucketSpace(); }

    /** Convert easily to a document bucket id to make class easy to use. */
    operator document::BucketId() const noexcept { return _bucket.getBucketId(); }

    bool operator==(const Bucket& o) const noexcept {
        return (_bucket == o._bucket);
    }

    vespalib::string toString() const;
};

vespalib::asciistream& operator<<(vespalib::asciistream& out, const Bucket& bucket);
std::ostream& operator<<(std::ostream& out, const Bucket& bucket);

}
