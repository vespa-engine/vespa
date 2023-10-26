// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "groupengine.h"

namespace search {
namespace grouping {

class GroupAndCollectEngine : public GroupEngine
{
public:
    GroupAndCollectEngine(const aggregation::GroupingLevel * request, size_t level, GroupEngine * nextEngine, bool frozen);
    ~GroupAndCollectEngine();
private:
    GroupRef group(Children & children, uint32_t docId, double rank) override;
    void group(uint32_t docId, double rank) override;
    GroupRef createGroup(const expression::ResultNode & id) override;
};

}
}
