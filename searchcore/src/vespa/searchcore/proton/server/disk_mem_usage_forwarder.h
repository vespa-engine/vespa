// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_disk_mem_usage_notifier.h"
#include "i_disk_mem_usage_listener.h"
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vector>
#include <mutex>

namespace proton {

/**
 * Forwarder for disk/memory usage state changes. Notification is forwarded
 * as a task run by the supplied executor.
 */
class DiskMemUsageForwarder : public IDiskMemUsageNotifier,
                              public IDiskMemUsageListener
{
    searchcorespi::index::IThreadService &_executor;
    std::vector<IDiskMemUsageListener *> _listeners;
    std::mutex        _lock;
    DiskMemUsageState _state;
    void forward(DiskMemUsageState state);
public:
    DiskMemUsageForwarder(searchcorespi::index::IThreadService &executor);
    ~DiskMemUsageForwarder() override;
    void addDiskMemUsageListener(IDiskMemUsageListener *listener) override;
    void removeDiskMemUsageListener(IDiskMemUsageListener *listener) override;
    void notifyDiskMemUsage(DiskMemUsageState state) override;
};

} // namespace proton
