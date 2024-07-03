// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "parse.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::query {

class PredicateQueryTerm;
class TermVector;

}

namespace search {
/**
 * An iterator to be used on a buffer that is a stack dump
 * of a SimpleQueryStack.
 */
class SimpleQueryStackDumpIterator
{
private:
    /** Pointer to the start of the input buffer */
    const char *_buf;
    /** Pointer to just past the input buffer */
    const char *_bufEnd;
    /** Pointer to the position of the current item in the buffer */
    uint32_t    _currPos;
    /** Pointer to after the current item */
    uint32_t    _currEnd;
    /** The type of the current item */
    ParseItem::ItemType _currType;
    /** flags of the current item **/
    uint8_t _currFlags;
    /** Rank weight of current item **/
    query::Weight _currWeight;
    /** unique id of the current item **/
    uint32_t _currUniqueId;
    /** The arity of the current item */
    uint32_t _currArity;
    /** The index name (field name) in the current item */
    std::string_view _curr_index_name;
    /** The term in the current item */
    std::string_view _curr_term;
    int64_t             _curr_integer_term;

    /* extra arguments */
    uint32_t _extraIntArg1;
    uint32_t _extraIntArg2;
    uint32_t _extraIntArg3;
    double   _extraDoubleArg4;
    double   _extraDoubleArg5;
    /** The predicate query specification */
    std::unique_ptr<query::PredicateQueryTerm> _predicate_query_term;
    std::unique_ptr<query::TermVector>         _terms;

    VESPA_DLL_LOCAL std::string_view read_string_view(const char *&p);
    VESPA_DLL_LOCAL uint64_t readCompressedPositiveInt(const char *&p);
    VESPA_DLL_LOCAL int64_t readCompressedInt(const char *&p);
    template <typename T>
    VESPA_DLL_LOCAL T read_value(const char*& p);
    VESPA_DLL_LOCAL void readPredicate(const char *&p);
    VESPA_DLL_LOCAL void readNN(const char *&p);
    VESPA_DLL_LOCAL void readComplexTerm(const char *& p);
    VESPA_DLL_LOCAL void readFuzzy(const char *&p);
    VESPA_DLL_LOCAL void read_string_in(const char*& p);
    VESPA_DLL_LOCAL void read_numeric_in(const char*& p);
    VESPA_DLL_LOCAL bool readNext();
public:
    /**
     * Make an iterator on a buffer. To get the first item, next must be called.
     */
    explicit SimpleQueryStackDumpIterator(std::string_view buf);
    SimpleQueryStackDumpIterator(const SimpleQueryStackDumpIterator &) = delete;
    SimpleQueryStackDumpIterator& operator=(const SimpleQueryStackDumpIterator &) = delete;
    ~SimpleQueryStackDumpIterator();

    std::string_view getStack() const noexcept { return std::string_view(_buf, _bufEnd - _buf); }
    size_t getPosition() const noexcept { return _currPos; }

    /**
     * Moves to the next item in the buffer.
     *
     * @return true if there is a new item, false if there are no more items
     * or if there was errors in extracting the next item.
     */
    bool next();

    /**
     * Get the type of the current item.
     * @return the type.
     */
    ParseItem::ItemType getType() const noexcept { return _currType; }
    /**
     * Get the type of the current item.
     * @return the type.
     */
    ParseItem::ItemCreator getCreator() const { return ParseItem::GetCreator(_currFlags); }

    /**
     * Get the rank weight of the current item.
     *
     * @return rank weight.
     **/
    query::Weight GetWeight() const noexcept { return _currWeight; }

    /**
     * Get the unique id of the current item.
     *
     * @return unique id of current item
     **/
    uint32_t getUniqueId() const noexcept { return _currUniqueId; }

    // Get the flags of the current item.
    [[nodiscard]] bool hasNoRankFlag() const noexcept {
        return (_currFlags & ParseItem::IFLAG_NORANK) != 0;
    }
    [[nodiscard]] bool hasSpecialTokenFlag() const noexcept {
        return (_currFlags & ParseItem::IFLAG_SPECIALTOKEN) != 0;
    }
    [[nodiscard]] bool hasNoPositionDataFlag() const noexcept {
        return (_currFlags & ParseItem::IFLAG_NOPOSITIONDATA) != 0;
    }
    [[nodiscard]] bool has_prefix_match_semantics() const noexcept {
        return (_currFlags & ParseItem::IFLAG_PREFIX_MATCH) != 0;
    }

    uint32_t getArity() const noexcept { return _currArity; }

    uint32_t getNearDistance() const noexcept { return _extraIntArg1; }
    uint32_t getTargetHits() const noexcept { return _extraIntArg1; }
    double getDistanceThreshold() const noexcept { return _extraDoubleArg4; }
    double getScoreThreshold() const noexcept { return _extraDoubleArg4; }
    double getThresholdBoostFactor() const noexcept { return _extraDoubleArg5; }
    bool getAllowApproximate() const noexcept { return (_extraIntArg2 != 0); }
    uint32_t getExploreAdditionalHits() const noexcept { return _extraIntArg3; }

    // fuzzy match arguments (see also: has_prefix_match_semantics() for fuzzy prefix matching)
    [[nodiscard]] uint32_t fuzzy_max_edit_distance() const noexcept { return _extraIntArg1; }
    [[nodiscard]] uint32_t fuzzy_prefix_lock_length() const noexcept { return _extraIntArg2; }

    std::unique_ptr<query::PredicateQueryTerm> getPredicateQueryTerm();
    std::unique_ptr<query::TermVector> get_terms();

    std::string_view getIndexName() const noexcept { return _curr_index_name; }
    std::string_view getTerm() const noexcept { return _curr_term; }
    int64_t getIntegerTerm() const noexcept { return _curr_integer_term; }

    static std::string_view DEFAULT_INDEX;
};

}
