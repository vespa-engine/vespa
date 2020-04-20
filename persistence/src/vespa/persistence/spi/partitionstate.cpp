// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "partitionstate.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage::spi {

PartitionState::PartitionState()
    : _state(UP),
      _reason()
{ }

PartitionState::PartitionState(State s, vespalib::stringref reason)
    : _state(s),
      _reason(reason)
{ }

PartitionStateList::PartitionStateList(PartitionId::Type partitionCount)
    : _states(partitionCount)
{ }

PartitionStateList::~PartitionStateList() = default;

PartitionState&
PartitionStateList::operator[](PartitionId::Type index)
{
    if (index >= _states.size()) {
        vespalib::asciistream ost;
        ost << "Cannot return disk " << index << " of " << _states.size();
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    return _states[index];
}

}
