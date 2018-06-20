// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flagattribute.h"
#include "load_utils.hpp"
#include "attributeiterators.h"
#include "multinumericattribute.hpp"

#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.flag_attribute");

namespace search {

using queryeval::SearchIterator;

namespace {

template <class FA, typename T>
class SaveBits
{
    vespalib::ConstArrayRef<T> _map;
    FA &_fa;
    
public:
    SaveBits(vespalib::ConstArrayRef<T> map,
             FA &fa)
        : _map(map),
          _fa(fa)
    {
    }
    
    void
    save(uint32_t e, uint32_t docId, int32_t weight)
    {
        (void) weight;
        assert(e < _map.size());
        _fa.setNewBVValue(docId, _map[e]);
    }
};

}


template <typename B>
FlagAttributeT<B>::FlagAttributeT(const vespalib::string & baseFileName, const AttributeVector::Config & cfg) :
    B(baseFileName, cfg),
    _bitVectorHolder(),
    _bitVectorStore(256),
    _bitVectors(256),
    _bitVectorSize(cfg.getGrowStrategy().getDocsInitialCapacity())
{
}

template <typename B>
AttributeVector::SearchContext::UP
FlagAttributeT<B>::getSearch(QueryTermSimple::UP qTerm,
                             const attribute::SearchContextParams & params) const
{
    (void) params;
    return AttributeVector::SearchContext::UP (new SearchContext(std::move(qTerm), *this));
}

template <typename B>
void FlagAttributeT<B>::clearOldValues(DocId doc)
{
    const typename B::WType * values(NULL);
    for (uint32_t i(0), m(this->get(doc, values)); i < m; i++) {
        BitVector * bv = _bitVectors[getOffset(values[i].value())];
        if (bv != NULL) {
            bv->clearBit(doc);
        }
    }
}

template <typename B>
bool
FlagAttributeT<B>::onLoadEnumerated(ReaderBase &attrReader)
{
    typedef typename B::WType::ValueType TT;

    uint32_t numDocs = attrReader.getNumIdx() - 1;
    uint64_t numValues = attrReader.getNumValues();
    uint64_t enumCount = attrReader.getEnumCount();
    assert(numValues == enumCount);
    (void) enumCount;

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    
    if (numValues > 0)
        _bitVectorSize = numDocs;

    fileutil::LoadedBuffer::UP udatBuffer(this->loadUDAT());
    assert((udatBuffer->size() % sizeof(TT)) == 0);
    vespalib::ConstArrayRef<TT> map(reinterpret_cast<const TT *>(udatBuffer->buffer()),
                                    udatBuffer->size() / sizeof(TT));
    SaveBits<FlagAttributeT<B>, TT> saver(map, *this);
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader, map, saver);
    this->checkSetMaxValueCount(maxvc);
    
    return true;
}

template <typename B>
bool FlagAttributeT<B>::onLoad()
{
    for (size_t i(0), m(_bitVectors.size()); i < m; i++) {
        _bitVectorStore[i].reset();
        _bitVectors[i] = NULL;
    }
    _bitVectorSize = 0;
    return B::onLoad();
}

template <typename B>
void FlagAttributeT<B>::setNewValues(DocId doc, const std::vector<typename B::WType> & values)
{
    B::setNewValues(doc, values);
    if (_bitVectorSize == 0) { // attribute being loaded
        _bitVectorSize = this->getNumDocs();
    }
    for (uint32_t i(0), m(values.size()); i < m; i++) {
        typename B::WType value = values[i];
        uint32_t offset = getOffset(value);
        BitVector * bv = _bitVectors[offset];
        if (bv == NULL) {
            assert(_bitVectorSize >= this->getNumDocs());
            _bitVectorStore[offset] = BitVector::create(_bitVectorSize);
            _bitVectors[offset] = _bitVectorStore[offset].get();
            bv = _bitVectors[offset];
            bv->invalidateCachedCount();
            ensureGuardBit(*bv);
        }
        bv->setBit(doc);
    }
}

template <typename B>
void
FlagAttributeT<B>::setNewBVValue(DocId doc, typename B::WType::ValueType value)
{
    uint32_t offset = getOffset(value);
    BitVector * bv = _bitVectors[offset];
    if (bv == NULL) {
        assert(_bitVectorSize >= this->getNumDocs());
            _bitVectorStore[offset] = BitVector::create(_bitVectorSize);
        _bitVectors[offset] = _bitVectorStore[offset].get();
        bv = _bitVectors[offset];
        bv->invalidateCachedCount();
        ensureGuardBit(*bv);
    }
    bv->setBit(doc);
}


template <typename B>
bool
FlagAttributeT<B>::onAddDoc(DocId doc)
{
    bool retval = false;
    if (doc >= _bitVectorSize) {
        resizeBitVectors(this->getNumDocs());
        retval = true;
    } else {
        ensureGuardBit();
    }
    std::atomic_thread_fence(std::memory_order_release);
    clearGuardBit(doc);
    return retval;
}

template <typename B>
void
FlagAttributeT<B>::onAddDocs(DocId docidLimit)
{
    if (docidLimit > _bitVectorSize) {
        resizeBitVectors(docidLimit);
    }
}

template <typename B>
void
FlagAttributeT<B>::ensureGuardBit(BitVector & bv)
{
    if (this->getNumDocs() < bv.size()) {
        bv.setBit(this->getNumDocs()); // add guard bit to avoid scanning to the end during search
    }
}

template <typename B>
void
FlagAttributeT<B>::ensureGuardBit()
{
    for (uint32_t i = 0; i < _bitVectors.size(); ++i) {
        BitVector * bv = _bitVectors[i];
        if (bv != NULL) {
            ensureGuardBit(*bv);
        }
    }
}

template <typename B>
void
FlagAttributeT<B>::clearGuardBit(DocId doc)
{
    for (uint32_t i = 0; i < _bitVectors.size(); ++i) {
        BitVector * bv = _bitVectors[i];
        if (bv != NULL) {
            bv->clearBit(doc); // clear guard bit and start using this doc id
        }
    }
}

template <typename B>
void
FlagAttributeT<B>::resizeBitVectors(uint32_t neededSize)
{
    const GrowStrategy & gs = this->getConfig().getGrowStrategy();
    uint32_t newSize = neededSize + (neededSize * gs.getDocsGrowPercent() / 100) + gs.getDocsGrowDelta();
    for (uint32_t i = 0; i < _bitVectors.size(); ++i) {
        BitVector * bv = _bitVectors[i];
        if (bv != NULL) {
            vespalib::GenerationHeldBase::UP hold(bv->grow(newSize));
            ensureGuardBit(*bv);
            _bitVectorHolder.hold(std::move(hold));
        }
    }
    _bitVectorSize = newSize;
    _bitVectorHolder.transferHoldLists(this->getCurrentGeneration());
}


template <typename B>
void
FlagAttributeT<B>::removeOldGenerations(vespalib::GenerationHandler::generation_t firstUsed)
{
    B::removeOldGenerations(firstUsed);
    _bitVectorHolder.trimHoldLists(firstUsed);
}

template <typename B>
FlagAttributeT<B>::SearchContext::SearchContext(QueryTermSimple::UP qTerm, const FlagAttributeT<B> & toBeSearched) :
    BaseSC(std::move(qTerm), toBeSearched),
    _zeroHits(false)
{
}

template <typename B>
SearchIterator::UP
FlagAttributeT<B>::SearchContext::createIterator(fef::TermFieldMatchData *
                                                 matchData,
                                                 bool strict)
{
    if (valid()) {
        if (_low == _high) {
            const Attribute & attr(static_cast<const Attribute &>(attribute()));
            const BitVector * bv(attr.getBitVector(_low));
            if (bv != NULL) {
                return BitVectorIterator::create(bv, attr.getCommittedDocIdLimit(), *matchData, strict);
            } else {
                return SearchIterator::UP(new queryeval::EmptySearch());
            }
        } else {
            SearchIterator::UP flagIterator(
              strict
                 ? new FlagAttributeIteratorStrict<typename FlagAttributeT<B>::SearchContext>(*this, matchData)
                 : new FlagAttributeIteratorT<typename FlagAttributeT<B>::SearchContext>(*this, matchData));
            return flagIterator;
        }
    } else {
        return SearchIterator::UP(new queryeval::EmptySearch());
    }
}

template class FlagAttributeT<FlagBaseImpl>;

}
