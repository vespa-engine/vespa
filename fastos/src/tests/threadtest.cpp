// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>

#include <vespa/fastos/fastos.h>
#include "tests.h"

#define PRI_TIME_COUNT 4096

enum JobCode
{
   PRINT_MESSAGE_AND_WAIT3SEC,
   INCREASE_NUMBER,
   PRIORITY_TEST,
   WAIT_FOR_BREAK_FLAG,
   WAIT_FOR_THREAD_TO_FINISH,
   WAIT_FOR_CONDITION,
   BOUNCE_CONDITIONS,
   TEST_ID,
   WAIT2SEC_AND_SIGNALCOND,
   HOLD_MUTEX_FOR2SEC,
   WAIT_2_SEC,
   SILENTNOP,
   NOP
};

class Job
{
private:
   Job(const Job &);
   Job &operator=(const Job&);

public:
   JobCode code;
   char *message;
   FastOS_Mutex *mutex;
   FastOS_Cond *condition;
   FastOS_BoolCond *boolcondition;
   FastOS_ThreadInterface *otherThread, *ownThread;
   double *timebuf;
   double average;
   int result;
   FastOS_ThreadId _threadId;
   Job *otherjob;
   int bouncewakeupcnt;
   bool bouncewakeup;

   Job()
     : code(NOP),
       message(NULL),
       mutex(NULL),
       condition(NULL),
       boolcondition(NULL),
       otherThread(NULL),
       ownThread(NULL),
       timebuf(NULL),
       average(0.0),
       result(-1),
       _threadId(),
       otherjob(NULL),
       bouncewakeupcnt(0),
       bouncewakeup(false)
   {
   }

   ~Job()
   {
      if(message != NULL)
         free(message);
   }
};

static volatile int64_t number;
#define INCREASE_NUMBER_AMOUNT 10000

#define MUTEX_TEST_THREADS 6
#define MAX_THREADS 7


class ThreadTest : public BaseTest, public FastOS_Runnable
{
private:
   FastOS_Mutex printMutex;

public:
   ThreadTest(void)
     : printMutex()
   {
   }
   virtual ~ThreadTest() {};

   void PrintProgress (char *string)
   {
      printMutex.Lock();
      BaseTest::PrintProgress(string);
      printMutex.Unlock();
   }

   void Run (FastOS_ThreadInterface *thread, void *arg);

   void WaitForThreadsToFinish (Job *jobs, int count)
   {
      int i;

      Progress(true, "Waiting for threads to finish...");
      for(;;)
      {
         bool threadsFinished=true;

         for(i=0; i<count; i++)
         {
            if(jobs[i].result == -1)
            {
               threadsFinished = false;
               break;
            }
         }

         FastOS_Thread::Sleep(500);

         if(threadsFinished)
            break;
      }

      Progress(true, "Threads finished");
   }

   void MutexTest (bool usingMutex)
   {
      if(usingMutex)
         TestHeader("Mutex Test");
      else
         TestHeader("Not Using Mutex Test");


      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024, MAX_THREADS);

      if(Progress(pool != NULL, "Allocating ThreadPool"))
      {
         int i;
         Job jobs[MUTEX_TEST_THREADS];
         FastOS_Mutex *myMutex=NULL;

         if(usingMutex)
            myMutex = new FastOS_Mutex();

         for(i=0; i<MUTEX_TEST_THREADS; i++)
         {
            jobs[i].code = INCREASE_NUMBER;
            jobs[i].mutex = myMutex;
         }

         number = 0;

         for(i=0; i<MUTEX_TEST_THREADS; i++)
         {
            bool rc = (NULL != pool->NewThread(this,
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

         if(myMutex != NULL)
            delete(myMutex);

         Progress(true, "Closing threadpool...");
         pool->Close();

         Progress(true, "Deleting threadpool...");
         delete(pool);
      }
      PrintSeparator();
   }


   void TooManyThreadsTest ()
   {
      TestHeader("Too Many Threads Test");

      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024, MAX_THREADS);

      if(Progress(pool != NULL, "Allocating ThreadPool"))
      {
         int i;
         Job jobs[MAX_THREADS];

         for(i=0; i<MAX_THREADS; i++)
         {
            jobs[i].code = PRINT_MESSAGE_AND_WAIT3SEC;
            jobs[i].message = static_cast<char *>(malloc(100));
            sprintf(jobs[i].message, "Thread %d invocation", i+1);
         }

         for(i=0; i<MAX_THREADS+1; i++)
         {
            if(i==MAX_THREADS)
            {
               bool rc = (NULL == pool->NewThread(this,
                                  static_cast<void *>(&jobs[0])));
               Progress(rc, "Creating too many threads should fail.");
            }
            else
            {
               bool rc = (NULL != pool->NewThread(this,
                                  static_cast<void *>(&jobs[i])));
               Progress(rc, "Creating Thread");
            }
         };

         WaitForThreadsToFinish(jobs, MAX_THREADS);

         Progress(true, "Verifying result codes...");
         for(i=0; i<MAX_THREADS; i++)
         {
            Progress(jobs[i].result ==
                     static_cast<int>(strlen(jobs[i].message)),
                     "Checking result code from thread (%d==%d)",
                     jobs[i].result, strlen(jobs[i].message));
         }

         Progress(true, "Closing threadpool...");
         pool->Close();

         Progress(true, "Deleting threadpool...");
         delete(pool);
      }
      PrintSeparator();
   }


   void HowManyThreadsTest ()
   {
      #define HOW_MAX_THREADS (1024)
      TestHeader("How Many Threads Test");

      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024, HOW_MAX_THREADS);

      if(Progress(pool != NULL, "Allocating ThreadPool"))
      {
         int i;
         Job jobs[HOW_MAX_THREADS];

         for(i=0; i<HOW_MAX_THREADS; i++)
         {
            jobs[i].code = PRINT_MESSAGE_AND_WAIT3SEC;
            jobs[i].message = static_cast<char *>(malloc(100));
            sprintf(jobs[i].message, "Thread %d invocation", i+1);
         }

         for(i=0; i<HOW_MAX_THREADS; i++)
         {
            if(i==HOW_MAX_THREADS)
            {
               bool rc = (NULL == pool->NewThread(this,
                                  static_cast<void *>(&jobs[0])));
               Progress(rc, "Creating too many threads should fail.");
            }
            else
            {
               bool rc = (NULL != pool->NewThread(this,
                                  static_cast<void *>(&jobs[i])));
               Progress(rc, "Creating Thread");
            }
         };

         WaitForThreadsToFinish(jobs, HOW_MAX_THREADS);

         Progress(true, "Verifying result codes...");
         for(i=0; i<HOW_MAX_THREADS; i++)
         {
            Progress(jobs[i].result ==
                     static_cast<int>(strlen(jobs[i].message)),
                     "Checking result code from thread (%d==%d)",
                     jobs[i].result, strlen(jobs[i].message));
         }

         Progress(true, "Closing threadpool...");
         pool->Close();

         Progress(true, "Deleting threadpool...");
         delete(pool);
      }
      PrintSeparator();
   }

   void CreateSingleThread ()
   {
      TestHeader("Create Single Thread Test");

      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024);

      if(Progress(pool != NULL, "Allocating ThreadPool"))
      {
         bool rc = (NULL != pool->NewThread(this, NULL));
         Progress(rc, "Creating Thread");

         Progress(true, "Sleeping 3 seconds");
         FastOS_Thread::Sleep(3000);
      }

      Progress(true, "Closing threadpool...");
      pool->Close();

      Progress(true, "Deleting threadpool...");
      delete(pool);
      PrintSeparator();
   }

   void CreateSingleThreadAndJoin ()
   {
      TestHeader("Create Single Thread And Join Test");

      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024);

      if(Progress(pool != NULL, "Allocating ThreadPool"))
      {
         Job job;

         job.code = NOP;
         job.result = -1;

         bool rc = (NULL != pool->NewThread(this, &job));
         Progress(rc, "Creating Thread");

         WaitForThreadsToFinish(&job, 1);
      }

      Progress(true, "Closing threadpool...");
      pool->Close();

      Progress(true, "Deleting threadpool...");
      delete(pool);
      PrintSeparator();
   }

  void ThreadCreatePerformance (bool silent, int count, int outercount)
  {
    int i;
    int j;
    bool rc;
    int threadsok;
    FastOS_Time starttime;
    FastOS_Time endtime;
    FastOS_Time usedtime;

    if (!silent)
      TestHeader("Thread Create Performance");

    FastOS_ThreadPool *pool = new FastOS_ThreadPool(128 * 1024);

    if (!silent)
      Progress(pool != NULL, "Allocating ThreadPool");
    if (pool != NULL) {
      Job *jobs = new Job[count];

      threadsok = 0;
      starttime.SetNow();
      for (i = 0; i < count; i++) {
        jobs[i].code = SILENTNOP;
        jobs[i].ownThread = pool->NewThread(this, &jobs[i]);
        rc = (jobs[i].ownThread != NULL);
        if (rc)
          threadsok++;
      }

      for (j = 0; j < outercount; j++) {
        for (i = 0; i < count; i++) {
          if (jobs[i].ownThread != NULL)
            jobs[i].ownThread->Join();
          jobs[i].ownThread = pool->NewThread(this, &jobs[i]);
          rc = (jobs[i].ownThread != NULL);
          if (rc)
            threadsok++;
        }
      }
      for (i = 0; i < count; i++) {
        if (jobs[i].ownThread != NULL)
          jobs[i].ownThread->Join();
      }
      endtime.SetNow();
      usedtime = endtime;
      usedtime -= starttime;

      if (!silent) {
        Progress(true, "Used time: %d.%03d",
                 usedtime.GetSeconds(), usedtime.GetMicroSeconds() / 1000);

        double timeused = usedtime.GetSeconds() +
          static_cast<double>(usedtime.GetMicroSeconds()) / 1000000.0;
        ProgressFloat(true, "Threads/s: %6.1f",
                      static_cast<float>(static_cast<double>(threadsok) /
                              timeused));
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
    for (i = 0; i < 8000; i++) {
      // Progress(true, "Creating pool iteration %d", i + 1);
      ThreadCreatePerformance(true, 2, 1);
    }
    PrintSeparator();
  }



   void ClosePoolTest ()
   {
      TestHeader("Close Pool Test");

      FastOS_ThreadPool pool(128*1024);
      const int closePoolThreads=9;
      Job jobs[closePoolThreads];

      number = 0;

      for(int i=0; i<closePoolThreads; i++)
      {
         jobs[i].code = INCREASE_NUMBER;

         bool rc = (NULL != pool.NewThread(this,
                            static_cast<void *>(&jobs[i])));
         Progress(rc, "Creating Thread %d", i+1);
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");
      PrintSeparator();
   }

   void BreakFlagTest ()
   {
      TestHeader("BreakFlag Test");

      FastOS_ThreadPool pool(128*1024);

      const int breakFlagThreads=4;

      Job jobs[breakFlagThreads];

      for(int i=0; i<breakFlagThreads; i++)
      {
         jobs[i].code = WAIT_FOR_BREAK_FLAG;

         bool rc = (NULL != pool.NewThread(this,
                            static_cast<void *>(&jobs[i])));
         Progress(rc, "Creating Thread %d", i+1);
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");
      PrintSeparator();
   }

   void SingleThreadJoinWaitMultipleTest(int variant)
   {
      bool rc=false;

      char testName[300];

      sprintf(testName, "Single Thread Join Wait Multiple Test %d", variant);
      TestHeader(testName);

      FastOS_ThreadPool pool(128*1024);

      const int testThreads=5;
      int lastThreadNum = testThreads-1;
      int i;

      Job jobs[testThreads];

      FastOS_Mutex jobMutex;

      // The mutex is used to pause the first threads until we have created
      // the last one.
      jobMutex.Lock();

      for(i=0; i<lastThreadNum; i++)
      {
         jobs[i].code = WAIT_FOR_THREAD_TO_FINISH;
         jobs[i].mutex = &jobMutex;
         jobs[i].ownThread = pool.NewThread(this,
                 static_cast<void *>(&jobs[i]));

         rc = (jobs[i].ownThread != NULL);
         Progress(rc, "Creating Thread %d", i+1);

         if(!rc)
            break;
      }

      if(rc)
      {
         jobs[lastThreadNum].code = (((variant & 2) != 0) ? NOP : PRINT_MESSAGE_AND_WAIT3SEC);
         jobs[lastThreadNum].message = strdup("This is the thread that others wait for.");

         FastOS_ThreadInterface *lastThread;

         lastThread = pool.NewThread(this,
                                     static_cast<void *>
                                     (&jobs[lastThreadNum]));

         rc = (lastThread != NULL);
         Progress(rc, "Creating last thread");

         if(rc)
         {
            for(i=0; i<lastThreadNum; i++)
            {
               jobs[i].otherThread = lastThread;
            }
         }
      }

      jobMutex.Unlock();

      if((variant & 1) != 0)
      {
         for(i=0; i<lastThreadNum; i++)
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

   void WaitForXThreadsToHaveWait (Job *jobs,
                                   int jobCount,
                                   FastOS_Cond *condition,
                                   int numWait)
   {
      Progress(true, "Waiting for %d threads to be in wait state", numWait);

      int oldNumber=-10000;
      for(;;)
      {
         int waitingThreads=0;

         condition->Lock();

         for(int i=0; i<jobCount; i++)
         {
            if(jobs[i].result == 1)
               waitingThreads++;
         }

         condition->Unlock();

         if(waitingThreads != oldNumber)
            Progress(true, "%d threads are waiting", waitingThreads);

         oldNumber = waitingThreads;

         if(waitingThreads == numWait)
            break;

         FastOS_Thread::Sleep(100);
      }
   }

   void SharedSignalAndBroadcastTest (Job *jobs, int numThreads,
                                      FastOS_Cond *condition,
                                      FastOS_ThreadPool *pool)
   {
      for(int i=0; i<numThreads; i++)
      {
         jobs[i].code = WAIT_FOR_CONDITION;
         jobs[i].condition = condition;
         jobs[i].ownThread = pool->NewThread(this,
                 static_cast<void *>(&jobs[i]));

         bool rc=(jobs[i].ownThread != NULL);
         Progress(rc, "CreatingThread %d", i+1);
      }

      WaitForXThreadsToHaveWait (jobs, numThreads,
                                 condition, numThreads);

      // Threads are not guaranteed to have entered sleep yet,
      // as this test only tests for result code
      // Wait another second to be sure.
      FastOS_Thread::Sleep(1000);
   }

   void BounceTest(void)
   {
      TestHeader("Bounce Test");

     FastOS_ThreadPool pool(128 * 1024);
     FastOS_Cond cond1;
     FastOS_Cond cond2;
     Job job1;
     Job job2;
     FastOS_Time checkTime;
     int cnt1;
     int cnt2;
     int cntsum;
     int lastcntsum;

     job1.code = BOUNCE_CONDITIONS;
     job2.code = BOUNCE_CONDITIONS;
     job1.otherjob = &job2;
     job2.otherjob = &job1;
     job1.condition = &cond1;
     job2.condition = &cond2;

     job1.ownThread = pool.NewThread(this, static_cast<void *>(&job1));
     job2.ownThread = pool.NewThread(this, static_cast<void *>(&job2));

     lastcntsum = -1;
     for (int iter = 0; iter < 8; iter++) {
       checkTime.SetNow();

       int left = static_cast<int>(checkTime.MilliSecsToNow());
       while (left < 1000) {
         FastOS_Thread::Sleep(1000 - left);
         left = static_cast<int>(checkTime.MilliSecsToNow());
       }

       cond1.Lock();
       cnt1 = job1.bouncewakeupcnt;
       cond1.Unlock();
       cond2.Lock();
       cnt2 = job2.bouncewakeupcnt;
       cond2.Unlock();
       cntsum = cnt1 + cnt2;
       Progress(lastcntsum != cntsum, "%d bounces", cntsum);
       lastcntsum = cntsum;
     }

     job1.ownThread->SetBreakFlag();
     cond1.Lock();
     job1.bouncewakeup = true;
     cond1.Signal();
     cond1.Unlock();

     job2.ownThread->SetBreakFlag();
     cond2.Lock();
     job2.bouncewakeup = true;
     cond2.Signal();
     cond2.Unlock();

     pool.Close();
     Progress(true, "Pool closed.");
     PrintSeparator();

   }

   void SignalTest ()
   {
      const int numThreads = 5;

      TestHeader("Signal Test");

      FastOS_ThreadPool pool(128*1024);
      Job jobs[numThreads];
      FastOS_Cond condition;

      SharedSignalAndBroadcastTest(jobs, numThreads, &condition, &pool);

      for(int i=0; i<numThreads; i++)
      {
         condition.Signal();
         WaitForXThreadsToHaveWait(jobs, numThreads,
                                   &condition, numThreads-1-i);
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");
      PrintSeparator();
   }

   void BroadcastTest ()
   {
      TestHeader("Broadcast Test");

      const int numThreads = 5;

      FastOS_ThreadPool pool(128*1024);
      Job jobs[numThreads];
      FastOS_Cond condition;

      SharedSignalAndBroadcastTest(jobs, numThreads, &condition, &pool);

      condition.Broadcast();
      WaitForXThreadsToHaveWait(jobs, numThreads, &condition, 0);

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");
      PrintSeparator();
   }

   void ThreadIdTest ()
   {
      const int numThreads = 5;
      int i,j;

      TestHeader ("Thread Id Test");

      FastOS_ThreadPool pool(128*1024);
      Job jobs[numThreads];
      FastOS_Mutex slowStartMutex;

      slowStartMutex.Lock();    // Halt all threads until we want them to run

      for(i=0; i<numThreads; i++) {
         jobs[i].code = TEST_ID;
         jobs[i].result = -1;
         jobs[i]._threadId = 0;
         jobs[i].mutex = &slowStartMutex;
         jobs[i].ownThread = pool.NewThread(this,
                 static_cast<void *>(&jobs[i]));
         bool rc=(jobs[i].ownThread != NULL);
         if(rc)
            jobs[i]._threadId = jobs[i].ownThread->GetThreadId();
         Progress(rc, "CreatingThread %d id:%lu", i+1,
                  (unsigned long)(jobs[i]._threadId));

         for(j=0; j<i; j++) {
            if(jobs[j]._threadId == jobs[i]._threadId) {
               Progress(false,
                        "Two different threads received the "
                        "same thread id (%lu)",
                        (unsigned long)(jobs[i]._threadId));
            }
         }
      }

      slowStartMutex.Unlock();    // Allow threads to run

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");

      for(i=0; i<numThreads; i++) {
         Progress(jobs[i].result == 1,
                  "Thread %lu: ID comparison (current vs stored)",
                  (unsigned long)(jobs[i]._threadId));
      }

      PrintSeparator();
   }

   void TimedWaitTest ()
   {
      TestHeader("Cond Timed Wait Test");

      FastOS_ThreadPool pool(128*1024);
      Job job;
      FastOS_Cond condition;

      job.code = WAIT2SEC_AND_SIGNALCOND;
      job.result = -1;
      job.condition = &condition;
      job.ownThread = pool.NewThread(this,
                                     static_cast<void *>(&job));

      Progress(job.ownThread !=NULL, "Creating thread");

      if(job.ownThread != NULL)
      {
         condition.Lock();
         bool gotCond = condition.TimedWait(500);
         Progress(!gotCond, "We should not get the condition just yet (%s)",
                  gotCond ? "got it" : "didn't get it");
         gotCond = condition.TimedWait(500);
         Progress(!gotCond, "We should not get the condition just yet (%s)",
                  gotCond ? "got it" : "didn't get it");
         gotCond = condition.TimedWait(5000);
         Progress(gotCond, "We should have got the condition now (%s)",
                  gotCond ? "got it" : "didn't get it");
         condition.Unlock();
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");

      PrintSeparator();
   }

   void TryLockTest ()
   {
      TestHeader("Mutex TryLock Test");

      FastOS_ThreadPool pool(128*1024);
      Job job;
      FastOS_Mutex mtx;

      job.code = HOLD_MUTEX_FOR2SEC;
      job.result = -1;
      job.mutex = &mtx;
      job.ownThread = pool.NewThread(this,
                                     static_cast<void *>(&job));

      Progress(job.ownThread !=NULL, "Creating thread");

      if(job.ownThread != NULL)
      {
         bool lockrc;

         FastOS_Thread::Sleep(1000);

         for(int i=0; i<5; i++)
         {
            lockrc = mtx.TryLock();
            Progress(!lockrc, "We should not get the mutex lock just yet (%s)",
                     lockrc ? "got it" : "didn't get it");
            if(lockrc) {
                mtx.Unlock();
               break;
            }
         }

         FastOS_Thread::Sleep(2000);

         lockrc = mtx.TryLock();
         Progress(lockrc, "We should get the mutex lock now (%s)",
                  lockrc ? "got it" : "didn't get it");

         if(lockrc)
            mtx.Unlock();

         Progress(true, "Attempting to do normal lock...");
         mtx.Lock();
         Progress(true, "Got lock. Attempt to do normal unlock...");
         mtx.Unlock();
         Progress(true, "Unlock OK.");
      }

      Progress(true, "Waiting for threads to finish using pool.Close()...");
      pool.Close();
      Progress(true, "Pool closed.");

      PrintSeparator();
   }

   void ThreadStatsTest ()
   {
      int inactiveThreads;
      int activeThreads;
      int startedThreads;

      TestHeader("Thread Statistics Test");

      FastOS_ThreadPool pool(128*1024);
      Job job[2];

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Initial inactive threads = %d",
               inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 0, "Initial active threads = %d",
               activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 0, "Initial started threads = %d",
               startedThreads);

      job[0].code = WAIT_FOR_BREAK_FLAG;
      job[0].ownThread = pool.NewThread(this,
                                        static_cast<void *>(&job[0]));

      FastOS_Thread::Sleep(1000);

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 1, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 1, "Started threads = %d", startedThreads);

      job[1].code = WAIT_FOR_BREAK_FLAG;
      job[1].ownThread = pool.NewThread(this,
                                        static_cast<void *>(&job[1]));

      FastOS_Thread::Sleep(1000);

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 2, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 2, "Started threads = %d", startedThreads);

      Progress(true, "Setting breakflag on threads...");
      job[0].ownThread->SetBreakFlag();
      job[1].ownThread->SetBreakFlag();

      FastOS_Thread::Sleep(3000);

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 2, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 0, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 2, "Started threads = %d", startedThreads);


      Progress(true, "Repeating process in the same pool...");

      job[0].code = WAIT_FOR_BREAK_FLAG;
      job[0].ownThread = pool.NewThread(this, static_cast<void *>(&job[0]));

      FastOS_Thread::Sleep(1000);

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 1, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 1, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 3, "Started threads = %d", startedThreads);

      job[1].code = WAIT_FOR_BREAK_FLAG;
      job[1].ownThread = pool.NewThread(this, static_cast<void *>(&job[1]));

      FastOS_Thread::Sleep(1000);

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 2, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 4, "Started threads = %d", startedThreads);

      Progress(true, "Setting breakflag on threads...");
      job[0].ownThread->SetBreakFlag();
      job[1].ownThread->SetBreakFlag();

      FastOS_Thread::Sleep(3000);

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 2, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 0, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 4, "Started threads = %d", startedThreads);


      pool.Close();
      Progress(true, "Pool closed.");

      PrintSeparator();
   }

   int Main ();

   void LeakTest ()
   {
      TestHeader("Leak Test");

      int allocCount = 2 * 1024 * 1024;
      int progressIndex= allocCount/8;
      int i;

      for(i=0; i<allocCount; i++)
      {
         FastOS_Mutex *mtx = new FastOS_Mutex();
         mtx->Lock();
         mtx->Unlock();
         delete mtx;

         if((i % progressIndex) == (progressIndex - 1))
            Progress(true, "Tested %d FastOS_Mutex instances", i + 1);
      }

      for(i=0; i<allocCount; i++)
      {
         FastOS_Cond *cond = new FastOS_Cond();
         delete cond;

         if((i % progressIndex) == (progressIndex - 1))
            Progress(true, "Tested %d FastOS_Cond instances", i+1);
      }

      for(i=0; i<allocCount; i++)
      {
         FastOS_BoolCond *cond = new FastOS_BoolCond();
         delete cond;

         if((i % progressIndex) == (progressIndex - 1))
            Progress(true, "Tested %d FastOS_BoolCond instances", i+1);
      }

      PrintSeparator();
   }

   void SynchronizationStressTest ()
   {
      TestHeader("Synchronization Object Stress Test");

      const int allocCount = 150000;
      int i;

      FastOS_Mutex **mutexes = new FastOS_Mutex*[allocCount];

      FastOS_Time startTime, nowTime;
      startTime.SetNow();

      for(i=0; i<allocCount; i++)
         mutexes[i] = new FastOS_Mutex();

      nowTime.SetNow();
      Progress(true, "Allocated %d mutexes at time: %d ms", allocCount,
               static_cast<int>(nowTime.MilliSecs() - startTime.MilliSecs()));

      for(int e=0; e<4; e++)
      {
         for(i=0; i<allocCount; i++)
            mutexes[i]->Lock();

         for(i=0; i<allocCount; i++)
            mutexes[i]->Unlock();

         nowTime.SetNow();
         Progress(true, "Tested %d mutexes at time: %d ms", allocCount,
                  static_cast<int>(nowTime.MilliSecs() -
                                   startTime.MilliSecs()));
      }
      for(i=0; i<allocCount; i++)
         delete mutexes[i];

      nowTime.SetNow();
      Progress(true, "Deleted %d mutexes at time: %d ms", allocCount,
               static_cast<int>(nowTime.MilliSecs() - startTime.MilliSecs()));

      delete [] mutexes;

      PrintSeparator();
   }

};

int ThreadTest::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);

   //   HowManyThreadsTest();
   SynchronizationStressTest();
   LeakTest();
   ThreadStatsTest();
   TryLockTest();
   TimedWaitTest();
   ThreadIdTest();
   SignalTest();
   BroadcastTest();
   CreateSingleThread();
   CreateSingleThreadAndJoin();
   TooManyThreadsTest();
   MutexTest(false);
   MutexTest(true);
   ClosePoolTest();
   BreakFlagTest();
   SingleThreadJoinWaitMultipleTest(0);
   SingleThreadJoinWaitMultipleTest(1);
   SingleThreadJoinWaitMultipleTest(2);
   SingleThreadJoinWaitMultipleTest(3);
   SingleThreadJoinWaitMultipleTest(2);
   SingleThreadJoinWaitMultipleTest(1);
   SingleThreadJoinWaitMultipleTest(0);
   BreakFlagTest();
   ClosePoolTest();
   MutexTest(true);
   MutexTest(false);
   TooManyThreadsTest();
   CreateSingleThreadAndJoin();
   CreateSingleThread();
   BroadcastTest();
   SignalTest();
   ThreadCreatePerformance(false, 500, 100);
   ClosePoolStability();
   BounceTest();

   printf("END OF TEST (%s)\n", _argv[0]);

   return 0;
}

volatile int busyCnt;

void ThreadTest::Run (FastOS_ThreadInterface *thread, void *arg)
{
   if(arg == NULL)
      return;

   Job *job = static_cast<Job *>(arg);
   char someStack[15*1024];

   memset(someStack, 0, 15*1024);

   switch(job->code)
   {
      case SILENTNOP:
      {
         job->result = 1;
         break;
      }

      case NOP:
      {
         Progress(true, "Doing NOP");
         job->result = 1;
         break;
      }

      case PRINT_MESSAGE_AND_WAIT3SEC:
      {
         Progress(true, "Thread printing message: [%s]", job->message);
         job->result = strlen(job->message);

         FastOS_Thread::Sleep(3000);
         break;
      }

      case INCREASE_NUMBER:
      {
         int result;

         if(job->mutex != NULL)
            job->mutex->Lock();

         result = static_cast<int>(number);

         int sleepOn = (INCREASE_NUMBER_AMOUNT/2) * 321/10000;
         for(int i=0; i<(INCREASE_NUMBER_AMOUNT/2); i++)
         {
            number = number + 2;

            if(i == sleepOn)
               FastOS_Thread::Sleep(1000);
         }

         if(job->mutex != NULL)
            job->mutex->Unlock();

         job->result = result;  // This marks the end of the thread

         break;
      }

      case WAIT_FOR_BREAK_FLAG:
      {
         for(;;)
         {
            FastOS_Thread::Sleep(1000);

            if(thread->GetBreakFlag())
            {
               Progress(true, "Thread %p got breakflag", thread);
               break;
            }
         }
         break;
      }

      case WAIT_FOR_THREAD_TO_FINISH:
      {
         if(job->mutex)
            job->mutex->Lock();

         if(job->otherThread != NULL)
            job->otherThread->Join();

         if(job->mutex)
            job->mutex->Unlock();
         break;
      }

      case WAIT_FOR_CONDITION:
      {
         job->condition->Lock();

         job->result = 1;

         job->condition->Wait();
         job->condition->Unlock();

         job->result = 0;

         break;
      }

      case BOUNCE_CONDITIONS:
      {
        while (!thread->GetBreakFlag()) {
          job->otherjob->condition->Lock();
          job->otherjob->bouncewakeupcnt++;
          job->otherjob->bouncewakeup = true;
          job->otherjob->condition->Signal();
          job->otherjob->condition->Unlock();

          job->condition->Lock();
          while (!job->bouncewakeup)
            job->condition->TimedWait(1);
          job->bouncewakeup = false;
          job->condition->Unlock();
        }
        break;
      }

      case TEST_ID:
      {
         job->mutex->Lock();          // Initially the parent threads owns the lock
         job->mutex->Unlock();        // It is unlocked when we should start

         FastOS_ThreadId currentId = FastOS_Thread::GetCurrentThreadId();

         if(currentId == job->_threadId)
            job->result = 1;
         else
            job->result = -1;
         break;
      }

      case WAIT2SEC_AND_SIGNALCOND:
      {
         FastOS_Thread::Sleep(2000);
         job->condition->Signal();
         job->result = 1;
         break;
      }

      case HOLD_MUTEX_FOR2SEC:
      {
         job->mutex->Lock();
         FastOS_Thread::Sleep(2000);
         job->mutex->Unlock();
         job->result = 1;
         break;
      }

      case WAIT_2_SEC:
      {
         FastOS_Thread::Sleep(2000);
         job->result = 1;
         break;
      }

      default:
         Progress(false, "Unknown jobcode");
         break;
   }
}

int main (int argc, char **argv)
{
   ThreadTest app;
   setvbuf(stdout, NULL, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
