// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node.h"
#include "querybuilder.h"
#include "term.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/issue.h>
#include <charconv>

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
            vespalib::Issue::report("Unable to create query tree from stack dump. Failed at position %ld out of %ld bytes %s",
                       queryStack.getPosition(), stack.size(), builder.error().c_str());
            LOG(error, "got bad query stack: %s", vespalib::HexDump(stack.data(), stack.size()).toString().c_str());
        }
        return builder.build();
    }

private:
    static void populateMultiTerm(search::SimpleQueryStackDumpIterator &queryStack, QueryBuilderBase & builder, MultiTerm & mt) {
        uint32_t added(0);
        for (added = 0; (added < mt.getNumTerms()) && queryStack.next(); added++) {
            ParseItem::ItemType type = queryStack.getType();
            switch (type) {
                case ParseItem::ITEM_PURE_WEIGHTED_LONG:
                    mt.addTerm(queryStack.getIntergerTerm(), queryStack.GetWeight());
                    break;
                case ParseItem::ITEM_PURE_WEIGHTED_STRING:
                    mt.addTerm(queryStack.getTerm(), queryStack.GetWeight());
                    break;
                default:
                    builder.reportError(vespalib::make_string("Got unexpected node %d for multiterm node at child term %d", type, added));
                    return;
            }
        }
        if (added < mt.getNumTerms()) {
            builder.reportError(vespalib::make_string("Too few nodes(%d) for multiterm(%d)", added, mt.getNumTerms()));
        }
    }
    static Term *
    createQueryTerm(search::SimpleQueryStackDumpIterator &queryStack, QueryBuilder<NodeTypes> & builder, vespalib::stringref & pureTermView) {
        uint32_t arity = queryStack.getArity();
        ParseItem::ItemType type = queryStack.getType();
        Node::UP node;
        Term *t = nullptr;
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
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            builder.addSameElement(arity, view, id, weight);
            pureTermView = view;
        } else if (type == ParseItem::ITEM_WEIGHTED_SET) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
           auto & ws = builder.addWeightedSetTerm(arity, view, id, weight);
            pureTermView = vespalib::stringref();
            populateMultiTerm(queryStack, builder, ws);
            t = &ws;
        } else if (type == ParseItem::ITEM_DOT_PRODUCT) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            auto & dotProduct = builder.addDotProduct(arity, view, id, weight);
            pureTermView = vespalib::stringref();
            populateMultiTerm(queryStack, builder, dotProduct);
            t = &dotProduct;
        } else if (type == ParseItem::ITEM_WAND) {
            vespalib::stringref view = queryStack.getIndexName();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            uint32_t targetNumHits = queryStack.getTargetNumHits();
            double scoreThreshold = queryStack.getScoreThreshold();
            double thresholdBoostFactor = queryStack.getThresholdBoostFactor();
            auto & wand = builder.addWandTerm(arity, view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor);
            pureTermView = vespalib::stringref();
            populateMultiTerm(queryStack, builder, wand);
            t = & wand;
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
            double distance_threshold = queryStack.getDistanceThreshold();
            builder.add_nearest_neighbor_term(query_tensor_name, field_name, id, weight,
                                              target_num_hits, allow_approximate, explore_additional_hits,
                                              distance_threshold);
        } else if (type == ParseItem::ITEM_TRUE) {
            builder.add_true_node();
        } else if (type == ParseItem::ITEM_FALSE) {
            builder.add_false_node();
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
                char buf[24];
                auto res = std::to_chars(buf, buf + sizeof(buf), queryStack.getIntergerTerm(), 10);
                t = &builder.addNumberTerm(vespalib::stringref(buf, res.ptr - buf), pureTermView, id, weight);
            } else if (type == ParseItem::ITEM_PREFIXTERM) {
                t = &builder.addPrefixTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_SUBSTRINGTERM) {
                t = &builder.addSubstringTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_EXACTSTRINGTERM) {
                t = &builder.addStringTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_SUFFIXTERM) {
                t = &builder.addSuffixTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_GEO_LOCATION_TERM) {
                search::common::GeoLocationParser parser;
                if (! parser.parseNoField(term)) {
                    vespalib::Issue::report("query builder: invalid geo location term '%s'", term.data());
                }
                Location loc(parser.getGeoLocation());
                t = &builder.addLocationTerm(loc, view, id, weight);
            } else if (type == ParseItem::ITEM_NUMTERM) {
                if (term[0] == '[' || term[0] == '<' || term[0] == '>') {
                    Range range(term);
                    t = &builder.addRangeTerm(range, view, id, weight);
                } else {
                    t = &builder.addNumberTerm(term, view, id, weight);
                }
            } else if (type == ParseItem::ITEM_PREDICATE_QUERY) {
                t = &builder.addPredicateQuery(queryStack.getPredicateQueryTerm(), view, id, weight);
            } else if (type == ParseItem::ITEM_REGEXP) {
                t = &builder.addRegExpTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_FUZZY) {
                uint32_t maxEditDistance = queryStack.getFuzzyMaxEditDistance();
                uint32_t prefixLength = queryStack.getFuzzyPrefixLength();
                t = &builder.addFuzzyTerm(term, view, id, weight, maxEditDistance, prefixLength);
            } else {
                vespalib::Issue::report("query builder: Unable to create query tree from stack dump. node type = %d.", type);
            }
        }
        return t;
    }
};

}
