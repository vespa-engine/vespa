// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/time.h>

namespace storage::distributor {

/**
 * Semantics are basically as follows:
 * We divide the timeline into periods based on the configured check
 * interval, with each bucket having a start point in this period
 * based on its hash. If the current time is at least that of the start
 * point and the bucket has not been checked after this point, it is
 * scheduled for GC. Otherwise, the bucket is checked iff there
 * has been at least one missed start point in a previous period.
 *
 * If the check period is zero, this is considered to mean GC is disabled.
 */

class BucketGcTimeCalculator
{
public:
    class BucketIdHasher {
        virtual size_t doHash(const document::BucketId&) const noexcept = 0;
    public:
        virtual ~BucketIdHasher() = default;
        size_t hash(const document::BucketId& b) const noexcept { return doHash(b); }
    };

    class BucketIdIdentityHasher : public BucketIdHasher {
        size_t doHash(const document::BucketId& b) const noexcept override {
            return b.getId();
        }
    };

    BucketGcTimeCalculator(const BucketIdHasher& hasher,
                           vespalib::duration checkInterval)
        : _hasher(hasher),
          _checkInterval(checkInterval)
    {
    }

    bool shouldGc(const document::BucketId&,
                  vespalib::duration currentTime,
                  vespalib::duration lastRunAt) const;

private:
    const BucketIdHasher& _hasher;
    vespalib::duration _checkInterval;
};

}
