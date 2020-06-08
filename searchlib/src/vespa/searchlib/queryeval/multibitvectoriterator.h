// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "unpackinfo.h"
#include <vespa/searchlib/common/bitword.h>

namespace search::queryeval {

class MultiBitVectorIteratorBase : public MultiSearch, protected BitWord
{
public:
    ~MultiBitVectorIteratorBase();
    void initRange(uint32_t beginId, uint32_t endId) override;
    void addUnpackIndex(size_t index) { _unpackInfo.add(index); }
    /**
     * Will steal and optimize bitvectoriterators if it can
     * Might return itself or a new structure.
     */
    static SearchIterator::UP optimize(SearchIterator::UP parent);
protected:
    MultiBitVectorIteratorBase(Children children);
    class MetaWord {
    public:
        MetaWord(const Word * words, bool inverted) : _words(words), _inverted(inverted) { }
        Word operator [] (uint32_t index) const { return _inverted ? ~_words[index] : _words[index]; }
    private:
        const Word * _words;
        bool         _inverted;
    };

    uint32_t                _numDocs;
    Word                    _lastValue; // Last value computed
    uint32_t                _lastMaxDocIdLimit; // next documentid requiring recomputation.
    std::vector<MetaWord>   _bvs;
private:
    virtual bool acceptExtraFilter() const = 0;
    UP andWith(UP filter, uint32_t estimate) override;
    void doUnpack(uint32_t docid) override;
    UnpackInfo _unpackInfo;
    static SearchIterator::UP optimizeMultiSearch(SearchIterator::UP parent);
};

}
