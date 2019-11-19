// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configholder.h"

namespace config {

ConfigHolder::ConfigHolder()
    : _monitor(),
      _current()
{
}

ConfigHolder::~ConfigHolder() = default;

ConfigUpdate::UP
ConfigHolder::provide()
{
    vespalib::MonitorGuard guard(_monitor);
    return std::move(_current);
}

void
ConfigHolder::handle(ConfigUpdate::UP update)
{
    vespalib::MonitorGuard guard(_monitor);
    if (_current) {
        update->merge(*_current);
    }
    _current = std::move(update);
    guard.broadcast();
}

bool
ConfigHolder::wait(milliseconds timeoutInMillis)
{
    vespalib::MonitorGuard guard(_monitor);
    return static_cast<bool>(_current) || guard.wait(timeoutInMillis);
}

bool
ConfigHolder::poll()
{
    vespalib::MonitorGuard guard(_monitor);
    return static_cast<bool>(_current);
}

void
ConfigHolder::interrupt()
{
    vespalib::MonitorGuard guard(_monitor);
    guard.broadcast();
}

} // namespace config
