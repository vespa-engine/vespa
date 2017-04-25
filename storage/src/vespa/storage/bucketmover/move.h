// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::bucketmover::Move
 * \ingroup bucketmover
 *
 * \brief Class representing a bucket move between disks.
 */

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/printable.h>

namespace storage {
namespace bucketmover {

class Move : public vespalib::Printable {
    uint16_t _sourceDisk;
    uint16_t _targetDisk;
    document::BucketId _bucket;
    uint32_t _totalDocSize;
    uint8_t _priority;

public:
    Move();
    Move(uint16_t source, uint16_t target, const document::BucketId& bucket,
         uint32_t totalDocSize);

    /** False if invalid move. (Empty constructor) Indicates end of run. */
    bool isDefined() const { return (_bucket.getRawId() != 0); }

    // Only valid to call if move is defined
    uint16_t getSourceDisk() const { return _sourceDisk; }
    uint16_t getTargetDisk() const { return _targetDisk; }
    const document::BucketId& getBucketId() const { return _bucket; }
    uint8_t getPriority() const { return _priority; }
    uint32_t getTotalDocSize() const { return _totalDocSize; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // bucketmover
} // storage

