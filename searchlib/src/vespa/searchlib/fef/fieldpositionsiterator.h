// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "termfieldmatchdataposition.h"

namespace search::fef {

/**
 * Iterator used to iterate over all positions of a term inside a
 * specific field.
 **/
class FieldPositionsIterator
{
public:
    /**
     * The iterator type of the underlying data, which have all
     * positions for a term across all fields searched.
     **/
    using PositionsIterator = const TermFieldMatchDataPosition *;

private:
    uint32_t          _length;
    PositionsIterator _begin;
    PositionsIterator _pos;
    PositionsIterator _end;

public:
    /**
     * The length reported for fields for which we do not know the
     * real length.
     **/
    static const uint32_t UNKNOWN_LENGTH;

    /**
     * Create a new iterator for a field we know nothing about. This
     * will give the field no position data and a length of 0.
     **/
    FieldPositionsIterator()
        : _length(UNKNOWN_LENGTH), _begin(0), _pos(0), _end(0) {}

    /**
     * Create a new iterator for a field with the given offset and
     * length, using a slice of the underlying position data.
     *
     * @param length the length of the field in words
     * @param begin start of position data slice
     * @param end end of position data slice
     **/
    FieldPositionsIterator(uint32_t length,
                           PositionsIterator begin,
                           PositionsIterator end)
        : _length(length), _begin(begin), _pos(begin), _end(end) {}

    /**
     * Relocate the references held by this object into the actual
     * occurrence data. This method assumes iterators are random
     * access and cheap to copy. This method must be invoked if the
     * underlying occurrence data is moved in memory.
     *
     * @param oldRef old reference iterator
     * @param newRef new reference iterator
     **/
    void relocate(PositionsIterator oldRef, PositionsIterator newRef) {
        if (_begin != PositionsIterator(0)) {
            _begin = newRef + (_begin - oldRef);
            _pos   = newRef + (_pos   - oldRef);
            _end   = newRef + (_end   - oldRef);
        }
    }

    /**
     * Check if there is valid data available at the current position
     * of this iterator.
     *
     * @return false if no more data is available
     **/
    bool valid() const { return _pos != _end; }

    /**
     * Step this iterator to the next position. This method may only
     * be invoked if the @ref valid method returns true.
     **/
    void next() { ++_pos; }

    /**
     * Try to step this iterator backwards. This method will return
     * false if the iterator is already located at the beginning.
     *
     * @return false if we are unable to step backwards
     **/
    bool prev() {
        if (_pos == _begin) {
            return false;
        }
        --_pos;
        return true;
    }

    /**
     * Obtain the word position within the field for the entry
     * indicated by the current position of this iterator. This method
     * may only be invoked if the @ref valid method returns true.
     *
     * @return word position within the field
     **/
    uint32_t getPosition() const { return _pos->getPosition(); }

    /**
     * Obtain the element id within the field for the entry
     * indicated by the current position of this iterator. This method
     * may only be invoked if the @ref valid method returns true.
     *
     * @return element id within the field
     **/
    uint32_t getElementId() const { return _pos->getElementId(); }

    /**
     * Obtain the element length within the field for the entry
     * indicated by the current position of this iterator. This method
     * may only be invoked if the @ref valid method returns true.
     *
     * @return element id within the field
     **/
    uint32_t getElementLen() const { return _pos->getElementLen(); }

    /**
     * Obtain the element weight within the field for the entry
     * indicated by the current position of this iterator. This method
     * may only be invoked if the @ref valid method returns true.
     *
     * @return element id within the field
     **/
    int32_t getElementWeight() const { return _pos->getElementWeight(); }

    /**
     * Obtain the match exactness indicated by the current position of
     * this iterator. This method may only be invoked if the @ref valid
     * method returns true.
     *
     * @return exactness measure
     **/
    double getMatchExactness() const { return _pos->getMatchExactness(); }

    /**
     * Obtain the total number of words in the field.
     *
     * @return field length in words.
     **/
    uint32_t getFieldLength() const { return _length; }

    /**
     * Obtain the number of positions in this iterator.
     *
     * @return number of positions
     **/
    uint32_t size() const { return (_end - _begin); }
};

}
