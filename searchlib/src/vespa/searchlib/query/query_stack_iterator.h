// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/parsequery/parse.h>
#include <memory>
#include <string>

namespace search::query {

class PredicateQueryTerm;
class TermVector;

}

namespace search {

class QueryStackIterator {
public:
    virtual bool next() = 0;
    QueryStackIterator() = default;
    virtual ~QueryStackIterator();

    static std::string_view DEFAULT_INDEX;

    struct Data {
        ParseItem::ItemType itemType = ParseItem::ItemType::ITEM_UNDEF;

        unsigned int  noRankFlag:1 = false;
        unsigned int  noPositionDataFlag:1 = false;
        unsigned int  creaFilterFlag:1 = false;
        unsigned int  isSpecialTokenFlag:1 = false;
        unsigned int  allowApproximateFlag:1 = false;
        unsigned int  prefix_match_semantics_flag:1 = false;

        query::Weight weight = query::Weight(0);

        std::unique_ptr<query::PredicateQueryTerm> predicateQueryTerm;
        std::unique_ptr<query::TermVector> termVector;

        std::string_view index_view;
        std::string_view term_view;
        int64_t integerTerm = 0;

        double distanceThreshold = 0;
        double scoreThreshold = 0;
        double thresholdBoostFactor = 0;

        uint32_t uniqueId = 0;
        uint32_t arity = 0;
        uint32_t nearDistance = 0;
        uint32_t targetHits = 0;
        uint32_t exploreAdditionalHits = 0;
        uint32_t fuzzy_max_edit_distance = 0;
        uint32_t fuzzy_prefix_lock_length = 0;

        void clear();
    };
protected:
    Data _d;

public:
    /** Get the type of the current item. */
    ParseItem::ItemType getType() const noexcept { return _d.itemType; }

    /** Get the creator of the current item. */
    ParseItem::ItemCreator getCreator() const noexcept {
        return _d.creaFilterFlag ? ParseItem::ItemCreator::CREA_FILTER : ParseItem::ItemCreator::CREA_ORIG;
    }

    /** Get the rank weight of the current item. */
    query::Weight GetWeight() const noexcept { return _d.weight; }

    /** Get the unique id of the current item. */
    uint32_t getUniqueId() const noexcept { return _d.uniqueId; }

    /** Get the arity of the current item */
    uint32_t getArity() const noexcept { return _d.arity; }

    uint32_t getNearDistance() const noexcept { return _d.nearDistance; }
    uint32_t getTargetHits() const noexcept { return _d.targetHits; }
    double getDistanceThreshold() const noexcept { return _d.distanceThreshold; }
    double getScoreThreshold() const noexcept { return _d.scoreThreshold; }
    double getThresholdBoostFactor() const noexcept { return _d.thresholdBoostFactor; }
    uint32_t getExploreAdditionalHits() const noexcept { return _d.exploreAdditionalHits; }
    // fuzzy match arguments (see also: has_prefix_match_semantics() for fuzzy prefix matching)
    [[nodiscard]] uint32_t fuzzy_max_edit_distance() const noexcept { return _d.fuzzy_max_edit_distance; }
    [[nodiscard]] uint32_t fuzzy_prefix_lock_length() const noexcept { return _d.fuzzy_prefix_lock_length; }

    // Get the flags of the current item.
    [[nodiscard]] bool hasNoRankFlag() const noexcept { return _d.noRankFlag; }
    [[nodiscard]] bool hasNoPositionDataFlag() const noexcept { return _d.noPositionDataFlag; }
    [[nodiscard]] bool hasSpecialTokenFlag() const noexcept { return _d.isSpecialTokenFlag; }
    [[nodiscard]] bool getAllowApproximate() const noexcept { return _d.allowApproximateFlag; }
    [[nodiscard]] bool has_prefix_match_semantics() const noexcept { return _d.prefix_match_semantics_flag; }

    std::unique_ptr<query::PredicateQueryTerm> getPredicateQueryTerm();
    std::unique_ptr<query::TermVector> get_terms();

    /** The index name (field name) in the current item */
    std::string_view index_as_view() const noexcept { return _d.index_view; }

    /** The index name (field name) in the current item */
    std::string index_as_string() const noexcept { return std::string(_d.index_view); }

    /** Get the term in the current item */
    std::string_view getTerm() const noexcept { return _d.term_view; }

    int64_t getIntegerTerm() const noexcept { return _d.integerTerm; }

    virtual std::string_view getStack() const noexcept { return ""; }
    virtual size_t getPosition() const noexcept { return 0; }

    static std::unique_ptr<QueryStackIterator> dummy();
};

} // namespace
