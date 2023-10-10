// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "groupandcollectengine.h"

namespace search {

using namespace expression;
using namespace aggregation;

namespace grouping {

GroupAndCollectEngine::GroupAndCollectEngine(const GroupingLevel * request, size_t level, GroupEngine * nextEngine, bool frozen) :
    GroupEngine(request, level, nextEngine, frozen)
{
}

GroupAndCollectEngine::~GroupAndCollectEngine()
{
}

GroupRef
GroupAndCollectEngine::group(Children & children, uint32_t docId, double rank)
{
    GroupRef gr(GroupEngine::group(children, docId, rank));
    if (gr.valid()) {
        collect(gr, docId, rank);
    }
    return gr;
}

void
GroupAndCollectEngine::group(uint32_t docId, double rank)
{
    GroupEngine::group(docId, rank);
    collect(GroupRef(0), docId, rank);
}

GroupRef
GroupAndCollectEngine::createGroup(const search::expression::ResultNode & v)
{
    GroupRef gr(GroupEngine::createGroup(v));
    createCollectors(gr);
    return gr;
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_grouping_groupandcollectengine() {}
