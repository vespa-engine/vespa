// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupingmanager.h"
#include "groupingsession.h"
#include "groupingcontext.h"
#include <vespa/searchlib/aggregation/fs4hit.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".groupingmanager");

namespace search::grouping {

using aggregation::Grouping;
using attribute::IAttributeContext;
using vespalib::Issue;
using vespalib::make_string_short::fmt;

//-----------------------------------------------------------------------------

GroupingManager::GroupingManager(GroupingContext & groupingContext)
    : _groupingContext(groupingContext)
{
}

GroupingManager::~GroupingManager() = default;

using search::expression::ExpressionNode;
using search::expression::AttributeNode;
using search::expression::ConfigureStaticParams;
using search::aggregation::Grouping;
using search::aggregation::GroupingLevel;

bool GroupingManager::empty() const {
    return _groupingContext.getGroupingList().empty();
}

void
GroupingManager::init(const IAttributeContext &attrCtx)
{
    GroupingContext::GroupingList list;
    GroupingContext::GroupingList &groupingList(_groupingContext.getGroupingList());
    for (size_t i = 0; i < groupingList.size(); ++i) {
        Grouping &grouping = *groupingList[i];
        try {
            Grouping::GroupingLevelList &levels = grouping.levels();
            for (size_t k = grouping.getFirstLevel(); k <= grouping.getLastLevel() &&
                            k < levels.size(); k++) {
                GroupingLevel & level(levels[k]);
                ExpressionNode & en = *level.getExpression().getRoot();

                if (en.inherits(AttributeNode::classId)) {
                    AttributeNode & an = static_cast<AttributeNode &>(en);
                    an.enableEnumOptimization(true);
                }
            }
            ConfigureStaticParams stuff(&attrCtx, nullptr);
            grouping.configureStaticStuff(stuff);
            list.push_back(groupingList[i]);
        } catch (const std::exception & e) {
            Issue::report("Could not locate attribute for grouping number %ld : %s. Ignoring this grouping.", i, e.what());
        }
    }
    std::swap(list, groupingList);
}

void
GroupingManager::groupInRelevanceOrder(const RankedHit *searchResults, uint32_t binSize)
{
    GroupingContext::GroupingList &groupingList(_groupingContext.getGroupingList());
    for (size_t i = 0; i < groupingList.size(); ++i) {
        Grouping & g = *groupingList[i];
        if ( ! g.needResort() ) {
            g.aggregate(searchResults, binSize);
            LOG(debug, "groupInRelevanceOrder: %s", g.asString().c_str());
            g.cleanTemporary();
            g.cleanupAttributeReferences();
        }
    }
}

void
GroupingManager::groupUnordered(const RankedHit *searchResults, uint32_t binSize, const search::BitVector * overflow)
{
    GroupingContext::GroupingList &groupingList(_groupingContext.getGroupingList());
    for (size_t i = 0; i < groupingList.size(); ++i) {
        Grouping & g = *groupingList[i];
        if ( g.needResort() ) {
            g.aggregate(searchResults, binSize, overflow);
            LOG(debug, "groupUnordered: %s", g.asString().c_str());
            g.cleanTemporary();
            g.cleanupAttributeReferences();
        }
    }
}

void
GroupingManager::merge(GroupingContext &ctx)
{
    GroupingContext::GroupingList &list_a(_groupingContext.getGroupingList());
    GroupingContext::GroupingList &list_b(ctx.getGroupingList());
    LOG_ASSERT(list_a.size() == list_b.size());
    for (size_t i = 0; i < list_a.size(); ++i) {
        Grouping &a = *list_a[i];
        Grouping &b = *list_b[i];
        LOG_ASSERT(a.getId() == b.getId());
        a.merge(b);
    }
}

void
GroupingManager::prune()
{
    GroupingContext::GroupingList &groupingList(_groupingContext.getGroupingList());
    for (size_t i = 0; i < groupingList.size(); ++i) {
        Grouping &g = *groupingList[i];
        g.postMerge();
        g.sortById();
    }
}

void
GroupingManager::convertToGlobalId(const search::IDocumentMetaStore &metaStore)
{
    GroupingContext::GroupingList & groupingList = _groupingContext.getGroupingList();
    for (size_t i = 0; i < groupingList.size(); ++i) {
        Grouping & g = *groupingList[i];
        g.convertToGlobalId(metaStore);
        LOG(debug, "convertToGlobalId: %s", g.asString().c_str());
    }
}

}
