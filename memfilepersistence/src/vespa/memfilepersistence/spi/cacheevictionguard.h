// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/memfile/memfileptr.h>
#include <cassert>

namespace storage {
namespace memfile {

/**
 * Guard which will forcefully un-mark a file as being modified and evict
 * it from the cache if an exception occurs before it is destructed (more
 * specifically, if unguard() is never invoked on it).
 *
 * Any data not yet persisted when the memfile is evicted will be lost.
 * It's up to the caller to ensure that this does not actually cause
 * any true data loss.
 */
class MemFileCacheEvictionGuard
{
public:
    MemFileCacheEvictionGuard(const MemFilePtr& ptr)
        : _ptr(ptr),
          _ok(false)
    {
        assert(_ptr.get());
    }
    ~MemFileCacheEvictionGuard();

    MemFile* operator->() { return _ptr.get(); }
    MemFile& operator*() { return *_ptr; }
    const MemFile* operator->() const { return _ptr.get(); }
    const MemFile& operator*() const { return *_ptr; }

    const MemFilePtr& get() const { return _ptr; }
    MemFilePtr& get() { return _ptr; }

    void unguard() { _ok = true; }
private:
    MemFilePtr _ptr;
    bool _ok;
};

}
}

