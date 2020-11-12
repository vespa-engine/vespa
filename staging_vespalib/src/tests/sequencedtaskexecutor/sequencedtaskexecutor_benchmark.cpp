// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/adaptive_sequenced_executor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/time.h>
#include <atomic>
#include <cinttypes>

using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using vespalib::AdaptiveSequencedExecutor;
using ExecutorId = vespalib::ISequencedTaskExecutor::ExecutorId;

size_t do_work(size_t size) {
    size_t ret = 0;
    for (size_t i = 0; i < size; ++i) {
        for (size_t j = 0; j < 128; ++j) {
            ret = (ret + i) * j;
        }
    }
    return ret;
}

struct SimpleParams {
    int argc;
    char **argv;
    int idx;
    SimpleParams(int argc_in, char **argv_in) : argc(argc_in), argv(argv_in), idx(0) {}
    int next(const char *name, int fallback) {
        ++idx;
        int value = 0;
        if (argc > idx) {
            value = atoi(argv[idx]);
        } else {
            value = fallback;
        }
        fprintf(stderr, "param %s: %d\n", name, value);
        return value;
    }
};

VESPA_THREAD_STACK_TAG(sequenced_executor)

int main(int argc, char **argv) {
    SimpleParams params(argc, argv);
    bool use_adaptive_executor = params.next("use_adaptive_executor", 0);
    bool optimize_for_throughput = params.next("optimize_for_throughput", 0);
    size_t num_tasks = params.next("num_tasks", 1000000);
    size_t num_strands = params.next("num_strands", 4);
    size_t task_limit = params.next("task_limit", 1000);
    size_t num_threads = params.next("num_threads", num_strands);
    size_t max_waiting = params.next("max_waiting", optimize_for_throughput ? 32 : 0);
    size_t work_size = params.next("work_size", 0);
    std::atomic<long> counter(0);
    std::unique_ptr<ISequencedTaskExecutor> executor;
    if (use_adaptive_executor) {
        executor = std::make_unique<AdaptiveSequencedExecutor>(num_strands, num_threads, max_waiting, task_limit);
    } else {
        auto optimize = optimize_for_throughput
                        ? vespalib::Executor::OptimizeFor::THROUGHPUT
                        : vespalib::Executor::OptimizeFor::LATENCY;
        executor = SequencedTaskExecutor::create(sequenced_executor, num_strands, task_limit, optimize);
    }
    vespalib::Timer timer;
    for (size_t task_id = 0; task_id < num_tasks; ++task_id) {
        executor->executeTask(ExecutorId(task_id % num_strands),
                              vespalib::makeLambdaTask([&counter,work_size] { (void) do_work(work_size); counter++; }));
    }
    executor.reset();
    fprintf(stderr, "\ntotal time: %" PRId64 " ms\n", vespalib::count_ms(timer.elapsed()));
    return (size_t(counter) == num_tasks) ? 0 : 1;
}
