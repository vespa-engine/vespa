// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include "job.h"
#include "thread_test_base.hpp"
#include <cstdlib>
#include <chrono>

#define MAX_THREADS 7

using namespace std::chrono;
using namespace std::chrono_literals;

class ThreadTest : public ThreadTestBase
{
   int Main () override;

   void TooManyThreadsTest ()
   {
      TestHeader("Too Many Threads Test");
      static constexpr size_t message_size = 100;

      FastOS_ThreadPool *pool = new FastOS_ThreadPool(MAX_THREADS);

      if (Progress(pool != nullptr, "Allocating ThreadPool")) {
         int i;
         Job jobs[MAX_THREADS+1];

         for (i=0; i<MAX_THREADS+1; i++) {
            jobs[i].code = WAIT_FOR_BREAK_FLAG;
            jobs[i].message = static_cast<char *>(malloc(message_size));
            if (jobs[i].message == nullptr) {
                abort(); // GCC may infer that a potentially null ptr is passed to sprintf
            }
            snprintf(jobs[i].message, message_size, "Thread %d invocation", i+1);
         }

         for (i=0; i<MAX_THREADS+1; i++) {
            if (i==MAX_THREADS) {
                while (pool->GetNumInactiveThreads() > 0);
                jobs[MAX_THREADS].code = PRINT_MESSAGE_AND_WAIT3MSEC;
               bool rc = (nullptr == pool->NewThread(this, static_cast<void *>(&jobs[MAX_THREADS])));
               Progress(rc, "Creating too many threads should fail.");
            } else {
               jobs[i].ownThread = pool->NewThread(this, static_cast<void *>(&jobs[i]));
               Progress(jobs[i].ownThread != nullptr, "Creating Thread");
            }
         }
          for (i=0; i<MAX_THREADS; i++) {
              jobs[i].ownThread->SetBreakFlag();
          }
          
         Progress(true, "Closing threadpool...");
         pool->Close();

         Progress(true, "Deleting threadpool...");
         delete(pool);
      }
      PrintSeparator();
   }

   void CreateSingleThreadAndJoin ()
   {
      TestHeader("Create Single Thread And Join Test");

      FastOS_ThreadPool *pool = new FastOS_ThreadPool;

      if (Progress(pool != nullptr, "Allocating ThreadPool")) {
         Job job;

         job.code = NOP;
         job.result = -1;

         bool rc = (nullptr != pool->NewThread(this, &job));
         Progress(rc, "Creating Thread");

         WaitForThreadsToFinish(&job, 1);
      }

      Progress(true, "Closing threadpool...");
      pool->Close();

      Progress(true, "Deleting threadpool...");
      delete(pool);
      PrintSeparator();
   }

  void ThreadCreatePerformance (bool silent, int count, int outercount) {
    int i;
    int j;
    bool rc;
    int threadsok;

    if (!silent)
      TestHeader("Thread Create Performance");

    FastOS_ThreadPool *pool = new FastOS_ThreadPool;

    if (!silent)
      Progress(pool != nullptr, "Allocating ThreadPool");
    if (pool != nullptr) {
      Job *jobs = new Job[count];

      threadsok = 0;
      steady_clock::time_point start = steady_clock::now();
      for (i = 0; i < count; i++) {
        jobs[i].code = SILENTNOP;
        jobs[i].ownThread = pool->NewThread(this, &jobs[i]);
        rc = (jobs[i].ownThread != nullptr);
        if (rc)
          threadsok++;
      }

      for (j = 0; j < outercount; j++) {
        for (i = 0; i < count; i++) {
          if (jobs[i].ownThread != nullptr)
            jobs[i].ownThread->Join();
          jobs[i].ownThread = pool->NewThread(this, &jobs[i]);
          rc = (jobs[i].ownThread != nullptr);
          if (rc)
            threadsok++;
        }
      }
      for (i = 0; i < count; i++) {
        if (jobs[i].ownThread != nullptr)
          jobs[i].ownThread->Join();
      }
      nanoseconds used = steady_clock::now() - start;

      if (!silent) {
          double timeused = used.count() / 1000000000.0;

          Progress(true, "Used time: %2.3f", timeused);
          ProgressFloat(true, "Threads/s: %6.1f",
                      static_cast<float>(static_cast<double>(threadsok) / timeused));
      }
      if (threadsok != ((outercount + 1) * count))
        Progress(false, "Only started %d of %d threads", threadsok,
                 (outercount + 1) * count);

      if (!silent)
        Progress(true, "Closing threadpool...");
      pool->Close();
      delete [] jobs;

      if (!silent)
        Progress(true, "Deleting threadpool...");
      delete(pool);
      if (!silent)
        PrintSeparator();
    }
  }

  void ClosePoolStability(void) {
    int i;
    TestHeader("ThreadPool close stability test");
    for (i = 0; i < 1000; i++) {
      // Progress(true, "Creating pool iteration %d", i + 1);
      ThreadCreatePerformance(true, 2, 1);
    }
    PrintSeparator();
  }



   void ClosePoolTest ()
   {
      TestHeader("Close Pool Test");

      FastOS_ThreadPool pool;
      const int closePoolThreads=9;
      Job jobs[closePoolThreads];

      number = 0;

      for(int i=0; i<closePoolThreads; i++) {
         jobs[i].code = INCREASE_NUMBER;

         bool rc = (nullptr != pool.NewThread(this, static_cast<void *>(&jobs[i])));
         Progress(rc, "Creating Thread %d", i+1);
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");
      PrintSeparator();
   }

   void BreakFlagTest () {
      TestHeader("BreakFlag Test");

      FastOS_ThreadPool pool;

      const int breakFlagThreads=4;

      Job jobs[breakFlagThreads];

      for (int i=0; i<breakFlagThreads; i++) {
         jobs[i].code = WAIT_FOR_BREAK_FLAG;

         bool rc = (nullptr != pool.NewThread(this, static_cast<void *>(&jobs[i])));
         Progress(rc, "Creating Thread %d", i+1);
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");
      PrintSeparator();
   }

   void ThreadIdTest () {
      constexpr int numThreads = 5;

      TestHeader ("Thread Id Test");

      FastOS_ThreadPool pool;
      Job jobs[numThreads];
      std::mutex slowStartMutex;

      slowStartMutex.lock();    // Halt all threads until we want them to run

      for (int i=0; i<numThreads; i++) {
         jobs[i].code = TEST_ID;
         jobs[i].result = -1;
         jobs[i]._threadId = 0;
         jobs[i].mutex = &slowStartMutex;
         jobs[i].ownThread = pool.NewThread(this, static_cast<void *>(&jobs[i]));
         bool rc=(jobs[i].ownThread != nullptr);
         if (rc) {
             jobs[i]._threadId = jobs[i].ownThread->GetThreadId();
         }
         Progress(rc, "CreatingThread %d id:%lu", i+1, (unsigned long)(jobs[i]._threadId));

         for (int j=0; j<i; j++) {
            if (jobs[j]._threadId == jobs[i]._threadId) {
               Progress(false, "Two different threads received the same thread id (%lu)",
                        (unsigned long)(jobs[i]._threadId));
            }
         }
      }

      slowStartMutex.unlock();    // Allow threads to run

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");

      for (int i=0; i<numThreads; i++) {
         Progress(jobs[i].result == 1,
                  "Thread %lu: ID comparison (current vs stored)",
                  (unsigned long)(jobs[i]._threadId));
      }

      PrintSeparator();
   }

};

int ThreadTest::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   ThreadIdTest();
   CreateSingleThreadAndJoin();
   TooManyThreadsTest();
   ClosePoolTest();
   BreakFlagTest();
   CreateSingleThreadAndJoin();
   ThreadCreatePerformance(false, 50, 10);
   ClosePoolStability();
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }

   printf("END OF TEST (%s)\n", _argv[0]);
   return allWasOk() ? 0 : 1;
}

int main (int argc, char **argv)
{
   ThreadTest app;
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
