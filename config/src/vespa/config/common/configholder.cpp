// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configholder.h"

namespace config {

ConfigHolder::ConfigHolder()
    : _monitor(),
      _current()
{
}

ConfigHolder::~ConfigHolder() {}

ConfigUpdate::UP
ConfigHolder::provide()
{
    vespalib::MonitorGuard guard(_monitor);
    ConfigUpdate::UP ret(new ConfigUpdate(*_current));
    return ret;
}

void
ConfigHolder::handle(ConfigUpdate::UP update)
{
    vespalib::MonitorGuard guard(_monitor);
    _current = std::move(update);
    guard.broadcast();
}

bool
ConfigHolder::wait(uint64_t timeoutInMillis)
{
    vespalib::MonitorGuard guard(_monitor);
    return guard.wait(timeoutInMillis);
}

bool
ConfigHolder::poll()
{
    vespalib::MonitorGuard guard(_monitor);
    return (_current.get() != NULL);
}

void
ConfigHolder::interrupt()
{
    vespalib::MonitorGuard guard(_monitor);
    guard.broadcast();
}

} // namespace config
