// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <condition_variable>
#include <map>
#include <memory>
#include <mutex>

namespace vespalib::portal {

class HandleManager;

/**
 * A guard that makes sure a handle remains valid while using it.
 **/
class HandleGuard {
    friend class HandleManager;
private:
    HandleManager *_manager;
    uint64_t       _handle;
    HandleGuard(HandleManager &manager_in, uint64_t handle_in)
        : _manager(&manager_in), _handle(handle_in) {}
    void unlock();
public:
    HandleGuard() : _manager(nullptr), _handle(0) {}
    HandleGuard(const HandleGuard &) = delete;
    HandleGuard &operator=(const HandleGuard &) = delete;
    HandleGuard(HandleGuard &&rhs)
        : _manager(rhs._manager),
          _handle(rhs._handle)
    {
        rhs._manager = nullptr;
        rhs._handle = 0;
    }
    HandleGuard &operator=(HandleGuard &&rhs) {
        unlock();
        _manager = rhs._manager;
        _handle = rhs._handle;
        rhs._manager = nullptr;
        rhs._handle = 0;
        return *this;
    }
    bool valid() { return (_manager != nullptr); }
    uint64_t handle() const { return _handle; }
    ~HandleGuard();
};

/**
 * A manager keeping track of all currently active handles. The
 * 'create' function will create a unique handle and return it. The
 * 'lock' function is used to obtain guard for a specific handle,
 * making sure it remains valid while using it. Calling the 'destroy'
 * function will tag the handle for destruction and also wait until
 * the handle is no longer in use. Any subsequent calls to 'lock'
 * after the handle has been tagged for destruction will return an
 * invalid guard, making it important to check the return value of
 * 'lock'. The 'destroy' function can be called by multiple actors at
 * any time. Only one of these calls will return true, indicating
 * credit for the destruction of the handle and responsibility for
 * cleaning up after it.
 **/
class HandleManager
{
    friend class HandleGuard;
private:
    struct Entry {
        std::condition_variable cond;
        bool                    disable;
        size_t                  use_cnt;
        size_t                  wait_cnt;
        bool should_notify() const {
            return ((use_cnt == 0) && (wait_cnt > 0));
        }
        bool should_erase() const {
            return (disable && (use_cnt == 0) && (wait_cnt == 0));
        }
        Entry() : cond(), disable(false), use_cnt(0), wait_cnt(0) {}
        ~Entry();
    };

    mutable std::mutex       _lock;
    uint64_t                 _next_handle;
    std::map<uint64_t,Entry> _repo;

    void unlock(uint64_t handle);
public:
    HandleManager();
    ~HandleManager();
    size_t size() const;
    bool empty() const { return (size() == 0); }
    uint64_t create();
    HandleGuard lock(uint64_t handle);
    bool destroy(uint64_t handle);
    static uint64_t null_handle() { return 0; }
};

} // namespace vespalib::portal
