// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_forwarder.h"
#include <vespa/searchlib/common/lambdatask.h>
#include <cassert>

using search::makeLambdaTask;

namespace proton {

DiskMemUsageForwarder::DiskMemUsageForwarder(searchcorespi::index::IThreadService &executor)
    : IDiskMemUsageNotifier(),
      IDiskMemUsageListener(),
      _executor(executor),
      _listeners(),
      _state()
{
}

DiskMemUsageForwarder::~DiskMemUsageForwarder()
{
}

void
DiskMemUsageForwarder::addDiskMemUsageListener(IDiskMemUsageListener *listener)
{
    assert(_executor.isCurrentThread());
    _listeners.push_back(listener);
    listener->notifyDiskMemUsage(_state);
}

void
DiskMemUsageForwarder::removeDiskMemUsageListener(IDiskMemUsageListener *listener)
{
    assert(_executor.isCurrentThread());
    for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
        if (*itr == listener) {
            _listeners.erase(itr);
            break;
        }
    }
}

void
DiskMemUsageForwarder::notifyDiskMemUsage(DiskMemUsageState state)
{
    _executor.execute(makeLambdaTask([=]() { forward(state); }));
}


void
DiskMemUsageForwarder::forward(DiskMemUsageState state)
{
    if (_state != state) {
        _state = state;
        for (const auto &listener : _listeners) {
            listener->notifyDiskMemUsage(state);
        }
    }
}

} // namespace proton
