// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posting_list_merger.h"
#include <algorithm>

namespace search::attribute {

template <typename DataT>
PostingListMerger<DataT>::PostingListMerger(uint32_t docIdLimit) noexcept
    : _array(),
      _startPos(),
      _bitVector(),
      _docIdLimit(docIdLimit),
      _arrayValid(false)
{
}

template <typename DataT>
PostingListMerger<DataT>::~PostingListMerger() = default;

template <typename DataT>
void
PostingListMerger<DataT>::reserveArray(uint32_t postingsCount, size_t postingsSize)
{
    _array.reserve(postingsSize);
    _startPos.reserve(postingsCount + 1);
    _startPos.push_back(0);
}


template <typename DataT>
void
PostingListMerger<DataT>::allocBitVector()
{
    _bitVector = BitVector::create(_docIdLimit);
}

template <typename DataT>
void
PostingListMerger<DataT>::merge()
{
    if (_bitVector) {
        _bitVector->invalidateCachedCount();
    } else {
        if (_startPos.size() > 2) {
            PostingVector temp(_array.size());
            _array.swap(merge(_array, temp, _startPos));
        }
        StartVector().swap(_startPos);
        _arrayValid = true;
    }
}


template <typename DataT>
typename PostingListMerger<DataT>::PostingVector &
PostingListMerger<DataT>::merge(PostingVector &v, PostingVector &temp, const StartVector &startPos)
{
    StartVector nextStartPos;
    nextStartPos.reserve((startPos.size() + 1) / 2);
    nextStartPos.push_back(0);
    for (size_t i(0), m((startPos.size() - 1) / 2); i < m; i++) {
        size_t aStart = startPos[i * 2 + 0];
        size_t aEnd = startPos[i * 2 + 1];
        size_t bStart = aEnd;
        size_t bEnd = startPos[i * 2 + 2];
        auto it = v.begin();
        std::merge(it + aStart, it + aEnd,
                   it + bStart, it + bEnd,
                   temp.begin() + aStart);
        nextStartPos.push_back(bEnd);
    }
    if ((startPos.size() - 1) % 2) {
        for (size_t i(startPos[startPos.size() - 2]), m(v.size()); i < m; i++) {
            temp[i] = v[i];
        }
        nextStartPos.push_back(temp.size());
    }
    return (nextStartPos.size() > 2) ? merge(temp, v, nextStartPos) : temp;
}


template class PostingListMerger<vespalib::btree::BTreeNoLeafData>;
template class PostingListMerger<int32_t>;

}
