// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class IClusterStateChangedHandler;

/**
 * Interface used to request notification when cluster state has changed.
 */
class IClusterStateChangedNotifier
{
public:
    virtual ~IClusterStateChangedNotifier() = default;

    virtual void addClusterStateChangedHandler(IClusterStateChangedHandler *handler) = 0;
    virtual void removeClusterStateChangedHandler(IClusterStateChangedHandler *handler) = 0;
};

}
