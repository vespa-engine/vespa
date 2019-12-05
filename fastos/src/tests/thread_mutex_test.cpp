// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include "job.h"
#include "thread_test_base.hpp"

#define MUTEX_TEST_THREADS 6
#define MAX_THREADS 7

class Thread_Mutex_Test : public ThreadTestBase
{
   int Main () override;

   void MutexTest (bool usingMutex)
   {
      if(usingMutex)
         TestHeader("Mutex Test");
      else
         TestHeader("Not Using Mutex Test");


      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024, MAX_THREADS);

      if(Progress(pool != nullptr, "Allocating ThreadPool"))
      {
         int i;
         Job jobs[MUTEX_TEST_THREADS];
         std::mutex *myMutex=nullptr;

         if(usingMutex) {
             myMutex = new std::mutex;
         }

         for(i=0; i<MUTEX_TEST_THREADS; i++)
         {
            jobs[i].code = INCREASE_NUMBER;
            jobs[i].mutex = myMutex;
         }

         number = 0;

         for(i=0; i<MUTEX_TEST_THREADS; i++)
         {
            bool rc = (nullptr != pool->NewThread(this,
                               static_cast<void *>(&jobs[i])));
            Progress(rc, "Creating Thread with%s mutex", (usingMutex ? "" : "out"));
         };

         WaitForThreadsToFinish(jobs, MUTEX_TEST_THREADS);


         for(i=0; i<MUTEX_TEST_THREADS; i++)
         {
            Progress(true, "Thread returned with resultcode %d", jobs[i].result);
         }

         bool wasOk=true;
         int concurrentHits=0;

         for(i=0; i<MUTEX_TEST_THREADS; i++)
         {
            int result = jobs[i].result;

            if(usingMutex)
            {
               if((result % INCREASE_NUMBER_AMOUNT) != 0)
               {
                  wasOk = false;
                  Progress(false, "Mutex locking did not work (%d).", result);
                  break;
               }
            }
            else
            {
               if((result != 0) &&
                  (result != INCREASE_NUMBER_AMOUNT*MUTEX_TEST_THREADS) &&
                  (result % INCREASE_NUMBER_AMOUNT) == 0)
               {
                  if((++concurrentHits) == 2)
                  {
                     wasOk = false;
                     Progress(false, "Very unlikely that threads are running "
                              "concurrently (%d)", jobs[i].result);
                     break;
                  }
               }
            }
         }

         if(wasOk)
         {
            if(usingMutex)
            {
               Progress(true, "Using the mutex, the returned numbers were alligned.");
            }
            else
            {
               Progress(true, "Returned numbers were not alligned. "
                        "This was the expected result.");
            }
         }

         if(myMutex != nullptr)
            delete(myMutex);

         Progress(true, "Closing threadpool...");
         pool->Close();

         Progress(true, "Deleting threadpool...");
         delete(pool);
      }
      PrintSeparator();
   }

   void TryLockTest ()
   {
      TestHeader("Mutex TryLock Test");

      FastOS_ThreadPool pool(128*1024);
      Job job;
      std::mutex mtx;

      job.code = HOLD_MUTEX_FOR2SEC;
      job.result = -1;
      job.mutex = &mtx;
      job.ownThread = pool.NewThread(this,
                                     static_cast<void *>(&job));

      Progress(job.ownThread !=nullptr, "Creating thread");

      if(job.ownThread != nullptr)
      {
         bool lockrc;

         std::this_thread::sleep_for(1s);

         for(int i=0; i<5; i++)
         {
            lockrc = mtx.try_lock();
            Progress(!lockrc, "We should not get the mutex lock just yet (%s)",
                     lockrc ? "got it" : "didn't get it");
            if(lockrc) {
                mtx.unlock();
               break;
            }
         }

         std::this_thread::sleep_for(2s);

         lockrc = mtx.try_lock();
         Progress(lockrc, "We should get the mutex lock now (%s)",
                  lockrc ? "got it" : "didn't get it");

         if(lockrc)
            mtx.unlock();

         Progress(true, "Attempting to do normal lock...");
         mtx.lock();
         Progress(true, "Got lock. Attempt to do normal unlock...");
         mtx.unlock();
         Progress(true, "Unlock OK.");
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");

      PrintSeparator();
   }

};

int Thread_Mutex_Test::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   MutexTest(true);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   MutexTest(false);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   TryLockTest();
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }

   printf("END OF TEST (%s)\n", _argv[0]);
   return allWasOk() ? 0 : 1;
}

int main (int argc, char **argv)
{
   Thread_Mutex_Test app;
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
