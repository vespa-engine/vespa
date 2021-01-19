// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Iterates metadata in the bucket using the SPI, and analyze where we need to
 * split in order to split the bucket in two pieces. Possible results include.
 *
 *   - Normal case: A set of two buckets (with same used bits count),
 *     splitting source bucket in half.
 *   - Empty source bucket. No data to split.
 *   - Error: Impossible to split data in two. All data has common bucket bits.
 *   - Single target split: Asked to limit bits used to less than max, and using
 *     this amount of bits won't split data in two. Currently, we return this
 *     as success and create the paired bucket, such that SPI can handle single
 *     target split just as a regular split, only that no data will actually be
 *     split into the other target. (And that target thus must be deleted
 *     afterwards if empty.)
 *
 */
#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/context.h>
#include <vespa/vespalib/util/printable.h>

namespace storage {

namespace spi { struct PersistenceProvider; }

struct SplitBitDetector
{
    enum ResultType {
        OK,
        EMPTY,
        ERROR
    };

    class Result : public vespalib::Printable {
        ResultType _result;
        document::BucketId _target1;
        document::BucketId _target2;
        vespalib::string _reason;
        bool _singleTarget;

    public:
        Result() : _result(EMPTY), _singleTarget(false) {}
        Result(vespalib::stringref error)
            : _result(ERROR), _reason(error), _singleTarget(false) {}
        Result(const document::BucketId& t1, const document::BucketId& t2,
               bool single)
            : _result(OK), _target1(t1), _target2(t2), _singleTarget(single) {}

        bool success() const { return (_result == OK); }
        bool failed() const { return (_result == ERROR); }
        bool empty() const { return (_result == EMPTY); }
        const vespalib::string& getReason() const { return _reason; }
        const document::BucketId& getTarget1() const { return _target1; }
        const document::BucketId& getTarget2() const { return _target2; }

            // Printable implementation
        void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    };

    static Result detectSplit(spi::PersistenceProvider&, const spi::Bucket&,
                              uint32_t maxSplitBits, spi::Context&,
                              uint32_t minCount = 0, uint32_t minSize = 0);
};

} // storage

