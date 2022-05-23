// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include "testbase.h"

#include <vespa/log/log.h>
LOG_SETUP("benchmark_test");

using namespace vespalib;

TEST_SETUP(Test)

int
Test::Main()
{
    TEST_INIT("benchmark_test");

    if (_argc > 1) {
        size_t concurrency(1);
        size_t numRuns(1000);
        if (_argc > 2) {
            numRuns = strtoul(_argv[2], NULL, 0);
            if (_argc > 3) {
                concurrency = strtoul(_argv[3], NULL, 0);
            }
        }
        Benchmark::run(_argv[1], numRuns, concurrency);
    }

    TEST_DONE();
}
