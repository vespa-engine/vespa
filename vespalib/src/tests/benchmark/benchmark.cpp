// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include "testbase.h"

#include <vespa/log/log.h>
LOG_SETUP("benchmark_test");

using namespace vespalib;

TEST_MAIN() {
    if (argc > 1) {
        size_t concurrency(1);
        size_t numRuns(1000);
        if (argc > 2) {
            numRuns = strtoul(argv[2], NULL, 0);
            if (argc > 3) {
                concurrency = strtoul(argv[3], NULL, 0);
            }
        }
        Benchmark::run(argv[1], numRuns, concurrency);
    }
}
