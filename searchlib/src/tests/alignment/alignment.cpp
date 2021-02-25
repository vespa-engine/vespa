// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("alignment_test");

#include <sys/resource.h>
#include <sys/time.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/size_literals.h>

struct Timer {
    rusage usage;
    void start() {
        getrusage(RUSAGE_SELF, &usage);
    }
    double stop() {
        rusage tmp;
        getrusage(RUSAGE_SELF, &tmp);
        double startMs = (((double)usage.ru_utime.tv_sec) * 1000.0)
                         + (((double)usage.ru_utime.tv_usec) / 1000.0);
        double stopMs = (((double)tmp.ru_utime.tv_sec) * 1000.0)
                        + (((double)tmp.ru_utime.tv_usec) / 1000.0);
        return (stopMs - startMs);
    }
};

TEST_SETUP(Test);

double
timeAccess(void *bufp, uint32_t len, double &sum)
{
    double *buf = (double *)bufp;
    Timer timer;
    timer.start();
    for(uint32_t i = 0; i < 512_Ki; ++i) {
        for (uint32_t j = 0; j < len; ++j) {
            sum += buf[j];
        }
    }
    double ret = timer.stop();
    return ret;
}

int
Test::Main()
{
    TEST_INIT("alignment_test");

    uint32_t buf[129];
    for (uint32_t i = 0; i < 129; ++i) {
        buf[i] = i;
    }

    uintptr_t ptr = reinterpret_cast<uintptr_t>(&buf[0]);
    bool aligned = (ptr % sizeof(double) == 0);

    double foo = 0, bar = 0;
    printf(aligned ? "ALIGNED\n" : "UNALIGNED\n");
    printf("warmup time = %.2f\n", timeAccess(reinterpret_cast<void*>(&buf[0]), 64, foo));
    printf("real   time = %.2f\n", timeAccess(reinterpret_cast<void*>(&buf[0]), 64, bar));
    EXPECT_EQUAL(foo, bar);

    printf(!aligned ? "ALIGNED\n" : "UNALIGNED\n");
    printf("warmup time = %.2f\n", timeAccess(reinterpret_cast<void*>(&buf[1]), 64, foo));
    printf("real   time = %.2f\n", timeAccess(reinterpret_cast<void*>(&buf[1]), 64, bar));
    EXPECT_EQUAL(foo, bar);

    TEST_DONE();
}
