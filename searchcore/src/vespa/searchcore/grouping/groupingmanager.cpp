// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupingmanager.h"

#include "fast_map_lookups.h"
#include "groupingcontext.h"

#include <vespa/searchlib/aggregation/attribute_node_replacer.h>
#include <vespa/searchlib/aggregation/fs4hit.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/decode_fast_map_lookup_node.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".groupingmanager");

namespace search::grouping {

using aggregation::Grouping;
using attribute::IAttributeContext;
using vespalib::Issue;

//-----------------------------------------------------------------------------

using search::aggregation::Grouping;
using search::aggregation::GroupingLevel;
using search::expression::AttributeNode;
using search::expression::ConfigureStaticParams;
using search::expression::DecodeFastMapLookupNode;
using search::expression::ExpressionNode;

namespace {

/**
 * Configures each fast map lookup decode node with the fast map lookup it names. The value type needed
 * for decoding is not serialized with the node, so this must be done each time a grouping is initialized.
 */
class ConfigureFastMapLookups : public vespalib::ObjectOperation, public vespalib::ObjectPredicate {
    const FastMapLookups* _lookups;

public:
    explicit ConfigureFastMapLookups(const FastMapLookups* lookups) noexcept : _lookups(lookups) {}
    void execute(vespalib::Identifiable& obj) override {
        auto&                node = static_cast<DecodeFastMapLookupNode&>(obj);
        const FastMapLookup* lookup = (_lookups != nullptr) ? _lookups->find(node.getName()) : nullptr;
        if (lookup == nullptr) {
            throw std::runtime_error(
                vespalib::make_string("No fast map lookup '%s' in schema", node.getName().c_str()));
        }
        node.configure(*lookup);
    }
    bool check(const vespalib::Identifiable& obj) const override {
        return obj.inherits(DecodeFastMapLookupNode::classId);
    }
};

} // namespace

bool GroupingManager::empty() const {
    return _groupingContext.empty();
}

void GroupingManager::init(const IAttributeContext& attrCtx, const document::DocumentType* documentType) {
    GroupingContext::GroupingList  list;
    GroupingContext::GroupingList& groupingList(_groupingContext.getGroupingList());
    bool                           enum_optimization = _groupingContext.enum_optimization_enabled();
    for (size_t i = 0; i < groupingList.size(); ++i) {
        Grouping& grouping = *groupingList[i];
        try {
            Grouping::GroupingLevelList& levels = grouping.levels();
            for (size_t k = grouping.getFirstLevel(); k <= grouping.getLastLevel() && k < levels.size(); k++) {
                GroupingLevel&  level(levels[k]);
                ExpressionNode& en = *level.getExpression().getRoot();

                if (en.inherits(AttributeNode::classId)) {
                    auto& an = static_cast<AttributeNode&>(en);
                    an.enableEnumOptimization(enum_optimization);
                }
            }
            ConfigureFastMapLookups configureFastMapLookups(_groupingContext.get_fast_map_lookups());
            grouping.select(configureFastMapLookups, configureFastMapLookups);
            aggregation::NonAttribute2DocumentAccessor nonAttributes2DocumentAccess(attrCtx);
            ConfigureStaticParams                      stuff(&attrCtx, documentType);
            grouping.configureStaticStuff(stuff);
            list.push_back(groupingList[i]);
        } catch (const std::exception& e) {
            Issue::report("Could not set up grouping number %zd : %s. Ignoring this grouping.", i,
                          e.what());
        }
    }
    std::swap(list, groupingList);
}

void GroupingManager::groupInRelevanceOrder(uint32_t distributionKey, std::span<const RankedHit> hits) {
    _groupingContext.groupInRelevanceOrder(hits);
    _groupingContext.setDistributionKey(distributionKey);
}

void GroupingManager::groupUnordered(uint32_t distributionKey, std::span<const RankedHit> hits,
                                     const search::BitVector* overflow) {
    _groupingContext.groupUnordered(hits, overflow);
    _groupingContext.setDistributionKey(distributionKey);
}

void GroupingManager::merge(GroupingContext& ctx) {
    GroupingContext::GroupingList& list_a(_groupingContext.getGroupingList());
    GroupingContext::GroupingList& list_b(ctx.getGroupingList());
    LOG_ASSERT(list_a.size() == list_b.size());
    for (size_t i = 0; i < list_a.size(); ++i) {
        Grouping& a = *list_a[i];
        Grouping& b = *list_b[i];
        LOG_ASSERT(a.getId() == b.getId());
        a.merge(b);
    }
}

void GroupingManager::prune() {
    GroupingContext::GroupingList& groupingList(_groupingContext.getGroupingList());
    for (const auto& g : groupingList) {
        g->postMerge();
        g->sortById();
    }
}

void GroupingManager::convertToGlobalId(const search::IDocumentMetaStore& metaStore) {
    GroupingContext::GroupingList& groupingList = _groupingContext.getGroupingList();
    for (const auto& g : groupingList) {
        g->convertToGlobalId(metaStore);
        LOG(debug, "convertToGlobalId: %s", g->asString().c_str());
    }
}

} // namespace search::grouping
