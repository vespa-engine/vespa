// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
namespace proton {

class IPendingLidTracker;

/**
 * Interface for anyone that needs to commit.
 **/
class ICommitable {
public:
    virtual void commit() = 0;
    virtual void commitAndWait() = 0;
    virtual void commitAndWait(IPendingLidTracker &uncommittedLidTracker, uint32_t lid) = 0;
    virtual void commitAndWait(IPendingLidTracker &uncommittedLidTracker, const std::vector<uint32_t> & lid) = 0;
protected:
    virtual ~ICommitable() = default;
};

}
