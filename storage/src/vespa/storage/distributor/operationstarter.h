// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once


namespace storage {
namespace distributor {

class Operation;

class OperationStarter
{
public:
    typedef uint8_t Priority;

    virtual ~OperationStarter() {}

    virtual bool start(const std::shared_ptr<Operation>& operation,
                       Priority priority) = 0;
};

}
}

