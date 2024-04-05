// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_set_term_search.h"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/attribute/i_direct_posting_store.h>
#include <vespa/searchlib/attribute/multi_term_hash_filter.hpp>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include "iterator_pack.h"
#include "blueprint.h"

using search::fef::TermFieldMatchData;
using vespalib::ObjectVisitor;

namespace search::queryeval {

enum class UnpackType {
    DocidAndWeights,
    Docid,
    None
};

template <UnpackType unpack_type, typename HEAP, typename IteratorPack>
class WeightedSetTermSearchImpl : public WeightedSetTermSearch
{
private:
    using ref_t = IteratorPack::ref_t;

    struct CmpDocId {
        const uint32_t *termPos;
        explicit CmpDocId(const uint32_t *tp) : termPos(tp) {}
        bool operator()(const ref_t &a, const ref_t &b) const {
            return (termPos[a] < termPos[b]);
        }
    };

    struct CmpWeight {
        const int32_t *weight;
        explicit CmpWeight(const int32_t *w) : weight(w) {}
        bool operator()(const ref_t &a, const ref_t &b) const {
            return (weight[a] > weight[b]);
        }
    };

    fef::TermFieldMatchData                       &_tmd;
    std::vector<int32_t>                           _weights_data;
    const std::vector<int32_t>                    &_weights;
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
    void get_matching_elements_child(ref_t child, uint32_t docId, const std::vector<Blueprint::UP> &child_blueprints, std::vector<uint32_t> &dst) {
        auto *sc = child_blueprints[child]->get_attribute_search_context();
        if (sc != nullptr) {
            int32_t weight(0);
            for (int32_t id = sc->find(docId, 0, weight); id >= 0; id = sc->find(docId, id + 1, weight)) {
                dst.push_back(id);
            }
        }
    }

public:
    WeightedSetTermSearchImpl(fef::TermFieldMatchData &tmd,
                              std::variant<std::reference_wrapper<const std::vector<int32_t>>, std::vector<int32_t>> weights,
                              IteratorPack &&iteratorPack)
        : _tmd(tmd),
          _weights_data((weights.index() == 1) ? std::move(std::get<1>(weights)) : std::vector<int32_t>()),
          _weights((weights.index() == 1) ? _weights_data : std::get<0>(weights).get()),
          _termPos(_weights.size()),
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
        if constexpr (unpack_type == UnpackType::DocidAndWeights) {
            _tmd.reservePositions(_children.size());
        }
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

    void pop_matching_children(uint32_t docId) {
        while ((_data_begin < _data_stash) &&
               _termPos[HEAP::front(_data_begin, _data_stash)] == docId)
        {
            HEAP::pop(_data_begin, _data_stash--, _cmpDocId);
        }
    }

    void doUnpack(uint32_t docId) override {
        if constexpr (unpack_type == UnpackType::DocidAndWeights) {
            _tmd.reset(docId);
            pop_matching_children(docId);
            std::sort(_data_stash, _data_end, _cmpWeight);
            for (ref_t *ptr = _data_stash; ptr < _data_end; ++ptr) {
                fef::TermFieldMatchDataPosition pos;
                pos.setElementWeight(_weights[*ptr]);
                _tmd.appendPosition(pos);
            }
        } else if constexpr (unpack_type == UnpackType::Docid) {
            _tmd.resetOnlyDocId(docId);
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

    void or_hits_into(BitVector &result, uint32_t begin_id) override {
        _children.or_hits_into(result, begin_id);
    }
    void and_hits_into(BitVector &result, uint32_t begin_id) override {
        result.andWith(*get_hits(begin_id));
    }
    void find_matching_elements(uint32_t docId, const std::vector<Blueprint::UP> &child_blueprints, std::vector<uint32_t> &dst) override {
        pop_matching_children(docId);
        for (ref_t *ptr = _data_stash; ptr < _data_end; ++ptr) {
            get_matching_elements_child(*ptr, docId, child_blueprints, dst);
        }
    }
};

template <typename HeapType, typename IteratorPackType>
SearchIterator::UP
create_helper(fef::TermFieldMatchData& tmd,
              bool is_filter_search,
              std::variant<std::reference_wrapper<const std::vector<int32_t>>, std::vector<int32_t>> weights,
              IteratorPackType&& pack)
{
    bool match_data_needed = !tmd.isNotNeeded();
    if (is_filter_search && match_data_needed) {
        return std::make_unique<WeightedSetTermSearchImpl<UnpackType::Docid, HeapType, IteratorPackType>>
            (tmd, std::move(weights), std::move(pack));
    } else if (!is_filter_search && match_data_needed) {
        return std::make_unique<WeightedSetTermSearchImpl<UnpackType::DocidAndWeights, HeapType, IteratorPackType>>
                (tmd, std::move(weights), std::move(pack));
    } else {
        return std::make_unique<WeightedSetTermSearchImpl<UnpackType::None, HeapType, IteratorPackType>>
                (tmd, std::move(weights), std::move(pack));
    }
}

SearchIterator::UP
WeightedSetTermSearch::create(const std::vector<SearchIterator *> &children,
                              TermFieldMatchData &tmd,
                              bool is_filter_search,
                              const std::vector<int32_t> &weights,
                              fef::MatchData::UP match_data)
{
    if (children.size() < 128) {
        if (SearchIteratorPack::can_handle_iterators(children.size())) {
            return create_helper<vespalib::LeftArrayHeap, SearchIteratorPack>(tmd, is_filter_search, std::cref(weights),
                                                                              SearchIteratorPack(children, std::move(match_data)));
        } else {
            return create_helper<vespalib::LeftArrayHeap, SearchIteratorPackUint32>(tmd, is_filter_search, std::cref(weights),
                                                                                    SearchIteratorPackUint32(children, std::move(match_data)));
        }
    }
    if (SearchIteratorPack::can_handle_iterators(children.size())) {
        return create_helper<vespalib::LeftHeap, SearchIteratorPack>(tmd, is_filter_search, std::cref(weights),
                                                                     SearchIteratorPack(children, std::move(match_data)));
    } else {
        return create_helper<vespalib::LeftHeap, SearchIteratorPackUint32>(tmd, is_filter_search, std::cref(weights),
                                                                           SearchIteratorPackUint32(children, std::move(match_data)));
    }
}

namespace {

template <typename IteratorType, typename IteratorPackType>
SearchIterator::UP
create_helper_resolve_pack(fef::TermFieldMatchData& tmd,
                           bool is_filter_search,
                           std::variant<std::reference_wrapper<const std::vector<int32_t>>, std::vector<int32_t>> weights,
                           std::vector<IteratorType>&& iterators)
{
    if (iterators.size() < 128) {
        return create_helper<vespalib::LeftArrayHeap, IteratorPackType>(tmd, is_filter_search, std::move(weights),
                                                                        IteratorPackType(std::move(iterators)));
    }
    return create_helper<vespalib::LeftHeap, IteratorPackType>(tmd, is_filter_search, std::move(weights),
                                                               IteratorPackType(std::move(iterators)));
}

}

SearchIterator::UP
WeightedSetTermSearch::create(fef::TermFieldMatchData& tmd,
                              bool is_filter_search,
                              std::variant<std::reference_wrapper<const std::vector<int32_t>>, std::vector<int32_t>> weights,
                              std::vector<DocidIterator>&& iterators)
{
    if (DocidIteratorPack::can_handle_iterators(iterators.size())) {
        return create_helper_resolve_pack<DocidIterator, DocidIteratorPack>(tmd, is_filter_search, std::move(weights), std::move(iterators));
    } else {
        return create_helper_resolve_pack<DocidIterator, DocidIteratorPackUint32>(tmd, is_filter_search, std::move(weights), std::move(iterators));
    }
}

SearchIterator::UP
WeightedSetTermSearch::create(fef::TermFieldMatchData &tmd,
                              bool is_filter_search,
                              std::variant<std::reference_wrapper<const std::vector<int32_t>>, std::vector<int32_t>> weights,
                              std::vector<DocidWithWeightIterator> &&iterators)
{
    if (DocidWithWeightIteratorPack::can_handle_iterators(iterators.size())) {
        return create_helper_resolve_pack<DocidWithWeightIterator, DocidWithWeightIteratorPack>(tmd, is_filter_search, std::move(weights), std::move(iterators));
    } else {
        return create_helper_resolve_pack<DocidWithWeightIterator, DocidWithWeightIteratorPackUint32>(tmd, is_filter_search, std::move(weights), std::move(iterators));
    }
}

namespace {

class HashFilterWrapper {
protected:
    const attribute::IAttributeVector& _attr;
public:
    HashFilterWrapper(const attribute::IAttributeVector& attr) : _attr(attr) {}
};

template <bool unpack_weights_t>
class StringHashFilterWrapper : public HashFilterWrapper {
public:
    using TokenT = attribute::IAttributeVector::EnumHandle;
    static constexpr bool unpack_weights = unpack_weights_t;
    StringHashFilterWrapper(const attribute::IAttributeVector& attr)
        : HashFilterWrapper(attr)
    {}
    auto mapToken(const IDirectPostingStore::LookupResult& term, const IDirectPostingStore& store, vespalib::datastore::EntryRef dict_snapshot) const {
        std::vector<TokenT> result;
        store.collect_folded(term.enum_idx, dict_snapshot, [&](vespalib::datastore::EntryRef ref) { result.emplace_back(ref.ref()); });
        return result;
    }
    TokenT getToken(uint32_t docid) const {
        return _attr.getEnum(docid);
    }
};

template <bool unpack_weights_t>
class IntegerHashFilterWrapper : public HashFilterWrapper {
public:
    using TokenT = attribute::IAttributeVector::largeint_t;
    static constexpr bool unpack_weights = unpack_weights_t;
    IntegerHashFilterWrapper(const attribute::IAttributeVector& attr)
        : HashFilterWrapper(attr)
    {}
    auto mapToken(const IDirectPostingStore::LookupResult& term,
                  const IDirectPostingStore& store,
                  vespalib::datastore::EntryRef) const {
        std::vector<TokenT> result;
        result.emplace_back(store.get_integer_value(term.enum_idx));
        return result;
    }
    TokenT getToken(uint32_t docid) const {
        return _attr.getInt(docid);
    }
};

template <typename WrapperType>
SearchIterator::UP
create_hash_filter_helper(fef::TermFieldMatchData& tfmd,
                          const std::vector<int32_t>& weights,
                          const std::vector<IDirectPostingStore::LookupResult>& terms,
                          const attribute::IAttributeVector& attr,
                          const IDirectPostingStore& posting_store,
                          vespalib::datastore::EntryRef dict_snapshot)
{
    using FilterType = attribute::MultiTermHashFilter<WrapperType>;
    typename FilterType::TokenMap tokens;
    WrapperType wrapper(attr);
    for (size_t i = 0; i < terms.size(); ++i) {
        for (auto token : wrapper.mapToken(terms[i], posting_store, dict_snapshot)) {
            tokens[token] = weights[i];
        }
    }
    return std::make_unique<FilterType>(tfmd, wrapper, std::move(tokens));
}

}

SearchIterator::UP
WeightedSetTermSearch::create_hash_filter(search::fef::TermFieldMatchData& tmd,
                                          bool is_filter_search,
                                          const std::vector<int32_t>& weights,
                                          const std::vector<IDirectPostingStore::LookupResult>& terms,
                                          const attribute::IAttributeVector& attr,
                                          const IDirectPostingStore& posting_store,
                                          vespalib::datastore::EntryRef dict_snapshot)
{
    if (attr.isStringType()) {
        if (is_filter_search) {
            return create_hash_filter_helper<StringHashFilterWrapper<false>>(tmd, weights, terms, attr, posting_store, dict_snapshot);
        } else {
            return create_hash_filter_helper<StringHashFilterWrapper<true>>(tmd, weights, terms, attr, posting_store, dict_snapshot);
        }
    } else {
        assert(attr.isIntegerType());
        if (is_filter_search) {
            return create_hash_filter_helper<IntegerHashFilterWrapper<false>>(tmd, weights, terms, attr, posting_store, dict_snapshot);
        } else {
            return create_hash_filter_helper<IntegerHashFilterWrapper<true>>(tmd, weights, terms, attr, posting_store, dict_snapshot);
        }
    }
}

}
