// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketscaniterator.h"

using document::BucketId;

namespace proton::bucketdb {

ScanIterator::ScanIterator(const Guard & db, BucketId bucket)
    : _db(std::move(db)),
      _itr(_db->lowerBound(bucket)),
      _end(_db->end())
{ }

} // namespace proton
