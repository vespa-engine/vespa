// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "parse.h"
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/vespalib/stllike/string.h>

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
    vespalib::stringref _curr_index_name;
    /** The term in the current item */
    vespalib::stringref _curr_term;
    int64_t             _curr_integer_term;

    /* extra arguments */
    uint32_t _extraIntArg1;
    uint32_t _extraIntArg2;
    uint32_t _extraIntArg3;
    double   _extraDoubleArg4;
    double   _extraDoubleArg5;
    /** The predicate query specification */
    query::PredicateQueryTerm::UP _predicate_query_term;

    VESPA_DLL_LOCAL vespalib::stringref read_stringref(const char *&p);
    VESPA_DLL_LOCAL uint64_t readUint64(const char *&p);
    VESPA_DLL_LOCAL double read_double(const char *&p);
    VESPA_DLL_LOCAL uint64_t readCompressedPositiveInt(const char *&p);
    VESPA_DLL_LOCAL bool readPredicate(const char *&p);
    VESPA_DLL_LOCAL bool readNN(const char *&p);
    VESPA_DLL_LOCAL bool readComplexTerm(const char *& p);
    VESPA_DLL_LOCAL bool readFuzzy(const char *&p);
    VESPA_DLL_LOCAL bool readNext();
public:
    /**
     * Make an iterator on a buffer. To get the first item, next must be called.
     */
    SimpleQueryStackDumpIterator(vespalib::stringref buf);
    SimpleQueryStackDumpIterator(const SimpleQueryStackDumpIterator &) = delete;
    SimpleQueryStackDumpIterator& operator=(const SimpleQueryStackDumpIterator &) = delete;
    ~SimpleQueryStackDumpIterator();

    vespalib::stringref getStack() const { return vespalib::stringref(_buf, _bufEnd - _buf); }
    size_t getPosition() const { return _currPos; }

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
    ParseItem::ItemType getType() const { return _currType; }
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
    query::Weight GetWeight() const { return _currWeight; }

    /**
     * Get the unique id of the current item.
     *
     * @return unique id of current item
     **/
    uint32_t getUniqueId() const { return _currUniqueId; }

    // Get the flags of the current item.
    bool hasNoRankFlag() const { return (_currFlags & ParseItem::IFLAG_NORANK) != 0; }
    bool hasSpecialTokenFlag() const { return (_currFlags & ParseItem::IFLAG_SPECIALTOKEN) != 0; }
    bool hasNoPositionDataFlag() const { return (_currFlags & ParseItem::IFLAG_NOPOSITIONDATA) != 0; }

    uint32_t getArity() const { return _currArity; }

    uint32_t getNearDistance() const { return _extraIntArg1; }
    uint32_t getTargetHits() const { return _extraIntArg1; }
    double getDistanceThreshold() const { return _extraDoubleArg4; }
    double getScoreThreshold() const { return _extraDoubleArg4; }
    double getThresholdBoostFactor() const { return _extraDoubleArg5; }
    bool getAllowApproximate() const { return (_extraIntArg2 != 0); }
    uint32_t getExploreAdditionalHits() const { return _extraIntArg3; }

    // fuzzy match arguments
    uint32_t getFuzzyMaxEditDistance() const { return _extraIntArg1; }
    uint32_t getFuzzyPrefixLength() const { return _extraIntArg2; }

    query::PredicateQueryTerm::UP getPredicateQueryTerm() { return std::move(_predicate_query_term); }

    vespalib::stringref getIndexName() const { return _curr_index_name; }
    vespalib::stringref getTerm() const { return _curr_term; }
    int64_t getIntergerTerm() const { return _curr_integer_term; }
};

}
