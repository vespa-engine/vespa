// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "move.h"
#include <vespa/persistence/spi/fixed_bucket_spaces.h>
#include <ostream>

namespace storage {
namespace bucketmover {

Move::Move()
    : _sourceDisk(0),
      _targetDisk(0),
      _bucket(),
      _totalDocSize(0),
      _priority(255)
{
}

Move::Move(uint16_t source, uint16_t target, const document::Bucket& bucket,
           uint32_t totalDocSize)
    : _sourceDisk(source),
      _targetDisk(target),
      _bucket(bucket),
      _totalDocSize(totalDocSize),
      _priority(255)
{
}

void
Move::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (!isDefined()) {
        out << "Move(none)";
        return;
    }
    out << "Move(" << _bucket << ", " << _sourceDisk << " -> " << _targetDisk
        << ", pri " << (uint16_t) _priority << ")";
}

} // bucketmover
} // storage
