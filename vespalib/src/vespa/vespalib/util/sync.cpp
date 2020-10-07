// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sync.h"
#include <cassert>

namespace vespalib {

Lock::Lock() noexcept
    : _mutex(std::make_unique<std::mutex>())
{}
Lock::Lock(Lock &&rhs) noexcept = default;
Lock::~Lock() = default;


Monitor::Monitor() noexcept
    : Lock(),
      _cond(std::make_unique<std::condition_variable>())
{}
Monitor::Monitor(Monitor &&rhs) noexcept = default;
Monitor::~Monitor() = default;


TryLock::TryLock(const Lock &lock)
    : _guard(*lock._mutex, std::try_to_lock),
      _cond(nullptr)
{}

TryLock::TryLock(const Monitor &mon)
    : _guard(*mon._mutex, std::try_to_lock),
      _cond(_guard ? mon._cond.get() : nullptr)
{}

TryLock::TryLock(TryLock &&rhs) noexcept
    : _guard(std::move(rhs._guard)),
      _cond(rhs._cond)
{
    rhs._cond = nullptr;
}

TryLock::~TryLock() = default;

bool
TryLock::hasLock() const {
    return static_cast<bool>(_guard);
}

void
TryLock::unlock() {
    if (_guard) {
        _guard.unlock();
        _cond = nullptr;
    }
}

LockGuard::LockGuard() : _guard() {}

LockGuard::LockGuard(LockGuard &&rhs) noexcept : _guard(std::move(rhs._guard)) { }
LockGuard::LockGuard(const Lock &lock) : _guard(*lock._mutex) { }
LockGuard::LockGuard(TryLock &&tlock) : _guard(std::move(tlock._guard))
{
    tlock._cond = nullptr;
}

LockGuard &
LockGuard::operator=(LockGuard &&rhs) noexcept{
    if (this != &rhs) {
        _guard = std::move(rhs._guard);
    }
    return *this;
}

void
LockGuard::unlock() {
    if (_guard) {
        _guard.unlock();
    }
}
LockGuard::~LockGuard() = default;

bool
LockGuard::locks(const Lock& lock) const {
    return (_guard && _guard.mutex() == lock._mutex.get());
}

MonitorGuard::MonitorGuard() : _guard(), _cond(nullptr) {}
MonitorGuard::MonitorGuard(MonitorGuard &&rhs) noexcept
    : _guard(std::move(rhs._guard)),
      _cond(rhs._cond)
{
    rhs._cond = nullptr;
}
MonitorGuard::MonitorGuard(const Monitor &monitor)
    : _guard(*monitor._mutex),
      _cond(monitor._cond.get())
{ }
MonitorGuard::MonitorGuard(TryLock &&tlock)
    : _guard(),
      _cond(nullptr)
{
    if (tlock._guard && tlock._cond != nullptr) {
        _guard = std::move(tlock._guard);
        _cond = tlock._cond;
        tlock._cond = nullptr;
    }
}

MonitorGuard &
MonitorGuard::operator=(MonitorGuard &&rhs) noexcept {
    if (this != &rhs) {
        _guard = std::move(rhs._guard);
        _cond = rhs._cond;
        rhs._cond = nullptr;
    }
    return *this;
}

void
MonitorGuard::unlock() {
    assert(_guard);
    _guard.unlock();
    _cond = nullptr;
}
void
MonitorGuard::wait() {
    _cond->wait(_guard);
}
bool
MonitorGuard::wait(int msTimeout) {
    return wait(std::chrono::milliseconds(msTimeout));
}
bool
MonitorGuard::wait(std::chrono::nanoseconds timeout) {
    return _cond->wait_for(_guard, timeout) == std::cv_status::no_timeout;
}
void
MonitorGuard::signal() {
    _cond->notify_one();
}
void
MonitorGuard::broadcast() {
    _cond->notify_all();
}
void
MonitorGuard::unsafeSignalUnlock() {
    _guard.unlock();
    _cond->notify_one();
    _cond = nullptr;
}

MonitorGuard::~MonitorGuard() = default;

} // namespace vespalib

