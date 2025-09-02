// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tree/node.h"
#include "tree/querybuilder.h"

namespace search {

using namespace searchlib::searchprotocol::protobuf;

template <class NodeTypes>
class ProtoTreeConverterImpl {
    const ProtobufQueryTree& _proto;
    search::query::QueryBuilder<NodeTypes> _builder;
public:
    std::unique_ptr<query::Node> convert() const {
        bool ok = handle_item(_proto.root());
        if (ok) {
            return _builder.build();
        } else {
            return {};
        }
    }

    struct DecodedTermProperties {
        std::string_view index_view;
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
        _builder.addNear(arity, nearDistance);
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
        _builder.addONear(arity, nearDistance);
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
        auto &term = _builder.addStringTerm(word, d.view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemPrefixTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addPrefixTerm(word, d.view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemSubstringTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addSubstringTerm(word, d.view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemSuffixTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addSuffixTerm(word, d.view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemExactStringTerm& item) {
        auto d = fillTermProperties(item.properties());
        auto word = item.word();
        auto &term = _builder.addExactStringTerm(word, d.view, d.uniqueId, d.weight);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }
    bool handle(const ItemRegexp& item) {
        auto d = fillTermProperties(item.properties());
        auto regexp = item.regexp();
        auto &term = _builder.addRegExpTerm(regexp, d.view, d.uniqueId, d.weight);
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
        auto &term = _builder.addFuzzyTerm(word, d.view, d.uniqueId, d.weight,
                                           max_edit_distance, prefix_lock_length, prefix_match);
        if (d.noRankFlag) term.setRanked(false);
        if (d.noPositionDataFlag) term.setPositionData(false);
        return true;
    }

    bool handle(const ItemEquiv& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.children_size();
        _builder.addEquiv(arity, d.uniqueId, d.weight);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }
    bool handle(const ItemWordAlternatives& item) {
        auto d = fillTermProperties(item.properties());
        uint32_t arity = item.children_size();
        _builder.addEquiv(arity, d.uniqueId, d.weight);
        for (const auto &child : item.children()) {
            if (!handle_item(child)) {
                return false;
            }
        }
        return true;
    }

    bool handle(const ItemWeightedSetOfString& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_WEIGHTED_SET;
        _d.arity = item.weighted_strings_size();
        return true;
    }

    bool handle(const ItemWeightedSetOfLong& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_WEIGHTED_SET;
        _d.arity = item.weighted_longs_size();
        return true;
    }

    bool handle(const ItemPhrase& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_PHRASE;
        _d.arity = item.children_size();
        return true;
    }

    bool handle(const ItemIntegerTerm& item,
                QueryStackIterator::Data& _d,
                std::string& tmp)
    {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_NUMTERM;
        _d.integerTerm = item.number();
        tmp = std::format("{}", item.number());
        _d.term_view = tmp;
        return true;
    }

    bool handle(const ItemFloatingPointTerm& item,
                QueryStackIterator::Data& _d,
                std::string& tmp)
    {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_NUMTERM;
        tmp = std::format("{}", item.number());
        _d.term_view = tmp;
        return true;
    }

    bool handle(const ItemIntegerRangeTerm& item,
                QueryStackIterator::Data& _d,
                std::string& tmp)
    {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_NUMTERM;
        tmp = std::format("[{};{}", item.lower_limit(), item.upper_limit());
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
        tmp += "]";
        _d.term_view = tmp;
        tmp = std::format("[{};{}]", item.lower_limit(), item.upper_limit());
        _d.term_view = tmp;
        return true;
    }
    bool handle(const ItemFloatingPointRangeTerm& item,
                QueryStackIterator::Data& _d,
                std::string& tmp)
    {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_NUMTERM;
        auto b_mark = item.lower_inclusive() ? "[" : "<";
        auto e_mark = item.upper_inclusive() ? "]" : "]";
        tmp = std::format("{}{};{}", b_mark, item.lower_limit(), item.upper_limit());
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
        _d.term_view = tmp;
        return true;
    }

    bool handle(const ItemSameElement& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_SAME_ELEMENT;
        _d.arity = item.children_size();
        return true;
    }

    bool handle(const ItemDotProductOfString& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_DOT_PRODUCT;
        _d.arity = item.weighted_strings_size();
        return true;
    }

    bool handle(const ItemDotProductOfLong& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_DOT_PRODUCT;
        _d.arity = item.weighted_longs_size();
        return true;
    }

    bool handle(const ItemStringWand& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_WAND;
        _d.arity = item.weighted_strings_size();
        _d.targetHits = item.target_num_hits();
        _d.scoreThreshold = item.score_threshold();
        _d.thresholdBoostFactor = item.threshold_boost_factor();
        return true;
    }

    bool handle(const ItemLongWand& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_WAND;
        _d.arity = item.weighted_longs_size();
        _d.targetHits = item.target_num_hits();
        _d.scoreThreshold = item.score_threshold();
        _d.thresholdBoostFactor = item.threshold_boost_factor();
        return true;
    }

    bool handle(const ItemPredicateQuery& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_PREDICATE_QUERY;
        _d.predicateQueryTerm = std::make_unique<PredicateQueryTerm>();
        for (const auto& feature : item.features()) {
            std::string key = feature.key();
            std::string value = feature.value();
            uint64_t sub_queries = feature.sub_queries();
            _d.predicateQueryTerm->addFeature(key, value, sub_queries);
        }
        for (const auto& range : item.range_features()) {
            std::string key = range.key();
            uint64_t value = range.value();
            uint64_t sub_queries = range.sub_queries();
            _d.predicateQueryTerm->addRangeFeature(key, value, sub_queries);
        }
        return true;
    }

    bool handle(const ItemNearestNeighbor& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_NEAREST_NEIGHBOR;
        _d.term_view = item.query_tensor_name();
        _d.targetHits = item.target_num_hits();
        _d.allowApproximateFlag = item.allow_approximate();
        _d.exploreAdditionalHits = item.explore_additional_hits();
        _d.distanceThreshold = item.distance_threshold();
        _d.arity = 0;
        return true;
    }

// TODO geo bounding box
    bool handle(const ItemGeoLocationTerm& item,
                QueryStackIterator::Data& _d,
                std::string& tmp)
    {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_GEO_LOCATION_TERM;
        int x = item.longitude() * 1000000;
        int y = item.latitude() * 1000000;
        int radius = item.radius() * 1000000;
        if (radius < 0)
            radius = -1;
        double radians = item.latitude() * M_PI / 180.0;
        double cosLat = cos(radians);
        int64_t aspect = cosLat * 4294967295L;
        if (aspect < 0)
            aspect = 0;
        tmp = std::format("(2,{},{},{},0,1,0,{})", x, y, radius, aspect);
        _d.term_view = tmp;
        return true;
    }


    bool handle(const ItemStringIn& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_STRING_IN;
        uint32_t num_terms = item.words_size();
        auto terms = std::make_unique<StringTermVector>(num_terms);
        for (const auto& word : item.words()) {
            terms->addTerm(word);
        }
        _d.termVector = std::move(terms);
        return true;
    }

    bool handle(const ItemNumericIn& item) {
        fillTermProperties(item.properties(), _d);
        _d.itemType = ParseItem::ItemType::ITEM_NUMERIC_IN;
        uint32_t num_terms = item.numbers_size();
        auto terms = std::make_unique<IntegerTermVector>(num_terms);
        for (int64_t number : item.numbers()) {
            terms->addTerm(number);
        }
        _d.termVector = std::move(terms);
        return true;
    }


    bool handle_item(const QueryTreeItem& qti) {
        switch (qti.item_case()) {

        case IC::kItemTrue:
            builder.add_true_node();
            return true;
        case IC::kItemFalse:
            builder.add_false_node();
            return true;

        case IC::kItemPureWeightedString:
            return false;
        case IC::kItemPureWeightedLong:
            return false;

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
