// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/task.h>
#include <vespa/vespalib/gtest/gtest.h>

class MyTask : public FNET_Task {
public:
    bool _done;

    MyTask(FNET_Scheduler& scheduler) : FNET_Task(&scheduler), _done(false) {}

    bool done() const { return _done; }
    void PerformTask() override { _done = true; }
};

TEST(SlowEventLoopTest, slow_event_loop) {
    vespalib::steady_time t(vespalib::duration::zero());

    FNET_Scheduler scheduler(&t);
    MyTask         task(scheduler);
    MyTask         task2(scheduler);

    scheduler.CheckTasks();
    t += 10s;
    task.Schedule(5.0);

    uint32_t cnt = 0;
    for (;;) {
        scheduler.CheckTasks();
        if (task.done()) {
            break;
        }
        ++cnt;
        t += 1ms;
    }

    if (bool ok = cnt > 4700 && cnt < 4800; !ok) {
        EXPECT_TRUE(ok);
        fprintf(stderr, "cnt=%d\n", cnt);
    }

    scheduler.CheckTasks();
    t += 10s;
    task2.Schedule(5.0);

    uint32_t cnt2 = 0;
    for (;;) {
        scheduler.CheckTasks();
        if (task2.done()) {
            break;
        }
        ++cnt2;
        t += 10s;
    }

    if (bool ok = cnt2 > 15 && cnt2 < 25; !ok) {
        EXPECT_TRUE(ok);
        fprintf(stderr, "cnt2=%d\n", cnt2);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
