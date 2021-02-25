// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ibucketstatecalculator.h"
#include <memory>

namespace proton {

/**
 * Interface used to notify when cluster state has changed.
 */
class IClusterStateChangedHandler
{
public:
    virtual ~IClusterStateChangedHandler() = default;

    virtual void notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc) = 0;
};

}
