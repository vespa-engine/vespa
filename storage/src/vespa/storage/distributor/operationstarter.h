// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace storage::distributor {

class Operation;

class OperationStarter
{
public:
    using Priority = uint8_t;
    virtual ~OperationStarter() = default;
    virtual bool start(const std::shared_ptr<Operation>& operation, Priority priority) = 0;
};

}
