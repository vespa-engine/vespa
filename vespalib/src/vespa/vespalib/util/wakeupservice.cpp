// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wakeupservice.h"
#include <cassert>

namespace vespalib {

WakeupService::WakeupService(duration napTime)
    : _naptime(napTime),
      _lock(),
      _closed(false),
      _toWakeup(),
      _thread()
{
}

WakeupService::~WakeupService()
{
    {
        std::lock_guard guard(_lock);
        assert(_toWakeup.empty());
        _closed = true;
    }
    if (_thread) {
        _thread->join();
    }
}

class WakeupService::Registration : public IDestructorCallback {
public:
    Registration(WakeupService * service, VoidFunc func) noexcept
        : _service(service),
          _func(func)
    { }
    Registration(const Registration &) = delete;
    Registration & operator=(const Registration &) = delete;
    ~Registration() override{
        _service->unregister(_func);
    }
private:
    WakeupService * _service;
    VoidFunc        _func;
};

std::unique_ptr<IDestructorCallback>
WakeupService::registerForInvoke(VoidFunc func) {
    std::lock_guard guard(_lock);
    _toWakeup.push_back(func);
    if ( ! _thread) {
        _thread = std::make_unique<std::thread>(WakeupService::run, this);
    }
    return std::make_unique<Registration>(this, func);
}

void
WakeupService::unregister(VoidFunc func) {
    std::lock_guard guard(_lock);
    auto found = std::find_if(_toWakeup.begin(), _toWakeup.end(), [&func](const VoidFunc & a) {
        return func.target<VoidFunc>() == a.target<VoidFunc>();
    });
    assert (found != _toWakeup.end());
    _toWakeup.erase(found);
}

void
WakeupService::runLoop() {
    bool done = false;
    while ( ! done ) {
        {
            std::lock_guard guard(_lock);
            for (VoidFunc & func: _toWakeup) {
                func();
            }
            done = _closed;
        }
        if ( ! done) {
            std::this_thread::sleep_for(_naptime);
        }
    }

}

void
WakeupService::run(WakeupService * service) {
    service->runLoop();
}

}

