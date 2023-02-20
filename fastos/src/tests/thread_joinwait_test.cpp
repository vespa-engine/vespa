// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include "job.h"
#include "thread_test_base.hpp"

class Thread_JoinWait_Test : public ThreadTestBase
{
   int Main () override;

   void SingleThreadJoinWaitMultipleTest(int variant)
   {
      bool rc=false;

      char testName[300];

      snprintf(testName, sizeof(testName), "Single Thread Join Wait Multiple Test %d", variant);
      TestHeader(testName);

      FastOS_ThreadPool pool;

      const int testThreads=5;
      int lastThreadNum = testThreads-1;
      int i;

      Job jobs[testThreads];

      std::mutex jobMutex;

      // The mutex is used to pause the first threads until we have created
      // the last one.
      jobMutex.lock();

      for(i=0; i<lastThreadNum; i++)
      {
         jobs[i].code = WAIT_FOR_THREAD_TO_FINISH;
         jobs[i].mutex = &jobMutex;
         jobs[i].ownThread = pool.NewThread(this, static_cast<void *>(&jobs[i]));

         rc = (jobs[i].ownThread != nullptr);
         Progress(rc, "Creating Thread %d", i+1);

         if(!rc)
            break;
      }

      if (rc)
      {
         jobs[lastThreadNum].code = (((variant & 2) != 0) ? NOP : PRINT_MESSAGE_AND_WAIT3MSEC);
         jobs[lastThreadNum].message = strdup("This is the thread that others wait for.");

         FastOS_ThreadInterface *lastThread;

         lastThread = pool.NewThread(this,
                                     static_cast<void *>
                                     (&jobs[lastThreadNum]));

         rc = (lastThread != nullptr);
         Progress(rc, "Creating last thread");

         if (rc)
         {
            for(i=0; i<lastThreadNum; i++) {
               jobs[i].otherThread = lastThread;
            }
         }
      }

      jobMutex.unlock();

      if ((variant & 1) != 0)
      {
         for (i=0; i<lastThreadNum; i++)
         {
            Progress(true, "Waiting for thread %d to finish using Join()", i+1);
            jobs[i].ownThread->Join();
            Progress(true, "Thread %d finished.", i+1);
         }
      }

      Progress(true, "Closing pool.");
      pool.Close();
      Progress(true, "Pool closed.");
      PrintSeparator();
   }

};

int Thread_JoinWait_Test::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   SingleThreadJoinWaitMultipleTest(0);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   SingleThreadJoinWaitMultipleTest(1);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   SingleThreadJoinWaitMultipleTest(2);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   SingleThreadJoinWaitMultipleTest(3);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   SingleThreadJoinWaitMultipleTest(2);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   SingleThreadJoinWaitMultipleTest(1);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   SingleThreadJoinWaitMultipleTest(0);
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }

   printf("END OF TEST (%s)\n", _argv[0]);
   return allWasOk() ? 0 : 1;
}

int main (int argc, char **argv)
{
   Thread_JoinWait_Test app;
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
