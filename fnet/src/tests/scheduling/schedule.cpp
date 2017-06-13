// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/fnet.h>

FastOS_Time         _time;
FNET_Scheduler     *_scheduler;


class MyTask : public FNET_Task
{
public:
  FastOS_Time _time;
  int         _target;
  bool        _done;

  MyTask(int target)
    : FNET_Task(::_scheduler),
      _time(),
      _target(target),
      _done(false) {}

  int GetTarget() const { return _target; }

  bool Check() const
  {
    int a = _target;
    int b = (int)_time.MilliSecs();

    if (!_done)
      return false;

    if (b < a)
      return false;

    if ((b - a) > (2 * FNET_Scheduler::SLOT_TICK))
      return false;

    return true;
  }

  void PerformTask() override
  {
    _time = ::_time;
    _done = true;
  }
};


class RealTimeTask : public FNET_Task
{
public:
  uint32_t _cnt;

  RealTimeTask() : FNET_Task(::_scheduler), _cnt(0)
  {
  }

  uint32_t GetCnt() { return _cnt; }

  void PerformTask() override
  {
    _cnt++;
    ScheduleNow(); // re-schedule as fast as possible
  }
};


TEST("schedule") {
  _time.SetMilliSecs(0);
  _scheduler = new FNET_Scheduler(&_time, &_time);

  RealTimeTask rt_task1;
  RealTimeTask rt_task2;
  RealTimeTask rt_task3;
  rt_task1.ScheduleNow();
  rt_task2.ScheduleNow();
  rt_task3.ScheduleNow();

  uint32_t   taskCnt = 1000000;
  MyTask   **tasks   = new MyTask*[taskCnt];
  assert(tasks != nullptr);
  for (uint32_t i = 0; i < taskCnt; i++) {
    tasks[i] = new MyTask(rand() & 131071);
    assert(tasks[i] != nullptr);
  }

  FastOS_Time start;
  FastOS_Time stop;

  start.SetNow();
  for (uint32_t j = 0; j < taskCnt; j++) {
    tasks[j]->Schedule(tasks[j]->GetTarget() / 1000.0);
  }
  stop.SetNow();
  stop -= start;
  double scheduleTime = stop.MilliSecs() / (double)taskCnt;
  fprintf(stderr, "scheduling cost: %1.2f microseconds\n", scheduleTime * 1000.0);

  start.SetNow();
  uint32_t tickCnt = 0;
  while (_time.MilliSecs() < 135000.0) {
    _time.AddMilliSecs(FNET_Scheduler::SLOT_TICK);
    _scheduler->CheckTasks();
    tickCnt++;
  }
  stop.SetNow();
  stop -= start;
  double runTime = stop.MilliSecs();
  fprintf(stderr, "3 RT tasks + %d one-shot tasks over 135s\n", taskCnt);
  fprintf(stderr, "%1.2f seconds actual run time\n", runTime / 1000.0);
  fprintf(stderr, "%1.2f tasks per simulated second\n", (double)taskCnt / (double)135);
  fprintf(stderr, "%d ticks\n", tickCnt);
  fprintf(stderr, "%1.2f %% simulated CPU usage\n", 100 * (runTime / 135000.0));
  fprintf(stderr, "%1.2f microseconds per performed task\n",
      1000.0 * (runTime / (taskCnt + tickCnt * 3.0)));

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
  delete [] tasks;
  delete _scheduler;

  { // trigger warning from scheduler destructor

      FNET_Scheduler *s = new FNET_Scheduler();

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

TEST_MAIN() { TEST_RUN_ALL(); }
