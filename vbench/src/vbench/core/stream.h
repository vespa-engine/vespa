// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "input.h"
#include "output.h"
#include "taintable.h"
#include <memory>

namespace vbench {

/**
 * A Stream is an abstract taintable entity that can act as both input
 * and output.
 **/
struct Stream : public Input,
                public Output,
                public Taintable
{
    typedef std::unique_ptr<Stream> UP;
    virtual bool eof() const = 0;
};

} // namespace vbench

