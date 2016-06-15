// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton
{

class IClusterStateChangedHandler;

/**
 * Interface used to request notification when cluster state has changed.
 */
class IClusterStateChangedNotifier
{
public:
    virtual ~IClusterStateChangedNotifier() { }

    virtual void
    addClusterStateChangedHandler(IClusterStateChangedHandler *handler) = 0;

    virtual void
    removeClusterStateChangedHandler(IClusterStateChangedHandler *handler) = 0;
};

} // namespace proton
