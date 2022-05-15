// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/searchlib/common/serialnum.h>

namespace searchcorespi {

class FlushTask : public vespalib::Executor::Task
{
public:
    typedef std::unique_ptr<FlushTask> UP;
    
    virtual search::SerialNum getFlushSerial() const = 0;
};

} // namespace searchcorespi

