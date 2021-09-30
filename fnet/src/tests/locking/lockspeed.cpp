// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include "dummy.h"
#include <chrono>

class SpinLock {
private:
    std::atomic_flag _lock;
public:
    SpinLock() : _lock(false) {}
    void lock() {
        while (_lock.test_and_set(std::memory_order_acquire)) {
            // spin
        }
    }
    void unlock() { _lock.clear(std::memory_order_release); }
};

TEST("lock speed") {
  using clock = std::chrono::steady_clock;
  using ms_double = std::chrono::duration<double, std::milli>;

  clock::time_point start;
  ms_double         ms;

  DummyLock        dummy;
  std::mutex        lock;
  SpinLock          spin;
  double       dummyTime;
  double      actualTime;
  double        overhead;
  double   spin_overhead;
  double     spin_factor;
  uint32_t             i;

  start = clock::now();
  for (i = 0; i < 1000000; i++) {
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
    dummy.Lock();
    dummy.Unlock();
  }
  ms = (clock::now() - start);
  dummyTime = ms.count();

  fprintf(stderr,
          "10M dummy lock/unlock: %f ms (%1.2f/ms)\n",
          dummyTime, 10000000.0 / dummyTime);

  start = clock::now();
  for (i = 0; i < 1000000; i++) {
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
    lock.lock();
    lock.unlock();
  }
  ms = (clock::now() - start);
  actualTime = ms.count();

  fprintf(stderr,
          "10M actual lock/unlock: %f ms (%1.2f/ms)\n",
          ms.count(), 10000000.0 / ms.count());

  overhead = (actualTime - dummyTime) / 10000.0;

  start = clock::now();
  for (i = 0; i < 1000000; i++) {
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
    spin.lock();
    spin.unlock();
  }
  ms = (clock::now() - start);
  actualTime = ms.count();

  fprintf(stderr,
          "10M actual (spin) lock/unlock: %f ms (%1.2f/ms)\n",
          ms.count(), 10000000.0 / ms.count());

  spin_overhead = (actualTime - dummyTime) / 10000.0;

  spin_factor = overhead / spin_overhead;

  fprintf(stderr,
          "approx overhead per lock/unlock: %f microseconds\n",
          overhead);

  fprintf(stderr,
          "approx overhead per lock/unlock (spin): %f microseconds\n",
          spin_overhead);

  fprintf(stderr,
          "spinlocks are %f times faster\n",
          spin_factor);

  //---------------------------------------------------------------------------

  start = clock::now();
  for (i = 0; i < 1000000; i++) {
      [[maybe_unused]] std::mutex lock0;
      [[maybe_unused]] std::mutex lock1;
      [[maybe_unused]] std::mutex lock2;
      [[maybe_unused]] std::mutex lock3;
      [[maybe_unused]] std::mutex lock4;
      [[maybe_unused]] std::mutex lock5;
      [[maybe_unused]] std::mutex lock6;
      [[maybe_unused]] std::mutex lock7;
      [[maybe_unused]] std::mutex lock8;
      [[maybe_unused]] std::mutex lock9;
  }
  ms = (clock::now() - start);
  fprintf(stderr, "10M mutex create/destroy %f ms (%1.2f/ms)\n",
      ms.count(), 10000000.0 / ms.count());

  //---------------------------------------------------------------------------

  start = clock::now();
  for (i = 0; i < 1000000; i++) {
      std::condition_variable cond0;
      std::condition_variable cond1;
      std::condition_variable cond2;
      std::condition_variable cond3;
      std::condition_variable cond4;
      std::condition_variable cond5;
      std::condition_variable cond6;
      std::condition_variable cond7;
      std::condition_variable cond8;
      std::condition_variable cond9;
  }
  ms = (clock::now() - start);
  fprintf(stderr, "10M cond create/destroy %f ms (%1.2f/ms)\n",
      ms.count(), 10000000.0 / ms.count());

  //---------------------------------------------------------------------------

  start = clock::now();
  for (i = 0; i < 1000000; i++) {
      DummyObj dummy0;
      DummyObj dummy1;
      DummyObj dummy2;
      DummyObj dummy3;
      DummyObj dummy4;
      DummyObj dummy5;
      DummyObj dummy6;
      DummyObj dummy7;
      DummyObj dummy8;
      DummyObj dummy9;
  }
  ms = (clock::now() - start);
  fprintf(stderr, "10M dummy create/destroy %f ms (%1.2f/ms)\n",
      ms.count(), 10000000.0 / ms.count());

  //---------------------------------------------------------------------------

  start = clock::now();
  for (i = 0; i < 1000000; i++) {
      DummyObj *dummy0 = new DummyObj();
      DummyObj *dummy1 = new DummyObj();
      DummyObj *dummy2 = new DummyObj();
      DummyObj *dummy3 = new DummyObj();
      DummyObj *dummy4 = new DummyObj();
      DummyObj *dummy5 = new DummyObj();
      DummyObj *dummy6 = new DummyObj();
      DummyObj *dummy7 = new DummyObj();
      DummyObj *dummy8 = new DummyObj();
      DummyObj *dummy9 = new DummyObj();
      delete dummy9;
      delete dummy8;
      delete dummy7;
      delete dummy6;
      delete dummy5;
      delete dummy4;
      delete dummy3;
      delete dummy2;
      delete dummy1;
      delete dummy0;
  }
  ms = (clock::now() - start);
  fprintf(stderr, "10M dummy new/delete %f ms (%1.2f/ms)\n",
          ms.count(), 10000000.0 / ms.count());

  //---------------------------------------------------------------------------
}

TEST_MAIN() { TEST_RUN_ALL(); }
