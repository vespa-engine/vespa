// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_set_term_search.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/objects/visit.h>

#include "iterator_pack.h"

using search::fef::TermFieldMatchData;
using vespalib::ObjectVisitor;

namespace search {
namespace queryeval {

template <typename HEAP, typename IteratorPack>
class WeightedSetTermSearchImpl : public WeightedSetTermSearch
{
private:
    typedef uint32_t ref_t;

    struct CmpDocId {
        const uint32_t *termPos;
        CmpDocId(const uint32_t *tp) : termPos(tp) {}
        bool operator()(const ref_t &a, const ref_t &b) const {
            return (termPos[a] < termPos[b]);
        }
    };

    struct CmpWeight {
        const int32_t *weight;
        CmpWeight(const int32_t *w) : weight(w) {}
        bool operator()(const ref_t &a, const ref_t &b) const {
            return (weight[a] > weight[b]);
        }
    };

    fef::TermFieldMatchData                       &_tmd;
    std::vector<int32_t>                           _weights;
    std::vector<uint32_t>                          _termPos;
    CmpDocId                                       _cmpDocId;
    CmpWeight                                      _cmpWeight;
    std::vector<ref_t>                             _data_space;
    ref_t                                         *_data_begin;
    ref_t                                         *_data_stash;
    ref_t                                         *_data_end;
    IteratorPack                                   _children;

    void seek_child(ref_t child, uint32_t docId) {
        _termPos[child] = _children.seek(child, docId);
    }

public:
    WeightedSetTermSearchImpl(search::fef::TermFieldMatchData &tmd,
                              const std::vector<int32_t> &weights,
                              IteratorPack &&iteratorPack)
        : _tmd(tmd),
          _weights(weights),
          _termPos(weights.size()),
          _cmpDocId(&_termPos[0]),
          _cmpWeight(&_weights[0]),
          _data_space(),
          _data_begin(nullptr),
          _data_stash(nullptr),
          _data_end(nullptr),
          _children(std::move(iteratorPack))
    {
        HEAP::require_left_heap();
        assert(_children.size() > 0);
        assert(_children.size() == _weights.size());
        _data_space.reserve(_children.size());
        for (size_t i = 0; i < _children.size(); ++i) {
            _data_space.push_back(i);
        }
        _data_begin = &_data_space[0];
        _data_end = _data_begin + _data_space.size();
    }

    void doSeek(uint32_t docId) override {
        while (_data_stash < _data_end) {
            seek_child(*_data_stash, docId);
            HEAP::push(_data_begin, ++_data_stash, _cmpDocId);
        }
        while (_termPos[HEAP::front(_data_begin, _data_stash)] < docId) {
            seek_child(HEAP::front(_data_begin, _data_stash), docId);
            HEAP::adjust(_data_begin, _data_stash, _cmpDocId);
        }
        setDocId(_termPos[HEAP::front(_data_begin, _data_stash)]);
    }

    void doUnpack(uint32_t docId) override {
        _tmd.reset(docId);
        while ((_data_begin < _data_stash) &&
               _termPos[HEAP::front(_data_begin, _data_stash)] == docId)
        {
            HEAP::pop(_data_begin, _data_stash--, _cmpDocId);
        }
        std::sort(_data_stash, _data_end, _cmpWeight);
        for (ref_t *ptr = _data_stash; ptr < _data_end; ++ptr) {
            fef::TermFieldMatchDataPosition pos;
            pos.setElementWeight(_weights[*ptr]);
            _tmd.appendPosition(pos);
        }
    }

    void initRange(uint32_t begin, uint32_t end) override {
        WeightedSetTermSearch::initRange(begin, end);
        _children.initRange(begin, end);
        for (size_t i = 0; i < _children.size(); ++i) {
            _termPos[i] = _children.get_docid(i);
        }
        _data_stash = _data_begin;
        while (_data_stash < _data_end) {
            HEAP::push(_data_begin, ++_data_stash, _cmpDocId);
        }
    }
    Trinary is_strict() const override { return Trinary::True; }

    void visitMembers(vespalib::ObjectVisitor &) const override { }

    BitVector::UP get_hits(uint32_t begin_id) override {
        return _children.get_hits(begin_id, getEndId());
    }
};

//-----------------------------------------------------------------------------

SearchIterator *
WeightedSetTermSearch::create(const std::vector<SearchIterator*> &children,
                              TermFieldMatchData &tmd,
                              const std::vector<int32_t> &weights)
{
    typedef WeightedSetTermSearchImpl<vespalib::LeftArrayHeap, SearchIteratorPack> ArrayHeapImpl;
    typedef WeightedSetTermSearchImpl<vespalib::LeftHeap, SearchIteratorPack> HeapImpl;

    if (children.size() < 128) {
        return new ArrayHeapImpl(tmd, weights, SearchIteratorPack(children));
    }
    return new HeapImpl(tmd, weights, SearchIteratorPack(children));
}

//-----------------------------------------------------------------------------

SearchIterator::UP
WeightedSetTermSearch::create(search::fef::TermFieldMatchData &tmd,
                              const std::vector<int32_t> &weights,
                              std::vector<DocumentWeightIterator> &&iterators)
{
    typedef WeightedSetTermSearchImpl<vespalib::LeftArrayHeap, AttributeIteratorPack> ArrayHeapImpl;
    typedef WeightedSetTermSearchImpl<vespalib::LeftHeap, AttributeIteratorPack> HeapImpl;

    if (iterators.size() < 128) {
        return SearchIterator::UP(new ArrayHeapImpl(tmd, weights, AttributeIteratorPack(std::move(iterators))));
    }
    return SearchIterator::UP(new HeapImpl(tmd, weights, AttributeIteratorPack(std::move(iterators))));
}

//-----------------------------------------------------------------------------

}  // namespace search::queryeval
}  // namespace search
