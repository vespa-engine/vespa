// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/fnet.h>


class DummyTime
{
public:
  void SetNow() {}
};


TEST("time speed") {
  FastOS_Time      start;
  FastOS_Time       stop;
  DummyTime        dummy;
  FastOS_Time        now;
  double       dummyTime;
  double      actualTime;
  double        overhead;
  uint32_t             i;

  start.SetNow();
  for (i = 0; i < 100000; i++) {
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
    dummy.SetNow();
  }
  stop.SetNow();
  stop -= start;
  dummyTime = stop.MilliSecs();

  fprintf(stderr, "1M dummy SetNow: %f ms (%1.2f/ms)\n",
      dummyTime, 1000000.0 / dummyTime);

  start.SetNow();
  for (i = 0; i < 100000; i++) {
    now.SetNow();
    now.SetNow();
    now.SetNow();
    now.SetNow();
    now.SetNow();
    now.SetNow();
    now.SetNow();
    now.SetNow();
    now.SetNow();
    now.SetNow();
  }
  stop.SetNow();
  stop -= start;
  actualTime = stop.MilliSecs();

  fprintf(stderr, "1M actual SetNow: %f ms (%1.2f/ms)\n",
      stop.MilliSecs(), 1000000.0 / stop.MilliSecs());

  overhead = (actualTime - dummyTime) / 1000.0;

  fprintf(stderr, "approx overhead per SetNow: %f microseconds\n", overhead);
}

TEST_MAIN() { TEST_RUN_ALL(); }
