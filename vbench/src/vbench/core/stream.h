// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/output.h>
#include "taintable.h"
#include <memory>

namespace vbench {

using Input = vespalib::Input;
using Output = vespalib::Output;

/**
 * A Stream is an abstract taintable entity that can act as both input
 * and output.
 **/
struct Stream : public Input,
                public Output,
                public Taintable
{
    ~Stream() { }
    using UP = std::unique_ptr<Stream>;
    virtual bool eof() const = 0;
};

} // namespace vbench

