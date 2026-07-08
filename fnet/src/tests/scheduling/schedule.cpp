// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/task.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <cassert>

using vespalib::steady_clock;
using vespalib::steady_time;
using ms_double = std::chrono::duration<double, std::milli>;

steady_time     _time;
FNET_Scheduler* _scheduler;

template <class Rep, class Period> int as_ms(std::chrono::duration<Rep, Period> duration) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(duration).count();
}

template <class Clock, class Duration> int as_ms(std::chrono::time_point<Clock, Duration> time) {
    return as_ms(time.time_since_epoch());
}

class MyTask : public FNET_Task {
public:
    steady_time _time;
    int         _target;
    bool        _done;

    explicit MyTask(int target) : FNET_Task(::_scheduler), _time(), _target(target), _done(false) {}

    int GetTarget() const { return _target; }

    bool Check() const {
        int a = _target;
        int b = as_ms(_time);

        if (!_done)
            return false;

        if (b < a)
            return false;

        if ((b - a) > (3 * vespalib::count_ms(FNET_Scheduler::tick_ms)))
            return false;

        return true;
    }

    void PerformTask() override {
        _time = ::_time;
        _done = true;
    }
};

class RealTimeTask : public FNET_Task {
public:
    uint32_t _cnt;

    RealTimeTask() : FNET_Task(::_scheduler), _cnt(0) {}

    uint32_t GetCnt() { return _cnt; }

    void PerformTask() override {
        _cnt++;
        ScheduleNow(); // re-schedule as fast as possible
    }
};

TEST(ScheduleTest, schedule) {
    _time = steady_time(vespalib::duration::zero());
    _scheduler = new FNET_Scheduler(&_time);

    RealTimeTask rt_task1;
    RealTimeTask rt_task2;
    RealTimeTask rt_task3;
    rt_task1.ScheduleNow();
    rt_task2.ScheduleNow();
    rt_task3.ScheduleNow();

    uint32_t taskCnt = 1000000;
    MyTask** tasks = new MyTask*[taskCnt];
    assert(tasks != nullptr);
    for (uint32_t i = 0; i < taskCnt; i++) {
        tasks[i] = new MyTask(rand() & 131071);
        assert(tasks[i] != nullptr);
    }

    steady_time start;
    ms_double   ms;

    start = steady_clock::now();
    for (uint32_t j = 0; j < taskCnt; j++) {
        tasks[j]->Schedule(tasks[j]->GetTarget() / 1000.0);
    }
    ms = (steady_clock::now() - start);
    double scheduleTime = ms.count() / (double)taskCnt;
    fprintf(stderr, "scheduling cost: %1.2f microseconds\n", scheduleTime * 1000.0);

    start = steady_clock::now();
    uint32_t tickCnt = 0;
    while (as_ms(_time) < 135000.0) {
        _time += FNET_Scheduler::tick_ms;
        _scheduler->CheckTasks();
        tickCnt++;
    }
    ms = (steady_clock::now() - start);
    fprintf(stderr, "3 RT tasks + %d one-shot tasks over 135s\n", taskCnt);
    fprintf(stderr, "%1.2f seconds actual run time\n", ms.count() / 1000.0);
    fprintf(stderr, "%1.2f tasks per simulated second\n", (double)taskCnt / (double)135);
    fprintf(stderr, "%d ticks\n", tickCnt);
    fprintf(stderr, "%1.2f %% simulated CPU usage\n", 100 * (ms.count() / 135000.0));
    fprintf(stderr, "%1.2f microseconds per performed task\n", 1000.0 * (ms.count() / (taskCnt + tickCnt * 3.0)));

    for (uint32_t k = 0; k < taskCnt; k++) {
        EXPECT_TRUE(tasks[k]->Check());
    }
    EXPECT_TRUE(rt_task1.GetCnt() == tickCnt);
    EXPECT_TRUE(rt_task2.GetCnt() == tickCnt);
    EXPECT_TRUE(rt_task3.GetCnt() == tickCnt);

    for (uint32_t l = 0; l < taskCnt; l++) {
        delete tasks[l];
    }
    rt_task1.Kill();
    rt_task2.Kill();
    rt_task3.Kill();
    delete[] tasks;
    delete _scheduler;

    { // trigger warning from scheduler destructor

        FNET_Scheduler* s = new FNET_Scheduler();

        FNET_Task t1(s);
        FNET_Task t2(s);
        FNET_Task t3(s);
        FNET_Task t4(s);
        FNET_Task t5(s);

        t1.ScheduleNow();
        t2.Schedule(5.0);
        t3.Schedule(5.0);
        t4.Schedule(10.0);
        t5.Schedule(15.0);

        delete s;
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
