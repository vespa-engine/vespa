// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

class MemoryUsage {
private:
    size_t _allocatedBytes;
    size_t _usedBytes;
    size_t _deadBytes;
    size_t _allocatedBytesOnHold;

public:
    MemoryUsage() noexcept
        : _allocatedBytes(0),
          _usedBytes(0),
          _deadBytes(0),
          _allocatedBytesOnHold(0)
    { }

    MemoryUsage(size_t allocated, size_t used, size_t dead, size_t onHold) noexcept
        : _allocatedBytes(allocated),
          _usedBytes(used),
          _deadBytes(dead),
          _allocatedBytesOnHold(onHold)
    { }

    size_t allocatedBytes() const { return _allocatedBytes; }
    size_t usedBytes() const { return _usedBytes; }
    size_t deadBytes() const { return _deadBytes; }
    size_t allocatedBytesOnHold() const { return _allocatedBytesOnHold; }
    void incAllocatedBytes(size_t inc) { _allocatedBytes += inc; }
    void decAllocatedBytes(size_t dec) { _allocatedBytes -= dec; }
    void incUsedBytes(size_t inc) { _usedBytes += inc; }
    void decUsedBytes(size_t dec) { _usedBytes -= dec; }
    void incDeadBytes(size_t inc) { _deadBytes += inc; }
    void incAllocatedBytesOnHold(size_t inc) { _allocatedBytesOnHold += inc; }
    void decAllocatedBytesOnHold(size_t inc) { _allocatedBytesOnHold -= inc; }
    void setAllocatedBytes(size_t alloc) { _allocatedBytes = alloc; }
    void setUsedBytes(size_t used) { _usedBytes = used; }
    void setDeadBytes(size_t dead) { _deadBytes = dead; }
    void setAllocatedBytesOnHold(size_t onHold) { _allocatedBytesOnHold = onHold; }

    void mergeGenerationHeldBytes(size_t inc) {
        _allocatedBytes += inc;
        _usedBytes += inc;
        _allocatedBytesOnHold += inc;
    }

    void merge(const MemoryUsage & rhs) {
        _allocatedBytes += rhs._allocatedBytes;
        _usedBytes += rhs._usedBytes;
        _deadBytes += rhs._deadBytes;
        _allocatedBytesOnHold += rhs._allocatedBytesOnHold;
    }
    string toString() const;
};

class asciistream;

asciistream & operator << (asciistream & os, const MemoryUsage & usage);

} // namespace vespalib
