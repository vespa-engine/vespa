// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "status.h"
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
    : _numDocs(rhs._numDocs),
      _numValues(rhs._numValues),
      _numUniqueValues(rhs._numUniqueValues),
      _allocated(rhs._allocated),
      _used(rhs._used),
      _dead(rhs._dead),
      _unused(rhs._unused),
      _onHold(rhs._onHold),
      _onHoldMax(rhs._onHoldMax),
      _lastSyncToken(rhs.getLastSyncToken()),
      _updates(rhs._updates),
      _nonIdempotentUpdates(rhs._nonIdempotentUpdates),
      _bitVectors(rhs._bitVectors)
{
}

Status&
Status::operator=(const Status& rhs)
{
    _numDocs = rhs._numDocs;
    _numValues = rhs._numValues;
    _numUniqueValues = rhs._numUniqueValues;
    _allocated = rhs._allocated;
    _used = rhs._used;
    _dead = rhs._dead;
    _unused = rhs._unused;
    _onHold = rhs._onHold;
    _onHoldMax = rhs._onHoldMax;
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
    _numValues       = numValues;
    _numUniqueValues = numUniqueValue;
    _allocated       = allocated;
    _used            = used;
    _dead            = dead;
    _unused          = allocated - used;
    _onHold          = onHold;
    _onHoldMax       = std::max(_onHoldMax, onHold);
}

}
