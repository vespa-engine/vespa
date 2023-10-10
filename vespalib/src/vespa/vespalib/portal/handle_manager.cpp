// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "handle_manager.h"

#include <cassert>

namespace vespalib::portal {

void
HandleGuard::unlock()
{
    if (valid()) {
        _manager->unlock(_handle);
        _manager = nullptr;
        _handle = 0;
    }
}

HandleGuard::~HandleGuard()
{
    unlock();
}

//-----------------------------------------------------------------------------

HandleManager::Entry::~Entry()
{
    assert(use_cnt == 0);
    assert(wait_cnt == 0);
}

void
HandleManager::unlock(uint64_t handle)
{
    std::lock_guard guard(_lock);
    auto pos = _repo.find(handle);
    assert(pos != _repo.end());
    --pos->second.use_cnt;
    if (pos->second.should_notify()) {
        pos->second.cond.notify_all();
    }
}

HandleManager::HandleManager()
    : _lock(),
      _next_handle(1),
      _repo()
{
}

HandleManager::~HandleManager() = default;

size_t
HandleManager::size() const
{
    std::lock_guard guard(_lock);
    return _repo.size();
}

uint64_t
HandleManager::create()
{
    std::lock_guard guard(_lock);
    uint64_t handle = _next_handle++;
    _repo[handle];
    return handle;
}

HandleGuard
HandleManager::lock(uint64_t handle)
{
    std::lock_guard guard(_lock);
    auto pos = _repo.find(handle);
    if (pos == _repo.end()) {
        return HandleGuard();
    }
    if (pos->second.disable) {
        return HandleGuard();
    }
    ++pos->second.use_cnt;
    return HandleGuard(*this, handle);
}

bool
HandleManager::destroy(uint64_t handle)
{
    std::unique_lock guard(_lock);
    auto pos = _repo.find(handle);
    if (pos == _repo.end()) {
        return false;
    }
    pos->second.disable = true;
    ++pos->second.wait_cnt;
    while (pos->second.use_cnt > 0) {
        pos->second.cond.wait(guard);
    }
    --pos->second.wait_cnt;
    if (pos->second.should_erase()) {
        _repo.erase(pos);
        return true;
    }
    return false;
}

} // namespace vespalib::portal
