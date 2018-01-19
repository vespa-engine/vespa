// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buffer_type.h"
#include <algorithm>
#include <cassert>

namespace search::datastore {

namespace {

constexpr float DEFAULT_ALLOC_GROW_FACTOR = 0.2;

}

void
BufferTypeBase::CleanContext::extraBytesCleaned(uint64_t value)
{
    assert(_extraBytes >= value);
    _extraBytes -= value;
}

BufferTypeBase::BufferTypeBase(uint32_t clusterSize,
                               uint32_t minClusters,
                               uint32_t maxClusters,
                               uint32_t numClustersForNewBuffer,
                               float allocGrowFactor)
    : _clusterSize(clusterSize),
      _minClusters(std::min(minClusters, maxClusters)),
      _maxClusters(maxClusters),
      _numClustersForNewBuffer(std::min(numClustersForNewBuffer, maxClusters)),
      _allocGrowFactor(allocGrowFactor),
      _activeBuffers(0),
      _holdBuffers(0),
      _activeUsedElems(0),
      _holdUsedElems(0),
      _lastUsedElems(nullptr)
{
}

BufferTypeBase::BufferTypeBase(uint32_t clusterSize,
                               uint32_t minClusters,
                               uint32_t maxClusters)
    : BufferTypeBase(clusterSize, minClusters, maxClusters, 0u, DEFAULT_ALLOC_GROW_FACTOR)
{
}

BufferTypeBase::~BufferTypeBase()
{
    assert(_activeBuffers == 0);
    assert(_holdBuffers == 0);
    assert(_activeUsedElems == 0);
    assert(_holdUsedElems == 0);
    assert(_lastUsedElems == nullptr);
}

size_t
BufferTypeBase::getReservedElements(uint32_t bufferId) const
{
    return bufferId == 0 ? _clusterSize : 0u;
}

void
BufferTypeBase::flushLastUsed()
{
    if (_lastUsedElems != nullptr) {
        _activeUsedElems += *_lastUsedElems;
        _lastUsedElems = nullptr;
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
    if (usedElems == _lastUsedElems) {
        flushLastUsed();
    }
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

void
BufferTypeBase::clampMaxClusters(uint32_t maxClusters)
{
    _maxClusters = std::min(_maxClusters, maxClusters);
    _minClusters = std::min(_minClusters, _maxClusters);
    _numClustersForNewBuffer = std::min(_numClustersForNewBuffer, _maxClusters);
}

size_t
BufferTypeBase::calcClustersToAlloc(uint32_t bufferId, size_t sizeNeeded, bool resizing) const
{
    size_t reservedElements = getReservedElements(bufferId);
    size_t usedElems = (resizing ? 0 : _activeUsedElems);
    if (_lastUsedElems != nullptr) {
        usedElems += *_lastUsedElems;
    }
    assert((usedElems % _clusterSize) == 0);
    size_t usedClusters = usedElems / _clusterSize;
    size_t needClusters = (sizeNeeded + (resizing ? usedElems : reservedElements) + _clusterSize - 1) / _clusterSize;
    size_t growClusters = (usedClusters * _allocGrowFactor);
    size_t wantClusters = std::max((resizing ? usedClusters : 0u) + growClusters,
                                   static_cast<size_t>(_minClusters));
    size_t result = wantClusters;
    if (result < needClusters) {
        result = needClusters;
    }
    if (result > _maxClusters) {
        result = _maxClusters;
    }
    assert(result >= needClusters);
    return result;
}

}

