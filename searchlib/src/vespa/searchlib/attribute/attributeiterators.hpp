// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeiterators.h"
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/searchlib/fef/termfieldmatchdataposition.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/objects/visit.h>

namespace search {

namespace {

template <typename SC>
bool matches(const SC & sc, uint32_t doc) {
    return sc.find(doc, 0) >= 0;
}

}

template <typename SC>
void
AttributeIteratorBase::and_hits_into(const SC & sc, BitVector & result, uint32_t begin_id) const {
    result.foreach_truebit([&](uint32_t key) { if ( ! matches(sc, key)) { result.clearBit(key); }}, begin_id);
    result.invalidateCachedCount();
}

template <typename SC>
void
AttributeIteratorBase::or_hits_into(const SC & sc, BitVector & result, uint32_t begin_id) const {
    result.foreach_falsebit([&](uint32_t key) { if ( matches(sc, key)) { result.setBit(key); }}, begin_id);
    result.invalidateCachedCount();
}


template <typename SC>
std::unique_ptr<BitVector>
AttributeIteratorBase::get_hits(const SC & sc, uint32_t begin_id) const {
    BitVector::UP result = BitVector::create(begin_id, getEndId());
    for (uint32_t docId(std::max(begin_id, getDocId())); docId < getEndId(); docId++) {
        if (matches(sc, docId)) {
            result->setBit(docId);
        }
    }
    result->invalidateCachedCount();
    return result;
}


template <typename PL>
template <typename... Args>
AttributePostingListIteratorT<PL>::
AttributePostingListIteratorT(const attribute::ISearchContext &baseSearchCtx,
                              fef::TermFieldMatchData *matchData,
                              Args &&... args)
    : AttributePostingListIterator(baseSearchCtx, matchData),
      _iterator(std::forward<Args>(args)...),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    setupPostingInfo();
}

template <typename PL>
AttributePostingListIteratorT<PL>::~AttributePostingListIteratorT() = default;

template <typename PL>
void AttributePostingListIteratorT<PL>::initRange(uint32_t begin, uint32_t end) {
    AttributePostingListIterator::initRange(begin, end);
    _iterator.lower_bound(begin);
    if (!_iterator.valid() || isAtEnd(_iterator.getKey())) {
        setAtEnd();
    } else {
        setDocId(_iterator.getKey());
    }
}

template <typename PL>
template<typename... Args>
FilterAttributePostingListIteratorT<PL>::
FilterAttributePostingListIteratorT(const attribute::ISearchContext &baseSearchCtx, fef::TermFieldMatchData *matchData, Args &&... args)
    : FilterAttributePostingListIterator(baseSearchCtx, matchData),
      _iterator(std::forward<Args>(args)...),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    setupPostingInfo();
    _matchPosition->setElementWeight(1);
}

template <typename PL>
FilterAttributePostingListIteratorT<PL>::~FilterAttributePostingListIteratorT() = default;

template <typename PL>
void  FilterAttributePostingListIteratorT<PL>::initRange(uint32_t begin, uint32_t end) {
    FilterAttributePostingListIterator::initRange(begin, end);
    _iterator.lower_bound(begin);
    if (!_iterator.valid() || isAtEnd(_iterator.getKey())) {
        setAtEnd();
    } else {
        setDocId(_iterator.getKey());
    }
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::doSeek(uint32_t docId)
{
    _iterator.linearSeek(docId);
    if (_iterator.valid()) {
        setDocId(_iterator.getKey());
    } else {
        setAtEnd();
    }
}

namespace {

template <typename> struct is_tree_iterator;

template <typename P>
struct is_tree_iterator<ArrayIterator<P>> {
    static constexpr bool value = false;
};

template <typename P>
struct is_tree_iterator<DocIdMinMaxIterator<P>> {
    static constexpr bool value = false;
};

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
struct is_tree_iterator<vespalib::btree::BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>> {
    static constexpr bool value = true;
};

template <typename PL>
inline constexpr bool is_tree_iterator_v = is_tree_iterator<PL>::value;

template <typename PL>
void get_hits_helper(BitVector& result, PL& iterator, uint32_t end_id)
{
    auto end_itr = iterator;
    if (end_itr.valid() && end_itr.getKey() < end_id) {
        end_itr.seek(end_id);
    }
    iterator.foreach_key_range(end_itr, [&](uint32_t key) { result.setBit(key); });
    iterator = end_itr;
}

template <typename PL>
void or_hits_helper(BitVector& result, PL& iterator, uint32_t end_id)
{
    auto end_itr = iterator;
    if (end_itr.valid() && end_itr.getKey() < end_id) {
        end_itr.seek(end_id);
    }
    iterator.foreach_key_range(end_itr, [&](uint32_t key)
                               {
                                   if (!result.testBit(key)) {
                                       result.setBit(key);
                                   }
                               });
    iterator = end_itr;
}
 
}

template <typename PL>
std::unique_ptr<BitVector>
AttributePostingListIteratorT<PL>::get_hits(uint32_t begin_id) {
    BitVector::UP result(BitVector::create(begin_id, getEndId()));
    if constexpr (is_tree_iterator_v<PL>) {
        get_hits_helper(*result, _iterator, getEndId());
    } else {
        for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
            result->setBit(_iterator.getKey());
        }
    }
    result->invalidateCachedCount();
    return result;
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::or_hits_into(BitVector & result, uint32_t begin_id) {
    (void) begin_id;
    if constexpr (is_tree_iterator_v<PL>) {
        or_hits_helper(result, _iterator, getEndId());
    } else {
        for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
            if ( ! result.testBit(_iterator.getKey()) ) {
                result.setBit(_iterator.getKey());
            }
        }
    }
    result.invalidateCachedCount();
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::and_hits_into(BitVector &result, uint32_t begin_id) {
    result.andWith(*get_hits(begin_id));
}

template <typename PL>
std::unique_ptr<BitVector>
FilterAttributePostingListIteratorT<PL>::get_hits(uint32_t begin_id) {
    BitVector::UP result(BitVector::create(begin_id, getEndId()));
    if constexpr (is_tree_iterator_v<PL>) {
        get_hits_helper(*result, _iterator, getEndId());
    } else {
        for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
            result->setBit(_iterator.getKey());
        }
    }
    result->invalidateCachedCount();
    return result;
}

template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::or_hits_into(BitVector & result, uint32_t begin_id) {
    (void) begin_id;
    if constexpr (is_tree_iterator_v<PL>) {
        or_hits_helper(result, _iterator, getEndId());
    } else {
        for (; _iterator.valid() && _iterator.getKey() < getEndId(); ++_iterator) {
            if ( ! result.testBit(_iterator.getKey()) ) {
                result.setBit(_iterator.getKey());
            }
        }
    }
    result.invalidateCachedCount();
}


template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::and_hits_into(BitVector &result, uint32_t begin_id) {
    result.andWith(*get_hits(begin_id));
}

template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::doSeek(uint32_t docId)
{
    _iterator.linearSeek(docId);
    if (_iterator.valid()) {
        setDocId(_iterator.getKey());
    } else {
        setAtEnd();
    }
}

template <typename PL>
void
AttributePostingListIteratorT<PL>::doUnpack(uint32_t docId)
{
    if (_matchData->has_ranking_data(docId)) {
        return;
    }
    _matchData->resetOnlyDocId(docId);

    int32_t weight(0);
    for(; _iterator.valid() && (_iterator.getKey() == docId); weight += getWeight(), ++_iterator);
    _matchPosition->setElementWeight(weight);
}

template <typename PL>
void
FilterAttributePostingListIteratorT<PL>::doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
}

template <typename SC>
void
AttributeIteratorT<SC>::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AttributeIterator::visitMembers(visitor);
    visit(visitor, "searchcontext.attribute", _concreteSearchCtx.attributeName());
    visit(visitor, "searchcontext.queryterm", _concreteSearchCtx.queryTerm());
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    FilterAttributeIterator::visitMembers(visitor);
    visit(visitor, "searchcontext.attribute", _concreteSearchCtx.attributeName());
    visit(visitor, "searchcontext.queryterm", _concreteSearchCtx.queryTerm());
}

template <typename SC>
AttributeIteratorT<SC>::AttributeIteratorT(const SC &concreteSearchCtx, fef::TermFieldMatchData *matchData)
    : AttributeIterator(concreteSearchCtx, matchData),
      _concreteSearchCtx(concreteSearchCtx)
{ }

template <typename SC>
AttributeIteratorT<SC>::~AttributeIteratorT() = default;

template <typename SC>
FilterAttributeIteratorT<SC>::FilterAttributeIteratorT(const SC &concreteSearchCtx, fef::TermFieldMatchData *matchData)
    : FilterAttributeIterator(concreteSearchCtx, matchData),
      _concreteSearchCtx(concreteSearchCtx)
{ }

template <typename SC>
FilterAttributeIteratorT<SC>::~FilterAttributeIteratorT() = default;

template <typename SC>
FlagAttributeIteratorStrict<SC>::~FlagAttributeIteratorStrict() = default;

template <typename SC>
void
FlagAttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    const SC & sc(_concreteSearchCtx);
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = sc.get_bit_vector(i);
        if ((bv != nullptr) && !isAtEnd(docId) && bv->testBit(docId)) {
            setDocId(docId);
            return;
        }
    }

    uint32_t minNextBit(search::endDocId);
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = sc.get_bit_vector(i);
        if (bv != nullptr && !isAtEnd(docId)) {
            uint32_t nextBit = bv->getNextTrueBit(docId);
            minNextBit = std::min(nextBit, minNextBit);
        }
    }
    if (!isAtEnd(minNextBit)) {
        setDocId(minNextBit);
    } else {
        setAtEnd();
    }
}

template <typename SC>
FlagAttributeIteratorT<SC>::~FlagAttributeIteratorT() = default;

template <typename SC>
void
FlagAttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    const SC & sc(_concreteSearchCtx);
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = sc.get_bit_vector(i);
        if ((bv != nullptr) && !isAtEnd(docId) && bv->testBit(docId)) {
            setDocId(docId);
            return;
        }
    }
}

template <typename SC>
void
FlagAttributeIteratorT<SC>::or_hits_into(BitVector &result, uint32_t begin_id) {
    (void) begin_id;
    const SC & sc(_concreteSearchCtx);
    for (int i = sc._low; (i <= sc._high); ++i) {
        const BitVector * bv = sc.get_bit_vector(i);
        if (bv != nullptr) {
            result.orWith(*bv);
        }
    }
}

template <typename SC>
void
FlagAttributeIteratorT<SC>::and_hits_into(BitVector &result, uint32_t begin_id) {
    const SC & sc(_concreteSearchCtx);
    if (sc._low == sc._high) {
        const BitVector * bv = sc.get_bit_vector(sc._low);
        if (bv != nullptr) {
            result.andWith(*bv);
        } else {
            // I would expect us never to end up in this case as we are probably
            // replaced by an EmptySearch, but I keep the code here to be functionally complete.
            result.clear();
        }
    } else {
        SearchIterator::and_hits_into(result, begin_id);
    }
}

template <typename SC>
std::unique_ptr<BitVector>
FlagAttributeIteratorT<SC>::get_hits(uint32_t begin_id) {
    const SC & sc(_concreteSearchCtx);
    int i = sc._low;
    BitVector::UP result;
    for (;!result && i < sc._high; ++i) {
        const BitVector * bv = sc.get_bit_vector(i);
        if (bv != nullptr) {
            result = BitVector::create(*bv, begin_id, getEndId());
        }
    }

    for (; i <= sc._high; ++i) {
        const BitVector * bv = sc.get_bit_vector(i);
        if (bv != nullptr) {
            result->orWith(*bv);
        }
    }
    if (!result) {
        result = BitVector::create(begin_id, getEndId());
    } else if (begin_id < getDocId()) {
        result->clearInterval(begin_id, std::min(getDocId(), getEndId()));
    }

    return result;
}

template <typename SC>
void
AttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (isAtEnd(docId)) {
        setAtEnd();
    } else if (matches(docId, _weight)) {
        setDocId(docId);
    }
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (isAtEnd(docId)) {
        setAtEnd();
    } else if (matches(docId)) {
        setDocId(docId);
    }
}

template <typename SC>
AttributeIteratorStrict<SC>::~AttributeIteratorStrict() = default;

template <typename SC>
void
AttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    for (uint32_t nextId = docId; !isAtEnd(nextId); ++nextId) {
        if (this->matches(nextId, _weight)) {
            setDocId(nextId);
            return;
        }
    }
    setAtEnd();
}

template <typename SC>
FilterAttributeIteratorStrict<SC>::~FilterAttributeIteratorStrict() = default;

template <typename SC>
void
FilterAttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    for (uint32_t nextId = docId; !isAtEnd(nextId); ++nextId) {
        if (this->matches(nextId)) {
            setDocId(nextId);
            return;
        }
    }
    setAtEnd();
}

template <typename SC>
void
AttributeIteratorT<SC>::or_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::or_hits_into(_concreteSearchCtx, result, begin_id);
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::or_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::or_hits_into(_concreteSearchCtx, result, begin_id);
}

template <typename SC>
BitVector::UP
AttributeIteratorT<SC>::get_hits(uint32_t begin_id) {
    return AttributeIteratorBase::get_hits(_concreteSearchCtx, begin_id);
}

template <typename SC>
BitVector::UP
FilterAttributeIteratorT<SC>::get_hits(uint32_t begin_id) {
    return AttributeIteratorBase::get_hits(_concreteSearchCtx, begin_id);
}

template <typename SC>
void
AttributeIteratorT<SC>::and_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::and_hits_into(_concreteSearchCtx, result, begin_id);
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::and_hits_into(BitVector & result, uint32_t begin_id) {
    AttributeIteratorBase::and_hits_into(_concreteSearchCtx, result, begin_id);
}

} // namespace search
