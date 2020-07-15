// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node.h"
#include "querybuilder.h"
#include "term.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/objects/hexdump.h>

namespace search::query {

/**
 * Creates a query tree from a stack dump.
 */
template <class NodeTypes>
class StackDumpQueryCreator {
public:
    static Node::UP create(search::SimpleQueryStackDumpIterator &queryStack)
    {
        QueryBuilder<NodeTypes> builder;

        // Make sure that the life time of what pureTermView refers to exceeds that of pureTermView.
        // Especially make sure that do not create any stack local objects like vespalib::string
        // with smaller scope, that you refer with pureTermView.
        vespalib::stringref pureTermView;
        while (!builder.hasError() && queryStack.next()) {
            Term *t = createQueryTerm(queryStack, builder, pureTermView);
            if (!builder.hasError() && t) {
                if (queryStack.hasNoRankFlag()) {
                    t->setRanked(false);
                }
                if (queryStack.hasNoPositionDataFlag()) {
                    t->setPositionData(false);
                }
            }
        }
        if (builder.hasError()) {
            vespalib::stringref stack = queryStack.getStack();
            LOG(error, "Unable to create query tree from stack dump. Failed at position %ld out of %ld bytes %s",
                       queryStack.getPosition(), stack.size(), builder.error().c_str());
            LOG(error, "Raw QueryStack = %s", vespalib::HexDump(stack.data(), stack.size()).toString().c_str());
        }
        return builder.build();
    }

private: 
    static Term * createQueryTerm(search::SimpleQueryStackDumpIterator &queryStack, QueryBuilder<NodeTypes> & builder, vespalib::stringref & pureTermView) {
        uint32_t arity = queryStack.getArity();
        ParseItem::ItemType type = queryStack.getType();
        Node::UP node;
        Term *t = 0;
        if (type == ParseItem::ITEM_AND) {
            builder.addAnd(arity);
        } else if (type == ParseItem::ITEM_RANK) {
            builder.addRank(arity);
        } else if (type == ParseItem::ITEM_OR) {
            builder.addOr(arity);
        } else if (type == ParseItem::ITEM_WORD_ALTERNATIVES) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            builder.addEquiv(arity, id, weight);
            pureTermView = view;
        } else if (type == ParseItem::ITEM_WEAK_AND) {
            vespalib::stringref view = queryStack.getIndexName();
            uint32_t targetNumHits = queryStack.getTargetNumHits();
            builder.addWeakAnd(arity, targetNumHits, view);
            pureTermView = view;
        } else if (type == ParseItem::ITEM_EQUIV) {
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            builder.addEquiv(arity, id, weight);
        } else if (type == ParseItem::ITEM_NEAR) {
            uint32_t nearDistance = queryStack.getNearDistance();
            builder.addNear(arity, nearDistance);
        } else if (type == ParseItem::ITEM_ONEAR) {
            uint32_t nearDistance = queryStack.getNearDistance();
            builder.addONear(arity, nearDistance);
        } else if (type == ParseItem::ITEM_PHRASE) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            t = &builder.addPhrase(arity, view, id, weight);
            pureTermView = view;
        } else if (type == ParseItem::ITEM_SAME_ELEMENT) {
            vespalib::stringref view = queryStack.getIndexName();
            builder.addSameElement(arity, view);
            pureTermView = view;
        } else if (type == ParseItem::ITEM_WEIGHTED_SET) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            t = &builder.addWeightedSetTerm(arity, view, id, weight);
            pureTermView = vespalib::stringref();
        } else if (type == ParseItem::ITEM_DOT_PRODUCT) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            t = &builder.addDotProduct(arity, view, id, weight);
            pureTermView = vespalib::stringref();
        } else if (type == ParseItem::ITEM_WAND) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            uint32_t targetNumHits = queryStack.getTargetNumHits();
            double scoreThreshold = queryStack.getScoreThreshold();
            double thresholdBoostFactor = queryStack.getThresholdBoostFactor();
            t = &builder.addWandTerm(arity, view, id, weight,
                                     targetNumHits, scoreThreshold, thresholdBoostFactor);
            pureTermView = vespalib::stringref();
        } else if (type == ParseItem::ITEM_NOT) {
            builder.addAndNot(arity);
        } else if (type == ParseItem::ITEM_NEAREST_NEIGHBOR) {
            vespalib::stringref query_tensor_name = queryStack.getTerm();
            vespalib::stringref field_name = queryStack.getIndexName();
            uint32_t target_num_hits = queryStack.getTargetNumHits();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            bool allow_approximate = queryStack.getAllowApproximate();
            uint32_t explore_additional_hits = queryStack.getExploreAdditionalHits();
            builder.add_nearest_neighbor_term(query_tensor_name, field_name, id, weight,
                                              target_num_hits, allow_approximate, explore_additional_hits);
        } else {
            vespalib::stringref term = queryStack.getTerm();
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();

            if (type == ParseItem::ITEM_TERM) {
                t = &builder.addStringTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_PURE_WEIGHTED_STRING) {
                t = &builder.addStringTerm(term, pureTermView, id, weight);
            } else if (type == ParseItem::ITEM_PURE_WEIGHTED_LONG) {
                t = &builder.addNumberTerm(term, pureTermView, id, weight);
            } else if (type == ParseItem::ITEM_PREFIXTERM) {
                t = &builder.addPrefixTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_SUBSTRINGTERM) {
                t = &builder.addSubstringTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_EXACTSTRINGTERM) {
                t = &builder.addStringTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_SUFFIXTERM) {
                t = &builder.addSuffixTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_LOCATION_TERM) {
                Location loc(term);
                t = &builder.addLocationTerm(loc, view, id, weight);
            } else if (type == ParseItem::ITEM_NUMTERM) {
                if (term[0] == '[' || term[0] == '<' || term[0] == '>') {
                    Range range(term);
                    t = &builder.addRangeTerm(range, view, id, weight);
                } else if (term[0] == '(') {
                    // TODO: handled above, should remove this block
                    Location loc(term);
                    t = &builder.addLocationTerm(loc, view, id, weight);
                } else {
                    t = &builder.addNumberTerm(term, view, id, weight);
                }
            } else if (type == ParseItem::ITEM_PREDICATE_QUERY) {
                t = &builder.addPredicateQuery(queryStack.getPredicateQueryTerm(), view, id, weight);
            } else if (type == ParseItem::ITEM_REGEXP) {
                t = &builder.addRegExpTerm(term, view, id, weight);
            } else {
                LOG(error, "Unable to create query tree from stack dump. node type = %d.", type);
            }
        }
        return t;
    }
};

}
