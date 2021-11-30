// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invokeserviceimpl.h"
#include <cassert>

namespace vespalib {

InvokeServiceImpl::InvokeServiceImpl(duration napTime)
    : _naptime(napTime),
      _lock(),
      _closed(false),
      _toWakeup(),
      _thread()
{
}

InvokeServiceImpl::~InvokeServiceImpl()
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

class InvokeServiceImpl::Registration : public IDestructorCallback {
public:
    Registration(InvokeServiceImpl * service, VoidFunc func) noexcept
        : _service(service),
          _func(func)
    { }
    Registration(const Registration &) = delete;
    Registration & operator=(const Registration &) = delete;
    ~Registration() override{
        _service->unregister(_func);
    }
private:
    InvokeServiceImpl * _service;
    VoidFunc        _func;
};

std::unique_ptr<IDestructorCallback>
InvokeServiceImpl::registerInvoke(VoidFunc func) {
    std::lock_guard guard(_lock);
    _toWakeup.push_back(func);
    if ( ! _thread) {
        _thread = std::make_unique<std::thread>([this]() { runLoop(); });
    }
    return std::make_unique<Registration>(this, func);
}

void
InvokeServiceImpl::unregister(VoidFunc func) {
    std::lock_guard guard(_lock);
    auto found = std::find_if(_toWakeup.begin(), _toWakeup.end(), [&func](const VoidFunc & a) {
        return func.target<VoidFunc>() == a.target<VoidFunc>();
    });
    assert (found != _toWakeup.end());
    _toWakeup.erase(found);
}

void
InvokeServiceImpl::runLoop() {
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

}

