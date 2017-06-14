// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedocumentsoperation.h"

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
    for (LidsToRemoveMap::const_iterator
             it = _lidsToRemoveMap.begin(), ite = _lidsToRemoveMap.end();
         it != ite; ++it) {
        os << it->first;
        it->second->serialize(os);
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
        LidVectorContext::SP lidsToRemove(new LidVectorContext());
        lidsToRemove->deserialize(is);
        setLidsToRemove(subDbId, lidsToRemove);
    }
}


} // namespace proton
