// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node.h"
#include "querybuilder.h"
#include "termnodes.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/issue.h>
#include <charconv>

namespace search::query {

class StackDumpQueryCreatorHelper {
public:
    static void populateMultiTerm(SimpleQueryStackDumpIterator &queryStack, QueryBuilderBase & builder, MultiTerm & mt);
    static void reportError(const SimpleQueryStackDumpIterator &queryStack, const QueryBuilderBase & builder);
};

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
        vespalib::string pureTermView;
        while (!builder.hasError() && queryStack.next()) {
            Term *t = createQueryTerm(queryStack, builder, pureTermView);
            if (!builder.hasError() && t) {
                if (queryStack.hasNoRankFlag()) {
                    t->setRanked(false);
                }
                if (queryStack.hasNoPositionDataFlag()) {
                    t->setPositionData(false);
                }
                if (queryStack.has_prefix_match_semantics()) [[unlikely]] {
                    t->set_prefix_match(true);
                }
            }
        }
        if (builder.hasError()) {
            StackDumpQueryCreatorHelper::reportError(queryStack, builder);
        }
        return builder.build();
    }

private:
    static void populateMultiTerm(search::SimpleQueryStackDumpIterator &queryStack, QueryBuilderBase & builder, MultiTerm & mt) {
        StackDumpQueryCreatorHelper::populateMultiTerm(queryStack, builder, mt);
    }
    static Term *
    createQueryTerm(search::SimpleQueryStackDumpIterator &queryStack, QueryBuilder<NodeTypes> & builder, vespalib::string & pureTermView) {
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
            std::string_view view = queryStack.index_as_view();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            builder.addEquiv(arity, id, weight);
            pureTermView = view;
        } else if (type == ParseItem::ITEM_WEAK_AND) {
            uint32_t targetNumHits = queryStack.getTargetHits();
            builder.addWeakAnd(arity, targetNumHits, queryStack.index_as_string());
            pureTermView = queryStack.index_as_view();
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
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            t = &builder.addPhrase(arity, queryStack.index_as_string(), id, weight);
            pureTermView = queryStack.index_as_view();
        } else if (type == ParseItem::ITEM_SAME_ELEMENT) {
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            builder.addSameElement(arity, queryStack.index_as_string(), id, weight);
            pureTermView = queryStack.index_as_view();
        } else if (type == ParseItem::ITEM_WEIGHTED_SET) {
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
           auto & ws = builder.addWeightedSetTerm(arity, queryStack.index_as_string(), id, weight);
            pureTermView = std::string_view();
            populateMultiTerm(queryStack, builder, ws);
            t = &ws;
        } else if (type == ParseItem::ITEM_DOT_PRODUCT) {
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            auto & dotProduct = builder.addDotProduct(arity, queryStack.index_as_string(), id, weight);
            pureTermView = std::string_view();
            populateMultiTerm(queryStack, builder, dotProduct);
            t = &dotProduct;
        } else if (type == ParseItem::ITEM_WAND) {
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            uint32_t targetNumHits = queryStack.getTargetHits();
            double scoreThreshold = queryStack.getScoreThreshold();
            double thresholdBoostFactor = queryStack.getThresholdBoostFactor();
            auto & wand = builder.addWandTerm(arity, queryStack.index_as_string(), id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor);
            pureTermView = std::string_view();
            populateMultiTerm(queryStack, builder, wand);
            t = & wand;
        } else if (type == ParseItem::ITEM_NOT) {
            builder.addAndNot(arity);
        } else if (type == ParseItem::ITEM_NEAREST_NEIGHBOR) {
            std::string_view query_tensor_name = queryStack.getTerm();
            uint32_t target_num_hits = queryStack.getTargetHits();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();
            bool allow_approximate = queryStack.getAllowApproximate();
            uint32_t explore_additional_hits = queryStack.getExploreAdditionalHits();
            double distance_threshold = queryStack.getDistanceThreshold();
            builder.add_nearest_neighbor_term(query_tensor_name, queryStack.index_as_string(), id, weight,
                                              target_num_hits, allow_approximate, explore_additional_hits,
                                              distance_threshold);
        } else if (type == ParseItem::ITEM_TRUE) {
            builder.add_true_node();
        } else if (type == ParseItem::ITEM_FALSE) {
            builder.add_false_node();
        } else {
            vespalib::string term(queryStack.getTerm());
            vespalib::string view = queryStack.index_as_string();
            int32_t id = queryStack.getUniqueId();
            Weight weight = queryStack.GetWeight();

            if (type == ParseItem::ITEM_TERM) {
                t = &builder.addStringTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_PURE_WEIGHTED_STRING) {
                t = &builder.addStringTerm(term, pureTermView, id, weight);
            } else if (type == ParseItem::ITEM_PURE_WEIGHTED_LONG) {
                char buf[24];
                auto res = std::to_chars(buf, buf + sizeof(buf), queryStack.getIntegerTerm(), 10);
                t = &builder.addNumberTerm(vespalib::string(buf, res.ptr - buf), pureTermView, id, weight);
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
                if (Term::isPossibleRangeTerm(term)) {
                    Range range({vespalib::string(term)});
                    t = &builder.addRangeTerm(range, view, id, weight);
                } else {
                    t = &builder.addNumberTerm(term, view, id, weight);
                }
            } else if (type == ParseItem::ITEM_PREDICATE_QUERY) {
                t = &builder.addPredicateQuery(queryStack.getPredicateQueryTerm(), view, id, weight);
            } else if (type == ParseItem::ITEM_REGEXP) {
                t = &builder.addRegExpTerm(term, view, id, weight);
            } else if (type == ParseItem::ITEM_FUZZY) {
                uint32_t max_edit_distance  = queryStack.fuzzy_max_edit_distance();
                uint32_t prefix_lock_length = queryStack.fuzzy_prefix_lock_length();
                bool     prefix_match       = queryStack.has_prefix_match_semantics();
                t = &builder.addFuzzyTerm(term, view, id, weight, max_edit_distance, prefix_lock_length, prefix_match);
            } else if (type == ParseItem::ITEM_STRING_IN) {
                t = &builder.add_in_term(queryStack.get_terms(), MultiTerm::Type::STRING, view, id, weight);
            } else if (type == ParseItem::ITEM_NUMERIC_IN) {
                t = &builder.add_in_term(queryStack.get_terms(), MultiTerm::Type::INTEGER, view, id, weight);
            } else {
                vespalib::Issue::report("query builder: Unable to create query tree from stack dump. node type = %d.", type);
            }
        }
        return t;
    }
};

}
