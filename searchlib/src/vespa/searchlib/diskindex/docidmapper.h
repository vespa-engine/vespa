// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/array.h>
#include <cassert>
#include <span>
#include <string>

namespace search { class BitVector; }

namespace search::diskindex {

using SelectorArray = vespalib::Array<uint8_t>;

class DocIdMapping
{
public:
    const SelectorArray *_selector; // External ownership
    uint32_t _docIdLimit;
    uint8_t _selectorId;

    DocIdMapping();
    void clear();
    void setup(uint32_t docIdLimit);
    void setup(uint32_t docIdLimit, const SelectorArray *selector, uint8_t selectorId);
    bool readDocIdLimit(const std::string &dir);
    std::span<const uint8_t> get_selector_view() const;
};


class DocIdMapper
{
public:
    std::span<const uint8_t> _selector;
    uint32_t _docIdLimit; // Limit on legal input values
    uint8_t  _selectorId;

    DocIdMapper()
        : _selector(),
          _docIdLimit(0u),
          _selectorId(0u)
    { }

    void setup(const DocIdMapping &mapping) {
        _selector = mapping.get_selector_view();
        _docIdLimit = mapping._docIdLimit;
        _selectorId = mapping._selectorId;
    }

    static uint32_t noDocId() {
        return static_cast<uint32_t>(-1);
    }

    uint32_t mapDocId(uint32_t docId) const {
        assert(docId < _docIdLimit);
        if (_selector.data() != nullptr && (docId >= _selector.size() || _selector[docId] != _selectorId)) {
            docId = noDocId();
        }
        return docId;
    }
};

}
