// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posting_iterator_pack.h"
#include <vespa/searchlib/common/bitvector.h>
#include <limits>

namespace search {

template <typename IteratorType>
PostingIteratorPack<IteratorType>::~PostingIteratorPack() = default;

template <typename IteratorType>
PostingIteratorPack<IteratorType>::PostingIteratorPack(std::vector<IteratorType> &&children)
    : _children(std::move(children))
{
    assert(_children.size() <= std::numeric_limits<ref_t>::max());
}

template <typename IteratorType>
std::unique_ptr<BitVector>
PostingIteratorPack<IteratorType>::get_hits(uint32_t begin_id, uint32_t end_id) {
    BitVector::UP result(BitVector::create(begin_id, end_id));
    or_hits_into(*result, begin_id);
    return result;
}

template <typename IteratorType>
void
PostingIteratorPack<IteratorType>::or_hits_into(BitVector &result, uint32_t begin_id) {
    for (size_t i = 0; i < size(); ++i) {
        uint32_t docId = get_docid(i);
        if (begin_id > docId) {
            docId = seek(i, begin_id);
        }
        for (uint32_t limit = result.size(); docId < limit; docId = next(i)) {
            result.setBit(docId);
        }
    }
    result.invalidateCachedCount();
}

template <>
int32_t
PostingIteratorPack<DocidIterator>::get_weight(ref_t, uint32_t)
{
    return 1;
}

template class PostingIteratorPack<DocidIterator>;
template class PostingIteratorPack<DocidWithWeightIterator>;

}
