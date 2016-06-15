// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <memory>
#include <vespa/vespalib/util/runnable.h>
#include <vbench/core/taintable.h>

namespace vbench {

struct Generator : public vespalib::Runnable,
                   public Taintable
{
    typedef std::unique_ptr<Generator> UP;
    virtual void abort() = 0;
    virtual ~Generator() {}
};

} // namespace vbench

