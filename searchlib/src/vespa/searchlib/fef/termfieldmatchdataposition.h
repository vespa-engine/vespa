// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/fslimits.h>
#include <cstdint>

namespace search::fef {

class TermFieldMatchDataPositionKey
{
private:
    uint32_t _elementId;
    uint32_t _position;

public:
    TermFieldMatchDataPositionKey()
        : _elementId(0u),
          _position(0u)
    { }

    TermFieldMatchDataPositionKey(uint32_t elementId,
                                  uint32_t position)
        : _elementId(elementId),
          _position(position)
    { }

    uint32_t getElementId() const { return _elementId; }
    uint32_t getPosition() const { return _position; }

    void setElementId(uint32_t elementId) { _elementId = elementId; }
    void setPosition(uint32_t position) { _position = position; }

    bool operator<(const TermFieldMatchDataPositionKey &rhs) const {
        if (_elementId != rhs._elementId) {
            return _elementId < rhs._elementId;
        }
        return _position < rhs._position;
    }

    bool operator==(const TermFieldMatchDataPositionKey &rhs) const {
        return ((_elementId == rhs._elementId) &&
                (_position == rhs._position));
    }
};

class TermFieldMatchDataPosition : public TermFieldMatchDataPositionKey
{
private:
    int32_t  _elementWeight  = 1;
    uint32_t _elementLen     = SEARCHLIB_FEF_UNKNOWN_FIELD_LENGTH;
    float    _matchExactness = 1.0; // or possibly _matchWeight
    uint32_t _matchLength    = 1;

public:
    TermFieldMatchDataPosition() : TermFieldMatchDataPositionKey() {}

    const TermFieldMatchDataPositionKey &key() const {
        return *this;
    }

    /**
     * A comparator for sorting in natural (ascending) order but if
     * positions are equal, sort best exactness first.
     */
    static bool compareWithExactness(const TermFieldMatchDataPosition &a,
                                     const TermFieldMatchDataPosition &b)
    {
        if (a < b) return true;
        if (b < a) return false;
        return a._matchExactness > b._matchExactness;
    }

    TermFieldMatchDataPosition(uint32_t elementId,
                               uint32_t position,
                               int32_t elementWeight,
                               uint32_t elementLen)
        : TermFieldMatchDataPositionKey(elementId, position),
          _elementWeight(elementWeight),
          _elementLen(elementLen),
          _matchExactness(1.0)
    { }

    int32_t getElementWeight() const { return _elementWeight; }
    uint32_t getElementLen() const { return _elementLen; }
    float getMatchExactness() const { return _matchExactness; }
    uint32_t getMatchLength() const { return _matchExactness; }

    void setElementWeight(int32_t elementWeight) {
        _elementWeight = elementWeight;
    }
    void setElementLen(uint32_t elementLen) {
        _elementLen = elementLen;
    }
    TermFieldMatchDataPosition& setMatchExactness(double exactness) {
        _matchExactness = exactness;
        return *this;
    }
    TermFieldMatchDataPosition& setMatchLength(uint32_t length) {
        _matchLength = length;
        return *this;
    }
};

}
