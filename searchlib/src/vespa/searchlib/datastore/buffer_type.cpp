// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "buffer_type.h"

namespace search {
namespace datastore {

BufferTypeBase::BufferTypeBase(uint32_t clusterSize,
                               uint32_t minClusters,
                               uint32_t maxClusters)
    : _clusterSize(clusterSize),
      _minClusters(std::min(minClusters, maxClusters)),
      _maxClusters(maxClusters),
      _activeBuffers(0),
      _holdBuffers(0),
      _activeUsedElems(0),
      _holdUsedElems(0),
      _lastUsedElems(NULL)
{
}


BufferTypeBase::~BufferTypeBase(void)
{
    assert(_activeBuffers == 0);
    assert(_holdBuffers == 0);
    assert(_activeUsedElems == 0);
    assert(_holdUsedElems == 0);
    assert(_lastUsedElems == NULL);
}

size_t
BufferTypeBase::getReservedElements(uint32_t bufferId) const
{
    return bufferId == 0 ? _clusterSize : 0u;
}

void
BufferTypeBase::flushLastUsed(void)
{
    if (_lastUsedElems != NULL) {
        _activeUsedElems += *_lastUsedElems;
        _lastUsedElems = NULL;
    }
}


void
BufferTypeBase::onActive(uint32_t bufferId, size_t *usedElems, size_t &deadElems, void *buffer)
{
    flushLastUsed();
    ++_activeBuffers;
    _lastUsedElems = usedElems;
    size_t reservedElements = getReservedElements(bufferId);
    if (reservedElements != 0u) {
        initializeReservedElements(buffer, reservedElements);
        *usedElems = reservedElements;
        deadElems = reservedElements;
    }
}


void
BufferTypeBase::onHold(const size_t *usedElems)
{
    if (usedElems == _lastUsedElems)
        flushLastUsed();
    --_activeBuffers;
    ++_holdBuffers;
    assert(_activeUsedElems >= *usedElems);
    _activeUsedElems -= *usedElems;
    _holdUsedElems += *usedElems;
}


void
BufferTypeBase::onFree(size_t usedElems)
{
    --_holdBuffers;
    assert(_holdUsedElems >= usedElems);
    _holdUsedElems -= usedElems;
}


size_t
BufferTypeBase::calcClustersToAlloc(uint32_t bufferId,
                                    size_t sizeNeeded,
                                    uint64_t clusterRefSize,
                                    bool resizing) const
{
    size_t reservedElements = getReservedElements(bufferId);
    size_t usedElems = _activeUsedElems;
    if (_lastUsedElems != NULL) {
        usedElems += *_lastUsedElems;
    }
    assert((usedElems % _clusterSize) == 0);
    uint64_t maxClusters = std::numeric_limits<size_t>::max() / _clusterSize;
    uint64_t maxClusters2 = clusterRefSize;
    if (maxClusters > maxClusters2) {
        maxClusters = maxClusters2;
    }
    if (maxClusters > _maxClusters) {
        maxClusters = _maxClusters;
    }
    uint32_t minClusters = _minClusters;
    if (minClusters > maxClusters) {
        minClusters = maxClusters;
    }
    size_t usedClusters = usedElems / _clusterSize;
    size_t needClusters = (sizeNeeded + (resizing ? usedElems : reservedElements) + _clusterSize - 1) / _clusterSize;
    uint64_t wantClusters = usedClusters + minClusters;
    if (wantClusters < needClusters) {
        wantClusters = needClusters;
    }
    if (wantClusters > maxClusters) {
        wantClusters = maxClusters;
    }
    assert(wantClusters >= needClusters);
    return wantClusters;
}

} // namespace datastore
} // namespace search

