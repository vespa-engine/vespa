// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class IDiskMemUsageListener;

/**
 * Interface used to request notification when disk/memory usage state
 * has changed.
 */
class IDiskMemUsageNotifier
{
public:
    virtual ~IDiskMemUsageNotifier() = default;
    virtual void addDiskMemUsageListener(IDiskMemUsageListener *listener) = 0;
    virtual void removeDiskMemUsageListener(IDiskMemUsageListener *listener) = 0;
};

} // namespace proton
