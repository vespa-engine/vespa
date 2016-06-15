// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/grouping/groupengine.h>

namespace search {
namespace grouping {

class GroupAndCollectEngine : public GroupEngine
{
public:
    GroupAndCollectEngine(const aggregation::GroupingLevel * request, size_t level, GroupEngine * nextEngine, bool frozen);
    ~GroupAndCollectEngine();
private:
    virtual GroupRef group(Children & children, uint32_t docId, double rank);
    virtual void group(uint32_t docId, double rank);
    virtual GroupRef createGroup(const expression::ResultNode & id);
};

}
}
