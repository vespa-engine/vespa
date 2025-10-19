// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tree/node.h"
#include "tree/querybuilder.h"

#include <vespa/searchlib/common/geo_location.h>
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>
#include <vespa/searchlib/query/tree/weighted_string_term_vector.h>

using search::query::IntegerTermVector;
using search::query::PredicateQueryTerm;
using search::query::StringTermVector;
using search::common::GeoLocation;

namespace search {

using namespace searchlib::searchprotocol::protobuf;

template <class NodeTypes>
class ProtoTreeConverterImpl {
    const ProtobufQueryTree& _proto;
    search::query::QueryBuilder<NodeTypes> _builder;
public:
    ProtoTreeConverterImpl(const ProtobufQueryTree& proto) : _proto(proto) {}
    std::unique_ptr<query::Node> convert() {
        bool ok = handle_item(_proto.root());
        if (ok) {
            return _builder.build();
        } else {
            return {};
        }
    }

    struct DecodedTermProperties {
        std::string index_view;
        query::Weight weight = query::Weight(100);
        uint32_t uniqueId;
        bool noRankFlag;
        bool noPositionDataFlag;
        bool creaFilterFlag;
        bool isSpecialTokenFlag;
        bool bad() const noexcept { return index_view.empty(); }
    };

    DecodedTermProperties fillTermProperties(const TermItemProperties& props) {
        DecodedTermProperties d;
        d.index_view = props.index();
        if (props.has_item_weight()) {
            d.weight.setPercent(props.item_weight());
        } else {
            d.weight.setPercent(100);
        }
        d.uniqueId = props.unique_id();
        d.noRankFlag = props.do_not_rank();
        d.noPositionDataFlag = props.do_not_use_position_data();
        d.creaFilterFlag = props.do_not_highlight();
        d.isSpecialTokenFlag = props.is_special_token();
        return d;
    }

    bool handle(const ItemOr& item) {
        uint32_t arity = item.children_size();
        _builder.addOr(arity);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }
    bool handle(const ItemAnd& item) {
        uint32_t arity = item.children_size();
        _builder.addAnd(arity);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }
    bool handle(const ItemAndNot& item) {
        uint32_t arity = item.children_size();
        _builder.addAndNot(arity);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }
    bool handle(const ItemRank& item) {
        uint32_t arity = item.children_size();
        _builder.addRank(arity);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }

    bool handle(const ItemNear& item) {
        uint32_t arity = item.children_size();
        uint32_t nearDistance = item.distance();
        uint32_t numNegativeTerms = item.num_negative_terms();
        uint32_t exclusionDistance = item.exclusion_distance();
        _builder.addNear(arity, nearDistance, numNegativeTerms, exclusionDistance);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }
    bool handle(const ItemOnear& item) {
        uint32_t arity = item.children_size();
        uint32_t nearDistance = item.distance();
        uint32_t numNegativeTerms = item.num_negative_terms();
        uint32_t exclusionDistance = item.exclusion_distance();
        _builder.addONear(arity, nearDistance, numNegativeTerms, exclusionDistance);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }

    bool handle(const ItemWeakAnd& item) {
        uint32_t arity = item.children_size();
        uint32_t targetNumHits = item.target_num_hits();
        std::string index = item.index();
        _builder.addWeakAnd(arity, targetNumHits, index);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }

    bool handle(const ItemWordTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addStringTerm(word, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemPrefixTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addPrefixTerm(word, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemSubstringTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addSubstringTerm(word, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemSuffixTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addSuffixTerm(word, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemExactStringTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addStringTerm(word, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemRegexp& item) {
        auto d = fillTermProperties(item.properties());
        auto regexp = item.regexp();
        auto &term = _builder.addRegExpTerm(regexp, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemFuzzy& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto prefix_match = item.prefix_match();
        auto max_edit_distance = item.max_edit_distance();
        auto prefix_lock_length = item.prefix_lock_length();
        auto &term = _builder.addFuzzyTerm(word, d.index_view, d.uniqueId, d.weight,
                                           max_edit_distance, prefix_lock_length, prefix_match);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        if (prefix_match) term.set_prefix_match(true); // nop?
        return true;
    }

    bool handle(const ItemEquiv& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.children_size();
        _builder.addEquiv(arity, d.uniqueId, d.weight);
        // not supported:
        // if (d.noRankFlag) term.setRanked(false);
        // if (d.noPositionDataFlag) term.setPositionData(false);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }
    bool handle(const ItemWordAlternatives& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.weighted_strings_size();
        auto words = std::make_unique<query::WeightedStringTermVector>(arity);
        for (const auto &child : item.weighted_strings()) {
            query::Weight weight(child.weight());
            auto word = child.value();
            words->addTerm(word, weight);
        }
        auto &term = _builder.add_word_alternatives(std::move(words), d.index_view, d.uniqueId, d.weight);
        // compare equiv above
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemWeightedSetOfString& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.weighted_strings_size();
        auto & ws = _builder.addWeightedSetTerm(arity, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) ws.setRanked(false);
        if (d.noPositionDataFlag) ws.setPositionData(false);
        for (const auto &child : item.weighted_strings()) {
            query::Weight weight(child.weight());
            ws.addTerm(child.value(), weight);
        }
        return true;
    }

    bool handle(const ItemWeightedSetOfLong& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.weighted_longs_size();
        auto & ws = _builder.addWeightedSetTerm(arity, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) ws.setRanked(false);
        if (d.noPositionDataFlag) ws.setPositionData(false);
        for (const auto &child : item.weighted_longs()) {
            query::Weight weight(child.weight());
            ws.addTerm(child.value(), weight);
        }
        return true;
    }

    bool handle(const ItemPhrase& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.children_size();
        auto &term = _builder.addPhrase(arity, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        // not meaningful?
        // if (d.noPositionDataFlag) term.setPositionData(false);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }

    bool handle(const ItemIntegerTerm& item) {
        auto d = fillTermProperties(item.properties());
        int64_t number = item.number();
        std::string word = std::format("{}", number);
        auto &term = _builder.addNumberTerm(word, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemFloatingPointTerm& item) {
        auto d = fillTermProperties(item.properties());
        double number = item.number();
        std::string word = std::format("{}", number);
        auto &term = _builder.addNumberTerm(word, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemIntegerRangeTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto b_mark = item.lower_inclusive() ? "[" : "<";
        auto e_mark = item.upper_inclusive() ? "]" : ">";
        std::string tmp = std::format("{}{};{}", b_mark, item.lower_limit(), item.upper_limit());
        if (item.has_range_limit() || item.with_diversity()) {
            tmp += std::format(";{}", item.range_limit());
            if (item.with_diversity()) {
                tmp += std::format(";{};{}",
                                   item.diversity_attribute(),
                                   item.diversity_max_per_group());
                if (item.with_diversity_cutoff()) {
                    tmp += std::format(";{}", item.diversity_cutoff_groups());
                    if (item.diversity_cutoff_strict()) {
                        tmp += ";strict";
                    }
                }
            }
        }
        tmp += e_mark;
        auto &term = _builder.addNumberTerm(tmp, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemFloatingPointRangeTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto b_mark = item.lower_inclusive() ? "[" : "<";
        auto e_mark = item.upper_inclusive() ? "]" : ">";
        std::string tmp = std::format("{}{};{}", b_mark, item.lower_limit(), item.upper_limit());
        if (item.has_range_limit() || item.with_diversity()) {
            tmp += std::format(";{}", item.range_limit());
            if (item.with_diversity()) {
                tmp += std::format(";{};{}",
                                   item.diversity_attribute(),
                                   item.diversity_max_per_group());
                if (item.with_diversity_cutoff()) {
                    tmp += std::format(";{}", item.diversity_cutoff_groups());
                    if (item.diversity_cutoff_strict()) {
                        tmp += ";strict";
                    }
                }
            }
        }
        tmp += e_mark;
        auto &term = _builder.addNumberTerm(tmp, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemSameElement& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.children_size();
        auto &term = _builder.addSameElement(arity, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }

    bool handle(const ItemDotProductOfString& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.weighted_strings_size();
        auto & dp = _builder.addDotProduct(arity, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) dp.setRanked(false);
        if (d.noPositionDataFlag) dp.setPositionData(false);
        for (const auto &child : item.weighted_strings()) {
            query::Weight weight(child.weight());
            dp.addTerm(child.value(), weight);
        }
        return true;
    }

    bool handle(const ItemDotProductOfLong& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.weighted_longs_size();
        auto & dp = _builder.addDotProduct(arity, d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) dp.setRanked(false);
        if (d.noPositionDataFlag) dp.setPositionData(false);
        for (const auto &child : item.weighted_longs()) {
            query::Weight weight(child.weight());
            dp.addTerm(child.value(), weight);
        }
        return true;
    }

    bool handle(const ItemStringWand& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.weighted_strings_size();
        uint32_t targetNumHits = item.target_num_hits();
        double scoreThreshold = item.score_threshold();
        double thresholdBoostFactor = item.threshold_boost_factor();
        auto & wand = _builder.addWandTerm(arity, d.index_view, d.uniqueId, d.weight, targetNumHits, scoreThreshold, thresholdBoostFactor);
        if (d.noRankFlag) wand.setRanked(false);
        if (d.noPositionDataFlag) wand.setPositionData(false);
        for (const auto &child : item.weighted_strings()) {
            query::Weight weight(child.weight());
            wand.addTerm(child.value(), weight);
        }
        return true;
    }

    bool handle(const ItemLongWand& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.weighted_longs_size();
        uint32_t targetNumHits = item.target_num_hits();
        double scoreThreshold = item.score_threshold();
        double thresholdBoostFactor = item.threshold_boost_factor();
        auto & wand = _builder.addWandTerm(arity, d.index_view, d.uniqueId, d.weight, targetNumHits, scoreThreshold, thresholdBoostFactor);
        if (d.noRankFlag) wand.setRanked(false);
        if (d.noPositionDataFlag) wand.setPositionData(false);
        for (const auto &child : item.weighted_longs()) {
            query::Weight weight(child.weight());
            wand.addTerm(child.value(), weight);
        }
        return true;
    }

    bool handle(const ItemPredicateQuery& item) {
        auto d = fillTermProperties(item.properties());

        auto predicateQueryTerm = std::make_unique<query::PredicateQueryTerm>();

        for (const auto& feature : item.features()) {
            std::string key = feature.key();
            std::string value = feature.value();
            uint64_t sub_queries = feature.sub_queries();
            predicateQueryTerm->addFeature(key, value, sub_queries);
        }
        for (const auto& range : item.range_features()) {
            std::string key = range.key();
            uint64_t value = range.value();
            uint64_t sub_queries = range.sub_queries();
            predicateQueryTerm->addRangeFeature(key, value, sub_queries);
        }
        auto &term = _builder.addPredicateQuery(std::move(predicateQueryTerm), d.index_view, d.uniqueId, d.weight);
        // not meaningful?
        if (d.noRankFlag) term.setRanked(false);
        // not meaningful?
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemNearestNeighbor& item) {
        auto d = fillTermProperties(item.properties());

        std::string_view query_tensor_name = item.query_tensor_name();
        uint32_t targetNumHits = item.target_num_hits();
        bool allowApproximate = item.allow_approximate();
        uint32_t exploreAdditionalHits = item.explore_additional_hits();
        double distanceThreshold = item.distance_threshold();
        auto &term = _builder.add_nearest_neighbor_term(query_tensor_name, d.index_view, d.uniqueId, d.weight,
                                           targetNumHits, allowApproximate, exploreAdditionalHits,
                                           distanceThreshold);
        // not meaningful?
        if (d.noRankFlag) term.setRanked(false);
        // not meaningful?
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemGeoLocationTerm& item) {
        auto d = fillTermProperties(item.properties());
        constexpr int M = 1000000;
        int32_t x = std::round(item.longitude() * M);
        int32_t y = std::round(item.latitude() * M);
        GeoLocation::Point center{x, y};
        int32_t radius = (item.radius() < 0) ? -1 : std::round(item.radius() * M);

        double latitudeRadians = item.latitude() * M_PI / 180.0;
        double cosLat = cos(latitudeRadians);
        if (cosLat < 0) cosLat = 0;
        GeoLocation::Aspect aspect{cosLat};

        int32_t w = std::round(item.w() * M);
        int32_t e = std::round(item.e() * M);
        int32_t s = std::round(item.s() * M);
        int32_t n = std::round(item.n() * M);

        GeoLocation::Range xRange{w, e};
        GeoLocation::Range yRange{s, n};
        GeoLocation::Box bbox{xRange, yRange};

        if (item.has_geo_circle()) {
            if (item.has_bounding_box()) {
                GeoLocation loc(bbox, center, radius, aspect);
                _builder.addLocationTerm(loc, d.index_view, d.uniqueId, d.weight);
                return true;
            } else {
                GeoLocation loc(center, radius, aspect);
                _builder.addLocationTerm(loc, d.index_view, d.uniqueId, d.weight);
                return true;
            }
        } else if (item.has_bounding_box()) {
            GeoLocation loc(bbox);
            _builder.addLocationTerm(loc, d.index_view, d.uniqueId, d.weight);
            return true;
        } else {
            return false;
        }
        // not meaningful?
        // if (d.noRankFlag) term.setRanked(false);
        // not meaningful:
        // if (d.noPositionDataFlag) term.setPositionData(false);
    }


    bool handle(const ItemStringIn& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t num_terms = item.words_size();
        auto terms = std::make_unique<StringTermVector>(num_terms);
        for (const auto& word : item.words()) {
            terms->addTerm(word);
        }
        auto &term = _builder.add_in_term(std::move(terms), query::MultiTerm::Type::STRING,
                             d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemNumericIn& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t num_terms = item.numbers_size();
        auto terms = std::make_unique<IntegerTermVector>(num_terms);
        for (int64_t number : item.numbers()) {
            terms->addTerm(number);
        }
        auto &term = _builder.add_in_term(std::move(terms), query::MultiTerm::Type::INTEGER,
                             d.index_view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }


    bool handle_item(const QueryTreeItem& qti) {
        using IC = QueryTreeItem::ItemCase;
        switch (qti.item_case()) {

        case IC::kItemTrue:
            _builder.add_true_node();
            return true;
        case IC::kItemFalse:
            _builder.add_false_node();
            return true;

        case IC::kItemOr:
            return handle(qti.item_or());
        case IC::kItemAnd:
            return handle(qti.item_and());
        case IC::kItemAndNot:
            return handle(qti.item_and_not());
        case IC::kItemRank:
            return handle(qti.item_rank());
        case IC::kItemNear:
            return handle(qti.item_near());
        case IC::kItemOnear:
            return handle(qti.item_onear());

        case IC::kItemWeakAnd:
            return handle(qti.item_weak_and());
        case IC::kItemPhrase:
            return handle(qti.item_phrase());
        case IC::kItemEquiv:
            return handle(qti.item_equiv());
        case IC::kItemWordAlternatives:
            return handle(qti.item_word_alternatives());
        case IC::kItemSameElement:
            return handle(qti.item_same_element());
        case IC::kItemDotProductOfString:
            return handle(qti.item_dot_product_of_string());
        case IC::kItemDotProductOfLong:
            return handle(qti.item_dot_product_of_long());
        case IC::kItemStringWand:
            return handle(qti.item_string_wand());
        case IC::kItemLongWand:
            return handle(qti.item_long_wand());

        case IC::kItemWordTerm:
            return handle(qti.item_word_term());
        case IC::kItemSubstringTerm:
            return handle(qti.item_substring_term());
        case IC::kItemSuffixTerm:
            return handle(qti.item_suffix_term());
        case IC::kItemPrefixTerm:
            return handle(qti.item_prefix_term());
        case IC::kItemExactstringTerm:
            return handle(qti.item_exactstring_term());
        case IC::kItemRegexp:
            return handle(qti.item_regexp());
        case IC::kItemFuzzy:
            return handle(qti.item_fuzzy());

        case IC::kItemStringIn:
            return handle(qti.item_string_in());
        case IC::kItemNumericIn:
            return handle(qti.item_numeric_in());

        case IC::kItemIntegerTerm:
            return handle(qti.item_integer_term());
        case IC::kItemFloatingPointTerm:
            return handle(qti.item_floating_point_term());
        case IC::kItemIntegerRangeTerm:
            return handle(qti.item_integer_range_term());
        case IC::kItemFloatingPointRangeTerm:
            return handle(qti.item_floating_point_range_term());

        case IC::kItemWeightedSetOfString:
            return handle(qti.item_weighted_set_of_string());
        case IC::kItemWeightedSetOfLong:
            return handle(qti.item_weighted_set_of_long());
        case IC::kItemPredicateQuery:
            return handle(qti.item_predicate_query());
        case IC::kItemNearestNeighbor:
            return handle(qti.item_nearest_neighbor());
        case IC::kItemGeoLocationTerm:
            return handle(qti.item_geo_location_term());

        case IC::ITEM_NOT_SET:
            return false;
        }
        return false;
    }
};

} // namespace
