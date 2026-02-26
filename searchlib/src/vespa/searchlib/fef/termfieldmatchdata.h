// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldpositionsiterator.h"
#include "fieldinfo.h"
#include <vespa/searchlib/common/feature.h>
#include <cstring>
#include <limits>
#include <span>

namespace search::fef {

class TermMatchDataMerger;

/**
 * Match information for a single term within a single field.
 **/
class TermFieldMatchData
{
public:
    using PositionsIterator = const TermFieldMatchDataPosition *;
    using MutablePositionsIterator = TermFieldMatchDataPosition *;
    struct Positions {
        TermFieldMatchDataPosition *_positions;
        uint16_t                    _maxElementLength;
        uint16_t                    _allocated;
    };

    union Features {
        feature_t     _rawScore;
        unsigned char _position[sizeof(TermFieldMatchDataPosition)];
        Positions     _positions;
        uint64_t      _subqueries;
    };
private:
    bool  isRawScore()  const noexcept { return _flags & RAW_SCORE_FLAG; }
    bool  isMultiPos()  const noexcept { return _flags & MULTIPOS_FLAG; }
    bool  empty() const noexcept { return _sz == 0; }
    void  clear() noexcept { _sz = 0; }
    bool  allocated() const noexcept { return isMultiPos(); }
    const TermFieldMatchDataPosition * getFixed() const noexcept { return reinterpret_cast<const TermFieldMatchDataPosition *>(_data._position); }
    TermFieldMatchDataPosition * getFixed() noexcept { return reinterpret_cast<TermFieldMatchDataPosition *>(_data._position); }
    const TermFieldMatchDataPosition * getMultiple() const noexcept { return _data._positions._positions; }
    TermFieldMatchDataPosition * getMultiple() noexcept { return _data._positions._positions; }
    int32_t  getElementWeight() const noexcept { return empty() ? 1 : allocated() ? getMultiple()->getElementWeight() : getFixed()->getElementWeight(); }
    uint32_t getMaxElementLength() const noexcept { return empty() ? 0 : allocated() ? _data._positions._maxElementLength : getFixed()->getElementLen(); }
    void appendPositionToAllocatedVector(const TermFieldMatchDataPosition &pos);
    void allocateVector();
    void resizePositionVector(size_t sz) __attribute__((noinline));
    MutablePositionsIterator mutable_begin() { return allocated() ? getMultiple() : getFixed(); }

    static constexpr uint16_t ILLEGAL_FIELD_ID = std::numeric_limits<uint16_t>::max();
    static constexpr uint16_t RAW_SCORE_FLAG = 1;
    static constexpr uint16_t MULTIPOS_FLAG = 2;
    static constexpr uint16_t UNPACK_NORMAL_FEATURES_FLAG = 4;
    static constexpr uint16_t UNPACK_INTERLEAVED_FEATURES_FLAG = 8;
    static constexpr uint16_t UNPACK_ALL_FEATURES_MASK = UNPACK_NORMAL_FEATURES_FLAG | UNPACK_INTERLEAVED_FEATURES_FLAG;

    uint32_t  _docId;
    uint16_t  _fieldId;
    uint16_t  _flags;
    uint16_t  _sz;

    // Number of occurrences and field length used when unpacking interleaved features.
    // This can exist in addition to full position features.
    uint16_t _numOccs;
    uint16_t _fieldLength;

    Features  _data;

    void finish_filter_elements();
public:
    PositionsIterator begin() const { return allocated() ? getMultiple() : getFixed(); }
    PositionsIterator end() const { return allocated() ? getMultiple() + _sz : empty() ? getFixed() : getFixed()+1; }
    size_t size() const noexcept { return _sz; }
    size_t capacity() const noexcept { return allocated() ? _data._positions._allocated : 1; }
    void reservePositions(size_t sz) {
        if (sz > capacity()) {
            if (!allocated()) {
                allocateVector();
                if (sz <= capacity()) return;
            }
            resizePositionVector(sz);
        }
    }

    /**
     * Create empty object. To complete object setup, field id must be
     * set.
     **/
    TermFieldMatchData();
    TermFieldMatchData(const TermFieldMatchData & rhs);
    ~TermFieldMatchData();
    TermFieldMatchData & operator = (const TermFieldMatchData & rhs);

    /**
     * Swaps the content of this object with the content of the given
     * term field match data object.
     *
     * @param rhs The object to swap with.
     **/
    void swap(TermFieldMatchData &rhs);

    MutablePositionsIterator populate_fixed();

    /**
     * Set which field this object has match information for.
     *
     * @return this object (for chaining)
     * @param fieldId field id
     **/
    TermFieldMatchData &setFieldId(uint32_t fieldId);

    /**
     * Obtain the field id
     *
     * @return field id
     **/
    uint32_t getFieldId() const noexcept {
        return __builtin_expect(_fieldId != ILLEGAL_FIELD_ID, true) ? _fieldId : IllegalFieldId;
    }

    /**
     * Reset the content of this match data and prepare it for use
     * with the given docid.
     *
     * @return this object (for chaining)
     * @param docId id of the document we are generating match information for
     **/
    TermFieldMatchData &reset(uint32_t docId) noexcept {
        _docId = docId;
        _sz = 0;
        _numOccs = 0;
        _fieldLength = 0;
        if (isRawScore()) {
            _data._rawScore = 0.0;
        } else if (isMultiPos()) {
            _data._positions._maxElementLength = 0;
        }
        return *this;
    }

    /**
     * Reset only the docid of this match data and prepare it for use
     * with the given docid. Assume all other are not touched.
     *
     * @return this object (for chaining)
     * @param docId id of the document we are generating match information for
     **/
    TermFieldMatchData &resetOnlyDocId(uint32_t docId) noexcept {
        _docId = docId;
        return *this;
    }

    /**
     * Indicate a match for a given docid and inject a raw score
     * instead of detailed match data. The raw score can be picked up
     * in the ranking framework by using the rawScore feature for the
     * appropriate field.
     *
     * @return this object (for chaining)
     * @param docId id of the document we have matched
     * @param score a raw score for the matched document
     **/
    TermFieldMatchData &setRawScore(uint32_t docId, feature_t score) noexcept {
        resetOnlyDocId(docId);
        enableRawScore();
        _data._rawScore = score;
        return *this;
    }
    TermFieldMatchData & enableRawScore() noexcept {
        _flags |= RAW_SCORE_FLAG;
        return *this;
    }

    /**
     * Obtain the raw score for this match data.
     *
     * @return raw score
     **/
    feature_t getRawScore() const noexcept {
        return __builtin_expect(isRawScore(), true) ? _data._rawScore : 0.0;
    }

    void setSubqueries(uint32_t docId, uint64_t subqueries) noexcept {
        resetOnlyDocId(docId);
        _data._subqueries = subqueries;
    }

    uint64_t getSubqueries() const noexcept {
        if (!empty() || isRawScore()) {
            return 0;
        }
        return _data._subqueries;
    }

    /**
     * Obtain the document id for which the data contained in this object is valid.
     *
     * @return document id
     **/
    uint32_t getDocId() const noexcept {
        return _docId;
    }

    // Returns true if this instance has match data for docId that is visible to the ranking framework.
    bool has_ranking_data(uint32_t docId) const noexcept { return docId == _docId; }

    // Returns true if this instance has match data for docId.
    bool has_data(uint32_t docId) const noexcept { return docId == _docId; }

    bool has_invalid_docid() const noexcept { return _docId == invalidId(); }

    /**
     * Obtain the weight of the first occurrence in this field, or 1
     * if no occurrences are present. This function is intended for
     * attribute matching calculations.
     *
     * @return weight
     **/
    int32_t getWeight() const noexcept {
        if (__builtin_expect(_sz == 0, false)) {
            return 1;
        }
        return __builtin_expect(allocated(), false) ? getMultiple()->getElementWeight() : getFixed()->getElementWeight();
    }

    /**
     * Add occurrence information to this match data for the current
     * document.
     *
     * @return this object (for chaining)
     * @param pos low-level occurrence information
     **/
    TermFieldMatchData &appendPosition(const TermFieldMatchDataPosition &pos) {
        if (_sz == 0 && !allocated()) {
            _sz = 1;
            new (_data._position) TermFieldMatchDataPosition(pos);
        } else {
            if (!allocated()) {
                allocateVector();
            }
            appendPositionToAllocatedVector(pos);
        }
        return *this;
    }

    /**
     * Obtain an object that gives access to the low-level occurrence
     * information stored in this object.
     *
     * @return field position iterator
     **/
    FieldPositionsIterator getIterator() const {
        const uint32_t len(getMaxElementLength());
        return FieldPositionsIterator(len != 0 ? len : FieldPositionsIterator::UNKNOWN_LENGTH, begin(), end());
    }

    uint16_t getNumOccs() const noexcept { return _numOccs; }
    uint16_t getFieldLength() const noexcept { return _fieldLength; }

    void setNumOccs(uint16_t value) { _numOccs = value; }
    void setFieldLength(uint16_t value) { _fieldLength = value; }

    /**
     * This indicates if this instance is actually used for ranking or not.
     * @return true if it is not needed.
     */
    bool isNotNeeded() const noexcept {
        return ((_flags & (UNPACK_NORMAL_FEATURES_FLAG | UNPACK_INTERLEAVED_FEATURES_FLAG)) == 0u);
    }

    bool needs_normal_features() const noexcept { return ((_flags & UNPACK_NORMAL_FEATURES_FLAG) != 0u); }

    bool needs_interleaved_features() const noexcept{ return ((_flags & UNPACK_INTERLEAVED_FEATURES_FLAG) != 0u); }

    /**
     * Tag that this instance is not really used for ranking.
     */
    void tagAsNotNeeded() noexcept {
        _flags &=  ~(UNPACK_NORMAL_FEATURES_FLAG | UNPACK_INTERLEAVED_FEATURES_FLAG);
    }

    /**
     * Tag that this instance is used for ranking (normal features)
     */
    void setNeedNormalFeatures(bool needed) noexcept {
        if (needed) {
            _flags |= UNPACK_NORMAL_FEATURES_FLAG;
        } else {
            _flags &= ~UNPACK_NORMAL_FEATURES_FLAG;
        }
    }

    /**
     * Tag that this instance is used for ranking (interleaved features)
     */
    void setNeedInterleavedFeatures(bool needed) noexcept {
        if (needed) {
            _flags |= UNPACK_INTERLEAVED_FEATURES_FLAG;
        } else {
            _flags &= ~UNPACK_INTERLEAVED_FEATURES_FLAG;
        }
    }

    void filter_elements(uint32_t docid, std::span<const uint32_t> element_ids);

    /**
     * Special docId value indicating that no data has been saved yet.
     * This should match (or be above) endId() in search::queryeval::SearchIterator.
     *
     * @return constant
     **/
    static uint32_t invalidId() noexcept { return 0xdeadbeefU; }
};

}
