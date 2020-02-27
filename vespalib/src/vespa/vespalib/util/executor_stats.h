// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * Struct representing stats for an executor.
 **/
struct ExecutorStats {
    size_t maxPendingTasks;
    size_t acceptedTasks;
    size_t rejectedTasks;
    ExecutorStats() : ExecutorStats(0, 0, 0) {}
    ExecutorStats(size_t maxPending, size_t accepted, size_t rejected)
        : maxPendingTasks(maxPending), acceptedTasks(accepted), rejectedTasks(rejected)
    {}
    ExecutorStats & operator += (const ExecutorStats & rhs) {
        maxPendingTasks += rhs.maxPendingTasks;
        acceptedTasks += rhs.acceptedTasks;
        rejectedTasks += rhs.rejectedTasks;
        return *this;
    }
};

}

