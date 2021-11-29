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
    Registration(WakeupService * service, IWakeup * toWakeup) noexcept
        : _service(service),
          _toWakeup(toWakeup)
    { }
    Registration(const Registration &) = delete;
    Registration & operator=(const Registration &) = delete;
    ~Registration() override{
        _service->unregister(_toWakeup);
    }
private:
    WakeupService * _service;
    IWakeup       * _toWakeup;
};

std::shared_ptr<IDestructorCallback>
WakeupService::registerForWakeup(IWakeup * toWakeup) {
    std::lock_guard guard(_lock);
    auto found = std::find(_toWakeup.begin(), _toWakeup.end(), toWakeup);
    if (found != _toWakeup.end()) return std::shared_ptr<IDestructorCallback>();

    _toWakeup.push_back(toWakeup);
    if ( ! _thread) {
        _thread = std::make_unique<std::thread>(WakeupService::run, this);
    }
    return std::make_shared<Registration>(this, toWakeup);
}

void
WakeupService::unregister(IWakeup * toWakeup) {
    std::lock_guard guard(_lock);
    auto found = std::find(_toWakeup.begin(), _toWakeup.end(), toWakeup);
    assert (found != _toWakeup.end());
    _toWakeup.erase(found);
}

void
WakeupService::runLoop() {
    bool done = false;
    while ( ! done ) {
        {
            std::lock_guard guard(_lock);
            for (IWakeup *toWakeup: _toWakeup) {
                toWakeup->wakeup();
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

