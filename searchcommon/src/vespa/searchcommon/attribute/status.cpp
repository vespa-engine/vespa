// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "status.h"
#include <vespa/vespalib/util/atomic.h>

using namespace vespalib::atomic;

namespace search::attribute {

Status::Status()
    : _numDocs              (0),
      _numValues            (0),
      _numUniqueValues      (0),
      _allocated            (0),
      _used                 (0),
      _dead                 (0),
      _unused               (0),
      _onHold               (0),
      _onHoldMax            (0),
      _lastSyncToken        (0),
      _updates              (0),
      _nonIdempotentUpdates (0),
      _bitVectors(0)
{
}

Status::Status(const Status& rhs)
    : _numDocs(load_relaxed(rhs._numDocs)),
      _numValues(load_relaxed(rhs._numValues)),
      _numUniqueValues(load_relaxed(rhs._numUniqueValues)),
      _allocated(load_relaxed(rhs._allocated)),
      _used(load_relaxed(rhs._used)),
      _dead(load_relaxed(rhs._dead)),
      _unused(load_relaxed(rhs._unused)),
      _onHold(load_relaxed(rhs._onHold)),
      _onHoldMax(load_relaxed(rhs._onHoldMax)),
      _lastSyncToken(rhs.getLastSyncToken()),
      _updates(rhs._updates),
      _nonIdempotentUpdates(rhs._nonIdempotentUpdates),
      _bitVectors(rhs._bitVectors)
{
}

Status&
Status::operator=(const Status& rhs)
{
    store_relaxed(_numDocs,         load_relaxed(rhs._numDocs));
    store_relaxed(_numValues,       load_relaxed(rhs._numValues));
    store_relaxed(_numUniqueValues, load_relaxed(rhs._numUniqueValues));
    store_relaxed(_allocated,       load_relaxed(rhs._allocated));
    store_relaxed(_used,            load_relaxed(rhs._used));
    store_relaxed(_dead,            load_relaxed(rhs._dead));
    store_relaxed(_unused,          load_relaxed(rhs._unused));
    store_relaxed(_onHold,          load_relaxed(rhs._onHold));
    store_relaxed(_onHoldMax,       load_relaxed(rhs._onHoldMax));
    setLastSyncToken(rhs.getLastSyncToken());
    _updates = rhs._updates;
    _nonIdempotentUpdates = rhs._nonIdempotentUpdates;
    _bitVectors = rhs._bitVectors;
    return *this;
}

vespalib::string
Status::createName(vespalib::stringref index, vespalib::stringref attr)
{
    vespalib::string name (index);
    name += ".attribute.";
    name += attr;
    return name;
}

void
Status::updateStatistics(uint64_t numValues, uint64_t numUniqueValue, uint64_t allocated,
                         uint64_t used, uint64_t dead, uint64_t onHold)
{
    store_relaxed(_numValues,       numValues);
    store_relaxed(_numUniqueValues, numUniqueValue);
    store_relaxed(_allocated,       allocated);
    store_relaxed(_used,            used);
    store_relaxed(_dead,            dead);
    store_relaxed(_unused,          allocated - used);
    store_relaxed(_onHold,          onHold);
    store_relaxed(_onHoldMax,       std::max(load_relaxed(_onHoldMax), onHold));
}

}
