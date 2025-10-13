// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "from_proto.h"
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <format>
#include <cmath>

using search::query::IntegerTermVector;
using search::query::StringTermVector;
using search::query::PredicateQueryTerm;

using namespace searchlib::searchprotocol::protobuf;

namespace search {
namespace {

using TreeItem = ProtoTreeIterator::TreeItem;

void walk(const QueryTreeItem &item, std::vector<TreeItem>& target);

void walk(const PureWeightedString &item, std::vector<TreeItem>& target) {
    target.push_back(TreeItem(&item));
}
void walk(const PureWeightedLong &item, std::vector<TreeItem>& target) {
    target.push_back(TreeItem(&item));
}

template<typename T>
void walk_children(const T& t, std::vector<TreeItem>& target) {
    for (const auto& child : t) {
        walk(child, target);
    }
}

void walk(const QueryTreeItem &item, std::vector<TreeItem>& target) {
    target.push_back(TreeItem(&item));
    using IC = QueryTreeItem::ItemCase;
    switch (item.item_case()) {
    case IC::kItemOr:
        return walk_children(item.item_or().children(), target);
    case IC::kItemAnd:
        return walk_children(item.item_and().children(), target);
    case IC::kItemAndNot:
        return walk_children(item.item_and_not().children(), target);
    case IC::kItemRank:
        return walk_children(item.item_rank().children(), target);
    case IC::kItemNear:
        return walk_children(item.item_near().children(), target);
    case IC::kItemOnear:
        return walk_children(item.item_onear().children(), target);
    case IC::kItemWeakAnd:
        return walk_children(item.item_weak_and().children(), target);
    case IC::kItemPhrase:
        return walk_children(item.item_phrase().children(), target);
    case IC::kItemEquiv:
        return walk_children(item.item_equiv().children(), target);
    case IC::kItemWeightedSetOfString:
        return walk_children(item.item_weighted_set_of_string().weighted_strings(), target);
    case IC::kItemWeightedSetOfLong:
        return walk_children(item.item_weighted_set_of_long().weighted_longs(), target);
    case IC::kItemSameElement:
        return walk_children(item.item_same_element().children(), target);
    case IC::kItemDotProductOfString:
        return walk_children(item.item_dot_product_of_string().weighted_strings(), target);
    case IC::kItemDotProductOfLong:
        return walk_children(item.item_dot_product_of_long().weighted_longs(), target);
    case IC::kItemStringWand:
        return walk_children(item.item_string_wand().weighted_strings(), target);
    case IC::kItemLongWand:
        return walk_children(item.item_long_wand().weighted_longs(), target);
    case IC::kItemWordAlternatives:
        return walk_children(item.item_word_alternatives().weighted_strings(), target);
    default:
        return;
    }
}

bool fillTermProperties(const TermItemProperties& props, QueryStackIterator::Data& _d) {
    _d.index_view = props.index();
    if (props.has_item_weight()) {
        _d.weight.setPercent(props.item_weight());
    } else {
        _d.weight.setPercent(100);
    }
    _d.uniqueId = props.unique_id();
    _d.noRankFlag = props.do_not_rank();
    _d.noPositionDataFlag = props.do_not_use_position_data();
    _d.creaFilterFlag = props.do_not_highlight();
    _d.isSpecialTokenFlag = props.is_special_token();
    return ! _d.index_view.empty();
}

bool handle(const ItemOr& item, QueryStackIterator::Data& _d) {
    _d.itemType = ParseItem::ItemType::ITEM_OR;
    _d.arity = item.children_size();
    return true;
}
bool handle(const ItemAnd& item, QueryStackIterator::Data& _d) {
    _d.itemType = ParseItem::ItemType::ITEM_AND;
    _d.arity = item.children_size();
    return true;
}
bool handle(const ItemAndNot& item, QueryStackIterator::Data& _d) {
    _d.itemType = ParseItem::ItemType::ITEM_NOT;
    _d.arity = item.children_size();
    return true;
}
bool handle(const ItemRank& item, QueryStackIterator::Data& _d) {
    _d.itemType = ParseItem::ItemType::ITEM_RANK;
    _d.arity = item.children_size();
    return true;
}

bool handle(const ItemNear& item, QueryStackIterator::Data& _d) {
    _d.itemType = ParseItem::ItemType::ITEM_NEAR;
    _d.arity = item.children_size();
    _d.nearDistance = item.distance();
    return true;
}
bool handle(const ItemOnear& item, QueryStackIterator::Data& _d) {
    _d.itemType = ParseItem::ItemType::ITEM_ONEAR;
    _d.arity = item.children_size();
    _d.nearDistance = item.distance();
    return true;
}

bool handle(const ItemWeakAnd& item, QueryStackIterator::Data& _d) {
    _d.itemType = ParseItem::ItemType::ITEM_WEAK_AND;
    _d.index_view = item.index();
    _d.targetHits = item.target_num_hits();
    _d.arity = item.children_size();
    return true;
}

bool handle(const ItemWordTerm& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_TERM;
    _d.term_view = item.word();
    return true;
}
bool handle(const ItemPrefixTerm& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_PREFIXTERM;
    _d.term_view = item.word();
    return true;
}
bool handle(const ItemSubstringTerm& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_SUBSTRINGTERM;
    _d.term_view = item.word();
    return true;
}
bool handle(const ItemSuffixTerm& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_SUFFIXTERM;
    _d.term_view = item.word();
    return true;
}
bool handle(const ItemExactStringTerm& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_EXACTSTRINGTERM;
    _d.term_view = item.word();
    return true;
}
bool handle(const ItemRegexp& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_REGEXP;
    _d.term_view = item.regexp();
    return true;
}

bool handle(const ItemEquiv& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_EQUIV;
    _d.arity = item.children_size();
    return true;
}

bool handle(const ItemWordAlternatives& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_WORD_ALTERNATIVES;
    _d.arity = item.weighted_strings_size();
    return true;
}

bool handle(const ItemWeightedSetOfString& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_WEIGHTED_SET;
    _d.arity = item.weighted_strings_size();
    return true;
}

bool handle(const ItemWeightedSetOfLong& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_WEIGHTED_SET;
    _d.arity = item.weighted_longs_size();
    return true;
}

bool handle(const ItemPhrase& item, QueryStackIterator::Data& _d) {
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
    auto b_mark = item.lower_inclusive() ? "[" : "<";
    auto e_mark = item.upper_inclusive() ? "]" : ">";
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

bool handle(const ItemFloatingPointRangeTerm& item,
            QueryStackIterator::Data& _d,
            std::string& tmp)
{
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_NUMTERM;
    auto b_mark = item.lower_inclusive() ? "[" : "<";
    auto e_mark = item.upper_inclusive() ? "]" : ">";
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

bool handle(const ItemSameElement& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_SAME_ELEMENT;
    _d.arity = item.children_size();
    return true;
}

bool handle(const ItemDotProductOfString& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_DOT_PRODUCT;
    _d.arity = item.weighted_strings_size();
    return true;
}

bool handle(const ItemDotProductOfLong& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_DOT_PRODUCT;
    _d.arity = item.weighted_longs_size();
    return true;
}

bool handle(const ItemStringWand& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_WAND;
    _d.arity = item.weighted_strings_size();
    _d.targetHits = item.target_num_hits();
    _d.scoreThreshold = item.score_threshold();
    _d.thresholdBoostFactor = item.threshold_boost_factor();
    return true;
}

bool handle(const ItemLongWand& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_WAND;
    _d.arity = item.weighted_longs_size();
    _d.targetHits = item.target_num_hits();
    _d.scoreThreshold = item.score_threshold();
    _d.thresholdBoostFactor = item.threshold_boost_factor();
    return true;
}

bool handle(const ItemPredicateQuery& item, QueryStackIterator::Data& _d) {
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

bool handle(const ItemNearestNeighbor& item, QueryStackIterator::Data& _d) {
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

bool handle(const ItemGeoLocationTerm& item,
            QueryStackIterator::Data& _d,
            std::string& tmp)
{
    constexpr int M = 1000000;
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_GEO_LOCATION_TERM;
    tmp = "";
    if (item.has_geo_circle()) {
        int x = item.longitude() * M;
        int y = item.latitude() * M;
        int radius = item.radius() * M;
        if (radius < 0)
            radius = -1;
        double radians = item.latitude() * M_PI / 180.0;
        double cosLat = cos(radians);
        int64_t aspect = cosLat * 4294967295L;
        if (aspect < 0)
            aspect = 0;
        tmp += std::format("(2,{},{},{},0,1,0,{})", x, y, radius, aspect);
    }
    if (item.has_bounding_box()) {
        int w = item.w() * M;
        int s = item.s() * M;
        int e = item.e() * M;
        int n = item.n() * M;
        tmp = std::format("[2,{},{},{},{}]", w, s, e, n);
    }
    if (tmp == "") return false;
    _d.term_view = tmp;
    return true;
}

bool handle(const ItemFuzzy& item, QueryStackIterator::Data& _d) {
    fillTermProperties(item.properties(), _d);
    _d.itemType = ParseItem::ItemType::ITEM_FUZZY;
    _d.term_view = item.word();
    _d.prefix_match_semantics_flag = item.prefix_match();
    _d.fuzzy_max_edit_distance = item.max_edit_distance();
    _d.fuzzy_prefix_lock_length = item.prefix_lock_length();
    _d.arity = 0;
    return true;
}

bool handle(const ItemStringIn& item, QueryStackIterator::Data& _d) {
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

bool handle(const ItemNumericIn& item, QueryStackIterator::Data& _d) {
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

} // namespace <unnamed>

bool ProtoTreeIterator::handle_variant_item(TreeItem item) {
    auto visitor = [&](const auto *p) -> bool {
        return handle_item(*p);
    };
    return std::visit(visitor, item);
}

bool ProtoTreeIterator::handle_item(const PureWeightedString& item) {
    _d.itemType = ParseItem::ItemType::ITEM_PURE_WEIGHTED_STRING;
    _d.term_view = item.value();
    _d.weight.setPercent(item.weight());
    return true;
}

bool ProtoTreeIterator::handle_item(const PureWeightedLong& item) {
    _d.itemType = ParseItem::ItemType::ITEM_PURE_WEIGHTED_LONG;
    _d.integerTerm = item.value();
    _d.weight.setPercent(item.weight());
    return true;
}


bool ProtoTreeIterator::handle_item(const QueryTreeItem& qsi) {
    using IC = QueryTreeItem::ItemCase;
    switch (qsi.item_case()) {

    case IC::kItemTrue:
        _d.itemType = ParseItem::ItemType::ITEM_TRUE;
        return true;
    case IC::kItemFalse:
        _d.itemType = ParseItem::ItemType::ITEM_FALSE;
        return true;

    case IC::kItemOr:
        return handle(qsi.item_or(), _d);
    case IC::kItemAnd:
        return handle(qsi.item_and(), _d);
    case IC::kItemAndNot:
        return handle(qsi.item_and_not(), _d);
    case IC::kItemRank:
        return handle(qsi.item_rank(), _d);
    case IC::kItemNear:
        return handle(qsi.item_near(), _d);
    case IC::kItemOnear:
        return handle(qsi.item_onear(), _d);

    case IC::kItemWeakAnd:
        return handle(qsi.item_weak_and(), _d);
    case IC::kItemPhrase:
        return handle(qsi.item_phrase(), _d);
    case IC::kItemEquiv:
        return handle(qsi.item_equiv(), _d);
    case IC::kItemWordAlternatives:
        return handle(qsi.item_word_alternatives(), _d);
    case IC::kItemSameElement:
        return handle(qsi.item_same_element(), _d);
    case IC::kItemDotProductOfString:
        return handle(qsi.item_dot_product_of_string(), _d);
    case IC::kItemDotProductOfLong:
        return handle(qsi.item_dot_product_of_long(), _d);
    case IC::kItemStringWand:
        return handle(qsi.item_string_wand(), _d);
    case IC::kItemLongWand:
        return handle(qsi.item_long_wand(), _d);

    case IC::kItemWordTerm:
        return handle(qsi.item_word_term(), _d);
    case IC::kItemSubstringTerm:
        return handle(qsi.item_substring_term(), _d);
    case IC::kItemSuffixTerm:
        return handle(qsi.item_suffix_term(), _d);
    case IC::kItemPrefixTerm:
        return handle(qsi.item_prefix_term(), _d);
    case IC::kItemExactstringTerm:
        return handle(qsi.item_exactstring_term(), _d);
    case IC::kItemRegexp:
        return handle(qsi.item_regexp(), _d);
    case IC::kItemFuzzy:
        return handle(qsi.item_fuzzy(), _d);

    case IC::kItemStringIn:
        return handle(qsi.item_string_in(), _d);
    case IC::kItemNumericIn:
        return handle(qsi.item_numeric_in(), _d);

    case IC::kItemIntegerTerm:
        return handle(qsi.item_integer_term(), _d, _serialized_term);
    case IC::kItemFloatingPointTerm:
        return handle(qsi.item_floating_point_term(), _d, _serialized_term);
    case IC::kItemIntegerRangeTerm:
        return handle(qsi.item_integer_range_term(), _d, _serialized_term);
    case IC::kItemFloatingPointRangeTerm:
        return handle(qsi.item_floating_point_range_term(), _d, _serialized_term);

    case IC::kItemWeightedSetOfString:
        return handle(qsi.item_weighted_set_of_string(), _d);
    case IC::kItemWeightedSetOfLong:
        return handle(qsi.item_weighted_set_of_long(), _d);
    case IC::kItemPredicateQuery:
        return handle(qsi.item_predicate_query(), _d);
    case IC::kItemNearestNeighbor:
        return handle(qsi.item_nearest_neighbor(), _d);
    case IC::kItemGeoLocationTerm:
        return handle(qsi.item_geo_location_term(), _d, _serialized_term);

    case IC::ITEM_NOT_SET:
        return false;
    }
    return false;
}

ProtoTreeIterator::~ProtoTreeIterator() = default;

ProtoTreeIterator::ProtoTreeIterator(const ProtobufQueryTree& proto_query_tree)
  : _proto(proto_query_tree),
    _items(),
    _pos(0),
    _serialized_term()
{
    walk(_proto.root(), _items);
}

bool ProtoTreeIterator::next() {
    if (_pos < _items.size()) {
        return handle_variant_item(_items[_pos++]);
    }
    return false;
}

} // namespace search
