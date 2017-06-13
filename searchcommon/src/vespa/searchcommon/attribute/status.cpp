// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/status.h>

namespace search {
namespace attribute {

Status::Status(const vespalib::string &)
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


vespalib::string
Status::createName(const vespalib::stringref &index,
                                    const vespalib::stringref &attr)
{
    vespalib::string name (index);
    name += ".attribute.";
    name += attr;
    return name;
}


void
Status::updateStatistics(uint64_t numValues,
        uint64_t numUniqueValue,
        uint64_t allocated,
        uint64_t used,
        uint64_t dead,
        uint64_t onHold)
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
}
