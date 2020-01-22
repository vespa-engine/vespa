// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_weight_attribute.h"
#include <vespa/searchlib/queryeval/begin_and_end_id.h>

namespace search {

class BitVector;

class AttributeIteratorPack
{
private:
    std::vector<DocumentWeightIterator> _children;

public:
    AttributeIteratorPack() : _children() {}
    AttributeIteratorPack(AttributeIteratorPack &&rhs) noexcept = default;
    AttributeIteratorPack &operator=(AttributeIteratorPack &&rhs) noexcept = default;

    explicit AttributeIteratorPack(std::vector<DocumentWeightIterator> &&children)
        : _children(std::move(children)) {}

    uint32_t get_docid(uint16_t ref) const {
        return _children[ref].valid() ? _children[ref].getKey() : endDocId;
    }

    uint32_t seek(uint16_t ref, uint32_t docid) {
        _children[ref].linearSeek(docid);
        if (__builtin_expect(_children[ref].valid(), true)) {
            return _children[ref].getKey();
        }
        return endDocId;
    }

    int32_t get_weight(uint16_t ref, uint32_t) {
        return _children[ref].getData();
    }

    std::unique_ptr<BitVector> get_hits(uint32_t begin_id, uint32_t end_id);
    void or_hits_into(BitVector &result, uint32_t begin_id);

    size_t size() const { return _children.size(); }
    void initRange(uint32_t begin, uint32_t end) {
        (void) end;
        for (auto &child: _children) {
            child.lower_bound(begin);
        }
    }
private:
    uint32_t next(uint16_t ref) {
        ++_children[ref];
        return get_docid(ref);
    }
};


} // namespace search

