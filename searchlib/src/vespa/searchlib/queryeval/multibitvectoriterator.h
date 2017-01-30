// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "unpackinfo.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

class MultiBitVectorIteratorBase : public MultiSearch, protected BitWord
{
public:
    ~MultiBitVectorIteratorBase();
    virtual bool isStrict() const = 0;
    void addUnpackIndex(size_t index) { _unpackInfo.add(index); }
    /**
     * Will steal and optimize bitvectoriterators if it can
     * Might return itself or a new structure.
     */
    static SearchIterator::UP optimize(SearchIterator::UP parent);
protected:
    MultiBitVectorIteratorBase(const Children & children);

    uint32_t                _numDocs;
    Word                    _lastValue; // Last value computed
    uint32_t                _lastMaxDocIdLimit; // next documentid requiring recomputation.
    std::vector<const Word  *> _bvs;
private:
    virtual bool acceptExtraFilter() const = 0;
    UP andWith(UP filter, uint32_t estimate) override;
    void doUnpack(uint32_t docid) override;
    UnpackInfo _unpackInfo;
    static SearchIterator::UP optimizeMultiSearch(SearchIterator::UP parent);
};

} // namespace queryeval
} // namespace search

