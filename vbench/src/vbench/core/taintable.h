// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "taint.h"

namespace vbench {

/**
 * Interface used to report what went wrong. Implementing this
 * interface indicates that something could go wrong.
 **/
struct Taintable
{
    static const Taintable &nil();
    virtual const Taint &tainted() const = 0;
    virtual ~Taintable() {}
};

} // namespace vbench

