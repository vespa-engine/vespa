// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class IBucketStateChangedHandler;

/**
 * Interface used to request notification when bucket state has changed.
 */
class IBucketStateChangedNotifier
{
public:
    virtual void addBucketStateChangedHandler(IBucketStateChangedHandler *handler) = 0;
    virtual void removeBucketStateChangedHandler(IBucketStateChangedHandler *handler) = 0;
    
    virtual ~IBucketStateChangedNotifier() = default;
};

}
