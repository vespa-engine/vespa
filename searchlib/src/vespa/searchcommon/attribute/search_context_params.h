// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <limits>
#include <cstdint>

namespace search::attribute {

class IAttributeVector;

/**
 * Params used to specify diversity and bitvector settings when creating a search context.
 */
class SearchContextParams {
private:
    const IAttributeVector * _diversityAttribute;
    uint32_t                 _diversityCutoffGroups;
    bool                     _useBitVector;
    bool                     _diversityCutoffStrict;

public:
    SearchContextParams()
        : _diversityAttribute(nullptr),
          _diversityCutoffGroups(std::numeric_limits<uint32_t>::max()),
          _useBitVector(false),
          _diversityCutoffStrict(false)
    { }
    bool useBitVector() const { return _useBitVector; }
    const IAttributeVector * diversityAttribute() const { return _diversityAttribute; }
    uint32_t diversityCutoffGroups() const { return _diversityCutoffGroups; }
    bool diversityCutoffStrict() const { return _diversityCutoffStrict; }

    SearchContextParams &useBitVector(bool value) {
        _useBitVector = value;
        return *this;
    }
    SearchContextParams &diversityAttribute(const IAttributeVector *value) {
        _diversityAttribute = value;
        return *this;
    }
    SearchContextParams &diversityCutoffGroups(uint32_t groups) {
        _diversityCutoffGroups = groups;
        return *this;
    }
    SearchContextParams &diversityCutoffStrict(bool strict) {
        _diversityCutoffStrict = strict;
        return *this;
    }
};

}
