// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/i_disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/server/i_disk_mem_usage_listener.h>

namespace proton
{

namespace test
{

/**
 * Test notifier for disk/mem usage.
 */
class DiskMemUsageNotifier : public IDiskMemUsageNotifier
{
    std::vector<IDiskMemUsageListener *> _listeners;
    DiskMemUsageState _state;
public:
    DiskMemUsageNotifier(DiskMemUsageState state)
        : IDiskMemUsageNotifier(),
          _listeners(),
          _state(state)
    {
    }
    DiskMemUsageNotifier()
        : DiskMemUsageNotifier(DiskMemUsageState())
    {
    }
    virtual ~DiskMemUsageNotifier() { }
    virtual void addDiskMemUsageListener(IDiskMemUsageListener *listener) override {
        _listeners.push_back(listener);
        listener->notifyDiskMemUsage(_state);
    }
    virtual void removeDiskMemUsageListener(IDiskMemUsageListener *listener) override {
        for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
            if (*itr == listener) {
                _listeners.erase(itr);
                break;
            }
        }
    }
    void notify(DiskMemUsageState state) {
        if (_state != state) {
            _state = state;
            for (const auto &listener : _listeners) {
                listener->notifyDiskMemUsage(state);
            }
        }
    }
};

} // namespace proton::test
} // namespace proton
