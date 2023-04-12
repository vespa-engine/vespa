// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flagattribute.h"
#include "load_utils.hpp"
#include "multinumericattribute.h"
#include "multi_numeric_flag_search_context.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/query/query_term_simple.h>

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
    SaveBits(vespalib::ConstArrayRef<T> map, FA &fa)
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
    _bitVectorSize(cfg.getGrowStrategy().getInitialCapacity())
{
}

template <typename B>
std::unique_ptr<attribute::SearchContext>
FlagAttributeT<B>::getSearch(QueryTermSimple::UP qTerm, const attribute::SearchContextParams &) const
{
    return std::make_unique<attribute::MultiNumericFlagSearchContext<typename B::BaseType, typename B::WType>>(std::move(qTerm), *this, this->_mvMapping.make_read_view(this->getCommittedDocIdLimit()), _bitVectors);
}

template <typename B>
void FlagAttributeT<B>::clearOldValues(DocId doc)
{
    const typename B::WType * values(nullptr);
    for (uint32_t i(0), m(this->get(doc, values)); i < m; i++) {
        BitVector * bv = _bitVectors[getOffset(multivalue::get_value(values[i]))].load_relaxed();
        if (bv != nullptr) {
            bv->clearBitAndMaintainCount(doc);
        }
    }
}

template <typename B>
bool
FlagAttributeT<B>::onLoadEnumerated(ReaderBase &attrReader)
{
    using TT = multivalue::ValueType_t<typename B::WType>;

    uint32_t numDocs = attrReader.getNumIdx() - 1;
    uint64_t numValues = attrReader.getNumValues();
    uint64_t enumCount = attrReader.getEnumCount();
    assert(numValues == enumCount);
    (void) enumCount;

    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);
    
    if (numValues > 0)
        _bitVectorSize = numDocs;

    auto udatBuffer = attribute::LoadUtils::loadUDAT(*this);
    assert((udatBuffer->size() % sizeof(TT)) == 0);
    vespalib::ConstArrayRef<TT> map(reinterpret_cast<const TT *>(udatBuffer->buffer()),
                                    udatBuffer->size() / sizeof(TT));
    SaveBits<FlagAttributeT<B>, TT> saver(map, *this);
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader, map, vespalib::ConstArrayRef<uint32_t>(), saver);
    this->checkSetMaxValueCount(maxvc);
    
    return true;
}

template <typename B>
bool FlagAttributeT<B>::onLoad(vespalib::Executor * executor)
{
    for (size_t i(0), m(_bitVectors.size()); i < m; i++) {
        _bitVectorStore[i].reset();
        _bitVectors[i].store_relaxed(nullptr);
    }
    _bitVectorSize = 0;
    return B::onLoad(executor);
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
        BitVector * bv = _bitVectors[offset].load_relaxed();
        if (bv == nullptr) {
            assert(_bitVectorSize >= this->getNumDocs());
            _bitVectorStore[offset] = std::make_shared<GrowableBitVector>(_bitVectorSize, _bitVectorSize, _bitVectorHolder);
            _bitVectors[offset].store_release(&_bitVectorStore[offset]->writer());
            bv = _bitVectors[offset].load_relaxed();
            ensureGuardBit(*bv);
        }
        bv->setBitAndMaintainCount(doc);
    }
}

template <typename B>
void
FlagAttributeT<B>::setNewBVValue(DocId doc, multivalue::ValueType_t<typename B::WType> value)
{
    uint32_t offset = getOffset(value);
    BitVector * bv = _bitVectors[offset].load_relaxed();
    if (bv == nullptr) {
        assert(_bitVectorSize >= this->getNumDocs());
        _bitVectorStore[offset] = std::make_shared<GrowableBitVector>(_bitVectorSize, _bitVectorSize, _bitVectorHolder);
        _bitVectors[offset].store_release(&_bitVectorStore[offset]->writer());
        bv = _bitVectors[offset].load_relaxed();
        ensureGuardBit(*bv);
    }
    bv->setBitAndMaintainCount(doc);
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
    for (const auto &wrapper: _bitVectors) {
        BitVector *bv = wrapper.load_relaxed();
        if (bv != nullptr) {
            ensureGuardBit(*bv);
        }
    }
}

template <typename B>
void
FlagAttributeT<B>::clearGuardBit(DocId doc)
{
    for (const auto &wrapper: _bitVectors) {
        BitVector *bv = wrapper.load_relaxed();
        if (bv != nullptr) {
            bv->clearBit(doc); // clear guard bit and start using this doc id
        }
    }
}

template <typename B>
void
FlagAttributeT<B>::resizeBitVectors(uint32_t neededSize)
{
    const GrowStrategy & gs = this->getConfig().getGrowStrategy();
    uint32_t newSize = neededSize + (neededSize * gs.getGrowFactor()) + gs.getGrowDelta();
    for (size_t i(0), m(_bitVectors.size()); i < m; i++) {
        BitVector *bv = _bitVectors[i].load_relaxed();
        if (bv != nullptr) {
            if (_bitVectorStore[i]->extend(newSize)) {
                _bitVectors[i].store_release(&_bitVectorStore[i]->writer());
                bv = _bitVectors[i].load_relaxed();
            }
            ensureGuardBit(*bv);
        }
    }
    _bitVectorSize = newSize;
    _bitVectorHolder.assign_generation(this->getCurrentGeneration());
}


template <typename B>
void
FlagAttributeT<B>::reclaim_memory(vespalib::GenerationHandler::generation_t oldest_used_gen)
{
    B::reclaim_memory(oldest_used_gen);
    _bitVectorHolder.reclaim(oldest_used_gen);
}

template class FlagAttributeT<FlagBaseImpl>;

}
