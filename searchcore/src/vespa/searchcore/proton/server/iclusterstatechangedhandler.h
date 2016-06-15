// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ibucketstatecalculator.h"

namespace proton
{

/**
 * Interface used to notify when cluster state has changed.
 */
class IClusterStateChangedHandler
{
public:
    virtual ~IClusterStateChangedHandler() { }

    virtual void
    notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc) = 0;
};

} // namespace proton
