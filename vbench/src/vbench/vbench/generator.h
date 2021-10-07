// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vbench/core/taintable.h>
#include <vespa/vespalib/util/runnable.h>
#include <memory>

namespace vbench {

struct Generator : public vespalib::Runnable,
                   public Taintable
{
    typedef std::unique_ptr<Generator> UP;
    virtual void abort() = 0;
    virtual ~Generator() {}
};

} // namespace vbench

