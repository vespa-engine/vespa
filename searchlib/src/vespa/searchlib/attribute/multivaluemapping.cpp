// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.multivaluemapping");
#include "multivaluemapping.h"
#include "multivaluemapping.hpp"
#include "attributevector.h"
#include "loadedenumvalue.h"

namespace search {

using vespalib::GenerationHeldBase;

MultiValueMappingBaseBase::MultiValueMappingBaseBase(size_t maxValues,
        size_t maxAlternatives)
    : _singleVectorsStatus(maxValues * maxAlternatives),
      _vectorVectorsStatus(maxAlternatives),
      _genHolder(),
      _pendingCompactSingleVector(),
      _pendingCompactVectorVector(false),
      _pendingCompact(false),
      _totalValueCnt(0)
{
}

MultiValueMappingBaseBase::~MultiValueMappingBaseBase()
{
}

void
MultiValueMappingBaseBase::failNewSize(uint64_t minNewSize, uint64_t maxSize)
{
    LOG(fatal,
        "MultiValueMappingBase::failNewSize: "
        "Minimum new size (%" PRIu64 ") exceeds max size (%" PRIu64 ")",
        minNewSize, maxSize);
    abort();
}

size_t
MultiValueMappingBaseBase::
computeNewSize(size_t used, size_t dead, size_t needed, size_t maxSize)
{
    float growRatio = 1.5f;
    size_t newSize = static_cast<size_t>
                       ((used - dead + needed) * growRatio);
    if (newSize <= maxSize)
        return newSize;
    newSize = (used - dead + needed) + 1000000;
    if (newSize <= maxSize)
        return maxSize;
    failNewSize(newSize, maxSize);
    return 0;
}

MultiValueMappingBaseBase::Histogram::Histogram(size_t maxValues) :
    _maxValues(maxValues),
    _histogram()
{
}

MultiValueMappingBaseBase::Histogram
MultiValueMappingBaseBase::getEmptyHistogram(size_t maxValues) const
{
    return Histogram(maxValues);
}

MultiValueMappingBaseBase::Histogram
MultiValueMappingBaseBase::getHistogram(AttributeVector::ReaderBase &reader)
    const
{
    Histogram capacityNeeded = getEmptyHistogram();
    uint32_t numDocs(reader.getNumIdx() - 1);
    for (AttributeVector::DocId doc = 0; doc < numDocs; ++doc) {
        const uint32_t valueCount(reader.getNextValueCount());
        capacityNeeded[valueCount] += 1;
    }
    return capacityNeeded;
}


void
MultiValueMappingBaseBase::clearPendingCompact(void)
{
    if (!_pendingCompact || _pendingCompactVectorVector ||
        !_pendingCompactSingleVector.empty())
        return;
    _pendingCompact = false;
}


template <typename I>
class MultiValueMappingHeldVector : public GenerationHeldBase
{
    typedef I Index;

    MultiValueMappingBase<I> &_mvmb;
    Index _idx;

public:
    MultiValueMappingHeldVector(size_t size,
                                MultiValueMappingBase<I> &mvmb,
                                Index &idx)
        : GenerationHeldBase(size),
          _mvmb(mvmb),
          _idx(idx)
    {
    }

    virtual
    ~MultiValueMappingHeldVector(void)
    {
        _mvmb.doneHoldVector(_idx);
    }
};


template <typename I>
void MultiValueMappingBase<I>::doneHoldVector(Index idx)
{
#ifdef LOG_MULTIVALUE_MAPPING
    LOG(info,
        "free vector: idx.values() = %u, idx.alternative() = %u",
        idx.values(), idx.alternative());
#endif
    clearVector(idx);
    if (idx.values() < Index::maxValues()) {
        _singleVectorsStatus[idx.vectorIdx()] = FREE;
    } else if (idx.values() == Index::maxValues()) {
        _vectorVectorsStatus[idx.alternative()] = FREE;
    }
}


template <typename I>
MemoryUsage
MultiValueMappingBase<I>::getMemoryUsage() const
{
    MemoryUsage retval = _indices.getMemoryUsage();

    for (size_t i = 0; i < _singleVectorsStatus.size(); ++i) {
        if (_singleVectorsStatus[i] == HOLD)
            continue;
        const MemoryUsage & memUsage(getSingleVectorUsage(i));
        retval.merge(memUsage);
    }
    for (size_t i = 0; i < _vectorVectorsStatus.size(); ++i) {
        if (_vectorVectorsStatus[i] == HOLD)
            continue;
        const MemoryUsage & memUsage(getVectorVectorUsage(i));
        retval.merge(memUsage);
    }
    retval.incAllocatedBytesOnHold(_genHolder.getHeldBytes());
    return retval;
}

template <typename I>
AddressSpace
MultiValueMappingBase<I>::getAddressSpaceUsage() const
{
    size_t addressSpaceUsed = 0;
    for (size_t i = 0; i < _singleVectorsStatus.size(); ++i) {
        if (_singleVectorsStatus[i] == ACTIVE) {
            addressSpaceUsed = std::max(addressSpaceUsed, getSingleVectorAddressSpaceUsed(i));
        }
    }
    for (size_t i = 0; i < _vectorVectorsStatus.size(); ++i) {
        if (_vectorVectorsStatus[i] == ACTIVE) {
            addressSpaceUsed = std::max(addressSpaceUsed, getVectorVectorAddressSpaceUsed(i));
        }
    }
    return AddressSpace(addressSpaceUsed, Index::offsetSize());
}

template <typename I>
MultiValueMappingBase<I>::MultiValueMappingBase(uint32_t &committedDocIdLimit,
                                                uint32_t numKeys,
        const GrowStrategy & gs)
    : MultiValueMappingBaseBase(Index::maxValues(), Index::alternativeSize()),
      _indices(gs.getDocsInitialCapacity(),
               gs.getDocsGrowPercent(),
               gs.getDocsGrowDelta(),
               _genHolder),
      _committedDocIdLimit(committedDocIdLimit)
{
    _indices.unsafe_reserve(numKeys);
    _indices.unsafe_resize(numKeys);
}

template <typename I>
MultiValueMappingBase<I>::~MultiValueMappingBase()
{
}

template <typename I>
void MultiValueMappingBase<I>::insertIntoHoldList(Index idx)
{
    size_t holdBytes = 0u;
    if (idx.values() < Index::maxValues()) {
        _singleVectorsStatus[idx.vectorIdx()] = HOLD;
        holdBytes = getSingleVectorUsage(idx.vectorIdx()).allocatedBytes();
    } else {
        _vectorVectorsStatus[idx.alternative()] = HOLD;
        holdBytes = getVectorVectorUsage(idx.alternative()).allocatedBytes();
    }
    GenerationHeldBase::UP hold(new MultiValueMappingHeldVector<I>(holdBytes,
                                        *this,
                                        idx));
    _genHolder.hold(std::move(hold));
}


template <typename I>
void MultiValueMappingBase<I>::setActiveVector(Index idx)
{
    if (idx.values() < Index::maxValues()) {
        _singleVectorsStatus[idx.vectorIdx()] = ACTIVE;
    } else {
        _vectorVectorsStatus[idx.alternative()] = ACTIVE;
    }
}

template <typename I>
void
MultiValueMappingBase<I>::reset(uint32_t numKeys)
{
    _genHolder.clearHoldLists();
    _indices.reset();
    _indices.unsafe_reserve(numKeys);
    for (size_t i = 0; i < numKeys; ++i) {
        _indices.push_back(Index());
    }
}


template <typename I>
void
MultiValueMappingBase<I>::addKey(uint32_t & key)
{
    uint32_t retval = _indices.size();
    _indices.push_back(Index());
    key = retval;
}


template <typename I>
void
MultiValueMappingBase<I>::shrinkKeys(uint32_t newSize)
{
    assert(newSize >= _committedDocIdLimit);
    assert(newSize < _indices.size());
    _indices.shrink(newSize);
}


template <typename I>
void
MultiValueMappingBase<I>::clearDocs(uint32_t lidLow, uint32_t lidLimit,
                                    AttributeVector &v)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= v.getNumDocs());
    assert(lidLimit <= _indices.size());
    for (uint32_t lid = lidLow; lid < lidLimit; ++lid) {
        if (_indices[lid].idx() != 0) {
            v.clearDoc(lid);
        }
    }
}

template <typename I>
class MultiValueMappingHoldElem : public GenerationHeldBase
{
    typedef I Index;

    MultiValueMappingBase<I> &_mvmb;
    Index _idx;
public:
    MultiValueMappingHoldElem(size_t size,
                              MultiValueMappingBase<I> &mvmb,
                              Index idx)
        : GenerationHeldBase(size),
          _mvmb(mvmb),
          _idx(idx)
    {
    }

    virtual ~MultiValueMappingHoldElem() {
        _mvmb.doneHoldElem(_idx);
    }
};


template <typename I>
void
MultiValueMappingBase<I>::holdElem(Index idx, size_t size)
{
    GenerationHeldBase::UP hold(new MultiValueMappingHoldElem<I>(size, *this,
                                                                 idx));
    _genHolder.hold(std::move(hold));
}


template class MultiValueMappingBase<multivalue::Index32>;
template class MultiValueMappingBase<multivalue::Index64>;

template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int8_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int16_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int32_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int64_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<float> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<double> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<EnumStoreBase::Index> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int8_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int16_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int32_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int64_t> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<float> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<double> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<EnumStoreBase::Index> >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int8_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int16_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int32_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int64_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<float> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<double> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<EnumStoreBase::Index> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int8_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int16_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int32_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int64_t> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<float> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<double> > >::VectorBase >;
template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<EnumStoreBase::Index> > >::VectorBase >;

template class MultiValueMappingVector<
    multivalue::Value<int8_t> >;
template class MultiValueMappingVector<
    multivalue::Value<int16_t> >;
template class MultiValueMappingVector<
    multivalue::Value<int32_t> >;
template class MultiValueMappingVector<
    multivalue::Value<int64_t> >;
template class MultiValueMappingVector<
    multivalue::Value<float> >;
template class MultiValueMappingVector<
    multivalue::Value<double> >;
template class MultiValueMappingVector<
    multivalue::Value<EnumStoreBase::Index> >;
template class MultiValueMappingVector<
    multivalue::WeightedValue<int8_t> >;
template class MultiValueMappingVector<
    multivalue::WeightedValue<int16_t> >;
template class MultiValueMappingVector<
    multivalue::WeightedValue<int32_t> >;
template class MultiValueMappingVector<
    multivalue::WeightedValue<int64_t> >;
template class MultiValueMappingVector<
    multivalue::WeightedValue<float> >;
template class MultiValueMappingVector<
    multivalue::WeightedValue<double> >;
template class MultiValueMappingVector<
    multivalue::WeightedValue<EnumStoreBase::Index> >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int8_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int16_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int32_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int64_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<float> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<double> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<EnumStoreBase::Index> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int8_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int16_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int32_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int64_t> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<float> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<double> > >;
template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<EnumStoreBase::Index> > >;

template class MultiValueMappingT<multivalue::Value<int8_t> >;
template class MultiValueMappingT<multivalue::Value<int16_t> >;
template class MultiValueMappingT<multivalue::Value<int32_t> >;
template class MultiValueMappingT<multivalue::Value<int64_t> >;
template class MultiValueMappingT<multivalue::Value<float> >;
template class MultiValueMappingT<multivalue::Value<double> >;
template class MultiValueMappingT<
    multivalue::Value<EnumStoreBase::Index> >;
template class MultiValueMappingT<multivalue::WeightedValue<int8_t> >;
template class MultiValueMappingT<multivalue::WeightedValue<int16_t> >;
template class MultiValueMappingT<multivalue::WeightedValue<int32_t> >;
template class MultiValueMappingT<multivalue::WeightedValue<int64_t> >;
template class MultiValueMappingT<multivalue::WeightedValue<float> >;
template class MultiValueMappingT<multivalue::WeightedValue<double> >;
template class MultiValueMappingT<
    multivalue::WeightedValue<EnumStoreBase::Index> >;
template class MultiValueMappingT<multivalue::Value<int8_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::Value<int16_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::Value<int32_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::Value<int64_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::Value<float>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::Value<double>,
                                  multivalue::Index64>;
template class MultiValueMappingT<
    multivalue::Value<EnumStoreBase::Index>,
    multivalue::Index64>;
template class MultiValueMappingT<multivalue::WeightedValue<int8_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::WeightedValue<int16_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::WeightedValue<int32_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::WeightedValue<int64_t>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::WeightedValue<float>,
                                  multivalue::Index64>;
template class MultiValueMappingT<multivalue::WeightedValue<double>,
                                  multivalue::Index64>;
template class MultiValueMappingT<
    multivalue::WeightedValue<EnumStoreBase::Index>,
    multivalue::Index64>;

using attribute::SaveLoadedEnum;
using attribute::NoSaveLoadedEnum;
using attribute::SaveEnumHist;
typedef EnumStoreBase::Index EnumIndex;

template
uint32_t
MultiValueMappingT<multivalue::Value<EnumIndex>,
                   multivalue::Index32>::
fillMapped<EnumIndex, SaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const EnumIndex *map,
                                      size_t mapSize,
                                      SaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<EnumIndex>,
                   multivalue::Index32>::
fillMapped<EnumIndex, SaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                           uint64_t numValues,
                           const EnumIndex *map,
                           size_t mapSize,
                           SaveLoadedEnum &saver,
                           uint32_t numDocs,
                           bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<EnumIndex>,
                   multivalue::Index64>::
fillMapped<EnumIndex, SaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const EnumIndex *map,
                                      size_t mapSize,
                                      SaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<EnumIndex>,
                   multivalue::Index64>::
fillMapped<EnumIndex, SaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const EnumIndex *map,
                                      size_t mapSize,
                                      SaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<EnumIndex>,
                   multivalue::Index32>::
fillMapped<EnumIndex, SaveEnumHist>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const EnumIndex *map,
                                    size_t mapSize,
                                    SaveEnumHist &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<EnumIndex>,
                   multivalue::Index32>::
fillMapped<EnumIndex, SaveEnumHist>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const EnumIndex *map,
                                    size_t mapSize,
                                    SaveEnumHist &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<EnumIndex>,
                   multivalue::Index64>::
fillMapped<EnumIndex, SaveEnumHist>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const EnumIndex *map,
                                    size_t mapSize,
                                    SaveEnumHist &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<EnumIndex>,
                   multivalue::Index64>::
fillMapped<EnumIndex, SaveEnumHist>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const EnumIndex *map,
                                    size_t mapSize,
                                    SaveEnumHist &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int8_t>,
                   multivalue::Index32>::
fillMapped<int8_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const int8_t *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int16_t>,
                   multivalue::Index32>::
fillMapped<int16_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int16_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int32_t>,
                   multivalue::Index32>::
fillMapped<int32_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int32_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int64_t>,
                   multivalue::Index32>::
fillMapped<int64_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int64_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<float>,
                   multivalue::Index32>::
fillMapped<float, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const float *map,
                                    size_t mapSize,
                                    NoSaveLoadedEnum &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<double>,
                   multivalue::Index32>::
fillMapped<double, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const double *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int8_t>,
                   multivalue::Index32>::
fillMapped<int8_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const int8_t *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int16_t>,
                   multivalue::Index32>::
fillMapped<int16_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int16_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int32_t>,
                   multivalue::Index32>::
fillMapped<int32_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int32_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int64_t>,
                   multivalue::Index32>::
fillMapped<int64_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int64_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<float>,
                   multivalue::Index32>::
fillMapped<float, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const float *map,
                                    size_t mapSize,
                                    NoSaveLoadedEnum &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<double>,
                   multivalue::Index32>::
fillMapped<double, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const double *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int8_t>,
                   multivalue::Index64>::
fillMapped<int8_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const int8_t *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int16_t>,
                   multivalue::Index64>::
fillMapped<int16_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int16_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int32_t>,
                   multivalue::Index64>::
fillMapped<int32_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int32_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<int64_t>,
                   multivalue::Index64>::
fillMapped<int64_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int64_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<float>,
                   multivalue::Index64>::
fillMapped<float, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const float *map,
                                    size_t mapSize,
                                    NoSaveLoadedEnum &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::Value<double>,
                   multivalue::Index64>::
fillMapped<double, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const double *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int8_t>,
                   multivalue::Index64>::
fillMapped<int8_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const int8_t *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int16_t>,
                   multivalue::Index64>::
fillMapped<int16_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int16_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int32_t>,
                   multivalue::Index64>::
fillMapped<int32_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int32_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<int64_t>,
                   multivalue::Index64>::
fillMapped<int64_t, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                      uint64_t numValues,
                                      const int64_t *map,
                                      size_t mapSize,
                                      NoSaveLoadedEnum &saver,
                                      uint32_t numDocs,
                                      bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<float>,
                   multivalue::Index64>::
fillMapped<float, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                    uint64_t numValues,
                                    const float *map,
                                    size_t mapSize,
                                    NoSaveLoadedEnum &saver,
                                    uint32_t numDocs,
                                    bool hasWeights);

template
uint32_t
MultiValueMappingT<multivalue::WeightedValue<double>,
                   multivalue::Index64>::
fillMapped<double, NoSaveLoadedEnum>(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const double *map,
                                     size_t mapSize,
                                     NoSaveLoadedEnum &saver,
                                     uint32_t numDocs,
                                     bool hasWeights);

} // namespace search
