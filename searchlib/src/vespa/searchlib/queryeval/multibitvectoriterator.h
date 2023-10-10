// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "unpackinfo.h"
#include <vespa/searchlib/common/bitword.h>

namespace vespalib::hwaccelrated { class IAccelrated; }

namespace search::queryeval {

class MultiBitVectorBase {
public:
    using Meta = std::pair<const void *, bool>;
    MultiBitVectorBase(size_t reserved);
    using Word = BitWord::Word;
    void reset() {
        _lastMaxDocIdLimit = 0;
        _lastMaxDocIdLimitRequireFetch = 0;
    }
    bool isAtEnd(uint32_t docId) const noexcept { return docId >= _numDocs; }
    void addBitVector(Meta bv, uint32_t docIdLimit);
protected:
    uint32_t            _numDocs;
    uint32_t            _lastMaxDocIdLimit; // next documentid requiring recomputation.
    uint32_t            _lastMaxDocIdLimitRequireFetch;
    Word                _lastValue; // Last value computed
    std::vector<Meta>   _bvs;
};

template <typename Update>
class MultiBitVector : public MultiBitVectorBase {
public:
    explicit MultiBitVector(size_t reserved);
    uint32_t strictSeek(uint32_t docId) noexcept;
    bool seek(uint32_t docId) noexcept;
    bool acceptExtraFilter() const noexcept { return Update::isAnd(); }
private:
    bool updateLastValue(uint32_t docId) noexcept;
    using IAccelrated = vespalib::hwaccelrated::IAccelrated;

    Update              _update;
    const IAccelrated & _accel;
    alignas(64) Word    _lastWords[8];
    static constexpr size_t NumWordsInBatch = sizeof(_lastWords) / sizeof(Word);
};

class MultiBitVectorIteratorBase : public MultiSearch
{
public:
    ~MultiBitVectorIteratorBase() override;
    void initRange(uint32_t beginId, uint32_t endId) override;
    void addUnpackIndex(size_t index) { _unpackInfo.add(index); }
    /**
     * Will steal and optimize bitvectoriterators if it can
     * Might return itself or a new structure.
     */
    static SearchIterator::UP optimize(SearchIterator::UP parent);
protected:
    explicit MultiBitVectorIteratorBase(Children children);
private:
    virtual bool acceptExtraFilter() const noexcept = 0;
    void doUnpack(uint32_t docid) override;
    static SearchIterator::UP optimizeMultiSearch(SearchIterator::UP parent);

    UnpackInfo  _unpackInfo;
};

}
