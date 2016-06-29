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

   int Main ();

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

};

int ThreadTest::Main ()
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
