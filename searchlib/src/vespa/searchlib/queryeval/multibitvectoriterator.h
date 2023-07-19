// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "unpackinfo.h"
#include <vespa/searchlib/common/bitword.h>

namespace search::queryeval {

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
    using Word = BitWord::Word;
    MultiBitVectorIteratorBase(Children children);
    using MetaWord = std::pair<const void *, bool>;

    uint32_t                _numDocs;
    uint32_t                _lastMaxDocIdLimit; // next documentid requiring recomputation.
    uint32_t                _lastMaxDocIdLimitRequireFetch;
    Word                    _lastValue; // Last value computed
    std::vector<MetaWord>   _bvs;
private:
    virtual bool acceptExtraFilter() const noexcept = 0;
    UP andWith(UP filter, uint32_t estimate) override;
    void doUnpack(uint32_t docid) override;
    static SearchIterator::UP optimizeMultiSearch(SearchIterator::UP parent);

    UnpackInfo  _unpackInfo;
};

}
