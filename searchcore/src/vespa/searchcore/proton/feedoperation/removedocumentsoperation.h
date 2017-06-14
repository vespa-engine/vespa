// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include "lidvectorcontext.h"
#include <map>

namespace proton {

class RemoveDocumentsOperation : public FeedOperation
{
protected:
    typedef std::map<uint32_t, LidVectorContext::SP> LidsToRemoveMap;
    LidsToRemoveMap _lidsToRemoveMap;

    RemoveDocumentsOperation(Type type);

    void serializeLidsToRemove(vespalib::nbostream &os) const;
    void deserializeLidsToRemove(vespalib::nbostream &is);
public:
    virtual ~RemoveDocumentsOperation() { }

    void setLidsToRemove(uint32_t subDbId, const LidVectorContext::SP &lidsToRemove) {
        _lidsToRemoveMap[subDbId] = lidsToRemove;
    }

    const LidVectorContext::SP
    getLidsToRemove(uint32_t subDbId) const {
        LidsToRemoveMap::const_iterator found(_lidsToRemoveMap.find(subDbId));
        if (found != _lidsToRemoveMap.end())
            return found->second;
        else
            return LidVectorContext::SP();
    }

};

} // namespace proton

