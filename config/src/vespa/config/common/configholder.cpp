// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configholder.h"

namespace config {

ConfigHolder::ConfigHolder()
    : _lock(),
      _cond(),
      _current()
{
}

ConfigHolder::~ConfigHolder() = default;

std::unique_ptr<ConfigUpdate>
ConfigHolder::provide()
{
    std::lock_guard guard(_lock);
    return std::move(_current);
}

void
ConfigHolder::handle(std::unique_ptr<ConfigUpdate> update)
{
    std::lock_guard guard(_lock);
    if (_current) {
        update->merge(*_current);
    }
    _current = std::move(update);
    _cond.notify_all();
}

bool
ConfigHolder::wait(vespalib::duration timeout)
{
    std::unique_lock guard(_lock);
    return static_cast<bool>(_current) || (_cond.wait_for(guard, timeout) == std::cv_status::no_timeout);
}

bool
ConfigHolder::poll()
{
    std::lock_guard guard(_lock);
    return static_cast<bool>(_current);
}

void
ConfigHolder::interrupt()
{
    _cond.notify_all();
}

} // namespace config
