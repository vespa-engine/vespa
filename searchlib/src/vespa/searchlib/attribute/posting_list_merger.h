// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/btree/btree_key_data.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/util/arrayref.h>

namespace search::attribute {

/*
 * Class providing a synthetic posting list by merging multiple posting lists
 * into an array or bitvector.
 */
template <typename DataT>
class PostingListMerger
{
    using Posting = vespalib::btree::BTreeKeyData<uint32_t, DataT>;
    using PostingVector = std::vector<Posting>;
    using StartVector = std::vector<size_t>;

    PostingVector  _array;
    StartVector    _startPos;
    std::shared_ptr<BitVector> _bitVector;
    uint32_t       _docIdLimit;
    bool           _arrayValid;

    PostingVector &merge(PostingVector &v, PostingVector &temp, const StartVector &startPos) __attribute__((noinline));
public:
    PostingListMerger(uint32_t docIdLimit) noexcept;

    ~PostingListMerger();

    void reserveArray(uint32_t postingsCount, size_t postingsSize);
    void allocBitVector();
    void merge();
    bool hasArray() const noexcept { return _arrayValid; }
    bool hasBitVector() const noexcept { return static_cast<bool>(_bitVector); }
    bool emptyArray() const noexcept { return _array.empty(); }
    vespalib::ConstArrayRef<Posting> getArray() const noexcept { return _array; }
    const BitVector *getBitVector() const noexcept { return _bitVector.get(); }
    const std::shared_ptr<BitVector> &getBitVectorSP() const noexcept { return _bitVector; }
    uint32_t getDocIdLimit() const noexcept { return _docIdLimit; }

    template <typename PostingListType>
    void addToArray(const PostingListType & postingList)
    {
        PostingVector &array = _array;
        postingList.foreach([&array](uint32_t key, const DataT &data)
                            { array.emplace_back(key, data); });
        if (_startPos.back() < array.size()) {
            _startPos.push_back(array.size());
        }
    }

    template <typename PostingListType>
    void addToBitVector(const PostingListType & postingList)
    {
        BitVector &bv = *_bitVector;
        uint32_t limit = _docIdLimit;
        postingList.foreach_key([&bv, limit](uint32_t key)
                                { if (__builtin_expect(key < limit, true)) { bv.setBit(key); } });
    }

    bool merge_done() const noexcept { return hasArray() || hasBitVector(); }

    // Until diversity handling has been rewritten
    PostingVector &getWritableArray() noexcept { return _array; }
    StartVector   &getWritableStartPos() noexcept { return _startPos; }
};

}
