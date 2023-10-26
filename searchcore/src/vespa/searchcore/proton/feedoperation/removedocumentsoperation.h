// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include "lidvectorcontext.h"
#include <map>

namespace proton {

class RemoveDocumentsOperation : public FeedOperation
{
protected:
    using LidsToRemoveMap = std::map<uint32_t, LidVectorContext::SP>;
    LidsToRemoveMap _lidsToRemoveMap;

    RemoveDocumentsOperation(Type type);

    void serializeLidsToRemove(vespalib::nbostream &os) const;
    void deserializeLidsToRemove(vespalib::nbostream &is);
public:
    ~RemoveDocumentsOperation() override { }

    void setLidsToRemove(uint32_t subDbId, const LidVectorContext::SP &lidsToRemove) {
        _lidsToRemoveMap[subDbId] = lidsToRemove;
    }

    bool hasLidsToRemove() const {
        return !_lidsToRemoveMap.empty();
    }

    const LidVectorContext::SP
    getLidsToRemove(uint32_t subDbId) const {
        auto found = _lidsToRemoveMap.find(subDbId);
        return (found != _lidsToRemoveMap.end()) ? found->second : LidVectorContext::SP();
    }

};

} // namespace proton
