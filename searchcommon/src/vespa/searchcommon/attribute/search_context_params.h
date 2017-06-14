// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace search::attribute {

class IAttributeVector;

/**
 * Params used to specify diversity and bitvector settings when creating a search context.
 */
class SearchContextParams {
private:
    const IAttributeVector * _diversityAttribute;
    size_t                   _diversityCutoffGroups;
    bool                     _useBitVector;
    bool                     _diversityCutoffStrict;

public:
    SearchContextParams();
    bool useBitVector() const { return _useBitVector; }
    const IAttributeVector * diversityAttribute() const { return _diversityAttribute; }
    size_t diversityCutoffGroups() const { return _diversityCutoffGroups; }
    bool diversityCutoffStrict() const { return _diversityCutoffStrict; }

    SearchContextParams &useBitVector(bool value) {
        _useBitVector = value;
        return *this;
    }
    SearchContextParams &diversityAttribute(const IAttributeVector *value) {
        _diversityAttribute = value;
        return *this;
    }
    SearchContextParams &diversityCutoffGroups(size_t groups) {
        _diversityCutoffGroups = groups;
        return *this;
    }
    SearchContextParams &diversityCutoffStrict(bool strict) {
        _diversityCutoffStrict = strict;
        return *this;
    }
};

}
