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
    ExecutorStats() : maxPendingTasks(0), acceptedTasks(0), rejectedTasks(0) {}
};

}

