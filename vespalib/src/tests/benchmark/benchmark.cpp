// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "testbase.h"

#include <vespa/log/log.h>
LOG_SETUP("benchmark_test");

using namespace vespalib;

int main(int argc, char **argv) {
    if (argc > 1) {
        size_t concurrency(1);
        size_t numRuns(1000);
        if (argc > 2) {
            numRuns = strtoul(argv[2], nullptr, 0);
            if (argc > 3) {
                concurrency = strtoul(argv[3], nullptr, 0);
            }
        }
        Benchmark::run(argv[1], numRuns, concurrency);
    }
    return 0;
}
