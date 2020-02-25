// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <atomic>

using search::SequencedTaskExecutor;
using ExecutorId = search::ISequencedTaskExecutor::ExecutorId;

int main(int argc, char *argv[]) {
    unsigned long numTasks = 1000000;
    unsigned numThreads = 4;
    std::atomic<long> counter(0);
    if (argc > 1)
        numTasks = atol(argv[1]);
    if (argc > 2)
        numThreads = atoi(argv[2]);

    SequencedTaskExecutor executor(numThreads);
    for (unsigned long tid(0); tid < numTasks; tid++) {
        executor.executeTask(ExecutorId(tid%numThreads), vespalib::makeLambdaTask([&counter] { counter++; }));
    }
    return 0;
}
