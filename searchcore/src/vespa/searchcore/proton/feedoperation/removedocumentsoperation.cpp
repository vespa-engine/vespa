// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedocumentsoperation.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace proton {

RemoveDocumentsOperation::RemoveDocumentsOperation(Type type)
    : FeedOperation(type),
      _lidsToRemoveMap()
{
}


void
RemoveDocumentsOperation::serializeLidsToRemove(vespalib::nbostream &os) const
{
    uint32_t mapSize = _lidsToRemoveMap.size();
    os << mapSize;
    for (const auto & entry : _lidsToRemoveMap) {
        os << entry.first;
        entry.second->serialize(os);
    }
}


void
RemoveDocumentsOperation::deserializeLidsToRemove(vespalib::nbostream &is)
{
    uint32_t mapSize;
    uint32_t i;
    is >> mapSize;
    for (i = 0; i < mapSize; ++i) {
        uint32_t subDbId;
        is >> subDbId;
        auto lidsToRemove = std::make_shared<LidVectorContext>();
        lidsToRemove->deserialize(is);
        setLidsToRemove(subDbId, lidsToRemove);
    }
}


} // namespace proton
