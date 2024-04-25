// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_direct_posting_store.h"
#include <vespa/searchlib/queryeval/begin_and_end_id.h>

namespace search {

class BitVector;

/**
 * Class that wraps a set of underlying low-level posting lists and provides an API to search in them.
 */
template <typename IteratorType, typename RefType>
class PostingIteratorPack {
private:
    std::vector<IteratorType> _children;

public:
    using ref_t = RefType;
    PostingIteratorPack() noexcept : _children() {}
    PostingIteratorPack(PostingIteratorPack &&rhs) noexcept = default;
    PostingIteratorPack &operator=(PostingIteratorPack &&rhs) noexcept = default;

    explicit PostingIteratorPack(std::vector<IteratorType> &&children) noexcept;
    ~PostingIteratorPack();

    constexpr static bool can_handle_iterators(size_t num_iterators) noexcept {
        return num_iterators <= std::numeric_limits<ref_t>::max();
    }

    uint32_t get_docid(ref_t ref) const noexcept {
        return _children[ref].valid() ? _children[ref].getKey() : endDocId;
    }

    uint32_t seek(ref_t ref, uint32_t docid) noexcept {
        _children[ref].linearSeek(docid);
        if (__builtin_expect(_children[ref].valid(), true)) {
            return _children[ref].getKey();
        }
        return endDocId;
    }

    int32_t get_weight(ref_t ref, uint32_t) noexcept {
        return _children[ref].getData();
    }

    std::unique_ptr<BitVector> get_hits(uint32_t begin_id, uint32_t end_id);
    void or_hits_into(BitVector &result, uint32_t begin_id);

    ref_t size() const noexcept { return _children.size(); }
    void initRange(uint32_t begin, uint32_t end) noexcept {
        (void) end;
        for (auto &child: _children) {
            child.lower_bound(begin);
        }
    }
private:
    uint32_t next(ref_t ref) noexcept {
        ++_children[ref];
        return get_docid(ref);
    }
};

using DocidIteratorPack = PostingIteratorPack<DocidIterator, uint16_t>;
using DocidIteratorPackUint32 = PostingIteratorPack<DocidIterator, uint32_t>;
using DocidWithWeightIteratorPack = PostingIteratorPack<DocidWithWeightIterator, uint16_t>;
using DocidWithWeightIteratorPackUint32 = PostingIteratorPack<DocidWithWeightIterator, uint32_t>;

}

