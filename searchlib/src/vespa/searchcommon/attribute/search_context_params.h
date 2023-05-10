// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_meta_store_context.h"
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
    const IAttributeVector                          * _diversityAttribute;
    const IDocumentMetaStoreContext::IReadGuard::SP * _metaStoreReadGuard;
    uint32_t                                          _diversityCutoffGroups;
    bool                                              _useBitVector;
    bool                                              _diversityCutoffStrict;

public:
    SearchContextParams()
        : _diversityAttribute(nullptr),
          _metaStoreReadGuard(nullptr),
          _diversityCutoffGroups(std::numeric_limits<uint32_t>::max()),
          _useBitVector(false),
          _diversityCutoffStrict(false)
    { }
    bool useBitVector() const { return _useBitVector; }
    const IAttributeVector * diversityAttribute() const { return _diversityAttribute; }
    uint32_t diversityCutoffGroups() const { return _diversityCutoffGroups; }
    bool diversityCutoffStrict() const { return _diversityCutoffStrict; }
    const IDocumentMetaStoreContext::IReadGuard::SP * metaStoreReadGuard() const { return _metaStoreReadGuard; }

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
    SearchContextParams &metaStoreReadGuard(const IDocumentMetaStoreContext::IReadGuard::SP * readGuard) {
        _metaStoreReadGuard = readGuard;
        return *this;
    }
};

}
